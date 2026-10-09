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
package com.gluonhq.netbeans.nbfx.javanavigator.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import javafx.beans.value.ObservableValue;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Registers the Navigator commands: {@code selectNavigator} (Window menu), the four Go to commands
 * and {@code inspectMembers} / {@code inspectFileMembers} (Navigate menu), all but the first with
 * window-scoped variants for detached editor windows. Where the commands appear is declared in the
 * layer: the menu bar entries by the shell's menu registrations, the editor's Navigate context
 * submenu by {@link NavigatorContextMenuRegistrations}.
 */
@ServiceProvider(service = CommandsProvider.class)
public class NavigatorCommandsProvider implements CommandsProvider {

    private static final Logger LOG = Logger.getLogger(NavigatorCommandsProvider.class.getName());

    private final EditorContext context = Lookup.getDefault().lookup(EditorContext.class);

    @Override
    public Collection<Command> createCommands() {
        if (context == null) {
            LOG.warning("No EditorContext found; the Inspect commands will not be registered");
            return List.of(new SelectNavigatorCommand());
        }
        ObservableValue<EditorDocument> activeDocument = context.activeDocumentProperty();
        List<Command> commands = new ArrayList<>();
        commands.add(new SelectNavigatorCommand());
        for (GoToResolver.Kind kind : GoToResolver.Kind.values()) {
            commands.add(new GoToCommand(kind, activeDocument));
        }
        commands.add(new InspectMembersCommand(activeDocument, true));
        commands.add(new InspectMembersCommand(activeDocument, false));
        return commands;
    }

    @Override
    public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
        if (context == null || activeDocument == null) {
            return Optional.empty();
        }
        for (GoToResolver.Kind kind : GoToResolver.Kind.values()) {
            if (GoToCommand.idOf(kind).equals(id)) {
                return Optional.of(new GoToCommand(kind, activeDocument));
            }
        }
        if (ActionIds.INSPECT_MEMBERS.equals(id)) {
            return Optional.of(new InspectMembersCommand(activeDocument, true));
        }
        if (ActionIds.INSPECT_FILE_MEMBERS.equals(id)) {
            return Optional.of(new InspectMembersCommand(activeDocument, false));
        }
        return Optional.empty();
    }
}
