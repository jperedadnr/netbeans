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
import java.util.Objects;
import javafx.scene.Node;
import org.openide.filesystems.FileObject;

/**
 * A Navigator panel showing a {@link ScanningPanel} fed by a {@link DocumentTracker} while it is the
 * active panel of the window. The panel's node is created on first use, on the JavaFX Application
 * Thread, as the panels are instantiated by the Lookup.
 *
 * @param <R> the result of the panel's scan
 * @since 1.0
 */
public abstract class TrackedStructurePanel<R> implements FxStructurePanel {

    private ScanningPanel<R> ui;
    private DocumentTracker<R> tracker;

    /** Creates the panel's node; called once, on the JavaFX Application Thread. */
    protected abstract ScanningPanel<R> createUI();

    /** Called once the panel's node exists, before its tracker does; nothing by default. */
    protected void uiCreated(ScanningPanel<R> ui) {
    }

    /** Called once the panel's node and its tracker exist, so a subclass can observe the tracker. */
    protected void trackerCreated(DocumentTracker<R> tracker) {
    }

    /**
     * Whether {@code file} is one this panel scans: by default a data file of one of the panel's
     * {@linkplain #handles content types}.
     */
    protected boolean accepts(FileObject file) {
        return file != null && file.isData() && handles(file.getMIMEType());
    }

    /** The panel's node, created on first use. */
    protected final ScanningPanel<R> ui() {
        if (ui == null) {
            ui = Objects.requireNonNull(createUI(), "createUI");
            ui.getNode().getStyleClass().add("navigator-view");
            uiCreated(ui);
            tracker = new DocumentTracker<>(ui, this::accepts);
            trackerCreated(tracker);
        }
        return ui;
    }

    /** The tracker feeding the panel, created with its node. */
    protected final DocumentTracker<R> tracker() {
        ui();
        return tracker;
    }

    @Override
    public Node getComponent() {
        return ui().getNode();
    }

    @Override
    public void panelActivated(FxActionContext context) {
        tracker().activate(context);
    }

    @Override
    public void panelDeactivated() {
        if (tracker != null) {
            tracker.deactivate();
        }
    }
}
