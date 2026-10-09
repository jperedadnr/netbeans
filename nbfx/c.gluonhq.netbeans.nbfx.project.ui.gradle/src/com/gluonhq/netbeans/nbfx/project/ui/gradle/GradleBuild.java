package com.gluonhq.netbeans.nbfx.project.ui.gradle;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

/**
 * One in-process Gradle build driven by the Gradle Tooling API: connects to the project (using the
 * wrapper distribution when present, else a default Gradle version), runs the requested tasks on a
 * background thread, and streams their output into a {@link BuildOutput}. Cancellation uses the
 * Tooling API's {@link CancellationTokenSource}.
 *
 * @since 1.0
 */
final class GradleBuild implements BuildExecution {

    private static final String DEFAULT_GRADLE_VERSION = "9.8";

    private final Path dir;
    private final String[] tasks;
    private final BuildOutput output;
    private final BuildProgress progress;
    private final AtomicReference<CancellationTokenSource> token = new AtomicReference<>();

    GradleBuild(Path dir, String[] tasks, BuildOutput output, BuildProgress progress) {
        this.dir = dir;
        this.tasks = tasks;
        this.output = output;
        this.progress = progress;
    }

    void start() {
        progress.start("gradle", this::cancel);
        Thread thread = new Thread(this::run, "nbfx-gradle-build");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            output.append("Connecting to Gradle...\n");
            GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(dir.toFile());
            configureDistribution(connector);
            CancellationTokenSource source = GradleConnector.newCancellationTokenSource();
            token.set(source);
            try (ProjectConnection connection = connector.connect()) {
                output.append("Connected. Running: " + String.join(" ", tasks) + "\n");
                BuildLauncher launcher = connection.newBuild();
                launcher.forTasks(tasks);
                launcher.setStandardOutput(outputStream());
                launcher.setStandardError(outputStream());
                launcher.setColorOutput(false);
                launcher.withCancellationToken(source.token());
                launcher.run();
            }
            output.append("\nBUILD SUCCESSFUL\n");
        } catch (Throwable ex) {
            output.append("\nBUILD FAILED: " + ex + "\n");
            java.util.logging.Logger.getLogger(GradleBuild.class.getName())
                    .log(java.util.logging.Level.WARNING, "Gradle build failed", ex);
        } finally {
            token.set(null);
            progress.finish();
        }
    }

    @Override
    public void cancel() {
        CancellationTokenSource source = token.get();
        if (source != null) {
            source.cancel();
        }
    }

    private void configureDistribution(GradleConnector connector) {
        URI distribution = wrapperDistribution();
        if (distribution != null) {
            connector.useDistribution(distribution);
        } else {
            connector.useGradleVersion(DEFAULT_GRADLE_VERSION);
        }
    }

    /** The distribution URL declared by the project's Gradle wrapper, or {@code null}. */
    private URI wrapperDistribution() {
        Path properties = dir.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(properties)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(properties)) {
            Properties props = new Properties();
            props.load(in);
            String url = props.getProperty("distributionUrl");
            return url == null || url.isBlank() ? null : URI.create(url.replace("\\", ""));
        } catch (IOException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** An output stream that reassembles lines and forwards them to the {@link BuildOutput}. */
    private OutputStream outputStream() {
        return new OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public synchronized void write(int b) {
                if (b == '\n') {
                    flushLine();
                } else if (b != '\r') {
                    line.append((char) b);
                }
            }

            @Override
            public synchronized void flush() {
                flushLine();
            }

            @Override
            public synchronized void close() {
                flushLine();
            }

            private void flushLine() {
                if (line.length() > 0) {
                    output.append(line + "\n");
                    line.setLength(0);
                }
            }
        };
    }
}
