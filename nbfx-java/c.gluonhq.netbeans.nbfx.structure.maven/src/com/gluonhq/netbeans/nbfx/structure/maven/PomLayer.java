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

import java.io.File;
import org.apache.maven.model.Model;

/**
 * One POM of a project's lineage: the project's own ({@link #current}), then its parents up to the
 * last one below the Maven super POM.
 *
 * @param model    the raw model of the POM, read with location tracking
 * @param file     the POM file, {@code null} when a parent could not be found in the local repository
 * @param current  whether this is the project's own POM, the first of the lineage
 * @param readOnly whether the file comes from the local repository, where it is not meant to be edited
 */
record PomLayer(Model model, File file, boolean current, boolean readOnly) {

    /** The artifact id, or {@code project} for a POM without one. */
    String artifactId() {
        return model.getArtifactId() != null ? model.getArtifactId() : "project";
    }

    /** The version, the parent's when the POM inherits it; {@code null} when neither has one. */
    String version() {
        if (model.getVersion() != null) {
            return model.getVersion();
        }
        return model.getParent() != null ? model.getParent().getVersion() : null;
    }

    /** The group id, the parent's when the POM inherits it; {@code null} when neither has one. */
    String groupId() {
        if (model.getGroupId() != null) {
            return model.getGroupId();
        }
        return model.getParent() != null ? model.getParent().getGroupId() : null;
    }
}
