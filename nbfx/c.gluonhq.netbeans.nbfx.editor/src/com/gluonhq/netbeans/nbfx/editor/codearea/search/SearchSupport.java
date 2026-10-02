package com.gluonhq.netbeans.nbfx.editor.codearea.search;

import com.gluonhq.netbeans.nbfx.api.search.SearchOptions;
import com.gluonhq.netbeans.nbfx.api.search.TextSearch;
import com.gluonhq.netbeans.nbfx.api.search.TextSearch.Match;
import com.gluonhq.netbeans.nbfx.editor.codearea.BaseSyntaxDecorator;
import com.gluonhq.netbeans.nbfx.editor.codearea.TextOffsets;
import com.gluonhq.netbeans.nbfx.editor.decoration.MarkedDecoration;
import com.gluonhq.netbeans.nbfx.editor.decoration.TokenCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.util.Subscription;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SelectionSegment;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;

/**
 * Find in one editor view - the behaviour of NetBeans' {@code SearchBar} + {@code EditorFindSupport}
 * over a {@link CodeArea}: shows the {@link SearchBar} in the editor's bottom slot (above the
 * breadcrumbs), searches incrementally as the query is typed - from the <em>anchor</em>, the
 * position the bar opened at or the start of the last match moved to, so typing narrows the same
 * match - selects the current match, keeps every match highlighted in the shared model
 * (a clone shows them too) while the bar is open and the Highlight toggle is on, and moves with
 * Enter / Shift+Enter, F3 / Shift+F3 or the arrows. Esc closes the bar and returns the focus to
 * the editor, keeping the selection. Options and history are the shared {@link SearchHistory}.
 * <p>
 * {@link #showReplace()} adds the {@link ReplaceBar} row: Replace / Replace All, Backwards and
 * Preserve Case (ignore-case literal searches only).
 * <p>
 * With the Regular Expression toggle the query is a {@link java.util.regex.Pattern} and the
 * replacement may use groups and escapes ({@link TextSearch#replacement}). Some examples:
 * <table class="striped">
 * <caption>Regular expression examples</caption>
 * <tr><th>Find What</th><th>Replace With</th><th>Effect</th></tr>
 * <tr><td>{@code get(\w+)\(\)}</td><td>{@code $1}</td><td>{@code getName()} → {@code Name}</td></tr>
 * <tr><td>{@code (\w+)\.equals\((\w+)\)}</td><td>{@code Objects.equals($1, $2)}</td>
 *     <td>{@code a.equals(b)} → {@code Objects.equals(a, b)}</td></tr>
 * <tr><td>{@code (?<type>\w+)\[\]}</td><td>{@code List<${type}>}</td><td>{@code String[]} → {@code List<String>}</td></tr>
 * <tr><td>{@code ,\s*}</td><td>{@code ,\n}</td><td>puts each comma-separated item on its own line</td></tr>
 * <tr><td>{@code \s+$}</td><td>(empty)</td><td>strips trailing blanks</td></tr>
 * <tr><td>{@code ^\s{0,}//.{0,}$}</td><td>(empty)</td><td>empties whole-line comments</td></tr>
 * <tr><td>{@code \bTODO\b}</td><td>{@code FIXME}</td><td>whole word only (Whole Words is literal-only)</td></tr>
 * <tr><td>{@code (?i)color}</td><td>{@code colour}</td><td>ignore case inline (or use Match Case off)</td></tr>
 * </table>
 * The pattern runs over the whole text in {@link java.util.regex.Pattern#MULTILINE} mode: {@code ^}
 * / {@code $} anchor at line boundaries, and a pattern may span lines with {@code \n}.
 * <p>
 * The bar is created on first use; one support per view, disposed with it.
 */
public final class SearchSupport {

    private static final int MAX_HIGHLIGHTS = 5000;

    private final CodeArea codeArea;
    private final CodeTextModel model;
    private final BaseSyntaxDecorator decorator;
    private final Pane slot;
    private final SearchHistory history;

