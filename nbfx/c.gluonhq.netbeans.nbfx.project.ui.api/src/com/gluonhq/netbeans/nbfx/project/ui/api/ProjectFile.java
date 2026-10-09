package com.gluonhq.netbeans.nbfx.project.ui.api;

/**
 * A file to show under a project's "project files" / "important files" group, given by its path
 * relative to the project directory.
 *
 * @param path        the file's path relative to the project directory (e.g. {@code nbproject/project.xml})
 * @param displayName the name to show, or {@code null} to use the file name
 */
public record ProjectFile(String path, String displayName) {
}
