package com.gluonhq.netbeans.nbfx.api.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A compiled search over plain text - the matching part of NetBeans' {@code EditorFindSupport} /
 * {@code DocumentFinder}. Offsets are character offsets in the text handed to each call, which is
 * the editor's text with {@code '\n'} separators.
 * <p>
 * The query is a literal (the search ignores case unless {@link SearchOptions#matchCase}) or,
 * with {@link SearchOptions#regex}, a {@link Pattern} in {@link Pattern#MULTILINE} mode. Whole
 * words apply to literals only, as in NetBeans: a match may not touch an identifier character on
 * either side. Empty matches a pattern may produce are skipped.
 */
public final class TextSearch {

    /** A match: {@code [start, end)} in the text, and whether the search wrapped around to reach it. */
    public record Match(int start, int end, boolean wrapped) {

        public int length() {
            return end - start;
        }

        Match afterWrap() {
            return new Match(start, end, true);
        }
    }

    private static final Pattern REPLACEMENT_ESCAPES = Pattern.compile("\\\\([nrt])");

    private final String query;
    private final SearchOptions options;
    private final Pattern pattern;

    private TextSearch(String query, SearchOptions options, Pattern pattern) {
        this.query = query;
        this.options = options;
        this.pattern = pattern;
    }

    /**
     * Compiles {@code query} with {@code options}. An empty query compiles to a search that never
     * matches ({@link #isEmpty()}).
     *
     * @throws PatternSyntaxException for a malformed regular expression
     */
    public static TextSearch compile(String query, SearchOptions options) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(options);
        if (query.isEmpty()) {
            return new TextSearch(query, options, null);
        }
        int flags = Pattern.MULTILINE;
        if (!options.matchCase()) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        String regex;
        if (options.regex()) {
            regex = query;
        } else if (options.wholeWords()) {
            regex = "(?<!\\p{javaJavaIdentifierPart})" + Pattern.quote(query) + "(?!\\p{javaJavaIdentifierPart})";
        } else {
            regex = Pattern.quote(query);
        }
        return new TextSearch(query, options, Pattern.compile(regex, flags));
    }

    public String getQuery() {
        return query;
    }

    public SearchOptions getOptions() {
        return options;
    }

    /** {@code true} for the empty query, which matches nothing. */
    public boolean isEmpty() {
        return pattern == null;
    }

    /**
     * The next match in the search's direction ({@link SearchOptions#backwards}): the first one
     * starting at or after {@code from}, or the last one ending at or before it.
     */
    public Match find(CharSequence text, int from) {
        return options.backwards() ? findPrevious(text, from) : findNext(text, from);
    }

    /**
     * The first match starting at or after {@code from}; with {@link SearchOptions#wrapAround},
     * the first match of the text when there is none after {@code from}. {@code null} when
     * nothing matches.
     */
    public Match findNext(CharSequence text, int from) {
        if (isEmpty()) {
            return null;
        }
        Match match = firstFrom(text, Math.max(0, Math.min(from, text.length())));
        if (match == null && options.wrapAround() && from > 0) {
            Match first = firstFrom(text, 0);
            return first == null ? null : first.afterWrap();
        }
        return match;
    }

    /**
     * The last match ending at or before {@code from}; with {@link SearchOptions#wrapAround}, the
     * last match of the text when there is none before {@code from}. {@code null} when nothing
     * matches.
     */
    public Match findPrevious(CharSequence text, int from) {
        if (isEmpty()) {
            return null;
        }
        Match last = null;
        Match lastOfAll = null;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                continue;
            }
            Match match = new Match(matcher.start(), matcher.end(), false);
            lastOfAll = match;
            if (match.end() <= from) {
                last = match;
            }
        }
        if (last == null && options.wrapAround() && lastOfAll != null && lastOfAll.end() > from) {
            return lastOfAll.afterWrap();
        }
        return last;
    }

    /** Every match of the text, in order - for the highlights and the "N of M" counter. */
    public List<Match> findAll(CharSequence text) {
        List<Match> matches = new ArrayList<>();
        if (isEmpty()) {
            return matches;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.end() > matcher.start()) {
                matches.add(new Match(matcher.start(), matcher.end(), false));
            }
        }
        return matches;
    }

    /** The index of the match containing {@code [start, end)} in {@code matches}, or -1. */
    public static int indexOf(List<Match> matches, int start, int end) {
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            if (match.start() == start && match.end() == end) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The text to put in place of {@code match}. A regular expression replacement resolves group
     * references ({@code $1}, {@code ${name}}) and the escapes {@code \n}, {@code \r}, {@code \t};
     * otherwise {@code replaceWith} is taken literally, reshaped to the case of the matched text
     * when {@link SearchOptions#preserveCase} applies (ignore-case searches only): all upper, all
     * lower, capitalised, or lower initial with the rest upper, as NetBeans does.
     *
     * @throws IllegalArgumentException for a group reference the pattern has no group for
     */
    public String replacement(CharSequence text, Match match, String replaceWith) {
        Objects.requireNonNull(match);
        Objects.requireNonNull(replaceWith);
        if (isEmpty()) {
            return replaceWith;
        }
        if (options.regex()) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find(match.start()) || matcher.start() != match.start() || matcher.end() != match.end()) {
                return replaceWith;
            }
            StringBuilder sb = new StringBuilder();
            try {
                matcher.appendReplacement(sb, unescape(replaceWith));
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            // appendReplacement copies the text before the match first
            return sb.substring(match.start());
        }
        if (options.preserveCase() && !options.matchCase() && !replaceWith.isEmpty()) {
            return preserveCase(text.subSequence(match.start(), match.end()).toString(), replaceWith);
        }
        return replaceWith;
    }

    /** {@code \n}, {@code \r} and {@code \t} in a regex replacement stand for the characters; {@code \\} stays an escape. */
    private static String unescape(String replaceWith) {
        StringBuilder sb = new StringBuilder(replaceWith.length());
        for (int i = 0; i < replaceWith.length(); i++) {
            char c = replaceWith.charAt(i);
            if (c == '\\' && i + 1 < replaceWith.length()) {
                char next = replaceWith.charAt(i + 1);
                switch (next) {
                    case 'n' -> { sb.append('\n'); i++; continue; }
                    case 'r' -> { sb.append('\r'); i++; continue; }
                    case 't' -> { sb.append('\t'); i++; continue; }
                    default -> { sb.append(c).append(next); i++; continue; }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String preserveCase(String found, String replaceWith) {
        if (found.equals(found.toUpperCase())) {
            return replaceWith.toUpperCase();
        }
        if (found.equals(found.toLowerCase())) {
            return replaceWith.toLowerCase();
        }
        char first = found.charAt(0);
        if (Character.isUpperCase(first)) {
            return Character.toUpperCase(replaceWith.charAt(0)) + replaceWith.substring(1);
        }
        if (Character.isLowerCase(first)) {
            String rest = found.substring(1);
            if (rest.equals(rest.toUpperCase())) {
                return Character.toLowerCase(replaceWith.charAt(0)) + replaceWith.substring(1).toUpperCase();
            }
            return Character.toLowerCase(replaceWith.charAt(0)) + replaceWith.substring(1);
        }
        return replaceWith;
    }

    private Match firstFrom(CharSequence text, int from) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find(from)) {
            return null;
        }
        while (matcher.end() == matcher.start()) {
            if (!matcher.find()) {
                return null;
            }
        }
        return new Match(matcher.start(), matcher.end(), false);
    }
}