    private SearchBar bar;
    private ReplaceBar replaceBar;
    private Subscription barSubscriptions = Subscription.EMPTY;
    private Subscription editableSubscription = Subscription.EMPTY;
    private boolean syncingToggles;

    /** The last query searched for - what F3 uses while the bar is closed. */
    private String lastQuery = "";
    private TextSearch search;
    private List<Match> matches = List.of();
    private Match current;
    private int anchor;
    private boolean refreshPending;
    private boolean highlighting;
    /** The index of the match left un-highlighted because the selection sits on it; -1 for none. */
    private int excluded = -1;
    private boolean highlightRefreshPending;
    /**
     * While the bar is shown: a selection moving onto / off a match re-renders the highlights.
     * Deferred: the selection also moves while the model relocates its markers after an edit, and
     * asking for new markers at that moment would modify the marker table under its iteration.
     */
    private final ChangeListener<SelectionSegment> selectionListener = (obs, o, n) -> {
        if (highlighting && !highlightRefreshPending && currentIndex() != excluded) {
            highlightRefreshPending = true;
            Platform.runLater(() -> {
                highlightRefreshPending = false;
                if (highlighting && currentIndex() != excluded) {
                    updateHighlights();
                }
            });
        }
    };

    private final StyledTextModel.Listener modelListener = change -> {
        if (change.isEdit() && isShowing()) {
            scheduleRefresh();
        }
    };

    public SearchSupport(CodeArea codeArea, CodeTextModel model, BaseSyntaxDecorator decorator, Pane slot) {
        this(codeArea, model, decorator, slot, SearchHistory.getDefault());
    }

