package com.gluonhq.netbeans.nbfx.editor.processor.lex;

import com.gluonhq.netbeans.nbfx.editor.decoration.LineDecoration;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.SourceContext;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.TextPosResult;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.List;
import java.util.Objects;

/**
 * Base class of the language-specific lex decoration processors: each one tokenizes a
 * full text source and produces the {@link LineDecoration}s for its tokens, cached per
 * line and lazily rebuilt after {@link #invalidate()}.
 *
 * <p>The source text and its lexical artifacts (line starts / lengths, token hierarchy)
 * live in the {@link SourceContext} the processor is created with — the Java processor
 * shares a {@code JavaSourceContext} with the semantic processors, the standalone
 * processors (CSS, XML) own a plain one.</p>
 *
 * <p>Brace matching walks the context's token hierarchy with the {@link BraceMatcher}
 * (no results for languages without a Java-lexer hierarchy); mark occurrences return no
 * results until a subclass overrides them.</p>
 */
public abstract class BaseLexDecorationProcessor {

    protected final SourceContext context;

    private List<List<LineDecoration>> cachedLines;

    protected BaseLexDecorationProcessor(SourceContext context) {
        this.context = Objects.requireNonNull(context);
    }

    /** Returns the {@link SourceContext} holding the source and its lexical artifacts. */
    public final SourceContext context() {
        return context;
    }

    /**
     * Updates the cached source. When the new text differs from the currently cached
     * one, every derived artifact is nullified and rebuilt on next access.
     *
     * @param newSource the new source text, or {@code null} to invalidate
     */
    public final void setSource(String newSource) {
        if (Objects.equals(context.source(), newSource)) {
            return;
        }
        context.setSource(newSource);
        cachedLines = null;
        onInvalidated();
    }

    /** Clears the cached source and every derived artifact. Called when the document content changes. */
    public final void invalidate() {
        context.invalidate();
        cachedLines = null;
        onInvalidated();
    }

    /** Returns the source of the last scan, or {@code null} after {@link #invalidate()}. */
    public final String source() {
        return context.source();
    }

    /**
     * Returns the cached {@link LineDecoration}s for the given paragraph index.
     * If the cache is empty, the full source is re-analyzed first.
     *
     * @param lineIndex  the paragraph index
     * @return decorations for the line, or an empty list if out of range
     */
    public final List<LineDecoration> getLineDecorations(int lineIndex) {
        ensureAnalyzed();
        if (lineIndex < 0 || lineIndex >= cachedLines.size()) {
            return List.of();
        }
        return cachedLines.get(lineIndex);
    }

    /**
     * Finds the matching brace pair for the brace token adjacent to the caret, walking
     * the context's token hierarchy; no results for languages whose hierarchy has no
     * Java token sequence (e.g. XML).
     *
     * @param caret the current caret position in document coordinates
     * @return 0, 1 (unmatched origin) or 2 (origin + match) single-character
     *         {@link TextPosResult}s styled with the brace-match / brace-mismatch style
     */
    public List<TextPosResult> findBraceMatchResults(TextPos caret) {
        String source = source();
        if (source == null || source.isEmpty() || caret == null) {
            return List.of();
        }
        BraceMatcher.BraceMatch bm = BraceMatcher.findMatch(context.hierarchy(), caretOffset(caret));
        if (bm == null) {
            return List.of();
        }
        int[] lineStarts = context.lineStarts();
        String style = bm.style();
        TextPosResult origin = TextPosResult.from(bm.origin(), bm.origin() + 1, lineStarts, style);
        if (!bm.matched()) {
            return List.of(origin);
        }
        TextPosResult match = TextPosResult.from(bm.match(), bm.match() + 1, lineStarts, style);
        return List.of(origin, match);
    }

    /**
     * Finds every occurrence of the name at the caret; no results for languages
     * whose occurrences are computed elsewhere (or not at all).
     *
     * @param caret the current caret position in document coordinates
     * @return the occurrence highlight ranges, or an empty list
     */
    public List<TextPosResult> findOccurrenceResults(TextPos caret) {
        return List.of();
    }

    /** Converts a caret position into a global character offset. */
    protected final int caretOffset(TextPos caret) {
        String source = context.source();
        int[] lineStarts = context.lineStarts();
        int lineStart = caret.index() < lineStarts.length
                ? lineStarts[caret.index()] : source.length();
        return Math.min(lineStart + caret.offset(), source.length());
    }

    /** Rebuilds the per-line decoration cache when it was invalidated. */
    protected final void ensureAnalyzed() {
        if (cachedLines == null) {
            cachedLines = analyze();
        }
    }

    /** Tokenizes the full source and returns the text-style decorations grouped by line. */
    protected abstract List<List<LineDecoration>> analyze();

    /** Invalidation hook for the subclass' derived artifacts (selector ranges...). */
    protected void onInvalidated() {
    }
}
