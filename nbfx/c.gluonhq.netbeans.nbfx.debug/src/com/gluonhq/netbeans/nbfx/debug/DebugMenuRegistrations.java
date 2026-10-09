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
package com.gluonhq.netbeans.nbfx.debug;

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.DEBUG_CONTINUE;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.DEBUG_FILE;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.DEBUG_STEP_INTO;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.DEBUG_STEP_OUT;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.DEBUG_STEP_OVER;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.DEBUG_STOP;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.TOGGLE_BREAKPOINT;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;
import com.gluonhq.netbeans.nbfx.annotations.FxMenuRegistration;

/**
 * Declares the Debug menu entries in the layer.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxMenuRegistration(id = "Debug", position = 50)
@FxActionReference(id = DEBUG_FILE, path = "Menus/Debug", position = 10)
@FxActionReference(id = DEBUG_CONTINUE, path = "Menus/Debug", position = 20, separatorBefore = true)
@FxActionReference(id = DEBUG_STEP_OVER, path = "Menus/Debug", position = 30)
@FxActionReference(id = DEBUG_STEP_INTO, path = "Menus/Debug", position = 40)
@FxActionReference(id = DEBUG_STEP_OUT, path = "Menus/Debug", position = 50)
@FxActionReference(id = DEBUG_STOP, path = "Menus/Debug", position = 60, separatorBefore = true)
@FxActionReference(id = TOGGLE_BREAKPOINT, path = "Menus/Debug", position = 70, separatorBefore = true)
final class DebugMenuRegistrations {

    private DebugMenuRegistrations() {
    }
}
