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

import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import java.nio.file.Paths;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * A build command (Build / Clean / Test / Run) acting on the selected project. The project's build
 * tool is detected from its directory, its command line is run in the background, and the output is
 * streamed into a console named after the action and the project. The command is disabled while no
 * project is selected.
 *
 * @since 1.0
 */
final class BuildCommand implements Command {

    private final ProjectRegistry projects;
    private final String id;
    private final String text;
    private final String command;
    private final ReadOnlyBooleanWrapper disabled = new ReadOnlyBooleanWrapper(this, "disabled", true);

    BuildCommand(ProjectRegistry projects, String id, String text, String command) {
        this.projects = projects;
        this.id = id;
        this.text = text;
        this.command = command;
        if (projects != null) {
            disabled.bind(Bindings.createBooleanBinding(
                    () -> {
                        OpenProject project = projects.selectedProjectProperty().getValue();
                        return project == null || !BuildActions.isEnabled(Paths.get(project.getPath()), command);
                    },
                    projects.selectedProjectProperty()));
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public boolean isDisabled() {
        return disabled.get();
    }

    @Override
    public ReadOnlyBooleanProperty disabledProperty() {
        return disabled.getReadOnlyProperty();
    }

    @Override
    public void run() {
        OpenProject project = projects == null ? null : projects.getSelected();
        if (project == null) {
            return;
        }
        BuildRunner.run(Paths.get(project.getPath()), command, text + " " + project.getDisplayName());
    }
}
