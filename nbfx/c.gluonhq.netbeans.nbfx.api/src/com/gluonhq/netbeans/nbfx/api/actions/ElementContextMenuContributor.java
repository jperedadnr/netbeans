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
package com.gluonhq.netbeans.nbfx.api.actions;

import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.scene.control.MenuItem;

/**
 * Adds items to the context menu of a source element shown outside the editor - a row of the
 * Navigator's Members tree. Implementations are registered in the default Lookup
 * ({@code @ServiceProvider}) by the module that owns the feature (e.g. Find Usages), so the view
 * does not depend on it. The view asks every contributor each time a menu opens, on the JavaFX
 * thread, and places the items - in the order returned, contributors in Lookup order - after its
 * own "Go to Source".
 */
public interface ElementContextMenuContributor {

    /**
     * The items for the element declared at {@code location}, or an empty list when the contributor
     * has nothing to offer. The location is a future because the view may still have to find the
     * declaration - an inherited member is declared in another file -; it completes on no particular
     * thread, with {@code null} when the declaration cannot be found (a library class without
     * sources). An item acts once it has completed. A fresh list of new items is expected on every call.
     */
    List<MenuItem> itemsFor(CompletableFuture<SourceLocation> location);
}
