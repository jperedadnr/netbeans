package com.gluonhq.netbeans.nbfx.build.ant;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildActionProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildCommands;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.openide.util.lookup.ServiceProvider;

/**
 * Ant build commands, run in-process with the bundled Apache Ant (no CLI shell-out). A NetBeans
 * module (apisupport) project uses the harness targets; a plain Ant project uses the conventional
 * {@code jar}/{@code test}/{@code javadoc} targets.
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
        String[] targets = targets(dir, command);
        if (targets == null) {
            return null;
        }
        List<String> line = new ArrayList<>(targets.length + 1);
        line.add("ant");
        line.addAll(List.of(targets));
        return List.copyOf(line);
    }

    @Override
    public BuildExecution start(Path dir, String command, BuildOutput output, BuildProgress progress) {
        String[] targets = targets(dir, command);
        if (targets == null) {
            return null;
        }
        AntBuild build = new AntBuild(dir, targets, output, progress);
        build.start();
        return build;
    }

    private static String[] targets(Path dir, String command) {
        boolean netbeansModule = Files.isRegularFile(dir.resolve("nbproject").resolve("project.xml"));
        if (netbeansModule) {
            return switch (command) {
                case BuildCommands.BUILD -> new String[] {"build"};
                case BuildCommands.CLEAN -> new String[] {"clean"};
                case BuildCommands.REBUILD -> new String[] {"clean", "build"};
                case BuildCommands.RUN -> new String[] {"run"};
                case BuildCommands.TEST -> new String[] {"test-unit"};
                case BuildCommands.JAVADOC -> new String[] {"javadoc-nb"};
                default -> null;
            };
        }
        return switch (command) {
            case BuildCommands.BUILD -> new String[] {"jar"};
            case BuildCommands.CLEAN -> new String[] {"clean"};
            case BuildCommands.REBUILD -> new String[] {"clean", "jar"};
            case BuildCommands.RUN -> new String[] {"run"};
            case BuildCommands.TEST -> new String[] {"test"};
            case BuildCommands.JAVADOC -> new String[] {"javadoc"};
            default -> null;
        };
    }
}
