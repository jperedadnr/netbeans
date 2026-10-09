package com.gluonhq.netbeans.nbfx.project.ui.java;

import com.gluonhq.netbeans.nbfx.project.ui.java.utils.DeleteActions;

import java.util.List;

import javafx.scene.control.TreeItem;

import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;

/**
 * The {@link NavigatorContextMenuFactory.Host} implementations of the two navigator views. They live
 * apart from the tree cells so the rules they encode — which node is a project root and which nodes
 * can be deleted — can be exercised without instantiating JavaFX controls.
 * <p>
 * Both trees hold a hidden root whose children are the open projects' top-level nodes, so a project
 * root is a node whose <em>grandparent</em> is missing.
 */
final class NavigatorHosts {

    private NavigatorHosts() {
    }

    /** Whether {@code item} is the top-level node of an open project. */
    static boolean isProjectRoot(TreeItem<?> item) {
        return item != null && item.getParent() != null && item.getParent().getParent() == null;
    }

    /** The hooks of the physical Files view, whose nodes are {@link FileObject}s. */
    static NavigatorContextMenuFactory.Host<FileObject> files() {
        return new NavigatorContextMenuFactory.Host<>() {
            @Override
            public FileObject fileOf(TreeItem<FileObject> item) {
                return item == null ? null : item.getValue();
            }

            @Override
            public boolean isRoot(TreeItem<FileObject> item) {
                return isProjectRoot(item);
            }

            @Override
            public boolean isDeletable(TreeItem<FileObject> item) {
                // A project's own folder is closed, never deleted, from the tree.
                return item != null && item.getValue() != null
                        && item.getParent() != null && !isProjectRoot(item);
            }

            @Override
            public boolean isProject(TreeItem<FileObject> item) {
                FileObject fo = item == null ? null : item.getValue();
                return fo != null && fo.isFolder() && !isProjectRoot(item)
                        && ProjectManager.getDefault().isProject2(fo) != null;
            }

            @Override
            public void deleteItems(List<TreeItem<FileObject>> items) {
                DeleteActions.deleteItems(items, TreeItem::getValue);
            }
        };
    }

    /** The hooks of the logical Project view, whose nodes are {@link ProjectEntry}s. */
    static NavigatorContextMenuFactory.Host<ProjectEntry> project() {
        return new NavigatorContextMenuFactory.Host<>() {
            @Override
            public FileObject fileOf(TreeItem<ProjectEntry> item) {
                ProjectEntry entry = item == null ? null : item.getValue();
                return entry == null ? null : entry.getFileObject();
            }

            @Override
            public boolean isRoot(TreeItem<ProjectEntry> item) {
                return isProjectRoot(item);
            }

            @Override
            public boolean isDeletable(TreeItem<ProjectEntry> item) {
                return item != null && !isProjectRoot(item) && DeleteActions.isDeletable(item.getValue());
            }

            @Override
            public boolean isProject(TreeItem<ProjectEntry> item) {
                // A nested NAME node is a subproject (the top-level NAME node is the tree root).
                ProjectEntry entry = item == null ? null : item.getValue();
                return entry != null
                        && entry.getType() == ProjectEntry.Type.NAME
                        && !isProjectRoot(item);
            }

            @Override
            public void deleteItems(List<TreeItem<ProjectEntry>> items) {
                DeleteActions.deleteItems(items, it -> {
                    ProjectEntry entry = it == null ? null : it.getValue();
                    return entry == null ? null : entry.getFileObject();
                });
            }
        };
    }
}
