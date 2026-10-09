package com.gluonhq.netbeans.nbfx.project.ui.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Dispatches project-navigator behaviour to the registered {@link ProjectKindProvider}s. The first
 * provider that recognises a project wins; when none does, the built-in generic provider (Gradle/Ant
 * heuristics) is used, so callers never need to know the concrete project type.
 */
public final class ProjectKinds {

    private static final Logger LOG = Logger.getLogger(ProjectKinds.class.getName());

    private static final ProjectKindProvider DEFAULT = new GenericProjectKindProvider();

    private ProjectKinds() {}

    /** The provider that handles {@code project}: the first registered one, or the generic fallback. */
    public static ProjectKindProvider providerOf(Project project) {
        for (ProjectKindProvider provider : Lookup.getDefault().lookupAll(ProjectKindProvider.class)) {
            if (provider.recognizes(project)) {
                return provider;
            }
        }
        return DEFAULT;
    }

    public static String getProjectName(Project project, ProjectKindProvider kind) {
        String name = kind.displayName(project);
        if (name != null) {
            return name;
        }
        ProjectInformation info = project.getLookup().lookup(ProjectInformation.class);
        return info != null ? info.getDisplayName() : project.getProjectDirectory().getNameExt();
    }

    public static List<Project> getSubprojects(Project project, ProjectKindProvider kind) {
        return kind.subprojects(project);
    }

    /** The label of the subprojects group node, falling back to the generic label. */
    public static String getSubprojectsGroupName(ProjectKindProvider kind) {
        String name = kind.subprojectsGroupName();
        return name != null ? name : NbBundle.getMessage(ProjectKinds.class, "DefaultSubprojectsName");
    }

    /**
     * The generic provider: handles every project no specific provider recognises, using only
     * file-system heuristics (Gradle build files, Ant {@code nbproject/project.xml}) and the
     * {@code ProjectInformation} name.
     */
    private static final class GenericProjectKindProvider implements ProjectKindProvider {

        @Override
        public String id() {
            return "generic";
        }

        @Override
        public boolean recognizes(Project project) {
            return true;
        }

        @Override
        public List<Project> subprojects(Project project) {
            List<Project> result = new ArrayList<>();
            findNestedProjects(project.getProjectDirectory(), result);
            return result;
        }

        @Override
        public String iconName(Project project, boolean isMaster) {
            FileObject dir = project.getProjectDirectory();
            if (isGradle(dir)) {
                return "gradle.png";
            }
            if (isAnt(dir)) {
                return "jdk-project.png";
            }
            return null;
        }

        private static boolean isGradle(FileObject dir) {
            ProjectManager.Result result = ProjectManager.getDefault().isProject2(dir);
            if (result != null && "org-netbeans-modules-gradle".equals(result.getProjectType())) {
                return true;
            }
            return dir.getFileObject("build.gradle") != null ||
                    dir.getFileObject("build.gradle.kts") != null ||
                    dir.getFileObject("settings.gradle") != null ||
                    dir.getFileObject("settings.gradle.kts") != null;
        }

        private static boolean isAnt(FileObject dir) {
            FileObject nbproject = dir.getFileObject("nbproject");
            if (nbproject == null) {
                return false;
            }
            FileObject projectXml = nbproject.getFileObject("project.xml");
            return projectXml != null && projectXml.isData();
        }

        /** Discovers Ant-style nested projects (a folder containing {@code nbproject/project.xml}). */
        private static void findNestedProjects(FileObject dir, List<Project> result) {
            for (FileObject child : dir.getChildren()) {
                if (!child.isFolder()) {
                    continue;
                }
                if (child.getFileObject("nbproject/project.xml") == null) {
                    continue;
                }
                try {
                    Project sub = ProjectManager.getDefault().findProject(child);
                    if (sub != null) {
                        result.add(sub);
                        findNestedProjects(child, result);
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }
}
