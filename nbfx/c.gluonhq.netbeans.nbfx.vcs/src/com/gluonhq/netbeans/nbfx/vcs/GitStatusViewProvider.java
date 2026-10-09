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
package com.gluonhq.netbeans.nbfx.vcs;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import javafx.scene.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Git view: the selected project's branch and changed files.
 *
 * @since 1.0
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = GitStatusViewProvider.ID, displayName = "Git",
        location = FxViewLocation.LEFT_BOTTOM, position = 10)
public final class GitStatusViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and the view manager. */
    public static final String ID = "git";

    private GitStatusView view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(GitStatusViewProvider.class, "Git.title");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.LEFT_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            view = new GitStatusView();
        }
        return view;
    }

    /** Brings the Git view on screen and refreshes it. Must run on the JavaFX Application Thread. */
    public void show() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
        GitStatusView current = view;
        if (current != null) {
            current.reload();
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static GitStatusViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(GitStatusViewProvider.class::isInstance)
                .map(GitStatusViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
