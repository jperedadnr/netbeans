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
package com.gluonhq.netbeans.nbfx.api.progress;

/**
 * The status bar's single progress slot: a long-running activity shows its message and an optional
 * cancel action, the way NetBeans shows a running build or scan in the status line.
 * <p>
 * The service is resolved from the global Lookup and driven from any thread; the implementation
 * marshals to the JavaFX Application Thread. Only one activity can use the slot at a time: a new
 * {@link #start} replaces whatever was showing, and {@link #finish} hides it.
 *
 * @since 1.0
 */
public interface FxProgress {

    /**
     * Shows {@code message} in the status bar, running {@code onCancel} when the user presses the
     * cancel button; when {@code onCancel} is {@code null} the cancel button is hidden.
     *
     * @param message  the text shown next to the progress bar
     * @param onCancel the action run when cancel is pressed, or {@code null}
     */
    void start(String message, Runnable onCancel);

    /** Hides the progress slot. */
    void finish();
}
