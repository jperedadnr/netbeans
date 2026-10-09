package com.gluonhq.netbeans.nbfx.api.actions;

import java.util.Map;
import javafx.scene.input.KeyCombination;

/**
 * The user-configurable keyboard map: it maps a command id to the accelerator the user chose,
 * overriding the command's built-in default. Resolved through the global
 * {@link org.openide.util.Lookup}; when no implementation is registered, commands keep their
 * defaults.
 *
 * @since 1.0
 */
public interface KeyMap {

    /**
     * The accelerator to use for {@code actionId}: the user's override when one is set (an empty
     * override means the user removed the shortcut), otherwise {@code defaultAccelerator}.
     */
    KeyCombination accelerator(String actionId, KeyCombination defaultAccelerator);

    /**
     * Sets the user's accelerator for {@code actionId}. A {@code null} accelerator records that the
     * user removed the shortcut (as opposed to having no override at all).
     */
    void setAccelerator(String actionId, KeyCombination accelerator);

    /** Removes any user override for {@code actionId}, restoring the command's default shortcut. */
    void reset(String actionId);

    /** The user's overrides, action id to accelerator (a {@code null} value means "removed"). */
    Map<String, KeyCombination> overrides();
}
