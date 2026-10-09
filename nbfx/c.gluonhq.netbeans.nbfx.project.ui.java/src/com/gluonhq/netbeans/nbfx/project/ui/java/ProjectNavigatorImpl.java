package com.gluonhq.netbeans.nbfx.project.ui.java;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.NavigatorProvider;
import com.gluonhq.netbeans.nbfx.api.project.ProjectOpener;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectDirectory;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectFile;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKindProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKinds;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectLibrary;
import com.gluonhq.netbeans.nbfx.project.ui.java.utils.DeleteActions;
import com.gluonhq.netbeans.nbfx.project.ui.java.utils.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.project.ui.java.utils.PackageScanner;
import com.gluonhq.netbeans.nbfx.project.ui.utils.Projects;
import com.gluonhq.netbeans.nbfx.project.ui.java.utils.TreeNav;
import com.gluonhq.netbeans.nbfx.file.actions.FileDragAndDrop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import static org.netbeans.api.java.project.JavaProjectConstants.SOURCES_TYPE_JAVA;
import static org.netbeans.api.java.project.JavaProjectConstants.SOURCES_TYPE_RESOURCES;

@ServiceProvider(service = NavigatorProvider.class, position=10)
@FxViewRegistration(displayName = "Projects",
        location = FxViewLocation.LEFT, position = 10, navigator = true)
public class ProjectNavigatorImpl extends AbstractNavigatorProvider<ProjectEntry> {

    private static final Logger LOG = Logger.getLogger(ProjectNavigatorImpl.class.getName());

    /** The per-project state of the logical view: the project and its background loading. */
    private final class ProjectRootState extends RootState {

        // Written by the project's loading thread, read by the FX and lazy-node threads.
        private volatile Project project;
        private volatile ProjectKindProvider kind;
        private volatile Thread loadingThread;
        private volatile boolean loadingCancelled;
        /** Whether the focus ring should keep following this project's restored selection. */
        private volatile boolean restoringFocus;

        private ProjectRootState(FileObject root, Path path) {
            super(root, path);
        }

        void cancelLoading() {
            loadingCancelled = true;
            if (loadingThread != null) {
                loadingThread.interrupt();
            }
        }

        @Override
        protected void dispose() {
            cancelLoading();
            project = null;
            kind = null;
        }
    }

    private Consumer<FileObject> onProjectLoaded;
    private Consumer<FileObject> onProjectLoadFailed;

    private final RequestProcessor nodeLoader =
            new RequestProcessor("ProjectNavigator-lazy-nodes", 1, true, true);

    private final ReadOnlyObjectWrapper<FileObject> selectedFile =
            new ReadOnlyObjectWrapper<>(this, "selectedFile");
    private final ReadOnlyObjectWrapper<List<FileObject>> selectedFiles =
            new ReadOnlyObjectWrapper<>(this, "selectedFiles", List.of());

    public ProjectNavigatorImpl() {

    }

    @Override
    protected RootState createRootState(FileObject root, Path path) {
        return new ProjectRootState(root, path);
    }

    @Override
    protected TreeItem<ProjectEntry> findItem(FileObject file) {
        Path target = FileUtil.toPath(file);
        if (target == null) {
            return null;
        }
        RootState state = rootStateOf(target);
        TreeItem<ProjectEntry> item = state == null ? null : state.item();
        return item == null ? null : findUnder(item, target);
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(FileNavigatorImpl.class, "Navigator.title.projects");
    }

    @Override
    public String getProjectIconName(FileObject projectRoot) {
        RootState state = rootState(projectRoot);
        if (state == null || state.item() == null || state.item().getValue() == null) {
            return null;
        }
        return NavigatorIcons.toMenuIconName(state.item().getValue().getIconName());
    }

    @Override
    public Node getProjectIcon(FileObject projectRoot, String iconName) {
        if (projectRoot == null || iconName == null || iconName.isBlank()) {
            return null;
        }
        return NavigatorIcons.createIconView(projectRoot, iconName);
    }

    @Override
    public void setOnProjectLoaded(Consumer<FileObject> onProjectLoaded) {
        this.onProjectLoaded = onProjectLoaded;
    }

    @Override
    public void setOnProjectLoadFailed(Consumer<FileObject> onProjectLoadFailed) {
        this.onProjectLoadFailed = onProjectLoadFailed;
    }

