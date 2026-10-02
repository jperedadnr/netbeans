package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.query.FileNamePattern;
import com.gluonhq.netbeans.nbfx.findinprojects.query.IgnoreList;
import com.gluonhq.netbeans.nbfx.findinprojects.query.MatchType;
import com.gluonhq.netbeans.nbfx.findinprojects.query.Replacement;
import com.gluonhq.netbeans.nbfx.findinprojects.query.ScopeOptions;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria.Validation;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchScope;
import com.gluonhq.netbeans.nbfx.findinprojects.query.TextPattern;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * The Find in Projects form, laid out as NetBeans' {@code BasicSearchForm}: <b>Containing Text</b>
 * (a {@link SearchTextCombo} - a text area th
at grows line by line, {@code Shift+Enter} adds a
 * line, {@code Enter} searches, and a drop-down holding the search history - with a hint that
 * follows the match type), <b>Match Case</b>, <b>Whole Words</b>,
 * <b>Match</b>; <b>Scope</b> with its three options; <b>File Name Patterns</b> with its hint and
 * <b>File Path Regular Expression</b>. A status line shows {@link SearchCriteria#validate()}: errors
 * in red - the Find button is then disabled - hints in grey. In <em>replace</em> mode the form adds
 * <b>Replace With</b> (with its own history) under the text and <b>Preserve Case when Replacing</b>
 * among the match options, and the criteria carry a {@link Replacement}.
 */
public final class BasicSearchPane extends GridPane {

    static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final FindSettings settings;

    private final SearchTextCombo textCombo = new SearchTextCombo();
    private final Label textHint = new Label();
    private final CheckBox matchCase = new CheckBox(message("LBL_MatchCase"));
    private final CheckBox wholeWords = new CheckBox(message("LBL_WholeWords"));
    private final ComboBox<MatchType> matchType = new ComboBox<>();
    private final boolean replace;
    private final ComboBox<String> replaceField = new ComboBox<>();
    private final CheckBox preserveCase = new CheckBox(message("LBL_PreserveCase"));
    private final ScopeChooser scope = new ScopeChooser();
    private final CheckBox searchInArchives = new CheckBox(message("LBL_SearchInArchives"));
    private final CheckBox searchInGenerated = new CheckBox(message("LBL_SearchInGenerated"));
    private final CheckBox useIgnoreList = new CheckBox(message("LBL_UseIgnoreList"));
    private final ComboBox<String> fileNameField = new ComboBox<>();
    private final Label fileNameHint = new Label();
    private final CheckBox filePathRegex = new CheckBox(message("LBL_FilePathRegex"));
    private final Label status = new Label();

    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper();
    private Runnable onSubmit = () -> { };
    private Supplier<Window> owner = () -> null;

    /**
     * @param settings the remembered options and histories the form starts from
     * @param folders  the folders selected in a tree when the dialog was opened from one, else
     *                 {@code null} or empty
     */
    public BasicSearchPane(FindSettings settings, List<FileObject> folders) {
        this(settings, folders, false);
    }

    /** @param replace whether the form asks for a replacement too (Replace in Projects) */
    public BasicSearchPane(FindSettings settings, List<FileObject> folders, boolean replace) {
        this.settings = Objects.requireNonNull(settings);
        this.replace = replace;
        getStyleClass().add("search-pane");
        setHgap(8);
        setVgap(6);
        setPadding(new Insets(12, 12, 6, 12));

        ColumnConstraints labels = new ColumnConstraints();
        labels.setHalignment(HPos.RIGHT);
        labels.setHgrow(Priority.NEVER);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        getColumnConstraints().addAll(labels, fields);

        buildTextRow();
        if (replace) {
            buildReplaceRow();
        }
        buildMatchRow();
        buildScopeRows();
        buildFileNameRows();
        buildStatusRow();

        scope.populate(folders, settings.getScopeId());
        matchCase.setSelected(settings.isMatchCase());
        wholeWords.setSelected(settings.isWholeWords());
        matchType.setValue(settings.getMatchType());
        ScopeOptions options = settings.getScopeOptions();
        searchInArchives.setSelected(options.searchInArchives());
        searchInGenerated.setSelected(options.searchInGenerated());
        useIgnoreList.setSelected(options.useIgnoreList());
        filePathRegex.setSelected(settings.isFilePathRegex());
        fileNameField.getItems().setAll(settings.getFileNameHistory());
        if (settings.isFileNameSpecified() && !fileNameField.getItems().isEmpty()) {
            fileNameField.getEditor().setText(fileNameField.getItems().getFirst());
        }
        setText(settings.getTextHistory().stream().findFirst().orElse(""));
        if (replace) {
            replaceField.getItems().setAll(settings.getReplaceHistory());
            replaceField.getEditor().setText(settings.getReplaceHistory().stream().findFirst().orElse(""));
            preserveCase.setSelected(settings.isPreserveCase());
        }

        textCombo.textProperty().subscribe(this::revalidate);
        matchType.valueProperty().subscribe(this::revalidate);
        wholeWords.selectedProperty().subscribe(this::revalidate);
        matchCase.selectedProperty().subscribe(this::revalidate);
        fileNameField.getEditor().textProperty().subscribe(this::revalidate);
        filePathRegex.selectedProperty().subscribe(this::revalidate);
        scope.valueProperty().subscribe(this::revalidate);
        revalidate();
    }

    // --- rows --------------------------------------------------------------------------------

    private void buildTextRow() {
        Label label = new Label(message("LBL_ContainingText"));
        label.setLabelFor(textCombo);
        GridPane.setValignment(label, VPos.TOP);
        GridPane.setMargin(label, new Insets(5, 0, 0, 0));

        TextArea textField = textCombo.area();
        textField.setPromptText(message("TIP_ContainingText"));
        textCombo.setVisibleRowCount(FindSettings.HISTORY_LIMIT);
        // a snapshot taken when the popup opens: the history is only reordered when a search is committed
        textCombo.setOnShowing(e -> textCombo.getItems().setAll(settings.getTextHistory()));
        // Enter searches, Shift+Enter breaks the line (NetBeans' multi-line text field)
        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    textField.insertText(textField.getCaretPosition(), "\n");
                } else if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) {
                    return;
                } else {
                    onSubmit.run();
                }
                e.consume();
            }
        });

        textHint.getStyleClass().add("search-hint");
        textHint.setWrapText(true);
        textHint.setMaxWidth(Double.MAX_VALUE);

        int row = getRowCount();
        add(label, 0, row);
        add(textCombo, 1, row);
        add(textHint, 1, row + 1);
    }

    /** Replace With: an editable combo holding the replacement history; Enter searches. */
    private void buildReplaceRow() {
        Label label = new Label(message("LBL_ReplaceWith"));
        label.setLabelFor(replaceField);
        replaceField.setEditable(true);
        replaceField.setMaxWidth(Double.MAX_VALUE);
        replaceField.getStyleClass().add("search-replace-with");
        replaceField.setVisibleRowCount(FindSettings.HISTORY_LIMIT);
        replaceField.getEditor().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !replaceField.isShowing()) {
                onSubmit.run();
                e.consume();
            }
        });
        int row = getRowCount();
        add(label, 0, row);
        add(replaceField, 1, row);
    }

    private void buildMatchRow() {
        Label label = new Label(message("LBL_Match"));
        label.setLabelFor(matchType);
        matchType.getStyleClass().add("match-type");
        matchType.getItems().setAll(MatchType.values());
        matchType.setConverter(new StringConverter<>() {
            @Override
            public String toString(MatchType type) {
                return type == null ? "" : message("MATCH_" + type.name());
            }

            @Override
            public MatchType fromString(String string) {
                return null;
            }
        });
        // Whole Words is not offered with a regular expression, as NetBeans' TextPatternCheckBoxGroup;
        // Preserve Case only applies to an ignore-case literal or wildcard search
        Runnable enable = () -> {
            boolean regexp = matchType.getValue() == MatchType.REGEXP;
            wholeWords.setDisable(regexp);
            preserveCase.setDisable(regexp || matchCase.isSelected());
        };
        matchType.valueProperty().subscribe(type -> {
            enable.run();
            textHint.setText(type == null ? "" : message("HINT_" + type.name()));
        });
        matchCase.selectedProperty().subscribe(on -> enable.run());

        matchType.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(matchType, Priority.ALWAYS);
        HBox options = replace
                ? new HBox(12, matchCase, wholeWords, preserveCase, label, matchType)
                : new HBox(12, matchCase, wholeWords, label, matchType);
        options.setAlignment(Pos.CENTER_LEFT);
        add(options, 1, getRowCount());
    }

    private void buildScopeRows() {
        Separator separator = new Separator();
        GridPane.setMargin(separator, new Insets(4, 0, 4, 0));
        add(separator, 0, getRowCount(), 2, 1);

        Label label = new Label(message("LBL_Scope"));
        label.setLabelFor(scope);
        int row = getRowCount();
        add(label, 0, row);
        add(scope, 1, row);

        Hyperlink editIgnoreList = new Hyperlink(message("LNK_EditIgnoreList"));
        editIgnoreList.getStyleClass().add("search-link");
        editIgnoreList.setFocusTraversable(false);
        editIgnoreList.setOnAction(e -> {
            editIgnoreList.setVisited(false);
            new IgnoreListDialog(owner.get(), IgnoreList.getDefault()).showAndWait();
        });
        HBox ignore = new HBox(2, useIgnoreList, editIgnoreList);
        ignore.setAlignment(Pos.CENTER_LEFT);
        HBox options = new HBox(12, searchInArchives, searchInGenerated, ignore);
        options.setAlignment(Pos.CENTER_LEFT);
        add(options, 1, getRowCount());
    }

    private void buildFileNameRows() {
        Separator separator = new Separator();
        GridPane.setMargin(separator, new Insets(4, 0, 4, 0));
        add(separator, 0, getRowCount(), 2, 1);

        Label label = new Label(message("LBL_FileNamePatterns"));
        label.setLabelFor(fileNameField);
        fileNameField.setEditable(true);
        fileNameField.setMaxWidth(Double.MAX_VALUE);
        fileNameField.getStyleClass().add("search-file-name");
        fileNameField.setVisibleRowCount(FindSettings.HISTORY_LIMIT);
        fileNameField.getEditor().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !fileNameField.isShowing()) {
                onSubmit.run();
                e.consume();
            }
        });
        int row = getRowCount();
        add(label, 0, row);
        add(fileNameField, 1, row);

        fileNameHint.getStyleClass().add("search-hint");
        fileNameHint.setWrapText(true);
        fileNameHint.setMaxWidth(Double.MAX_VALUE);
        add(fileNameHint, 1, getRowCount());
        add(filePathRegex, 1, getRowCount());

        Runnable hint = () -> {
            String text = fileNameText();
            fileNameHint.setText(message(text.isEmpty() ? "HINT_AllFiles"
                    : filePathRegex.isSelected() ? "HINT_FilePathRegex" : "HINT_FileNamePatterns"));
        };
        fileNameField.getEditor().textProperty().subscribe(hint);
        filePathRegex.selectedProperty().subscribe(hint);
    }

    private void buildStatusRow() {
        status.getStyleClass().add("search-status");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setMinHeight(Label.USE_PREF_SIZE);
        GridPane.setMargin(status, new Insets(6, 0, 0, 0));
        add(status, 0, getRowCount(), 2, 1);
    }

    // --- the criteria ------------------------------------------------------------------------

    /** The criteria the form describes now, valid or not (see {@link #validProperty()}). */
    public SearchCriteria criteria() {
        SearchScope selected = scope.getScope();
        return new SearchCriteria(
                new TextPattern(textCombo.getText(), matchType.getValue(), matchCase.isSelected(),
                        wholeWords.isSelected() && !wholeWords.isDisabled()),
                new FileNamePattern(fileNameText(), filePathRegex.isSelected()),
                selected != null ? selected : new SearchScope.Folders(List.of()),
                new ScopeOptions(searchInArchives.isSelected(), searchInGenerated.isSelected(), useIgnoreList.isSelected()),
                replace ? new Replacement(replaceText(), preserveCase.isSelected() && !preserveCase.isDisabled()) : null);
    }

    /** Whether the form asks for a replacement. */
    public boolean isReplace() {
        return replace;
    }

    /** Fills the form from {@code criteria} (Modify Criteria); a scope kind no longer offered keeps the current one. */
    public void seed(SearchCriteria criteria) {
        Objects.requireNonNull(criteria);
        setText(criteria.text().query());
        matchType.setValue(criteria.text().matchType());
        matchCase.setSelected(criteria.text().matchCase());
        wholeWords.setSelected(criteria.text().wholeWords());
        fileNameField.getEditor().setText(criteria.fileName().text());
        filePathRegex.setSelected(criteria.fileName().pathRegex());
        searchInArchives.setSelected(criteria.options().searchInArchives());
        searchInGenerated.setSelected(criteria.options().searchInGenerated());
        useIgnoreList.setSelected(criteria.options().useIgnoreList());
        scope.select(criteria.scope());
        if (replace && criteria.replacement() != null) {
            replaceField.getEditor().setText(criteria.replacement().text());
            preserveCase.setSelected(criteria.replacement().preserveCase());
        }
    }

    /** Replaces the text to search for, e.g. with the editor's selection. */
    public void setText(String text) {
        textCombo.setText(text);
    }

    /**
     * The rows the text field shows now, 1 to {@link SearchTextCombo#MAX_ROWS}: the dialog holding
     * the form grows with it, so a multi-line query never needs a scroll bar.
     */
    public ReadOnlyIntegerProperty textRowsProperty() {
        return textCombo.rowsProperty();
    }

    /** Whether {@link #criteria()} can be searched: no error in the status line. */
    public ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }

    /** The status line's text: the validation error or hint, {@code null} when there is nothing to say. */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /** Wh
at {@code Enter} in a field does: the dialog's Find. */
    public void setOnSubmit(Runnable action) {
        onSubmit = Objects.requireNonNull(action);
    }

    /** The window the Browse... chooser and the ignore list dialog belong to. */
    public void setOwner(Window window) {
        owner = () -> window;
        scope.setOwner(owner);
    }

    /** Focuses the text field with its content selected, so typing replaces it. */
    public void focusText() {
        textCombo.focusText();
    }

    /** Remembers the current options and puts the current texts first in their histories. */
    public void commit() {
        SearchCriteria criteria = criteria();
        settings.addText(criteria.text().query());
        settings.addFileName(criteria.fileName().text());
        settings.setFileNameSpecified(!criteria.fileName().isEmpty());
        settings.setMatchCase(criteria.text().matchCase());
        settings.setWholeWords(wholeWords.isSelected());
        settings.setMatchType(criteria.text().matchType());
        settings.setFilePathRegex(criteria.fileName().pathRegex());
        settings.setScopeOptions(criteria.options());
        settings.setScopeId(criteria.scope().id());
        if (replace) {
            settings.addReplace(replaceText());
            settings.setPreserveCase(preserveCase.isSelected());
        }
    }

    private String replaceText() {
        String text = replaceField.getEditor().getText();
        return text == null ? "" : text;
    }

    // --- validation --------------------------------------------------------------------------

    private String fileNameText() {
        String text = fileNameField.getEditor().getText();
        return text == null ? "" : text.strip();
    }

    private void revalidate() {
        Validation validation = criteria().validate();
        boolean error = validation != null && validation.error();
        if (!error && scope.getScope() == null) {
            validation = new Validation(true, message("ERR_NoScope"));
            error = true;
        }
        valid.set(!error);
        message.set(validation == null ? null : validation.message());
        status.setText(validation == null ? "" : validation.message());
        status.pseudoClassStateChanged(ERROR, error);
    }

    // --- for tests ---------------------------------------------------------------------------

    TextArea textField() {
        return textCombo.area();
    }

    SearchTextCombo textCombo() {
        return textCombo;
    }

    ComboBox<MatchType> matchTypeBox() {
        return matchType;
    }

    CheckBox wholeWordsBox() {
        return wholeWords;
    }

    ComboBox<String> replaceField() {
        return replaceField;
    }

    CheckBox preserveCaseBox() {
        return preserveCase;
    }

    ComboBox<String> fileNameField() {
        return fileNameField;
    }

    CheckBox filePathRegexBox() {
        return filePathRegex;
    }

    ScopeChooser scopeChooser() {
        return scope;
    }

    Label statusLabel() {
        return status;
    }

    Label textHintLabel() {
        return textHint;
    }

    Label fileNameHintLabel() {
        return fileNameHint;
    }

    private static String message(String key) {
        return NbBundle.getMessage(BasicSearchPane.class, key);
    }
}
