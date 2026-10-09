/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.actions.ElementContextMenuContributor;
import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import com.gluonhq.netbeans.nbfx.api.view.CellBreadth;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberFilters;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberFilters.Filter;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MembersScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javax.lang.model.element.ElementKind;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The Members view of the Navigator: the tree of the types and members of the edited file, over
 * the filter bar that hides member categories, qualifies type names and picks the sort order.
 * Double-click or Enter on a row opens it in the editor; the row enclosing the editor's caret is
 * kept selected. Row expansion survives rescans of the same file; a new file starts fully expanded,
 * as in NetBeans.
 */
final class MembersPanel extends BorderPane implements NavigatorPanel<MembersScanner.Result> {

    private final MemberFilters filters;
    private final TreeView<MemberNode> tree = new TreeView<>();
    private final CellBreadth breadth = new CellBreadth();
    private final TreeItem<MemberNode> root = new TreeItem<>();
    private final Label empty = new Label(message("LBL_NoMembersAvail"));
    private MembersScanner.Result result;
    /** The keys of every row of the last build, to tell a new row (expanded) from a known one. */
    private Set<String> knownKeys = Set.of();
    /** The row selected for the editor's caret, and its key so a rebuild keeps it without scrolling again. */
    private TreeItem<MemberNode> caretItem;
    private String caretKey;

