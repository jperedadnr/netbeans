package com.gluonhq.netbeans.nbfx.editor.processor.semantics;

import com.gluonhq.netbeans.nbfx.editor.processor.SourceUtils;
import java.util.Objects;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenHierarchy;
import org.openide.filesystems.FileObject;

/**
 * For a given {@link FileObject}, the SourceContext keeps an up-to-date cache of the
 * source text and its lexical artifacts: the line-start / line-length arrays and the
 * {@link TokenHierarchy} built with the context's {@link Language}. A single instance
 * is owned by the file's lex decoration processor and shared with its syntax decorator.
 * <p>
 * Whenever the source text changes, invalidation clears the cache, and the artifacts
 * are recomputed on next access.
 * </p>
 * <p>
 * {@link JavaSourceContext} extends this class with the Java semantic artifacts
 * ({@code ClasspathInfo}, {@code JavaSource}, snapshots and semantic task helpers).
 * </p>
 */
public class SourceContext {

    private final FileObject fileObject;
    private final Language<?> language;

    private String source;
    private int[] lineStarts;
    private int[] lineLengths;
    private TokenHierarchy<?> hierarchy;

    /**
     * @param fileObject the file this context belongs to, may be {@code null} for
     *                   sources not backed by a file
     * @param language   the lexer language used to build the {@link TokenHierarchy}
     */
    public SourceContext(FileObject fileObject, Language<?> language) {
        this.fileObject = fileObject;
        this.language = Objects.requireNonNull(language);
    }

    public FileObject fileObject() {
        return fileObject;
    }

    /**
     * Updates the cached source. When the new text differs from the currently
     * cached one, every derived artifact is nullified and will be rebuilt on next access.
     *
     * @param newSource the new source text, or {@code null} to invalidate
     */
    public synchronized void setSource(String newSource) {
        if (Objects.equals(this.source, newSource)) {
            return;
        }
        this.source = newSource;
        this.lineStarts = null;
        this.lineLengths = null;
        this.hierarchy = null;
        clearArtifacts();
    }

    /** Clears the cached source and every derived artifact. */
    public void invalidate() {
        setSource(null);
    }

    /**
     * Gets the source of the current context
     */
    public synchronized String source() {
        return source;
    }

    /**
     * Returns an array with the offsets of the first position of each line
     */
    public synchronized int[] lineStarts() {
        requireSource();
        if (lineStarts == null) {
            lineStarts = SourceUtils.computeLineStarts(source);
        }
        return lineStarts;
    }

    /**
     * Returns an array with the length of each line
     */
    public synchronized int[] lineLengths() {
        requireSource();
        if (lineLengths == null) {
            lineLengths = SourceUtils.computeLineLengths(source, lineStarts());
        }
        return lineLengths;
    }

    /**
     * Return the {@link TokenHierarchy} for the current source.
     */
    public synchronized TokenHierarchy<?> hierarchy() {
        requireSource();
        if (hierarchy == null) {
            hierarchy = createHierarchy(source, language);
        }
        return hierarchy;
    }

    /**
     * Builds the {@link TokenHierarchy} for the given source; subclasses may attach
     * language-specific input attributes.
     */
    protected TokenHierarchy<?> createHierarchy(String source, Language<?> language) {
        return TokenHierarchy.create(source, language);
    }

    /** Invalidation hook for the subclass' derived artifacts, called from {@link #setSource}. */
    protected void clearArtifacts() {
    }

    protected final void requireSource() {
        if (source == null) {
            throw new IllegalStateException("SourceContext has no source; call setSource() first.");
        }
    }
}
