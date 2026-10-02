package com.gluonhq.netbeans.nbfx.findinprojects.model;

/**
 * One occurrence of the search text in a file - NetBeans' {@code TextDetail}. Offsets count
 * characters in the file's text with {@code '\n'} line separators (the editor's convention);
 * {@code line} and {@code column} are 1-based, as shown to the user.
 *
 * @param start     the offset of the first matched character
 * @param end       the offset after the last matched character
 * @param line      the line the match starts on
 * @param column    the column the match starts at, in that line
 * @param lineStart the offset of that line's first character
 * @param lineText  the text of that line, without its separator
 */
public record TextMatch(int start, int end, int line, int column, int lineStart, String lineText) {

    public int length() {
        return end - start;
    }

    /** Whether the match continues past the end of its first line. */
    public boolean isMultiline() {
        return end > lineStart + lineText.length();
    }
}
