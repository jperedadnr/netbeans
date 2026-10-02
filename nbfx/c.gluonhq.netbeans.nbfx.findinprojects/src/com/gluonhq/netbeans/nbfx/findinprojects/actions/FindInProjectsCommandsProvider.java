package com.gluonhq.netbeans.nbfx.findinprojects.actions;

import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FindModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Registers the Find in Projects commands: {@code findInProjects} and {@code replaceInProjects}
 * (Edit menu), {@code selectSearchResults} (Window menu) and {@code file.find} (Shortcut+F on the
 * navigator selection, when a {@link FileSelectionContext} is available).
 */
@ServiceProvider(service = CommandsProvider.class)
public class FindInProjectsCommandsProvider implements CommandsProvider {

    private static final Logger LOG = Logger.getLogger(FindInProjectsCommandsProvider.class.getName());

    @Override
    public Collection<Command> createCommands() {
        FindModel model = Lookup.getDefault().lookup(FindModel.class);
        if (model == null) {
            LOG.warning("No FindModel found; Find in Projects actions will not be registered");
            return List.of();
        }
        EditorContext editors = Lookup.getDefault().lookup(EditorContext.class);
        FindInProjectsCommand find = new FindInProjectsCommand(model, editors);
        FindInProjectsCommand replace = new FindInProjectsCommand(model, editors, true);
        List<Command> commands = new ArrayList<>(List.of(find, replace, new SelectSearchResultsCommand()));
        FileSelectionContext selection = Lookup.getDefault().lookup(FileSelectionContext.class);
        if (selection != null) {
            commands.add(FindInSelectionCommand.create(selection, find::open));
        }
        return commands;
    }
}
