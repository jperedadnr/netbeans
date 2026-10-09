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
package com.gluonhq.netbeans.nbfx.project;

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.NEW_PROJECT;
import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.OPEN_PROJECT;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;
import com.gluonhq.netbeans.nbfx.annotations.FxToolbarRegistration;

/**
 * Declares the File tool bar and its project entries in the layer, so the window builds the tool bar
 * from registrations. The editor module adds the save entries to the same bar.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxToolbarRegistration(id = "file", position = 10)
@FxActionReference(id = NEW_PROJECT, path = "Toolbars/file", position = 10)
@FxActionReference(id = OPEN_PROJECT, path = "Toolbars/file", position = 20)
final class ProjectToolbarRegistrations {

    private ProjectToolbarRegistrations() {
    }
}
