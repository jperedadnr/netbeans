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
package com.gluonhq.netbeans.nbfx.ui;

import com.gluonhq.netbeans.nbfx.api.progress.FxProgress;
import com.gluonhq.netbeans.nbfx.ui.shell.ProgressStatusElement;
import org.openide.util.lookup.ServiceProvider;

/**
 * The launcher's {@link FxProgress}: forwards the progress slot to the main window's
 * {@link ProgressStatusElement}. The main window binds the element while starting up; before that,
 * progress requests are ignored.
 *
 * @since 1.0
 */
@ServiceProvider(service = FxProgress.class)
public final class FxProgressImpl implements FxProgress {

    private volatile ProgressStatusElement element;

    /** Binds the main window's progress element. Called once while the window is being built. */
    void bind(ProgressStatusElement element) {
        this.element = element;
    }

    @Override
    public void start(String message, Runnable onCancel) {
        ProgressStatusElement target = element;
        if (target != null) {
            target.show(message, onCancel);
        }
    }

    @Override
    public void finish() {
        ProgressStatusElement target = element;
        if (target != null) {
            target.hide();
        }
    }
}
