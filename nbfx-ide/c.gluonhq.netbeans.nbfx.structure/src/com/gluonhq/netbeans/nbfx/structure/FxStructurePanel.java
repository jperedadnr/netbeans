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
import javafx.scene.Node;
import org.openide.filesystems.FileObject;

/**
 * A structure view contributed for one or more content types, mirroring the Swing
 * {@code org.netbeans.spi.navigator.NavigatorPanel}.
 * <p>
 * A panel shows the structure of the object in the global action context (for example the members of
 * the active Java document). Implementations are registered in the global Lookup with
 * {@code @ServiceProvider(service = FxStructurePanel.class)} and resolved by the content type
 * (MIME) of the context's file.
 *
 * @since 1.0
 */
public interface FxStructurePanel {

    /** The name shown in the panel selector. */
    String getDisplayName();

    /** The content types (MIME types) this panel handles. */
    String[] contentTypes();

    /**
     * Whether this panel shows the structure of files of {@code contentType}: by default one of its
     * {@link #contentTypes()}. A panel for a family of types (the XML view, for any {@code +xml}
     * type) overrides this.
     *
     * @since 1.0
     */
    default boolean handles(String contentType) {
        for (String type : contentTypes()) {
            if (type.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The panel's content, suitable for adding to a scene graph. Must return the same node on every
     * call.
     */
    Node getComponent();

    /**
     * An optional toolbar shown in the window's header beside the panel selector (as a Swing
     * {@code NavigatorPanelWithToolbar}'s), or {@code null} when there is none. Must return the same
     * node on every call.
     */
    default Node getToolbar() {
        return null;
    }

    /**
     * Called when this panel becomes the shown one, so it can attach to the context. Called on the
     * JavaFX Application Thread.
     *
     * @param context the global action context the panel should read from
     */
    default void panelActivated(FxActionContext context) {
    }

    /** Called when this panel is about to be hidden, so it can detach from the context. */
    default void panelDeactivated() {
    }

    /**
     * The file whose structure should be shown for {@code context}: the file the active component
     * carries. The global context now follows the active component (§22), so this is simply the
     * context's {@link FileObject} - the active editor document's file, or the file selected in the
     * active navigator - with no precedence heuristic.
     *
     * @param context the global action context
     * @return the file to show, or {@code null} when the context carries none
     * @since 1.0
     */
    static FileObject selectedFile(FxActionContext context) {
        return context.lookup(FileObject.class);
    }
}
