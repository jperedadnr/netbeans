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
package com.gluonhq.netbeans.nbfx.api.elements;

import java.util.Objects;
import org.openide.filesystems.FileObject;

/**
 * Where a source element is declared: a file and an offset in it - that of the declared name, in
 * the {@code '\n'}-counted offsets of the editor and the NetBeans source model.
 *
 * @param file   the file declaring the element
 * @param offset the offset of the declaration in the file
 */
public record SourceLocation(FileObject file, int offset) {

    public SourceLocation {
        Objects.requireNonNull(file, "file");
        if (offset < 0) {
            throw new IllegalArgumentException("offset out of bounds: " + offset);
        }
    }
}
