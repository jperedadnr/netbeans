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
package com.gluonhq.netbeans.nbfx.structure.maven;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.structure.BackgroundScans;
import com.gluonhq.netbeans.nbfx.structure.NavigatorButtons;
import com.gluonhq.netbeans.nbfx.structure.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TreeScrolling;
import com.gluonhq.netbeans.nbfx.structure.xml.XmlNode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * The POM model panel, after NetBeans' {@code POMModelPanel}: the POM's elements in schema order,
 * merged with the parents': a row reads <em>Name : value</em> with the effective value, in italics
 * when a parent supplies it, in bold when the POM overrides a parent's, and
 * <em>&lt;Undefined&gt;</em> when no POM defines it (hidden by the first filter). The second filter
 * sorts list items by name. The icon tells how far up the lineage the value comes from. Double-click
 * (or Enter) shows the element in its POM, <em>Show in POM</em> in the context menu picks the layer;
 * the editor's caret keeps the row of the element it is in selected.
 */
final class PomModelUI extends BorderPane implements ScanningPanel<PomModelUI.Result> {

    /**
     * The scan of a POM: its lineage and the tree built from it, with the elements of the POM's own
     * text by the rows standing for them. {@link #root} is {@code null} while the text is not a
     * well-formed POM: the tree shown last stays.
     */
    record Result(FileObject file, String text, List<PomLayer> layers, PomNode root,
            Map<XmlNode, PomNode> elements, XmlNode document, String problem) {

        boolean parsed() {
            return root != null;
        }
    }

    private static final Logger LOG = Logger.getLogger(PomModelUI.class.getName());
    private static final String SHOW_UNDEFINED = "navigator.showUndefined";
    private static final String SORT_LISTS = "navigator.sortLists";
    private static final String[] LEVEL_ICONS = {"icons/value.png", "icons/value2.png", "icons/value3.png", "icons/value4.png"};
    private static final Comparator<PomNode> BY_NAME = Comparator.comparing(PomNode::displayName, String.CASE_INSENSITIVE_ORDER);

    private final Preferences prefs = NbPreferences.forModule(PomModelUI.class);
    /** Whether rows no POM defines are hidden ("show only POM elements defined in at least one place"). */
    private final BooleanProperty filterUndefined = new SimpleBooleanProperty(this, SHOW_UNDEFINED, true);
    private final BooleanProperty sortLists = new SimpleBooleanProperty(this, SORT_LISTS, false);
    private final TreeView<PomNode> tree = new TreeView<>();
    private final TreeItem<PomNode> rootItem = new TreeItem<>();
    private final Map<PomNode, TreeItem<PomNode>> items = new HashMap<>();
    private final Label problem = new Label();
    private Result result;
    private FileObject shownFile;
    private TreeItem<PomNode> caretItem;

