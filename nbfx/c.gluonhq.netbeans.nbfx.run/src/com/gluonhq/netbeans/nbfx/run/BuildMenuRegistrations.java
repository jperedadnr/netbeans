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
package com.gluonhq.netbeans.nbfx.run;

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.BUILD;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.CLEAN;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.CLEAN_BUILD;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.JAVADOC;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.RUN;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.TEST;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;
import com.gluonhq.netbeans.nbfx.annotations.FxMenuRegistration;

/**
 * Declares the Build menu entries in the layer, so the menu is assembled from registrations. The
 * processor writes {@code NbFx/Menus/Build/*.ref}, which the window reads.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxMenuRegistration(id = "Build", position = 40)
@FxActionReference(id = BUILD, path = "Menus/Build", position = 10)
@FxActionReference(id = CLEAN_BUILD, path = "Menus/Build", position = 20)
@FxActionReference(id = CLEAN, path = "Menus/Build", position = 30, separatorBefore = true)
@FxActionReference(id = TEST, path = "Menus/Build", position = 40, separatorBefore = true)
@FxActionReference(id = RUN, path = "Menus/Build", position = 50, separatorBefore = true)
@FxActionReference(id = JAVADOC, path = "Menus/Build", position = 60, separatorBefore = true)
final class BuildMenuRegistrations {

    private BuildMenuRegistrations() {
    }
}
