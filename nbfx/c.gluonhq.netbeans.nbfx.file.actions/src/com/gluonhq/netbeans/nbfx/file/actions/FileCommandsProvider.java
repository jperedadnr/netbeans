package com.gluonhq.netbeans.nbfx.file.actions;

import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes the file-scoped commands (file Cut / Copy / Paste / Undo / Redo, built by
 * {@link FileActions}) to the shared registry, wiring their enablement to the shared
 * {@link FileSelectionContext}.
 */
@ServiceProvider(service = CommandsProvider.class)
public class FileCommandsProvider implements CommandsProvider {

    private static final Logger LOG = Logger.getLogger(FileCommandsProvider.class.getName());

    @Override
    public Collection<Command> createCommands() {
        FileSelectionContext context = Lookup.getDefault().lookup(FileSelectionContext.class);
        if (context == null) {
            LOG.warning("No FileSelectionContext found; file actions will not be registered");
            return List.of();
        }
        FileActions actions = new FileActions(context);
        return List.of(
                actions.cutCommand(),
                actions.copyCommand(),
                actions.pasteCommand(),
                actions.undoCommand(),
                actions.redoCommand());
    }
}
