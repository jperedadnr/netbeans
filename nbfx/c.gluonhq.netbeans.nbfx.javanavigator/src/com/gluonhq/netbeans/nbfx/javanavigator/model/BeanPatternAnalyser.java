/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.javanavigator.model;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.javanavigator.model.BeanPattern.Kind;
import com.gluonhq.netbeans.nbfx.javanavigator.model.BeanPattern.Mode;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.TreeUtilities;

/**
 * Finds the JavaBeans patterns of a type the way {@code java.beans.Introspector} does for a class
 * without a {@code BeanInfo}, ported from {@code PatternAnalyser} of NetBeans' {@code beans} module:
 * <ul>
 *   <li>a <b>property</b> from a public {@code getX()} / boolean {@code isX()} and/or {@code setX(T)},
 *       the two agreeing on the type; a {@code boolean} {@code isX} wins over {@code getX};</li>
 *   <li>an <b>indexed property</b> from {@code getX(int)} / {@code setX(int, T)}, which may go with
 *       a plain {@code getX()} / {@code setX(T[])} of the array type;</li>
 *   <li>an <b>event set</b> from a {@code addXListener(XListener)} / {@code removeXListener(XListener)}
 *       pair whose listener type extends {@code java.util.EventListener}, unicast when the add
 *       method throws {@code TooManyListenersException}.</li>
 * </ul>
 * Static methods are skipped; only the methods the type itself declares count. A method that looks
 * like a pattern but breaks its rules (a getter and a setter of different types) is dropped, as the
 * Introspector drops it.
 */
final class BeanPatternAnalyser {

    private static final Logger LOG = Logger.getLogger(BeanPatternAnalyser.class.getName());
    private static final String GET = "get";
    private static final String SET = "set";
    private static final String IS = "is";
    private static final String ADD = "add";
    private static final String REMOVE = "remove";

    /** A method that does not form the pattern it looked like. */
    private static final class InvalidPattern extends Exception {

        InvalidPattern(String message) {
            super(message);
        }
    }

    private final Trees trees;
    private final SourcePositions sourcePositions;
    private final TreeUtilities treeUtilities;
    private final Types types;
    private final TypeMirror eventListener;
    private final TypeMirror tooManyListeners;
    private final Cancellation cancellation;

    BeanPatternAnalyser(CompilationController controller, Cancellation cancellation) {
        this.trees = controller.getTrees();
        this.sourcePositions = trees.getSourcePositions();
        this.treeUtilities = controller.getTreeUtilities();
        this.types = controller.getTypes();
        this.eventListener = typeOf(controller, "java.util.EventListener");
        this.tooManyListeners = typeOf(controller, "java.util.TooManyListenersException");
        this.cancellation = cancellation;
    }

    private static TypeMirror typeOf(CompilationController controller, String name) {
        TypeElement element = controller.getElements().getTypeElement(name);
        return element == null ? null : element.asType();
    }

