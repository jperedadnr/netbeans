/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.structure;

import com.gluonhq.netbeans.nbfx.api.actions.FxActionContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;
import javafx.util.Subscription;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 * Feeds a {@link ScanningPanel} with the file of the global action context while the panel is the
 * active one of the Navigator window: the active editor's document - scanned again after each edit,
 * with the caret's row selected - else the file selected in a project view, scanned once. A file the
 * panel does not {@linkplain #DocumentTracker(ScanningPanel, Predicate) accept} clears it. Scans run
 * on {@link BackgroundScans#EXECUTOR} and are suspended while the panel is off screen.
 * <p>
 * A panel can also be {@linkplain #pin pinned} on a file aside from the context (the Java
 * Navigator's <em>Inspect Members</em>): the file is shown, with the row of the pinned key selected,
 * until the user turns to another file or edits the active document.
 *
 * @param <R> the result of the panel's scan
 * @since 1.0
 */
public final class DocumentTracker<R> {

    private static final Logger LOG = Logger.getLogger(DocumentTracker.class.getName());
    /** How long after the last edit the file is scanned again. */
    private static final Duration EDIT_DELAY = Duration.millis(400);

    private final ScanningPanel<R> panel;
    private final Predicate<FileObject> accepts;
    private final OpenSources openSources;
    private final PauseTransition editDelay = new PauseTransition(EDIT_DELAY);
    private final LookupListener contextListener = this::contextChanged;
    /** The context followed, {@code null} while the panel is not the active one. */
    private FxActionContext context;
    private Lookup.Result<EditorDocument> documents;
    private Lookup.Result<FileObject> files;
    /** The file shown, from an editor ({@link #document}), from a project view or pinned. */
    private FileObject file;
    /** The editor followed, {@code null} while a project-view selection or a pinned file is shown. */
    private EditorDocument document;
    /** The last document that was active, to tell a re-activation of it from the activation of another. */
    private EditorDocument lastActive;
    private Subscription documentSubscription = Subscription.EMPTY;
    private boolean active;
    /** Whether the file changed, or was edited, while the panel was off screen. */
    private boolean stale;
    private int generation;
    private AtomicBoolean cancelled = new AtomicBoolean();
    /** The key pinned, {@code null} while the tracker follows the context. */
    private final ObjectProperty<Object> pinned = new SimpleObjectProperty<>(this, "pinned");
    /** While pinned, an edit of the active document brings the context back. */
    private Subscription pinSubscription = Subscription.EMPTY;

    /**
     * Creates a tracker feeding {@code panel} with the files {@code accepts} admits.
     *
     * @param panel   the panel fed
     * @param accepts whether a file of the context is one the panel scans (a data file of the
     *                panel's content type); other files clear the panel
     */
    public DocumentTracker(ScanningPanel<R> panel, Predicate<FileObject> accepts) {
        this(panel, Lookup.getDefault().lookup(OpenSources.class), accepts);
    }

    DocumentTracker(ScanningPanel<R> panel, OpenSources openSources, Predicate<FileObject> accepts) {
        this.panel = Objects.requireNonNull(panel, "panel");
        this.accepts = Objects.requireNonNull(accepts, "accepts");
        this.openSources = openSources;
        editDelay.setOnFinished(e -> scan());
        // Scans are suspended while the panel is off screen; they resume when it is shown again.
        panel.getNode().sceneProperty().subscribe(scene -> setActive(scene != null));
    }

    /** Starts following {@code context}; the panel became the active one. FX thread. */
    public void activate(FxActionContext context) {
        if (this.context == context) {
            return;
        }
        deactivate();
        this.context = context;
        documents = context.lookupResult(EditorDocument.class);
        files = context.lookupResult(FileObject.class);
        documents.addLookupListener(contextListener);
        files.addLookupListener(contextListener);
        contextChanged();
    }

    /** Stops following the context and shows nothing; the panel was hidden. FX thread. */
    public void deactivate() {
        if (context == null) {
            return;
        }
        documents.removeLookupListener(contextListener);
        files.removeLookupListener(contextListener);
        documents = null;
        files = null;
        context = null;
        leavePin();
        stopFollowing();
        cancelPending();
        file = null;
        lastActive = null;
        panel.clear();
    }

    /** Whether the panel is on screen: scans are suspended while it is not. */
    private void setActive(boolean active) {
        this.active = active;
        if (active && stale) {
            scan();
        }
    }

    /** The key pinned, {@code null} while the context is followed. */
    public ReadOnlyObjectProperty<Object> pinnedProperty() {
        return pinned;
    }

    /** The file shown, or {@code null} when the panel shows nothing. FX thread. */
    public FileObject file() {
        return file;
    }

    /**
     * Shows {@code file}, with the row of {@code key} selected ({@link ScanningPanel#select}), until
     * the user turns to another file or edits the active document. When that file is the active
     * document's own, the caret keeps selecting the row it is in. FX thread.
     */
    public void pin(FileObject file, Object key) {
        stopFollowing();
        pinned.set(Objects.requireNonNull(key, "key"));
        this.file = Objects.requireNonNull(file, "file");
        EditorDocument activeDocument = context == null ? null : context.lookup(EditorDocument.class);
        if (activeDocument != null) {
            lastActive = activeDocument;
            pinSubscription = activeDocument.textVersionProperty().subscribe((was, now) -> unpin());
            if (file.equals(activeDocument.getFileObject())) {
                pinSubscription = pinSubscription.and(activeDocument.caretInfoProperty()
                        .subscribe((was, now) -> panel.selectAt(activeDocument.getCaretOffset())));
            }
        }
        scan();
    }

    /** Back to following the context: the active document or the selected file is shown again. */
    public void unpin() {
        if (pinned.get() == null) {
            return;
        }
        leavePin();
        if (context != null) {
            contextChanged();
        }
    }

    /** Scans the file shown again, now: the panel's input changed aside from the text. FX thread. */
    public void rescan() {
        if (file != null) {
            scan();
        }
    }

    private void leavePin() {
        if (pinned.get() != null) {
            pinned.set(null);
            pinSubscription.unsubscribe();
            pinSubscription = Subscription.EMPTY;
        }
    }

    private void contextChanged(LookupEvent event) {
        if (Platform.isFxApplicationThread()) {
            contextChanged();
        } else {
            Platform.runLater(this::contextChanged);
        }
    }

    /**
     * Shows what the context now carries: the active editor's document, else the file selected in a
     * project view. A re-activation of the document a pin started from (the panes were rearranged,
     * or the focus moved) is not the user turning to another one, and keeps the pin; a file the
     * panel does not accept shows nothing.
     */
    private void contextChanged() {
        if (context == null) {
            return;
        }
        EditorDocument activeDocument = context.lookup(EditorDocument.class);
        if (activeDocument != null) {
            if (activeDocument == document || pinned.get() != null && activeDocument == lastActive) {
                return;
            }
            lastActive = activeDocument;
            followDocument(activeDocument);
            return;
        }
        FileObject selected = context.lookup(FileObject.class);
        if (selected != null && selected.isData() && accepts.test(selected)) {
            followFile(selected);
        } else {
            leavePin();
            stopFollowing();
            file = null;
            panel.clear();
        }
    }

    private void followFile(FileObject selected) {
        if (document == null && pinned.get() == null && selected.equals(file)) {
            return;
        }
        leavePin();
        stopFollowing();
        file = selected;
        scan();
    }

    private void followDocument(EditorDocument newDocument) {
        leavePin();
        stopFollowing();
        document = newDocument;
        file = document.getFileObject();
        if (file == null || !accepts.test(file)) {
            cancelPending();
            file = null;
            panel.clear();
            return;
        }
        documentSubscription = document.textVersionProperty().subscribe((was, now) -> editDelay.playFromStart())
                .and(document.caretInfoProperty().subscribe(info -> {
                    if (document != null) {
                        panel.selectAt(document.getCaretOffset());
                    }
                }));
        scan();
    }

    private void stopFollowing() {
        documentSubscription.unsubscribe();
        documentSubscription = Subscription.EMPTY;
        editDelay.stop();
        document = null;
    }

    private void scan() {
        editDelay.stop();
        if (file == null) {
            return;
        }
        if (!active) {
            stale = true;
            return;
        }
        stale = false;
        cancelPending();
        scan(file, document, pinned.get(), cancelled, generation);
    }

    private void scan(FileObject scanned, EditorDocument scannedDocument, Object scannedKey,
            AtomicBoolean cancel, int requested) {
        String text = openSources == null ? null : openSources.textOf(scanned);
        BackgroundScans.EXECUTOR.execute(() -> {
            R result = null;
            try {
                String source = text != null ? text : scanned.asText().replace("\r\n", "\n").replace('\r', '\n');
                result = panel.scan(scanned, source, cancel::get);
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not scan " + scanned.getPath(), ex);
            }
            if (cancel.get()) {
                return;
            }
            R scannedResult = result;
            Platform.runLater(() -> {
                if (requested != generation || !scanned.equals(file)) {
                    return;
                }
                panel.show(scannedResult);
                if (scannedResult == null) {
                    return;
                }
                if (scannedKey != null && scannedKey == pinned.get()) {
                    panel.select(scannedKey);
                } else {
                    // A file shown from a project view has no caret: no row is selected.
                    panel.selectAt(scannedDocument != null && scannedDocument == document
                            ? scannedDocument.getCaretOffset() : -1);
                }
            });
        });
    }

    /** Invalidates the scan under way, if any: its result will be dropped. */
    private void cancelPending() {
        cancelled.set(true);
        cancelled = new AtomicBoolean();
        generation++;
    }
}
