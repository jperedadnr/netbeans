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
package com.gluonhq.netbeans.nbfx.structure.css;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.structure.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TreeScrolling;
import com.gluonhq.netbeans.nbfx.structure.css.CssScanner.Item;
import com.gluonhq.netbeans.nbfx.structure.css.CssScanner.Structure;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The tree of a style sheet, after NetBeans' CSS navigator: a group row per kind of item the sheet
 * has - <em>Classes</em> and <em>Rules</em> for a typical sheet; <em>Ids</em>, <em>Elements</em>,
 * <em>At-Rules</em> and <em>Imports</em> when present - with the items sorted by name under it.
 * Double-clicking an item (or Enter) opens the sheet at it; the editor's caret keeps the rule it is
 * in selected.
 */
final class CssStructureUI extends StackPane implements ScanningPanel<CssStructureUI.Result> {

    /** The scan of a file: its structure. */
    record Result(FileObject file, Structure structure) {
    }

    /** A tree row: a group, or an item of one. */
    record Row(String group, Item item) {

        boolean isGroup() {
            return item == null;
        }

        String text() {
            return item == null ? group : item.name();
        }
    }

    private static final Comparator<Item> BY_NAME = Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(Item::start);

    private final TreeView<Row> tree = new TreeView<>();
    private final TreeItem<Row> root = new TreeItem<>();
    private final Map<Item, TreeItem<Row>> ruleItems = new HashMap<>();
    private Result result;
    private FileObject shownFile;
    private TreeItem<Row> caretItem;

    CssStructureUI() {
        root.setExpanded(true);
        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setMinHeight(0);
        tree.getStyleClass().add("navigator-tree");
        tree.setCellFactory(view -> new RowCell());
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
        getChildren().add(tree);
        setMinHeight(0);
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Result scan(FileObject file, String text, Cancellation cancellation) {
        Structure structure = CssScanner.scan(text, cancellation);
        return structure == null ? null : new Result(file, structure);
    }

    @Override
    public void show(Result newResult) {
        Set<String> collapsed = newResult != null && newResult.file().equals(shownFile) ? collapsedGroups() : Set.of();
        result = newResult;
        shownFile = newResult == null ? null : newResult.file();
        caretItem = null;
        ruleItems.clear();
        root.getChildren().clear();
        if (newResult == null) {
            return;
        }
        Structure structure = newResult.structure();
        List<TreeItem<Row>> groups = new ArrayList<>();
        addGroup(groups, message("AtRules"), structure.atRules());
        addGroup(groups, message("Classes"), structure.classes());
        addGroup(groups, message("Elements"), structure.elements());
        addGroup(groups, message("Ids"), structure.ids());
        addGroup(groups, message("Imports"), structure.imports());
        addGroup(groups, message("Rules"), structure.rules());
        groups.sort(Comparator.comparing(group -> group.getValue().group(), String.CASE_INSENSITIVE_ORDER));
        for (TreeItem<Row> group : groups) {
            // Groups open at first, as NetBeans' do; a rescan keeps the ones the user closed closed.
            group.setExpanded(!collapsed.contains(group.getValue().group()));
            root.getChildren().add(group);
        }
    }

    private void addGroup(List<TreeItem<Row>> groups, String name, List<Item> items) {
        if (items.isEmpty()) {
            return;
        }
        TreeItem<Row> group = new TreeItem<>(new Row(name, null));
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(BY_NAME);
        for (Item item : sorted) {
            TreeItem<Row> row = new TreeItem<>(new Row(name, item));
            group.getChildren().add(row);
            if (items == result.structure().rules()) {
                ruleItems.put(item, row);
            }
        }
        groups.add(group);
    }

    private Set<String> collapsedGroups() {
        Set<String> names = new HashSet<>();
        for (TreeItem<Row> group : root.getChildren()) {
            if (!group.isExpanded()) {
                names.add(group.getValue().group());
            }
        }
        return names;
    }

    @Override
    public void clear() {
        result = null;
        shownFile = null;
        caretItem = null;
        ruleItems.clear();
        root.getChildren().clear();
    }

    @Override
    public void selectAt(int offset) {
        Item rule = result == null || offset < 0 ? null : result.structure().ruleAt(offset);
        TreeItem<Row> item = rule == null ? null : ruleItems.get(rule);
        if (item == null) {
            caretItem = null;
            tree.getSelectionModel().clearSelection();
            return;
        }
        if (item == caretItem) {
            return;
        }
        caretItem = item;
        item.getParent().setExpanded(true);
        tree.getSelectionModel().select(item);
        TreeScrolling.scrollIntoView(tree, item);
    }

    private void openSelected() {
        TreeItem<Row> selected = tree.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null || selected.getValue().isGroup() || result == null) {
            return;
        }
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager != null) {
            int start = selected.getValue().item().start();
            contentManager.openFile(result.file(), start, start);
            contentManager.focusActiveEditor();
        }
    }

    static String message(String key) {
        return NbBundle.getMessage(CssStructureUI.class, key);
    }

    /** A row: the group's package icon or the item's rule icon, and its name; the icon view is reused. */
    private static final class RowCell extends TreeCell<Row> {

        private final ImageView icon = new ImageView();
        private final Tooltip tooltip = new Tooltip();

        RowCell() {
            getStyleClass().add("navigator-cell");
            icon.setFitWidth(NavigatorIcons.SIZE);
            icon.setFitHeight(NavigatorIcons.SIZE);
            icon.setPreserveRatio(true);
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            setText(row.text());
            icon.setImage(NavigatorIcons.image(CssStructureUI.class, row.isGroup() ? "icons/package.png" : "icons/rule.png"));
            setGraphic(icon);
            tooltip.setText(row.text());
            setTooltip(row.isGroup() ? null : tooltip);
        }
    }
}
