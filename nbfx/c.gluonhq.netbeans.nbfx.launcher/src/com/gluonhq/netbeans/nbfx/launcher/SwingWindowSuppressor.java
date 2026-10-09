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
package com.gluonhq.netbeans.nbfx.launcher;

import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.util.logging.Logger;
import org.openide.windows.WindowManager;

/**
 * Hides the NetBeans Swing main window, which the platform starts but the JavaFX frontend does not
 * use.
 * <p>
 * <b>Quarantine.</b> This is the only nbfx code allowed to touch Swing/AWT (via
 * {@link WindowManager} and the AWT toolkit), and it is temporary: it exists because the Swing
 * window system is still part of the cluster. When the window system is fully replaced it, and this
 * class, are removed. The purity guardrail exempts exactly this file.
 *
 * @since 1.0
 */
final class SwingWindowSuppressor {

    private static final Logger LOG = Logger.getLogger(SwingWindowSuppressor.class.getName());

    private final AWTEventListener windowSuppressor = this::onAwtEvent;

    private SwingWindowSuppressor() {
    }

    /** Installs the AWT listener that hides the platform's main window as soon as it appears. */
    static void install() {
        LOG.info("NetBeans main window, AWT listener added");
        Toolkit.getDefaultToolkit().addAWTEventListener(new SwingWindowSuppressor().windowSuppressor,
                AWTEvent.COMPONENT_EVENT_MASK);
    }

    private void onAwtEvent(AWTEvent event) {
        Frame mainWindow;
        try {
            mainWindow = WindowManager.getDefault().getMainWindow();
        } catch (Exception e) {
            return; // too early, window manager not yet initialized
        }
        if (mainWindow == null) {
            return;
        }

        // Component events (SHOWN / RESIZED)
        if (event instanceof ComponentEvent ce && ce.getComponent() == mainWindow && !mainWindow.isDisplayable()) {
            try {
                mainWindow.setUndecorated(true);
                mainWindow.setOpacity(0f);
                mainWindow.setFocusable(false);
            } catch (Exception e) {
                // ignore and hide the window on next events instead
            }
            LOG.info("mainWindow suppressed (opacity=0)");
        } else if (mainWindow.isVisible()) {
            mainWindow.setVisible(false);
            mainWindow.setFocusable(false);
            Toolkit.getDefaultToolkit().removeAWTEventListener(windowSuppressor);
            LOG.info("mainWindow hidden");
        }
    }
}
