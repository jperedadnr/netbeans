package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Application" category: the application title and vendor. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SEApplicationCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "application";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SEApplicationCustomizerPanel.class, "Application.displayName");
    }

    @Override
    public int position() {
        return 500;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("application.title", "Application.title"),
                new Field("application.vendor", "Application.vendor"));
    }
}