    SearchSupport(CodeArea codeArea, CodeTextModel model, BaseSyntaxDecorator decorator, Pane slot, SearchHistory history) {
        this.codeArea = codeArea;
        this.model = model;
        this.decorator = decorator;
        this.slot = slot;
        this.history = history;
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isConsumed() && e.getCode() == KeyCode.ESCAPE && isShowing()) {
                close();
                e.consume();
            }
        });
    }

    // --- the actions ----------------------------------------------------------------------------

    /**
     * Shows the bar (or re-focuses it), seeded with the editor's selection when it is within one
     * line, else with the last query, and searches from the selection's start. Over a Replace bar
     * this hides the Replace row: back to Find mode.
     * <p>
     * The shortcuts live with the commands in {@code nbfx-editor-actions} (Edit ▸ Find... /
     * Replace...), whose menu accelerators reach this through {@code EditorDocument}: nothing in
     * the editor or the bars consumes Shortcut+F / Shortcut+R, so they work wherever the focus is.
     */
    public void showFind() {
        show(false);
    }

    /**
     * Shows the Find row and, when the editor is editable, the Replace row under it (NetBeans'
     * {@code ReplaceBar}); seeds and focuses the query field like {@link #showFind()}. On a
     * read-only editor this is just {@link #showFind()}.
     */
    public void showReplace() {
        show(codeArea.isEditable());
    }

    private void show(boolean replace) {
        SearchBar b = ensureBar();
        if (!isShowing()) {
            slot.getChildren().addFirst(b);
            model.addListener(modelListener);
            codeArea.selectionProperty().addListener(selectionListener);
            editableSubscription = codeArea.editableProperty().subscribe(editable -> {
                if (!editable) {
                    hideReplaceRow();
                }
            });
        }
        if (replace && !isReplaceShowing()) {
            ReplaceBar r = ensureReplaceBar();
            slot.getChildren().add(slot.getChildren().indexOf(b) + 1, r);
            r.setReplacement(history.getReplaceHistory().stream().findFirst().orElse(""));
            b.setReplaceMode(true);
        } else if (!replace) {
            hideReplaceRow();
        }
        anchor = selectionStart();
        String seed = singleLineSelection();
        String query = seed.isEmpty() ? (lastQuery.isEmpty() ? history.getFindHistory().stream().findFirst().orElse("") : lastQuery) : seed;
        if (query.equals(b.getQuery())) {
            research(true);
        } else {
            b.setQuery(query);
        }
        focusField();
    }

    /**
     * Focuses the field and selects its text. The first time the bar enters the scene its skin does
     * not exist yet, so the focus request would go nowhere: defer it to after the next layout.
     */
    private void focusField() {
        Runnable focus = () -> {
            bar.field().requestFocus();
            bar.editor().selectAll();
        };
        if (bar.field().getSkin() == null) {
            Platform.runLater(focus);
        } else {
            focus.run();
        }
    }

    /** Moves to the next match after the selection, wrapping when the option is on; opens the bar when there is no query yet. */
    public void findNext() {
        move(false);
    }

    /** Moves to the previous match before the selection, wrapping when the option is on; opens the bar when there is no query yet. */
    public void findPrevious() {
        move(true);
    }

    /**
     * Searches for the selection (within one line) or the identifier at the caret, and moves to
     * its next occurrence - NetBeans' {@code find-selection}. Nothing to search for: no-op.
     */
    public void findSelection() {
        String query = singleLineSelection();
        if (query.isEmpty()) {
            query = identifierAtCaret();
        }
        if (query.isEmpty()) {
            return;
        }
        anchor = selectionStart();
        lastQuery = query;
        history.addFind(query);
        if (isShowing()) {
            bar.setQuery(query);
        } else {
            compile(query);
        }
        move(false);
    }

    public boolean isShowing() {
        return bar != null && bar.getParent() == slot;
    }

    public boolean isReplaceShowing() {
        return replaceBar != null && replaceBar.getParent() == slot;
    }

    /**
     * Replaces the current match - the selection, when it is one - with the replacement and moves
     * to the next match (the previous one with Backwards, the only thing that option drives); when
     * the selection is not a match, only moves, so a second Replace replaces it (NetBeans'
     * behaviour). The replacement is recorded in the history.
     */
    public void replace() {
        if (!isReplaceShowing() || !codeArea.isEditable()) {
            return;
        }
        if (search == null && !compile(bar.getQuery())) {
            return;
        }
        if (search == null || search.isEmpty()) {
            return;
        }
        String replaceWith = replaceBar.getReplacement();
        history.addReplace(replaceWith);
        String text = TextOffsets.text(model);
        int start = selectionStart();
        int end = selectionEnd();
        boolean backwards = search.getOptions().backwards();
        Match selected = search.findNext(text, start);
        if (selected != null && selected.start() == start && selected.end() == end) {
            String replacement = search.replacement(text, selected, replaceWith);
            TextPos from = TextOffsets.positionOf(model, start);
            TextPos to = TextOffsets.positionOf(model, end);
            TextPos after = codeArea.replaceText(from, to, replacement);
            codeArea.select(backwards ? from : after);
        }
        move(backwards);
    }

    /**
     * Replaces every match in one undoable edit and reports the count in the Find row's status.
     */
    public void replaceAll() {
        if (!isReplaceShowing() || !codeArea.isEditable()) {
            return;
        }
        if (search == null && !compile(bar.getQuery())) {
            return;
        }
        if (search == null || search.isEmpty()) {
            return;
        }
        String replaceWith = replaceBar.getReplacement();
        history.addReplace(replaceWith);
        String text = TextOffsets.text(model);
        List<Match> all = search.findAll(text);
        if (all.isEmpty()) {
            bar.setNotFound(true);
            bar.setCounter(SearchBar.message("MSG_NoMatches"));
            return;
        }
        int spanStart = all.getFirst().start();
        int spanEnd = all.getLast().end();
        StringBuilder replaced = new StringBuilder(spanEnd - spanStart);
        int cursor = spanStart;
        for (Match match : all) {
            replaced.append(text, cursor, match.start());
            replaced.append(search.replacement(text, match, replaceWith));
            cursor = match.end();
        }
        replaced.append(text, cursor, spanEnd);
        TextPos from = TextOffsets.positionOf(model, spanStart);
        TextPos to = TextOffsets.positionOf(model, spanEnd);
        codeArea.replaceText(from, to, replaced.toString());
        codeArea.select(from);
        lastQuery = search.getQuery();
        history.addFind(lastQuery);
        Platform.runLater(() -> {
            if (isShowing()) {
                bar.setNotFound(false);
                bar.setCounter(SearchBar.message("MSG_Replaced", all.size()));
            }
        });
    }

    /** Hides the bar, drops the highlights, and gives the focus back to the editor; the selection stays. */
    public void close() {
        if (!isShowing()) {
            return;
        }
        detach();
        clearHighlights();
        bar.setNotFound(false);
        codeArea.requestFocus();
    }

    private void detach() {
        hideReplaceRow();
        slot.getChildren().remove(bar);
        model.removeListener(modelListener);
        codeArea.selectionProperty().removeListener(selectionListener);
        editableSubscription.unsubscribe();
        editableSubscription = Subscription.EMPTY;
    }

    private void hideReplaceRow() {
        if (isReplaceShowing()) {
            slot.getChildren().remove(replaceBar);
            bar.setReplaceMode(false);
        }
    }

    /** Releases the model listener and the highlights this view put on the shared model. */
    public void dispose() {
        if (isShowing()) {
            detach();
        }
        clearHighlights();
        barSubscriptions.unsubscribe();
        barSubscriptions = Subscription.EMPTY;
    }

    // --- for tests ------------------------------------------------------------------------------

    SearchBar bar() {
        return bar;
    }

    ReplaceBar replaceBar() {
        return replaceBar;
    }

    Match currentMatch() {
        return current;
    }

    List<Match> matches() {
        return matches;
    }

    // --- searching ------------------------------------------------------------------------------

    private void move(boolean backwards) {
        if (search == null || search.isEmpty()) {
            String query = isShowing() ? bar.getQuery() : lastQuery;
            if (query.isEmpty()) {
                query = history.getFindHistory().stream().findFirst().orElse("");
            }
            if (query.isEmpty()) {
                showFind();
                return;
            }
            if (!compile(query)) {
                return;
            }
        }
        String text = TextOffsets.text(model);
        boolean previous = backwards;
        Match match = previous
                ? search.findPrevious(text, selectionStart())
                : search.findNext(text, selectionEnd());
        matches = search.findAll(text);
        lastQuery = search.getQuery();
        history.addFind(lastQuery);
        select(match, previous);
        if (match != null) {
            anchor = match.start();
        }
        if (isShowing()) {
            updateHighlights();
        }
    }

    /** Recompiles the bar's query and, when {@code select}, moves to the first match from the anchor. */
    private void research(boolean select) {
        if (!compile(bar.getQuery())) {
            return;
        }
        String text = TextOffsets.text(model);
        matches = search.findAll(text);
        if (search.isEmpty()) {
            current = null;
            bar.setNotFound(false);
            bar.setCounter("");
            updateHighlights();
            return;
        }
        lastQuery = search.getQuery();
        if (select) {
            select(search.findNext(text, anchor), false);
        } else {
            updateCounter(currentIndex());
        }
        updateHighlights();
    }

    /** Compiles {@code query} with the shared options; a malformed regex marks the bar and leaves the old matches. */
    private boolean compile(String query) {
        try {
            search = TextSearch.compile(query, history.getOptions());
            if (bar != null) {
                bar.setInvalid(false);
            }
            return true;
        } catch (PatternSyntaxException e) {
            search = null;
            current = null;
            matches = List.of();
            clearHighlights();
            if (bar != null) {
                bar.setInvalid(true);
                bar.setNotFound(false);
                bar.setCounter(SearchBar.message("MSG_InvalidRegex"));
            }
            return false;
        }
    }

    private void select(Match match, boolean movedBackwards) {
        current = match;
        if (match == null) {
            if (bar != null) {
                bar.setNotFound(true);
                bar.setCounter(SearchBar.message("MSG_NoMatches"));
            }
            return;
        }
        codeArea.select(TextOffsets.positionOf(model, match.start()), TextOffsets.positionOf(model, match.end()));
        if (bar != null) {
            bar.setNotFound(false);
            updateCounter(TextSearch.indexOf(matches, match.start(), match.end()));
            if (match.wrapped()) {
                bar.setCounter(bar.getCounterText() + " - "
                        + SearchBar.message(movedBackwards ? "MSG_WrappedToBottom" : "MSG_WrappedToTop"));
            }
        }
    }

    private void updateCounter(int index) {
        int count = matches.size();
        String text;
        if (count == 0) {
            text = SearchBar.message("MSG_NoMatches");
        } else if (index >= 0) {
            text = SearchBar.message("MSG_MatchOf", index + 1, count);
        } else if (count == 1) {
            text = SearchBar.message("MSG_OneMatch");
        } else {
            text = SearchBar.message("MSG_Matches", count);
        }
        bar.setCounter(text);
    }

    /** The index of the selection among the matches, or -1 when the selection is not a match. */
    private int currentIndex() {
        return TextSearch.indexOf(matches, selectionStart(), selectionEnd());
    }

    /** After an edit: the matches and highlights follow the new text, the counter the selection. */
    private void scheduleRefresh() {
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        Platform.runLater(() -> {
            refreshPending = false;
            if (isShowing()) {
                research(false);
            }
        });
    }

    // --- highlights -----------------------------------------------------------------------------

    private void updateHighlights() {
        if (!isShowing() || search == null || search.isEmpty() || !history.getOptions().highlight()) {
            clearHighlights();
            return;
        }
        // NetBeans leaves the selected match out, so the selection colour keeps it visible
        excluded = currentIndex();
        List<MarkedDecoration> decorations = new ArrayList<>(Math.min(matches.size(), MAX_HIGHLIGHTS));
        for (int i = 0; i < matches.size(); i++) {
            if (decorations.size() >= MAX_HIGHLIGHTS) {
                break;
            }
            if (i == excluded) {
                continue;
            }
            Match match = matches.get(i);
            decorations.add(new MarkedDecoration(
                    model.getMarker(TextOffsets.positionOf(model, match.start())),
                    model.getMarker(TextOffsets.positionOf(model, match.end())),
                    TokenCategory.SEARCH_MATCH.style(), null));
        }
        decorator.applySearchDecorations(model, List.copyOf(decorations));
        highlighting = true;
    }

    private void clearHighlights() {
        if (highlighting) {
            decorator.clearSearchDecorations(model);
            highlighting = false;
            excluded = -1;
        }
    }

    // --- the bar ------------------------------------------------------------------------------

    private SearchBar ensureBar() {
        if (bar != null) {
            return bar;
        }
        bar = new SearchBar(history.getFindHistory());
        bar.editor().textProperty().subscribe(text -> {
            if (isShowing()) {
                research(true);
            }
        });
        // the ComboBox owns the focus, not its inner TextField: filter there
        bar.field().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    move(e.isShiftDown());
                    e.consume();
                }
                case ESCAPE -> {
                    close();
                    e.consume();
                }
                default -> { }
            }
        });
        bar.previousButton().setOnAction(e -> move(true));
        bar.nextButton().setOnAction(e -> move(false));
        bar.closeButton().setOnAction(e -> close());

        bindToggle(bar.matchCaseToggle(), SearchOptions::matchCase, SearchOptions::withMatchCase);
        bindToggle(bar.wholeWordsToggle(), SearchOptions::wholeWords, SearchOptions::withWholeWords);
        bindToggle(bar.regexToggle(), SearchOptions::regex, SearchOptions::withRegex);
        bindToggle(bar.highlightToggle(), SearchOptions::highlight, SearchOptions::withHighlight);
        bindToggle(bar.wrapAroundToggle(), SearchOptions::wrapAround, SearchOptions::withWrapAround);
        barSubscriptions = barSubscriptions.and(history.optionsProperty().subscribe(options -> {
            syncToggles(options);
            if (isShowing()) {
                research(true);
            }
        }));
        bar.setReplaceMode(false);
        return bar;
    }

    private ReplaceBar ensureReplaceBar() {
        if (replaceBar != null) {
            return replaceBar;
        }
        replaceBar = new ReplaceBar(history.getReplaceHistory());
        replaceBar.field().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    replace();
                    e.consume();
                }
                case ESCAPE -> {
                    close();
                    e.consume();
                }
                default -> { }
            }
        });
        replaceBar.replaceButton().setOnAction(e -> replace());
        replaceBar.replaceAllButton().setOnAction(e -> replaceAll());
        bindToggle(replaceBar.backwardsCheck().selectedProperty(), SearchOptions::backwards, SearchOptions::withBackwards);
        bindToggle(replaceBar.preserveCaseCheck().selectedProperty(), SearchOptions::preserveCase, SearchOptions::withPreserveCase);
        // preserve case only applies to ignore-case literal searches (NetBeans disables it otherwise)
        replaceBar.preserveCaseCheck().disableProperty().bind(
                bar.regexToggle().selectedProperty().or(bar.matchCaseToggle().selectedProperty()));
        return replaceBar;
    }

    private void bindToggle(ToggleButton toggle, Predicate<SearchOptions> get,
            BiFunction<SearchOptions, Boolean, SearchOptions> with) {
        bindToggle(toggle.selectedProperty(), get, with);
    }

    private void bindToggle(BooleanProperty selected, Predicate<SearchOptions> get,
            BiFunction<SearchOptions, Boolean, SearchOptions> with) {
        selected.set(get.test(history.getOptions()));
        selected.subscribe(on -> {
            if (!syncingToggles && get.test(history.getOptions()) != on) {
                history.setOptions(with.apply(history.getOptions(), on));
            }
        });
    }

    private void syncToggles(SearchOptions options) {
        syncingToggles = true;
        try {
            bar.matchCaseToggle().setSelected(options.matchCase());
            bar.wholeWordsToggle().setSelected(options.wholeWords());
            bar.regexToggle().setSelected(options.regex());
            bar.highlightToggle().setSelected(options.highlight());
            bar.wrapAroundToggle().setSelected(options.wrapAround());
            if (replaceBar != null) {
                replaceBar.backwardsCheck().setSelected(options.backwards());
                replaceBar.preserveCaseCheck().setSelected(options.preserveCase());
            }
        } finally {
            syncingToggles = false;
        }
    }

    // --- the editor's selection -------------------------------------------------------------------

    private int selectionStart() {
        SelectionSegment selection = codeArea.getSelection();
        TextPos pos = selection == null ? codeArea.getCaretPosition() : selection.getMin();
        return pos == null ? 0 : TextOffsets.offsetOf(model, pos);
    }

    private int selectionEnd() {
        SelectionSegment selection = codeArea.getSelection();
        TextPos pos = selection == null ? codeArea.getCaretPosition() : selection.getMax();
        return pos == null ? 0 : TextOffsets.offsetOf(model, pos);
    }

    /** The selected text when it lies within one line, else the empty string. */
    private String singleLineSelection() {
        SelectionSegment selection = codeArea.getSelection();
        if (selection == null || selection.isCollapsed() || selection.getMin().index() != selection.getMax().index()) {
            return "";
        }
        String line = model.getPlainText(selection.getMin().index());
        return line.substring(Math.min(selection.getMin().offset(), line.length()),
                Math.min(selection.getMax().offset(), line.length()));
    }

    /** The identifier around the caret, or the empty string. */
    private String identifierAtCaret() {
        TextPos caret = codeArea.getCaretPosition();
        if (caret == null) {
            return "";
        }
        String line = model.getPlainText(caret.index());
        int start = Math.min(caret.offset(), line.length());
        int end = start;
        while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        return line.substring(start, end);
    }
}
