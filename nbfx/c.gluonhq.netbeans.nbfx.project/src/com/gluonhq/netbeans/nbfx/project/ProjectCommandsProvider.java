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

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import java.util.Collection;
import java.util.List;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes the New Project and New File commands, each opening its wizard.
 *
 * @since 1.0
 */
@ServiceProvider(service = CommandsProvider.class)
public final class ProjectCommandsProvider implements CommandsProvider {

    @Override
    public Collection<Command> createCommands() {
        return List.of(
                new WizardCommand(ActionIds.NEW_PROJECT, message("CTL_NewProjectCommand"),
                        new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        NewProjectWizard.ID),
                new WizardCommand(ActionIds.NEW_FILE, message("CTL_NewFileCommand"),
                        new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                        NewFileWizard.ID));
    }

    private static String message(String key) {
        return NbBundle.getMessage(ProjectCommandsProvider.class, key);
    }
}
