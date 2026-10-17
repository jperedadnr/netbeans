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

import com.gluonhq.netbeans.nbfx.structure.maven.PomNode.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;
import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationOS;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Extension;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.model.Site;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.openide.util.NbBundle;

/**
 * Builds the POM model tree of a lineage, after NetBeans' {@code POMModelVisitor}: the elements of
 * the POM schema in schema order, each row carrying the value the project's POM and each parent
 * give it, so the tree shows what is inherited and what is overridden. Lists (dependencies, plugins,
 * profiles...) merge their items across the layers by key - a dependency's {@code groupId:artifactId},
 * a profile's id - and a list that has no key (developers, licenses) keeps each layer's items apart.
 */
final class PomTreeBuilder {

    /** The root of the tree: the {@code project} element of every layer. */
    static PomNode build(List<PomLayer> layers) {
        Object[] models = layers.stream().map(PomLayer::model).toArray();
        PomNode root = new PomNode(Kind.OBJECT, "root", "project", models, locations(models, ""), false);
        new PomTreeBuilder(models.length).project(root, models);
        return root;
    }

    /** How a list's items are told apart across the layers, and named. */
    private record ListKind<E>(Function<E, Object> key, Function<E, String> name) {
    }

    private final int layerCount;

    private PomTreeBuilder(int layerCount) {
        this.layerCount = layerCount;
    }

    // -- the POM schema ----------------------------------------------------------------------------

    private void project(PomNode node, Object[] cuts) {
        field(node, cuts, "modelVersion", "MODEL_VERSION", Model::getModelVersion);
        field(node, cuts, "groupId", "GROUPID", Model::getGroupId);
        field(node, cuts, "artifactId", "ARTIFACTID", Model::getArtifactId);
        // Packaging and modules are the project's own: no point in showing the parents'.
        field(node, currentOnly(cuts), "packaging", "PACKAGING", Model::getPackaging);
        field(node, cuts, "name", "NAME", Model::getName);
        field(node, cuts, "version", "VERSION", Model::getVersion);
        field(node, cuts, "description", "DESCRIPTION", Model::getDescription);
        field(node, cuts, "url", "URL", Model::getUrl);
        object(node, cuts, "prerequisites", "PREREQUISITES", Model::getPrerequisites, this::prerequisites);
        object(node, cuts, "issueManagement", "ISSUEMANAGEMENT", Model::getIssueManagement, this::issueManagement);
        object(node, cuts, "ciManagement", "CIMANAGEMENT", Model::getCiManagement, this::ciManagement);
        field(node, cuts, "inceptionYear", "INCEPTION_YEAR", Model::getInceptionYear);
        list(node, cuts, "mailingLists", "mailingList", "MAILING_LISTS", Model::getMailingLists,
                identity(list -> or(list.getName(), "MAILING_LIST")), this::mailingList);
        list(node, cuts, "developers", "developer", "DEVELOPERS", Model::getDevelopers,
                identity(developer -> or(developer.getId(), "DEVELOPER")), this::developer);
        list(node, cuts, "contributors", "contributor", "CONTRIBUTORS", Model::getContributors,
                identity(contributor -> or(contributor.getName(), "CONTRIBUTOR")), this::contributor);
        list(node, cuts, "licenses", "license", "LICENSES", Model::getLicenses,
                identity(license -> or(license.getName(), "LICENSE")), this::license);
        object(node, cuts, "scm", "SCM", Model::getScm, this::scm);
        object(node, cuts, "organization", "ORGANIZATION", Model::getOrganization, this::organization);
        object(node, cuts, "build", "BUILD", Model::getBuild, this::build);
        list(node, cuts, "profiles", "profile", "PROFILES", Model::getProfiles,
                new ListKind<>(Profile::getId, profile -> or(profile.getId(), "PROFILE")), this::profile);
        repositories(node, cuts, Model::getRepositories, Model::getPluginRepositories);
        dependencies(node, cuts, Model::getDependencies);
        object(node, cuts, "reporting", "REPORTING", Model::getReporting, this::reporting);
        object(node, cuts, "dependencyManagement", "DEPENDENCY_MANAGEMENT", Model::getDependencyManagement,
                (n, c) -> dependencies(n, c, DependencyManagement::getDependencies));
        object(node, cuts, "distributionManagement", "DISTRIBUTION_MANAGEMENT", Model::getDistributionManagement,
                this::distributionManagement);
        properties(node, cuts, Model::getProperties);
        stringList(node, currentOnly(cuts), "modules", "module", "MODULES", "MODULE", Model::getModules);
    }

