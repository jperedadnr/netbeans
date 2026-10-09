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
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MembersScanner;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javax.lang.model.element.Element;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.SourceUtils;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Locates and opens members - the NetBeans Navigator's <em>Go to Source</em>: a member declared in
 * the scanned file is at its name there; an inherited one is resolved to the source file that
 * declares it, when the project has it, on the {@link Background} thread.
 */
final class MemberOpener {

    private static final Logger LOG = Logger.getLogger(MemberOpener.class.getName());

    private MemberOpener() {
    }

    /**
     * Where {@code node} is declared: at once for a member of the scanned file, after a background
     * resolution for an inherited one. Completes with {@code null} when the declaration cannot be
     * found (a library class without sources).
     */
    static CompletableFuture<SourceLocation> locate(MembersScanner.Result result, MemberNode node) {
        if (result == null || node == null) {
            return CompletableFuture.completedFuture(null);
        }
        int offset = node.getOpenOffset();
        if (offset >= 0) {
            return CompletableFuture.completedFuture(new SourceLocation(result.file(), offset));
        }
        ElementHandle<? extends Element> handle = node.getHandle();
        if (handle == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<SourceLocation> location = new CompletableFuture<>();
        Background.EXECUTOR.execute(() -> {
            FileObject source = SourceUtils.getFile(handle, result.classpath());
            if (source == null) {
                LOG.log(Level.INFO, "No source for {0}", node.getText());
                location.complete(null);
                return;
            }
            int declaration = -1;
            try {
                OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
                declaration = MembersScanner.declarationOffset(source,
                        openSources == null ? null : openSources.textOf(source), handle);
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not locate " + node.getText() + " in " + source.getPath(), ex);
            }
            // The declaring file is worth opening even when the declaration was not found in it.
            location.complete(new SourceLocation(source, Math.max(declaration, 0)));
        });
        return location;
    }

    /** Opens {@code node} in the editor, at its declaration. Must be called on the FX thread. */
    static void open(MembersScanner.Result result, MemberNode node) {
        locate(result, node).thenAccept(location -> {
            if (location == null) {
                return;
            }
            Platform.runLater(() -> {
                ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
                if (contentManager != null) {
                    contentManager.openFile(location.file(), location.offset(), location.offset());
                }
            });
        });
    }
}
