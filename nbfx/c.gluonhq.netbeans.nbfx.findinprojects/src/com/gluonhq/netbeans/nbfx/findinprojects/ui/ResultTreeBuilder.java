package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.findinprojects.model.FileResult;
import com.gluonhq.netbeans.nbfx.findinprojects.model.TextMatch;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Detail;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.File;
import com.gluonhq.netbeans.nbfx.findinprojects.ui.ResultRow.Folder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.control.TreeItem;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Arranges a query's results under the summary row, in one of the two shapes of NetBeans'
 * results outline: a <b>tree</b> of scope roots, folders, files and occurrences, or a <b>flat</b>
 * list of files with their occurrences. The results are taken in walk order (folders then files,
 * by name) and every item remembers its place in it, so the table can go back to that order when
 * no column sorts it.
 */
final class ResultTreeBuilder {

    /** The shape of the results. */
    enum Flavour {
        /** scope root → folders → files → occurrences (NetBeans' "directory tree"). */
        TREE,
        /** files → occurrences (NetBeans' "list of files"). */
        FLAT
    }

    /** A tree item that knows its place in the walk order. */
    static final class Item extends TreeItem<ResultRow> {

        private final int sequence;

        Item(ResultRow row, int sequence) {
            super(row);
            this.sequence = sequence;
        }

        /** The position of this item in the walk order among its siblings. */
        int sequence() {
            return sequence;
        }
    }

    private final Function<FileObject, FileObject> rootOf;
    private final Function<FileObject, String> rootNameOf;

    /**
     * @param rootOf     the scope root a matching file is grouped under (a project root, a browsed
     *                   folder; the file's parent when it lies under no root), never {@code null}
     * @param rootNameOf the text a root's row shows: the project name, or the folder's path or name
     */
    ResultTreeBuilder(Function<FileObject, FileObject> rootOf, Function<FileObject, String> rootNameOf) {
        this.rootOf = Objects.requireNonNull(rootOf);
        this.rootNameOf = Objects.requireNonNull(rootNameOf);
    }

    /**
     * The items to put under the summary row for {@code results}, leaving out the hidden files and
     * occurrences (a file whose occurrences are all hidden is left out too); every item expanded.
     */
    List<TreeItem<ResultRow>> build(List<FileResult> results, Flavour flavour,
            Set<FileResult> hiddenFiles, Set<Detail> hiddenDetails) {
        Group root = new Group(null);
        for (FileResult result : results) {
            if (hiddenFiles.contains(result)) {
                continue;
            }
            List<TextMatch> shown = new ArrayList<>(result.matches().size());
            for (TextMatch match : result.matches()) {
                if (!hiddenDetails.contains(new Detail(result, match))) {
                    shown.add(match);
                }
            }
            if (shown.isEmpty() && !result.matches().isEmpty()) {
                continue;
            }
            FileObject scopeRoot = rootOf.apply(result.file());
            Group group = flavour == Flavour.TREE ? folderOf(root, scopeRoot, result.file()) : root;
            group.files.add(new File(result, shown, scopeRoot));
        }
        return root.toItems();
    }

    /** The group of the folder holding {@code file}: the scope root, then one level per path segment. */
    private Group folderOf(Group root, FileObject scopeRoot, FileObject file) {
        if (scopeRoot == null) {
            return root;
        }
        Group group = root.child(scopeRoot, () -> new FolderSeed(scopeRoot, rootNameOf.apply(scopeRoot), true));
        FileObject parent = file.getParent();
        String relative = parent == null ? null : FileUtil.getRelativePath(scopeRoot, parent);
        if (relative == null || relative.isEmpty()) {
            return group;
        }
        FileObject folder = scopeRoot;
        for (String segment : relative.split("/")) {
            FileObject next = folder.getFileObject(segment);
            if (next == null) {
                break;
            }
            folder = next;
            FileObject current = folder;
            group = group.child(current, () -> new FolderSeed(current, current.getNameExt(), false));
        }
        return group;
    }

    /** What a folder row is built from, before its counts are known. */
    private record FolderSeed(FileObject folder, String name, boolean scopeRoot) {
    }

    /** A folder level under construction: its sub-folders by file, then its files, in arrival order. */
    private static final class Group {

        private final FolderSeed seed;
        private final Map<FileObject, Group> folders = new LinkedHashMap<>();
        private final List<File> files = new ArrayList<>();

        Group(FolderSeed seed) {
            this.seed = seed;
        }

        Group child(FileObject folder, Supplier<FolderSeed> seed) {
            return folders.computeIfAbsent(folder, f -> new Group(seed.get()));
        }

        int fileCount() {
            int count = files.size();
            for (Group child : folders.values()) {
                count += child.fileCount();
            }
            return count;
        }

        int matchCount() {
            int count = 0;
            for (File file : files) {
                count += file.matches().size();
            }
            for (Group child : folders.values()) {
                count += child.matchCount();
            }
            return count;
        }

        List<TreeItem<ResultRow>> toItems() {
            List<TreeItem<ResultRow>> items = new ArrayList<>();
            int sequence = 0;
            for (Group child : folders.values()) {
                Item item = new Item(new Folder(child.seed.folder(), child.seed.name(), child.seed.scopeRoot(),
                        child.fileCount(), child.matchCount()), sequence++);
                item.setExpanded(true);
                item.getChildren().setAll(child.toItems());
                items.add(item);
            }
            for (File file : files) {
                Item item = new Item(file, sequence++);
                item.setExpanded(true);
                int position = 0;
                for (TextMatch match : file.matches()) {
                    item.getChildren().add(new Item(new Detail(file.result(), match), position++));
                }
                items.add(item);
            }
            return items;
        }
    }
}