    private void prerequisites(PomNode node, Object[] cuts) {
        field(node, cuts, "maven", "MAVEN", Prerequisites::getMaven);
    }

    private void issueManagement(PomNode node, Object[] cuts) {
        field(node, cuts, "system", "SYSTEM", IssueManagement::getSystem);
        field(node, cuts, "url", "URL", IssueManagement::getUrl);
    }

    private void ciManagement(PomNode node, Object[] cuts) {
        field(node, cuts, "system", "SYSTEM", CiManagement::getSystem);
        field(node, cuts, "url", "URL", CiManagement::getUrl);
    }

    private void mailingList(PomNode node, Object[] cuts) {
        field(node, cuts, "name", "NAME", MailingList::getName);
        field(node, cuts, "subscribe", "SUBSCRIBE", MailingList::getSubscribe);
        field(node, cuts, "unsubscribe", "UNSUBSCRIBE", MailingList::getUnsubscribe);
        field(node, cuts, "post", "POST", MailingList::getPost);
        field(node, cuts, "archive", "ARCHIVE", MailingList::getArchive);
    }

    private void developer(PomNode node, Object[] cuts) {
        field(node, cuts, "id", "ID", Developer::getId);
        field(node, cuts, "name", "NAME", Developer::getName);
        field(node, cuts, "email", "EMAIL", Developer::getEmail);
        field(node, cuts, "url", "URL", Developer::getUrl);
        field(node, cuts, "organization", "ORGANIZATION", Developer::getOrganization);
        field(node, cuts, "organizationUrl", "ORGANIZATION_URL", Developer::getOrganizationUrl);
        field(node, cuts, "timezone", "TIMEZONE", Developer::getTimezone);
    }

    private void contributor(PomNode node, Object[] cuts) {
        field(node, cuts, "name", "NAME", Contributor::getName);
        field(node, cuts, "email", "EMAIL", Contributor::getEmail);
        field(node, cuts, "url", "URL", Contributor::getUrl);
        field(node, cuts, "organization", "ORGANIZATION", Contributor::getOrganization);
        field(node, cuts, "organizationUrl", "ORGANIZATION_URL", Contributor::getOrganizationUrl);
        field(node, cuts, "timezone", "TIMEZONE", Contributor::getTimezone);
    }

    private void license(PomNode node, Object[] cuts) {
        field(node, cuts, "name", "NAME", License::getName);
        field(node, cuts, "url", "URL", License::getUrl);
    }

    private void scm(PomNode node, Object[] cuts) {
        field(node, cuts, "connection", "CONNECTION", Scm::getConnection);
        field(node, cuts, "developerConnection", "DEVELOPER_CONNECTION", Scm::getDeveloperConnection);
        field(node, cuts, "tag", "TAG", Scm::getTag);
        field(node, cuts, "url", "URL", Scm::getUrl);
    }

    private void organization(PomNode node, Object[] cuts) {
        field(node, cuts, "name", "NAME", Organization::getName);
        field(node, cuts, "url", "URL", Organization::getUrl);
    }

    private void build(PomNode node, Object[] cuts) {
        field(node, cuts, "sourceDirectory", "SOURCE_DIRECTORY", Build::getSourceDirectory);
        field(node, cuts, "scriptSourceDirectory", "SCRIPT_SOURCE_DIRECTORY", Build::getScriptSourceDirectory);
        field(node, cuts, "testSourceDirectory", "TEST_SOURCE_DIRECTORY", Build::getTestSourceDirectory);
        field(node, cuts, "outputDirectory", "OUTPUT_DIRECTORY", Build::getOutputDirectory);
        field(node, cuts, "testOutputDirectory", "TEST_OUTPUT_DIRECTORY", Build::getTestOutputDirectory);
        list(node, cuts, "extensions", "extension", "EXTENSIONS", Build::getExtensions,
                new ListKind<>(e -> e.getGroupId() + ":" + e.getArtifactId(), e -> or(e.getArtifactId(), "EXTENSION")),
                this::extension);
        buildBase(node, cuts);
    }

