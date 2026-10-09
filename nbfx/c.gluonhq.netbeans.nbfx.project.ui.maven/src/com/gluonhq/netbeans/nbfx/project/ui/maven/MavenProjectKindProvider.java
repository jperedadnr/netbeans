package com.gluonhq.netbeans.nbfx.project.ui.maven;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectFile;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKindProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.project.MavenProject;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * {@link ProjectKindProvider} for Maven projects. Recognises Maven projects, resolves their
 * display name and module subprojects, and selects their navigator icons.
 */
@ServiceProvider(service = ProjectKindProvider.class)
public final class MavenProjectKindProvider implements ProjectKindProvider {

    @Override
    public String id() {
        return "maven";
    }

    @Override
    public boolean recognizes(Project project) {
        if (project.getLookup().lookup(NbMavenProject.class) != null) {
            return true;
        }
        ProjectManager.Result result = ProjectManager.getDefault().isProject2(project.getProjectDirectory());
        if (result != null && "org-netbeans-modules-maven".equals(result.getProjectType())) {
            return true;
        }
        FileObject pom = project.getProjectDirectory().getFileObject("pom.xml");
        return pom != null && pom.isData();
    }

    @Override
    public String displayName(Project project) {
        NbMavenProject nbMaven = project.getLookup().lookup(NbMavenProject.class);
        if (nbMaven != null && nbMaven.getMavenProject().getName() != null) {
            return nbMaven.getMavenProject().getName();
        }
        return null;
    }

    @Override
    public List<Project> subprojects(Project root) {
        NbMavenProject nbMaven = root.getLookup().lookup(NbMavenProject.class);
        if (nbMaven == null) {
            return List.of();
        }
        MavenProject mavenProject = nbMaven.getMavenProject();
        List<Project> result = new ArrayList<>();
        for (String module : mavenProject.getModules()) {
            String relPath = module.replace("\\", "/");
            FileObject moduleDir = root.getProjectDirectory().getFileObject(relPath);
            if (moduleDir == null) {
                continue;
            }
            try {
                Project sub = ProjectManager.getDefault().findProject(moduleDir);
                if (sub != null) {
                    result.add(sub);
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return result;
    }

    @Override
    public String subprojectsGroupName() {
        return NbBundle.getMessage(MavenProjectKindProvider.class, "SubprojectsGroupName");
    }

    @Override
    public String projectFilesGroupName() {
        return NbBundle.getMessage(MavenProjectKindProvider.class, "ProjectFilesGroupName");
    }

    @Override
    public List<ProjectFile> projectFiles(Project project) {
        FileObject dir = project.getProjectDirectory();
        List<ProjectFile> files = new ArrayList<>();
        addIfPresent(dir, "pom.xml", files);
        addIfPresent(dir, ".nb-configuration.xml", files);
        addIfPresent(dir, "nbactions.xml", files);
        return files;
    }

    private static void addIfPresent(FileObject dir, String path, List<ProjectFile> files) {
        if (dir.getFileObject(path) != null) {
            files.add(new ProjectFile(path, null));
        }
    }

    @Override
    public String iconName(Project project, boolean isMaster) {
        if (isMaster) {
            return "Maven2Icon.png";
        }
        NbMavenProject nbMaven = project.getLookup().lookup(NbMavenProject.class);
        if (nbMaven != null) {
            String packaging = nbMaven.getMavenProject().getPackaging();
            if ("nbm".equals(packaging)) {
                return "nbmicon.png";
            } else if ("nbm-application".equals(packaging)) {
                return "suiteicon.png";
            }
        }
        return "jaricon.png";
    }
}
