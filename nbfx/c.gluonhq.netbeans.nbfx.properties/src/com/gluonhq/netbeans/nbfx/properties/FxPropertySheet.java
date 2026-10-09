package com.gluonhq.netbeans.nbfx.properties;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * A pure-JavaFX property sheet: a two-column tree (Name / Value) whose branches are the
 * {@link FxPropertySet}s and whose leaves are the {@link FxProperty properties}, with an expert
 * toggle and per-type inline editors from {@link FxPropertyEditors}.
 *
 * <p>Reused for Project Properties, File Properties and the future FXML component inspector. The
 * caller sets the context object; the sheet asks every registered {@link FxPropertiesProvider} for
 * its property sets.</p>
 */
public final class FxPropertySheet extends BorderPane {

    private final TreeTableView<Object> tree = new TreeTableView<>();
    private final CheckBox expert = new CheckBox(message("FxPropertySheet.expert"));
    private Object context;
    private boolean showExpert;

    public FxPropertySheet() {
        TreeTableColumn<Object, String> nameColumn = new TreeTableColumn<>(message("FxPropertySheet.name"));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(displayName(cell.getValue().getValue())));
        nameColumn.setPrefWidth(180);

        TreeTableColumn<Object, Object> valueColumn = new TreeTableColumn<>(message("FxPropertySheet.value"));
        valueColumn.setCellValueFactory(cell -> {
            Object value = cell.getValue().getValue();
            return value instanceof FxProperty property
                    ? property.valueObservable()
                    : new ReadOnlyObjectWrapper<>();
        });
        valueColumn.setCellFactory(column -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                TreeItem<Object> row = getTreeTableRow() == null ? null : getTreeTableRow().getTreeItem();
                Object value = row == null ? null : row.getValue();
                if (value instanceof FxProperty property) {
                    setGraphic(FxPropertyEditors.createEditor(property));
                    setText(null);
                } else {
                    setGraphic(null);
                    setText(null);
                }
            }
        });

        tree.getColumns().add(nameColumn);
        tree.getColumns().add(valueColumn);
        tree.setShowRoot(false);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        expert.selectedProperty().addListener((observable, old, now) -> {
            showExpert = now;
            rebuild();
        });
        setTop(expert);
        setCenter(tree);
    }

    /** Sets the context object whose properties are shown, and rebuilds the sheet. */
    public void setContext(Object context) {
        this.context = context;
        rebuild();
    }

    private void rebuild() {
        TreeItem<Object> root = new TreeItem<>();
        if (context != null) {
            for (FxPropertiesProvider provider : Lookup.getDefault().lookupAll(FxPropertiesProvider.class)) {
                for (FxPropertySet set : provider.getPropertySets(context)) {
                    TreeItem<Object> setItem = new TreeItem<>(set);
                    for (FxProperty property : set.getProperties()) {
                        if (property.isExpert() && !showExpert) {
                            continue;
                        }
                        setItem.getChildren().add(new TreeItem<>(property));
                    }
                    root.getChildren().add(setItem);
                }
            }
        }
        tree.setRoot(root);
    }

    private static String displayName(Object value) {
        if (value instanceof FxPropertySet set) {
            return set.getDisplayName();
        }
        if (value instanceof FxProperty property) {
            return property.getDisplayName();
        }
        return "";
    }

    private static String message(String key) {
        return NbBundle.getMessage(FxPropertySheet.class, key);
    }
}
