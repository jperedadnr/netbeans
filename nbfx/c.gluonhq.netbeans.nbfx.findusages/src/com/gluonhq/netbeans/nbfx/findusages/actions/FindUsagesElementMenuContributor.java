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
package com.gluonhq.netbeans.nbfx.findusages.actions;

import com.gluonhq.netbeans.nbfx.api.actions.ElementContextMenuContributor;
import com.gluonhq.netbeans.nbfx.api.elements.SourceLocation;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.scene.control.MenuItem;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * "Find Usages" on a member of the Navigator: searches the usages of the element declared at the
 * row's location once it is known, as NetBeans does on a member node.
 */
@ServiceProvider(service = ElementContextMenuContributor.class)
public final class FindUsagesElementMenuContributor implements ElementContextMenuContributor {

    @Override
    public List<MenuItem> itemsFor(CompletableFuture<SourceLocation> location) {
        MenuItem item = new MenuItem(NbBundle.getMessage(FindUsagesElementMenuContributor.class, "CTL_FindUsagesCommand"));
        // Shown for consistency with the editor's item; the key itself is bound to the editor command.
        item.setAccelerator(FindUsagesCommand.SHORTCUT);
        item.setOnAction(e -> {
            UsagesModel model = Lookup.getDefault().lookup(UsagesModel.class);
            if (model == null) {
                return;
            }
            String noTarget = NbBundle.getMessage(FindUsagesElementMenuContributor.class, "ERR_NoElementAtCaret");
            FindUsagesCommand.show(location.thenCompose(declaration -> declaration == null
                    ? CompletableFuture.completedFuture(null)
                    : model.findUsages(declaration.file(), declaration.offset())), noTarget);
        });
        return List.of(item);
    }
}
