package com.gluonhq.netbeans.nbfx.findinprojects.query;

import java.util.Objects;
import java.util.regex.PatternSyntaxException;
import org.openide.util.NbBundle;

/**
 * Everything the Find in Projects dialog collects: what to find, in which files, where, and -
 * for Replace in Projects - what to put in its place.
 *
 * @param text        the containing text; {@link TextPattern#EMPTY} for a file-name-only search
 * @param fileName    the file name patterns; {@link FileNamePattern#ALL} for every file
 * @param scope       where to search
 * @param options     the scope options
 * @param replacement the replacement, or {@code null} for a plain search
 */
public record SearchCriteria(TextPattern text, FileNamePattern fileName, SearchScope scope,
        ScopeOptions options, Replacement replacement) {

    /** What {@link #validate()} found: an error blocks the search, an info is only shown. */
    public record Validation(boolean error, String message) {
    }

    public SearchCriteria {
        Objects.requireNonNull(text);
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(options);
    }

    /** A plain search for {@code text} with the default options. */
    public static SearchCriteria of(TextPattern text, SearchScope scope) {
        return new SearchCriteria(text, FileNamePattern.ALL, scope, ScopeOptions.DEFAULT, null);
    }

    public SearchCriteria withText(TextPattern pattern) {
        return new SearchCriteria(pattern, fileName, scope, options, replacement);
    }

    public SearchCriteria withFileName(FileNamePattern pattern) {
        return new SearchCriteria(text, pattern, scope, options, replacement);
    }

    public SearchCriteria withScope(SearchScope where) {
        return new SearchCriteria(text, fileName, where, options, replacement);
    }

    public SearchCriteria withOptions(ScopeOptions scopeOptions) {
        return new SearchCriteria(text, fileName, scope, scopeOptions, replacement);
    }

    public SearchCriteria withReplacement(Replacement replaceWith) {
        return new SearchCriteria(text, fileName, scope, options, replaceWith);
    }

    /** Whether this is a Replace in Projects search. */
    public boolean isReplace() {
        return replacement != null;
    }

    /** Whether only file names are searched: matching files are reported without details. */
    public boolean isFileNameOnly() {
        return text.isEmpty();
    }

    /**
     * The first problem with these criteria, in the order NetBeans reports them: nothing to search
     * for (a replace needs text to find: a file-name pattern alone is not enough), a malformed text
     * expression, a malformed file-name expression, or - as an info - a simple file-name pattern
     * without wildcards. {@code null} when there is nothing to say.
     */
    public Validation validate() {
        if (text.isEmpty() && (fileName.isEmpty() || isReplace())) {
            return new Validation(true, message("ERR_MissingCriteria"));
        }
        if (!text.isEmpty()) {
            try {
                text.compile();
            } catch (PatternSyntaxException ex) {
                return new Validation(true, message("ERR_TextPattern"));
            }
        }
        if (!fileName.isEmpty()) {
            try {
                fileName.toPattern();
            } catch (PatternSyntaxException ex) {
                return new Validation(true, message("ERR_FileNamePattern"));
            }
            if (!fileName.pathRegex() && !fileName.hasWildcards()) {
                return new Validation(false, message("INFO_NoWildcards"));
            }
        }
        return null;
    }

    /** Whether the criteria describe a search that can run. */
    public boolean isValid() {
        Validation validation = validate();
        return validation == null || !validation.error();
    }

    private static String message(String key) {
        return NbBundle.getMessage(SearchCriteria.class, key);
    }
}
