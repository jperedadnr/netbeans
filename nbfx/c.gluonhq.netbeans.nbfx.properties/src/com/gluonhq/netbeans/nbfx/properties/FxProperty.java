package com.gluonhq.netbeans.nbfx.properties;

import javafx.beans.value.ObservableValue;

/**
 * One row in a {@link FxPropertySheet}, mirroring the shape of an {@code org.openide.nodes.Node.Property}
 * but for JavaFX. Implementations are usually simple value holders; the sheet edits them through a
 * registered {@link FxPropertyEditor}.
 */
public interface FxProperty {

    /** A stable name, unique within its property set. */
    String getName();

    /** The display name shown in the sheet. */
    String getDisplayName();

    /** A short help text, or {@code null}. */
    String getShortDescription();

    /** The value's type, used to pick an editor. */
    Class<?> getValueType();

    /** The current value, or {@code null}. */
    Object getValue();

    /** Sets the value; ignored when {@link #isWritable()} is {@code false}. */
    void setValue(Object value);

    /** Whether the value can be edited. */
    boolean isWritable();

    /** Whether the property is only shown with the expert toggle on. */
    boolean isExpert();

    /** Whether the value currently equals the property's default. */
    boolean isDefaultValue();

    /** The value as an observable, so the sheet can react to changes. */
    ObservableValue<Object> valueObservable();
}
