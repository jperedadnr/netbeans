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
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;
import com.gluonhq.netbeans.nbfx.javanavigator.model.InspectedType;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.util.Duration;
import javafx.util.Subscription;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Keeps the Navigator's selected {@link NavigatorPanel} in step with what the user works on, the way
 * NetBeans' Navigator follows the activated nodes:
 * <ul>
 *   <li>the <b>active editor</b>: a new active Java document is scanned at once, an edit after a
 *       short pause, and the row enclosing the caret is selected as it moves;</li>
 *   <li>the <b>file selected in the Projects or Files tree</b> while that tree has the keyboard
 *       focus: a Java file is scanned without being opened; the keyboard focus landing in an editor
 *       brings its document back.</li>
 * </ul>
 * When neither applies - the user moved to a view tab, the Navigator's own included - the last
 * context is kept until its editor closes or another one is activated. Switching the panel scans
 * the current file for it.
 * <p>
 * {@link #inspect Inspecting} a type (Navigate &#9656; Inspect) shows the file declaring it instead,
 * with the type's row selected, until the editor is used again - another document activated, the
 * active one edited, a tree selection - or the history combo goes back to {@code <auto>}. The
 * keyboard focus merely landing in the editor does not count: showing the Navigator rearranges the
 * panes, which moves the focus about without the user doing anything.
 * <p>
 * Scans run on the {@link Background} thread from the editor's current text (the file on disk for
 * one that is not open); a result that arrives after a newer request started, or for another
 * panel, is dropped. Nothing is scanned while the view is not on screen; what was missed is scanned
 * when it is shown again.
 */
final class DocumentTracker {

    private static final Logger LOG = Logger.getLogger(DocumentTracker.class.getName());
    /** How long after the last edit the file is scanned again. */
    private static final Duration EDIT_DELAY = Duration.millis(400);

    private final NavigatorView view;
    private final EditorContext context;
    private final FileSelectionContext selection;
    private final OpenSources openSources;
    private final PauseTransition editDelay = new PauseTransition(EDIT_DELAY);
    private NavigatorPanel<?> panel;
    /** The file shown, from an editor ({@link #document}) or from the tree. */
    private FileObject file;
    /** The editor followed, {@code null} while a tree selection is shown. */
    private EditorDocument document;
    /** The last document that was active, to tell a re-activation of it from the activation of another. */
    private EditorDocument lastActive;
    private Subscription documentSubscription = Subscription.EMPTY;
    private boolean active;
    /** Whether the file changed, or was edited, while the view was off screen. */
    private boolean stale;
    private int generation;
    private AtomicBoolean cancelled = new AtomicBoolean();
    /** The type being inspected, {@code null} while the tracker follows the editor and the trees. */
    private final ObjectProperty<InspectedType> inspected = new SimpleObjectProperty<>(this, "inspected");
    /** While inspecting, an edit of the active document brings it back. */
    private Subscription inspectSubscription = Subscription.EMPTY;

    DocumentTracker(NavigatorView view, NavigatorPanel<?> panel) {
        this(view, panel, Lookup.getDefault().lookup(EditorContext.class),
                Lookup.getDefault().lookup(FileSelectionContext.class), Lookup.getDefault().lookup(OpenSources.class));
    }

    DocumentTracker(NavigatorView view, NavigatorPanel<?> panel, EditorContext context,
            FileSelectionContext selection, OpenSources openSources) {
        this.view = view;
        this.panel = panel;
        this.context = context;
        this.selection = selection;
        this.openSources = openSources;
        editDelay.setOnFinished(e -> scan());
    }

    /** Starts following the editor and the tree; the view lives as long as the session, so nothing is released. */
    void start() {
        if (context == null) {
            LOG.warning("No EditorContext found; the Navigator will not follow the editor");
            view.showUnavailable();
            return;
        }
        context.activeDocumentProperty().subscribe(this::activeDocumentChanged);
        // The kept document is let go when its editor closes.
        context.getDocuments().addListener((ListChangeListener<EditorDocument>) change -> {
            while (change.next()) {
                if (document != null && change.getRemoved().contains(document) && !context.getDocuments().contains(document)) {
                    followDocument(null);
                }
            }
        });
        if (selection != null) {
            selection.selectedFiles().subscribe(files -> {
                if (Boolean.TRUE.equals(selection.navigatorFocused().getValue())) {
                    followSelection(files);
                }
            });
            selection.navigatorFocused().subscribe(focused -> {
                if (Boolean.TRUE.equals(focused)) {
                    followSelection(selection.selectedFiles().getValue());
                }
            });
        }
        // The keyboard focus landing in an editor of the Navigator's own window brings its document
        // back after a tree selection, even when it is still the active document.
        view.sceneProperty().flatMap(Scene::focusOwnerProperty).subscribe(this::focusOwnerChanged);
    }

    /** Whether the view is on screen: scans are suspended while it is not. */
    void setActive(boolean active) {
        this.active = active;
        if (active && stale) {
            scan();
        }
    }

