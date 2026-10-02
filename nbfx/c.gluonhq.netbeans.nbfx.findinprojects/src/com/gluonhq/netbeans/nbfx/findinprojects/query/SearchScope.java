package com.gluonhq.netbeans.nbfx.findinprojects.query;

import com.gluonhq.netbeans.nbfx.api.ContentManager;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Where a search looks - NetBeans' {@code SearchScopeDefinition}s. A scope is plain data: its
 * {@link #roots()} are resolved when it is created (on the JavaFX thread, as the open-files scope
 * asks the UI), so a query holds a fixed set and the model describes the scope again for a rescan.
 * {@link #id()} is the persisted "scope type id"; {@link #label()} names the scope in the dialog
 * and in the results header.
 */
public sealed interface SearchScope {

    String ID_OPEN_PROJECTS = "open projects";
    String ID_CURRENT_PROJECT = "current project";
    String ID_OPEN_FILES = "open files";
    String ID_FOLDERS = "node selection";
    String ID_BROWSE = "browse";

    /** The persisted identity of this kind of scope. */
    String id();

    /** The text shown for this scope. */
    String label();

    /** The folders (or files) searched; folders are walked recursively. */
    List<FileObject> roots();

    /**
     * This scope described again for a rescan - the projects or files open <em>now</em>. Scopes
     * with fixed roots return themselves. Call on the JavaFX thread, as the open-files scope asks
     * the UI.
     */
    default SearchScope refreshed() {
        return this;
    }

    /** Every open project's root. */
    record OpenProjects(List<FileObject> roots) implements SearchScope {

        public OpenProjects {
            roots = List.copyOf(roots);
        }

        /** The open projects of the registry in the Lookup - an empty scope without one. */
        public static OpenProjects current() {
            ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
            List<FileObject> roots = new ArrayList<>();
            if (registry != null) {
                for (OpenProject project : registry.getOpenProjects()) {
                    roots.add(project.getRoot());
                }
            }
            return new OpenProjects(roots);
        }

        @Override
        public String id() {
            return ID_OPEN_PROJECTS;
        }

        @Override
        public SearchScope refreshed() {
            return current();
        }

        @Override
        public String label() {
            return message("SCOPE_OpenProjects");
        }
    }

    /** The selected project. */
    record CurrentProject(OpenProject project) implements SearchScope {

        public CurrentProject {
            Objects.requireNonNull(project);
        }

        /** The selected project of the registry in the Lookup, or {@code null} when there is none. */
        public static CurrentProject current() {
            ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
            OpenProject selected = registry == null ? null : registry.getSelected();
            return selected == null ? null : new CurrentProject(selected);
        }

        @Override
        public String id() {
            return ID_CURRENT_PROJECT;
        }

        // refreshed(): the project stays the one the search was started for, as NetBeans' dialog fixes it.

        @Override
        public String label() {
            return message("SCOPE_CurrentProject", project.getDisplayName());
        }

        @Override
        public List<FileObject> roots() {
            return List.of(project.getRoot());
        }
    }

    /** The files open in editors. */
    record OpenFiles(List<FileObject> roots) implements SearchScope {

        public OpenFiles {
            roots = List.copyOf(roots);
        }

        /** The documents the {@link ContentManager} in the Lookup has open, in pane and tab order. */
        public static OpenFiles current() {
            Set<FileObject> files = new LinkedHashSet<>();
            ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
            ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
            if (contentManager != null) {
                List<String> projectPaths = new ArrayList<>();
                if (registry != null) {
                    for (OpenProject project : registry.getOpenProjects()) {
                        projectPaths.add(project.getPath());
                    }
                }
                projectPaths.add(null);
                for (String path : projectPaths) {
                    for (EditorDocument document : contentManager.documentsOf(path)) {
                        if (document.getFileObject() != null) {
                            files.add(document.getFileObject());
                        }
                    }
                }
            }
            return new OpenFiles(new ArrayList<>(files));
        }

        @Override
        public String id() {
            return ID_OPEN_FILES;
        }

        @Override
        public SearchScope refreshed() {
            return current();
        }

        @Override
        public String label() {
            return message("SCOPE_OpenFiles");
        }
    }

    /** The packages / folders selected in the Projects or Files view. */
    record Folders(List<FileObject> roots) implements SearchScope {

        public Folders {
            roots = List.copyOf(roots);
        }

        @Override
        public String id() {
            return ID_FOLDERS;
        }

        @Override
        public String label() {
            return roots.size() == 1
                    ? message("SCOPE_Folder", roots.get(0).getNameExt())
                    : message("SCOPE_Folders", roots.size());
        }
    }

    /** Folders chosen with a directory chooser. */
    record Browse(List<FileObject> roots) implements SearchScope {

        public Browse {
            roots = List.copyOf(roots);
        }

        @Override
        public String id() {
            return ID_BROWSE;
        }

        @Override
        public String label() {
            return roots.size() == 1
                    ? roots.get(0).getPath()
                    : message("SCOPE_Folders", roots.size());
        }
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(SearchScope.class, key, args);
    }
}