    PomModelUI() {
        filterUndefined.set(prefs.getBoolean(SHOW_UNDEFINED, true));
        sortLists.set(prefs.getBoolean(SORT_LISTS, false));
        filterUndefined.subscribe(on -> {
            prefs.putBoolean(SHOW_UNDEFINED, on);
            rebuild(expandedKeys());
        });
        sortLists.subscribe(on -> {
            prefs.putBoolean(SORT_LISTS, on);
            rebuild(expandedKeys());
        });

        rootItem.setExpanded(true);
        tree.setRoot(rootItem);
        tree.setShowRoot(false);
        tree.setMinHeight(0);
        tree.getStyleClass().add("navigator-tree");
        tree.setCellFactory(view -> new PomCell());
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                showSelected();
            }
        });
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                showSelected();
                e.consume();
            }
        });
        ContextMenu contextMenu = new ContextMenu();
        tree.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> fillContextMenu(contextMenu));
        fillContextMenu(contextMenu);
        tree.setContextMenu(contextMenu);

        problem.getStyleClass().add("navigator-empty");
        problem.setVisible(false);
        problem.setManaged(false);
        StackPane center = new StackPane(tree);
        center.setMinHeight(0);
        setTop(problem);
        setCenter(center);
        setBottom(createFilterBar());
        setMinHeight(0);
    }

    private Node createFilterBar() {
        ToggleButton undefined = NavigatorButtons.toggle(NavigatorIcons.view(PomModelUI.class, "icons/filterHideFields.png"),
                message("DESC_FilterUndefined"));
        undefined.selectedProperty().bindBidirectional(filterUndefined);
        ToggleButton sort = NavigatorButtons.toggle(NavigatorIcons.view(PomModelUI.class, "icons/sortAlpha.png"),
                message("DESC_SortLists"));
        sort.selectedProperty().bindBidirectional(sortLists);
        return NavigatorButtons.filterBar(undefined, sort);
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Result scan(FileObject file, String text, Cancellation cancellation) {
        File pomFile = FileUtil.toFile(file);
        if (pomFile == null) {
            return null;
        }
        PomLineage.Result lineage = PomLineage.read(pomFile, text);
        if (lineage.layers() == null || cancellation.isCancelled()) {
            return new Result(file, text, null, null, null, null, null);
        }
        PomNode root = PomTreeBuilder.build(lineage.layers());
        PomLocator locator = new PomLocator(text);
        return new Result(file, text, lineage.layers(), root, locator.map(root, 0), locator.document(), lineage.problem());
    }

    @Override
    public void show(Result newResult) {
        if (newResult == null) {
            clear();
            return;
        }
        boolean sameFile = newResult.file().equals(shownFile);
        if (!newResult.parsed()) {
            // Not a well-formed POM right now (being edited): the last tree of the file stays.
            if (!sameFile) {
                clear();
            }
            return;
        }
        Set<String> expanded = sameFile ? expandedKeys() : Set.of();
        result = newResult;
        shownFile = newResult.file();
        problem.setText(newResult.problem());
        problem.setVisible(newResult.problem() != null);
        problem.setManaged(newResult.problem() != null);
        rebuild(expanded);
    }

    private void rebuild(Set<String> expanded) {
        caretItem = null;
        items.clear();
        rootItem.getChildren().clear();
        if (result == null) {
            return;
        }
        addChildren(rootItem, result.root(), expanded);
    }

    private void addChildren(TreeItem<PomNode> parent, PomNode node, Set<String> expanded) {
        List<PomNode> children = new ArrayList<>();
        for (PomNode child : node.children()) {
            if (!filterUndefined.get() || child.definesValue()) {
                children.add(child);
            }
        }
        if (sortLists.get() && !children.isEmpty() && children.get(0).isListItem()) {
            children.sort(BY_NAME);
        }
        for (PomNode child : children) {
            TreeItem<PomNode> item = new TreeItem<>(child);
            items.put(child, item);
            parent.getChildren().add(item);
            addChildren(item, child, expanded);
            if (expanded.contains(child.pathKey())) {
                item.setExpanded(true);
            }
        }
    }

    private Set<String> expandedKeys() {
        Set<String> keys = new HashSet<>();
        collectExpanded(rootItem, keys);
        return keys;
    }

    private static void collectExpanded(TreeItem<PomNode> item, Set<String> keys) {
        for (TreeItem<PomNode> child : item.getChildren()) {
            if (child.isExpanded() && child.getValue() != null) {
                keys.add(child.getValue().pathKey());
                collectExpanded(child, keys);
            }
        }
    }

    @Override
    public void clear() {
        result = null;
        shownFile = null;
        caretItem = null;
        items.clear();
        rootItem.getChildren().clear();
        problem.setVisible(false);
        problem.setManaged(false);
    }

    @Override
    public void selectAt(int offset) {
        PomNode node = null;
        if (result != null && offset >= 0) {
            for (XmlNode element = result.document().nodeAt(offset); element != null && node == null;
                    element = element.parent()) {
                node = result.elements().get(element);
            }
        }
        TreeItem<PomNode> item = node == null ? null : items.get(node);
        if (item == null) {
            caretItem = null;
            tree.getSelectionModel().clearSelection();
            return;
        }
        if (item == caretItem) {
            return;
        }
        caretItem = item;
        for (TreeItem<PomNode> ancestor = item.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.setExpanded(true);
        }
        tree.getSelectionModel().select(item);
        TreeScrolling.scrollIntoView(tree, item);
    }

    /** Shows the selected row's element in the POM defining it: the project's own when it does, else the nearest parent. */
    private void showSelected() {
        TreeItem<PomNode> selected = tree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null) {
            PomNode node = selected.getValue();
            showInPom(node, node.definedInCurrent() ? 0 : node.firstDefinedLayer());
        }
    }

    /** Opens the POM of layer {@code layer} at the element {@code node} stands for. FX thread. */
    private void showInPom(PomNode node, int layer) {
        if (result == null || layer < 0 || layer >= result.layers().size()) {
            return;
        }
        Result shown = result;
        PomLayer pomLayer = shown.layers().get(layer);
        FileObject file = layer == 0 ? shown.file() : pomLayer.file() == null ? null : FileUtil.toFileObject(pomLayer.file());
        if (file == null) {
            return;
        }
        BackgroundScans.EXECUTOR.execute(() -> {
            int offset = 0;
            try {
                String text = layer == 0 ? shown.text() : file.asText().replace("\r\n", "\n").replace('\r', '\n');
                XmlNode element = new PomLocator(text).locate(node, layer);
                offset = element == null ? 0 : element.start();
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not locate " + node + " in " + file.getPath(), ex);
            }
            int at = offset;
            Platform.runLater(() -> {
                ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
                if (contentManager != null) {
                    contentManager.openFile(file, at, at);
                    contentManager.focusActiveEditor();
                }
            });
        });
    }

    /** The <em>Show in POM</em> submenu: one entry per layer, enabled where the row's element is defined. */
    private void fillContextMenu(ContextMenu menu) {
        menu.getItems().clear();
        TreeItem<PomNode> selected = tree.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null || result == null) {
            return;
        }
        PomNode node = selected.getValue();
        Menu show = new Menu(message("ACT_Show"));
        for (int i = 0; i < result.layers().size(); i++) {
            PomLayer layer = result.layers().get(i);
            MenuItem item = new MenuItem(message(i == 0 ? "ACT_Current" : "ACT_PARENT", layer.artifactId()));
            item.setDisable(!node.definedIn(i));
            int index = i;
            item.setOnAction(a -> showInPom(node, index));
            show.getItems().add(item);
        }
        menu.getItems().add(show);
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(PomModelUI.class, key, args);
    }

    /** A row: the icon of the layer the value comes from, the name and value, in bold/italics by provenance; the nodes are reused. */
    private final class PomCell extends TreeCell<PomNode> {

        private final ImageView icon = new ImageView();
        private final Label text = new Label();
        private final HBox row;
        private final Tooltip tooltip = new Tooltip();

        PomCell() {
            getStyleClass().add("navigator-cell");
            icon.setFitWidth(NavigatorIcons.SIZE);
            icon.setFitHeight(NavigatorIcons.SIZE);
            icon.setPreserveRatio(true);
            text.getStyleClass().add("navigator-text");
            row = new HBox(4, icon, text);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(PomNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            text.setText(label(node));
            boolean inherited = !node.definedInCurrent() && (node.kind() != PomNode.Kind.LIST || node.definesValue());
            text.setStyle((node.overridesParent() ? "-fx-font-weight: bold;" : "") + (inherited ? "-fx-font-style: italic;" : ""));
            int level = node.firstDefinedLayer();
            icon.setImage(NavigatorIcons.image(PomModelUI.class,
                    LEVEL_ICONS[level < 0 ? LEVEL_ICONS.length - 1 : Math.min(level, LEVEL_ICONS.length - 1)]));
            setGraphic(row);
            tooltip.setText(tooltip(node));
            setTooltip(tooltip);
        }

        private String label(PomNode node) {
            if (node.kind() == PomNode.Kind.FIELD) {
                String value = node.firstValue();
                return node.displayName() + " : " + (value != null ? value : message("UNDEFINED"));
            }
            return node.definesValue() ? node.displayName() : node.displayName() + " " + message("UNDEFINED");
        }

        private String tooltip(PomNode node) {
            StringBuilder text = new StringBuilder(message("TOOLTIP_Defined_in"));
            boolean field = node.kind() == PomNode.Kind.FIELD;
            for (int i = 0; i < node.layerCount() && i < result.layers().size(); i++) {
                text.append('\n').append(result.layers().get(i).artifactId()).append(": ");
                if (field) {
                    String value = node.valueAt(i);
                    text.append(value != null ? value : message("UNDEFINED"));
                } else {
                    text.append(message(node.definedIn(i) ? "TOOLTIP_YES" : "TOOLTIP_NO"));
                }
            }
            return text.toString();
        }
    }
}