    /** The row of {@code type} with its patterns and inner types under it. */
    BeanPattern analyse(TypeElement type) {
        Span span = spanOf(type);
        BeanPattern row = new BeanPattern(Kind.CLASS, type.getSimpleName().toString(), null, null, null, false,
                type.getKind() == ElementKind.INTERFACE, span.start(), span.end(), span.name());
        Map<String, Property> properties = new LinkedHashMap<>();
        Map<String, IndexedProperty> indexed = new LinkedHashMap<>();
        Map<String, EventSet> eventSets = new LinkedHashMap<>();
        Map<String, ExecutableElement> adds = new LinkedHashMap<>();
        Map<String, ExecutableElement> removes = new LinkedHashMap<>();

        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (cancellation.isCancelled()) {
                return row;
            }
            if (!method.getModifiers().contains(Modifier.PUBLIC) || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            String name = method.getSimpleName().toString();
            if (startsWithPrefix(name, GET) || startsWithPrefix(name, SET) || startsWithPrefix(name, IS)) {
                try {
                    Property property = propertyOf(method);
                    if (property != null) {
                        addProperty(properties, indexed, property);
                    }
                } catch (InvalidPattern ex) {
                    LOG.log(Level.FINE, "{0}: {1}", new Object[] {method, ex.getMessage()});
                }
            }
            if (startsWithPrefix(name, ADD) || startsWithPrefix(name, REMOVE)) {
                collectListenerMethod(method, adds, removes);
            }
        }
        for (Map.Entry<String, ExecutableElement> entry : adds.entrySet()) {
            ExecutableElement remove = removes.get(entry.getKey());
            if (remove == null || entry.getKey().indexOf("Listener:") <= 0) {
                continue;
            }
            TypeMirror listener = entry.getValue().getParameters().get(0).asType();
            if (listener.getKind() != TypeKind.DECLARED || eventListener == null || !types.isSubtype(listener, eventListener)) {
                continue;
            }
            EventSet eventSet = new EventSet(entry.getValue(), remove);
            // A later pair of the same name and listener type replaces the earlier one.
            eventSets.put(eventSet.name + ":" + eventSet.listenerName(), eventSet);
        }

        properties.values().stream().sorted(Comparator.comparing(p -> p.name))
                .forEach(property -> row.addChild(property.toPattern()));
        indexed.values().stream().sorted(Comparator.comparing(p -> p.name))
                .forEach(property -> row.addChild(property.toPattern()));
        eventSets.values().stream().sorted(Comparator.comparing(e -> e.name))
                .forEach(eventSet -> row.addChild(eventSet.toPattern()));
        for (TypeElement inner : ElementFilter.typesIn(type.getEnclosedElements())) {
            if (inner.getKind() == ElementKind.CLASS || inner.getKind() == ElementKind.INTERFACE) {
                row.addChild(analyse(inner));
            }
        }
        return row;
    }

    private static boolean startsWithPrefix(String name, String prefix) {
        return name.startsWith(prefix) && name.length() > prefix.length();
    }

    // -- properties ------------------------------------------------------------------------------

