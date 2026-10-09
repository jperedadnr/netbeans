package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Build / Compiling" category: the Java source/target level. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SECompilingCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "build.compiling";
    }

    @Override
    public String parentId() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SECompilingCustomizerPanel.class, "Compiling.displayName");
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("javac.source", "Compiling.source"),
                new Field("javac.target", "Compiling.target"));
    }
}
