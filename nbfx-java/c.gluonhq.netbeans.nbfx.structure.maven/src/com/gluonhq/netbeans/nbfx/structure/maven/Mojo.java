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

/**
 * A goal of a plugin the project uses, as NetBeans' {@code GoalsPanel.Mojo}.
 *
 * @param prefix         the plugin's goal prefix ({@code compiler})
 * @param goal           the goal ({@code compile})
 * @param groupId        the plugin's group id
 * @param artifactId     the plugin's artifact id
 * @param version        the plugin's version
 * @param lifecycleBound whether an execution of the POM binds the goal to the lifecycle
 */
record Mojo(String prefix, String goal, String groupId, String artifactId, String version, boolean lifecycleBound)
        implements Comparable<Mojo> {

    /** Whether this is the plugin's {@code help} goal. */
    boolean isHelp() {
        return "help".equals(goal);
    }

    /** The plugin's coordinates, {@code groupId:artifactId:version}. */
    String pluginId() {
        return groupId + ":" + artifactId + ":" + version;
    }

    /** The fully qualified goal, {@code groupId:artifactId:version:goal}, which Maven runs unambiguously. */
    String qualifiedGoal() {
        return pluginId() + ":" + goal;
    }

    @Override
    public int compareTo(Mojo other) {
        int result = prefix.compareTo(other.prefix);
        return result != 0 ? result : goal.compareTo(other.goal);
    }
}
