package com.gluonhq.netbeans.nbfx.editor.codearea;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import jfx.incubator.scene.control.richtext.LineEnding;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;
import org.openide.filesystems.FileObject;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the line separator of the file behind a {@link StyledTextModel}: detects it on load (caching
 * it in the {@link #ATTR} file attribute so the file is only scanned once), exposes it as an
 * observable property, applies user changes to the model, and tracks the saved baseline so the
 * editor's dirty state can account for separator changes.
 */
final class LineSeparatorSupport {

    private static final Logger LOG = Logger.getLogger(LineSeparatorSupport.class.getName());

    /** The file attribute that caches the detected line separator. */
    static final String ATTR = "line.separator";

    private final FileObject fileObject;
    private final StyledTextModel model;

    /**
     * The line separator of the file: detected (or read from its attribute) on {@link #load},
     * and changeable by the user via {@link #set}.
     */
    private final ReadOnlyObjectWrapper<String> lineSeparator =
            new ReadOnlyObjectWrapper<>(this, "lineSeparator", null);

    /**
     * The line separator as of the last load or successful save, part of the editor's dirty
     * baseline.
     */
    private String saved;

    LineSeparatorSupport(FileObject fileObject, StyledTextModel model) {
        this.fileObject = fileObject;
        this.model = model;
    }

    ObservableValue<String> property() {
        return lineSeparator.getReadOnlyProperty();
    }

    /**
     * Detects the line separator of the given file content (reading it from the cached
     * {@link #ATTR} file attribute when present, otherwise scanning the content and defaulting
     * to the platform separator), applies it to the model, and baselines it. Must be called
     * before the content is set, so the model joins paragraphs with the right separator.
     */
    void load(String text) {
        String separator;
        if (fileObject.getAttribute(ATTR) instanceof String cached && !cached.isEmpty()) {
            separator = cached;
        } else {
            String detected = scan(text);
            separator = detected != null ? detected : System.lineSeparator();
            store(fileObject, separator);
        }
        model.setLineEnding(lineEndingOf(separator));
        saved = separator;
        lineSeparator.set(separator);
    }

    /**
     * Changes the line separator the editor content is joined and saved with. Unknown separators
     * are ignored.
     *
     * @param separator the new line separator: {@code "\n"}, {@code "\r"} or {@code "\r\n"}
     * @return {@code true} when the separator was applied (known and different from the current one)
     */
    boolean set(String separator) {
        boolean known = "\n".equals(separator) || "\r".equals(separator) || "\r\n".equals(separator);
        if (!known || separator.equals(lineSeparator.get())) {
            return false;
        }
        model.setLineEnding(lineEndingOf(separator));
        lineSeparator.set(separator);
        return true;
    }

    /** Whether the current separator differs from the saved baseline (a persistable change). */
    boolean isDirty() {
        return !Objects.equals(lineSeparator.get(), saved);
    }

    /** Persists the current separator in the {@link #ATTR} attribute and re-baselines it. */
    void markSaved() {
        String separator = lineSeparator.get();
        store(fileObject, separator);
        saved = separator;
    }

    /** Re-baselines to the separator read from disk after an external reload (a clean state). */
    void resetTo(String separator) {
        saved = separator;
        lineSeparator.set(separator);
    }

    /** Returns the first line separator found in {@code text}, or {@code null} if it has none. */
    static String scan(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                return "\n";
            }
            if (c == '\r') {
                return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? "\r\n" : "\r";
            }
        }
        return null;
    }

    /** Maps a line separator string to the corresponding {@link LineEnding}. */
    static LineEnding lineEndingOf(String separator) {
        return switch (separator) {
            case "\r" -> LineEnding.CR;
            case "\r\n" -> LineEnding.CRLF;
            default -> LineEnding.LF;
        };
    }

    /** Stores {@code separator} in the {@link #ATTR} attribute when it changed, logging failures. */
    static void store(FileObject fileObject, String separator) {
        try {
            if (!Objects.equals(fileObject.getAttribute(ATTR), separator)) {
                fileObject.setAttribute(ATTR, separator);
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not store the line separator of " + fileObject.getNameExt(), ex);
        }
    }
}
