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

import java.util.List;

/**
 * A JavaFX wizard: an ordered list of pages the user fills in, and the action that runs when they
 * press Finish.
 * <p>
 * Wizards are registered with {@code @FxWizardRegistration} and discovered through
 * {@link WizardRegistry}.
 *
 * @since 1.0
 */
public interface FxWizard {

    /** The stable identifier of the wizard; must match the id used in {@code @FxWizardRegistration}. */
    String getId();

    /** The wizard's display name, used as the dialog title. */
    String getDisplayName();

    /** The name of the icon resource shown for the wizard, or an empty string. */
    default String getIconName() {
        return "";
    }

    /** The category the wizard belongs to (for example {@code "Project"} or {@code "File"}). */
    default String getCategory() {
        return "";
    }

    /** The wizard's pages, in order; must not be empty. */
    List<FxWizardPanel> getPages();

    /** Performs the wizard's action, after the user presses Finish. Called on the JavaFX thread. */
    void finish();
}
