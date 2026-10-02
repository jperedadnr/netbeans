package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.ComboBoxListViewSkin;

/**
 * The <b>Containing Text</b> field: a combo box whose display node is a {@link TextArea}, so the
 * query can hold the line breaks a multi-line search needs while the drop-down still offers the
 * search history. The area is one row high - a text field - and grows with the text, line by line,
 * up to {@link #MAX_ROWS} rows; {@link #rowsProperty()} lets the dialog grow with it instead of
 * letting the area scroll.
 */
final class SearchTextCombo extends ComboBox<String> {

    /** The number of rows the area grows to before it scrolls, as NetBeans' multi-line field. */
    static final int MAX_ROWS = 6;

    private final TextArea area = new TextArea();
    private final ReadOnlyIntegerWrapper rows = new ReadOnlyIntegerWrapper(this, "rows", 1);

    SearchTextCombo() {
        getStyleClass().add("search-text-combo");
        setMaxWidth(Double.MAX_VALUE);

        area.getStyleClass().add("search-text");
        area.setPrefRowCount(1);
        area.setWrapText(false);
        area.textProperty().subscribe(text -> {
            int lines = text == null ? 1 : (int) text.chars().filter(c -> c == '\n').count() + 1;
            rows.set(Math.clamp(lines, 1, MAX_ROWS));
            area.setPrefRowCount(rows.get());
        });

        valueProperty().subscribe(picked -> {
            if (picked != null) {
                setText(picked);
                // the query lives in the area: let go of the entry, so it can be picked again
                Platform.runLater(() -> getSelectionModel().clearSelection());
            }
        });
        // a multi-line entry is shown on one line, as NetBeans' history combo does
        setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.replace('\n', '↵'));
            }
        });
        // the skin keeps focus traversal out of its children, so the combo hands the area its focus
        focusedProperty().subscribe(focused -> {
            if (focused) {
                area.requestFocus();
            }
        });
    }

    /** The display node is the area rather than the button cell an editable combo would build. */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new ComboBoxListViewSkin<>(this) {
            @Override
            public Node getDisplayNode() {
                return area;
            }
        };
    }

    /** The area holding the query - what the form installs its key bindings on. */
    TextArea area() {
        return area;
    }

    /** The query, the text of {@link #area()}. */
    StringProperty textProperty() {
        return area.textProperty();
    }

    String getText() {
        return area.getText();
    }

    void setText(String text) {
        area.setText(text == null ? "" : text);
    }

    /** The rows the area shows now, 1 to {@link #MAX_ROWS}; the dialog follows it. */
    ReadOnlyIntegerProperty rowsProperty() {
        return rows.getReadOnlyProperty();
    }

    /** Focuses the area with its content selected, so typing replaces the query. */
    void focusText() {
        area.requestFocus();
        area.selectAll();
    }
}
