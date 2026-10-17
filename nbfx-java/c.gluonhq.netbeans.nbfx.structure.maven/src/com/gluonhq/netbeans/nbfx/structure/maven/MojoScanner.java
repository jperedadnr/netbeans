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

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.embedder.MavenEmbedder;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Lists the goals of the plugins a Maven project uses, as NetBeans' {@code GoalsPanel} does: each
 * plugin artifact is resolved (from the local repository, else downloaded) and its
 * {@code META-INF/maven/plugin.xml} read for its goal prefix and mojos.
 */
final class MojoScanner {

    private static final Logger LOG = Logger.getLogger(MojoScanner.class.getName());
    private static final String PLUGIN_DESCRIPTOR = "META-INF/maven/plugin.xml";

    private MojoScanner() {
    }

    /**
     * The goals of {@code project}'s plugins, sorted by prefix and goal; the empty list for a project
     * that is not a Maven one, {@code null} when cancelled. Background thread: resolving a plugin may
     * download it.
     */
    static List<Mojo> scan(Project project, Cancellation cancellation) {
        NbMavenProject nbMaven = project.getLookup().lookup(NbMavenProject.class);
        MavenProject mavenProject = nbMaven == null ? null : nbMaven.getMavenProject();
        if (mavenProject == null) {
            return List.of();
        }
        Set<Artifact> artifacts = mavenProject.getPluginArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        MavenEmbedder embedder = EmbedderFactory.getOnlineEmbedder();
        Set<Mojo> mojos = new TreeSet<>();
        for (Artifact artifact : artifacts) {
            if (cancellation.isCancelled()) {
                return null;
            }
            try {
                embedder.resolveArtifact(artifact, mavenProject.getPluginArtifactRepositories(), embedder.getLocalRepository());
            } catch (ArtifactResolutionException | ArtifactNotFoundException | RuntimeException ex) {
                LOG.log(Level.FINE, "Plugin artifact " + artifact + " cannot be resolved", ex);
                continue;
            }
            File jar = artifact.getFile();
            if (jar == null) {
                LOG.log(Level.FINE, "Plugin artifact {0} does not resolve to a local file", artifact);
                continue;
            }
            Document descriptor = readDescriptor(jar);
            if (descriptor == null) {
                continue;
            }
            Element root = descriptor.getDocumentElement();
            Element goalPrefix = XMLUtil.findElement(root, "goalPrefix", null);
            Element mojoList = XMLUtil.findElement(root, "mojos", null);
            if (goalPrefix == null || mojoList == null) {
                LOG.log(Level.FINE, "No goalPrefix or mojos in {0}", jar);
                continue;
            }
            String prefix = XMLUtil.findText(goalPrefix).trim();
            Plugin plugin = mavenProject.getPlugin(Plugin.constructKey(artifact.getGroupId(), artifact.getArtifactId()));
            for (Element mojo : XMLUtil.findSubElements(mojoList)) {
                if (!"mojo".equals(mojo.getTagName())) {
                    continue;
                }
                Element goalElement = XMLUtil.findElement(mojo, "goal", null);
                String goal = goalElement == null ? null : XMLUtil.findText(goalElement);
                if (goal == null || goal.isBlank()) {
                    continue;
                }
                goal = goal.trim();
                mojos.add(new Mojo(prefix, goal, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                        isLifecycleBound(plugin, goal)));
            }
        }
        return new ArrayList<>(mojos);
    }

    /** Whether an execution of {@code plugin} in the POM runs {@code goal} (NetBeans' heuristic). */
    private static boolean isLifecycleBound(Plugin plugin, String goal) {
        if (plugin == null || plugin.getExecutions() == null) {
            return false;
        }
        for (PluginExecution execution : plugin.getExecutions()) {
            if (execution.getGoals() != null && execution.getGoals().contains(goal)) {
                return true;
            }
        }
        return false;
    }

    private static Document readDescriptor(File jar) {
        if (!jar.isFile() || !jar.getName().endsWith(".jar")) {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            JarEntry entry = jarFile.getJarEntry(PLUGIN_DESCRIPTOR);
            if (entry == null) {
                return null;
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                return XMLUtil.parse(new InputSource(in), false, false, XMLUtil.defaultErrorHandler(), null);
            }
        } catch (IOException | SAXException | RuntimeException ex) {
            LOG.log(Level.FINE, "Could not read the plugin descriptor of " + jar, ex);
            return null;
        }
    }
}