    private void buildBase(PomNode node, Object[] cuts) {
        field(node, cuts, "defaultGoal", "DEFAULT_GOAL", BuildBase::getDefaultGoal);
        list(node, cuts, "resources", "resource", "RESOURCES", BuildBase::getResources,
                identity(resource -> or(resource.getDirectory(), "RESOURCE")), this::resource);
        list(node, cuts, "testResources", "testResource", "TEST_RESOURCES", BuildBase::getTestResources,
                identity(resource -> or(resource.getDirectory(), "TEST_RESOURCE")), this::resource);
        field(node, cuts, "directory", "DIRECTORY", BuildBase::getDirectory);
        field(node, cuts, "finalName", "FINAL_NAME", BuildBase::getFinalName);
        object(node, cuts, "pluginManagement", "PLUGIN_MANAGEMENT", BuildBase::getPluginManagement,
                (n, c) -> plugins(n, c, PluginManagement::getPlugins));
        plugins(node, cuts, BuildBase::getPlugins);
    }

    private <T> void plugins(PomNode node, Object[] cuts, Function<T, List<Plugin>> getter) {
        list(node, cuts, "plugins", "plugin", "PLUGINS", getter,
                new ListKind<>(plugin -> or(plugin.getGroupId(), "org.apache.maven.plugins") + ":" + plugin.getArtifactId(),
                        plugin -> or(plugin.getArtifactId(), "PLUGIN")),
                this::plugin);
    }

    private void plugin(PomNode node, Object[] cuts) {
        field(node, cuts, "groupId", "GROUPID", Plugin::getGroupId);
        field(node, cuts, "artifactId", "ARTIFACTID", Plugin::getArtifactId);
        field(node, cuts, "version", "VERSION", Plugin::getVersion);
        field(node, cuts, "extensions", "EXTENSIONS", Plugin::getExtensions);
        list(node, cuts, "executions", "execution", "EXECUTIONS", Plugin::getExecutions,
                new ListKind<>(PluginExecution::getId, execution -> or(execution.getId(), "EXECUTION")), this::execution);
        dependencies(node, cuts, Plugin::getDependencies);
        field(node, cuts, "inherited", "INHERITED", Plugin::getInherited);
        configuration(node, cuts, Plugin::getConfiguration);
    }

    private void execution(PomNode node, Object[] cuts) {
        field(node, cuts, "id", "ID", PluginExecution::getId);
        field(node, cuts, "phase", "PHASE", PluginExecution::getPhase);
        field(node, cuts, "inherited", "INHERITED", PluginExecution::getInherited);
        configuration(node, cuts, PluginExecution::getConfiguration);
        stringList(node, cuts, "goals", "goal", "GOALS", "GOAL", PluginExecution::getGoals);
    }

    private void extension(PomNode node, Object[] cuts) {
        field(node, cuts, "groupId", "GROUPID", Extension::getGroupId);
        field(node, cuts, "artifactId", "ARTIFACTID", Extension::getArtifactId);
        field(node, cuts, "version", "VERSION", Extension::getVersion);
    }

    private void resource(PomNode node, Object[] cuts) {
        field(node, cuts, "targetPath", "TARGET_PATH", Resource::getTargetPath);
        field(node, cuts, "directory", "DIRECTORY", Resource::getDirectory);
        stringList(node, cuts, "includes", "include", "INCLUDES", "INCLUDE", Resource::getIncludes);
        stringList(node, cuts, "excludes", "exclude", "EXCLUDES", "EXCLUDE", Resource::getExcludes);
    }

    private <T> void dependencies(PomNode node, Object[] cuts, Function<T, List<Dependency>> getter) {
        list(node, cuts, "dependencies", "dependency", "DEPENDENCIES", getter,
                new ListKind<>(d -> d.getGroupId() + ":" + d.getArtifactId(), d -> or(d.getArtifactId(), "DEPENDENCY")),
                this::dependency);
    }

