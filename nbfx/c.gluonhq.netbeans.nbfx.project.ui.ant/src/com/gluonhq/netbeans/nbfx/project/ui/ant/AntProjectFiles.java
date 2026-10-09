package com.gluonhq.netbeans.nbfx.project.ui.ant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 * Minimal read/write access to the files an Ant / NetBeans-module project's customizers edit:
 * {@code nbproject/project.properties} and {@code manifest.mf}. Line-preserving, so unrelated
 * comments and ordering survive a round-trip.
 *
 * @since 1.0
 */
final class AntProjectFiles {

    private AntProjectFiles() {
    }

    /** The value of {@code key} in {@code nbproject/project.properties}, or {@code null}. */
    static String property(FileObject dir, String key) {
        FileObject file = dir.getFileObject("nbproject/project.properties");
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

    /** Sets {@code key} in {@code nbproject/project.properties}, replacing or appending the line. */
    static void setProperty(FileObject dir, String key, String value) {
        FileObject file = dir.getFileObject("nbproject/project.properties");
        if (file == null || !file.isData()) {
            return;
        }
        List<String> lines = lines(file);
        if (!setEntry(lines, key, value, '=')) {
            lines.add(key + "=" + value);
        }
        write(file, lines);
    }

    /** The value of {@code key} in {@code manifest.mf}, or {@code null}. */
    static String manifestEntry(FileObject dir, String key) {
        FileObject file = dir.getFileObject("manifest.mf");
        if (file == null || !file.isData()) {
            return null;
        }
        for (String line : lines(file)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equals(key)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** Sets {@code key} in {@code manifest.mf}, replacing or appending the line. */
    static void setManifestEntry(FileObject dir, String key, String value) {
        FileObject file = dir.getFileObject("manifest.mf");
        if (file == null || !file.isData()) {
            return;
        }
        List<String> lines = lines(file);
        if (!setEntry(lines, key, value, ':')) {
            lines.add(key + ": " + value);
        }
        write(file, lines);
    }

    /** The public packages declared in {@code nbproject/project.xml}, in order. */
    static List<String> publicPackages(FileObject dir) {
        List<String> result = new ArrayList<>();
        FileObject file = dir.getFileObject("nbproject/project.xml");
        if (file == null || !file.isData()) {
            return result;
        }
        String xml = text(file);
        int start = xml.indexOf("<public-packages>");
        int end = xml.indexOf("</public-packages>");
        if (start < 0 || end < 0) {
            return result;
        }
        Matcher matcher = Pattern.compile("<subpackages>([^<]*)</subpackages>")
                .matcher(xml.substring(start, end));
        while (matcher.find()) {
            result.add(matcher.group(1).trim());
        }
        return result;
    }

    /** Replaces the public packages in {@code nbproject/project.xml}. */
    static void setPublicPackages(FileObject dir, List<String> packages) {
        FileObject file = dir.getFileObject("nbproject/project.xml");
        if (file == null || !file.isData()) {
            return;
        }
        String xml = text(file);
        String indent = "                ";
        StringBuilder block = new StringBuilder("<public-packages>");
        for (String pkg : packages) {
            block.append("\n").append(indent).append("    <subpackages>").append(pkg).append("</subpackages>");
        }
        block.append("\n").append(indent).append("</public-packages>");
        int start = xml.indexOf("<public-packages>");
        int end = xml.indexOf("</public-packages>");
        String updated;
        if (start >= 0 && end >= 0) {
            updated = xml.substring(0, start) + block + xml.substring(end + "</public-packages>".length());
        } else {
            int closeData = xml.indexOf("</data>");
            updated = xml.substring(0, closeData) + indent + block + "\n" + xml.substring(closeData);
        }
        write(file, List.of(updated.split("\n", -1)));
    }

    private static String text(FileObject file) {
        StringBuilder builder = new StringBuilder();
        try (InputStream in = file.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return builder.toString();
    }

    /** The project type declared in {@code nbproject/project.xml}, or {@code null}. */
    static String projectType(FileObject dir) {
        FileObject file = dir.getFileObject("nbproject/project.xml");
        if (file == null || !file.isData()) {
            return null;
        }
        Matcher matcher = Pattern.compile("<type>([^<]*)</type>").matcher(text(file));
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static boolean setEntry(List<String> lines, String key, String value, char separator) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int index = line.indexOf(separator);
            if (index > 0 && line.substring(0, index).trim().equals(key)) {
                lines.set(i, separator == '=' ? key + "=" + value : key + ": " + value);
                return true;
            }
        }
        return false;
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
