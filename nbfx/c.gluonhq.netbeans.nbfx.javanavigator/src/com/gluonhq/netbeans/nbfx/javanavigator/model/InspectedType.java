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

import java.util.Objects;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.ElementHandle;
import org.openide.filesystems.FileObject;

/**
 * A type the user inspected (Navigate &#9656; Inspect &#9656; Members): the source file declaring it
 * and the type itself, kept as a handle so it resolves again in a later compilation. An entry of the
 * Inspect Members history; two entries are the same when they name the same type in the same file.
 *
 * @param file   the source file declaring the type
 * @param handle the type
 */
public record InspectedType(FileObject file, ElementHandle<TypeElement> handle) {

    public InspectedType {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(handle, "handle");
    }

    /** The fully qualified name, e.g. {@code java.util.Map.Entry}. */
    public String getQualifiedName() {
        return handle.getQualifiedName();
    }

    /** The simple name, shown in the history. */
    public String getSimpleName() {
        String qualified = getQualifiedName();
        int separator = Math.max(qualified.lastIndexOf('.'), qualified.lastIndexOf('$'));
        return separator < 0 ? qualified : qualified.substring(separator + 1);
    }

    /** What precedes the simple name - the package and the enclosing types - or the empty string. */
    public String getEnclosingName() {
        String qualified = getQualifiedName();
        int separator = Math.max(qualified.lastIndexOf('.'), qualified.lastIndexOf('$'));
        return separator < 0 ? "" : qualified.substring(0, separator);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InspectedType that
                && file.equals(that.file) && getQualifiedName().equals(that.getQualifiedName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, getQualifiedName());
    }

    @Override
    public String toString() {
        return getQualifiedName();
    }
}
