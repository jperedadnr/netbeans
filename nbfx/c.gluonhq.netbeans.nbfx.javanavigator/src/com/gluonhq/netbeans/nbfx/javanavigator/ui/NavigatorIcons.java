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
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.api.elements.ElementIcons;
import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import com.gluonhq.netbeans.nbfx.api.elements.SourceTypeKind;
import com.gluonhq.netbeans.nbfx.javanavigator.model.BeanPattern;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.lang.model.element.ElementKind;

/**
 * The icons of the Navigator: its tool bar glyphs copied from NetBeans' {@code java.navigation}
 * module (in {@code icons/}) and the element glyphs shared through {@link ElementIcons}.
 */
public final class NavigatorIcons {

    static final double SIZE = 16;

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private NavigatorIcons() {
    }

    /** The (cached) image {@code icons/<name>.png}. */
    public static Image image(String name) {
        return CACHE.computeIfAbsent(name, n -> new Image(Objects.requireNonNull(
                NavigatorIcons.class.getResource("icons/" + n + ".png"), () -> "Missing icon " + n).toExternalForm()));
    }

    /**
     * The icon of the Navigator window at 16 pixels, for the Window-menu entry: the macOS system
     * menu bar draws an image at its own size, whatever the view's fit size, so the menu needs the
     * small file itself. The tab's icon ({@code icons/navigator.png}, 64 pixels scaled down) is
     * declared by the view's registration instead.
     */
    public static final String NAVIGATOR_MENU = "navigator-16";

    /** A fixed-size view of the icon {@code icons/<name>.png}. */
    public static ImageView view(String name) {
        ImageView view = new ImageView(image(name));
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(true);
        return view;
    }

    /** The element glyph of {@code node}: its kind in its visibility / static variant, or {@code null} for kinds without one. */
    static ImageView elementIcon(MemberNode node) {
        return elementIcon(node.getKind(), node.getModifiers());
    }

    /** The image of a bean pattern: its kind and accessors, the class / interface glyph for a type. */
    static Image patternImage(BeanPattern pattern) {
        return switch (pattern.getKind()) {
            case CLASS -> ElementIcons.iconFor(SourceElementKind.TYPE,
                    pattern.isInterface() ? SourceTypeKind.INTERFACE : SourceTypeKind.CLASS, Modifier.PUBLIC);
            case PROPERTY -> image("property" + modeSuffix(pattern.getMode()));
            case INDEXED_PROPERTY -> image("propertyIndexed" + modeSuffix(pattern.getMode()));
            case EVENT_SET -> image(pattern.isUnicast() ? "eventSetUnicast" : "eventSetMulticast");
        };
    }

    private static String modeSuffix(BeanPattern.Mode mode) {
        return switch (mode) {
            case READ_ONLY -> "RO";
            case WRITE_ONLY -> "WO";
            case READ_WRITE -> "RW";
        };
    }

    /** The (shared) image of {@code node}'s element glyph, for a view that is reused; {@code null} for kinds without one. */
    static Image elementImage(MemberNode node) {
        SourceElementKind sourceKind = sourceKind(node.getKind());
        return sourceKind == null ? null : ElementIcons.iconFor(sourceKind, typeKind(node.getKind()), node.getModifiers());
    }

    /** The element glyph of a {@code kind} with {@code modifiers}, or {@code null} for kinds without one. */
    static Image elementImage(ElementKind kind, int modifiers) {
        SourceElementKind sourceKind = sourceKind(kind);
        return sourceKind == null ? null : ElementIcons.iconFor(sourceKind, typeKind(kind), modifiers);
    }

    static ImageView elementIcon(ElementKind kind, int modifiers) {
        SourceElementKind sourceKind = sourceKind(kind);
        return sourceKind == null ? null : ElementIcons.iconViewFor(sourceKind, typeKind(kind), modifiers);
    }

    private static SourceElementKind sourceKind(ElementKind kind) {
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> SourceElementKind.TYPE;
            case METHOD, CONSTRUCTOR -> SourceElementKind.METHOD;
            case FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> SourceElementKind.FIELD;
            case PACKAGE -> SourceElementKind.PACKAGE;
            case MODULE -> SourceElementKind.MODULE;
            default -> null;
        };
    }

    private static SourceTypeKind typeKind(ElementKind kind) {
        return switch (kind) {
            case CLASS -> SourceTypeKind.CLASS;
            case INTERFACE -> SourceTypeKind.INTERFACE;
            case ENUM -> SourceTypeKind.ENUM;
            case RECORD -> SourceTypeKind.RECORD;
            case ANNOTATION_TYPE -> SourceTypeKind.ANNOTATION;
            case CONSTRUCTOR -> SourceTypeKind.CONSTRUCTOR;
            default -> SourceTypeKind.OTHER;
        };
    }
}
