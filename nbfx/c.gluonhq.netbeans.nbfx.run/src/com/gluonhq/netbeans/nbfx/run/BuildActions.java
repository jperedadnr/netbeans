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
import com.gluonhq.netbeans.nbfx.project.ui.api.ProjectKinds;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Resolves the {@link BuildActionProvider} for a project directory and answers whether a command is
 * enabled for it, mirroring the original {@code ActionProvider} lookup and
 * {@code isActionEnabled(command, context)}.
 *
 * @since 1.0
 */
final class BuildActions {

    private static final Logger LOG = Logger.getLogger(BuildActions.class.getName());

    private BuildActions() {
    }

    /** The provider for the project at {@code dir}, or {@code null} when none matches. */
    static BuildActionProvider providerFor(Path dir) {
        String kind = kindOf(dir);
        if (kind == null) {
            return null;
        }
        for (BuildActionProvider provider : Lookup.getDefault().lookupAll(BuildActionProvider.class)) {
            if (kind.equals(provider.projectTypeId())) {
                return provider;
            }
        }
        return null;
    }

    /**
     * The project-kind id of {@code dir}: from the project model when it can be resolved, otherwise
     * from the marker-file detected build tool, so a provider is found even when the project is not
     * (yet) recognised by the platform.
     */
    private static String kindOf(Path dir) {
        FileObject fileObject = dir == null ? null : FileUtil.toFileObject(dir.toFile());
        if (fileObject != null) {
            try {
                Project project = ProjectManager.getDefault().findProject(fileObject);
                if (project != null) {
                    return ProjectKinds.providerOf(project).id();
                }
            } catch (Exception ex) {
                LOG.log(Level.FINE, "No project for " + dir, ex);
            }
        }
        return switch (BuildTool.detect(dir)) {
            case MAVEN -> "maven";
            case GRADLE -> "gradle";
            case ANT -> "ant";
            case UNKNOWN -> null;
        };
    }

    /**
     * Whether {@code command} is enabled for the project at {@code dir}: supported and enabled by
     * its provider, or (when no provider matches) left to the marker-file fallback.
     */
    static boolean isEnabled(Path dir, String command) {
        BuildActionProvider provider = providerFor(dir);
        if (provider == null) {
            return true;
        }
        return supports(provider, command) && provider.isActionEnabled(command);
    }

    private static boolean supports(BuildActionProvider provider, String command) {
        for (String supported : provider.getSupportedActions()) {
            if (supported.equals(command)) {
                return true;
            }
        }
        return false;
    }
}
