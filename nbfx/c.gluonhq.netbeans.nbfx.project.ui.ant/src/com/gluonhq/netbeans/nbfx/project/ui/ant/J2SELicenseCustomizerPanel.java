package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "License Headers" category: the project's license file. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SELicenseCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "license";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SELicenseCustomizerPanel.class, "License.displayName");
    }

    @Override
    public int position() {
        return 605;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("project.license", "License.projectLicense"));
    }
}
