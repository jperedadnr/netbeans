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

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Runs a Go to command: resolves its target from the editor's text on the {@link Background}
 * thread, then opens the declaring file at it - or, for Go to Implementation, offers the
 * implementations in a popup first. A command reports why it went nowhere - nothing at
 * the caret, a library without sources, a failed compilation - while a hyperlink click stays
 * quiet, as a click on a keyword is no error.
 */
public final class GoToOpener {

    private static final Logger LOG = Logger.getLogger(GoToOpener.class.getName());

    private GoToOpener() {
    }

    /**
     * Navigates from {@code offset} of {@code file}. Must be called on the FX thread.
     *
     * @param kind   the command
     * @param file   the file open in the editor
     * @param offset the caret, counting one character per line break
     * @param title  the command's name, the title of the reports; {@code null} to report nothing
     * @return whether a target was opened, completed on the FX thread
     */
    public static CompletableFuture<Boolean> goTo(GoToResolver.Kind kind, FileObject file, int offset, String title) {
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        String text = openSources == null ? null : openSources.textOf(file);
        CompletableFuture<Boolean> opened = new CompletableFuture<>();
        Background.EXECUTOR.execute(() -> {
            GoToResolver.Target target = null;
            Throwable failure = null;
            try {
                String source = text != null ? text : file.asText().replace("\r\n", "\n").replace('\r', '\n');
                target = GoToResolver.resolve(kind, file, source, offset, openSources);
            } catch (IOException | RuntimeException ex) {
                failure = ex;
            }
            GoToResolver.Target resolved = target;
            Throwable failed = failure;
            Platform.runLater(() -> opened.complete(open(file, resolved, failed, title)));
        });
        return opened;
    }

    /**
     * Go to Implementation from {@code offset} of {@code file}: lists the implementations of the
     * type or method there in a popup at {@code anchor} and opens the chosen one; a single one is
     * opened at once. Must be called on the FX thread.
     *
     * @param anchor where the popup goes, in screen coordinates; {@code null} for the focused window
     * @param title  the command's name, the title of the reports; {@code null} to report nothing
     * @return whether a target was opened or offered, completed on the FX thread
     */
    public static CompletableFuture<Boolean> goToImplementation(FileObject file, int offset, Point2D anchor, String title) {
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        String text = openSources == null ? null : openSources.textOf(file);
        // The sources of the open projects, searched whichever file the caret is in.
        List<FileObject> sourceRoots = new ArrayList<>(GlobalPathRegistry.getDefault().getSourceRoots());
        CompletableFuture<Boolean> opened = new CompletableFuture<>();
        Background.EXECUTOR.execute(() -> {
            GoToResolver.Implementations implementations = null;
            Throwable failure = null;
            try {
                String source = text != null ? text : file.asText().replace("\r\n", "\n").replace('\r', '\n');
                implementations = GoToResolver.implementations(file, source, offset, sourceRoots);
            } catch (IOException | RuntimeException ex) {
                failure = ex;
            }
            GoToResolver.Implementations resolved = implementations;
            Throwable failed = failure;
            Platform.runLater(() -> opened.complete(offer(file, resolved, failed, anchor, title)));
        });
        return opened;
    }

    private static boolean offer(FileObject file, GoToResolver.Implementations implementations, Throwable failure,
            Point2D anchor, String title) {
        if (failure != null) {
            LOG.log(Level.WARNING, "Could not find the implementations from " + file.getPath(), failure);
            if (title != null) {
                ErrorReporter.report(title, null, message("ERR_GoTo"), failure);
            }
            return false;
        }
        if (implementations == null) {
            if (title != null) {
                ErrorReporter.report(title, null, message("ERR_NothingAtCaret"));
            }
            return false;
        }
        if (implementations.items().isEmpty()) {
            if (title != null) {
                ErrorReporter.report(title, null,
                        NbBundle.getMessage(GoToOpener.class, "ERR_NoImplementations", implementations.name()));
            }
            return false;
        }
        offer(message("TITLE_Implementations"), implementations.items(), anchor);
        return true;
    }

    /**
     * Opens the one of {@code items} when there is one, else lists them in a popup titled
     * {@code title} at {@code anchor} (screen coordinates) and opens the one chosen. FX thread.
     */
    public static void offer(String title, List<GoToResolver.Implementation> items, Point2D anchor) {
        if (items.size() == 1) {
            openImplementation(items.get(0));
        } else if (!items.isEmpty()) {
            ImplementationsPopup.show(title, items, anchor, GoToOpener::openImplementation);
        }
    }

    /** Opens {@code implementation} at its declaration, located in the background. FX thread. */
    public static void openImplementation(GoToResolver.Implementation implementation) {
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        Background.EXECUTOR.execute(() -> {
            SourceLocation location = null;
            try {
                location = GoToResolver.locate(implementation, openSources);
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not locate " + implementation.name(), ex);
            }
            SourceLocation found = location;
            Platform.runLater(() -> {
                if (found == null) {
                    LOG.log(Level.INFO, "No source for {0}", implementation.name());
                    return;
                }
                ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
                if (contentManager != null) {
                    contentManager.openFile(found.file(), found.offset(), found.offset());
                }
            });
        });
    }

    /** Runs {@code work} on the {@link Background} thread, completing the future with its result or its failure. */
    public static <T> CompletableFuture<T> inBackground(Work<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Background.EXECUTOR.execute(() -> {
            try {
                result.complete(work.run());
            } catch (IOException | RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    /** Work for {@link #inBackground}. */
    public interface Work<T> {
        T run() throws IOException;
    }

    private static boolean open(FileObject file, GoToResolver.Target target, Throwable failure, String title) {
        if (failure != null) {
            LOG.log(Level.WARNING, "Could not navigate from " + file.getPath(), failure);
            if (title != null) {
                ErrorReporter.report(title, null, message("ERR_GoTo"), failure);
            }
            return false;
        }
        if (target == GoToResolver.Target.HERE) {
            // Already on the declaration: nowhere to go, nothing to say.
            return false;
        }
        if (target == null) {
            if (title != null) {
                ErrorReporter.report(title, null, message("ERR_NothingAtCaret"));
            }
            return false;
        }
        SourceLocation location = target.location();
        if (location == null) {
            LOG.log(Level.INFO, "No source for {0}", target.name());
            if (title != null) {
                ErrorReporter.report(title, null, NbBundle.getMessage(GoToOpener.class, "ERR_NoSource", target.name()));
            }
            return false;
        }
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager == null) {
            return false;
        }
        contentManager.openFile(location.file(), location.offset(), location.offset());
        return true;
    }

    private static String message(String key) {
        return NbBundle.getMessage(GoToOpener.class, key);
    }
}
