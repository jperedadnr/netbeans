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
package com.gluonhq.netbeans.nbfx.wizard;

import java.util.Objects;

/**
 * A registered wizard: the metadata declared with
 * {@link com.gluonhq.netbeans.nbfx.annotations.FxWizardRegistration} plus the wizard itself.
 *
 * @param id          the wizard id (the layer file's base name)
 * @param displayName the dialog title
 * @param iconName    the icon resource name, or an empty string
 * @param category    the wizard's category
 * @param position    the position among the wizards of the same category
 * @param wizard      the wizard
 * @since 1.0
 */
public record WizardRegistration(String id, String displayName, String iconName, String category,
        int position, FxWizard wizard) {

    public WizardRegistration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(iconName, "iconName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(wizard, "wizard");
    }
}
