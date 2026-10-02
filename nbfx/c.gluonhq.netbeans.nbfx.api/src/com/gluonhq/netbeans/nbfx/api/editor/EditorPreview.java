package com.gluonhq.netbeans.nbfx.api.editor;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * A read-only preview of a file with a range revealed - the right half of the Usages and Search
 * result views. One {@linkplain EditorService#createPreviewDocument preview document} per file,
 * replaced when the range moves to another file, kept while nothing is asked for, and released
 * by {@link #clear()} when the pane is hidden or disposed. The revealed range is marked and
 * centred, the caret untouched. The document is a view of the file's shared buffer, so it shows
 * unsaved edits of open editors live.
 * <p>
 * Style class {@code editor-preview}; the placeholder label shows until a range is revealed.
 */
public final class EditorPreview extends StackPane {

    private static final Logger LOG = Logger.getLogger(EditorPreview.class.getName());

    private record Range(FileObject file, int start, int end) {
    }

    private final Label placeholder;
    private EditorDocument document;
    private FileObject file;
    private Range shown;
    private Range pending;

    /** @param placeholderText the text shown while nothing is previewed */
    public EditorPreview(String placeholderText) {
        placeholder = new Label(Objects.requireNonNull(placeholderText));
        getStyleClass().add("editor-preview");
        getChildren().add(placeholder);
        setMinWidth(0);
    }

    /**
     * Reveals {@code [start, end)} of {@code file}. While the pane is not in a scene the request
     * is kept for {@link #restore()}; a range already shown is left as it is.
     */
    public void show(FileObject file, int start, int end) {
        Objects.requireNonNull(file);
        Range range = new Range(file, start, end);
        pending = range;
        if (getScene() == null) {
            return;
        }
        if (range.equals(shown) && document != null) {
            return;
        }
        if (document == null || !Objects.equals(this.file, file)) {
            clear();
            EditorService editors = Lookup.getDefault().lookup(EditorService.class);
            if (editors == null) {
                return;
            }
            try {
                document = editors.createPreviewDocument(file);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not preview " + file.getPath(), ex);
                return;
            }
            this.file = file;
            getChildren().setAll(document.getNode());
        }
        document.revealRange(start, end);
        shown = range;
    }

    /** Reveals again the range last asked for, once the pane is attached to a scene. */
    public void restore() {
        if (pending != null) {
            show(pending.file(), pending.start(), pending.end());
        }
    }

    /** Releases the preview document and shows the placeholder; the last request stays pending. */
    public void clear() {
        if (document != null) {
            document.dispose();
            document = null;
            file = null;
            shown = null;
        }
        getChildren().setAll(placeholder);
    }

    /** The document shown, or {@code null} while the placeholder is. */
    public EditorDocument getDocument() {
        return document;
    }
}
