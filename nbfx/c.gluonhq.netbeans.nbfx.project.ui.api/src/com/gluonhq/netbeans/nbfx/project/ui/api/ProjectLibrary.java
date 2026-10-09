package com.gluonhq.netbeans.nbfx.project.ui.api;

/**
 * A library / dependency shown under a project's "libraries" group. Purely descriptive: it has a
 * name, an optional detail (e.g. a version) and an optional icon resource name.
 *
 * @param name     the library's display name
 * @param detail   an optional detail shown in parentheses, or {@code null}
 * @param iconName an optional icon resource name, or {@code null} for the default
 */
public record ProjectLibrary(String name, String detail, String iconName) {
}
