package com.gluonhq.netbeans.nbfx.editor.processor.lex;

import com.gluonhq.netbeans.nbfx.editor.decoration.LineDecoration;
import com.gluonhq.netbeans.nbfx.editor.decoration.TokenCategory;
import com.gluonhq.netbeans.nbfx.editor.processor.SourceUtils;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.SourceContext;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.TextPosResult;
import jfx.incubator.scene.control.richtext.TextPos;
import org.netbeans.api.lexer.TokenHierarchy;

import java.util.ArrayList;
import java.util.List;

/**
 * The CSS counterpart of {@link JavaLexDecorationProcessor}: it scans a full CSS source
 * and produces the {@link LineDecoration}s for its selectors, pseudo-classes, property
 * names, strings and comments, and resolves caret-driven brace-match and
 * mark-occurrences requests.
 *
 * <p>The scan is a single character pass that buffers the words between structural
 * delimiters and classifies the whole run when the delimiter is reached: a run ended by
 * <code>{</code> is a selector (words directly preceded by a colon are pseudo-classes),
 * a run ended by <code>;</code>, <code>}</code> or end of file is a declaration (words
 * before its first colon form the property name, the rest is the value) — or, without a
 * colon, an at-rule statement highlighted like a selector (e.g. {@code @import "theme.css";}).</p>
 *
 * <p>Brace matching delegates to {@link BraceMatcher} over a Java {@link TokenHierarchy}
 * of the CSS text — the Java lexer tokenizes CSS braces, strings and block comments well
 * enough, so no dedicated CSS lexer is needed. Mark-occurrences applies only to
 * class/selector names: property names and values are never highlighted.</p>
 */
public class CssLexDecorationProcessor extends BaseLexDecorationProcessor {

    /** Words in the pending run: selector/property/value names, pseudo-classes and strings. */
    private enum WordType { WORD, PSEUDO, STRING, COLON }

    private record Word(WordType type, int start, int end) {
    }

    /** Global {@code [start, end)} bounds of every class/selector name (the mark-occurrences targets). */
    private List<int[]> selectorRanges;

    /**
     * @param context a {@link SourceContext} with a Java-lexer {@link TokenHierarchy} —
     *                the Java lexer tokenizes CSS braces, strings and block comments well
     *                enough for {@link BraceMatcher}, so no dedicated CSS lexer is needed
     */
    public CssLexDecorationProcessor(SourceContext context) {
        super(context);
    }

    @Override
    protected void onInvalidated() {
        selectorRanges = null;
    }

    /**
     * Finds every whole-name occurrence of the class/selector name at the caret.
     * Only selector names are considered: a caret on a property name or value
     * yields no occurrences.
     */
    @Override
    public List<TextPosResult> findOccurrenceResults(TextPos caret) {
        String source = source();
        if (source == null || source.isEmpty() || caret == null) {
            return List.of();
        }
        ensureAnalyzed();
        int[] lineStarts = context.lineStarts();
        int caretOffset = caretOffset(caret);
        String word = selectorRanges.stream()
                .filter(r -> r[0] <= caretOffset && caretOffset <= r[1])
                .findFirst()
                .map(r -> source.substring(r[0], r[1]))
                .orElse(null);
        if (word == null) {
            return List.of();
        }
        String style = TokenCategory.OCCURRENCE.style();
        return selectorRanges.stream()
                .filter(r -> word.equals(source.substring(r[0], r[1])))
                .map(r -> TextPosResult.from(r[0], r[1], lineStarts, style))
                .toList();
    }

    @Override
    protected List<List<LineDecoration>> analyze() {
        String source = source();
        int[] lineStarts = context.lineStarts();
        int[] lineLengths = context.lineLengths();
        selectorRanges = new ArrayList<>();
        List<List<LineDecoration>> results = new ArrayList<>(lineStarts.length);
        for (int i = 0; i < lineStarts.length; i++) {
            results.add(new ArrayList<>());
        }
        Emitter emitter = (start, end, category) -> {
            if (category == TokenCategory.CSS_SELECTOR) {
                selectorRanges.add(new int[] { start, end });
            }
            TextPosResult.toLineDecorationMap(start, end, lineStarts, lineLengths, category.style())
                    .forEach((line, list) -> results.get(line).addAll(list));
        };

        List<Word> run = new ArrayList<>();
        int i = 0;
        int length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
                emitter.emit(i, end, TokenCategory.COMMENT);
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = SourceUtils.skipString(source, i);
                run.add(new Word(WordType.STRING, i, end));
                i = end;
            } else if (c == '{') {
                classifySelector(run, emitter);
                run.clear();
                i++;
            } else if (c == '}' || c == ';') {
                classifyDeclaration(run, emitter);
                run.clear();
                i++;
            } else if (isWordChar(c)) {
                int start = i;
                while (i < length && isWordChar(source.charAt(i))) {
                    i++;
                }
                boolean afterColon = start > 0 && source.charAt(start - 1) == ':';
                run.add(new Word(afterColon ? WordType.PSEUDO : WordType.WORD, start, i));
            } else {
                if (c == ':') {
                    run.add(new Word(WordType.COLON, i, i + 1));
                }
                i++;
            }
        }
        // A trailing run (mid-edit, no delimiter yet) is classified as a declaration.
        classifyDeclaration(run, emitter);
        return results;
    }

    /** Styles a run ended by <code>{</code>: selector names, with pseudo-classes in italics. */
    private static void classifySelector(List<Word> run, Emitter emitter) {
        for (Word word : run) {
            switch (word.type()) {
                case WORD -> emitter.emit(word.start(), word.end(), TokenCategory.CSS_SELECTOR);
                case PSEUDO -> emitter.emit(word.start(), word.end(), TokenCategory.CSS_PSEUDO);
                case STRING -> emitter.emit(word.start(), word.end(), TokenCategory.STRING);
                case COLON -> {
                }
            }
        }
    }

    /**
     * Styles a run ended by <code>;</code> / <code>}</code>: the words before the first
     * colon form the property name, the value after it stays plain except string
     * literals. A run without a colon (an at-rule statement such as
     * {@code @import "theme.css";}) is styled like a selector.
     */
    private static void classifyDeclaration(List<Word> run, Emitter emitter) {
        int colon = -1;
        for (int i = 0; i < run.size(); i++) {
            if (run.get(i).type() == WordType.COLON) {
                colon = i;
                break;
            }
        }
        if (colon < 0) {
            classifySelector(run, emitter);
            return;
        }
        for (int i = 0; i < run.size(); i++) {
            Word word = run.get(i);
            if (i < colon && (word.type() == WordType.WORD || word.type() == WordType.PSEUDO)) {
                emitter.emit(word.start(), word.end(), TokenCategory.CSS_PROPERTY);
            } else if (i > colon && word.type() == WordType.STRING) {
                emitter.emit(word.start(), word.end(), TokenCategory.STRING);
            }
        }
    }

    /** Name characters of selectors, properties, at-rules and value words. */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '@' || c == '%';
    }

    /** Receives the styled global character ranges found by the scan. */
    private interface Emitter {

        void emit(int start, int end, TokenCategory category);
    }
}
