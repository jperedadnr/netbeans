package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery;
import com.gluonhq.netbeans.nbfx.findusages.query.QueryOptions;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsageTreeBuilder.Flavour;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The user's Usages view settings, persisted in the module preferences: the tree flavour, the
 * enabled filters, whether the preview is shown and the last query options - the defaults for the
 * next query - and the open queries as the file and offset they started from, their options and
 * their own view state - results are not persisted, the queries are run again at the next start.
 */
final class UsagesSettings {

    private static final String FLAVOUR = "flavour";
    private static final String PREVIEW = "preview";
    private static final String FILTER = "filter.";
    private static final String QUERY_COUNT = "queries";
    private static final String QUERY = "query.";
    private static final String OPTIONS_SUFFIX = ".options";
    private static final String VIEW_SUFFIX = ".view";
    private static final String COMMENTS = "comments";
    private static final String SCOPE = "scope";

    /**
     * Where a query started - the absolute path of the file and the editor offset in it - how it
     * searched, and how its tab looked ({@code null} for the defaults, as saved by an older session).
     */
    record SavedQuery(String path, int offset, QueryOptions options, ViewState view) {

        SavedQuery(UsagesQuery query, ViewState view) {
            this(query.getTarget().getFile().getPath(), query.getTarget().getOffset(), query.getOptions(), view);
        }

        /** Identifies the query this entry restores: {@code offset:path}. */
        String key() {
            return offset + ":" + path;
        }
    }

    /** How one query's tab looks: the tree flavour, whether the preview is shown, and the filters turned off. */
    record ViewState(Flavour flavour, boolean preview, Set<String> disabledFilters) {

        static ViewState of(Flavour flavour, boolean preview, UsageFilters filters) {
            Set<String> disabled = new LinkedHashSet<>();
            for (UsageFilters.Kind kind : UsageFilters.Kind.values()) {
                if (!filters.enabled(kind).get()) {
                    disabled.add(kind.name());
                }
            }
            for (UsageFilters.Root root : UsageFilters.Root.values()) {
                if (!filters.enabled(root).get()) {
                    disabled.add(root.name());
                }
            }
            return new ViewState(flavour, preview, Set.copyOf(disabled));
        }

        void applyTo(UsageFilters filters) {
            for (UsageFilters.Kind kind : UsageFilters.Kind.values()) {
                filters.enabled(kind).set(!disabledFilters.contains(kind.name()));
            }
            for (UsageFilters.Root root : UsageFilters.Root.values()) {
                filters.enabled(root).set(!disabledFilters.contains(root.name()));
            }
        }

        /** {@code FLAVOUR:preview:FILTER,FILTER} - the disabled filters, possibly none. */
        String encode() {
            return flavour.name() + ":" + preview + ":" + String.join(",", disabledFilters);
        }

