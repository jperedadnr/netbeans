package com.gluonhq.netbeans.nbfx.project.ui.maven;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.embedder.MavenEmbedder;

/**
 * One in-process Maven build via {@link MavenEmbedder#execute}: sets the goals, base directory and
 * properties on a {@link MavenExecutionRequest} and runs it on a background thread. Maven's Plexus
 * log output is routed through java.util.logging under the embedder's logger, so a {@link Handler}
 * on that logger forwards it to the {@link BuildOutput}.
 *
 * @since 1.0
 */
final class MavenBuild implements BuildExecution {

    /** The embedder's JUL logger; Maven's Plexus logs are logged under it. */
    private static final String EMBEDDER_LOGGER = "org.netbeans.modules.maven.embedder.EmbedderFactory";

    private final java.nio.file.Path dir;
    private final String[] goals;
    private final BuildOutput output;
    private final BuildProgress progress;

    MavenBuild(java.nio.file.Path dir, String[] goals, BuildOutput output, BuildProgress progress) {
        this.dir = dir;
        this.goals = goals;
        this.output = output;
        this.progress = progress;
    }

    void start() {
        progress.start("maven", this::cancel);
        Thread thread = new Thread(this::run, "nbfx-maven-build");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        Logger logger = Logger.getLogger(EMBEDDER_LOGGER);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage() != null) {
                    output.append(record.getMessage() + "\n");
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Level previous = logger.getLevel();
        try {
            logger.addHandler(handler);
            logger.setLevel(Level.FINER);
            MavenEmbedder embedder = EmbedderFactory.getProjectEmbedder();
            MavenExecutionRequest request = embedder.createMavenExecutionRequest();
            request.setBaseDirectory(dir.toFile());
            request.setGoals(List.of(goals));
            MavenExecutionResult result = embedder.execute(request);
            if (result.hasExceptions()) {
                output.append("\nBUILD FAILED\n");
                for (Throwable failure : result.getExceptions()) {
                    output.append(String.valueOf(failure.getMessage()) + "\n");
                }
            } else {
                output.append("\nBUILD SUCCESSFUL\n");
            }
        } catch (Exception ex) {
            output.append("\nBUILD FAILED: " + ex + "\n");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
            progress.finish();
        }
    }

    @Override
    public void cancel() {
        // MavenEmbedder.execute has no cancellation hook; the build runs to completion.
    }
}
