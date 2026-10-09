package com.gluonhq.netbeans.nbfx.properties;

import java.util.List;

/**
 * Contributes the property sets shown for a context object, mirroring {@code Node.getPropertySets()}.
 * Registered via {@code @ServiceProvider}; the {@link FxPropertySheet} asks every provider for the
 * context it is showing.
 */
public interface FxPropertiesProvider {

    /** The property sets for {@code context}, or an empty list when the provider has nothing to offer. */
    List<FxPropertySet> getPropertySets(Object context);
}