    private void dependency(PomNode node, Object[] cuts) {
        field(node, cuts, "groupId", "GROUPID", Dependency::getGroupId);
        field(node, cuts, "artifactId", "ARTIFACTID", Dependency::getArtifactId);
        field(node, cuts, "version", "VERSION", Dependency::getVersion);
        field(node, cuts, "type", "TYPE", Dependency::getType);
        field(node, cuts, "classifier", "CLASSIFIER", Dependency::getClassifier);
        field(node, cuts, "scope", "SCOPE", Dependency::getScope);
        list(node, cuts, "exclusions", "exclusion", "EXCLUSIONS", Dependency::getExclusions,
                new ListKind<>(e -> e.getGroupId() + ":" + e.getArtifactId(), e -> or(e.getArtifactId(), "EXCLUSION")),
                this::exclusion);
    }

    private void exclusion(PomNode node, Object[] cuts) {
        field(node, cuts, "groupId", "GROUPID", Exclusion::getGroupId);
        field(node, cuts, "artifactId", "ARTIFACTID", Exclusion::getArtifactId);
    }

    private void profile(PomNode node, Object[] cuts) {
        field(node, cuts, "id", "ID", Profile::getId);
        object(node, cuts, "activation", "ACTIVATION", Profile::getActivation, this::activation);
        object(node, cuts, "build", "BUILD", Profile::getBuild, this::buildBase);
        repositories(node, cuts, Profile::getRepositories, Profile::getPluginRepositories);
        dependencies(node, cuts, Profile::getDependencies);
        object(node, cuts, "reporting", "REPORTING", Profile::getReporting, this::reporting);
        object(node, cuts, "dependencyManagement", "DEPENDENCY_MANAGEMENT", Profile::getDependencyManagement,
                (n, c) -> dependencies(n, c, DependencyManagement::getDependencies));
        object(node, cuts, "distributionManagement", "DISTRIBUTION_MANAGEMENT", Profile::getDistributionManagement,
                this::distributionManagement);
        properties(node, cuts, Profile::getProperties);
    }

    private void activation(PomNode node, Object[] cuts) {
        object(node, cuts, "os", "OPERATING_SYSTEM", Activation::getOs, (n, c) -> {
            field(n, c, "name", "NAME", ActivationOS::getName);
            field(n, c, "family", "FAMILY", ActivationOS::getFamily);
            field(n, c, "arch", "ARCH", ActivationOS::getArch);
            field(n, c, "version", "VERSION", ActivationOS::getVersion);
        });
        object(node, cuts, "property", "PROPERTY", Activation::getProperty, (n, c) -> {
            field(n, c, "name", "NAME", ActivationProperty::getName);
            field(n, c, "value", "VALUE", ActivationProperty::getValue);
        });
        object(node, cuts, "file", "FILE", Activation::getFile, (n, c) -> {
            field(n, c, "exists", "EXISTS", ActivationFile::getExists);
            field(n, c, "missing", "MISSING", ActivationFile::getMissing);
        });
    }

    private <T> void repositories(PomNode node, Object[] cuts, Function<T, List<Repository>> repositories,
            Function<T, List<Repository>> pluginRepositories) {
        ListKind<Repository> byId = new ListKind<>(Repository::getId, repository -> or(repository.getId(), "REPOSITORY"));
        list(node, cuts, "repositories", "repository", "REPOSITORIES", repositories, byId, this::repository);
        list(node, cuts, "pluginRepositories", "pluginRepository", "PLUGIN_REPOSITORIES", pluginRepositories, byId,
                this::repository);
    }

    private void repository(PomNode node, Object[] cuts) {
        field(node, cuts, "id", "ID", Repository::getId);
        field(node, cuts, "name", "NAME", Repository::getName);
        field(node, cuts, "url", "URL", Repository::getUrl);
        field(node, cuts, "layout", "LAYOUT", Repository::getLayout);
        object(node, cuts, "releases", "RELEASES", Repository::getReleases, this::repositoryPolicy);
        object(node, cuts, "snapshots", "SNAPSHOTS", Repository::getSnapshots, this::repositoryPolicy);
    }

    private void repositoryPolicy(PomNode node, Object[] cuts) {
        field(node, cuts, "enabled", "ENABLED", RepositoryPolicy::getEnabled);
        field(node, cuts, "updatePolicy", "UPDATE_POLICY", RepositoryPolicy::getUpdatePolicy);
        field(node, cuts, "checksumPolicy", "CHECKSUM_POLICY", RepositoryPolicy::getChecksumPolicy);
    }

