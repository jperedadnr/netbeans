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
package com.gluonhq.netbeans.nbfx.options;

import java.util.Objects;

/**
 * A registered options panel: the metadata declared with
 * {@link com.gluonhq.netbeans.nbfx.annotations.FxOptionsRegistration} plus the panel itself.
 *
 * @param id          the panel id (the layer file's base name)
 * @param displayName the display name
 * @param category    the category
 * @param position    the position among the panels
 * @param panel       the panel
 * @since 1.0
 */
public record OptionsRegistration(String id, String displayName, String category, int position,
        FxOptionsPanel panel) {

    public OptionsRegistration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(panel, "panel");
    }
}
