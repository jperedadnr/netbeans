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
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.RUN;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.TEST;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;
import com.gluonhq.netbeans.nbfx.annotations.FxToolbarRegistration;

/**
 * Declares the Build tool bar contents in the layer, so the bar is assembled from registrations.
 * The processor writes {@code NbFx/Toolbars/build/*.ref}, which the window reads; the bar appears
 * only while a module registers entries in it.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxToolbarRegistration(id = "build", position = 40)
@FxActionReference(id = BUILD, path = "Toolbars/build", position = 10)
@FxActionReference(id = CLEAN_BUILD, path = "Toolbars/build", position = 20)
@FxActionReference(id = CLEAN, path = "Toolbars/build", position = 30)
@FxActionReference(id = TEST, path = "Toolbars/build", position = 40)
@FxActionReference(id = RUN, path = "Toolbars/build", position = 50)
final class BuildToolbarRegistrations {

    private BuildToolbarRegistrations() {
    }
}
