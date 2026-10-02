package com.gluonhq.netbeans.nbfx.api.search;

/**
 * How a search matches and replaces - the toggles of NetBeans' search and replace bars.
 *
 * @param matchCase    letters must match in case (else the search ignores case)
 * @param wholeWords   a match may not start or end inside an identifier
 * @param regex        the query is a {@link java.util.regex.Pattern}; the replacement may use
 *                     {@code $1} group references
 * @param wrapAround   a search past the end (start) continues from the start (end) of the text
 * @param highlight    every match is highlighted in the editor
 * @param backwards    Enter and Replace move to the previous match instead of the next
 * @param preserveCase a replacement takes the case shape of the text it replaces (all caps,
 *                     capitalised, ...); only when neither {@code matchCase} nor {@code regex}
 */
public record SearchOptions(boolean matchCase, boolean wholeWords, boolean regex, boolean wrapAround,
        boolean highlight, boolean backwards, boolean preserveCase) {

    /** NetBeans' defaults: ignore case, wrap around, highlight, forward. */
    public static final SearchOptions DEFAULT = new SearchOptions(false, false, false, true, true, false, false);

    public SearchOptions withMatchCase(boolean on) {
        return new SearchOptions(on, wholeWords, regex, wrapAround, highlight, backwards, preserveCase);
    }

    public SearchOptions withWholeWords(boolean on) {
        return new SearchOptions(matchCase, on, regex, wrapAround, highlight, backwards, preserveCase);
    }

    public SearchOptions withRegex(boolean on) {
        return new SearchOptions(matchCase, wholeWords, on, wrapAround, highlight, backwards, preserveCase);
    }

    public SearchOptions withWrapAround(boolean on) {
        return new SearchOptions(matchCase, wholeWords, regex, on, highlight, backwards, preserveCase);
    }

    public SearchOptions withHighlight(boolean on) {
        return new SearchOptions(matchCase, wholeWords, regex, wrapAround, on, backwards, preserveCase);
    }

    public SearchOptions withBackwards(boolean on) {
        return new SearchOptions(matchCase, wholeWords, regex, wrapAround, highlight, on, preserveCase);
    }

    public SearchOptions withPreserveCase(boolean on) {
        return new SearchOptions(matchCase, wholeWords, regex, wrapAround, highlight, backwards, on);
    }
}
