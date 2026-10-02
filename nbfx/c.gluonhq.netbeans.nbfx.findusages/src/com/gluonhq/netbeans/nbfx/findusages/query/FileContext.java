package com.gluonhq.netbeans.nbfx.findusages.query;

import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.findusages.model.EnclosingElement;
import com.gluonhq.netbeans.nbfx.findusages.model.UsageContext;
import com.sun.source.tree.CompilationUnitTree;
import java.net.URL;
import java.util.List;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.queries.UnitTestForSourceQuery;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Where one file lives - project, source root, package - computed once per compiled file and shared
 * by all its usages, which only add their own enclosing declarations.
 *
 * @param file           the on-disk file
 * @param projectRoot    the open project's root, or the source root's parent, or the file's folder
 * @param projectName    its name
 * @param sourceRoot     the source root, or {@code null}
 * @param sourceRootName the root's path relative to the project, or "Source Packages"
 * @param packageName    the unit's package, empty when unnamed
 * @param inTestRoot     whether the source root is a test root
 */
record FileContext(FileObject file, FileObject projectRoot, String projectName, FileObject sourceRoot,
        String sourceRootName, String packageName, boolean inTestRoot) {

    private static final String DEFAULT_ROOT_NAME = "Source Packages";

    /** The context of {@code file}, whose compiled unit is {@code unit}, under {@code sourceRoot} (may be {@code null}). */
    static FileContext of(FileObject file, CompilationUnitTree unit, FileObject sourceRoot) {
        String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        return of(file, packageName, sourceRoot);
    }

    /**
     * The context of a resource {@code file} (a style sheet) under {@code sourceRoot} (may be
     * {@code null}): its "package" is its folder's path relative to the root, dotted as the
     * Projects view shows resource folders.
     */
    static FileContext ofResource(FileObject file, FileObject sourceRoot) {
        String relative = sourceRoot == null || file.getParent() == null ? null
                : FileUtil.getRelativePath(sourceRoot, file.getParent());
        return of(file, relative == null ? "" : relative.replace('/', '.'), sourceRoot);
    }

    private static FileContext of(FileObject file, String packageName, FileObject sourceRoot) {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.ownerOf(file);
        FileObject projectRoot = project != null ? project.getRoot()
                : sourceRoot != null && sourceRoot.getParent() != null ? sourceRoot.getParent()
                : file.getParent();
        String projectName = project != null ? project.getDisplayName() : projectRoot.getNameExt();
        String rootName = sourceRoot == null ? DEFAULT_ROOT_NAME : sourceRootName(projectRoot, sourceRoot);
        return new FileContext(file, projectRoot, projectName, sourceRoot, rootName, packageName, isTestRoot(sourceRoot));
    }

    /**
     * The label of {@code sourceRoot} as the Projects view shows it: its source group's display name
     * ("Source Packages", "Test Packages", ...) when the owning project declares one, else its path
     * relative to the project.
     */
    private static String sourceRootName(FileObject projectRoot, FileObject sourceRoot) {
        Project owner = FileOwnerQuery.getOwner(sourceRoot);
        if (owner != null) {
            Sources sources = ProjectUtils.getSources(owner);
            for (String type : List.of(JavaProjectConstants.SOURCES_TYPE_JAVA, JavaProjectConstants.SOURCES_TYPE_RESOURCES)) {
                for (SourceGroup group : sources.getSourceGroups(type)) {
                    if (sourceRoot.equals(group.getRootFolder())) {
                        return group.getDisplayName();
                    }
                }
            }
        }
        String relative = FileUtil.getRelativePath(projectRoot, sourceRoot);
        return relative == null || relative.isEmpty() ? DEFAULT_ROOT_NAME : relative;
    }

    /**
     * Whether {@code root} holds tests: the platform's {@link UnitTestForSourceQuery} knows the
     * source roots it tests, else the conventional {@code src/test} layout tells.
     */
    static boolean isTestRoot(FileObject root) {
        if (root == null) {
            return false;
        }
        URL[] sources = UnitTestForSourceQuery.findSources(root);
        if (sources != null && sources.length > 0) {
            return true;
        }
        String path = root.getPath();
        return path.contains("/test/") || path.endsWith("/test");
    }

    UsageContext context(List<EnclosingElement> enclosing) {
        return new UsageContext(projectRoot, projectName, sourceRoot, sourceRootName, packageName, file, enclosing);
    }
}
