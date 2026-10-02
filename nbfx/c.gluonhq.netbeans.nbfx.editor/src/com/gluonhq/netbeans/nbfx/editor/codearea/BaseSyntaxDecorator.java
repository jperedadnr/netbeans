package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.editor.decoration.LineDecoration;
import com.gluonhq.netbeans.nbfx.editor.decoration.MarkedDecoration;
import com.gluonhq.netbeans.nbfx.editor.processor.SourceUtils;
import com.gluonhq.netbeans.nbfx.editor.processor.lex.BaseLexDecorationProcessor;
import com.gluonhq.netbeans.nbfx.editor.processor.semantics.TextPosResult;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.SelectionSegment;
import jfx.incubator.scene.control.richtext.Marker;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import org.openide.filesystems.FileObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base class of the language-specific {@link SyntaxDecorator}s used by the {@link CodeEditor}.
 * It owns the file's {@link BaseLexDecorationProcessor} and implements the caret-driven API the
 * editor calls without knowing the language with lex-based defaults: line decorations,
 * source refresh and cache invalidation, brace-match and mark-occurrences highlights —
 * plus safe no-op diagnostics lookups — sharing the caret-highlight bookkeeping: the
 * current brace / occurrence {@link MarkedDecoration}s, their compare-swap-fire
 * update, and their per-line overlay used from {@code createRichParagraph}.
 *
 * <p>All public methods must be called on the JavaFX Application Thread.</p>
 */
public abstract class BaseSyntaxDecorator implements SyntaxDecorator {

    /** The lex decoration processor matching the file's language. */
    protected final BaseLexDecorationProcessor lexProcessor;

    private List<MarkedDecoration> braceDecorations = List.of();
    private List<MarkedDecoration> occurrenceDecorations = List.of();
    private List<MarkedDecoration> searchDecorations = List.of();
    /** The selected occurrence left un-highlighted so the selection colour stays visible; {@code null} for none. */
    private SelectionSegment occurrenceExclusion;
    /** The last occurrences as computed, before the exclusion is applied. */
    private List<MarkedDecoration> allOccurrenceDecorations = List.of();

    protected BaseSyntaxDecorator(FileObject fo) {
        this.lexProcessor = getLexProcessor(fo);
    }

    protected abstract BaseLexDecorationProcessor getLexProcessor(FileObject fo);

    /**
     * Builds the styled paragraph for the given index: refreshes the cached source
     * when needed and renders the language's {@link #lineDecorations} on the text.
     */
    @Override
    public final RichParagraph createRichParagraph(CodeTextModel model, int index) {
        String text = Objects.requireNonNull(model).getPlainText(index);
        if (text == null || text.isEmpty()) {
            return RichParagraph.builder().build();
        }
        ensureSource(model);
        return LineDecoration.getRichParagraph(text, lineDecorations(index, text));
    }

    /** Refreshes the cached source from the model when it was invalidated. */
    protected void ensureSource(CodeTextModel model) {
        if (lexProcessor.source() == null) {
            lexProcessor.setSource(SourceUtils.getFullText(model));
        }
    }

    /**
     * Returns every decoration of the given paragraph — the language's text styles
     * plus the {@link #caretOverlays} — in the order they must be rendered. The
     * default merges the lex decorations with the caret overlays; languages with
     * extra decoration sources (e.g. Java semantic highlights) override it.
     *
     * @param index the paragraph index
     * @param text  the (non-empty) paragraph text
     */
    protected List<LineDecoration> lineDecorations(int index, String text) {
        List<LineDecoration> lexDecorations = lexProcessor.getLineDecorations(index);
        List<LineDecoration> caretOverlays = caretOverlays(index, text.length());
        if (caretOverlays.isEmpty()) {
            return lexDecorations;
        }
        List<LineDecoration> decorations = new ArrayList<>(lexDecorations);
        decorations.addAll(caretOverlays);
        return decorations;
    }

