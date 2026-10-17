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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single background thread the Navigator panels scan files on, so scans never run concurrently
 * and a stale scan is discarded rather than raced.
 *
 * @since 1.0
 */
public final class BackgroundScans {

    /** The scanning executor: one daemon thread named {@code nbfx-navigator}. */
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "nbfx-navigator");
        thread.setDaemon(true);
        return thread;
    });

    private BackgroundScans() {
    }
}
