package com.gluonhq.netbeans.nbfx.editor.codearea;

import javafx.application.Platform;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the file behind an {@link EditorBuffer} for external modifications and keeps the editors
 * in sync with the disk: a clean buffer is reloaded from disk, while a buffer with unsaved local
 * edits is locked (its editors made non-editable) instead, since saving would overwrite the external
 * change. Editing is regained by closing the file and opening it again.
 * <p>
 * Also tracks the file modification time of the last load or save, so the buffer's own saves are
 * not mistaken for external changes and write conflicts can be detected before saving.
 */
final class ExternalChangeSupport {

    private static final Logger LOG = Logger.getLogger(ExternalChangeSupport.class.getName());

    private final FileObject fileObject;
    /** Whether the buffer has unsaved local edits (queried on the FX thread). */
    private final BooleanSupplier modified;
    /** Locks the editors: the file changed on disk while there are unsaved edits. */
    private final Runnable lock;
    /**
     * Applies a reload: replaces the content and line separator with the ones read from disk and
     * re-baselines; the buffer stays clean.
     */
    private final BiConsumer<String, String> reload;

    /** Listens for external modifications of the file; removed on {@link #dispose()}. */
    private final FileChangeListener fileChangeListener = new FileChangeAdapter() {
        @Override
        public void fileChanged(FileEvent fe) {
            if (fe != null) {
                onExternalFileChange(fe.getTime());
            }
        }
    };

    /**
     * Last known modification time of the file at the moment its content was loaded
     * (or last successfully saved). Used to detect external modifications (write conflicts).
     */
    private long lastSyncedModifiedTime;

    ExternalChangeSupport(FileObject fileObject, BooleanSupplier modified,
                          Runnable lock, BiConsumer<String, String> reload) {
        this.fileObject = fileObject;
        this.modified = modified;
        this.lock = lock;
        this.reload = reload;
    }

    /** Marks the editor as in sync with the disk and starts watching for external changes. */
    void watch() {
        markSynced();
        fileObject.addFileChangeListener(fileChangeListener);
    }

    /** Records the current file modification time as the editor's last load or save. */
    void markSynced() {
        lastSyncedModifiedTime = fileObject.lastModified().getTime();
    }

    /** Whether the file was modified externally since the editor last loaded or saved it. */
    boolean isConflicting() {
        return fileObject.lastModified().getTime() != lastSyncedModifiedTime;
    }

    /** Stops watching the file. */
    void dispose() {
        fileObject.removeFileChangeListener(fileChangeListener);
    }

    /**
     * Reacts to an external modification of the file, on the FX thread (where the dirty state and
     * {@link #lastSyncedModifiedTime} live). The editor's own save is ignored.
     */
    private void onExternalFileChange(long eventTime) {
        Platform.runLater(() -> {
            if (eventTime == lastSyncedModifiedTime) {
                // The change is this editor's own save()
                return;
            }
            if (modified.getAsBoolean()) {
                lock.run();
            } else {
                reloadFromDiskInBackground();
            }
        });
    }

    /**
     * Reloads the externally modified file in a background task: reads its content, rescans its
     * line separator (refreshing the cached {@link LineSeparatorSupport#ATTR} attribute), and applies
     * both to the buffer on the FX thread.
     */
    private void reloadFromDiskInBackground() {
        CompletableFuture.runAsync(() -> {
            String text;
            try {
                text = fileObject.asText();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not reload the externally modified file " + fileObject.getNameExt(), ex);
                return;
            }
            String detected = LineSeparatorSupport.scan(text);
            String separator = detected != null ? detected : System.lineSeparator();
            LineSeparatorSupport.store(fileObject, separator);
            Platform.runLater(() -> applyExternalReload(text, separator));
        });
    }

    /**
     * Applies an external reload through the buffer, unless the user edited the document while the
     * reload was being prepared: that is now a conflict, so the editors are locked instead of
     * discarding those edits.
     */
    private void applyExternalReload(String text, String separator) {
        if (modified.getAsBoolean()) {
            lock.run();
            return;
        }
        reload.accept(text, separator);
        markSynced();
    }
}
