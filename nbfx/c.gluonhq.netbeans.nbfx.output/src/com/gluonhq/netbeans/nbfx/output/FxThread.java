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
package com.gluonhq.netbeans.nbfx.output;

import javafx.application.Platform;

/**
 * Runs work on the JavaFX Application Thread, falling back to the calling thread when the JavaFX
 * toolkit is not running (headless or unit tests), where the {@code Platform} calls throw.
 */
final class FxThread {

    private FxThread() {
    }

    static void run(Runnable action) {
        if (isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (RuntimeException ex) {
            // No JavaFX toolkit (headless or unit tests): run inline.
            action.run();
        }
    }

    private static boolean isFxApplicationThread() {
        try {
            return Platform.isFxApplicationThread();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
