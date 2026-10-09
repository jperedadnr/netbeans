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

/**
 * A named JavaFX output console: the text of a build, a run, a test or any other streaming activity.
 * Appending is safe from any thread; the text is shown on the JavaFX Application Thread.
 *
 * @since 1.0
 */
public interface FxConsole {

    /** The console's name, shown on its tab. */
    String getName();

    /** Appends {@code text} to the console. Safe to call from any thread. */
    void append(String text);

    /** Clears the console. Safe to call from any thread. */
    void clear();

    /** Brings the console's tab to the front, if it is showing. */
    void show();
}
