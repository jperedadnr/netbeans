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
package com.gluonhq.netbeans.nbfx.javanavigator.actions;

import com.gluonhq.netbeans.nbfx.api.editor.EditorAnnotation;
import com.gluonhq.netbeans.nbfx.api.editor.EditorAnnotationProvider;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver.Implementation;
import com.gluonhq.netbeans.nbfx.javanavigator.model.OverriddenScanner;
import com.gluonhq.netbeans.nbfx.javanavigator.ui.GoToOpener;
import com.gluonhq.netbeans.nbfx.javanavigator.ui.NavigatorIcons;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.BooleanSupplier;
import javafx.geometry.Point2D;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The override badges of the Java editor's gutter, after NetBeans' {@code IsOverriddenAnnotation}:
 * at a method that overrides a method, a green O; that implements one, a green I; at a method or
 * class overridden by subtypes, a grey O; implemented by them, a grey I; at a method that does
 * both, a plain grey circle. A click on a badge opens the one method it relates to, or lists them
 * in the Implementors/Overridders popup: the ancestors with an up arrow, the descendants with a
 * down one.
 */
@ServiceProvider(service = EditorAnnotationProvider.class)
public class OverriddenAnnotationProvider implements EditorAnnotationProvider {

    @Override
    public boolean handles(FileObject file) {
        return file != null && "java".equalsIgnoreCase(file.getExt());
    }

    /**
     * The scans run on their own thread, apart from the Navigator's, so neither waits for the
     * other; the active document's scan goes before the other open documents' (the tabs restored
     * at start-up, say), and a scan no longer wanted is skipped when its turn comes.
     */
    private static final Logger LOG = Logger.getLogger(OverriddenAnnotationProvider.class.getName());
    private static final PriorityBlockingQueue<Job> QUEUE = new PriorityBlockingQueue<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    static {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    QUEUE.take().run();
                } catch (InterruptedException ex) {
                    return;
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Annotation scan failed", ex);
                }
            }
        }, "nbfx-annotations");
        worker.setDaemon(true);
        worker.start();
    }

    /** A queued scan: the active document's first, then in the order asked. */
    private record Job(boolean active, long order, Runnable work) implements Comparable<Job>, Runnable {
        @Override
        public int compareTo(Job other) {
            return active != other.active ? (active ? -1 : 1) : Long.compare(order, other.order);
        }

        @Override
        public void run() {
            work.run();
        }
    }

    @Override
    public CompletableFuture<List<EditorAnnotation>> annotate(FileObject file, String text, BooleanSupplier cancelled) {
        List<FileObject> sourceRoots = new ArrayList<>(GlobalPathRegistry.getDefault().getSourceRoots());
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        EditorDocument activeDocument = context == null ? null : context.getActiveDocument();
        boolean active = activeDocument != null && file.equals(activeDocument.getFileObject());
        CompletableFuture<List<EditorAnnotation>> result = new CompletableFuture<>();
        QUEUE.add(new Job(active, SEQUENCE.incrementAndGet(), () -> {
            if (cancelled.getAsBoolean()) {
                result.cancel(false);
                return;
            }
            try {
                List<EditorAnnotation> annotations = new ArrayList<>();
                for (OverriddenScanner.Mark mark : OverriddenScanner.scan(file, text, sourceRoots, cancelled)) {
                    annotations.add(annotationOf(mark));
                }
                result.complete(annotations);
            } catch (IOException | RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        }));
        return result;
    }

    private static EditorAnnotation annotationOf(OverriddenScanner.Mark mark) {
        boolean up = !mark.ancestors().isEmpty();
        boolean down = !mark.descendants().isEmpty();
        String icon = up && down ? "override-is-overridden-combined"
                : up ? (mark.implemented() ? "implements" : "overrides")
                : mark.implementor() ? "has-implementations" : "is-overridden";
        List<String> lines = new ArrayList<>();
        for (Implementation ancestor : mark.ancestors()) {
            lines.add(message(mark.implemented() ? "TIP_Implements" : "TIP_Overrides", ancestor.name()));
        }
        for (Implementation descendant : mark.descendants()) {
            String where = descendant.enclosing().isEmpty() ? descendant.name() : descendant.enclosing() + "." + descendant.name();
            lines.add(message(mark.implementor() ? "TIP_IsImplementedIn" : "TIP_IsOverriddenIn", where));
        }
        List<Implementation> items = new ArrayList<>(mark.ancestors());
        items.addAll(mark.descendants());
        String title = up && down
                ? message("TITLE_ImplementsIsOverridden", GoToCommand.IMPLEMENTATION_SHORTCUT.getDisplayText())
                : up ? message("TITLE_Overrides") : message("TITLE_Implementations");
        return new EditorAnnotation(mark.line(), NavigatorIcons.image(icon), String.join("\n", lines),
                (Point2D anchor) -> GoToOpener.offer(title, items, anchor));
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(OverriddenAnnotationProvider.class, key, args);
    }
}