    /** The type being inspected, {@code null} while the editor and the trees are followed ({@code <auto>}). */
    ReadOnlyObjectProperty<InspectedType> inspectedProperty() {
        return inspected;
    }

    /**
     * Shows the file declaring {@code type}, its row selected, until the editor is used again. When
     * that file is the active document's own, the caret keeps selecting the member it is in.
     */
    void inspect(InspectedType type) {
        stopFollowing();
        inspected.set(type);
        file = type.file();
        EditorDocument active = context == null ? null : context.getActiveDocument();
        if (active != null) {
            inspectSubscription = active.textVersionProperty().subscribe((was, now) -> returnToAuto());
            if (type.file().equals(active.getFileObject())) {
                inspectSubscription = inspectSubscription.and(active.caretInfoProperty()
                        .subscribe((was, now) -> panel.selectAt(active.getCaretOffset())));
            }
        }
        scan();
    }

    /** Back to following the editor and the trees: the active document is shown again. */
    void returnToAuto() {
        if (inspected.get() == null) {
            return;
        }
        inspected.set(null);
        inspectSubscription.unsubscribe();
        inspectSubscription = Subscription.EMPTY;
        EditorDocument active = context == null ? null : context.getActiveDocument();
        if (active != null) {
            followDocument(active);
        } else {
            file = null;
            view.showUnavailable();
        }
    }

    private void leaveInspection() {
        if (inspected.get() != null) {
            inspected.set(null);
            inspectSubscription.unsubscribe();
            inspectSubscription = Subscription.EMPTY;
        }
    }

    /** Switches to {@code panel}, scanning the current file for it. */
    void setPanel(NavigatorPanel<?> panel) {
        if (this.panel == panel) {
            return;
        }
        this.panel.clear();
        this.panel = panel;
        if (file != null) {
            scan();
        }
    }

    private void activeDocumentChanged(EditorDocument newDocument) {
        boolean another = newDocument != null && newDocument != lastActive;
        if (newDocument != null) {
            lastActive = newDocument;
        }
        if (newDocument == document) {
            return;
        }
        if (inspected.get() != null && !another) {
            // The document active before the inspection became active again - the panes were
            // rearranged, or the focus moved - which is not the user turning to another one.
            return;
        }
        if (newDocument == null && document != null && context.getDocuments().contains(document)) {
            // No editor is active, the last one is still open: keep following it.
            return;
        }
        if (newDocument == null && document == null) {
            // A tree selection or an inspected type is shown and no editor took over: keep it.
            return;
        }
        followDocument(newDocument);
    }

    private void focusOwnerChanged(Node owner) {
        if (owner == null || inspected.get() != null || document != null && isWithin(owner, document.getNode())) {
            return;
        }
        for (EditorDocument candidate : context.getDocuments()) {
            if (isWithin(owner, candidate.getNode())) {
                followDocument(candidate);
                return;
            }
        }
    }

    private static boolean isWithin(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** Shows the file selected in the focused tree: a single Java file; anything else has no view. */
    private void followSelection(List<FileObject> files) {
        FileObject selected = files != null && files.size() == 1 ? files.get(0) : null;
        if (selected != null && selected.isData() && isJava(selected)) {
            followFile(selected);
        } else {
            leaveInspection();
            stopFollowing();
            file = null;
            view.showUnavailable();
        }
    }

    private void followFile(FileObject selected) {
        if (document == null && inspected.get() == null && selected.equals(file)) {
            return;
        }
        leaveInspection();
        stopFollowing();
        file = selected;
        scan();
    }

    private void followDocument(EditorDocument newDocument) {
        leaveInspection();
        stopFollowing();
        document = newDocument;
        file = document == null ? null : document.getFileObject();
        if (file == null || !isJava(file)) {
            cancelPending();
            file = null;
            view.showUnavailable();
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
        scan(panel, file, document, inspected.get(), cancelled, generation);
    }

    private <R> void scan(NavigatorPanel<R> target, FileObject scanned, EditorDocument scannedDocument,
            InspectedType scannedType, AtomicBoolean cancel, int requested) {
        String text = openSources == null ? null : openSources.textOf(scanned);
        Background.EXECUTOR.execute(() -> {
            R result = null;
            try {
                String source = text != null ? text : scanned.asText().replace("\r\n", "\n").replace('\r', '\n');
                result = target.scan(scanned, source, cancel::get);
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not scan " + scanned.getPath(), ex);
            }
            if (cancel.get()) {
                return;
            }
            R scannedResult = result;
            Platform.runLater(() -> {
                if (requested != generation || !scanned.equals(file) || target != panel) {
                    return;
                }
                target.show(scannedResult);
                view.showContent();
                if (scannedResult == null) {
                    return;
                }
                if (scannedType != null && scannedType == inspected.get()) {
                    target.selectType(scannedType.getQualifiedName());
                } else {
                    // A file shown from the tree has no caret: no row is selected.
                    target.selectAt(scannedDocument != null && scannedDocument == document
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

    private static boolean isJava(FileObject file) {
        return file != null && "java".equalsIgnoreCase(file.getExt());
    }
}
