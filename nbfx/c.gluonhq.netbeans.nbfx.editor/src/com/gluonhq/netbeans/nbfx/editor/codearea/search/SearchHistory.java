package com.gluonhq.netbeans.nbfx.editor.codearea.search;

import com.gluonhq.netbeans.nbfx.api.search.SearchOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.util.NbPreferences;

/**
 * What the search bars share and keep between sessions, as NetBeans' {@code EditorFindSupport}
 * properties: the recent find and replace strings (most recent first, at most {@link #LIMIT}) and
 * the {@link SearchOptions} toggles - a toggle flipped in one editor is the default in the next.
 * Persisted in the module preferences as {@code find.<i>}, {@code replace.<i>} and one boolean per
 * option ({@code matchCase}, {@code wholeWords}, {@code regex}, {@code wrapAround},
 * {@code highlight}, {@code backwards}, {@code preserveCase}).
 */
public final class SearchHistory {

    public static final int LIMIT = 20;

    private static final String FIND = "find.";
    private static final String REPLACE = "replace.";
    private static final String MATCH_CASE = "matchCase";
    private static final String WHOLE_WORDS = "wholeWords";
    private static final String REGEX = "regex";
    private static final String WRAP_AROUND = "wrapAround";
    private static final String HIGHLIGHT = "highlight";
    private static final String BACKWARDS = "backwards";
    private static final String PRESERVE_CASE = "preserveCase";

    private static SearchHistory instance;

    private final Preferences prefs;
    private final ObservableList<String> findHistory = FXCollections.observableArrayList();
    private final ObservableList<String> replaceHistory = FXCollections.observableArrayList();
    private final ObjectProperty<SearchOptions> options = new SimpleObjectProperty<>(this, "options");

    /** The shared history, backed by the module preferences. JavaFX thread. */
    public static synchronized SearchHistory getDefault() {
        if (instance == null) {
            instance = new SearchHistory(NbPreferences.forModule(SearchHistory.class));
        }
        return instance;
    }

    SearchHistory(Preferences prefs) {
        this.prefs = prefs;
        findHistory.setAll(load(FIND));
        replaceHistory.setAll(load(REPLACE));
        SearchOptions d = SearchOptions.DEFAULT;
        options.set(new SearchOptions(
                prefs.getBoolean(MATCH_CASE, d.matchCase()),
                prefs.getBoolean(WHOLE_WORDS, d.wholeWords()),
                prefs.getBoolean(REGEX, d.regex()),
                prefs.getBoolean(WRAP_AROUND, d.wrapAround()),
                prefs.getBoolean(HIGHLIGHT, d.highlight()),
                prefs.getBoolean(BACKWARDS, d.backwards()),
                prefs.getBoolean(PRESERVE_CASE, d.preserveCase())));
        options.subscribe(this::store);
    }

    /** The recent find strings, most recent first; read-only. */
    public ObservableList<String> getFindHistory() {
        return FXCollections.unmodifiableObservableList(findHistory);
    }

    /** The recent replace strings, most recent first; read-only. */
    public ObservableList<String> getReplaceHistory() {
        return FXCollections.unmodifiableObservableList(replaceHistory);
    }

    /** The shared toggles; a new value is persisted at once. Never {@code null}. */
    public ObjectProperty<SearchOptions> optionsProperty() {
        return options;
    }

    public SearchOptions getOptions() {
        return options.get();
    }

    public void setOptions(SearchOptions value) {
        options.set(value == null ? SearchOptions.DEFAULT : value);
    }

    /** Moves (or adds) {@code text} to the front of the find history; the empty string is ignored. */
    public void addFind(String text) {
        add(findHistory, FIND, text);
    }

    /** Moves (or adds) {@code text} to the front of the replace history; the empty string is kept (replace with nothing). */
    public void addReplace(String text) {
        add(replaceHistory, REPLACE, text);
    }

    private void add(ObservableList<String> history, String prefix, String text) {
        if (text == null || (text.isEmpty() && history == findHistory)) {
            return;
        }
        List<String> updated = new ArrayList<>(history);
        updated.remove(text);
        updated.addFirst(text);
        while (updated.size() > LIMIT) {
            updated.removeLast();
        }
        history.setAll(updated);
        for (int i = 0; i < LIMIT; i++) {
            if (i < updated.size()) {
                prefs.put(prefix + i, updated.get(i));
            } else {
                prefs.remove(prefix + i);
            }
        }
    }

    private List<String> load(String prefix) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < LIMIT; i++) {
            String entry = prefs.get(prefix + i, null);
            if (entry == null) {
                break;
            }
            entries.add(entry);
        }
        return entries;
    }

    private void store(SearchOptions o) {
        prefs.putBoolean(MATCH_CASE, o.matchCase());
        prefs.putBoolean(WHOLE_WORDS, o.wholeWords());
        prefs.putBoolean(REGEX, o.regex());
        prefs.putBoolean(WRAP_AROUND, o.wrapAround());
        prefs.putBoolean(HIGHLIGHT, o.highlight());
        prefs.putBoolean(BACKWARDS, o.backwards());
        prefs.putBoolean(PRESERVE_CASE, o.preserveCase());
    }
}
