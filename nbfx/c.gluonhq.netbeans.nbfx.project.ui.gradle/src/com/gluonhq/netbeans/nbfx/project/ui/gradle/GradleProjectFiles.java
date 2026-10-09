package com.gluonhq.netbeans.nbfx.project.ui.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 * Minimal read/write access to {@code gradle.properties}, preserving unrelated lines.
 *
 * @since 1.0
 */
final class GradleProjectFiles {

    private GradleProjectFiles() {
    }

    /** The value of {@code key} in {@code gradle.properties}, or {@code null}. */
    static String property(FileObject dir, String key) {
        FileObject file = dir.getFileObject("gradle.properties");
        if (file == null || !file.isData()) {
            return null;
        }
        for (String line : lines(file)) {
            int eq = line.indexOf('=');
            if (eq > 0 && line.substring(0, eq).trim().equals(key)) {
                return line.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /** Sets {@code key} in {@code gradle.properties}, creating the file when absent. */
    static void setProperty(FileObject dir, String key, String value) {
        FileObject file = dir.getFileObject("gradle.properties");
        try {
            if (file == null) {
                file = dir.createData("gradle.properties");
            }
            List<String> lines = lines(file);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                int eq = lines.get(i).indexOf('=');
                if (eq > 0 && lines.get(i).substring(0, eq).trim().equals(key)) {
                    lines.set(i, key + "=" + value);
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add(key + "=" + value);
            }
            write(file, lines);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private static List<String> lines(FileObject file) {
        List<String> result = new ArrayList<>();
        try (InputStream in = file.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return result;
    }

    private static void write(FileObject file, List<String> lines) {
        FileLock lock = null;
        try {
            lock = file.lock();
            try (OutputStream out = file.getOutputStream(lock)) {
                out.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            if (lock != null) {
                lock.releaseLock();
            }
        }
    }
}
