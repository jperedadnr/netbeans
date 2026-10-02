package com.gluonhq.netbeans.nbfx.findinprojects.replace;

import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.findinprojects.replace.ReplaceTask.Buffers;
import com.gluonhq.netbeans.nbfx.findinprojects.replace.ReplaceTask.Edit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * The editors of the application: their text through the {@link OpenSources} the editor module
 * registers, their edits through the {@link EditorDocument}s the {@link EditorContext} lists, on the
 * JavaFX thread. Without an editor module every file is on disk.
 */
final class EditorBuffers implements Buffers {

    private static final Logger LOG = Logger.getLogger(EditorBuffers.class.getName());
    private static final long TIMEOUT_SECONDS = 30;

    @Override
    public String textOf(FileObject file) {
        OpenSources sources = Lookup.getDefault().lookup(OpenSources.class);
        return sources == null ? null : sources.textOf(file);
    }

    @Override
    public boolean replace(FileObject file, String snapshot, List<Edit> edits) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        Runnable apply = () -> done.complete(applyNow(file, snapshot, edits));
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            try {
                Platform.runLater(apply);
            } catch (IllegalStateException toolkitNotRunning) {
                apply.run();
            }
        }
        try {
            return done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException ex) {
            LOG.log(Level.WARNING, "Could not replace in the editor of " + file.getPath(), ex);
            return false;
        }
    }

    /** On the JavaFX thread: the edits, unless the editor is gone or its text moved on. */
    private boolean applyNow(FileObject file, String snapshot, List<Edit> edits) {
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        EditorDocument document = context == null ? null : context.getDocuments().stream()
                .filter(d -> file.equals(d.getFileObject()))
                .findFirst()
                .orElse(null);
        if (document == null || !snapshot.equals(textOf(file))) {
            return false;
        }
        for (Edit edit : edits) {
            if (!document.replaceRange(edit.start(), edit.end(), edit.text())) {
                return false;
            }
        }
        return true;
    }
}
