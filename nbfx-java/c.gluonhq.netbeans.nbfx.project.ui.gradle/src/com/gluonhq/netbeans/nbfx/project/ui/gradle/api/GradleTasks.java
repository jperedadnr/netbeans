package com.gluonhq.netbeans.nbfx.project.ui.gradle.api;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs Gradle tasks in-process, through the Gradle Tooling API, for the build commands of a Gradle
 * project and for a task picked elsewhere (the Navigator's <em>Related tasks</em>).
 *
 * @since 1.0
 */
public final class GradleTasks {

    private GradleTasks() {
    }

    /**
     * Starts {@code tasks} in the project at {@code dir}, streaming the build's log to
     * {@code output} and reporting to {@code progress}.
     *
     * @return the running build, to cancel it
     */
    public static BuildExecution start(Path dir, List<String> tasks, BuildOutput output, BuildProgress progress) {
        GradleBuild build = new GradleBuild(dir, tasks.toArray(String[]::new), output, progress);
        build.start();
        return build;
    }
}
