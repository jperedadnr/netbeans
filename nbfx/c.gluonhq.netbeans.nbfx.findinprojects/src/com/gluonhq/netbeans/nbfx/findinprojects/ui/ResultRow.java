package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.model.FileResult;
import com.gluonhq.netbeans.nbfx.findinprojects.model.TextMatch;
import java.util.List;
import java.util.Objects;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * One row of the search results table: the summary at the root, a folder of the directory tree, a
 * matching file, or one occurrence in it. Rows are plain values; the cells in {@link ResultCells}
 * draw them and the {@link ResultTreeBuilder} arranges them.
 */
sealed interface ResultRow permits ResultRow.Summary, ResultRow.Folder, ResultRow.File, ResultRow.Detail {

    /** The order of the kinds at one level of the tree: folders before files before details. */
    int rank();

    /** Identifies the row across rebuilds of the tree, to keep its expansion and selection. */
    String key();

    /** The file the row stands for, or {@code null} for the summary. */
    FileObject file();

    /**
     * The root row: what was found so far, with the search text emphasised.
     *
     * @param prefix      the text before the emphasised part
     * @param emphasis    the search text, or {@code null} when nothing is emphasised
     * @param suffix      the text after it, possibly empty
     * @param replacement the replacement of a replace search, emphasised in a sentence of its own
     *                    after the suffix; {@code null} for a plain search
     * @param issues      the number of warnings and errors to link to, 0 for none
     */
    record Summary(String prefix, String emphasis, String suffix, String replacement, int issues) implements ResultRow {

        public Summary {
            Objects.requireNonNull(prefix);
            Objects.requireNonNull(suffix);
        }

        /** A plain search's summary. */
        public Summary(String prefix, String emphasis, String suffix, int issues) {
            this(prefix, emphasis, suffix, null, issues);
        }

        /** The text before the replacement: "To be replaced with ", empty without one. */
        public String replacementPrefix() {
            return replacement == null ? "" : " " + NbBundle.getMessage(ResultRow.class, "SUMMARY_ReplaceWith") + " ";
        }

        /** The whole sentence, as plain text. */
        public String text() {
            return prefix + (emphasis == null ? "" : emphasis) + suffix
                    + (replacement == null ? "" : replacementPrefix() + replacement + ".");
        }

        @Override
        public int rank() {
            return 0;
        }

        @Override
        public String key() {
            return "summary";
        }

        @Override
        public FileObject file() {
            return null;
        }
    }

    /**
     * A folder of the directory-tree view: a scope root (a project, a browsed folder) or one
     * segment of the path from the root to the matching files.
     *
     * @param folder    the folder
     * @param name      the text shown: the project name for a root, else the folder name
     * @param scopeRoot whether this is a root of the search scope (drawn with the project icon)
     * @param files     the matching files under it, after the hidden ones are removed
     * @param matches   the occurrences under it, likewise
     */
    record Folder(FileObject folder, String name, boolean scopeRoot, int files, int matches) implements ResultRow {

        public Folder {
            Objects.requireNonNull(folder);
            Objects.requireNonNull(name);
        }

        @Override
        public int rank() {
            return 1;
        }

        @Override
        public String key() {
            return "d:" + folder.getPath();
        }

        @Override
        public FileObject file() {
            return folder;
        }
    }

    /**
     * A matching file.
     *
     * @param result    the result as found
     * @param matches   its occurrences still shown (empty for a file-name search)
     * @param scopeRoot the scope root the file was found under, for the relative path; {@code null}
     *                  when it lies outside every root (an open file, an archive entry)
     */
    record File(FileResult result, List<TextMatch> matches, FileObject scopeRoot) implements ResultRow {

        public File {
            Objects.requireNonNull(result);
            matches = List.copyOf(matches);
        }

        public FileObject file() {
            return result.file();
        }

        public String name() {
            return result.file().getNameExt();
        }

        /** The folder of the file relative to the scope root, {@code ""} at the root, else its full path. */
        public String relativeFolder() {
            FileObject parent = result.file().getParent();
            if (parent == null) {
                return "";
            }
            String relative = scopeRoot == null ? null : FileUtil.getRelativePath(scopeRoot, parent);
            return relative != null ? relative : parent.getPath();
        }

        @Override
        public int rank() {
            return 2;
        }

        @Override
        public String key() {
            return "f:" + result.file().getPath();
        }
    }

    /**
     * One occurrence in a file.
     *
     * @param result the file it was found in
     * @param match  the occurrence
     */
    record Detail(FileResult result, TextMatch match) implements ResultRow {

        public Detail {
            Objects.requireNonNull(result);
            Objects.requireNonNull(match);
        }

        public FileObject file() {
            return result.file();
        }

        @Override
        public int rank() {
            return 3;
        }

        @Override
        public String key() {
            return "m:" + result.file().getPath() + ":" + match.start();
        }
    }
}
