package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Run" category: the main class and application arguments. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SERunCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "run";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SERunCustomizerPanel.class, "Run.displayName");
    }

    @Override
    public int position() {
        return 400;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("main.class", "Run.mainClass"),
                new Field("application.args", "Run.args"));
    }
}
