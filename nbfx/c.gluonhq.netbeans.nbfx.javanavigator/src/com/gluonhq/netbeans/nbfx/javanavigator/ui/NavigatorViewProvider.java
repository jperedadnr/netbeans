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

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.editor.OpenSources;
import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.javanavigator.model.InspectedType;
import com.gluonhq.netbeans.nbfx.javanavigator.model.TypeResolver;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The "Navigator" view: a dockable tab, below the Projects and Files views by default (NetBeans'
 * {@code navigator} mode) and part of the default layout, showing the members of the class in the
 * active editor.
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = NavigatorViewProvider.ID, displayName = "#TITLE_Navigator",
        iconName = "com/gluonhq/netbeans/nbfx/javanavigator/ui/icons/navigator.png",
        location = FxViewLocation.LEFT_BOTTOM, position = 5, openAtStartup = true)
public final class NavigatorViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and {@link ViewManager}. */
    public static final String ID = "navigator";

    private static final Logger LOG = Logger.getLogger(NavigatorViewProvider.class.getName());

    private NavigatorView view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(NavigatorViewProvider.class, "TITLE_Navigator");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(NavigatorViewProvider.class, "DESC_Navigator");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.LEFT_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            view = new NavigatorView();
        }
        return view;
    }

    /** Brings the view on screen and focuses its tree. Must run on the FX thread. */
    public void show() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
    }

    /**
     * Inspects the members of the type at {@code offset} of {@code file} (Navigate &#9656; Inspect
     * &#9656; Members): resolves it in the background, then shows the file declaring it in the
     * Navigator with its row selected and records it in the history. Reports when nothing typed is
     * there. Must be called on the FX thread.
     */
    public void inspectAt(FileObject file, int offset) {
        inspect(file, text -> TypeResolver.atCaret(file, text, offset));
    }

    /** Inspects the members of the main type of {@code file} (Navigate &#9656; Inspect &#9656; File Members). FX thread. */
    public void inspectFile(FileObject file) {
        inspect(file, text -> TypeResolver.mainType(file, text));
    }

    private void inspect(FileObject file, Resolution resolution) {
        OpenSources openSources = Lookup.getDefault().lookup(OpenSources.class);
        String text = openSources == null ? null : openSources.textOf(file);
        Background.EXECUTOR.execute(() -> {
            InspectedType type = null;
            Throwable failure = null;
            try {
                String source = text != null ? text : file.asText().replace("\r\n", "\n").replace('\r', '\n');
                type = resolution.resolve(source);
            } catch (IOException | RuntimeException ex) {
                failure = ex;
            }
            InspectedType resolved = type;
            Throwable failed = failure;
            Platform.runLater(() -> {
                if (failed != null) {
                    LOG.log(Level.WARNING, "Could not inspect " + file.getPath(), failed);
                    ErrorReporter.report(getTitle(), null, NbBundle.getMessage(NavigatorViewProvider.class, "ERR_Inspect"), failed);
                    return;
                }
                if (resolved == null) {
                    ErrorReporter.report(getTitle(), null, NbBundle.getMessage(NavigatorViewProvider.class, "ERR_NotDeclaredType"));
                    return;
                }
                show();
                ((NavigatorView) getView()).inspect(resolved);
            });
        });
    }

    /** Resolves the type to inspect from a file's text; background thread. */
    private interface Resolution {
        InspectedType resolve(String text) throws IOException;
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static NavigatorViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(NavigatorViewProvider.class::isInstance)
                .map(NavigatorViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
