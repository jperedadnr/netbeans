package com.gluonhq.netbeans.nbfx.findusages.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.actions.EditorContextMenuIds;
import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import javafx.beans.value.ObservableValue;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Registers the Find Usages commands: {@code findUsages} (Edit menu, editor context menu, and a
 * window-scoped variant for detached editor windows) and {@code selectUsages} (Window menu).
 */
@ServiceProvider(service = CommandsProvider.class)
public class FindUsagesCommandsProvider implements CommandsProvider {

    private static final Logger LOG = Logger.getLogger(FindUsagesCommandsProvider.class.getName());

    private final EditorContext context;
    private final UsagesModel model;

    public FindUsagesCommandsProvider() {
        context = Lookup.getDefault().lookup(EditorContext.class);
        model = Lookup.getDefault().lookup(UsagesModel.class);
    }

    @Override
    public Collection<Command> createCommands() {
        if (context == null || model == null) {
            LOG.warning("No EditorContext or UsagesModel found; Find Usages actions will not be registered");
            return List.of();
        }
        if (!EditorContextMenuIds.ids().contains(ActionIds.FIND_USAGES)) {
            EditorContextMenuIds.add(EditorContextMenuIds.SEPARATOR, ActionIds.FIND_USAGES);
        }
        return List.of(
                new FindUsagesCommand(context.activeDocumentProperty(), model),
                new SelectUsagesCommand());
    }

    @Override
    public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
        if (context == null || model == null || activeDocument == null || !ActionIds.FIND_USAGES.equals(id)) {
            return Optional.empty();
        }
        return Optional.of(new FindUsagesCommand(activeDocument, model));
    }
}
