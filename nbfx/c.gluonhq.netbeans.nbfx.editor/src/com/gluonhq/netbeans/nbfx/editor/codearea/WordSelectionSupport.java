package com.gluonhq.netbeans.nbfx.editor.codearea;

import java.util.function.IntPredicate;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.SelectionSegment;
import jfx.incubator.scene.control.richtext.TextPos;

/**
 * Makes a double click (and {@link RichTextArea.Tag#SELECT_WORD}) select the Java identifier at
 * the caret, as NetBeans does, instead of the locale's {@code BreakIterator} word, which runs
 * through {@code .}, {@code _}-free punctuation and so selects {@code a.b.c} as one word. The
 * caret on blanks selects the run of blanks, on punctuation the run of that character.
 */
final class WordSelectionSupport {

    private WordSelectionSupport() {
    }

    static void install(RichTextArea area) {
        area.getInputMap().registerFunction(RichTextArea.Tag.SELECT_WORD, () -> selectIdentifier(area));
    }

    static void selectIdentifier(RichTextArea area) {
        TextPos caret = area.getCaretPosition();
        if (caret == null) {
            return;
        }
        String line = area.getPlainText(caret.index());
        int off = Math.min(caret.offset(), line.length());
        if (line.isEmpty()) {
            return;
        }
        // the character class to select: an identifier touching the caret wins, else the character
        // under it (the one on the left at the end of the line)
        char at = line.charAt(off < line.length() ? off : off - 1);
        boolean identifier = Character.isJavaIdentifierPart(at)
                || (off > 0 && Character.isJavaIdentifierPart(line.charAt(off - 1)));
        IntPredicate same = identifier ? c -> Character.isJavaIdentifierPart((char) c)
                : Character.isWhitespace(at) ? c -> Character.isWhitespace((char) c)
                : c -> c == at;
        int start = off;
        while (start > 0 && same.test(line.charAt(start - 1))) {
            start--;
        }
        int end = off;
        while (end < line.length() && same.test(line.charAt(end))) {
            end++;
        }
        if (start == end) {
            // between two different classes with nothing on the right: take the run on the left
            end = off;
            start = off - 1;
            while (start > 0 && same.test(line.charAt(start - 1))) {
                start--;
            }
        }
        area.select(TextPos.ofLeading(caret.index(), start), TextPos.ofLeading(caret.index(), end));
    }

    /**
     * Whether {@code selection} is exactly one identifier of the text (the double-click selection),
     * so that caret-driven highlights such as mark occurrences still apply to it.
     */
    static boolean isIdentifierSelection(RichTextArea area, SelectionSegment selection) {
        if (selection == null || selection.isCollapsed()) {
            return false;
        }
        TextPos min = selection.getMin();
        TextPos max = selection.getMax();
        if (min.index() != max.index()) {
            return false;
        }
        String line = area.getPlainText(min.index());
        int start = min.offset();
        int end = Math.min(max.offset(), line.length());
        if (start >= end
                || (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1)))
                || (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end)))) {
            return false;
        }
        for (int i = start; i < end; i++) {
            if (!Character.isJavaIdentifierPart(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
