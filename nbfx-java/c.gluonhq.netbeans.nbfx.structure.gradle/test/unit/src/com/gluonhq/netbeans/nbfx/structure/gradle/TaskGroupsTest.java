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

import com.gluonhq.netbeans.nbfx.structure.gradle.TaskGroups.Group;
import com.gluonhq.netbeans.nbfx.structure.gradle.TaskGroups.Task;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Checks how {@link TaskGroups} arranges a project's tasks. */
public class TaskGroupsTest {

    private static Task task(String name, String group) {
        return new Task(":" + name, group, name, null);
    }

    @Test
    public void groupsSortedIgnoringCaseWithOtherLast() {
        List<Group> groups = TaskGroups.of(List.of(
                task("compileJava", TaskGroups.PRIVATE_GROUP),
                task("javadoc", "documentation"),
                task("build", "build"),
                task("help", "Help"),
                task("assemble", "build")));
        assertEquals(List.of("build", "documentation", "Help", TaskGroups.PRIVATE_GROUP), groups.stream().map(Group::name).toList());
        assertTrue(groups.get(3).other());
        assertFalse(groups.get(0).other());
    }

    @Test
    public void tasksSortedByNameIgnoringCase() {
        List<Group> groups = TaskGroups.of(List.of(
                task("Zip", "build"), task("assemble", "build"), task("build", "build"), task("Clean", "build")));
        assertEquals(1, groups.size());
        assertEquals(List.of("assemble", "build", "Clean", "Zip"), groups.get(0).tasks().stream().map(Task::name).toList());
    }

    @Test
    public void tasksWithoutGroupGoToOther() {
        List<Group> groups = TaskGroups.of(List.of(task("a", null), task("b", ""), task("c", "  "), task("d", "<private>")));
        assertEquals(1, groups.size());
        assertTrue(groups.get(0).other());
        assertEquals(List.of("a", "b", "c", "d"), groups.get(0).tasks().stream().map(Task::name).toList());
    }

    @Test
    public void noTasksNoGroups() {
        assertTrue(TaskGroups.of(List.of()).isEmpty());
    }

    @Test
    public void capitalize() {
        assertEquals("Build", TaskGroups.capitalize("build"));
        assertEquals("Help", TaskGroups.capitalize("Help"));
        assertEquals("", TaskGroups.capitalize(null));
        assertEquals("", TaskGroups.capitalize(""));
        assertEquals("Ñandú", TaskGroups.capitalize("ñandú"));
    }
}
