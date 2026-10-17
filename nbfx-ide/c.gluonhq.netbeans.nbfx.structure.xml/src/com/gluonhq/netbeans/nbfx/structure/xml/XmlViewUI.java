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
package com.gluonhq.netbeans.nbfx.structure.xml;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.structure.NavigatorButtons;
import com.gluonhq.netbeans.nbfx.structure.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TreeScrolling;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * The XML View: the tree of an XML document's elements, processing instructions, declarations and
 * CDATA sections, after NetBeans' {@code xml.text} navigator. A row shows the element name, its
 * attributes in grey and its text content in parentheses; the two can be hidden with the filters
 * along the bottom. A malformed element is shown in red, and the ancestors of one carry an error
 * badge. Clicking a row moves the editor's caret to the node, double-clicking (or Enter) opens the
 * file there and focuses the editor; the editor's caret keeps the row it is in selected.
 */
final class XmlViewUI extends BorderPane implements ScanningPanel<XmlViewUI.Result> {

    /** The scan of a file: its document node. */
    record Result(FileObject file, XmlNode document) {
    }

    private static final String SHOW_ATTRIBUTES = "showAttributes";
    private static final String SHOW_CONTENT = "showContent";
    private static final int ATTRIBUTES_MAX_LENGTH = 100;
    private static final int TEXT_MAX_LENGTH = 50;

    private final Preferences prefs = NbPreferences.forModule(XmlViewUI.class);
    private final BooleanProperty showAttributes = new SimpleBooleanProperty(this, SHOW_ATTRIBUTES, true);
    private final BooleanProperty showContent = new SimpleBooleanProperty(this, SHOW_CONTENT, true);
    private final TreeView<XmlNode> tree = new TreeView<>();
    private final TreeItem<XmlNode> root = new TreeItem<>();
    private final Map<XmlNode, TreeItem<XmlNode>> items = new HashMap<>();
    private Result result;
    private FileObject shownFile;
    private TreeItem<XmlNode> caretItem;

