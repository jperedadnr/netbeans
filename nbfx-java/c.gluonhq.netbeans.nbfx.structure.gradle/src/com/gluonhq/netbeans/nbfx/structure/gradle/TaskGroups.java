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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.netbeans.modules.gradle.api.GradleBaseProject;
import org.netbeans.modules.gradle.api.GradleTask;

/**
 * The tasks of a Gradle project arranged as NetBeans' {@code TasksPanel} arranges them: one group
 * per task group, sorted by name ignoring case, with the tasks that have no group last under
 * <em>Other Tasks</em>; the tasks of a group sorted by name ignoring case.
 */
final class TaskGroups {

    /** The group Gradle gives the tasks without one. */
    static final String PRIVATE_GROUP = GradleBaseProject.PRIVATE_TASK_GROUP;

    private static final Comparator<Task> BY_NAME = Comparator.comparing(Task::name, String.CASE_INSENSITIVE_ORDER);

    private TaskGroups() {
    }

    /** A task: its path ({@code :sub:name}), group, name and description, the last two possibly empty. */
    record Task(String path, String group, String name, String description) {

        static Task of(GradleTask task) {
            return new Task(task.getPath(), task.getGroup(), task.getName(), task.getDescription());
        }
    }

    /** A group of tasks; {@code other} for the tasks without a group. */
    record Group(String name, boolean other, List<Task> tasks) {
    }

    /** Groups {@code tasks}; the tasks without a group, or in Gradle's private group, make the last one. */
    static List<Group> of(Collection<Task> tasks) {
        Map<String, List<Task>> byGroup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Task> other = new ArrayList<>();
        for (Task task : tasks) {
            String group = task.group();
            if (group == null || group.isBlank() || PRIVATE_GROUP.equals(group)) {
                other.add(task);
            } else {
                byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(task);
            }
        }
        List<Group> groups = new ArrayList<>(byGroup.size() + 1);
        byGroup.forEach((name, list) -> groups.add(new Group(name, false, sorted(list))));
        if (!other.isEmpty()) {
            groups.add(new Group(PRIVATE_GROUP, true, sorted(other)));
        }
        return List.copyOf(groups);
    }

    /** {@code name} with its first letter in upper case, as the group rows read. */
    static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int first = name.codePointAt(0);
        int upper = Character.toTitleCase(first);
        return upper == first ? name : new StringBuilder().appendCodePoint(upper).append(name, Character.charCount(first), name.length()).toString();
    }

    private static List<Task> sorted(List<Task> tasks) {
        tasks.sort(BY_NAME);
        return List.copyOf(tasks);
    }
}
