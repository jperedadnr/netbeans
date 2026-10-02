package com.gluonhq.netbeans.nbfx.editor.codearea;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.ContentChange;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.netbeans.api.queries.FileEncodingQuery;
import org.openide.filesystems.FileObject;

/**
 * The state of an open file that every {@link CodeEditor} showing it shares - the counterpart of
 * NetBeans' {@code CloneableEditorSupport}: the text model (and with it the undo history and syntax
 * analysis), the saved baseline and dirty flag, the line separator, the watch for external
 * modifications, and saving.
 * <p>
 * Editors {@link #attach attach} to the buffer when created and {@link #detach detach} when
 * disposed; the buffer stops watching the file once its last editor is gone. There is no
 * <em>original</em> editor: cloned editors are all equal views of the same buffer. There is at most
 * one buffer per file ({@link #open}), so every editor of a file - previews included - shows the
 * same text.
 * <p>
 * The current text of every open buffer is published through {@link com.gluonhq.netbeans.nbfx.api.editor.OpenSources},
 * so that source analyses run outside the editor (such as Find Usages) can work on the unsaved edits.
 */
final class EditorBuffer {

    private static final Logger LOG = Logger.getLogger(EditorBuffer.class.getName());

    /**
     * The open buffers by file; a buffer leaves when its last editor detaches. Mutated on the FX
     * thread, read from any thread by {@link OpenSourcesImpl}.
     */
    private static final Map<FileObject, EditorBuffer> OPEN = new ConcurrentHashMap<>();

    /** The open files whose content differs from disk, for {@link OpenSourcesImpl}; any thread. */
    static Set<FileObject> modifiedFiles() {
        Set<FileObject> files = new HashSet<>();
        OPEN.forEach((file, buffer) -> {
            if (buffer.modifiedSnapshot) {
                files.add(file);
            }
        });
        return files;
    }

    /** The open buffer of {@code fileObject}, or {@code null}; any thread. */
    static EditorBuffer openBuffer(FileObject fileObject) {
        return OPEN.get(fileObject);
    }

    /** The buffer of {@code fileObject}: the one already open, else a new one. */
    static EditorBuffer open(FileObject fileObject) {
        EditorBuffer buffer = OPEN.get(fileObject);
        if (buffer == null) {
            buffer = new EditorBuffer(fileObject);
            OPEN.put(fileObject, buffer);
        }
        return buffer;
    }

    final FileObject fileObject;
    final CodeTextModel model;
    final BaseSyntaxDecorator decorator;

    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(this, "modified", false);
    /** Whether the editors accept edits; cleared when the file changes on disk under unsaved edits. */
    private final BooleanProperty editable = new SimpleBooleanProperty(this, "editable", true);
    private final LineSeparatorSupport lineSeparator;
    private final ExternalChangeSupport externalChanges;
    private final List<CodeEditor> views = new ArrayList<>();

    /**
     * The current text with {@code '\n'} line breaks and the dirty flag, as of the last change, for
     * readers on other threads ({@link OpenSourcesImpl}).
     */
    private volatile String snapshot = "";
    private volatile boolean modifiedSnapshot;

    /**
     * The content as of the last load or successful save. The buffer is modified whenever the
     * current content differs from this baseline, so undoing back to the saved state clears the flag.
     */
    private String savedText;

    /**
     * The project this file belongs to. Resolved on demand and then kept, so that the document
     * keeps naming its project while it is being closed with it.
     */
    volatile String projectPath;

    /** Reacts to edits: dirty flag and background analysis. Removed with the last editor. */
    private final StyledTextModel.Listener editListener = this::onChange;

    private EditorBuffer(FileObject fileObject) {
        this.fileObject = Objects.requireNonNull(fileObject);
        model = new CodeTextModel();
        decorator = SyntaxDecorators.forFile(fileObject);
        model.setDecorator(decorator);

        // line separator handling (detect on load, before the content is set)
        lineSeparator = new LineSeparatorSupport(fileObject, model);
        try {
            String text = fileObject.asText();
            lineSeparator.load(text);
            setText(text);
            savedText = text();
            snapshot = TextOffsets.text(model);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to read file content: " + fileObject.getNameExt(), ex);
        }

        externalChanges = new ExternalChangeSupport(fileObject, modified::get, this::lock, this::reload);
        externalChanges.watch();

        analyze();
        model.addListener(editListener);
    }

    // --- Views -----------------------------------------------------------------------------------

    void attach(CodeEditor view) {
        views.add(view);
    }

