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
package com.gluonhq.netbeans.nbfx.annotations.processors;

import com.gluonhq.netbeans.nbfx.annotations.FxLayer;
import com.gluonhq.netbeans.nbfx.annotations.FxPropertyEditorRegistration;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import org.openide.filesystems.annotations.LayerBuilder.File;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Generates the layer entry for {@link FxPropertyEditorRegistration}:
 * {@code NbFx/PropertyEditors/<valueType>.instance} with the handled value type as an attribute.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class FxPropertyEditorRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(FxPropertyEditorRegistration.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(FxPropertyEditorRegistration.class)) {
            FxPropertyEditorRegistration registration = element.getAnnotation(FxPropertyEditorRegistration.class);
            if (registration == null) {
                continue;
            }
            String valueType = valueTypeOf(registration);
            String fileName = valueType.replace('.', '-').replace('$', '-');
            File file = layer(element).instanceFile(FxLayer.PROPERTY_EDITORS, fileName, (Class<?>) null);
            file.stringvalue("valueType", valueType);
            file.intvalue("position", registration.position());
            file.boolvalue("inline", registration.inline());
            file.write();
        }
        return true;
    }

    /**
     * The canonical name of the handled value type. The type may not be loadable by the processor,
     * in which case {@link MirroredTypeException} carries the {@link TypeMirror} to read the name
     * from.
     */
    private static String valueTypeOf(FxPropertyEditorRegistration registration) {
        try {
            return registration.valueType().getCanonicalName();
        } catch (MirroredTypeException ex) {
            TypeMirror mirror = ex.getTypeMirror();
            if (mirror instanceof DeclaredType declared) {
                return declared.asElement().toString();
            }
            return mirror.toString();
        }
    }
}