    /** The property or indexed property {@code method} is an accessor of, by its shape; {@code null} when it is none. */
    private Property propertyOf(ExecutableElement method) throws InvalidPattern {
        String name = method.getSimpleName().toString();
        List<? extends VariableElement> params = method.getParameters();
        TypeKind returnKind = method.getReturnType().getKind();
        switch (params.size()) {
            case 0 -> {
                if (name.startsWith(GET) || (returnKind == TypeKind.BOOLEAN && name.startsWith(IS))) {
                    return new Property(method, null);
                }
            }
            case 1 -> {
                if (params.get(0).asType().getKind() == TypeKind.INT) {
                    if (name.startsWith(GET) || (returnKind == TypeKind.BOOLEAN && name.startsWith(IS))) {
                        return new IndexedProperty(null, null, method, null);
                    }
                    if (returnKind == TypeKind.VOID && name.startsWith(SET)) {
                        return new Property(null, method);
                    }
                } else if (returnKind == TypeKind.VOID && name.startsWith(SET)) {
                    return new Property(null, method);
                }
            }
            case 2 -> {
                if (params.get(0).asType().getKind() == TypeKind.INT && name.startsWith(SET)) {
                    return new IndexedProperty(null, null, null, method);
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** Adds {@code property}, merging it with one of the same name: a new type replaces, otherwise the accessors combine. */
    private void addProperty(Map<String, Property> properties, Map<String, IndexedProperty> indexed, Property property)
            throws InvalidPattern {
        boolean isIndexed = property instanceof IndexedProperty;
        Property old = properties.get(property.name);
        if (old == null) {
            old = indexed.get(property.name);
        }
        if (old == null) {
            put(properties, indexed, property);
            return;
        }
        if (old.type != null && property.type != null && !types.isSameType(old.type, property.type)) {
            put(properties, indexed, property);
            return;
        }
        boolean wasIndexed = old instanceof IndexedProperty;
        if (isIndexed || wasIndexed) {
            if (isIndexed && !wasIndexed) {
                properties.remove(old.name);
            } else if (!isIndexed && wasIndexed) {
                indexed.remove(old.name);
            }
            indexed.put(property.name, new IndexedProperty(old, property));
        } else {
            properties.put(property.name, new Property(old, property));
        }
    }

    private static void put(Map<String, Property> properties, Map<String, IndexedProperty> indexed, Property property) {
        if (property instanceof IndexedProperty indexedProperty) {
            indexed.put(property.name, indexedProperty);
        } else {
            properties.put(property.name, property);
        }
    }

    /** A property being assembled from its accessors, after {@code TmpPattern.Property} of NetBeans. */
    private class Property {

        ExecutableElement getter;
        ExecutableElement setter;
        TypeMirror type;
        String name;

        Property(ExecutableElement getter, ExecutableElement setter) throws InvalidPattern {
            this.getter = getter;
            this.setter = setter;
            type = propertyType();
            name = propertyName();
        }

        /** Merges {@code x} and {@code y}, {@code y} winning where they conflict. */
        Property(Property x, Property y) throws InvalidPattern {
            getter = y.getter != null ? y.getter : x.getter;
            // Both read the same boolean in the same class: the "is" method wins over the "get" one.
            if (x.getter != null && y.getter != null
                    && x.getter.getEnclosingElement().equals(y.getter.getEnclosingElement())
                    && x.getter.getReturnType().getKind() == TypeKind.BOOLEAN
                    && y.getter.getReturnType().getKind() == TypeKind.BOOLEAN
                    && x.getter.getSimpleName().toString().startsWith(IS)
                    && y.getter.getSimpleName().toString().startsWith(GET)) {
                getter = x.getter;
            }
            setter = y.setter != null ? y.setter : x.setter;
            type = propertyType();
            name = propertyName();
        }

        /** The type the getter and the setter agree on. */
        private TypeMirror propertyType() throws InvalidPattern {
            TypeMirror resolved = null;
            if (getter != null) {
                if (!getter.getParameters().isEmpty()) {
                    throw new InvalidPattern("bad read method arg count");
                }
                resolved = getter.getReturnType();
                if (resolved.getKind() == TypeKind.VOID) {
                    throw new InvalidPattern("read method returns void");
                }
            }
            if (setter != null) {
                List<? extends VariableElement> params = setter.getParameters();
                if (params.size() != 1) {
                    throw new InvalidPattern("bad write method arg count");
                }
                TypeMirror param = params.get(0).asType();
                if (resolved != null && !types.isSameType(resolved, param)) {
                    throw new InvalidPattern("type mismatch between read and write methods");
                }
                resolved = param;
            }
            return resolved;
        }

        /** The name after the accessor's prefix, decapitalised, or {@code null} without an accessor. */
        String propertyName() {
            ExecutableElement accessor = getter != null ? getter : setter;
            if (accessor == null) {
                return null;
            }
            String method = accessor.getSimpleName().toString();
            return decapitalize(method.substring(method.startsWith(IS) ? 2 : 3));
        }

        Mode mode() {
            if (getter != null && setter != null) {
                return Mode.READ_WRITE;
            }
            return getter != null ? Mode.READ_ONLY : Mode.WRITE_ONLY;
        }

        List<ExecutableElement> methods() {
            List<ExecutableElement> methods = new ArrayList<>();
            if (getter != null) {
                methods.add(getter);
            }
            if (setter != null) {
                methods.add(setter);
            }
            return methods;
        }

        BeanPattern toPattern() {
            Span span = spanOf(methods());
            return new BeanPattern(Kind.PROPERTY, name, typeName(type), null, mode(), false, false,
                    span.start(), span.end(), span.name());
        }
    }

    /** An indexed property being assembled, after {@code TmpPattern.IdxProperty} of NetBeans. */
    private final class IndexedProperty extends Property {

        ExecutableElement indexedGetter;
        ExecutableElement indexedSetter;
        TypeMirror indexedType;

        IndexedProperty(ExecutableElement getter, ExecutableElement setter,
                ExecutableElement indexedGetter, ExecutableElement indexedSetter) throws InvalidPattern {
            super(getter, setter);
            this.indexedGetter = indexedGetter;
            this.indexedSetter = indexedSetter;
            indexedType = indexedPropertyType();
            if (type == null && indexedType != null) {
                type = types.getArrayType(indexedType);
            }
            name = indexedPropertyName();
        }

        IndexedProperty(Property x, Property y) throws InvalidPattern {
            super(x, y);
            if (x instanceof IndexedProperty ix) {
                indexedGetter = ix.indexedGetter;
                indexedSetter = ix.indexedSetter;
                indexedType = ix.indexedType;
                type = type == null ? ix.type : type;
            }
            if (y instanceof IndexedProperty iy) {
                if (iy.indexedGetter != null) {
                    indexedGetter = iy.indexedGetter;
                }
                if (iy.indexedSetter != null) {
                    indexedSetter = iy.indexedSetter;
                }
                indexedType = iy.indexedType;
                type = type == null ? iy.type : type;
            }
            name = indexedPropertyName();
        }

        /** The element type the indexed accessors agree on, which the array type must match. */
        private TypeMirror indexedPropertyType() throws InvalidPattern {
            TypeMirror resolved = null;
            if (indexedGetter != null) {
                List<? extends VariableElement> params = indexedGetter.getParameters();
                if (params.size() != 1) {
                    throw new InvalidPattern("bad indexed read method arg count");
                }
                if (params.get(0).asType().getKind() != TypeKind.INT) {
                    throw new InvalidPattern("not int index to indexed read method");
                }
                resolved = indexedGetter.getReturnType();
                if (resolved.getKind() == TypeKind.VOID) {
                    throw new InvalidPattern("indexed read method returns void");
                }
            }
            if (indexedSetter != null) {
                List<? extends VariableElement> params = indexedSetter.getParameters();
                if (params.size() != 2) {
                    throw new InvalidPattern("bad indexed write method arg count");
                }
                if (params.get(0).asType().getKind() != TypeKind.INT) {
                    throw new InvalidPattern("non int index to indexed write method");
                }
                TypeMirror element = params.get(1).asType();
                if (resolved != null && !types.isSameType(resolved, element)) {
                    throw new InvalidPattern("type mismatch between indexed read and write methods");
                }
                resolved = element;
            }
            if (type != null && (type.getKind() != TypeKind.ARRAY
                    || !types.isSameType(resolved, ((ArrayType) type).getComponentType()))) {
                throw new InvalidPattern("type mismatch between property type and indexed type");
            }
            return resolved;
        }

        private String indexedPropertyName() {
            String plain = propertyName();
            if (plain != null) {
                return plain;
            }
            ExecutableElement accessor = indexedGetter != null ? indexedGetter : indexedSetter;
            String method = accessor.getSimpleName().toString();
            return decapitalize(method.substring(method.startsWith(IS) ? 2 : 3));
        }

        @Override
        Mode mode() {
            if (indexedGetter != null && indexedSetter != null) {
                return Mode.READ_WRITE;
            }
            if (indexedGetter != null) {
                return Mode.READ_ONLY;
            }
            return indexedSetter != null ? Mode.WRITE_ONLY : super.mode();
        }

        @Override
        List<ExecutableElement> methods() {
            List<ExecutableElement> methods = new ArrayList<>();
            if (indexedGetter != null) {
                methods.add(indexedGetter);
            }
            if (indexedSetter != null) {
                methods.add(indexedSetter);
            }
            methods.addAll(super.methods());
            return methods;
        }

        @Override
        BeanPattern toPattern() {
            Span span = spanOf(methods());
            return new BeanPattern(Kind.INDEXED_PROPERTY, name, typeName(type), typeName(indexedType), mode(),
                    false, false, span.start(), span.end(), span.name());
        }
    }

    // -- event sets ------------------------------------------------------------------------------

    /** Records {@code method} as the add or remove half of an event set, keyed by {@code XListener:XListener}. */
    private void collectListenerMethod(ExecutableElement method, Map<String, ExecutableElement> adds,
            Map<String, ExecutableElement> removes) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() != 1 || method.getReturnType().getKind() != TypeKind.VOID) {
            return;
        }
        TypeMirror param = params.get(0).asType();
        if (param.getKind() != TypeKind.DECLARED) {
            return;
        }
        String listener = ((DeclaredType) param).asElement().getSimpleName().toString();
        String name = method.getSimpleName().toString();
        if (name.startsWith(ADD) && name.substring(ADD.length()).equals(listener)) {
            adds.put(listener + ":" + listener, method);
        } else if (name.startsWith(REMOVE) && name.substring(REMOVE.length()).equals(listener)) {
            removes.put(listener + ":" + listener, method);
        }
    }

    /** An event set: the add / remove pair of a listener type. */
    private final class EventSet {

        final ExecutableElement add;
        final ExecutableElement remove;
        final String name;

        EventSet(ExecutableElement add, ExecutableElement remove) {
            this.add = add;
            this.remove = remove;
            this.name = decapitalize(add.getSimpleName().toString().substring(ADD.length()));
        }

        TypeMirror listenerType() {
            return add.getParameters().get(0).asType();
        }

        String listenerName() {
            return typeName(listenerType());
        }

        boolean isUnicast() {
            if (tooManyListeners == null) {
                return false;
            }
            for (TypeMirror thrown : add.getThrownTypes()) {
                if (types.isSubtype(thrown, tooManyListeners)) {
                    return true;
                }
            }
            return false;
        }

        BeanPattern toPattern() {
            Span span = spanOf(List.of(add, remove));
            return new BeanPattern(Kind.EVENT_SET, name, listenerName(), null, null, isUnicast(), false,
                    span.start(), span.end(), span.name());
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    /** Where a pattern lies: the span of its declarations, and the name of the one the caret goes to. */
    private record Span(int start, int end, int name) {
    }

    private static final Span NOWHERE = new Span(-1, -1, -1);

    /** The span covering {@code methods}, the caret target being the first one's name. */
    private Span spanOf(List<ExecutableElement> methods) {
        int start = -1;
        int end = -1;
        int name = -1;
        for (ExecutableElement method : methods) {
            Span span = spanOf(method);
            if (span.start() < 0) {
                continue;
            }
            start = start < 0 ? span.start() : Math.min(start, span.start());
            end = Math.max(end, span.end());
            if (name < 0) {
                name = span.name();
            }
        }
        return new Span(start, end, name);
    }

    private Span spanOf(Element element) {
        TreePath path = trees.getPath(element);
        if (path == null) {
            return NOWHERE;
        }
        Tree tree = path.getLeaf();
        int[] nameSpan = tree instanceof ClassTree clazz ? treeUtilities.findNameSpan(clazz)
                : tree instanceof MethodTree method ? treeUtilities.findNameSpan(method) : null;
        int start = (int) sourcePositions.getStartPosition(path.getCompilationUnit(), tree);
        int end = (int) sourcePositions.getEndPosition(path.getCompilationUnit(), tree);
        return new Span(start, end, nameSpan != null ? nameSpan[0] : start);
    }

    /** {@code int}, {@code String}, {@code String[]}: simple names, as NetBeans prints pattern types. */
    private static String typeName(TypeMirror type) {
        if (type == null) {
            return null;
        }
        return switch (type.getKind()) {
            case DECLARED -> ((DeclaredType) type).asElement().getSimpleName().toString();
            case ARRAY -> typeName(((ArrayType) type).getComponentType()) + "[]";
            default -> type.toString();
        };
    }

    /** {@code java.beans.Introspector.decapitalize}: {@code Name} to {@code name}, {@code URL} left alone. */
    static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
