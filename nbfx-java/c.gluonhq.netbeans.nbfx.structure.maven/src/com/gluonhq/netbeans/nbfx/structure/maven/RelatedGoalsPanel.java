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
package com.gluonhq.netbeans.nbfx.structure.maven;

import com.gluonhq.netbeans.nbfx.structure.FxStructurePanel;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TrackedStructurePanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The <em>Related goals</em> panel of the Navigator window for Maven POMs, after NetBeans'
 * {@code GoalsNavigationPanel}.
 */
@ServiceProvider(service = FxStructurePanel.class, position = 120)
public final class RelatedGoalsPanel extends TrackedStructurePanel<RelatedGoalsUI.Result> {

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(RelatedGoalsPanel.class, "GOALS_NAME");
    }

    @Override
    public String[] contentTypes() {
        return new String[] {PomModelPanel.POM_MIME_TYPE};
    }

    @Override
    protected ScanningPanel<RelatedGoalsUI.Result> createUI() {
        return new RelatedGoalsUI(() -> tracker().rescan());
    }
}