    XmlViewUI() {
        showAttributes.set(prefs.getBoolean(SHOW_ATTRIBUTES, true));
        showContent.set(prefs.getBoolean(SHOW_CONTENT, true));
        showAttributes.subscribe(on -> {
            prefs.putBoolean(SHOW_ATTRIBUTES, on);
            tree.refresh();
        });
        showContent.subscribe(on -> {
            prefs.putBoolean(SHOW_CONTENT, on);
            tree.refresh();
        });

        root.setExpanded(true);
        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setMinHeight(0);
        tree.getStyleClass().add("navigator-tree");
        tree.setCellFactory(view -> new XmlCell());
        tree.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            TreeItem<XmlNode> selected = tree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) {
                return;
            }
            if (e.getClickCount() == 2) {
                open(selected.getValue(), true);
            } else if (e.getClickCount() == 1 && e.getTarget() instanceof Node target && isOnCell(target)) {
                open(selected.getValue(), false);
            }
        });
        tree.setOnKeyPressed(e -> {
            TreeItem<XmlNode> selected = tree.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && selected != null && selected.getValue() != null) {
                open(selected.getValue(), true);
                e.consume();
            }
        });
        ContextMenu contextMenu = new ContextMenu();
        CheckMenuItem attributesItem = new CheckMenuItem(message("LBL_ShowAttributes"));
        attributesItem.selectedProperty().bindBidirectional(showAttributes);
        CheckMenuItem contentItem = new CheckMenuItem(message("LBL_ShowContent"));
        contentItem.selectedProperty().bindBidirectional(showContent);
        contextMenu.getItems().addAll(attributesItem, contentItem);
        tree.setContextMenu(contextMenu);

        StackPane center = new StackPane(tree);
        center.setMinHeight(0);
        setCenter(center);
        setBottom(createFilterBar());
        setMinHeight(0);
    }

    private Node createFilterBar() {
        Label label = new Label(message("LBL_Filter"));
        ToggleButton attributes = NavigatorButtons.toggle(NavigatorIcons.view(XmlViewUI.class, "icons/a.png"),
                message("LBL_ShowAttributesTip"));
        attributes.selectedProperty().bindBidirectional(showAttributes);
        ToggleButton content = NavigatorButtons.toggle(NavigatorIcons.view(XmlViewUI.class, "icons/content.png"),
                message("LBL_ShowContentTip"));
        content.selectedProperty().bindBidirectional(showContent);
        return NavigatorButtons.filterBar(label, attributes, content);
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Result scan(FileObject file, String text, Cancellation cancellation) {
        XmlNode document = XmlScanner.scan(text, cancellation);
        return document == null ? null : new Result(file, document);
    }

    @Override
    public void show(Result newResult) {
        Set<String> expanded = newResult != null && newResult.file().equals(shownFile) ? expandedKeys() : null;
        result = newResult;
        shownFile = newResult == null ? null : newResult.file();
        caretItem = null;
        items.clear();
        root.getChildren().clear();
        if (newResult == null) {
            return;
        }
        for (XmlNode node : newResult.document().children()) {
            TreeItem<XmlNode> item = createItem(node, expanded);
            root.getChildren().add(item);
            // The top-level nodes open at first, as NetBeans does; a rescan keeps what the user set.
            if (expanded == null && !item.getChildren().isEmpty()) {
                item.setExpanded(true);
            }
        }
    }

    private TreeItem<XmlNode> createItem(XmlNode node, Set<String> expanded) {
        TreeItem<XmlNode> item = new TreeItem<>(node);
        items.put(node, item);
        for (XmlNode child : node.children()) {
            item.getChildren().add(createItem(child, expanded));
        }
        if (expanded != null && expanded.contains(node.pathKey())) {
            item.setExpanded(true);
        }
        return item;
    }

    private Set<String> expandedKeys() {
        Set<String> keys = new HashSet<>();
        collectExpanded(root, keys);
        return keys;
    }

    private static void collectExpanded(TreeItem<XmlNode> item, Set<String> keys) {
        for (TreeItem<XmlNode> child : item.getChildren()) {
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
        root.getChildren().clear();
    }

    @Override
    public void selectAt(int offset) {
        XmlNode node = result == null || offset < 0 ? null : result.document().nodeAt(offset);
        TreeItem<XmlNode> item = node == null ? null : items.get(node);
        if (item == null) {
            caretItem = null;
            tree.getSelectionModel().clearSelection();
            return;
        }
        if (item == caretItem) {
            return;
        }
        caretItem = item;
        for (TreeItem<XmlNode> ancestor = item.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.setExpanded(true);
        }
        tree.getSelectionModel().select(item);
        TreeScrolling.scrollIntoView(tree, item);
    }

    /**
     * Shows {@code node} in the editor: moves the caret of the file's open editor to it, or, with
     * {@code focus}, opens the file there and focuses the editor.
     */
    private void open(XmlNode node, boolean focus) {
        if (result == null) {
            return;
        }
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager == null) {
            return;
        }
        if (focus) {
            contentManager.openFile(result.file(), node.start(), node.start());
            contentManager.focusActiveEditor();
        } else {
            EditorDocument document = contentManager.documentForFile(result.file());
            if (document != null) {
                document.selectRange(node.start(), node.start());
            }
        }
    }

    private static boolean isOnCell(Node target) {
        for (Node node = target; node != null; node = node.getParent()) {
            if (node instanceof TreeCell<?> cell) {
                return !cell.isEmpty();
            }
        }
        return false;
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(XmlViewUI.class, key, args);
    }

    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    /** A row: the node's icon (badged when a descendant is malformed), name, attributes and content; the nodes are reused. */
    private final class XmlCell extends TreeCell<XmlNode> {

        private final ImageView icon = new ImageView();
        private final ImageView badge = new ImageView(NavigatorIcons.image(XmlViewUI.class, "icons/badge_error.png"));
        private final Label name = new Label();
        private final Label detail = new Label();
        private final Label content = new Label();
        private final HBox row;
        private final Tooltip tooltip = new Tooltip();

        XmlCell() {
            getStyleClass().add("navigator-cell");
            icon.setFitWidth(NavigatorIcons.SIZE);
            icon.setFitHeight(NavigatorIcons.SIZE);
            icon.setPreserveRatio(true);
            badge.setFitWidth(8);
            badge.setFitHeight(8);
            badge.setPreserveRatio(true);
            StackPane iconBox = new StackPane(icon, badge);
            StackPane.setAlignment(badge, Pos.BOTTOM_RIGHT);
            iconBox.setMinWidth(NavigatorIcons.SIZE);
            detail.getStyleClass().add("navigator-detail");
            content.getStyleClass().add("navigator-text");
            HBox label = new HBox(name, detail, content);
            label.setAlignment(Pos.BASELINE_LEFT);
            row = new HBox(4, iconBox, label);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(XmlNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            icon.setImage(NavigatorIcons.image(XmlViewUI.class, "icons/" + iconName(node)));
            badge.setVisible(node.descendantErrors() > 0);
            name.getStyleClass().setAll("label", node.hasError() ? "navigator-error" : "navigator-text");
            if (node.kind() == XmlNode.Kind.ELEMENT) {
                name.setText(node.name());
                String attributes = showAttributes.get() ? attributesText(node) : "";
                detail.setText(attributes.isBlank() ? "" : " " + clip(attributes, ATTRIBUTES_MAX_LENGTH));
                String text = showContent.get() ? node.text() : "";
                content.setText(text.isBlank() ? "" : " (" + clip(text, TEXT_MAX_LENGTH) + ")");
            } else {
                name.setText(clip(node.text().isBlank() ? node.name() : node.text(), TEXT_MAX_LENGTH));
                detail.setText("");
                content.setText("");
            }
            setGraphic(row);
            String tip = tooltip(node);
            tooltip.setText(tip);
            setTooltip(tip.isBlank() ? null : tooltip);
        }

        private String tooltip(XmlNode node) {
            return switch (node.kind()) {
                case ELEMENT -> (attributesText(node) + " " + node.text()).trim();
                case PI -> (node.name() + " " + node.text()).trim();
                default -> node.text();
            };
        }
    }

    private static String iconName(XmlNode node) {
        return switch (node.kind()) {
            case PI -> "xml_declaration.png";
            case DOCTYPE -> "doc_type.png";
            case CDATA -> "cdata.png";
            default -> "tag.png";
        };
    }

    private static String attributesText(XmlNode node) {
        StringBuilder text = new StringBuilder();
        node.attributes().forEach((name, value) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name).append("=\"").append(value).append('"');
        });
        return text.toString();
    }
}
