package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import org.netbeans.api.project.Project;

/**
 * Base for the apisupport (NetBeans module) customizer panels. Apisupport and J2SE projects share the
 * "ant" kind, so these panels apply only when the project's declared type is apisupport.
 */
abstract class AntModuleCustomizerPanel implements FxProjectCustomizerPanel {

    /** The project type of a NetBeans module project. */
    static final String APISUPPORT_TYPE = "org.netbeans.modules.apisupport.project";

    @Override
    public boolean appliesTo(Project project, String kindId) {
        return APISUPPORT_TYPE.equals(AntProjectFiles.projectType(project.getProjectDirectory()));
    }
}
