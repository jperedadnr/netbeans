package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import org.netbeans.api.project.Project;

/**
 * Base for the J2SE (Ant-based Java SE) customizer panels. Apisupport and J2SE projects share the
 * "ant" kind, so these panels apply only when the project's declared type is the J2SE project.
 */
abstract class J2SECustomizerPanel implements FxProjectCustomizerPanel {

    /** The project type of a J2SE (Ant) project. */
    static final String J2SE_TYPE = "org.netbeans.modules.java.j2seproject";

    @Override
    public boolean appliesTo(Project project, String kindId) {
        return J2SE_TYPE.equals(AntProjectFiles.projectType(project.getProjectDirectory()));
    }
}
