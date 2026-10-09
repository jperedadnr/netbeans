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

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildCommands;
import java.util.Collection;
import java.util.List;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes the Build / Clean / Test / Run commands, acting on the selected project through its
 * build tool.
 *
 * @since 1.0
 */
@ServiceProvider(service = CommandsProvider.class)
public final class BuildCommandsProvider implements CommandsProvider {

    private final ProjectRegistry projects = Lookup.getDefault().lookup(ProjectRegistry.class);

    @Override
    public Collection<Command> createCommands() {
        return List.of(
                new BuildCommand(projects, ActionIds.BUILD, message("BuildCommand.build"),
                        BuildCommands.BUILD),
                new BuildCommand(projects, ActionIds.CLEAN_BUILD, message("BuildCommand.cleanBuild"),
                        BuildCommands.REBUILD),
                new BuildCommand(projects, ActionIds.CLEAN, message("BuildCommand.clean"),
                        BuildCommands.CLEAN),
                new BuildCommand(projects, ActionIds.TEST, message("BuildCommand.test"),
                        BuildCommands.TEST),
                new BuildCommand(projects, ActionIds.RUN, message("BuildCommand.run"),
                        BuildCommands.RUN),
                new BuildCommand(projects, ActionIds.JAVADOC, message("BuildCommand.javadoc"),
                        BuildCommands.JAVADOC));
    }

    private static String message(String key) {
        return NbBundle.getMessage(BuildCommandsProvider.class, key);
    }
}
