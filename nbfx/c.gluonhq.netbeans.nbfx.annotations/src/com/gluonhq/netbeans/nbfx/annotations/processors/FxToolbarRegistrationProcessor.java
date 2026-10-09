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
import com.gluonhq.netbeans.nbfx.annotations.FxToolbarRegistration;
import com.gluonhq.netbeans.nbfx.annotations.FxToolbarRegistrations;
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
 * Generates the layer entry for {@link FxToolbarRegistration}: the
 * {@code NbFx/Toolbars/<id>} folder with its {@code position} (and optional display name), so the
 * window can build the set and order of tool bars from the layer.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class FxToolbarRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                FxToolbarRegistration.class.getCanonicalName(),
                FxToolbarRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(FxToolbarRegistration.class)) {
            FxToolbarRegistration registration = element.getAnnotation(FxToolbarRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(FxToolbarRegistrations.class)) {
            FxToolbarRegistrations registrations = element.getAnnotation(FxToolbarRegistrations.class);
            if (registrations != null) {
                for (FxToolbarRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, FxToolbarRegistration registration)
            throws LayerGenerationException {
        File folder = layer(element).folder(FxLayer.TOOLBARS + "/" + registration.id());
        folder.intvalue("position", registration.position());
        if (!registration.displayName().isEmpty()) {
            folder.stringvalue("displayName", registration.displayName());
        }
        folder.write();
    }
}
