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

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import javafx.scene.input.KeyCombination;

/**
 * A command that opens a registered wizard by id.
 *
 * @since 1.0
 */
final class WizardCommand extends AbstractCommand {

    private final String wizardId;

    WizardCommand(String id, String text, KeyCombination accelerator, String wizardId) {
        super(id, text, accelerator, false);
        this.wizardId = wizardId;
    }

    @Override
    public void run() {
        Wizards.run(wizardId);
    }
}
