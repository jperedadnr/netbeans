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
package com.gluonhq.netbeans.nbfx.statusbar;

import com.gluonhq.netbeans.nbfx.annotations.FxStatusAlignment;
import javafx.scene.Node;

/**
 * One element of the JavaFX status bar: the caret position, the line separator, the selected
 * project, a progress indicator, the VCS branch...
 * <p>
 * Elements are registered with {@code @FxStatusRegistration}; the generated layer entry under
 * {@code NbFx/Status} supplies the alignment and position, so the methods here are only a fallback
 * for programmatically created elements. The {@link #getNode() node} is created once, when the
 * status bar is built on the JavaFX Application Thread, and {@link #dispose() disposed} when the
 * status bar goes away.
 *
 * @since 1.0
 */
public interface FxStatusElement {

    /** The stable identifier of this element; must be unique across the application. */
    String getId();

    /**
     * The horizontal slot this element occupies. Overridden by the registered
     * {@code alignment} attribute when one is present.
     */
    default FxStatusAlignment alignment() {
        return FxStatusAlignment.LEFT;
    }

    /**
     * The position of this element within its slot. Overridden by the registered {@code position}
     * attribute when one is present.
     */
    default int position() {
        return 0;
    }

    /** The JavaFX node shown in the status bar. Must be called on the JavaFX Application Thread. */
    Node getNode();

    /** Releases any resources held by this element; called when the status bar is torn down. */
    default void dispose() {
    }
}
