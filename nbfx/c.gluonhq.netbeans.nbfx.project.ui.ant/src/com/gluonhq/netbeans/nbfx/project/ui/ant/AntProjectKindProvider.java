package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectFile;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKindProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * {@link ProjectKindProvider} for Ant-based projects, including the NetBeans module (apisupport)
 * projects this repository itself is made of. Recognition reads the project type from
 * {@code nbproject/project.xml}; the icon is chosen from that type, using the same icons as the
 * original Swing modules (copied into this module).
 */
@ServiceProvider(service = ProjectKindProvider.class)
public final class AntProjectKindProvider implements ProjectKindProvider {

    private static final String TYPE_APISUPPORT = "org.netbeans.modules.apisupport.project";
    private static final String TYPE_APISUPPORT_SUITE = "org.netbeans.modules.apisupport.project.suite";
    private static final String TYPE_J2SE = "org.netbeans.modules.java.j2seproject";

    @Override
    public String id() {
        return "ant";
    }

    @Override
    public boolean recognizes(Project project) {
        return projectType(project.getProjectDirectory()) != null;
    }

    @Override
    public List<Project> subprojects(Project project) {
        List<Project> result = new ArrayList<>();
        findNestedProjects(project.getProjectDirectory(), result);
        return result;
    }

    @Override
    public String projectFilesGroupName() {
        return NbBundle.getMessage(AntProjectKindProvider.class, "ImportantFilesGroupName");
    }

    @Override
    public List<ProjectFile> projectFiles(Project project) {
        FileObject dir = project.getProjectDirectory();
        List<ProjectFile> files = new ArrayList<>();
        for (String path : IMPORTANT_FILES) {
            if (dir.getFileObject(path) != null) {
                files.add(new ProjectFile(path, null));
            }
        }
        return files;
    }

    /** The files the original apisupport "Important Files" node lists, in order. */
    private static final String[] IMPORTANT_FILES = {
        "manifest.mf",
        "build.xml",
        "nbproject/project.xml",
        "nbproject/project.properties",
        "nbproject/private/private.properties",
        "nbproject/platform.properties",
        "nbproject/private/platform-private.properties",
    };

    @Override
    public String librariesGroupName() {
        return NbBundle.getMessage(AntProjectKindProvider.class, "LibrariesGroupName");
    }

    @Override
    public List<ProjectLibrary> libraries(Project project) {
        FileObject projectXml = project.getProjectDirectory().getFileObject("nbproject/project.xml");
        if (projectXml == null || !projectXml.isData()) {
            return List.of();
        }
        List<ProjectLibrary> libraries = new ArrayList<>();
        try (InputStream in = projectXml.getInputStream()) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(in);
            NodeList moduleDependencies = doc.getElementsByTagName("module-dependencies");
            for (int m = 0; m < moduleDependencies.getLength(); m++) {
                Element section = (Element) moduleDependencies.item(m);
                NodeList dependencies = section.getElementsByTagName("dependency");
                for (int d = 0; d < dependencies.getLength(); d++) {
                    Element dependency = (Element) dependencies.item(d);
                    String cnb = textOf(dependency, "code-name-base");
                    if (cnb == null) {
                        continue;
                    }
                    libraries.add(new ProjectLibrary(cnb, textOf(dependency, "specification-version"), "jaricon.png"));
                }
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return libraries;
    }

    private static String textOf(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    @Override
    public String iconName(Project project, boolean isMaster) {
        String type = projectType(project.getProjectDirectory());
        if (TYPE_APISUPPORT.equals(type)) {
            return "module.png";
        }
        if (TYPE_APISUPPORT_SUITE.equals(type)) {
            return "suite.png";
        }
        if (TYPE_J2SE.equals(type)) {
            return "j2seProject.png";
        }
        return "jdk-project.png";
    }

    /**
     * The Ant project type declared in {@code nbproject/project.xml}, or {@code null} when the
     * project has no readable {@code nbproject/project.xml}. An empty string means it is an Ant
     * project without a declared type.
     */
    private static String projectType(FileObject dir) {
        FileObject nbproject = dir.getFileObject("nbproject");
        if (nbproject == null) {
            return null;
        }
        FileObject projectXml = nbproject.getFileObject("project.xml");
        if (projectXml == null || !projectXml.isData()) {
            return null;
        }
        try (InputStream in = projectXml.getInputStream()) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(in);
            NodeList types = doc.getDocumentElement().getElementsByTagName("type");
            if (types.getLength() > 0) {
                return types.item(0).getTextContent().trim();
            }
            return "";
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    /** Discovers Ant-style nested projects (a folder containing {@code nbproject/project.xml}). */
    private static void findNestedProjects(FileObject dir, List<Project> result) {
        for (FileObject child : dir.getChildren()) {
            if (!child.isFolder() || child.getFileObject("nbproject/project.xml") == null) {
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
