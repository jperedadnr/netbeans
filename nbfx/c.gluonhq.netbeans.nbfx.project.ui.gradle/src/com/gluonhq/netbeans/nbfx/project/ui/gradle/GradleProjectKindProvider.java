package com.gluonhq.netbeans.nbfx.project.ui.gradle;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectDirectory;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectFile;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKindProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * {@link ProjectKindProvider} for Gradle projects. Recognises a project by its build/settings
 * scripts, discovers its subprojects from the {@code include} declarations in {@code settings.gradle}
 * (the piece the generic provider left unimplemented), and contributes its build scripts and icon.
 */
@ServiceProvider(service = ProjectKindProvider.class)
public final class GradleProjectKindProvider implements ProjectKindProvider {

    private static final String[] BUILD_FILES = {"build.gradle", "build.gradle.kts"};
    private static final String[] SETTINGS_FILES = {"settings.gradle", "settings.gradle.kts"};
    private static final String[] PROJECT_FILES = {
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties"
    };

    /** Matches an {@code include ...} line; the quoted project paths are extracted from the tail. */
    private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\b(.*)$");
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");

    @Override
    public String id() {
        return "gradle";
    }

    @Override
    public boolean recognizes(Project project) {
        FileObject dir = project.getProjectDirectory();
        return hasAny(dir, BUILD_FILES) || hasAny(dir, SETTINGS_FILES);
    }

    @Override
    public String subprojectsGroupName() {
        return NbBundle.getMessage(GradleProjectKindProvider.class, "SubprojectsGroupName");
    }

    @Override
    public List<Project> subprojects(Project project) {
        FileObject dir = project.getProjectDirectory();
        List<Project> result = new ArrayList<>();
        for (String path : includePaths(dir)) {
            FileObject subDir = dir.getFileObject(path);
            if (subDir == null || !subDir.isFolder()) {
                continue;
            }
            try {
                Project sub = ProjectManager.getDefault().findProject(subDir);
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
    public List<ProjectDirectory> subprojectDirectories(Project project) {
        FileObject dir = project.getProjectDirectory();
        List<ProjectDirectory> result = new ArrayList<>();
        for (String path : includePaths(dir)) {
            FileObject subDir = dir.getFileObject(path);
            if (subDir != null && subDir.isFolder()) {
                result.add(new ProjectDirectory(subDir.getNameExt(), subDir));
            }
        }
        return result;
    }

    private static Set<String> includePaths(FileObject dir) {
        Set<String> paths = new LinkedHashSet<>();
        for (String settings : SETTINGS_FILES) {
            FileObject file = dir.getFileObject(settings);
            if (file != null && file.isData()) {
                paths.addAll(parseIncludes(file));
            }
        }
        return paths;
    }

    @Override
    public String projectFilesGroupName() {
        return NbBundle.getMessage(GradleProjectKindProvider.class, "BuildScriptsGroupName");
    }

    @Override
    public List<ProjectFile> projectFiles(Project project) {
        FileObject dir = project.getProjectDirectory();
        List<ProjectFile> files = new ArrayList<>();
        for (String path : PROJECT_FILES) {
            if (dir.getFileObject(path) != null) {
                files.add(new ProjectFile(path, null));
            }
        }
        return files;
    }

    @Override
    public String iconName(Project project, boolean isMaster) {
        return "gradle.png";
    }

    private static boolean hasAny(FileObject dir, String[] names) {
        for (String name : names) {
            if (dir.getFileObject(name) != null) {
                return true;
            }
        }
        return false;
    }

    /** The subproject directories declared by {@code include ':a', ':b:c'} in a settings script. */
    static List<String> parseIncludes(FileObject settings) {
        try (InputStream in = settings.getInputStream()) {
            return parseIncludes(in);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return List.of();
        }
    }

    static List<String> parseIncludes(InputStream in) throws IOException {
        List<String> paths = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher include = INCLUDE.matcher(line);
                if (!include.find()) {
                    continue;
                }
                Matcher quoted = QUOTED.matcher(include.group(1));
                while (quoted.find()) {
                    String projectPath = quoted.group(1).trim();
                    String relative = (projectPath.startsWith(":") ? projectPath.substring(1) : projectPath)
                            .replace(':', '/');
                    if (!relative.isBlank()) {
                        paths.add(relative);
                    }
                }
            }
        }
        return paths;
    }
}