    @Override
    public void cancelLoading() {
        for (RootState state : rootStates()) {
            ((ProjectRootState) state).cancelLoading();
        }
    }

    @Override
    public void cancelLoading(FileObject root) {
        RootState state = rootState(root);
        if (state != null) {
            ((ProjectRootState) state).cancelLoading();
        }
    }

    @Override
    public List<String> getExpandedPaths(FileObject root) {
        List<String> result = new ArrayList<>();
        RootState state = rootState(root);
        if (state != null && state.item() != null) {
            collectExpanded(state.item(), result);
        }
        return result;
    }

    @Override
    public String getSelectedPath(FileObject root) {
        RootState state = rootState(root);
        if (view == null || state == null) {
            return null;
        }
        TreeItem<ProjectEntry> selected = view.getSelectionModel().getSelectedItem();
        return isUnder(state, selected) ? nodeId(selected) : null;
    }

    /** Whether {@code item} belongs to the project tracked by {@code state}. */
    private boolean isUnder(RootState state, TreeItem<ProjectEntry> item) {
        for (TreeItem<ProjectEntry> cur = item; cur != null; cur = cur.getParent()) {
            if (cur == state.item()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(ProjectNavigatorImpl.class, "Navigator.description.logical");
    }

    /**
     * Recursively expands the container nodes leading to {@code target} and returns its node, or
     * {@code null} when not found. Branches that do not contain the target are collapsed back.
     */
    private TreeItem<ProjectEntry> findUnder(TreeItem<ProjectEntry> parent, Path target) {
        ProjectEntry parentEntry = parent.getValue();
        FileObject parentFo = parentEntry != null && parentEntry.isFileObject() ? parentEntry.getFileObject() : null;
        Path parentPath = parentFo != null ? FileUtil.toPath(parentFo) : null;
        if (parentPath != null && parentPath.equals(target)) {
            return parent;
        }
        if (!parent.isExpanded()) {
            parent.setExpanded(true);
        }
        for (TreeItem<ProjectEntry> child : new ArrayList<>(parent.getChildren())) {
            ProjectEntry entry = child.getValue();
            FileObject fo = entry != null && entry.isFileObject() ? entry.getFileObject() : null;
            Path path = fo != null ? FileUtil.toPath(fo) : null;
            if (path != null && path.equals(target)) {
                return child;
            }
            // A node worth descending into: a group node (no file) or a folder/package that
            // contains the target on disk.
            if (path == null || target.startsWith(path)) {
                boolean wasExpanded = child.isExpanded();
                TreeItem<ProjectEntry> result = findUnder(child, target);
                if (result != null) {
                    return result;
                }
                if (!wasExpanded) {
                    child.setExpanded(false);
                }
            }
        }
        return null;
    }

    @Override
    public void addProject(Lookup context) {
        FileObject fileObject = context.lookup(FileObject.class);
        if (fileObject == null) {
            throw new IllegalArgumentException("Can't render filetree without fileobject");
        }
        LOG.info("Show Project explorer with context: " + context + " and fileobject " + fileObject);
        if (view == null) {
            view = createTreeView();
        }
        ProjectRootState state = (ProjectRootState) addRootState(fileObject);
        if (state == null) {
            LOG.warning("Can't show a project without a path: " + fileObject);
            notifyLoadFailed(fileObject);
            return;
        }
        if (state.item() != null) {
            LOG.info("Project already shown in the Project view: " + fileObject.getPath());
            // Already loaded: report it again so a caller waiting for this project stops waiting.
            notifyLoaded(state.root());
            return;
        }
        treeRoot();
        state.loadingCancelled = false;
        state.loadingThread = new Thread(() -> {
            if (!loadProject(state, fileObject) && !state.loadingCancelled) {
                notifyLoadFailed(state.root());
            }
        }, "ProjectNavigator-load");
        state.loadingThread.setDaemon(true);
        state.loadingThread.start();
        view.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<ProjectEntry> selectedItem = view.getSelectionModel().getSelectedItem();
                LOG.info("Selected project item: " + selectedItem);
                if (selectedItem == null) return;
                ProjectEntry selectedProjectEntry = selectedItem.getValue();
                if (selectedProjectEntry != null
                        && selectedProjectEntry.getType() == ProjectEntry.Type.NAME
                        && !NavigatorHosts.isProjectRoot(selectedItem)) {
                    // A subproject: double-clicking opens it as its own project.
                    ProjectOpener opener = Lookup.getDefault().lookup(ProjectOpener.class);
                    if (opener != null) {
                        opener.open(selectedProjectEntry.getFileObject());
                    }
                } else {
                    PackageScanner.openFile(selectedProjectEntry);
                }
            }
         });
    }

    @Override
    protected FileChangeListener createFileChangeListener(RootState state) {
        return new FileChangeListener() {
            @Override
            public void fileFolderCreated(FileEvent fe) {
                LOG.info("Folder created: " + fe);
                syncPackagesFor(state, fe.getFile());
            }

            @Override
            public void fileDataCreated(FileEvent fe) {
                LOG.info("Data created: " + fe);
                FileObject fileObject = fe.getFile();
                if (PackageScanner.isIgnored(fileObject)) {
                    return;
                }
                Path path = FileUtil.toPath(fileObject);
                if (path == null || !path.startsWith(state.path())) {
                    return;
                }
                runOnFx(() -> {
                    // Make sure the package node exists before attaching the file to it.
                    syncPackages(state, path);
                    addFileNode(state, fileObject, path);
                });
            }

            @Override
            public void fileChanged(FileEvent fe) {
                LOG.info("File changed: " + fe);
            }

            @Override
            public void fileDeleted(FileEvent fe) {
                LOG.info("File deleted: " + fe);
                removeTreeItem(state, fe.getFile());
                syncPackagesFor(state, fe.getFile());
            }

            @Override
            public void fileRenamed(FileRenameEvent fre) {
                LOG.info("File renamed: " + fre);
                syncPackagesFor(state, fre.getFile());
            }

            @Override
            public void fileAttributeChanged(FileAttributeEvent fae) {
                LOG.info("File attribute changed: " + fae);
            }
        };
    }

    /** Adds a file node under its package node, if that package is shown and the node is missing. */
    private void addFileNode(RootState state, FileObject fileObject, Path path) {
        if (state.item() == null) {
            return;
        }
        TreeItem<ProjectEntry> parentItem = TreeNav.findTreeItem(state.item(), path.getParent());
        if (!(parentItem instanceof ProjectTreeItem pkg) || !pkg.isPackage()) {
            return;
        }
        boolean present = parentItem.getChildren().stream()
                .map(TreeItem::getValue)
                .anyMatch(e -> e != null && fileObject.equals(e.getFileObject()));
        if (present) {
            return;
        }
        boolean wasEmpty = parentItem.getChildren().stream()
                .map(TreeItem::getValue)
                .noneMatch(e -> e != null && e.getType() == ProjectEntry.Type.FILE);
        ProjectEntry fileEntry = new ProjectEntry(fileObject, path.getFileName().toString(),
                ProjectEntry.Type.FILE, ProjectEntry.BADGE.NO_BADGE, NavigatorIcons.getFileIconName(fileObject));
        parentItem.getChildren().add(new ProjectTreeItem(fileEntry));
        if (wasEmpty) {
            // The package was empty and now has a file: swap the empty-package icon for the normal one.
            pkg.refreshIcon();
        }
        TreeNav.checkMainClasses(parentItem);
        LOG.info("Added file node " + fileObject.getNameExt());
    }

    /** Reconciles the package children of the source group that owns {@code fileObject}'s path. */
    private void syncPackagesFor(RootState state, FileObject fileObject) {
        if (fileObject == null) {
            return;
        }
        Path path = FileUtil.toPath(fileObject);
        if (path == null || !path.startsWith(state.path())) {
            return;
        }
        runOnFx(() -> syncPackages(state, path));
    }

    /** Finds the expanded source-group node owning {@code path} and reconciles its packages. */
    private void syncPackages(RootState state, Path path) {
        if (state.item() == null) {
            return;
        }
        ProjectTreeItem group = findGroup(state.item(), path);
        if (group != null && group.isExpanded()) {
            group.syncPackages();
        }
    }

    /** The expanded {@link ProjectEntry.Type#GROUP} node whose source root contains {@code path}. */
    private ProjectTreeItem findGroup(TreeItem<ProjectEntry> node, Path path) {
        if (node instanceof ProjectTreeItem item) {
            ProjectEntry entry = item.getValue();
            if (entry != null && entry.getType() == ProjectEntry.Type.GROUP && entry.isFileObject()) {
                Path root = FileUtil.toPath(entry.getFileObject());
                if (root != null && path.startsWith(root)) {
                    return item;
                }
            }
        }
        for (TreeItem<ProjectEntry> child : node.getChildren()) {
            ProjectTreeItem found = findGroup(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Attaches lazily-built {@code children} to {@code node} on the FX thread, unless the background
     * work has gone stale. Skips the add when loading was cancelled (project closed/switched), when
     * {@code node} was detached from the current tree by a rebuild, or when {@code node} was already
     * populated (guards against a double-add if expansion somehow fires again).
     */
    private void addLazyChildren(ProjectRootState state, ProjectTreeItem node, List<TreeItem<ProjectEntry>> children) {
        if (state.loadingCancelled || !isInCurrentTree(node) || !node.getChildren().isEmpty()) {
            return;
        }
        node.getChildren().addAll(children);
    }

    /** Whether {@code node} is still connected to the live tree (not detached by a rebuild/close). */
    private boolean isInCurrentTree(TreeItem<?> node) {
        if (node == null || view == null || view.getRoot() == null) {
            return false;
        }
        TreeItem<?> cur = node;
        while (cur.getParent() != null) {
            cur = cur.getParent();
        }
        return cur == view.getRoot();
    }

    /** Removes the tree node backing {@code fileObject} (a file or folder) after it was deleted. */
    private void removeTreeItem(RootState state, FileObject fileObject) {
        if (fileObject == null || PackageScanner.isIgnored(fileObject)) {
            return;
        }
        FileObject parent = fileObject.getParent();
        if (parent == null) {
            return;
        }
        Path parentPath = FileUtil.toPath(parent);
        if (parentPath == null || !parentPath.startsWith(state.path())) {
            return;
        }
        runOnFx(() -> {
            if (state.item() == null) {
                return;
            }
            TreeItem<ProjectEntry> parentItem = TreeNav.findTreeItem(state.item(), parentPath);
            if (parentItem == null) {
                return;
            }
            boolean removed = parentItem.getChildren().removeIf(child -> {
                ProjectEntry entry = child.getValue();
                return entry != null && entry.isFileObject() && fileObject.equals(entry.getFileObject());
            });
            if (removed) {
                LOG.info("Removed file node " + fileObject.getNameExt());
                // If the package is now empty, show it with the empty-package icon instead of removing it.
                if (parentItem instanceof ProjectTreeItem pkg && pkg.isPackage()
                        && PackageScanner.isPackageEmpty(parent)) {
                    pkg.refreshIcon();
                }
            }
        });
    }

    /**
     * Loads the project rooted at {@code fileObject} into {@code state}. Runs off the FX thread:
     * {@code refresh()}, {@code isProject()} and {@code findProject()} all hit the disk - and parse
     * the project's build files - so only the tree updates are posted back.
     *
     * @return whether the project was found and its tree handed over to {@link #visualize}; a
     *         {@code false} return means either a failure or a cancellation
     */
    private boolean loadProject(ProjectRootState state, FileObject fileObject) {
        fileObject.refresh();
        if (!ProjectManager.getDefault().isProject(fileObject)) {
            LOG.info("No directory/project for: " + fileObject.getPath());
            return false;
        }
        LOG.info("This is a project, load it");
        try {
            state.project = ProjectManager.getDefault().findProject(fileObject);
        } catch (IOException e) {
            LOG.warning("Error finding project " + fileObject + ": " + e);
            Exceptions.printStackTrace(e);
            return false;
        }
        if (state.loadingCancelled) {
            LOG.info("Project loading cancelled for " + fileObject);
            return false;
        }
        if (state.project == null) {
            LOG.warning("ProjectManager couldn't find a project for " + fileObject);
            return false;
        }
        state.kind = ProjectKinds.providerOf(state.project);
        LOG.info("Project loaded: " + state.project + ", kind = " + state.kind);
        installFileChangeListener(state, FileUtil.toPath(state.project.getProjectDirectory()).toFile());
        if (state.loadingCancelled) {
            LOG.info("Project loading cancelled for " + fileObject);
            return false;
        }
        return visualize(state);
    }

    /**
     * Reports a project that could not be loaded, so the caller stops waiting for it. Every load
     * ends either here or in {@code onProjectLoaded}, unless it was cancelled.
     */
    private void notifyLoadFailed(FileObject root) {
        Platform.runLater(() -> {
            if (onProjectLoadFailed != null) {
                onProjectLoadFailed.accept(root);
            }
        });
    }

    /** Reports a loaded project on the FX thread. */
    private void notifyLoaded(FileObject root) {
        Platform.runLater(() -> {
            if (onProjectLoaded != null) {
                onProjectLoaded.accept(root);
            }
        });
    }

    private boolean visualize(ProjectRootState state) {
        Project project = state.project;
        ProjectKindProvider kind = state.kind;
        if (project == null || kind == null) {
            LOG.warning("visualize() called without project or valid kind");
            return false;
        }
        List<Project> subprojects = ProjectKinds.getSubprojects(project, kind);
        List<ProjectDirectory> subprojectDirectories = subprojects.isEmpty()
                ? kind.subprojectDirectories(project)
                : List.of();
        LOG.info("Project kind: " + kind + ", found " + subprojects.size()
                + " subprojects and " + subprojectDirectories.size() + " subproject directories");

        TreeItem<ProjectEntry> rootProjectNode = buildProjectNode(state, project,
                subprojects.isEmpty() && subprojectDirectories.isEmpty(), true);
        if (!subprojects.isEmpty() || !subprojectDirectories.isEmpty()) {
            ProjectEntry groupEntry = new ProjectEntry(project.getProjectDirectory(),
                    ProjectKinds.getSubprojectsGroupName(kind), ProjectEntry.Type.MODULES,
                    ProjectEntry.BADGE.MODULES_BADGE);
            ProjectTreeItem groupNode = new ProjectTreeItem(groupEntry);
            TreeNav.setExpandedInvalidationListener(groupNode, () ->
                nodeLoader.post(() -> {
                    List<TreeItem<ProjectEntry>> children = new ArrayList<>();
                    for (Project sub : subprojects) {
                        children.add(buildProjectNode(state, sub, true, false));
                    }
                    for (ProjectDirectory directory : subprojectDirectories) {
                        children.add(new ProjectTreeItem(new ProjectEntry(directory.dir(), directory.name(),
                                ProjectEntry.Type.DIRECTORY, ProjectEntry.BADGE.NO_BADGE)));
                    }
                    Platform.runLater(() -> addLazyChildren(state, groupNode, children));
                }));
            rootProjectNode.getChildren().add(groupNode);
        }
        Platform.runLater(() -> {
            if (state.loadingCancelled) {
                return;
            }
            setRootItem(state, rootProjectNode);
            applyPendingExpansion(state);
            if (onProjectLoaded != null) {
                onProjectLoaded.accept(state.root());
            }
        });
        return true;
    }

    private TreeItem<ProjectEntry> buildProjectNode(ProjectRootState state, Project project, boolean withSources, boolean isRoot) {
        ProjectKindProvider kind = state.kind;
        String name = ProjectKinds.getProjectName(project, kind);
        LOG.info("Building project node for " + name);
        String icon = NavigatorIcons.getProjectIconName(project, isRoot && !withSources, kind);
        ProjectEntry projectEntry = new ProjectEntry(project.getProjectDirectory(), name, ProjectEntry.Type.NAME, ProjectEntry.BADGE.NO_BADGE, icon);
        ProjectTreeItem projectNode = new ProjectTreeItem(projectEntry);
        if (withSources) {
            TreeNav.setExpandedInvalidationListener(projectNode, () ->
                nodeLoader.post(() -> {
                    Sources sources = ProjectUtils.getSources(project);
                    SourceGroup[] javaSources = sources.getSourceGroups(SOURCES_TYPE_JAVA);
                    List<TreeItem<ProjectEntry>> children = new ArrayList<>();
                    for (SourceGroup source : javaSources) {
                        LOG.info("Adding java source group: " + source.getDisplayName() + " for source " + source);
                        ProjectEntry packageGroupEntry = new ProjectEntry(source.getRootFolder(), source.getDisplayName(), ProjectEntry.Type.GROUP, ProjectEntry.BADGE.PACKAGE_BADGE);
                        children.add(new ProjectTreeItem(packageGroupEntry));
                    }

                    SourceGroup[] resources = sources.getSourceGroups(SOURCES_TYPE_RESOURCES);
                    for (SourceGroup source : resources) {
                        LOG.info("Adding resources group: " + source.getDisplayName() + " for source " + source);
                        ProjectEntry resourcesGroupEntry = new ProjectEntry(source.getRootFolder(), source.getDisplayName(), ProjectEntry.Type.GROUP, ProjectEntry.BADGE.OTHERS_BADGE);
                        children.add(new ProjectTreeItem(resourcesGroupEntry));
                    }
                    Platform.runLater(() -> {
                        addProjectFilesNode(project, kind, children);
                        addLibrariesNode(project, kind, children);
                        addLazyChildren(state, projectNode, children);
                    });
                }));
        }
        return projectNode;
    }

    /**
     * Appends the project's "project files" / "important files" group (from the project-kind
     * provider), if any. Runs on the FX thread because it attaches tree nodes.
     */
    private void addProjectFilesNode(Project project, ProjectKindProvider kind, List<TreeItem<ProjectEntry>> children) {
        String groupName = kind.projectFilesGroupName();
        if (groupName == null) {
            return;
        }
        List<ProjectFile> projectFiles = kind.projectFiles(project);
        if (projectFiles.isEmpty()) {
            return;
        }
        FileObject dir = project.getProjectDirectory();
        List<TreeItem<ProjectEntry>> fileChildren = new ArrayList<>();
        for (ProjectFile file : projectFiles) {
            FileObject fo = dir.getFileObject(file.path());
            if (fo != null && fo.isData()) {
                String name = file.displayName() != null ? file.displayName() : fo.getNameExt();
                fileChildren.add(new ProjectTreeItem(new ProjectEntry(fo, name, ProjectEntry.Type.FILE,
                        ProjectEntry.BADGE.NO_BADGE, NavigatorIcons.getFileIconName(fo))));
            }
        }
        if (fileChildren.isEmpty()) {
            return;
        }
        ProjectEntry groupEntry = new ProjectEntry(dir, groupName, ProjectEntry.Type.FILES,
                ProjectEntry.BADGE.PROJECTFILES_BADGE);
        ProjectTreeItem groupNode = new ProjectTreeItem(groupEntry);
        groupNode.getChildren().setAll(fileChildren);
        children.add(groupNode);
    }

    /**
     * Appends the project's "libraries" group (from the project-kind provider), if any. The entries
     * are descriptive (name + optional version), not files. Runs on the FX thread.
     */
    private void addLibrariesNode(Project project, ProjectKindProvider kind, List<TreeItem<ProjectEntry>> children) {
        String groupName = kind.librariesGroupName();
        if (groupName == null) {
            return;
        }
        List<ProjectLibrary> libraries = kind.libraries(project);
        if (libraries.isEmpty()) {
            return;
        }
        List<TreeItem<ProjectEntry>> libraryChildren = new ArrayList<>();
        for (ProjectLibrary library : libraries) {
            String name = library.detail() == null
                    ? library.name()
                    : library.name() + " (" + library.detail() + ")";
            String icon = library.iconName() != null ? library.iconName() : "jaricon.png";
            libraryChildren.add(new ProjectTreeItem(new ProjectEntry(null, name, ProjectEntry.Type.LIBRARY,
                    ProjectEntry.BADGE.NO_BADGE, icon)));
        }
        ProjectEntry groupEntry = new ProjectEntry(project.getProjectDirectory(), groupName,
                ProjectEntry.Type.LIBRARIES, ProjectEntry.BADGE.LIBRARIES_BADGE);
        ProjectTreeItem groupNode = new ProjectTreeItem(groupEntry);
        groupNode.getChildren().setAll(libraryChildren);
        children.add(groupNode);
    }

    @Override
    public Node getView() {
        LOG.info("View asked, return " + view);
        if (view == null) {
            view = createTreeView();
        }
        return view;
    }

    @Override
    public ObservableValue<FileObject> selectedFile() {
        return selectedFile.getReadOnlyProperty();
    }

    @Override
    public ObservableValue<List<FileObject>> selectedFiles() {
        return selectedFiles.getReadOnlyProperty();
    }

    private TreeView<ProjectEntry> createTreeView() {
        TreeView<ProjectEntry> treeView = new TreeView<>();
        // The root is a hidden holder: its children are the open projects' top-level nodes.
        treeView.setShowRoot(false);
        treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        treeView.setCellFactory(tv -> new ProjectTreeCell(this::selectMovedFiles));
        // The tree handles file drops itself (see TreeCellDnD), so the tab pane holding it, which
        // otherwise opens dropped files in an editor, leaves them alone.
        FileDragAndDrop.markDropTarget(treeView);
        treeView.getStylesheets().add(getClass().getResource("fileexplorer.css").toExternalForm());
        treeView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<ProjectEntry>>) c -> {
            List<FileObject> files = new ArrayList<>();
            for (TreeItem<ProjectEntry> item : treeView.getSelectionModel().getSelectedItems()) {
                ProjectEntry entry = item == null ? null : item.getValue();
                FileObject fo = entry == null ? null : entry.getFileObject();
                if (fo != null) {
                    files.add(fo);
                }
            }
            selectedFiles.set(List.copyOf(files));
            selectedFile.set(files.isEmpty() ? null : files.get(0));
            // Selecting anything inside a project makes that project the selected one - unless the
            // selection is one the session is restoring, which must not steal the selection.
            if (!isRestoringSelection()) {
                Projects.selectOwnerOf(files.isEmpty() ? null : files.get(0));
            }
            TreeNav.syncFocusToSelection(treeView);
        });
        // The focus model addresses rows by INDEX, so the row focused when the session's selection was
        // restored stops being the selected one as the packages still loading in the background insert
        // rows above it - leaving a focus ring on an unrelated file. Re-sync on every change of the
        // visible row count until the user takes over the tree.
        treeView.expandedItemCountProperty().subscribe(count -> {
            if (isRestoringFocus()) {
                TreeNav.syncFocusToSelection(treeView);
            }
        });
        trackSelectedProject(treeView);
        treeView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> stopRestoringFocus());
        treeView.addEventFilter(KeyEvent.KEY_PRESSED, e -> stopRestoringFocus());
        // Deletes the selected file(s) or package(s) (mirrors the context menu's Delete accelerator).
        treeView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                List<TreeItem<ProjectEntry>> selected =
                        new ArrayList<>(treeView.getSelectionModel().getSelectedItems());
                if (DeleteActions.deleteEntries(selected)) {
                    e.consume();
                }
            }
        });
        return treeView;
    }

    /** Whether any project is still restoring its selection, so the focus ring must follow it. */
    private boolean isRestoringFocus() {
        for (RootState state : rootStates()) {
            if (((ProjectRootState) state).restoringFocus) {
                return true;
            }
        }
        return false;
    }

    /** Stops following the restored selections: the user has taken over the tree. */
    private void stopRestoringFocus() {
        for (RootState state : rootStates()) {
            ((ProjectRootState) state).restoringFocus = false;
        }
    }

    // --- Tree expansion state ----------------------------------------------

    /**
     * Builds an identifier for an expandable node: its type, the root of the project owning it, and
     * its path relative to that root. The project root is part of the id so that the same relative
     * path in two open projects does not expand both.
     */
    private String nodeId(TreeItem<ProjectEntry> item) {
        ProjectEntry entry = item == null ? null : item.getValue();
        if (entry == null || !entry.isFileObject()) {
            return null;
        }
        Path path = FileUtil.toPath(entry.getFileObject());
        if (path == null) {
            return null;
        }
        RootState state = rootStateOf(path);
        if (state == null) {
            return null;
        }
        Path rootPath = state.path();
        String rel = rootPath.relativize(path).toString().replace(File.separatorChar, '/');
        return entry.getType().name() + "|" + rootPath + "|" + rel;
    }

    /** Collects the identifiers of {@code item} and all its expanded descendants, depth-first. */
    private void collectExpanded(TreeItem<ProjectEntry> item, List<String> out) {
        if (!item.isExpanded()) {
            return;
        }
        String id = nodeId(item);
        if (id != null) {
            out.add(id);
        }
        for (TreeItem<ProjectEntry> child : item.getChildren()) {
            collectExpanded(child, out);
        }
    }

    /** Re-applies a requested expansion set to the freshly built tree, honoring lazy child loading. */
    private void applyPendingExpansion(RootState state) {
        TreeItem<ProjectEntry> rootItem = state.item();
        if (rootItem == null) {
            return;
        }
        List<String> pending = state.takePendingExpanded();
        if (pending != null) {
            Set<String> targets = new HashSet<>(pending);
            Set<TreeItem<ProjectEntry>> visited = new HashSet<>();
            // The project node itself can be a target; expandNodeIfTarget then already watches its
            // children, so only walk into them here when it was not one.
            expandNodeIfTarget(state, rootItem, targets, visited);
            if (!visited.contains(rootItem)) {
                expandInto(state, rootItem, targets, visited);
            }
        }
        trySelectPending(state);
    }

    /**
     * Expands any target children of {@code parent}, and watches for children that are added later
     * (lazily, when a node is first expanded) so their own targets can be expanded in turn.
     */
    private void expandInto(RootState state, TreeItem<ProjectEntry> parent, Set<String> targets, Set<TreeItem<ProjectEntry>> visited) {
        for (TreeItem<ProjectEntry> child : new ArrayList<>(parent.getChildren())) {
            expandNodeIfTarget(state, child, targets, visited);
        }
        parent.getChildren().addListener((ListChangeListener<TreeItem<ProjectEntry>>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (TreeItem<ProjectEntry> child : new ArrayList<>(change.getAddedSubList())) {
                        expandNodeIfTarget(state, child, targets, visited);
                    }
                    trySelectPending(state);
                }
            }
        });
    }

    private void expandNodeIfTarget(RootState state, TreeItem<ProjectEntry> item, Set<String> targets, Set<TreeItem<ProjectEntry>> visited) {
        String id = nodeId(item);
        if (id == null || !targets.contains(id) || !visited.add(item)) {
            return;
        }
        // Watch this node's children (present now or built lazily on expand), then expand it.
        expandInto(state, item, targets, visited);
        if (!item.isExpanded()) {
            item.setExpanded(true);
        }
    }

    /** Selects the pending node once it has been built, without moving keyboard focus into the tree. */
    private void trySelectPending(RootState state) {
        String pendingSelected = state.pendingSelected();
        TreeItem<ProjectEntry> rootItem = state.item();
        if (pendingSelected == null || rootItem == null || view == null) {
            return;
        }
        TreeItem<ProjectEntry> match = pendingSelected.equals(nodeId(rootItem))
                ? rootItem : findNode(rootItem, pendingSelected);
        if (match == null) {
            return;
        }
        state.setPendingSelected(null);
        // The tree allows multiple selection, so select() would ADD to whatever is selected: restoring
        // a session must end with exactly the persisted node of THIS project selected, without
        // disturbing what another open project restored or what the user selected there. Only reached
        // once the node has been found, so an unresolvable target never clears anything.
        restoringSelection(() -> {
            clearSelectionOf(state);
            view.getSelectionModel().select(match);
        });
        ((ProjectRootState) state).restoringFocus = true;
        int row = view.getRow(match);
        LOG.fine(() -> "Restored selection of " + nodeId(match) + " at row " + view.getRow(match)
                + " of " + view.getExpandedItemCount());
        if (row >= 0) {
            view.scrollTo(row);
        }
    }

    /** Deselects everything currently selected within {@code state}'s project, leaving the rest alone. */
    private void clearSelectionOf(RootState state) {
        for (TreeItem<ProjectEntry> item : new ArrayList<>(view.getSelectionModel().getSelectedItems())) {
            if (isUnder(state, item)) {
                int row = view.getRow(item);
                if (row >= 0) {
                    view.getSelectionModel().clearSelection(row);
                }
            }
        }
    }

    /** Depth-first search for the tree item whose {@link #nodeId} equals {@code id}. */
    private TreeItem<ProjectEntry> findNode(TreeItem<ProjectEntry> parent, String id) {
        for (TreeItem<ProjectEntry> child : new ArrayList<>(parent.getChildren())) {
            if (id.equals(nodeId(child))) {
                return child;
            }
            TreeItem<ProjectEntry> found = findNode(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
