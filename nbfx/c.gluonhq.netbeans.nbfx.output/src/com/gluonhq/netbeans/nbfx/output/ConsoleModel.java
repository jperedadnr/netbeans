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

import java.util.function.Consumer;

/**
 * The model behind a console: the accumulated text plus the hooks the view installs to render it.
 * Appends may come from a background thread, so they are marshalled to the JavaFX Application
 * Thread; the text is kept even before the view exists, and flushed to it when it attaches.
 */
final class ConsoleModel implements FxConsole {

    private final String name;
    private final StringBuilder text = new StringBuilder();
    private Consumer<String> appender;
    private Runnable clearer;
    private Runnable shower;

    ConsoleModel(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public synchronized void append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        text.append(delta);
        FxThread.run(() -> {
            if (appender != null) {
                appender.accept(delta);
            }
        });
    }

    @Override
    public synchronized void clear() {
        text.setLength(0);
        FxThread.run(() -> {
            if (clearer != null) {
                clearer.run();
            }
        });
    }

    @Override
    public void show() {
        FxThread.run(() -> {
            if (shower != null) {
                shower.run();
            }
        });
    }

    /** Installs the view hooks and flushes the text accumulated so far. Called on the FX thread. */
    synchronized void attach(Consumer<String> appender, Runnable clearer, Runnable shower) {
        this.appender = appender;
        this.clearer = clearer;
        this.shower = shower;
        appender.accept(text.toString());
    }
}
