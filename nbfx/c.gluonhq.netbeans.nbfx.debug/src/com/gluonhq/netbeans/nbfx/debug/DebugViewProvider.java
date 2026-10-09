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
package com.gluonhq.netbeans.nbfx.debug;

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
 * The Debug view: the current session's threads, call stack and variables.
 *
 * @since 1.0
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = DebugViewProvider.ID, displayName = "Debug",
        location = FxViewLocation.CENTER_BOTTOM, position = 20)
public final class DebugViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and the view manager. */
    public static final String ID = "debug";

    private DebugView view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(DebugViewProvider.class, "Debug.title");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            view = new DebugView();
        }
        return view;
    }

    /** Brings the Debug view on screen. Must run on the JavaFX Application Thread. */
    public void show() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        } else {
            getView();
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static DebugViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(DebugViewProvider.class::isInstance)
                .map(DebugViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
