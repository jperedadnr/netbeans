package com.gluonhq.netbeans.nbfx.editor.codearea.search;

import java.util.Objects;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

/**
 * The Replace row of the editor's search sidebar, NetBeans' {@code ReplaceBar}: shown under the
 * {@link SearchBar} with the replacement field and its history, Replace / Replace All, and the
 * Backwards / Preserve Case options. Pure view - {@link SearchSupport} wires it. Shares the
 * {@code search-bar} style class (and so the Find row's styling) plus {@code replace-bar}.
 */
public final class ReplaceBar extends HBox {

    private final ComboBox<String> field = new ComboBox<>();
    private final Button replace = new Button();
    private final Button replaceAll = new Button();
    private final Label label = new Label(SearchBar.message("LBL_ReplaceWith"));
    private final CheckBox backwards = new CheckBox(SearchBar.message("LBL_Backwards"));
    private final CheckBox preserveCase = new CheckBox(SearchBar.message("LBL_PreserveCase"));

    ReplaceBar(ObservableList<String> history) {
        getStyleClass().addAll("search-bar", "replace-bar");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(0);

        label.getStyleClass().add("search-label");
        label.setLabelFor(field);

        field.setEditable(true);
        field.setOnShowing(e -> field.getItems().setAll(history));
        field.getStyleClass().add("search-field");
        field.setTooltip(new Tooltip(SearchBar.message("TIP_ReplaceField")));
        field.setVisibleRowCount(10);

        decorate(replace, "replace", "TIP_Replace");
        decorate(replaceAll, "replace_all", "TIP_ReplaceAll");
        backwards.getStyleClass().add("search-check");
        backwards.setTooltip(new Tooltip(SearchBar.message("TIP_Backwards")));
        backwards.setFocusTraversable(false);
        preserveCase.getStyleClass().add("search-check");
        preserveCase.setTooltip(new Tooltip(SearchBar.message("TIP_PreserveCase")));
        preserveCase.setFocusTraversable(false);

        getChildren().addAll(label, field, replace, replaceAll, new Separator(Orientation.VERTICAL),
                backwards, preserveCase);
    }

    Label label() {
        return label;
    }

    ComboBox<String> field() {
        return field;
    }

    TextField editor() {
        return field.getEditor();
    }

    Button replaceButton() {
        return replace;
    }

    Button replaceAllButton() {
        return replaceAll;
    }

    CheckBox backwardsCheck() {
        return backwards;
    }

    CheckBox preserveCaseCheck() {
        return preserveCase;
    }

    /** The replacement as typed. */
    public String getReplacement() {
        return editor().getText() == null ? "" : editor().getText();
    }

    void setReplacement(String text) {
        editor().setText(text == null ? "" : text);
    }

    private static void decorate(Button button, String icon, String tooltipKey) {
        ImageView imageView = new ImageView(new Image(Objects.requireNonNull(
                SearchBar.class.getResource(icon + ".png")).toExternalForm()));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        imageView.setPreserveRatio(true);
        button.setGraphic(imageView);
        button.setTooltip(new Tooltip(SearchBar.message(tooltipKey)));
        button.getStyleClass().add("search-tool-button");
        button.setFocusTraversable(false);
    }
}
