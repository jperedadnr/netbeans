package com.gluonhq.netbeans.nbfx.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ActionRegistry;
import com.gluonhq.netbeans.nbfx.api.actions.Command;
import com.gluonhq.netbeans.nbfx.api.actions.CommandsProvider;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import javafx.beans.value.ObservableValue;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default {@link ActionRegistry}. On creation, it discovers every {@link CommandsProvider} in the
 * global Lookup and registers the commands each one contributes, so all action modules publish
 * their commands the same way. Scoped variants are created by asking the providers.
 */
@ServiceProvider(service = ActionRegistry.class)
public class ActionRegistryImpl implements ActionRegistry {

    private static final Logger LOG = Logger.getLogger(ActionRegistryImpl.class.getName());

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final List<CommandsProvider> providers;

    public ActionRegistryImpl() {
        providers = List.copyOf(Lookup.getDefault().lookupAll(CommandsProvider.class));
        if (providers.isEmpty()) {
            LOG.warning("No CommandsProvider found; no built-in actions will be registered");
        }
        for (CommandsProvider provider : providers) {
            provider.createCommands().forEach(this::register);
        }
    }

    @Override
    public void register(Command command) {
        Objects.requireNonNull(command);
        commands.put(command.getId(), command);
    }

    @Override
    public Optional<Command> find(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    @Override
    public Collection<Command> getCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }

    @Override
    public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
        if (activeDocument == null) {
            return Optional.empty();
        }
        for (CommandsProvider provider : providers) {
            Optional<Command> scoped = provider.createScoped(id, activeDocument);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return Optional.empty();
    }
}
