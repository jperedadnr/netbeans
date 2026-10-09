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
package com.gluonhq.netbeans.nbfx.project.ui.ant;

import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizerPanel;
import com.gluonhq.netbeans.nbfx.project.customizer.FxProjectCustomizers;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import junit.framework.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/** Verifies that the apisupport customizer panels apply to a real apisupport project. */
public class AntCustomizersTest extends NbTestCase {

    public AntCustomizersTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(AntCustomizersTest.class)
                .clusters(".*")
                .enableModules(".*")
                .gui(false)
                .suite();
    }

    public void testApisupportPanelsApply() throws Exception {
        File dir = new File("nbfx/c.gluonhq.netbeans.nbfx.actions");
        FileObject root = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        assertNotNull("project dir not found: " + dir.getAbsolutePath(), root);
        Project project = ProjectManager.getDefault().findProject(root);
        assertNotNull("no project for " + dir, project);

        List<FxProjectCustomizerPanel> panels = FxProjectCustomizers.panelsFor(project);
        Set<String> ids = panels.stream().map(FxProjectCustomizerPanel::id).collect(Collectors.toSet());
        assertTrue("missing general; found " + ids, ids.contains("general"));
        assertTrue("missing sources; found " + ids, ids.contains("sources"));
        assertTrue("missing display; found " + ids, ids.contains("display"));
        assertFalse("J2SE panel leaked; found " + ids, ids.contains("application"));
    }
}
