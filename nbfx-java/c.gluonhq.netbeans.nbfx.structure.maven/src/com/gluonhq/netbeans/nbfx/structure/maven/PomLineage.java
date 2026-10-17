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
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.io.xpp3.MavenXpp3ReaderEx;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.embedder.MavenEmbedder;
import org.openide.util.NbBundle;

/**
 * Reads the lineage of a POM: the POM itself from the editor's text, so unsaved edits show, and its
 * parents through the Maven embedder, which resolves them from the project tree or the local
 * repository. The parents are cached per file and read again when the POM's parent coordinates or
 * the POM file on disk change.
 */
final class PomLineage {

    private static final Logger LOG = Logger.getLogger(PomLineage.class.getName());

    /**
     * The lineage read for a POM.
     *
     * @param layers  the POM first, then its parents, the super POM excluded; {@code null} when the
     *                text is not a well-formed POM (being edited)
     * @param problem what kept the parents from being read, else {@code null}: the layers then hold
     *                the POM alone
     */
    record Result(List<PomLayer> layers, String problem) {
    }

    /** The parents read for a POM, valid while the POM and the parents on disk and the parent coordinates stay. */
    private record Parents(String parentKey, long pomModified, long[] parentsModified, List<PomLayer> layers, String problem) {

        boolean isCurrent(String key, long modified) {
            if (!parentKey.equals(key) || pomModified != modified) {
                return false;
            }
            for (int i = 0; i < layers.size(); i++) {
                File file = layers.get(i).file();
                if (file != null && file.lastModified() != parentsModified[i]) {
                    return false;
                }
            }
            return true;
        }

        static Parents of(String parentKey, long pomModified, List<PomLayer> layers, String problem) {
            long[] modified = new long[layers.size()];
            for (int i = 0; i < modified.length; i++) {
                File file = layers.get(i).file();
                modified[i] = file == null ? 0 : file.lastModified();
            }
            return new Parents(parentKey, pomModified, modified, layers, problem);
        }
    }

    private static final Map<File, Parents> CACHE = new ConcurrentHashMap<>();

    private PomLineage() {
    }

    /**
     * Reads the lineage of the POM {@code pomFile} whose current text is {@code text}; background
     * thread.
     */
    static Result read(File pomFile, String text) {
        Model current;
        try {
            current = new MavenXpp3ReaderEx().read(new StringReader(text), false, new InputSource());
        } catch (IOException | XmlPullParserException | RuntimeException ex) {
            LOG.log(Level.FINE, "Not a well-formed POM: " + pomFile, ex);
            return new Result(null, null);
        }
        List<PomLayer> layers = new ArrayList<>();
        layers.add(new PomLayer(current, pomFile, true, false));
        Parents parents = parents(pomFile, current);
        layers.addAll(parents.layers());
        return new Result(List.copyOf(layers), parents.problem());
    }

    /** Drops what is cached for {@code pomFile}. */
    static void forget(File pomFile) {
        CACHE.remove(pomFile);
    }

    private static Parents parents(File pomFile, Model current) {
        String parentKey = parentKey(current);
        long modified = pomFile.lastModified();
        Parents cached = CACHE.get(pomFile);
        if (cached != null && cached.isCurrent(parentKey, modified)) {
            return cached;
        }
        Parents read = readParents(pomFile, parentKey, modified);
        CACHE.put(pomFile, read);
        return read;
    }

    private static Parents readParents(File pomFile, String parentKey, long modified) {
        MavenEmbedder embedder = EmbedderFactory.getProjectEmbedder();
        List<PomLayer> layers = new ArrayList<>();
        try {
            File localRepository = embedder.getLocalRepositoryFile();
            for (Model model : embedder.createModelLineage(pomFile)) {
                if (model.getArtifactId() == null) {
                    continue; // the super POM
                }
                File file = model.getPomFile();
                if (file != null && file.equals(pomFile)) {
                    continue; // the POM itself, read from the editor's text instead
                }
                if (file == null) {
                    file = repositoryFile(embedder, model);
                }
                boolean readOnly = file != null && localRepository != null
                        && file.toPath().startsWith(localRepository.toPath());
                layers.add(new PomLayer(model, file, false, readOnly));
            }
            return Parents.of(parentKey, modified, List.copyOf(layers), null);
        } catch (ModelBuildingException ex) {
            StringBuilder problem = new StringBuilder();
            for (ModelProblem p : ex.getProblems()) {
                if (problem.length() > 0) {
                    problem.append("; ");
                }
                problem.append(p.getMessage());
            }
            LOG.log(Level.FINE, "Could not read the parents of " + pomFile, ex);
            return Parents.of(parentKey, modified, List.of(),
                    NbBundle.getMessage(PomLineage.class, "ERR_Model", problem.toString()));
        } catch (RuntimeException | LinkageError ex) {
            LOG.log(Level.WARNING, "Could not read the parents of " + pomFile, ex);
            return Parents.of(parentKey, modified, List.of(),
                    NbBundle.getMessage(PomLineage.class, "ERR_Model", String.valueOf(ex)));
        }
    }

    /** The file of a parent that came from the local repository, {@code null} when it is not there. */
    private static File repositoryFile(MavenEmbedder embedder, Model model) {
        Parent parent = model.getParent();
        String groupId = model.getGroupId() != null ? model.getGroupId() : parent != null ? parent.getGroupId() : null;
        String version = model.getVersion() != null ? model.getVersion() : parent != null ? parent.getVersion() : null;
        if (groupId == null || version == null) {
            return null;
        }
        try {
            Artifact artifact = embedder.createArtifact(groupId, model.getArtifactId(), version, "pom");
            var repository = embedder.getLocalRepository();
            File file = new File(repository.getBasedir(), repository.pathOf(artifact));
            return file.isFile() ? file : null;
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Could not locate the POM of " + model.getId(), ex);
            return null;
        }
    }

    private static String parentKey(Model model) {
        Parent parent = model.getParent();
        return parent == null ? "" : Objects.toString(parent.getGroupId()) + ":" + parent.getArtifactId()
                + ":" + parent.getVersion() + ":" + parent.getRelativePath();
    }
}
