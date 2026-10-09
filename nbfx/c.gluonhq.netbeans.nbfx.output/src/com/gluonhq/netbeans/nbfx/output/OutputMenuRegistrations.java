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

import static com.gluonhq.netbeans.nbfx.api.actions.ActionIds.SELECT_OUTPUT;

import com.gluonhq.netbeans.nbfx.annotations.FxActionReference;

/**
 * Declares the Window ▸ Output entry in the layer, next to the other view-selection entries.
 * <p>
 * This class carries metadata only; it is never instantiated.
 */
@FxActionReference(id = SELECT_OUTPUT, path = "Menus/Window", position = 55)
final class OutputMenuRegistrations {

    private OutputMenuRegistrations() {
    }
}
