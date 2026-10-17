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
package com.gluonhq.netbeans.nbfx.structure.gradle;

import com.gluonhq.netbeans.nbfx.structure.FxStructurePanel;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TrackedStructurePanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The <em>Related tasks</em> panel of the Navigator window for Gradle build scripts, after
 * NetBeans' {@code TasksNavigatorPanel}: the tasks of the script's project, by group.
 */
@ServiceProvider(service = FxStructurePanel.class, position = 100)
public final class GradleTasksPanel extends TrackedStructurePanel<GradleTasksUI.Result> {

    /** The MIME type NetBeans gives a Groovy build script ({@code build.gradle}). */
    public static final String GROOVY_MIME_TYPE = "text/x-gradle+x-groovy";
    /** The MIME type NetBeans gives a Kotlin build script ({@code build.gradle.kts}). */
    public static final String KOTLIN_MIME_TYPE = "text/x-gradle+x-kotlin";

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(GradleTasksPanel.class, "TASKS_NAME");
    }

    @Override
    public String[] contentTypes() {
        return new String[] {GROOVY_MIME_TYPE, KOTLIN_MIME_TYPE};
    }

    @Override
    protected ScanningPanel<GradleTasksUI.Result> createUI() {
        return new GradleTasksUI(() -> tracker().rescan());
    }
}
