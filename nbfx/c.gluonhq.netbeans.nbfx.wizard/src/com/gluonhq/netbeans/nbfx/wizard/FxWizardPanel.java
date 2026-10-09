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
package com.gluonhq.netbeans.nbfx.wizard;

import javafx.scene.Node;

/**
 * One page of an {@link FxWizard}: a title, a JavaFX component, and whether the input is valid.
 *
 * @since 1.0
 */
public interface FxWizardPanel {

    /** The page title, shown above the component. */
    String getTitle();

    /** The page's JavaFX component. Called once, on the JavaFX Application Thread. */
    Node getComponent();

    /** Whether the page's input is currently valid; the wizard gates Next/Finish on it. */
    default boolean isValid() {
        return true;
    }
}
