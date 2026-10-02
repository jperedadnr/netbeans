package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.query.MatchType;
import com.gluonhq.netbeans.nbfx.findinprojects.query.ScopeOptions;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchScope;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultTreeBuilder.Flavour;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * What the Find in Projects dialog remembers between uses, under NetBeans' {@code FindDialogMemory}
 * key names: the text, replacement and file-name histories (most recent first, at most
 * {@link #HISTORY_LIMIT}), the match options, the scope kind and the scope options - and what the
 * Search view remembers: the results flavour, the column layout and the open searches, which are
 * run again at the next start.
 */
public final class FindSettings {

    public static final int HISTORY_LIMIT = 10;

    private static final String TEXT_PREFIX = "text_history_";
    private static final String REPLACE_PREFIX = "replace_history_";
    private static final String FILENAME_PREFIX = "filename_pattern_";
    private static final String WHOLE_WORDS = "whole_words";
    private static final String CASE_SENSITIVE = "case_sensitive";
    private static final String PRESERVE_CASE = "preserve_case";
    private static final String MATCH_TYPE = "match_type";
    private static final String SCOPE_TYPE_ID = "scope_type_id";
    private static final String FILENAME_SPECIFIED = "filename_specified";
    private static final String SEARCH_IN_ARCHIVES = "search_in_archives";
    private static final String SEARCH_IN_GENERATED = "search_in_generated";
    private static final String FILE_PATH_REGEX = "file_path_regex";
    private static final String USE_IGNORE_LIST = "use_ignore_list";
    private static final String OPEN_IN_NEW_TAB = "open_in_new_tab";
    private static final String RESULTS_VIEW_MODE = "results_view_mode";
    private static final String RESULTS_COLUMN_WIDTHS = "results_column_widths";
    private static final String SHOW_PREVIEW = "ShowPreview";
    private static final String RESULTS_DIVIDER = "results_divider";
    private static final String SEARCH_COUNT = "searches";
    private static final String SEARCH = "search.";

    /** The saved layout of one results column: its id, its width and whether it is shown. */
    public record ColumnState(String id, double width, boolean visible) {

        public ColumnState {
            Objects.requireNonNull(id);
        }
    }

    private static final class Default {
        static final FindSettings INSTANCE = new FindSettings(NbPreferences.forModule(FindSettings.class));
    }

    private final Preferences preferences;

    /** The settings of this module's preferences node. */
    public static FindSettings getDefault() {
        return Default.INSTANCE;
    }

    public FindSettings(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    // --- histories ---------------------------------------------------------------------------

    public List<String> getTextHistory() {
        return history(TEXT_PREFIX);
    }

    /** Puts {@code text} first in the text history; blank text is not remembered. */
    public void addText(String text) {
        add(TEXT_PREFIX, text);
    }

    public List<String> getReplaceHistory() {
        return history(REPLACE_PREFIX);
    }

    /** Puts {@code text} first in the replacement history; blank text is not remembered. */
    public void addReplace(String text) {
        add(REPLACE_PREFIX, text);
    }

    public List<String> getFileNameHistory() {
        return history(FILENAME_PREFIX);
    }

    /** Puts {@code pattern} first in the file-name history; blank text is not remembered. */
    public void addFileName(String pattern) {
        add(FILENAME_PREFIX, pattern);
    }

    private List<String> history(String prefix) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < HISTORY_LIMIT; i++) {
            String item = preferences.get(prefix + i, null);
            if (item == null) {
                break;
            }
            items.add(item);
        }
        return items;
    }

    private void add(String prefix, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> items = history(prefix);
        items.remove(text);
        items.addFirst(text);
        while (items.size() > HISTORY_LIMIT) {
            items.removeLast();
        }
        for (int i = 0; i < HISTORY_LIMIT; i++) {
            if (i < items.size()) {
                preferences.put(prefix + i, items.get(i));
            } else {
                preferences.remove(prefix + i);
            }
        }
    }

    // --- match options -----------------------------------------------------------------------

    public boolean isWholeWords() {
        return preferences.getBoolean(WHOLE_WORDS, false);
    }

    public void setWholeWords(boolean on) {
        preferences.putBoolean(WHOLE_WORDS, on);
    }

    public boolean isMatchCase() {
        return preferences.getBoolean(CASE_SENSITIVE, false);
    }

    public void setMatchCase(boolean on) {
        preferences.putBoolean(CASE_SENSITIVE, on);
    }

    public boolean isPreserveCase() {
        return preferences.getBoolean(PRESERVE_CASE, false);
    }

    public void setPreserveCase(boolean on) {
        preferences.putBoolean(PRESERVE_CASE, on);
    }

    public MatchType getMatchType() {
        String name = preferences.get(MATCH_TYPE, MatchType.LITERAL.name());
        try {
            return MatchType.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return MatchType.LITERAL;
        }
    }

    public void setMatchType(MatchType type) {
        preferences.put(MATCH_TYPE, Objects.requireNonNull(type).name());
    }

    // --- scope -------------------------------------------------------------------------------

    /** The {@link SearchScope#id() id} of the scope kind last searched. */
    public String getScopeId() {
        return preferences.get(SCOPE_TYPE_ID, SearchScope.ID_OPEN_PROJECTS);
    }

    public void setScopeId(String id) {
        preferences.put(SCOPE_TYPE_ID, Objects.requireNonNull(id));
    }

    public ScopeOptions getScopeOptions() {
        return new ScopeOptions(
                preferences.getBoolean(SEARCH_IN_ARCHIVES, ScopeOptions.DEFAULT.searchInArchives()),
                preferences.getBoolean(SEARCH_IN_GENERATED, ScopeOptions.DEFAULT.searchInGenerated()),
                preferences.getBoolean(USE_IGNORE_LIST, ScopeOptions.DEFAULT.useIgnoreList()));
    }

    public void setScopeOptions(ScopeOptions options) {
        preferences.putBoolean(SEARCH_IN_ARCHIVES, options.searchInArchives());
        preferences.putBoolean(SEARCH_IN_GENERATED, options.searchInGenerated());
        preferences.putBoolean(USE_IGNORE_LIST, options.useIgnoreList());
    }

    // --- file name ---------------------------------------------------------------------------

    /** Whether the last search had a file-name pattern; the form then preselects the first of the history. */
    public boolean isFileNameSpecified() {
        return preferences.getBoolean(FILENAME_SPECIFIED, false);
    }

    public void setFileNameSpecified(boolean on) {
        preferences.putBoolean(FILENAME_SPECIFIED, on);
    }

    public boolean isFilePathRegex() {
        return preferences.getBoolean(FILE_PATH_REGEX, false);
    }

    public void setFilePathRegex(boolean on) {
        preferences.putBoolean(FILE_PATH_REGEX, on);
    }

    // --- dialog ------------------------------------------------------------------------------

    public boolean isOpenInNewTab() {
        return preferences.getBoolean(OPEN_IN_NEW_TAB, false);
    }

    public void setOpenInNewTab(boolean on) {
        preferences.putBoolean(OPEN_IN_NEW_TAB, on);
    }

    // --- results view ------------------------------------------------------------------------

    /** How the results are arranged: as a directory tree (the default) or as a list of files. */
    public Flavour getViewMode() {
        String name = preferences.get(RESULTS_VIEW_MODE, Flavour.TREE.name());
        try {
            return Flavour.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return Flavour.TREE;
        }
    }

    public void setViewMode(Flavour flavour) {
        preferences.put(RESULTS_VIEW_MODE, Objects.requireNonNull(flavour).name());
    }

    /** The results columns as last laid out, in order; empty when never saved or unreadable. */
    public List<ColumnState> getResultsColumns() {
        String saved = preferences.get(RESULTS_COLUMN_WIDTHS, "");
        List<ColumnState> columns = new ArrayList<>();
        if (saved.isEmpty()) {
            return columns;
        }
        for (String entry : saved.split(",")) {
            int equals = entry.indexOf('=');
            String[] parts = equals < 0 ? new String[0] : entry.substring(equals + 1).split(":");
            if (parts.length != 2) {
                return List.of();
            }
            try {
                columns.add(new ColumnState(entry.substring(0, equals), Double.parseDouble(parts[0]), Boolean.parseBoolean(parts[1])));
            } catch (NumberFormatException corrupt) {
                return List.of();
            }
        }
        return columns;
    }

    /** Saves the columns as {@code id=width:visible,...}. */
    public void setResultsColumns(List<ColumnState> columns) {
        StringBuilder sb = new StringBuilder();
        for (ColumnState column : columns) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(column.id()).append('=').append(Math.round(column.width())).append(':').append(column.visible());
        }
        preferences.put(RESULTS_COLUMN_WIDTHS, sb.toString());
    }

    /** Whether the results pane shows the preview of the selected occurrence. */
    public boolean isPreviewVisible() {
        return preferences.getBoolean(SHOW_PREVIEW, false);
    }

    public void setPreviewVisible(boolean on) {
        preferences.putBoolean(SHOW_PREVIEW, on);
    }

    /** The position of the divider between the results and the preview, a fraction between 0.1 and 0.9. */
    public double getResultsDivider() {
        double position = preferences.getDouble(RESULTS_DIVIDER, 0.5);
        return Double.isFinite(position) ? Math.max(0.1, Math.min(0.9, position)) : 0.5;
    }

    public void setResultsDivider(double position) {
        preferences.putDouble(RESULTS_DIVIDER, position);
    }

    // --- open searches -----------------------------------------------------------------------

    /** The searches saved by {@link #setSearches}, in tab order, skipping the ones that cannot be read. */
    List<SavedSearch> getSearches() {
        int count = preferences.getInt(SEARCH_COUNT, 0);
        List<SavedSearch> searches = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SavedSearch search = SavedSearch.read(preferences, SEARCH + i);
            if (search != null) {
                searches.add(search);
            }
        }
        return searches;
    }

    /** Replaces the saved searches with {@code searches}. */
    void setSearches(List<SavedSearch> searches) {
        int previous = preferences.getInt(SEARCH_COUNT, 0);
        for (int i = 0; i < searches.size(); i++) {
            searches.get(i).write(preferences, SEARCH + i);
        }
        for (int i = searches.size(); i < previous; i++) {
            SavedSearch.remove(preferences, SEARCH + i);
        }
        preferences.putInt(SEARCH_COUNT, searches.size());
    }
}
