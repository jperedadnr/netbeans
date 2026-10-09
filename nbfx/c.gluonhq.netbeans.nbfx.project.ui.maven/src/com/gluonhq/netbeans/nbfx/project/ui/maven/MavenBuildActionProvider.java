package com.gluonhq.netbeans.nbfx.project.ui.maven;

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

/** Maven build commands, run in-process via {@link MavenBuild} (CLI fallback via mvn/mvnw). */
@ServiceProvider(service = BuildActionProvider.class)
public final class MavenBuildActionProvider implements BuildActionProvider {

    @Override
    public String projectTypeId() {
        return "maven";
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
        String[] goals = goals(command);
        if (goals == null) {
            return null;
        }
        List<String> line = new ArrayList<>(goals.length + 1);
        line.add(executable(dir, "mvnw", "mvn"));
        line.addAll(List.of(goals));
        return List.copyOf(line);
    }

    @Override
    public BuildExecution start(Path dir, String command, BuildOutput output, BuildProgress progress) {
        String[] goals = goals(command);
        if (goals == null) {
            return null;
        }
        MavenBuild build = new MavenBuild(dir, goals, output, progress);
        build.start();
        return build;
    }

    private static String[] goals(String command) {
        return switch (command) {
            case BuildCommands.BUILD -> new String[] {"package"};
            case BuildCommands.CLEAN -> new String[] {"clean"};
            case BuildCommands.REBUILD -> new String[] {"clean", "package"};
            case BuildCommands.TEST -> new String[] {"test"};
            case BuildCommands.RUN -> new String[] {"compile", "exec:java"};
            case BuildCommands.JAVADOC -> new String[] {"javadoc:javadoc"};
            default -> null;
        };
    }

    private static String executable(Path dir, String wrapper, String fallback) {
        Path unix = dir.resolve(wrapper);
        if (Files.isRegularFile(unix)) {
            return unix.toString();
        }
        Path windows = dir.resolve(wrapper + ".cmd");
        return Files.isRegularFile(windows) ? windows.toString() : fallback;
    }
}
