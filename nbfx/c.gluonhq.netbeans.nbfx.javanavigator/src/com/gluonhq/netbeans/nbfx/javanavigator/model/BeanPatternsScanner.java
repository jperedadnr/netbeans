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
package com.gluonhq.netbeans.nbfx.javanavigator.model;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.openide.filesystems.FileObject;

/**
 * Builds the Bean Patterns tree of a Java source file: one {@link BeanPattern} per top-level class
 * or interface, holding the properties, indexed properties and event sets its public methods form,
 * and, nested, the same for its inner classes - what NetBeans' {@code BeanScanningTask} does with
 * {@code PatternAnalyser}. The file is compiled from the editor's text like the Members tree
 * ({@link MembersScanner}); the scan runs on the calling thread, off the FX thread.
 */
public final class BeanPatternsScanner {

    /**
     * The tree of a file.
     *
     * @param file  the scanned file
     * @param roots the top-level classes and interfaces, in source order
     */
    public record Result(FileObject file, List<BeanPattern> roots) {
    }

    private BeanPatternsScanner() {
    }

    /**
     * Scans {@code text}, the current content of {@code file}.
     *
     * @return the tree, or {@code null} when the scan was cancelled or the file could not be compiled
     * @throws IOException when the in-memory copy or the compilation fails
     */
    public static Result scan(FileObject file, String text, Cancellation cancellation) throws IOException {
        JavaSources sources = JavaSources.of(file, text);
        if (sources == null) {
            return null;
        }
        List<BeanPattern> roots = new ArrayList<>();
        boolean[] completed = {false};
        sources.run(controller -> {
            if (cancellation.isCancelled()) {
                return;
            }
            BeanPatternAnalyser analyser = new BeanPatternAnalyser(controller, cancellation);
            for (TypeElement type : controller.getTopLevelElements()) {
                if (cancellation.isCancelled()) {
                    return;
                }
                if (type.getKind() == ElementKind.CLASS || type.getKind() == ElementKind.INTERFACE) {
                    roots.add(analyser.analyse(type));
                }
            }
            completed[0] = !cancellation.isCancelled();
        });
        return completed[0] ? new Result(file, List.copyOf(roots)) : null;
    }
}
