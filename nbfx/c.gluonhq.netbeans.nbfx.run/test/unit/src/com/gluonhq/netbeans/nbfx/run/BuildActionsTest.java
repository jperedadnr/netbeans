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
package com.gluonhq.netbeans.nbfx.run;

import com.gluonhq.netbeans.nbfx.project.ui.api.BuildActionProvider;
import java.util.HashSet;
import java.util.Set;
import junit.framework.Test;
import static org.junit.Assert.assertTrue;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Lookup;

/**
 * Verifies that the project-type {@link BuildActionProvider}s are discoverable through the module
 * system's default Lookup - the reason a build action can run in-process instead of via the CLI.
 */
public class BuildActionsTest extends NbTestCase {

    public BuildActionsTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(BuildActionsTest.class)
                .clusters(".*")
                .enableModules(".*")
                .gui(false)
                .suite();
    }

    public void testProvidersAreDiscoverable() {
        Set<String> ids = new HashSet<>();
        for (BuildActionProvider provider : Lookup.getDefault().lookupAll(BuildActionProvider.class)) {
            ids.add(provider.projectTypeId());
        }
        assertTrue("no provider for ant; found " + ids, ids.contains("ant"));
        assertTrue("no provider for gradle; found " + ids, ids.contains("gradle"));
        assertTrue("no provider for maven; found " + ids, ids.contains("maven"));
    }
}