        /** The state saved by {@link #encode}; {@code null} when missing or corrupt. */
        static ViewState decode(String entry) {
            if (entry == null) {
                return null;
            }
            String[] parts = entry.split(":", -1);
            if (parts.length != 3) {
                return null;
            }
            try {
                Set<String> disabled = parts[2].isEmpty() ? Set.of() : Set.of(parts[2].split(","));
                return new ViewState(Flavour.valueOf(parts[0]), Boolean.parseBoolean(parts[1]), disabled);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final Preferences prefs;

    UsagesSettings() {
        this(NbPreferences.forModule(UsagesSettings.class));
    }

    UsagesSettings(Preferences prefs) {
        this.prefs = prefs;
    }

    Flavour getFlavour() {
        try {
            return Flavour.valueOf(prefs.get(FLAVOUR, Flavour.LOGICAL.name()));
        } catch (IllegalArgumentException ex) {
            return Flavour.LOGICAL;
        }
    }

    void setFlavour(Flavour flavour) {
        prefs.put(FLAVOUR, flavour.name());
    }

    boolean isPreviewVisible() {
        return prefs.getBoolean(PREVIEW, false);
    }

    void setPreviewVisible(boolean visible) {
        prefs.putBoolean(PREVIEW, visible);
    }

    /** The options the last query was run with, the defaults for the next. */
    QueryOptions getOptions() {
        return new QueryOptions(prefs.getBoolean(COMMENTS, QueryOptions.DEFAULT.searchComments()),
                scope(prefs.get(SCOPE, null)));
    }

    void setOptions(QueryOptions options) {
        prefs.putBoolean(COMMENTS, options.searchComments());
        prefs.put(SCOPE, options.scope().name());
    }

    private static QueryOptions.Scope scope(String name) {
        try {
            return name == null ? QueryOptions.DEFAULT.scope() : QueryOptions.Scope.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return QueryOptions.DEFAULT.scope();
        }
    }

    /** The queries saved by {@link #setQueries}, oldest first. */
    List<SavedQuery> getQueries() {
        int count = prefs.getInt(QUERY_COUNT, 0);
        List<SavedQuery> saved = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String entry = prefs.get(QUERY + i, null);
            int separator = entry == null ? -1 : entry.indexOf(':');
            if (separator < 0) {
                continue;
            }
            try {
                saved.add(new SavedQuery(entry.substring(separator + 1), Integer.parseInt(entry.substring(0, separator)),
                        options(prefs.get(QUERY + i + OPTIONS_SUFFIX, null)),
                        ViewState.decode(prefs.get(QUERY + i + VIEW_SUFFIX, null))));
            } catch (NumberFormatException ex) {
                // a corrupt entry: skip it
            }
        }
        return saved;
    }

    /** Replaces the saved queries with {@code queries}. */
    void setQueries(List<SavedQuery> queries) {
        int previous = prefs.getInt(QUERY_COUNT, 0);
        for (int i = 0; i < queries.size(); i++) {
            SavedQuery query = queries.get(i);
            prefs.put(QUERY + i, query.key());
            prefs.put(QUERY + i + OPTIONS_SUFFIX, query.options().searchComments() + ":" + query.options().scope().name());
            if (query.view() != null) {
                prefs.put(QUERY + i + VIEW_SUFFIX, query.view().encode());
            } else {
                prefs.remove(QUERY + i + VIEW_SUFFIX);
            }
        }
        for (int i = queries.size(); i < previous; i++) {
            prefs.remove(QUERY + i);
            prefs.remove(QUERY + i + OPTIONS_SUFFIX);
            prefs.remove(QUERY + i + VIEW_SUFFIX);
        }
        prefs.putInt(QUERY_COUNT, queries.size());
    }

    /** The options saved as {@code comments:SCOPE}; the defaults when missing or corrupt. */
    private static QueryOptions options(String entry) {
        int separator = entry == null ? -1 : entry.indexOf(':');
        if (separator < 0) {
            return QueryOptions.DEFAULT;
        }
        return new QueryOptions(Boolean.parseBoolean(entry.substring(0, separator)), scope(entry.substring(separator + 1)));
    }

    /** Loads the persisted filter states into {@code filters} (a filter missing from the store is enabled). */
    void load(UsageFilters filters) {
        for (UsageFilters.Kind kind : UsageFilters.Kind.values()) {
            filters.enabled(kind).set(prefs.getBoolean(FILTER + kind.name(), true));
        }
        for (UsageFilters.Root root : UsageFilters.Root.values()) {
            filters.enabled(root).set(prefs.getBoolean(FILTER + root.name(), true));
        }
    }

    /** Persists every change of {@code filters} from now on. */
    void watch(UsageFilters filters) {
        for (UsageFilters.Kind kind : UsageFilters.Kind.values()) {
            filters.enabled(kind).subscribe(on -> prefs.putBoolean(FILTER + kind.name(), on));
        }
        for (UsageFilters.Root root : UsageFilters.Root.values()) {
            filters.enabled(root).subscribe(on -> prefs.putBoolean(FILTER + root.name(), on));
        }
    }
}
