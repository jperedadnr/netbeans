package com.gluonhq.netbeans.nbfx.project.customizer;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKinds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.netbeans.api.project.Project;
import org.openide.util.Lookup;

/**
 * Collects the {@link FxProjectCustomizerPanel}s that apply to a project: those registered for the
 * project's kind (its {@link ProjectKinds#providerOf(Project) provider id}) plus the type-agnostic
 * ones, ordered by {@link FxProjectCustomizerPanel#position()}.
 */
public final class FxProjectCustomizers {

    private FxProjectCustomizers() {
    }

    public static List<FxProjectCustomizerPanel> panelsFor(Project project) {
        String kind = ProjectKinds.providerOf(project).id();
        List<FxProjectCustomizerPanel> panels = new ArrayList<>();
        for (FxProjectCustomizerPanel panel : Lookup.getDefault().lookupAll(FxProjectCustomizerPanel.class)) {
            if (panel.appliesTo(project, kind)) {
                panels.add(panel);
            }
        }
        panels.sort(Comparator.comparingInt(FxProjectCustomizerPanel::position));
        return panels;
    }
}
