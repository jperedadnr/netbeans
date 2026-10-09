package com.gluonhq.netbeans.nbfx.project.ui.api;

import java.util.List;
import org.netbeans.api.project.Project;

/**
 * Service provider interface for build-system specific project behaviour in the navigator.
 *
 * <p>There is deliberately no closed set of project kinds: a provider identifies itself with an
 * open-ended {@link #id()} and declares which projects it {@link #recognizes(Project) recognises}.
 * The first registered provider that recognises a project supplies its display name, subprojects,
 * subprojects group label and icon. When none recognises it, the built-in generic provider handles
 * the project (Gradle/Ant heuristics and the {@code ProjectInformation} name).</p>
 *
 * <p>Register implementations via {@code @ServiceProvider(service = ProjectKindProvider.class)}.</p>
 */
public interface ProjectKindProvider {

    /** A stable, open-ended identifier for this kind (for example {@code "maven"}), used for diagnostics. */
    String id();

    /** Whether this provider knows how to handle {@code project}. */
    boolean recognizes(Project project);

    /** Returns the project's display name, or {@code null} to fall back to {@code ProjectInformation}. */
    default String displayName(Project project) {
        return null;
    }

    /** Returns the project's subprojects/modules, or an empty list. */
    default List<Project> subprojects(Project project) {
        return List.of();
    }

    /**
     * Returns the project's subprojects that are not standalone NetBeans projects (for example
     * Gradle subprojects), rendered as directory nodes under the subprojects group. Used when
     * {@link #subprojects(Project)} is empty.
     */
    default List<ProjectDirectory> subprojectDirectories(Project project) {
        return List.of();
    }

    /** Returns the label of the subprojects group node, or {@code null} to use the generic label. */
    default String subprojectsGroupName() {
        return null;
    }

    /**
     * Returns the label of the "project files" / "important files" group, or {@code null} to omit
     * that group. The files themselves come from {@link #projectFiles(Project)}.
     */
    default String projectFilesGroupName() {
        return null;
    }

    /**
     * Returns the files to show under the {@link #projectFilesGroupName() project files group}, or an
     * empty list. Paths are relative to the project directory.
     */
    default List<ProjectFile> projectFiles(Project project) {
        return List.of();
    }

    /**
     * Returns the label of the "libraries" group, or {@code null} to omit that group. The libraries
     * themselves come from {@link #libraries(Project)}.
     */
    default String librariesGroupName() {
        return null;
    }

    /** Returns the libraries / dependencies to show under the libraries group, or an empty list. */
    default List<ProjectLibrary> libraries(Project project) {
        return List.of();
    }

    /**
     * Returns the navigator icon resource name for the project, or {@code null}.
     *
     * @param isMaster whether the project is the root of the navigator tree
     */
    default String iconName(Project project, boolean isMaster) {
        return null;
    }
}
