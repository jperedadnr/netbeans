package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Build / Packaging" category: the output jar. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SEPackagingCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "build.packaging";
    }

    @Override
    public String parentId() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SEPackagingCustomizerPanel.class, "Packaging.displayName");
    }

    @Override
    public int position() {
        return 200;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("dist.jar", "Packaging.distJar"));
    }
}