    /** Detaches {@code view}; the buffer releases its resources once no editor shows it any more. */
    void detach(CodeEditor view) {
        views.remove(view);
        if (views.isEmpty()) {
            externalChanges.dispose();
            model.removeListener(editListener);
            OPEN.remove(fileObject, this);
        }
    }

    // --- State -----------------------------------------------------------------------------------

    ReadOnlyBooleanProperty modifiedProperty() {
        return modified.getReadOnlyProperty();
    }

    boolean isModified() {
        return modified.get();
    }

    BooleanProperty editableProperty() {
        return editable;
    }

    LineSeparatorSupport lineSeparator() {
        return lineSeparator;
    }

    /** Changes the line separator; {@code true} when it was applied (known and different). */
    boolean setLineSeparator(String separator) {
        if (lineSeparator.set(separator)) {
            updateModified();
            return true;
        }
        return false;
    }

    /** The whole content, paragraphs joined with the model's line ending - what a save writes. */
    String text() {
        String ending = model.getLineEnding().getText();
        StringBuilder sb = new StringBuilder();
        int count = model.size();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(ending);
            }
            sb.append(model.getPlainText(i));
        }
        return sb.toString();
    }

    /** Replaces the whole content without leaving an undo entry (a load is not a user edit). */
    private void setText(String text) {
        model.setUndoRedoEnabled(false);
        try {
            model.replace(null, TextPos.ZERO, model.getDocumentEnd(), text);
        } finally {
            model.setUndoRedoEnabled(true);
        }
    }

    private void onChange(ContentChange change) {
        if (change.isEdit()) {
            snapshot = TextOffsets.text(model);
            updateModified();
            analyze();
        }
    }

    /** The current text with {@code '\n'} line breaks; any thread. */
    String snapshot() {
        return snapshot;
    }

    private void analyze() {
        try {
            decorator.analyzeInBackground(model);
        } catch (RuntimeException ex) {
            LOG.warning("Analysis failed for " + fileObject.getNameExt());
        }
    }

    /** Recomputes the dirty flag: content or line separator differing from the saved baseline. */
    private void updateModified() {
        modified.set(!Objects.equals(text(), savedText) || lineSeparator.isDirty());
        modifiedSnapshot = modified.get();
    }

    /** Locks every editor: the file changed on disk while there are unsaved edits. */
    private void lock() {
        editable.set(false);
    }

    /**
     * Applies an external reload: replaces the content and line separator with the ones read from
     * disk, re-baselines (the buffer stays clean), and puts each editor's caret back as closely as
     * possible.
     */
    private void reload(String text, String separator) {
        List<TextPos> carets = new ArrayList<>(views.size());
        for (CodeEditor view : views) {
            carets.add(view.codeArea.getCaretPosition());
        }
        model.setLineEnding(LineSeparatorSupport.lineEndingOf(separator));
        setText(text);
        savedText = text();
        snapshot = TextOffsets.text(model);
        lineSeparator.resetTo(separator);
        updateModified();
        for (int i = 0; i < views.size(); i++) {
            views.get(i).restoreCaretAfterReload(carets.get(i));
        }
    }

    // --- Save ------------------------------------------------------------------------------------

    /**
     * Persists the content back to the file. Must be invoked on the JavaFX Application Thread. If
     * the file was modified externally since it was opened (or last saved), the save is aborted
     * with an {@link IOException} to avoid overwriting those changes.
     */
    void save() throws IOException {
        if (!modified.get()) {
            return;
        }
        String text = text();
        if (externalChanges.isConflicting()) {
            throw new IOException("File \"" + fileObject.getNameExt()
                    + "\" was modified externally since it was opened; save aborted to avoid"
                    + " overwriting those changes.");
        }
        // Write with the same encoding FileObject.asText() used to read
        Charset charset = encodingOf(fileObject);
        try (OutputStream out = fileObject.getOutputStream();
             Writer writer = new OutputStreamWriter(out, charset)) {
            writer.write(text);
        }
        savedText = text;
        // Persist the (possibly changed) line separator alongside the content
        lineSeparator.markSaved();
        externalChanges.markSynced();
        modified.set(false);
        modifiedSnapshot = false;
    }

    /**
     * Returns the charset {@link FileObject#asText()} uses for this file, falling back to the
     * platform default when the encoding cannot be resolved (e.g. outside the NetBeans Platform).
     */
    private static Charset encodingOf(FileObject fileObject) {
        try {
            return FileEncodingQuery.getEncoding(fileObject);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not resolve the encoding of " + fileObject.getNameExt()
                    + ", falling back to the default charset", ex);
        }
        return Charset.defaultCharset();
    }
}
