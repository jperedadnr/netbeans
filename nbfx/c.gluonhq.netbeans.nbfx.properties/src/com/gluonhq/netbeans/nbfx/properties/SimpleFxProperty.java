package com.gluonhq.netbeans.nbfx.properties;

import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

/**
 * A simple {@link FxProperty} backed by a JavaFX property. Convenience for providers and tests.
 */
public final class SimpleFxProperty implements FxProperty {

    private final String name;
    private final String displayName;
    private final String shortDescription;
    private final Class<?> valueType;
    private final boolean writable;
    private final boolean expert;
    private final Object defaultValue;
    private final ObjectProperty<Object> value;

    public SimpleFxProperty(String name, String displayName, Class<?> valueType, Object value) {
        this(name, displayName, null, valueType, value, true, false, value);
    }

    public SimpleFxProperty(String name, String displayName, String shortDescription, Class<?> valueType,
            Object value, boolean writable, boolean expert, Object defaultValue) {
        this.name = name;
        this.displayName = displayName;
        this.shortDescription = shortDescription;
        this.valueType = valueType;
        this.writable = writable;
        this.expert = expert;
        this.defaultValue = defaultValue;
        this.value = new SimpleObjectProperty<>(this, name, value);
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
    public String getShortDescription() {
        return shortDescription;
    }

    @Override
    public Class<?> getValueType() {
        return valueType;
    }

    @Override
    public Object getValue() {
        return value.get();
    }

    @Override
    public void setValue(Object value) {
        if (writable) {
            this.value.set(value);
        }
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public boolean isExpert() {
        return expert;
    }

    @Override
    public boolean isDefaultValue() {
        return Objects.equals(value.get(), defaultValue);
    }

    @Override
    public ObservableValue<Object> valueObservable() {
        return value;
    }
}
