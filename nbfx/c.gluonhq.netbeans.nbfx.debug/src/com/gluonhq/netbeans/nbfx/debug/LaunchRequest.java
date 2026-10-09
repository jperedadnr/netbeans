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
package com.gluonhq.netbeans.nbfx.debug;

import org.netbeans.api.java.classpath.ClassPath;
import org.openide.filesystems.FileObject;

/**
 * What the launcher needs to start a debug session: the main class and its runtime classpath, the
 * working directory and source path, plus an optional breakpoint on the main file.
 *
 * @param mainClass      the fully-qualified main class
 * @param classpath      the runtime classpath, in platform path-separator form
 * @param workDir        the debuggee's working directory
 * @param sourcePath     the sources classpath, for resolving source files
 * @param mainFile       the main class's source file, or {@code null}
 * @param breakpointLine a one-based line for a breakpoint on {@code mainFile}, or 0 for none
 * @since 1.0
 */
record LaunchRequest(String mainClass, String classpath, java.nio.file.Path workDir,
        ClassPath sourcePath, FileObject mainFile, int breakpointLine) {
}
