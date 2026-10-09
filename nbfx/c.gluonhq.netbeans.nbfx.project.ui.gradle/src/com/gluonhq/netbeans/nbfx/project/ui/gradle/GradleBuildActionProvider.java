package com.gluonhq.netbeans.nbfx.project.ui.gradle;

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

/** Gradle build commands, run in-process with the Gradle Tooling API (CLI fallback via gradlew). */
@ServiceProvider(service = BuildActionProvider.class)
public final class GradleBuildActionProvider implements BuildActionProvider {

    @Override
    public String projectTypeId() {
        return "gradle";
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
        String[] tasks = tasks(command);
        if (tasks == null) {
            return null;
        }
        List<String> line = new ArrayList<>(tasks.length + 1);
        line.add(executable(dir, "gradlew", "gradle"));
        line.addAll(List.of(tasks));
        return List.copyOf(line);
    }

    @Override
    public BuildExecution start(Path dir, String command, BuildOutput output, BuildProgress progress) {
        String[] tasks = tasks(command);
        if (tasks == null) {
            return null;
        }
        GradleBuild build = new GradleBuild(dir, tasks, output, progress);
        build.start();
        return build;
    }

    private static String[] tasks(String command) {
        return switch (command) {
            case BuildCommands.BUILD -> new String[] {"build"};
            case BuildCommands.CLEAN -> new String[] {"clean"};
            case BuildCommands.REBUILD -> new String[] {"clean", "build"};
            case BuildCommands.TEST -> new String[] {"test"};
            case BuildCommands.RUN -> new String[] {"run"};
            case BuildCommands.JAVADOC -> new String[] {"javadoc"};
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
