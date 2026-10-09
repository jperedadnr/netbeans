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
import com.gluonhq.netbeans.nbfx.annotations.FxStatusRegistration;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.openide.filesystems.annotations.LayerBuilder.File;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Generates the layer entry for {@link FxStatusRegistration}:
 * {@code NbFx/Status/<id>.instance} with the element's alignment and position.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class FxStatusRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(FxStatusRegistration.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(FxStatusRegistration.class)) {
            FxStatusRegistration registration = element.getAnnotation(FxStatusRegistration.class);
            if (registration == null) {
                continue;
            }
            File file = layer(element).instanceFile(FxLayer.STATUS, registration.id(), (Class<?>) null);
            file.stringvalue("alignment", registration.alignment().name());
            file.intvalue("position", registration.position());
            file.write();
        }
        return true;
    }
}
