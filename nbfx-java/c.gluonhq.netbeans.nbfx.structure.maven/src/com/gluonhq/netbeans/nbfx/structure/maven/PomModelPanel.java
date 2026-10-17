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
 * The <em>POM model</em> panel of the Navigator window for Maven POMs ({@code text/x-maven-pom+xml}),
 * after NetBeans' {@code POMModelNavigator}: the first of the POM panels, before
 * <em>POM inheritance</em>, <em>Related goals</em> and the <em>XML View</em>.
 */
@ServiceProvider(service = FxStructurePanel.class, position = 100)
public final class PomModelPanel extends TrackedStructurePanel<PomModelUI.Result> {

    /** The content type of Maven POMs, as the Maven support resolves them. */
    static final String POM_MIME_TYPE = "text/x-maven-pom+xml";

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(PomModelPanel.class, "POM_MODEL_NAME");
    }

    @Override
    public String[] contentTypes() {
        return new String[] {POM_MIME_TYPE};
    }

    @Override
    protected ScanningPanel<PomModelUI.Result> createUI() {
        return new PomModelUI();
    }
}
