package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/** J2SE "Build / Documenting" category: javadoc options. */
@ServiceProvider(service = FxProjectCustomizerPanel.class)
public final class J2SEDocumentingCustomizerPanel extends J2SEPropertiesCustomizerPanel {

    @Override
    public String projectTypeId() {
        return "ant";
    }

    @Override
    public String id() {
        return "build.documenting";
    }

    @Override
    public String parentId() {
        return "build";
    }

    @Override
    public String displayName() {
        return NbBundle.getMessage(J2SEDocumentingCustomizerPanel.class, "Documenting.displayName");
    }

    @Override
    public int position() {
        return 300;
    }

    @Override
    protected List<Field> fields() {
        return fields(new Field("javadoc.window.title", "Documenting.windowTitle"),
                new Field("javadoc.author", "Documenting.author"),
                new Field("javadoc.version", "Documenting.version"));
    }
}
