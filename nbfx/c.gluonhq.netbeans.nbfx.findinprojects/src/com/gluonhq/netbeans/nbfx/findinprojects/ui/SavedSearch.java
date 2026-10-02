package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FileNamePattern;
import com.gluonhq.netbeans.nbfx.findinprojects.query.MatchType;
import com.gluonhq.netbeans.nbfx.findinprojects.query.Replacement;
import com.gluonhq.netbeans.nbfx.findinprojects.query.ScopeOptions;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchScope;
import com.gluonhq.netbeans.nbfx.findinprojects.query.TextPattern;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultTreeBuilder.Flavour;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;
import org.openide.filesystems.FileObject;

/**
 * A search of the Search view as it is persisted between sessions: its criteria with the scope
 * reduced to its kind and the paths of its roots - a {@link SearchScope} holds {@link FileObject}s,
 * which are described again by {@link SearchRestorer} at the next start - whether it is the tab a
 * search with "Open in New Tab" off replaces, and how its tab looks. Results are not persisted:
 * the search is run again.
 *
 * @param text        the containing text
 * @param fileName    the file name patterns
 * @param scopeId     the {@link SearchScope#id() kind} of scope
 * @param roots       the absolute paths of the scope's roots when it was saved
 * @param options     the scope options
 * @param replacement the replacement of a Replace in Projects search, else {@code null}
 * @param reusable    whether this was the {@link com.gluonhq.netbeans.nbfx.findinprojects.model.FindModel#getReusable() reusable} query
 * @param view        how the tab looked; {@code null} for the defaults
 */
record SavedSearch(TextPattern text, FileNamePattern fileName, String scopeId, List<String> roots,
        ScopeOptions options, Replacement replacement, boolean reusable, ViewState view) {

    /** How one results tab looks: the flavour of the tree and whether the preview is shown. */
    record ViewState(Flavour flavour, boolean preview) {

        ViewState {
            Objects.requireNonNull(flavour);
        }

        /** {@code FLAVOUR:preview}. */
        String encode() {
            return flavour.name() + ":" + preview;
        }

        /** The state saved by {@link #encode}; {@code null} when missing or corrupt. */
        static ViewState decode(String entry) {
            int separator = entry == null ? -1 : entry.indexOf(':');
            if (separator < 0) {
                return null;
            }
            try {
                return new ViewState(Flavour.valueOf(entry.substring(0, separator)),
                        Boolean.parseBoolean(entry.substring(separator + 1)));
            } catch (IllegalArgumentException corrupt) {
                return null;
            }
        }
    }

    private static final String TEXT = ".text";
    private static final String MATCH = ".match";
    private static final String FILENAME = ".filename";
    private static final String PATH_REGEX = ".path_regex";
    private static final String SCOPE = ".scope";
    private static final String ROOTS = ".roots";
    private static final String OPTIONS = ".options";
    private static final String REPLACE = ".replace";
    private static final String PRESERVE_CASE = ".preserve_case";
    private static final String REUSABLE = ".reusable";
    private static final String VIEW = ".view";
    private static final String[] SUFFIXES = {
        TEXT, MATCH, FILENAME, PATH_REGEX, SCOPE, ROOTS, OPTIONS, REPLACE, PRESERVE_CASE, REUSABLE, VIEW
    };
    /** Separates the root paths, which a path cannot contain on any supported platform. */
    private static final String ROOT_SEPARATOR = "\n";

    SavedSearch {
        Objects.requireNonNull(text);
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(scopeId);
        roots = List.copyOf(roots);
        Objects.requireNonNull(options);
    }

    /** {@code criteria} as saved, with the roots of its scope as absolute paths. */
    static SavedSearch of(SearchCriteria criteria, boolean reusable, ViewState view) {
        List<String> roots = new ArrayList<>();
        for (FileObject root : criteria.scope().roots()) {
            roots.add(OpenProject.pathOf(root));
        }
        return new SavedSearch(criteria.text(), criteria.fileName(), criteria.scope().id(), roots,
                criteria.options(), criteria.replacement(), reusable, view);
    }

    /** The criteria saved here over {@code scope}, the persisted one described again. */
    SearchCriteria criteria(SearchScope scope) {
        return new SearchCriteria(text, fileName, scope, options, replacement);
    }

    /** Writes this search under {@code prefix} (e.g. {@code search.3}) in {@code prefs}. */
    void write(Preferences prefs, String prefix) {
        prefs.put(prefix + TEXT, text.query());
        prefs.put(prefix + MATCH, text.matchType().name() + ":" + text.matchCase() + ":" + text.wholeWords());
        prefs.put(prefix + FILENAME, fileName.text());
        prefs.putBoolean(prefix + PATH_REGEX, fileName.pathRegex());
        prefs.put(prefix + SCOPE, scopeId);
        prefs.put(prefix + ROOTS, String.join(ROOT_SEPARATOR, roots));
        prefs.put(prefix + OPTIONS, options.searchInArchives() + ":" + options.searchInGenerated() + ":" + options.useIgnoreList());
        if (replacement != null) {
            prefs.put(prefix + REPLACE, replacement.text());
            prefs.putBoolean(prefix + PRESERVE_CASE, replacement.preserveCase());
        } else {
            prefs.remove(prefix + REPLACE);
            prefs.remove(prefix + PRESERVE_CASE);
        }
        prefs.putBoolean(prefix + REUSABLE, reusable);
        if (view != null) {
            prefs.put(prefix + VIEW, view.encode());
        } else {
            prefs.remove(prefix + VIEW);
        }
    }

    /** The search written under {@code prefix} by {@link #write}; {@code null} when missing or corrupt. */
    static SavedSearch read(Preferences prefs, String prefix) {
        String query = prefs.get(prefix + TEXT, null);
        String[] match = split(prefs.get(prefix + MATCH, null), 3);
        String scopeId = prefs.get(prefix + SCOPE, null);
        String[] options = split(prefs.get(prefix + OPTIONS, null), 3);
        if (query == null || match == null || scopeId == null || options == null) {
            return null;
        }
        MatchType matchType;
        try {
            matchType = MatchType.valueOf(match[0]);
        } catch (IllegalArgumentException corrupt) {
            return null;
        }
        String roots = prefs.get(prefix + ROOTS, "");
        String replace = prefs.get(prefix + REPLACE, null);
        return new SavedSearch(
                new TextPattern(query, matchType, Boolean.parseBoolean(match[1]), Boolean.parseBoolean(match[2])),
                new FileNamePattern(prefs.get(prefix + FILENAME, ""), prefs.getBoolean(prefix + PATH_REGEX, false)),
                scopeId,
                roots.isEmpty() ? List.of() : List.of(roots.split(ROOT_SEPARATOR)),
                new ScopeOptions(Boolean.parseBoolean(options[0]), Boolean.parseBoolean(options[1]), Boolean.parseBoolean(options[2])),
                replace == null ? null : new Replacement(replace, prefs.getBoolean(prefix + PRESERVE_CASE, false)),
                prefs.getBoolean(prefix + REUSABLE, false),
                ViewState.decode(prefs.get(prefix + VIEW, null)));
    }

    /** Removes whatever {@link #write} put under {@code prefix}. */
    static void remove(Preferences prefs, String prefix) {
        for (String suffix : SUFFIXES) {
            prefs.remove(prefix + suffix);
        }
    }

    /** {@code entry} split at {@code :} into exactly {@code count} parts, else {@code null}. */
    private static String[] split(String entry, int count) {
        if (entry == null) {
            return null;
        }
        String[] parts = entry.split(":", -1);
        return parts.length == count ? parts : null;
    }
}
