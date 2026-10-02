package com.gluonhq.netbeans.nbfx.findusages.model;

import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * One declaration enclosing a usage, outermost first in {@link UsageContext#enclosing()}: a type,
 * a method or constructor, a field or an initializer.
 *
 * @param kind      the declaration's kind
 * @param name      the display name: the simple type name, {@code method(ParamType, ...)} for
 *                  executables, the field name
 * @param modifiers the declaration's modifiers, for its icon
 */
public record EnclosingElement(ElementKind kind, String name, Set<Modifier> modifiers) {
}
