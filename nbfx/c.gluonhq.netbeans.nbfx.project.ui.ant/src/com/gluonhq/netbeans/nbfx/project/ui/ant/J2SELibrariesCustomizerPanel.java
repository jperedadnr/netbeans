package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Libraries" category: the compile and run classpath entries. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SELibrariesCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "libraries";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SELibrariesCustomizerPanel.class, "Libraries.displayName");
    }

    @Override
    public int position() {
        return 200;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("javac.classpath", "Libraries.compileClasspath"),
                new Field("run.classpath", "Libraries.runClasspath"));
    }
}
