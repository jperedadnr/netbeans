package com.gluonhq.netbeans.nbfx.api.breadcrumbs;

import org.openide.filesystems.FileObject;

import java.util.Objects;

/**
 * Immutable context snapshot that is passed to the breadcrumbs providers.
 *
 * @param fileObject file being edited
 * @param documentText complete document text
 * @param caretOffset caret offset from document start
 */
public record BreadcrumbsContext(FileObject fileObject, String documentText, int caretOffset) {

    public BreadcrumbsContext {
        Objects.requireNonNull(fileObject, "fileObject must not be null");
        Objects.requireNonNull(documentText, "documentText must not be null");
        if (caretOffset < 0 || caretOffset > documentText.length()) {
            throw new IllegalArgumentException("caretOffset out of bounds: " + caretOffset);
        }
    }
}
