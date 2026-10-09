package com.gluonhq.netbeans.nbfx.project.ui.api;

import org.openide.filesystems.FileObject;

/**
 * A subproject shown under a project's "subprojects" group that is not a standalone NetBeans
 * {@link org.netbeans.api.project.Project} (for example a Gradle subproject, which is part of the
 * root project's model). It is rendered as an expandable directory node.
 *
 * @param name the display name of the subproject
 * @param dir  the subproject's directory
 */
public record ProjectDirectory(String name, FileObject dir) {
}
