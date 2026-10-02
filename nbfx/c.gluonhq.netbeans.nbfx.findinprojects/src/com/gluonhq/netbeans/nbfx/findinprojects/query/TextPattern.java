package com.gluonhq.netbeans.nbfx.findinprojects.query;

import com.gluonhq.netbeans.nbfx.api.search.SearchOptions;
import com.gluonhq.netbeans.nbfx.api.search.TextSearch;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The "Containing Text" of a search and how it matches - NetBeans' {@code SearchPattern}. An empty
 * query is a file-name-only search.
 *
 * @param query      the text to find, possibly spanning lines
 * @param matchType  literal, basic wildcards or regular expression
 * @param matchCase  letters must match in case
 * @param wholeWords a match may not start or end inside a word (letters, digits, {@code _}); as in
 *                   NetBeans, it applies to literal and wildcard patterns, not to regular expressions
 */
public record TextPattern(String query, MatchType matchType, boolean matchCase, boolean wholeWords) {

    public static final TextPattern EMPTY = new TextPattern("", MatchType.LITERAL, false, false);

    private static final String WORD_CHAR = "[\\p{javaLetterOrDigit}_]";
    private static final String NOT_AFTER_WORD = "(?<!" + WORD_CHAR + ")";
    private static final String NOT_BEFORE_WORD = "(?!" + WORD_CHAR + ")";

    public TextPattern {
        Objects.requireNonNull(query);
        Objects.requireNonNull(matchType);
    }

    public static TextPattern literal(String query) {
        return new TextPattern(query, MatchType.LITERAL, false, false);
    }

    public TextPattern withQuery(String text) {
        return new TextPattern(text, matchType, matchCase, wholeWords);
    }

    public TextPattern withMatchType(MatchType type) {
        return new TextPattern(query, type, matchCase, wholeWords);
    }

    public TextPattern withMatchCase(boolean on) {
        return new TextPattern(query, matchType, on, wholeWords);
    }

    public TextPattern withWholeWords(boolean on) {
        return new TextPattern(query, matchType, matchCase, on);
    }

    /** {@code true} when there is no text to find (a file-name-only search). */
    public boolean isEmpty() {
        return query.isEmpty();
    }

    /** Whether the query spans lines, or is a regular expression that may match across them. */
    public boolean isMultiline() {
        if (query.indexOf('\n') >= 0 || query.indexOf('\r') >= 0) {
            return true;
        }
        return matchType == MatchType.REGEXP && MULTILINE_REGEXP.matcher(query).matches();
    }

    private static final Pattern MULTILINE_REGEXP =
            Pattern.compile(".*(\\\\n|\\\\r|\\\\f|\\\\u|\\\\0|\\\\x|\\\\s|\\(\\?[idmux]*s).*", Pattern.DOTALL);

    /**
     * The compiled search: the literal or the wildcard pattern turned into a regular expression
     * with the whole-words guards, or the user's expression.
     *
     * @throws PatternSyntaxException for a malformed regular expression
     */
    public TextSearch compile() {
        SearchOptions options = SearchOptions.DEFAULT
                .withMatchCase(matchCase)
                .withWrapAround(false)
                .withHighlight(false);
        return switch (matchType) {
            case LITERAL -> TextSearch.compile(query, options.withWholeWords(wholeWords));
            case BASIC_WILDCARDS -> TextSearch.compile(wildcardsToRegex(query, wholeWords), options.withRegex(true));
            case REGEXP -> TextSearch.compile(query, options.withRegex(true));
        };
    }

    /**
     * NetBeans' {@code TextRegexpUtil.makeRegexp}: {@code *} → any run of characters within a line,
     * {@code ?} → any one character, a backslash quotes the next character; everything else is
     * literal. With {@code wholeWords}, guards are added where the pattern starts or ends on a
     * word character (a pattern starting with {@code *} has no start guard, as in NetBeans).
     */
    static String wildcardsToRegex(String pattern, boolean wholeWords) {
        StringBuilder sb = new StringBuilder(pattern.length() + 16);
        boolean quoted = false;
        boolean started = false;
        char last = '*';
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (quoted) {
                quoted = false;
                if (!started && wholeWords && isWordChar(c)) {
                    sb.append(NOT_AFTER_WORD);
                }
                sb.append(Pattern.quote(String.valueOf(c)));
                started = true;
                last = c;
                continue;
            }
            switch (c) {
                case '\\' -> quoted = true;
                case '*' -> {
                    sb.append("[^\\n]*");
                    started = true;
                    last = c;
                }
                case '?' -> {
                    if (!started && wholeWords) {
                        sb.append(NOT_AFTER_WORD);
                    }
                    sb.append("[^\\n]");
                    started = true;
                    last = c;
                }
                default -> {
                    if (!started && wholeWords && isWordChar(c)) {
                        sb.append(NOT_AFTER_WORD);
                    }
                    sb.append(Pattern.quote(String.valueOf(c)));
                    started = true;
                    last = c;
                }
            }
        }
        if (quoted) {
            sb.append("\\\\");
            last = '\\';
        }
        if (wholeWords && (isWordChar(last) || last == '?')) {
            sb.append(NOT_BEFORE_WORD);
        }
        return sb.toString();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
