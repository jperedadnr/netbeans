package com.gluonhq.netbeans.nbfx.editor.codearea.search;

import java.util.Objects;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.openide.util.NbBundle;

/**
 * The Find row of the editor's search sidebar, NetBeans' {@code SearchBar}: the query field with
 * its history, Previous / Next, the Match Case / Whole Words / Regular Expression / Highlight /
 * Wrap Around toggles, the match counter and a close button. Pure view - {@link SearchSupport}
 * wires it to the editor and to the shared {@link SearchHistory}.
 * <p>
 * Pseudo-class {@code not-found} is set on the bar while the query has no match (the field turns
 * red), {@code invalid} while it is a malformed regular expression.
 */
public final class SearchBar extends HBox {

    static final PseudoClass NOT_FOUND = PseudoClass.getPseudoClass("not-found");
    static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    static final PseudoClass REPLACE = PseudoClass.getPseudoClass("replace");

    private final ComboBox<String> field = new ComboBox<>();
    private final Button previous = button("find_previous", "TIP_FindPrevious");
    private final Button next = button("find_next", "TIP_FindNext");
    private final ToggleButton matchCase = toggle("matchCase", "TIP_MatchCase");
    private final ToggleButton wholeWords = toggle("wholeWord", "TIP_WholeWords");
    private final ToggleButton regex = toggle("regexp", "TIP_Regex");
    private final ToggleButton highlight = toggle("highlight", "TIP_Highlight");
    private final ToggleButton wrapAround = toggle("wrapAround", "TIP_WrapAround");
    private final Label counter = new Label();
    private final Label label = new Label();
    private final Button close = new Button("\u2715");

    SearchBar(ObservableList<String> history) {
        getStyleClass().add("search-bar");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(0);

        label.getStyleClass().add("search-label");
        label.setLabelFor(field);

        field.setEditable(true);
        // A snapshot, refreshed when the popup opens: the live history is reordered on every Find
        // Next, and the combo's selection model would follow that reorder by index and swap the
        // query for a neighbouring entry.
        field.setOnShowing(e -> field.getItems().setAll(history));
        field.getStyleClass().add("search-field");
        field.setTooltip(new Tooltip(message("TIP_FindField")));
        field.setVisibleRowCount(10);

        counter.getStyleClass().add("search-counter");
        counter.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMinWidth(0);

        close.getStyleClass().add("search-close");
        close.setFocusTraversable(false);
        close.setTooltip(new Tooltip(message("TIP_Close")));

        getChildren().addAll(label, field, previous, next, new Separator(Orientation.VERTICAL),
                matchCase, wholeWords, regex, highlight, wrapAround, spacer, counter, close);
    }

    // --- the parts SearchSupport wires -------------------------------------------------------

    Label label() {
        return label;
    }

    void setReplaceMode(boolean replace) {
        label.setText(message(replace ? "LBL_FindWhat" : "LBL_Find"));
        label.pseudoClassStateChanged(REPLACE, replace);
    }

    ComboBox<String> field() {
        return field;
    }

    /** The editable field's text control - where typing and Enter / Esc happen. */
    TextField editor() {
        return field.getEditor();
    }

    Button previousButton() {
        return previous;
    }

    Button nextButton() {
        return next;
    }

    ToggleButton matchCaseToggle() {
        return matchCase;
    }

    ToggleButton wholeWordsToggle() {
        return wholeWords;
    }

    ToggleButton regexToggle() {
        return regex;
    }

    ToggleButton highlightToggle() {
        return highlight;
    }

    ToggleButton wrapAroundToggle() {
        return wrapAround;
    }

    Button closeButton() {
        return close;
    }

    /** The current query as typed. */
    public String getQuery() {
        return editor().getText() == null ? "" : editor().getText();
    }

    /** Sets the query text (without committing it to the history) and selects it for retyping. */
    void setQuery(String query) {
        editor().setText(query == null ? "" : query);
        editor().selectAll();
    }

    /** The counter text - "3 of 12 matches", "No matches", ... - for tests and the status. */
    public String getCounterText() {
        return counter.getText();
    }

    void setCounter(String text) {
        counter.setText(text == null ? "" : text);
    }

    void setNotFound(boolean notFound) {
        pseudoClassStateChanged(NOT_FOUND, notFound);
        editor().pseudoClassStateChanged(NOT_FOUND, notFound);
    }

    void setInvalid(boolean invalid) {
        pseudoClassStateChanged(INVALID, invalid);
        editor().pseudoClassStateChanged(INVALID, invalid);
    }

    // --- helpers ------------------------------------------------------------------------------

    static String message(String key, Object... args) {
        return NbBundle.getMessage(SearchBar.class, key, args);
    }

    private static Button button(String icon, String tooltipKey) {
        Button button = new Button();
        decorate(button, icon, tooltipKey);
        return button;
    }

    private static ToggleButton toggle(String icon, String tooltipKey) {
        ToggleButton button = new ToggleButton();
        decorate(button, icon, tooltipKey);
        return button;
    }

    private static void decorate(ButtonBase button, String icon, String tooltipKey) {
        ImageView imageView = new ImageView(new Image(Objects.requireNonNull(
                SearchBar.class.getResource(icon + ".png")).toExternalForm()));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        imageView.setPreserveRatio(true);
        button.setGraphic(imageView);
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.getStyleClass().add("search-tool-button");
        button.setFocusTraversable(false);
    }
}
