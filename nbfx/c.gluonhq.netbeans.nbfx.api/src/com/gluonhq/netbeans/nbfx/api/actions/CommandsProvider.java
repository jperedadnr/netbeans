package com.gluonhq.netbeans.nbfx.api.actions;

import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.file.FileSelectionContext;

import java.util.Collection;
import java.util.Optional;

import javafx.beans.value.ObservableValue;

/**
 * A contribution of {@link Command}s to the shared {@link ActionRegistry}.
 * <p>
 * Modules publish their commands by registering an implementation in the global
 * {@link org.openide.util.Lookup} (typically with
 * {@code @ServiceProvider(service = CommandsProvider.class)}). The registry discovers all
 * providers when it is created and registers every contributed command, so no module needs to
 * push its commands into the registry by hand.
 */
public interface CommandsProvider {

    /**
     * Creates this provider's commands, resolving whatever context they need (for example
     * {@link EditorContext} or {@link FileSelectionContext}) from the global Lookup.
     *
     * @return the commands to register; empty if a required context is unavailable
     */
    Collection<Command> createCommands();

    /**
     * Creates a window-scoped variant of one of this provider's active-document commands, bound
     * to {@code activeDocument} instead of the global active document (see
     * {@link ActionRegistry#createScoped}).
     *
     * @param id             the command id (see {@link ActionIds})
     * @param activeDocument the window's active document, observed for changes
     * @return a fresh scoped command, or empty if {@code id} is not one of this provider's
     *         active-document commands
     */
    default Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
        return Optional.empty();
    }
}
