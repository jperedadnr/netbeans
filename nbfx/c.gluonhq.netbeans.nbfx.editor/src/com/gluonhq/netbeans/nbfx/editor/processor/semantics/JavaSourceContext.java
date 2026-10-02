package com.gluonhq.netbeans.nbfx.editor.processor.semantics;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.queries.SourceLevelQuery;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenHierarchy;
import org.openide.filesystems.FileObject;

/**
 * A {@link SourceContext} for Java sources that adds the semantic artifacts on top of
 * the lexical ones: the {@link ClasspathInfo}, the in-memory {@link FileObject} and
 * {@link JavaSource} of the current text, and the Java lexer input attributes. A single
 * instance is owned by the {@code JavaSyntaxDecorator} and passed to every processor
 * (lex, semantic) so they all reuse the same artifacts.
 * <p>
 * A {@link Snapshot} is an immutable bundle of the source and artifacts at a given instant, useful for background
 * tasks that require an immutable context.
 * </p>
 */
public final class JavaSourceContext extends SourceContext {

    private static final Logger LOG = Logger.getLogger(JavaSourceContext.class.getName());

    /**
     * Immutable bundle of the source artifacts at a point in time, intended
     * for background tasks
     */
    public record Snapshot(String source, JavaSource javaSource, int[] lineStarts, int[] lineLengths) {}

    private final InputAttributes lexerAttributes;

    private ClasspathInfo cpInfo;
    private FileObject inMemoryFo;
    private JavaSource javaSource;

    public JavaSourceContext(FileObject fileObject) {
        super(Objects.requireNonNull(fileObject), JavaTokenId.language());
        this.cpInfo = ClasspathInfo.create(fileObject);
        this.lexerAttributes = createLexerAttributes(fileObject);
    }

    public synchronized ClasspathInfo classpathInfo() {
        return cpInfo;
    }

    /**
     * Rebuilds the {@link ClasspathInfo} from the current state of the project and drops the cached
     * {@link JavaSource} so the next analysis uses it. Needed when the editor was opened before the
     * owning project finished opening (or before the java indexer finished scanning): the original
     * ClasspathInfo captured empty or partial classpaths and would keep them forever.
     */
    public synchronized void refreshClasspath() {
        cpInfo = ClasspathInfo.create(fileObject());
        clearArtifacts();
    }

    @Override
    protected synchronized void clearArtifacts() {
        inMemoryFo = null;
        javaSource = null;
    }

    @Override
    protected TokenHierarchy<?> createHierarchy(String source, Language<?> language) {
        return TokenHierarchy.create(source, false, JavaTokenId.language(), null, lexerAttributes);
    }

    /**
     * Returns a {@link JavaSource} for the current source text.
     *
     * @return the cached {@link JavaSource}, or {@code null} when the source
     *         is empty or the in-memory FS could not be built
     */
    public synchronized JavaSource javaSource() {
        String source = source();
        if (source == null || source.isEmpty()) {
            return null;
        }
        if (javaSource == null) {
            try {
                inMemoryFo = InMemoryFileSystem.createFileObject(fileObject(), source);
                javaSource = JavaSource.create(cpInfo, inMemoryFo);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not create in-memory JavaSource", ex);
            }
        }
        return javaSource;
    }

    /**
     * Create an immutable snapshot with the current source and artifacts, so background tasks
     * operate on a consistent set of values, given that other threads could invalidate the context at any time.
     */
    public synchronized Snapshot snapshot() {
        String source = source();
        if (source == null || source.isEmpty()) {
            return null;
        }
        return new Snapshot(source, javaSource(), lineStarts(), lineLengths());
    }

    public static InputAttributes createLexerAttributes(FileObject fo) {
        InputAttributes attrs = new InputAttributes();
        // enable module keywords if the file is module-info
        attrs.setValue(JavaTokenId.language(), "fileName",
                (Supplier<String>) fo::getNameExt, false);
        // enable all language features up to the file source level
        String sourceLevel = SourceLevelQuery.getSourceLevel(fo);
        attrs.setValue(JavaTokenId.language(), "version",
                (Supplier<String>) () -> sourceLevel != null ? sourceLevel : String.valueOf(Runtime.version().feature()),
                false);
        return attrs;
    }

    /**
     * Convenience method to update source and run a semantic JavaSource task only when a snapshot is available.
     *
     * @return {@code true} when the task was executed, {@code false} when source/snapshot JavaSource is unavailable
     */
    public boolean runSemanticTask(String newSource,
                                   CancellableTask<CompilationController> task,
                                   boolean shared) throws IOException {
        Snapshot snapshot;
        synchronized (this) {
            setSource(newSource);
            snapshot = snapshot();
        }
        return runSemanticTask(snapshot, task, shared);
    }

    /**
     * Convenience method to run a semantic JavaSource task for an existing snapshot.
     *
     * @return {@code true} when the task was executed, {@code false} when snapshot JavaSource is unavailable
     */
    public static boolean runSemanticTask(Snapshot snapshot,
                                          CancellableTask<CompilationController> task,
                                          boolean shared) throws IOException {
        if (snapshot == null || snapshot.javaSource() == null) {
            return false;
        }
        SilentJavacLogs.installOnce();
        try {
            snapshot.javaSource().runUserActionTask(task, shared);
        } catch (IOException | RuntimeException ex) {
            if (SilentJavacLogs.isKnownTransientJavacBug(ex)) {
                // Mid-edit state triggers a known javac NPE (e.g. `@SuppressWarnings`
                // without parentheses crashes Lint.suppressionsFrom). Demote to FINE
                // so it doesn't spam the console.
                LOG.log(Level.FINE, () -> "Skipping transient javac failure on incomplete source: "
                        + SilentJavacLogs.rootCauseSummary(ex));
                return false;
            }
            LOG.log(Level.SEVERE, "Exception while running semantic task: " + ex.getMessage(), ex);
            throw new IOException("Exception while running semantic task: ", ex);
        }
        return true;
    }
}