    private void reporting(PomNode node, Object[] cuts) {
        field(node, cuts, "excludeDefaults", "EXCLUDE_DEFAULTS", Reporting::getExcludeDefaults);
        field(node, cuts, "outputDirectory", "OUTPUT_DIRECTORY", Reporting::getOutputDirectory);
        list(node, cuts, "plugins", "plugin", "REPORT_PLUGINS", Reporting::getPlugins,
                new ListKind<>(p -> p.getGroupId() + ":" + p.getArtifactId(), p -> or(p.getArtifactId(), "REPORT_PLUGIN")),
                this::reportPlugin);
    }

    private void reportPlugin(PomNode node, Object[] cuts) {
        field(node, cuts, "groupId", "GROUPID", ReportPlugin::getGroupId);
        field(node, cuts, "artifactId", "ARTIFACTID", ReportPlugin::getArtifactId);
        field(node, cuts, "version", "VERSION", ReportPlugin::getVersion);
        field(node, cuts, "inherited", "INHERITED", ReportPlugin::getInherited);
        configuration(node, cuts, ReportPlugin::getConfiguration);
        list(node, cuts, "reportSets", "reportSet", "REPORTSETS", ReportPlugin::getReportSets,
                new ListKind<>(ReportSet::getId, set -> or(set.getId(), "REPORTSET")), this::reportSet);
    }

    private void reportSet(PomNode node, Object[] cuts) {
        field(node, cuts, "id", "ID", ReportSet::getId);
        configuration(node, cuts, ReportSet::getConfiguration);
        field(node, cuts, "inherited", "INHERITED", ReportSet::getInherited);
        stringList(node, cuts, "reports", "report", "REPORTS", "REPORT", ReportSet::getReports);
    }

    private void distributionManagement(PomNode node, Object[] cuts) {
        object(node, cuts, "repository", "REPOSITORY", DistributionManagement::getRepository, this::deploymentRepository);
        object(node, cuts, "snapshotRepository", "SNAPSHOT_REPOSITORY", DistributionManagement::getSnapshotRepository,
                this::deploymentRepository);
        object(node, cuts, "site", "SITE", DistributionManagement::getSite, (n, c) -> {
            field(n, c, "id", "ID", Site::getId);
            field(n, c, "name", "NAME", Site::getName);
            field(n, c, "url", "URL", Site::getUrl);
        });
        field(node, cuts, "downloadUrl", "DOWNLOAD_URL", DistributionManagement::getDownloadUrl);
    }

    private void deploymentRepository(PomNode node, Object[] cuts) {
        field(node, cuts, "id", "ID", DeploymentRepository::getId);
        field(node, cuts, "name", "NAME", DeploymentRepository::getName);
        field(node, cuts, "url", "URL", DeploymentRepository::getUrl);
        field(node, cuts, "layout", "LAYOUT", DeploymentRepository::getLayout);
    }

    // -- the generic rows ----------------------------------------------------------------------------

    /** The layers' objects behind a row: {@code null} where a layer lacks the element. */
    @FunctionalInterface
    private interface Visitor {
        void visit(PomNode node, Object[] cuts);
    }

    private <T> void field(PomNode parent, Object[] cuts, String element, String nameKey, Function<T, String> getter) {
        Object[] values = new Object[layerCount];
        for (int i = 0; i < cuts.length; i++) {
            values[i] = cuts[i] == null ? null : getter.apply(cast(cuts[i]));
        }
        parent.add(new PomNode(Kind.FIELD, message(nameKey), element, values, locations(cuts, element), false));
    }

    private <T, C> void object(PomNode parent, Object[] cuts, String element, String nameKey, Function<T, C> getter,
            Visitor visitor) {
        Object[] values = new Object[layerCount];
        for (int i = 0; i < cuts.length; i++) {
            values[i] = cuts[i] == null ? null : getter.apply(cast(cuts[i]));
        }
        PomNode node = new PomNode(Kind.OBJECT, message(nameKey), element, values, locations(cuts, element), false);
        parent.add(node);
        visitor.visit(node, values);
    }

