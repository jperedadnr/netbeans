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
package com.gluonhq.netbeans.nbfx.structure;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Loads and caches the icons of the Navigator panels, 16 px PNG resources next to a panel's classes.
 *
 * @since 1.0
 */
public final class NavigatorIcons {

    /** The size the icons are shown at. */
    public static final double SIZE = 16;

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private NavigatorIcons() {
    }

    /** The (cached) image of the resource {@code name}, resolved against {@code owner}'s package. */
    public static Image image(Class<?> owner, String name) {
        URL url = Objects.requireNonNull(owner.getResource(name), () -> "Missing icon " + name + " next to " + owner.getName());
        return CACHE.computeIfAbsent(url.toExternalForm(), Image::new);
    }

    /** A fixed-size view of the resource {@code name}, resolved against {@code owner}'s package. */
    public static ImageView view(Class<?> owner, String name) {
        ImageView view = new ImageView(image(owner, name));
        view.setFitWidth(SIZE);
        view.setFitHeight(SIZE);
        view.setPreserveRatio(true);
        return view;
    }
}
