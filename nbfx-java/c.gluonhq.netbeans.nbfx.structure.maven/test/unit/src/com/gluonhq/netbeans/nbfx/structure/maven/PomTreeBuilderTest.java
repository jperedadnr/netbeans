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
import com.gluonhq.netbeans.nbfx.structure.xml.XmlNode;
import java.io.StringReader;
import java.util.List;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3ReaderEx;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Checks the tree {@link PomTreeBuilder} merges from a POM and its parent, and how {@link PomLocator} maps it back. */
public class PomTreeBuilderTest {

    private static final String CHILD = """
            <?xml version="1.0"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>g</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
              </parent>
              <artifactId>child</artifactId>
              <packaging>jar</packaging>
              <properties>
                <a>1</a>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>x</groupId>
                  <artifactId>y</artifactId>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                      <release>21</release>
                      <args><arg>p</arg><arg>q</arg></args>
                    </configuration>
                  </plugin>
                </plugins>
              </build>
              <modules>
                <module>m1</module>
              </modules>
            </project>
            """;

    private static final String PARENT = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>g</groupId>
              <artifactId>parent</artifactId>
              <version>1</version>
              <packaging>pom</packaging>
              <properties>
                <a>0</a>
                <b>2</b>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>x</groupId>
                  <artifactId>y</artifactId>
                  <version>3</version>
                </dependency>
                <dependency>
                  <groupId>x</groupId>
                  <artifactId>z</artifactId>
                </dependency>
              </dependencies>
              <modules>
                <module>child</module>
              </modules>
            </project>
            """;

    private PomNode root;

    @Before
    public void build() throws Exception {
        root = PomTreeBuilder.build(List.of(new PomLayer(read(CHILD), null, true, false),
                new PomLayer(read(PARENT), null, false, true)));
    }

    private static Model read(String pom) throws Exception {
        return new MavenXpp3ReaderEx().read(new StringReader(pom), false, new InputSource());
    }

    private static PomNode child(PomNode parent, String elementName) {
        for (PomNode child : parent.children()) {
            if (child.elementName().equals(elementName)) {
                return child;
            }
        }
        return null;
    }

    private static PomNode item(PomNode list, String displayName) {
        for (PomNode child : list.children()) {
            if (child.isListItem() && child.displayName().equals(displayName)) {
                return child;
            }
        }
        return null;
    }

    @Test
    public void mergesFieldsAcrossTheLineage() {
        PomNode groupId = child(root, "groupId");
        assertEquals(Kind.FIELD, groupId.kind());
        assertEquals("GroupId", groupId.displayName());
        assertFalse(groupId.definedInCurrent());
        assertTrue(groupId.definesValue());
        assertEquals("g", groupId.firstValue());
        assertEquals(1, groupId.firstDefinedLayer());
        assertFalse(groupId.overridesParent());

        PomNode artifactId = child(root, "artifactId");
        assertEquals("child", artifactId.firstValue());
        assertEquals("parent", artifactId.valueAt(1));
        assertTrue(artifactId.overridesParent());

        PomNode packaging = child(root, "packaging");
        assertEquals("jar", packaging.firstValue());
        assertFalse("the parents' packaging is not shown", packaging.definedIn(1));

        PomNode description = child(root, "description");
        assertFalse(description.definesValue());
        assertNull(description.firstValue());
    }

    @Test
    public void mergesListItemsByKey() {
        PomNode dependencies = child(root, "dependencies");
        assertEquals(Kind.LIST, dependencies.kind());
        assertTrue(dependencies.definedInCurrent());
        assertEquals(List.of("y", "z"), dependencies.children().stream().map(PomNode::displayName).toList());
        PomNode y = item(dependencies, "y");
        assertTrue(y.isListItem());
        assertTrue(y.definedIn(0));
        assertTrue(y.definedIn(1));
        PomNode version = child(y, "version");
        assertFalse(version.definedInCurrent());
        assertEquals("3", version.firstValue());
        PomNode z = item(dependencies, "z");
        assertFalse(z.definedInCurrent());
        assertTrue(z.definedIn(1));
    }

    @Test
    public void listsPropertiesModulesAndConfiguration() {
        PomNode properties = child(root, "properties");
        assertEquals(List.of("a", "b"), properties.children().stream().map(PomNode::displayName).toList());
        PomNode a = child(properties, "a");
        assertEquals("1", a.valueAt(0));
        assertEquals("0", a.valueAt(1));
        assertTrue(a.overridesParent());
        assertFalse(child(properties, "b").definedInCurrent());

        PomNode modules = child(root, "modules");
        assertEquals(List.of("Module"), modules.children().stream().map(PomNode::displayName).toList());
        assertEquals("m1", modules.children().get(0).firstValue());
        assertFalse("the parents' modules are not shown", modules.definedIn(1));

        PomNode plugin = item(child(child(root, "build"), "plugins"), "maven-compiler-plugin");
        assertNotNull(plugin);
        PomNode configuration = child(plugin, "configuration");
        assertEquals(Kind.OBJECT, configuration.kind());
        PomNode release = child(configuration, "release");
        assertEquals(Kind.FIELD, release.kind());
        assertEquals("21", release.firstValue());
        PomNode args = child(configuration, "args");
        assertEquals(Kind.OBJECT, args.kind());
        // Repeated elements are told apart by their value but, as in NetBeans, labelled by name.
        assertEquals(List.of("arg", "arg"), args.children().stream().map(PomNode::displayName).toList());
        assertEquals(List.of("p", "q"), args.children().stream().map(PomNode::firstValue).toList());
    }

    @Test
    public void locatesRowsInTheText() {
        PomLocator locator = new PomLocator(CHILD);
        assertEquals(CHILD.indexOf("<artifactId>child"), locator.locate(child(root, "artifactId"), 0).start());
        // Containers carry no reader position: they are found as children of the parent's element.
        PomNode dependencies = child(root, "dependencies");
        assertEquals(CHILD.indexOf("<dependencies>"), locator.locate(dependencies, 0).start());
        assertEquals(CHILD.indexOf("<dependency>"), locator.locate(item(dependencies, "y"), 0).start());
        PomNode a = child(child(root, "properties"), "a");
        assertEquals(CHILD.indexOf("<a>"), locator.locate(a, 0).start());
        PomNode release = child(child(item(child(child(root, "build"), "plugins"), "maven-compiler-plugin"), "configuration"), "release");
        assertEquals(CHILD.indexOf("<release>"), locator.locate(release, 0).start());
        assertEquals(CHILD.indexOf("<module>"), locator.locate(child(root, "modules").children().get(0), 0).start());
        // The parent's rows are located in the parent's text, not the child's.
        assertNull(locator.locate(item(dependencies, "z"), 0));
        int secondDependency = PARENT.indexOf("<dependency>", PARENT.indexOf("</dependency>"));
        assertEquals(secondDependency, new PomLocator(PARENT).locate(item(dependencies, "z"), 1).start());

        var elements = locator.map(root, 0);
        XmlNode releaseElement = locator.document().nodeAt(CHILD.indexOf("21</release>"));
        assertSame(release, elements.get(releaseElement));
        assertSame(root, elements.get(locator.document().child("project")));
    }
}
