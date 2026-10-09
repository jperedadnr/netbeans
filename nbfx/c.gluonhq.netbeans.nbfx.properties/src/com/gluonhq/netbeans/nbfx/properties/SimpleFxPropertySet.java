package com.gluonhq.netbeans.nbfx.properties;

import java.util.List;

/** A simple {@link FxPropertySet} holding a fixed list of properties. */
public final class SimpleFxPropertySet implements FxPropertySet {

    private final String name;
    private final String displayName;
    private final List<FxProperty> properties;

    public SimpleFxPropertySet(String name, String displayName, List<FxProperty> properties) {
        this.name = name;
        this.displayName = displayName;
        this.properties = List.copyOf(properties);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public List<FxProperty> getProperties() {
        return properties;
    }
}
