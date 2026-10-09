package com.gluonhq.netbeans.nbfx.properties;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import org.openide.util.lookup.ServiceProvider;

/** The editor for {@link Enum} values, shown as a choice box. */
@ServiceProvider(service = FxPropertyEditor.class)
public final class EnumPropertyEditor implements FxPropertyEditor {

    @Override
    public Class<?> valueType() {
        return Enum.class;
    }

    @Override
    public Node createEditor(FxProperty property) {
        ComboBox<Object> box = new ComboBox<>(FXCollections.observableArrayList(property.getValueType().getEnumConstants()));
        box.setValue(property.getValue());
        box.setDisable(!property.isWritable());
        box.valueProperty().addListener((observable, old, now) -> {
            if (property.isWritable()) {
                property.setValue(now);
            }
        });
        return box;
    }
}
