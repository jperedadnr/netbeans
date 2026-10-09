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
package com.gluonhq.netbeans.nbfx.editor.codearea;

import com.gluonhq.netbeans.nbfx.api.editor.EditorAnnotation;
import com.gluonhq.netbeans.nbfx.api.editor.EditorAnnotationProvider;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.util.Subscription;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * The gutter's annotations - NetBeans' override badges and the like - asked from the
 * {@link EditorAnnotationProvider} registered for the file's kind when the editor opens and after
 * each pause in editing, and handed to the {@link MarkLineNumberDecorator}, which keeps them on
 * markers so they follow the text until the next answer. Nothing is installed for a file no
 * provider handles.
 */
final class AnnotationSupport {

    private static final Logger LOG = Logger.getLogger(AnnotationSupport.class.getName());
    private static final long DELAY_MS = 700;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "annotations-delay");
        thread.setDaemon(true);
        return thread;
    });

    private final FileObject file;
    private final CodeTextModel model;
    private final MarkLineNumberDecorator decorator;
    private final Runnable refresh;
    private final EditorAnnotationProvider provider;
    private final AtomicLong sequence = new AtomicLong();
    private volatile ScheduledFuture<?> pending;

    private AnnotationSupport(FileObject file, CodeTextModel model, MarkLineNumberDecorator decorator,
            Runnable refresh, EditorAnnotationProvider provider) {
        this.file = file;
        this.model = model;
        this.decorator = decorator;
        this.refresh = refresh;
        this.provider = provider;
    }

    /**
     * Installs the support when a provider handles {@code file}; {@code refresh} rebuilds the
     * gutter's nodes. The subscription stops the updates.
     */
    static Subscription install(FileObject file, CodeTextModel model, MarkLineNumberDecorator decorator, Runnable refresh) {
        EditorAnnotationProvider provider = providerFor(file);
        if (provider == null) {
            return Subscription.EMPTY;
        }
        AnnotationSupport support = new AnnotationSupport(file, model, decorator, refresh, provider);
        StyledTextModel.Listener listener = change -> {
            if (change.isEdit()) {
                support.schedule();
            }
        };
        model.addListener(listener);
        support.schedule();
        return () -> {
            model.removeListener(listener);
            support.sequence.incrementAndGet();
            support.cancelPending();
        };
    }

    private void schedule() {
        sequence.incrementAndGet();
        cancelPending();
        pending = SCHEDULER.schedule(() -> Platform.runLater(this::query), DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelPending() {
        ScheduledFuture<?> scheduled = pending;
        if (scheduled != null) {
            scheduled.cancel(false);
            pending = null;
        }
    }

    /** Asks the provider for the text as it is now, applying the answer unless the text changed meanwhile. FX thread. */
    private void query() {
        long request = sequence.get();
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        String text = openSources == null ? null : openSources.textOf(file);
        if (text == null) {
            text = TextOffsets.text(model);
        }
        provider.annotate(file, text, () -> request != sequence.get()).whenComplete((annotations, failure) ->
                Platform.runLater(() -> {
                    if (request != sequence.get()) {
                        return;
                    }
                    if (failure != null) {
                        LOG.log(Level.WARNING, "Could not annotate " + file.getPath(), failure);
                        return;
                    }
                    decorator.setAnnotations(model, annotations == null ? List.of() : annotations);
                    refresh.run();
                }));
    }

    /** The first registered provider handling {@code file}, or {@code null}. */
    private static EditorAnnotationProvider providerFor(FileObject file) {
        for (EditorAnnotationProvider provider : Lookup.getDefault().lookupAll(EditorAnnotationProvider.class)) {
            if (provider.handles(file)) {
                return provider;
            }
        }
        return null;
    }
}
