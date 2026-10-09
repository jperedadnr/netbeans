package com.gluonhq.netbeans.nbfx.properties;

import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKindProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKinds;
import java.util.List;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes a read-only "General" property set for a {@link Project}: name, type and location.
 */
@ServiceProvider(service = FxPropertiesProvider.class)
public final class ProjectPropertiesProvider implements FxPropertiesProvider {

    @Override
    public List<FxPropertySet> getPropertySets(Object context) {
        if (!(context instanceof Project project)) {
            return List.of();
        }
        ProjectKindProvider kind = ProjectKinds.providerOf(project);
        List<FxProperty> properties = List.of(
                readOnly("name", message("ProjectPropertiesProvider.name"),
                        ProjectKinds.getProjectName(project, kind)),
                readOnly("type", message("ProjectPropertiesProvider.type"), kind.id()),
                readOnly("location", message("ProjectPropertiesProvider.location"),
                        project.getProjectDirectory().getPath()));
        return List.of(new SimpleFxPropertySet("general",
                message("ProjectPropertiesProvider.general"), properties));
    }

    private static FxProperty readOnly(String name, String displayName, Object value) {
        return new SimpleFxProperty(name, displayName, null, String.class, value, false, false, value);
    }

    private static String message(String key) {
        return NbBundle.getMessage(ProjectPropertiesProvider.class, key);
    }
}