    MembersPanel(MemberFilters filters) {
        this.filters = filters;
        getStyleClass().add("members-panel");

        root.setExpanded(true);
        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setMinHeight(0);
        tree.getStyleClass().add("members-tree");
        tree.setCellFactory(view -> new MemberTreeCell(() -> filters.fullyQualifiedNames().get(), breadth));
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                openSelected();
            }
        });
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openSelected();
                e.consume();
            }
        });
        ContextMenu contextMenu = new ContextMenu();
        // The items depend on the selected row, so they are rebuilt whenever the menu is requested -
        // before the tree shows it: a context menu without items is not shown at all.
        tree.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> fillContextMenu(contextMenu));
        fillContextMenu(contextMenu);
        tree.setContextMenu(contextMenu);

        empty.getStyleClass().add("navigator-empty");
        StackPane.setAlignment(empty, Pos.CENTER);
        StackPane center = new StackPane(tree, empty);
        center.setMinHeight(0);
        setCenter(center);
        setBottom(createFilterBar());
        setMinHeight(0);

        // Filtering and sorting keep the expansion of the rows that stay.
        filters.subscribe(() -> rebuild(expandedKeys()));
        updateEmpty();
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public MembersScanner.Result scan(FileObject file, String text, Cancellation cancellation) throws IOException {
        return MembersScanner.scan(file, text, cancellation);
    }

    /**
     * Shows {@code result}: a rescan of the file already shown keeps the expansion of its rows (new
     * rows start expanded); another file starts fully expanded. {@code null} shows no members.
     */
    @Override
    public void show(MembersScanner.Result result) {
        boolean sameFile = this.result != null && result != null && this.result.file().equals(result.file());
        Set<String> expanded = sameFile ? expandedKeys() : null;
        this.result = result;
        rebuild(expanded);
    }

    @Override
    public void clear() {
        result = null;
        rebuild(null);
    }

    /**
     * Selects the deepest row whose declaration encloses {@code offset} - the editor's caret - or
     * the nearest visible ancestor of it; clears the selection when no row does, without moving the
     * keyboard focus. A row whose declaration is not in the file (inherited) is never selected.
     */
    @Override
    public void selectAt(int offset) {
        TreeItem<MemberNode> item = enclosing(root, offset);
        if (item == null) {
            // Nothing encloses the caret - or there is no caret, the file being shown from the
            // tree -: no row is selected, not even one the user picked before.
            caretItem = null;
            caretKey = null;
            tree.getSelectionModel().clearSelection();
            return;
        }
        if (item == caretItem) {
            return;
        }
        caretItem = item;
        caretKey = item.getValue().getKey();
        for (TreeItem<MemberNode> ancestor = item.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.setExpanded(true);
        }
        tree.getSelectionModel().select(item);
        TreeScrolling.scrollIntoView(tree, item);
    }

    /** Selects the type row {@code qualifiedName} names: the root whose name is a segment of it, then the nested ones. */
    @Override
    public void selectType(String qualifiedName) {
        String[] segments = qualifiedName.split("[.$]");
        TreeItem<MemberNode> found = null;
        for (int i = 0; i < segments.length && found == null; i++) {
            TreeItem<MemberNode> item = childType(root, segments[i]);
            for (int j = i + 1; item != null && j < segments.length; j++) {
                TreeItem<MemberNode> nested = childType(item, segments[j]);
                if (nested == null) {
                    break;
                }
                item = nested;
            }
            found = item;
        }
        caretItem = found;
        caretKey = found == null ? null : found.getValue().getKey();
        if (found == null) {
            tree.getSelectionModel().clearSelection();
            return;
        }
        for (TreeItem<MemberNode> ancestor = found.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.setExpanded(true);
        }
        tree.getSelectionModel().select(found);
        TreeScrolling.scrollIntoView(tree, found);
    }

    private static TreeItem<MemberNode> childType(TreeItem<MemberNode> parent, String name) {
        for (TreeItem<MemberNode> item : parent.getChildren()) {
            MemberNode node = item.getValue();
            if (node != null && node.isType() && !node.isInherited() && node.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void requestFocus() {
        tree.requestFocus();
    }

    private TreeItem<MemberNode> enclosing(TreeItem<MemberNode> parent, int offset) {
        for (TreeItem<MemberNode> item : parent.getChildren()) {
            MemberNode node = item.getValue();
            if (node != null && node.contains(offset)) {
                TreeItem<MemberNode> deeper = enclosing(item, offset);
                return deeper != null ? deeper : item;
            }
        }
        return null;
    }

    private void openSelected() {
        TreeItem<MemberNode> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null) {
            MemberOpener.open(result, selected.getValue());
        }
    }

    // -- tree ----------------------------------------------------------------------------------

    /** Rebuilds the rows; {@code expanded} are the keys of the rows to expand, {@code null} expanding every type. */
    private void rebuild(Set<String> expanded) {
        MemberNode selected = tree.getSelectionModel().getSelectedItem() == null
                ? null : tree.getSelectionModel().getSelectedItem().getValue();
        Set<String> keys = new HashSet<>();
        List<TreeItem<MemberNode>> items = result == null
                ? List.of() : items(result.roots(), expanded, keys);
        breadth.reset();
        root.getChildren().setAll(items);
        knownKeys = keys;
        // The caret's row survives a rebuild under its key, so a rescan does not scroll back to it.
        caretItem = caretKey == null ? null : find(root, caretKey);
        updateEmpty();
        if (selected != null) {
            TreeItem<MemberNode> again = find(root, selected.getKey());
            if (again != null) {
                tree.getSelectionModel().select(again);
            }
        }
    }

    private List<TreeItem<MemberNode>> items(List<MemberNode> nodes, Set<String> expanded, Set<String> keys) {
        List<TreeItem<MemberNode>> items = new ArrayList<>();
        for (MemberNode node : filters.apply(nodes)) {
            TreeItem<MemberNode> item = new TreeItem<>(node);
            keys.add(node.getKey());
            if (node.isType()) {
                item.getChildren().setAll(items(node.getChildren(), expanded, keys));
                item.setExpanded(expanded == null || expanded.contains(node.getKey()) || !knownKeys.contains(node.getKey()));
            }
            items.add(item);
        }
        return items;
    }

    private Set<String> expandedKeys() {
        Set<String> keys = new HashSet<>();
        collectExpanded(root, keys);
        return keys;
    }

    private static void collectExpanded(TreeItem<MemberNode> parent, Set<String> keys) {
        for (TreeItem<MemberNode> item : parent.getChildren()) {
            if (item.isExpanded() && item.getValue() != null) {
                keys.add(item.getValue().getKey());
            }
            collectExpanded(item, keys);
        }
    }

    private static TreeItem<MemberNode> find(TreeItem<MemberNode> parent, String key) {
        for (TreeItem<MemberNode> item : parent.getChildren()) {
            if (item.getValue() != null && key.equals(item.getValue().getKey())) {
                return item;
            }
            TreeItem<MemberNode> found = find(item, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void updateEmpty() {
        boolean none = root.getChildren().isEmpty();
        empty.setVisible(none);
        tree.setVisible(!none);
    }

    // -- context menu --------------------------------------------------------------------------

    /**
     * The row menu, as NetBeans': Go to Source and the items other modules contribute for the
     * element (Find Usages), the sort order, and a Filters submenu. The element items are built each
     * time the menu opens, for the selected row; without one only the sort and filter items apply.
     */
    private void fillContextMenu(ContextMenu menu) {
        TreeItem<MemberNode> selected = tree.getSelectionModel().getSelectedItem();
        MemberNode node = selected == null ? null : selected.getValue();
        List<MenuItem> items = new ArrayList<>();
        MenuItem goToSource = new MenuItem(message("CTX_GoToSource"));
        goToSource.setDisable(node == null);
        goToSource.setOnAction(a -> openSelected());
        items.add(goToSource);
        if (node != null && result != null) {
            CompletableFuture<SourceLocation> location = MemberOpener.locate(result, node);
            for (ElementContextMenuContributor contributor : Lookup.getDefault().lookupAll(ElementContextMenuContributor.class)) {
                items.addAll(contributor.itemsFor(location));
            }
        }
        items.add(new SeparatorMenuItem());
        ToggleGroup sorts = new ToggleGroup();
        RadioMenuItem byName = new RadioMenuItem(message("CTX_SortByName"), NavigatorIcons.view("sortAlpha"));
        RadioMenuItem bySource = new RadioMenuItem(message("CTX_SortBySource"), NavigatorIcons.view("sortPosition"));
        byName.setToggleGroup(sorts);
        bySource.setToggleGroup(sorts);
        byName.setSelected(filters.sortByName().get());
        bySource.setSelected(!filters.sortByName().get());
        byName.setOnAction(a -> filters.sortByName().set(true));
        bySource.setOnAction(a -> filters.sortByName().set(false));
        items.add(byName);
        items.add(bySource);
        items.add(new SeparatorMenuItem());
        Menu filtersMenu = new Menu(message("CTX_Filters"));
        for (Filter filter : Filter.values()) {
            CheckMenuItem item = new CheckMenuItem(message("CTX_Show" + filter.name()));
            item.setSelected(filters.showing(filter).get());
            item.setOnAction(a -> filters.showing(filter).set(item.isSelected()));
            filtersMenu.getItems().add(item);
        }
        items.add(filtersMenu);
        menu.getItems().setAll(items);
    }

    // -- filter bar ----------------------------------------------------------------------------

    private ToolBar createFilterBar() {
        List<Node> items = new ArrayList<>();
        items.add(filterToggle(Filter.INHERITED, NavigatorIcons.view("filterHideInherited"), "LBL_ShowInheritedTip"));
        items.add(filterToggle(Filter.FIELDS, NavigatorIcons.view("filterHideFields"), "LBL_ShowFieldsTip"));
        items.add(filterToggle(Filter.STATIC, NavigatorIcons.view("filterHideStatic"), "LBL_ShowStaticTip"));
        items.add(filterToggle(Filter.NON_PUBLIC, NavigatorIcons.view("filterHideNonPublic"), "LBL_ShowNonPublicTip"));
        items.add(filterToggle(Filter.INNER_CLASSES, NavigatorIcons.elementIcon(ElementKind.CLASS, 0), "LBL_ShowInnerClassesTip"));
        items.add(new Separator(Orientation.VERTICAL));

        ToggleButton fqn = toggle(NavigatorIcons.view("fqn"), "LBL_FullyQualifiedNamesTip");
        fqn.selectedProperty().bindBidirectional(filters.fullyQualifiedNames());
        items.add(fqn);
        items.add(new Separator(Orientation.VERTICAL));

        // One of the two sort orders is always selected.
        ToggleGroup sorts = new ToggleGroup();
        ToggleButton byName = toggle(NavigatorIcons.view("sortAlpha"), "LBL_SortByNameTip");
        ToggleButton bySource = toggle(NavigatorIcons.view("sortPosition"), "LBL_SortBySourceTip");
        byName.setToggleGroup(sorts);
        bySource.setToggleGroup(sorts);
        (filters.sortByName().get() ? byName : bySource).setSelected(true);
        sorts.selectedToggleProperty().subscribe((was, now) -> {
            if (now == null) {
                sorts.selectToggle(was);
            } else {
                filters.sortByName().set(now == byName);
            }
        });
        filters.sortByName().subscribe(name -> sorts.selectToggle(name ? byName : bySource));
        items.add(byName);
        items.add(bySource);

        ToolBar bar = new ToolBar(items.toArray(Node[]::new));
        bar.setOrientation(Orientation.HORIZONTAL);
        bar.getStyleClass().add("navigator-filters");
        return bar;
    }

    private ToggleButton filterToggle(Filter filter, Node icon, String tooltipKey) {
        ToggleButton button = toggle(icon, tooltipKey);
        button.selectedProperty().bindBidirectional(filters.showing(filter));
        return button;
    }

    private static ToggleButton toggle(Node icon, String tooltipKey) {
        ToggleButton button = new ToggleButton();
        decorate(button, icon, tooltipKey);
        return button;
    }

    static void decorate(ButtonBase button, Node icon, String tooltipKey) {
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.getStyleClass().add("navigator-tool-button");
        button.setFocusTraversable(false);
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(MembersPanel.class, key, args);
    }
}