    /**
     * A list row whose items are merged across the layers by {@code kind}'s key: an item defined in
     * the project's POM and in a parent is one row, defined in both layers.
     */
    private <T, E> void list(PomNode parent, Object[] cuts, String element, String itemElement, String nameKey,
            Function<T, List<E>> getter, ListKind<E> kind, Visitor itemVisitor) {
        Object[] values = new Object[layerCount];
        Map<Object, Object[]> items = new LinkedHashMap<>();
        Map<Object, String> names = new LinkedHashMap<>();
        for (int i = 0; i < cuts.length; i++) {
            List<E> list = cuts[i] == null ? null : getter.apply(cast(cuts[i]));
            if (list == null || list.isEmpty()) {
                continue;
            }
            values[i] = list;
            for (E item : list) {
                Object key = kind.key().apply(item);
                Object[] itemCuts = items.computeIfAbsent(key, k -> new Object[layerCount]);
                if (itemCuts[i] == null) {
                    itemCuts[i] = item;
                    names.putIfAbsent(key, kind.name().apply(item));
                }
            }
        }
        PomNode node = new PomNode(Kind.LIST, message(nameKey), element, values, locations(cuts, element), false);
        parent.add(node);
        for (Map.Entry<Object, Object[]> entry : items.entrySet()) {
            Object[] itemCuts = entry.getValue();
            PomNode item = new PomNode(Kind.OBJECT, names.get(entry.getKey()), itemElement, itemCuts,
                    locations(itemCuts, ""), true);
            node.add(item);
            itemVisitor.visit(item, itemCuts);
        }
    }

    /** A list of strings (modules, goals, includes): one field row per distinct string. */
    private <T> void stringList(PomNode parent, Object[] cuts, String element, String itemElement, String nameKey,
            String itemNameKey, Function<T, List<String>> getter) {
        Object[] values = new Object[layerCount];
        Map<String, Object[]> items = new LinkedHashMap<>();
        Map<String, InputLocation[]> itemLocations = new LinkedHashMap<>();
        for (int i = 0; i < cuts.length; i++) {
            List<String> list = cuts[i] == null ? null : getter.apply(cast(cuts[i]));
            if (list == null || list.isEmpty()) {
                continue;
            }
            values[i] = list;
            InputLocation listLocation = location(cuts[i], element);
            for (int index = 0; index < list.size(); index++) {
                String item = list.get(index);
                Object[] itemCuts = items.computeIfAbsent(item, k -> new Object[layerCount]);
                InputLocation[] locations = itemLocations.computeIfAbsent(item, k -> new InputLocation[layerCount]);
                if (itemCuts[i] == null) {
                    itemCuts[i] = item;
                    locations[i] = listLocation == null ? null : listLocation.getLocation(index);
                }
            }
        }
        PomNode node = new PomNode(Kind.LIST, message(nameKey), element, values, locations(cuts, element), false);
        parent.add(node);
        String itemName = message(itemNameKey);
        for (Map.Entry<String, Object[]> entry : items.entrySet()) {
            node.add(new PomNode(Kind.FIELD, itemName, itemElement, entry.getValue(), itemLocations.get(entry.getKey()), true));
        }
    }

    /** The {@code properties} row: one field per property name, in name order. */
    private <T> void properties(PomNode parent, Object[] cuts, Function<T, Properties> getter) {
        Object[] values = new Object[layerCount];
        Map<String, Object[]> entries = new TreeMap<>();
        Map<String, InputLocation[]> entryLocations = new TreeMap<>();
        for (int i = 0; i < cuts.length; i++) {
            Properties properties = cuts[i] == null ? null : getter.apply(cast(cuts[i]));
            if (properties == null || properties.isEmpty()) {
                continue;
            }
            values[i] = properties;
            InputLocation propertiesLocation = location(cuts[i], "properties");
            for (String name : properties.stringPropertyNames()) {
                entries.computeIfAbsent(name, k -> new Object[layerCount])[i] = properties.getProperty(name);
                entryLocations.computeIfAbsent(name, k -> new InputLocation[layerCount])[i] =
                        propertiesLocation == null ? null : propertiesLocation.getLocation(name);
            }
        }
        PomNode node = new PomNode(Kind.OBJECT, message("PROPERTIES"), "properties", values,
                locations(cuts, "properties"), false);
        parent.add(node);
        for (Map.Entry<String, Object[]> entry : entries.entrySet()) {
            node.add(new PomNode(Kind.FIELD, entry.getKey(), entry.getKey(), entry.getValue(),
                    entryLocations.get(entry.getKey()), false));
        }
    }

