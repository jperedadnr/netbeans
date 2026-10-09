package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Sources" category: the main and test source directories. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SESourcesCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "sources";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SESourcesCustomizerPanel.class, "Sources.displayName");
    }

    @Override
    public int position() {
        return 100;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("src.dir", "Sources.srcDir"),
                new Field("test.src.dir", "Sources.testSrcDir"));
    }
}
