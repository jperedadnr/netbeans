package com.gluonhq.netbeans.nbfx.properties;

import java.util.List;

/**
 * A named group of {@link FxProperty properties}, shown as a branch in the {@link FxPropertySheet}.
 */
public interface FxPropertySet {

    /** A stable name, unique among the sets of a context. */
    String getName();

    /** The display name shown for the group. */
    String getDisplayName();

    /** The properties in this group. */
    List<FxProperty> getProperties();
}
