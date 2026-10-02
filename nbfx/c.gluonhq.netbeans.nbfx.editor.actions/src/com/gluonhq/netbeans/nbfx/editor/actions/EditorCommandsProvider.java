package com.gluonhq.netbeans.nbfx.editor.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javafx.beans.value.ObservableValue;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes the built-in editor commands (Save / Save All / Save Project and
 * Undo / Redo / Cut / Copy / Paste / Find / Replace on the active document) to the shared registry, wiring their
 * enablement to the shared {@link EditorContext}. Also creates the window-scoped variants of the
 * active-document commands for detached editor windows.
 */
@ServiceProvider(service = CommandsProvider.class)
public class EditorCommandsProvider implements CommandsProvider {

    private static final Logger LOG = Logger.getLogger(EditorCommandsProvider.class.getName());

    private final EditorContext context;

    public EditorCommandsProvider() {
        context = Lookup.getDefault().lookup(EditorContext.class);
    }

    @Override
    public Collection<Command> createCommands() {
        if (context == null) {
            LOG.warning("No EditorContext found; editor actions will not be registered");
            return List.of();
        }
        ObservableValue<EditorDocument> activeDocument = context.activeDocumentProperty();
        return List.of(
                new SaveCommand(activeDocument),
                new SaveAllCommand(context),
                new SaveProjectCommand(context),
                new UndoCommand(activeDocument),
                new RedoCommand(activeDocument),
                new CopyCommand(activeDocument),
                new CutCommand(activeDocument),
                new PasteCommand(activeDocument),
                new FindCommand(activeDocument),
                new ReplaceCommand(activeDocument),
                new FindNextCommand(activeDocument),
                new FindPreviousCommand(activeDocument),
                new FindSelectionCommand(activeDocument));
    }

    @Override
    public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
        if (context == null || activeDocument == null) {
            return Optional.empty();
        }
        Command command = switch (id) {
            case ActionIds.SAVE -> new SaveCommand(activeDocument);
            case ActionIds.CUT -> new CutCommand(activeDocument);
            case ActionIds.COPY -> new CopyCommand(activeDocument);
            case ActionIds.PASTE -> new PasteCommand(activeDocument);
            case ActionIds.UNDO -> new UndoCommand(activeDocument);
            case ActionIds.REDO -> new RedoCommand(activeDocument);
            case ActionIds.FIND -> new FindCommand(activeDocument);
            case ActionIds.REPLACE -> new ReplaceCommand(activeDocument);
            case ActionIds.FIND_NEXT -> new FindNextCommand(activeDocument);
            case ActionIds.FIND_PREVIOUS -> new FindPreviousCommand(activeDocument);
            case ActionIds.FIND_SELECTION -> new FindSelectionCommand(activeDocument);
            default -> null;
        };
        return Optional.ofNullable(command);
    }
}