    /**
     * Invalidates the lex processor's caches and drops the caret highlights when the
     * document content changes; they are lazily rebuilt on the next access.
     */
    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
            int charsTop, int linesAdded, int charsBottom) {
        lexProcessor.invalidate();
        resetCaretDecorations();
    }

    /**
     * Triggers a background semantic analysis of the full source; no-op for
     * languages without one.
     */
    public void analyzeInBackground(CodeTextModel model) {
    }

    /**
     * Recomputes the brace-match highlight for the caret position from the lex
     * processor's brace matching.
     */
    public void updateBraceMatch(CodeTextModel model, TextPos caret) {
        if (model == null || caret == null) {
            return;
        }
        ensureSource(model);
        applyBraceDecorations(model,
                TextPosResult.toMarkedDecorations(lexProcessor.findBraceMatchResults(caret), model));
    }

    /**
     * Recomputes the mark-occurrences highlight for the caret position from the lex
     * processor's occurrences; languages that compute them semantically override it.
     */
    public void updateOccurrencesInBackground(CodeTextModel model, TextPos caret) {
        if (model == null || caret == null) {
            return;
        }
        ensureSource(model);
        applyOccurrenceDecorations(model,
                TextPosResult.toMarkedDecorations(lexProcessor.findOccurrenceResults(caret), model));
    }

    /**
     * Clears any active brace-match highlight and refreshes the previously
     * affected paragraphs.
     */
    public void clearBraceMatch(CodeTextModel model) {
        applyBraceDecorations(model, List.of());
    }

    /**
     * Clears any active mark-occurrences highlights and refreshes the previously
     * affected paragraphs.
     */
    public void clearOccurrences(CodeTextModel model) {
        applyOccurrenceDecorations(model, List.of());
    }

    /**
     * Returns the error/warning message at the given paragraph index and character
     * offset, or {@code null} when no diagnostic covers that position (always, for
     * languages without diagnostics).
     */
    public String getErrorMessageAt(int lineIndex, int charOffset) {
        return null;
    }

    /**
     * Returns a combined error/warning message for the given paragraph, or
     * {@code null} when no diagnostics touch that line (always, for languages
     * without diagnostics).
     */
    public String getErrorMessagesForLine(int lineIndex) {
        return null;
    }

    /**
     * Returns {@code "error"}, {@code "warning"}, or {@code null} for the given
     * paragraph ({@code null} always, for languages without diagnostics).
     */
    public String getErrorSeverityOnLine(int lineIndex) {
        return null;
    }

    /**
     * Replaces the brace-match decorations, firing a targeted style change for the
     * affected paragraphs when they differ from the current ones.
     */
    protected final void applyBraceDecorations(CodeTextModel model, List<MarkedDecoration> newDecorations) {
        if (MarkedDecoration.sameDecorations(newDecorations, braceDecorations)) {
            return;
        }
        int[] range = MarkedDecoration.lineRange(braceDecorations, newDecorations);
        braceDecorations = newDecorations;
        fireCaretHighlightChange(model, range);
    }

    /**
     * Replaces the mark-occurrences decorations, firing a targeted style change for
     * the affected paragraphs when they differ from the current ones.
     */
    /**
     * Leaves the occurrence equal to {@code selection} out of the highlights (the double-clicked
     * identifier keeps the selection colour, as in NetBeans); {@code null} highlights them all.
     * Re-filters the current occurrences at once (so a Find Next landing on a marked occurrence
     * does not show it green until the occurrences are recomputed) and the next ones as they come.
     */
    public final void setOccurrenceExclusion(CodeTextModel model, SelectionSegment selection) {
        if (Objects.equals(occurrenceExclusion, selection)) {
            return;
        }
        occurrenceExclusion = selection;
        applyOccurrenceDecorations(model, allOccurrenceDecorations);
    }

    protected final void applyOccurrenceDecorations(CodeTextModel model, List<MarkedDecoration> newDecorations) {
        allOccurrenceDecorations = newDecorations;
        SelectionSegment excluded = occurrenceExclusion;
        if (excluded != null && !newDecorations.isEmpty()) {
            newDecorations = newDecorations.stream()
                    .filter(d -> !(matches(d.start(), excluded.getMin()) && matches(d.end(), excluded.getMax())))
                    .toList();
        }
        if (MarkedDecoration.sameDecorations(newDecorations, occurrenceDecorations)) {
            return;
        }
        int[] range = MarkedDecoration.lineRange(occurrenceDecorations, newDecorations);
        occurrenceDecorations = newDecorations;
        fireCaretHighlightChange(model, range);
    }

    private static boolean matches(Marker marker, TextPos pos) {
        return marker.getIndex() == pos.index() && marker.getOffset() == pos.offset();
    }

    /**
     * Replaces the search bar's match highlights, firing a targeted style change for the
     * affected paragraphs when they differ from the current ones. Unlike the caret highlights
     * they survive edits (their markers follow the text) until replaced or cleared.
     */
    public final void applySearchDecorations(CodeTextModel model, List<MarkedDecoration> newDecorations) {
        if (MarkedDecoration.sameDecorations(newDecorations, searchDecorations)) {
            return;
        }
        int[] range = MarkedDecoration.lineRange(searchDecorations, newDecorations);
        searchDecorations = newDecorations;
        fireCaretHighlightChange(model, range);
    }

    /** Clears the search bar's match highlights and refreshes the paragraphs they covered. */
    public final void clearSearchDecorations(CodeTextModel model) {
        applySearchDecorations(model, List.of());
    }

    /** The search bar's current match highlights (empty when the bar is closed or highlighting is off). */
    public final List<MarkedDecoration> searchDecorations() {
        return searchDecorations;
    }

    /**
     * Drops the brace / occurrence decorations without firing (they are recomputed on
     * the next caret update); called from {@code handleChange} when the text changed.
     */
    protected final void resetCaretDecorations() {
        braceDecorations = List.of();
        occurrenceDecorations = List.of();
        allOccurrenceDecorations = List.of();
    }

    /**
     * Returns the line-local brace, occurrence and search-match overlays for a paragraph, to be
     * appended after the language's text-style decorations in
     * {@code createRichParagraph}.
     */
    protected final List<LineDecoration> caretOverlays(int lineIndex, int lineLength) {
        if (braceDecorations.isEmpty() && occurrenceDecorations.isEmpty() && searchDecorations.isEmpty()) {
            return List.of();
        }
        List<LineDecoration> overlays = new ArrayList<>();
        for (MarkedDecoration decoration : braceDecorations) {
            if (decoration.touchesLine(lineIndex)) {
                overlays.add(decoration.toLineLocal(lineIndex, lineLength));
            }
        }
        for (MarkedDecoration decoration : occurrenceDecorations) {
            if (decoration.touchesLine(lineIndex)) {
                overlays.add(decoration.toLineLocal(lineIndex, lineLength));
            }
        }
        for (MarkedDecoration decoration : searchDecorations) {
            if (decoration.touchesLine(lineIndex)) {
                overlays.add(decoration.toLineLocal(lineIndex, lineLength));
            }
        }
        return overlays;
    }

    /** Fires a style-change event for the given {@code [minLine, maxLine]} range. */
    protected final void fireCaretHighlightChange(CodeTextModel model, int[] range) {
        if (model == null || range == null) {
            return;
        }
        int minLine = range[0];
        int maxLine = Math.clamp(model.size() - 1, 0, range[1]);
        String last = model.getPlainText(maxLine);
        model.fireStyleChangeEvent(
                TextPos.ofLeading(minLine, 0),
                TextPos.ofLeading(maxLine, last == null ? 0 : last.length()));
    }
}
