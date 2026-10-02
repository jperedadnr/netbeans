package com.gluonhq.netbeans.nbfx.findinprojects.model;

import java.util.List;
import java.util.Objects;
import org.openide.filesystems.FileObject;

/**
 * A file that matched a search - NetBeans' {@code MatchingObject}: the file, its size and
 * modification time when it was searched, and its matches (none for a file-name-only search).
 *
 * @param file         the matching file
 * @param size         the file's size in bytes
 * @param lastModified the file's modification time, in milliseconds since the epoch
 * @param matches      the occurrences, in text order; empty when only the name was matched
 */
public record FileResult(FileObject file, long size, long lastModified, List<TextMatch> matches) {

    public FileResult {
        Objects.requireNonNull(file);
        matches = List.copyOf(matches);
    }

    public int matchCount() {
        return matches.size();
    }
}
