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
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.actions.ElementContextMenuContributor;
import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import com.gluonhq.netbeans.nbfx.api.view.CellBreadth;
import com.gluonhq.netbeans.nbfx.javanavigator.model.BeanPattern;
import com.gluonhq.netbeans.nbfx.javanavigator.model.BeanPatternsScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

import static com.gluonhq.netbeans.nbfx.javanavigator.ui.MembersPanel.message;

/**
 * The Bean Patterns view of the Navigator, after NetBeans' {@code BeanPanelUI}: the classes of the
 * edited file with the properties, indexed properties and event sets their methods form. Double-click
 * or Enter opens the pattern at its getter (or setter, add method, type); the row enclosing the
 * editor's caret is kept selected; the row menu offers Go to Source and what other modules contribute.
 */
final class BeanPatternsPanel extends StackPane implements NavigatorPanel<BeanPatternsScanner.Result> {

    private final TreeView<BeanPattern> tree = new TreeView<>();
    private final CellBreadth breadth = new CellBreadth();
    private final TreeItem<BeanPattern> root = new TreeItem<>();
    private final Label empty = new Label(message("LBL_NoBeanPatternsAvail"));
    private BeanPatternsScanner.Result result;
    private Set<String> knownKeys = Set.of();
    private TreeItem<BeanPattern> caretItem;
    private String caretKey;

    BeanPatternsPanel() {
        getStyleClass().add("bean-patterns-panel");
        root.setExpanded(true);
        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setMinHeight(0);
        tree.getStyleClass().add("members-tree");
        tree.setCellFactory(view -> new BeanPatternTreeCell(breadth));
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
        tree.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> fillContextMenu(contextMenu));
        fillContextMenu(contextMenu);
        tree.setContextMenu(contextMenu);

        empty.getStyleClass().add("navigator-empty");
        StackPane.setAlignment(empty, Pos.CENTER);
        getChildren().addAll(tree, empty);
        setMinHeight(0);
        updateEmpty();
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public BeanPatternsScanner.Result scan(FileObject file, String text, Cancellation cancellation) throws IOException {
        return BeanPatternsScanner.scan(file, text, cancellation);
    }

    @Override
    public void show(BeanPatternsScanner.Result newResult) {
        boolean sameFile = result != null && newResult != null && result.file().equals(newResult.file());
        Set<String> expanded = sameFile ? expandedKeys() : null;
        result = newResult;
        rebuild(expanded);
    }

    @Override
    public void clear() {
        result = null;
        rebuild(null);
    }

    @Override
    public void selectAt(int offset) {
        TreeItem<BeanPattern> item = enclosing(root, offset);
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
        for (TreeItem<BeanPattern> ancestor = item.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.setExpanded(true);
        }
        tree.getSelectionModel().select(item);
        TreeScrolling.scrollIntoView(tree, item);
    }

    @Override
    public void requestFocus() {
        tree.requestFocus();
    }

    private TreeItem<BeanPattern> enclosing(TreeItem<BeanPattern> parent, int offset) {
        for (TreeItem<BeanPattern> item : parent.getChildren()) {
            BeanPattern pattern = item.getValue();
            if (pattern != null && pattern.contains(offset)) {
                TreeItem<BeanPattern> deeper = enclosing(item, offset);
                return deeper != null ? deeper : item;
            }
        }
        return null;
    }

    private BeanPattern selectedPattern() {
        TreeItem<BeanPattern> selected = tree.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getValue();
    }

    private void openSelected() {
        BeanPattern pattern = selectedPattern();
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (pattern != null && result != null && pattern.getOpenOffset() >= 0 && contentManager != null) {
            contentManager.openFile(result.file(), pattern.getOpenOffset(), pattern.getOpenOffset());
        }
    }

    /** Go to Source, then the items other modules contribute for the pattern's declaration. */
    private void fillContextMenu(ContextMenu menu) {
        BeanPattern pattern = selectedPattern();
        List<MenuItem> items = new ArrayList<>();
        MenuItem goToSource = new MenuItem(message("CTX_GoToSource"));
        goToSource.setDisable(pattern == null || pattern.getOpenOffset() < 0);
        goToSource.setOnAction(a -> openSelected());
        items.add(goToSource);
        if (pattern != null && result != null && pattern.getOpenOffset() >= 0) {
            CompletableFuture<SourceLocation> location = CompletableFuture.completedFuture(
                    new SourceLocation(result.file(), pattern.getOpenOffset()));
            for (ElementContextMenuContributor contributor : Lookup.getDefault().lookupAll(ElementContextMenuContributor.class)) {
                items.addAll(contributor.itemsFor(location));
            }
        }
        menu.getItems().setAll(items);
    }

    // -- tree ----------------------------------------------------------------------------------

    private void rebuild(Set<String> expanded) {
        BeanPattern selected = selectedPattern();
        Set<String> keys = new HashSet<>();
        List<TreeItem<BeanPattern>> items = result == null ? List.of() : items(result.roots(), expanded, keys);
        breadth.reset();
        root.getChildren().setAll(items);
        knownKeys = keys;
        caretItem = caretKey == null ? null : find(root, caretKey);
        updateEmpty();
        if (selected != null) {
            TreeItem<BeanPattern> again = find(root, selected.getKey());
            if (again != null) {
                tree.getSelectionModel().select(again);
            }
        }
    }

    private List<TreeItem<BeanPattern>> items(List<BeanPattern> patterns, Set<String> expanded, Set<String> keys) {
        List<TreeItem<BeanPattern>> items = new ArrayList<>();
        for (BeanPattern pattern : patterns) {
            TreeItem<BeanPattern> item = new TreeItem<>(pattern);
            keys.add(pattern.getKey());
            if (pattern.getKind() == BeanPattern.Kind.CLASS) {
                item.getChildren().setAll(items(pattern.getChildren(), expanded, keys));
                item.setExpanded(expanded == null || expanded.contains(pattern.getKey()) || !knownKeys.contains(pattern.getKey()));
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

    private static void collectExpanded(TreeItem<BeanPattern> parent, Set<String> keys) {
        for (TreeItem<BeanPattern> item : parent.getChildren()) {
            if (item.isExpanded() && item.getValue() != null) {
                keys.add(item.getValue().getKey());
            }
            collectExpanded(item, keys);
        }
    }

    private static TreeItem<BeanPattern> find(TreeItem<BeanPattern> parent, String key) {
        for (TreeItem<BeanPattern> item : parent.getChildren()) {
            if (item.getValue() != null && key.equals(item.getValue().getKey())) {
                return item;
            }
            TreeItem<BeanPattern> found = find(item, key);
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
}
