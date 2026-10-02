package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchCriteria;
import com.gluonhq.netbeans.nbfx.findinprojects.query.SearchScope;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.ListChangeListener;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Runs last session's searches again. The projects of the session open in the background after
 * the window layout is restored, so a search waits for the projects its scope needs: an
 * open-projects search until every project it covered is open again (the ones that are gone are
 * not waited for), a current-project search until its project is open. A search over folders,
 * browsed directories or open files runs at once over those of its roots that still exist; a
 * search with no root left is dropped. Call on the JavaFX thread.
 */
final class SearchRestorer {

    /** Starts a restored search: {@code criteria} is {@code saved} over its scope described again. */
    interface Starter {
        void start(SavedSearch saved, SearchCriteria criteria);
    }

    private static final Logger LOG = Logger.getLogger(SearchRestorer.class.getName());

    private final ProjectRegistry registry;
    private final Starter starter;
    private final Deque<SavedSearch> pending;
    private final ListChangeListener<OpenProject> onProjectsChanged = change -> attempt();

    private SearchRestorer(List<SavedSearch> saved, ProjectRegistry registry, Starter starter) {
        this.pending = new ArrayDeque<>(saved);
        this.registry = registry;
        this.starter = starter;
    }

    /**
     * Starts {@code saved}, in order, each as soon as its scope can be described; without a
     * {@code registry} the project scopes use the roots they were saved with.
     */
    static void restore(List<SavedSearch> saved, ProjectRegistry registry, Starter starter) {
        if (saved.isEmpty()) {
            return;
        }
        SearchRestorer restorer = new SearchRestorer(saved, registry, starter);
        if (registry != null) {
            registry.getOpenProjects().addListener(restorer.onProjectsChanged);
        }
        restorer.attempt();
    }

    private void attempt() {
        for (Iterator<SavedSearch> it = pending.iterator(); it.hasNext();) {
            SavedSearch saved = it.next();
            Resolution resolution = resolve(saved);
            if (resolution instanceof Resolution.Dropped(String reason)) {
                LOG.log(Level.FINE, "Dropping a saved search: {0}", reason);
                it.remove();
            } else if (resolution instanceof Resolution.Ready(SearchScope scope)) {
                it.remove();
                starter.start(saved, saved.criteria(scope));
            }
        }
        if (pending.isEmpty() && registry != null) {
            registry.getOpenProjects().removeListener(onProjectsChanged);
        }
    }

    private sealed interface Resolution {
        record Ready(SearchScope scope) implements Resolution { }
        record Waiting() implements Resolution { }
        record Dropped(String reason) implements Resolution { }
    }

    private Resolution resolve(SavedSearch saved) {
        List<FileObject> existing = existing(saved.roots());
        return switch (saved.scopeId()) {
            case SearchScope.ID_FOLDERS -> existing.isEmpty()
                    ? new Resolution.Dropped("none of its folders exists: " + saved.roots())
                    : new Resolution.Ready(new SearchScope.Folders(existing));
            case SearchScope.ID_BROWSE -> existing.isEmpty()
                    ? new Resolution.Dropped("none of its directories exists: " + saved.roots())
                    : new Resolution.Ready(new SearchScope.Browse(existing));
            case SearchScope.ID_OPEN_FILES -> existing.isEmpty()
                    ? new Resolution.Dropped("none of its files exists: " + saved.roots())
                    : new Resolution.Ready(new SearchScope.OpenFiles(existing));
            case SearchScope.ID_CURRENT_PROJECT -> currentProject(saved, existing);
            case SearchScope.ID_OPEN_PROJECTS -> openProjects(saved, existing);
            default -> new Resolution.Dropped("unknown scope " + saved.scopeId());
        };
    }

    private Resolution currentProject(SavedSearch saved, List<FileObject> existing) {
        if (existing.isEmpty()) {
            return new Resolution.Dropped("its project no longer exists: " + saved.roots());
        }
        if (registry == null) {
            return new Resolution.Dropped("no project registry for " + saved.roots());
        }
        OpenProject project = registry.find(OpenProject.pathOf(existing.get(0)));
        return project == null ? new Resolution.Waiting() : new Resolution.Ready(new SearchScope.CurrentProject(project));
    }

    private Resolution openProjects(SavedSearch saved, List<FileObject> existing) {
        if (registry == null) {
            return new Resolution.Ready(new SearchScope.OpenProjects(existing));
        }
        if (existing.isEmpty()) {
            // every project it covered is gone: search the ones that open, once one has
            return registry.getOpenProjects().isEmpty() ? new Resolution.Waiting() : new Resolution.Ready(openProjects());
        }
        for (FileObject root : existing) {
            if (registry.find(OpenProject.pathOf(root)) == null) {
                return new Resolution.Waiting();
            }
        }
        return new Resolution.Ready(openProjects());
    }

    /** The projects open in the registry now; a rescan describes them again through the Lookup. */
    private SearchScope openProjects() {
        List<FileObject> roots = new ArrayList<>();
        for (OpenProject project : registry.getOpenProjects()) {
            roots.add(project.getRoot());
        }
        return new SearchScope.OpenProjects(roots);
    }

    /** The file objects of those of {@code paths} that still exist, in order. */
    private static List<FileObject> existing(List<String> paths) {
        List<FileObject> files = new ArrayList<>();
        for (String path : paths) {
            FileObject file = FileUtil.toFileObject(FileUtil.normalizeFile(new File(path)));
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }
}
