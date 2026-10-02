package com.gluonhq.netbeans.nbfx.findinprojects.query;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.queries.SharabilityQuery;
import org.netbeans.api.queries.SharabilityQuery.Sharability;
import org.netbeans.api.queries.VisibilityQuery;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Lists the files of a {@link SearchScope}, applying NetBeans' default search filters
 * ({@code SearchInfoDefinitionFactory.DEFAULT_FILTERS}): the {@link VisibilityQuery} (hidden
 * files and folders) and, unless {@link ScopeOptions#searchInGenerated()} drops it as
 * {@code FilterHelper} does, the {@link SharabilityQuery} (a {@code NOT_SHARABLE} folder - the
 * build output - is not traversed). The {@link IgnoreList} applies when
 * {@link ScopeOptions#useIgnoreList()}; archives are entered when
 * {@link ScopeOptions#searchInArchives()}.
 * <p>
 * Folders are visited depth first, sub-folders before files, each level sorted by name, so a
 * rescan yields the same order. Symbolic-link loops are cut on the canonical path.
 */
public final class FileWalker {

    private static final Logger LOG = Logger.getLogger(FileWalker.class.getName());

    private final ScopeOptions options;
    private final IgnoreList ignoreList;
    private final Set<String> visited = new HashSet<>();

    public FileWalker(ScopeOptions options, IgnoreList ignoreList) {
        this.options = Objects.requireNonNull(options);
        this.ignoreList = Objects.requireNonNull(ignoreList);
    }

    /** Every file of {@code scope}, in walk order; stops early when {@code cancellation} says so. */
    public List<FileObject> collect(SearchScope scope, Cancellation cancellation) {
        List<FileObject> files = new ArrayList<>();
        walk(scope, cancellation, files::add);
        return files;
    }

    /** Hands every file of {@code scope} to {@code sink}, in walk order. */
    public void walk(SearchScope scope, Cancellation cancellation, Consumer<FileObject> sink) {
        visited.clear();
        for (FileObject root : scope.roots()) {
            if (cancellation.isCancelled()) {
                return;
            }
            if (root.isFolder()) {
                // The root itself is never filtered: the user chose it.
                walkFolder(root, sharabilityMatters(root), cancellation, sink);
            } else if (root.isData() && accepts(root)) {
                visitFile(root, cancellation, sink);
            }
        }
    }

    private void walkFolder(FileObject folder, boolean checkSharability, Cancellation cancellation,
            Consumer<FileObject> sink) {
        if (!markVisited(folder)) {
            return;
        }
        FileObject[] children = folder.getChildren();
        Arrays.sort(children, Comparator.comparing(FileObject::getNameExt, String.CASE_INSENSITIVE_ORDER));
        for (FileObject child : children) {
            if (cancellation.isCancelled()) {
                return;
            }
            if (!child.isFolder()) {
                continue;
            }
            if (!VisibilityQuery.getDefault().isVisible(child)) {
                continue;
            }
            if (options.useIgnoreList() && ignoreList.matches(child)) {
                continue;
            }
            boolean checkBelow = checkSharability;
            if (checkSharability) {
                switch (sharabilityOf(child)) {
                    case NOT_SHARABLE -> {
                        continue;
                    }
                    // Everything below a sharable folder is sharable: no need to ask again.
                    case SHARABLE -> checkBelow = false;
                    default -> {
                    }
                }
            }
            walkFolder(child, checkBelow, cancellation, sink);
        }
        for (FileObject child : children) {
            if (cancellation.isCancelled()) {
                return;
            }
            if (child.isFolder() || !accepts(child)) {
                continue;
            }
            if (checkSharability && sharabilityOf(child) == Sharability.NOT_SHARABLE) {
                continue;
            }
            visitFile(child, cancellation, sink);
        }
    }

    private void visitFile(FileObject file, Cancellation cancellation, Consumer<FileObject> sink) {
        if (options.searchInArchives() && FileUtil.isArchiveFile(file)) {
            FileObject archiveRoot = FileUtil.getArchiveRoot(file);
            if (archiveRoot != null) {
                // Archive entries are neither visible-filtered nor sharability-filtered in NetBeans.
                walkFolder(archiveRoot, false, cancellation, sink);
                return;
            }
        }
        sink.accept(file);
    }

    /** The visibility and ignore-list filters for a file. */
    private boolean accepts(FileObject file) {
        if (!VisibilityQuery.getDefault().isVisible(file)) {
            return false;
        }
        return !options.useIgnoreList() || !ignoreList.matches(file);
    }

    private boolean sharabilityMatters(FileObject root) {
        // Archive entries and other non-disk file objects have no sharability.
        return !options.searchInGenerated() && FileUtil.toFile(root) != null;
    }

    /**
     * The sharability of {@code fo} as the owning project's {@code SharabilityQueryImplementation}
     * answers it (Maven: the POM's build directory is {@code NOT_SHARABLE}). When no project type
     * answers ({@code UNKNOWN}), a Maven layout is recognised by hand: a {@code target} folder next
     * to a {@code pom.xml}.
     * <p>
     * TODO: other project types (Gradle {@code build/}, Ant {@code build/} / {@code dist/}) once
     * NetBeansFX opens them.
     */
    static Sharability sharabilityOf(FileObject fo) {
        Sharability sharability;
        try {
            sharability = SharabilityQuery.getSharability(fo);
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "SharabilityQuery failed for " + fo.getPath(), ex);
            sharability = Sharability.UNKNOWN;
        }
        if (sharability == Sharability.UNKNOWN && fo.isFolder() && "target".equals(fo.getNameExt())) {
            FileObject parent = fo.getParent();
            if (parent != null && parent.getFileObject("pom.xml") != null) {
                return Sharability.NOT_SHARABLE;
            }
        }
        return sharability;
    }

    private boolean markVisited(FileObject folder) {
        File file = FileUtil.toFile(folder);
        String key;
        if (file == null) {
            key = folder.toURI().toString();
        } else {
            try {
                key = file.getCanonicalPath();
            } catch (IOException ex) {
                key = file.getAbsolutePath();
            }
        }
        return visited.add(key);
    }
}