    /** A plugin's {@code configuration}: its free-form elements, nested as written. */
    private <T> void configuration(PomNode parent, Object[] cuts, Function<T, Object> getter) {
        Object[] values = new Object[layerCount];
        for (int i = 0; i < cuts.length; i++) {
            Object configuration = cuts[i] == null ? null : getter.apply(cast(cuts[i]));
            values[i] = configuration instanceof Xpp3Dom dom ? dom : null;
        }
        PomNode node = new PomNode(Kind.OBJECT, message("CONFIGURATION"), "configuration", values,
                domLocations(values), false);
        parent.add(node);
        domChildren(node, values);
    }

    /**
     * The child elements of free-form configuration elements, merged across the layers by name; an
     * element repeated within one configuration is keyed by its value too, as NetBeans does.
     */
    private void domChildren(PomNode parent, Object[] doms) {
        Map<String, Object[]> children = new LinkedHashMap<>();
        for (int i = 0; i < doms.length; i++) {
            if (!(doms[i] instanceof Xpp3Dom dom)) {
                continue;
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Xpp3Dom child : dom.getChildren()) {
                counts.merge(child.getName(), 1, Integer::sum);
            }
            for (Xpp3Dom child : dom.getChildren()) {
                String key = counts.get(child.getName()) > 1 ? child.getName() + "=" + child.getValue() : child.getName();
                Object[] childCuts = children.computeIfAbsent(key, k -> new Object[layerCount]);
                if (childCuts[i] == null) {
                    childCuts[i] = child;
                }
            }
        }
        for (Object[] childCuts : children.values()) {
            Xpp3Dom first = null;
            boolean nested = false;
            for (Object cut : childCuts) {
                if (cut instanceof Xpp3Dom dom) {
                    first = first == null ? dom : first;
                    nested |= dom.getChildCount() > 0;
                }
            }
            if (first == null) {
                continue;
            }
            if (nested) {
                PomNode node = new PomNode(Kind.OBJECT, first.getName(), first.getName(), childCuts,
                        domLocations(childCuts), false);
                parent.add(node);
                domChildren(node, childCuts);
            } else {
                Object[] values = new Object[layerCount];
                for (int i = 0; i < childCuts.length; i++) {
                    values[i] = childCuts[i] instanceof Xpp3Dom dom ? or(dom.getValue(), "") : null;
                }
                parent.add(new PomNode(Kind.FIELD, first.getName(), first.getName(), values, domLocations(childCuts), false));
            }
        }
    }

    // -- helpers ------------------------------------------------------------------------------------

    /** {@code cuts} with the parents' objects dropped: the row shows the project's POM alone. */
    private Object[] currentOnly(Object[] cuts) {
        Object[] current = new Object[layerCount];
        if (cuts.length > 0) {
            current[0] = cuts[0];
        }
        return current;
    }

    private static <E> ListKind<E> identity(Function<E, String> name) {
        return new ListKind<>(item -> item, name);
    }

    /** {@code value}, or the message {@code fallbackKey} names when it is {@code null}. */
    private static String or(String value, String fallbackKey) {
        return value != null ? value : message(fallbackKey);
    }

    private static InputLocation[] locations(Object[] cuts, String element) {
        InputLocation[] locations = new InputLocation[cuts.length];
        for (int i = 0; i < cuts.length; i++) {
            locations[i] = location(cuts[i], element);
        }
        return locations;
    }

    /** Where {@code holder} declares {@code element} ({@code ""}: where the holder itself is declared). */
    private static InputLocation location(Object holder, String element) {
        return holder instanceof InputLocationTracker tracker ? tracker.getLocation(element) : null;
    }

    private static InputLocation[] domLocations(Object[] doms) {
        InputLocation[] locations = new InputLocation[doms.length];
        for (int i = 0; i < doms.length; i++) {
            if (doms[i] instanceof Xpp3Dom dom && dom.getInputLocation() instanceof InputLocation location) {
                locations[i] = location;
            }
        }
        return locations;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object cut) {
        return (T) cut;
    }

    private static String message(String key) {
        return NbBundle.getMessage(PomTreeBuilder.class, key);
    }
}
