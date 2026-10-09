package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildActionProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildCommands;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.openide.util.lookup.ServiceProvider;

/**
 * Ant build commands. A NetBeans module (apisupport) project - recognised by its
 * {@code nbproject/project.xml} - uses the harness targets; a plain Ant project uses the
 * conventional {@code jar}/{@code test}/{@code javadoc} targets.
 */
@ServiceProvider(service = BuildActionProvider.class)
public final class AntBuildActionProvider implements BuildActionProvider {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String[] getSupportedActions() {
        return new String[] {
            BuildCommands.BUILD, BuildCommands.CLEAN, BuildCommands.REBUILD,
            BuildCommands.RUN, BuildCommands.TEST, BuildCommands.JAVADOC
        };
    }

    @Override
    public List<String> commandLine(Path dir, String command) {
        boolean netbeansModule = Files.isRegularFile(dir.resolve("nbproject").resolve("project.xml"));
        if (netbeansModule) {
            return switch (command) {
                case BuildCommands.BUILD -> List.of("ant", "build");
                case BuildCommands.CLEAN -> List.of("ant", "clean");
                case BuildCommands.REBUILD -> List.of("ant", "clean", "build");
                case BuildCommands.RUN -> List.of("ant", "run");
                case BuildCommands.TEST -> List.of("ant", "test-unit");
                case BuildCommands.JAVADOC -> List.of("ant", "javadoc-nb");
                default -> null;
            };
        }
        return switch (command) {
            case BuildCommands.BUILD -> List.of("ant", "jar");
            case BuildCommands.CLEAN -> List.of("ant", "clean");
            case BuildCommands.REBUILD -> List.of("ant", "clean", "jar");
            case BuildCommands.RUN -> List.of("ant", "run");
            case BuildCommands.TEST -> List.of("ant", "test");
            case BuildCommands.JAVADOC -> List.of("ant", "javadoc");
            default -> null;
        };
    }
}
