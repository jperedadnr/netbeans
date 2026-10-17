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
package com.gluonhq.netbeans.nbfx.structure.xml;

import com.gluonhq.netbeans.nbfx.structure.FxStructurePanel;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import com.gluonhq.netbeans.nbfx.structure.TrackedStructurePanel;
import org.openide.filesystems.MIMEResolver;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The XML View panel of the Navigator window, for any XML content type: {@code text/xml},
 * {@code application/xml} and every {@code +xml} type (a Maven POM, an FXML document). It is the
 * last panel of a type with panels of its own, as NetBeans' {@code XMLNavigatorPanel} is.
 * <p>
 * FXML files are resolved here to {@code text/x-fxml+xml}, the type NetBeans' JavaFX support
 * gives them, as that support is not part of the application.
 */
@ServiceProvider(service = FxStructurePanel.class, position = 300)
@MIMEResolver.ExtensionRegistration(displayName = "#FxmlResolver", extension = "fxml", mimeType = XmlStructurePanel.FXML_MIME_TYPE)
public final class XmlStructurePanel extends TrackedStructurePanel<XmlViewUI.Result> {

    /** The content type of FXML documents. */
    public static final String FXML_MIME_TYPE = "text/x-fxml+xml";

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(XmlStructurePanel.class, "XML_View");
    }

    @Override
    public String[] contentTypes() {
        return new String[] {"text/xml", "application/xml", FXML_MIME_TYPE};
    }

    @Override
    public boolean handles(String contentType) {
        return contentType != null && (super.handles(contentType) || contentType.endsWith("+xml"));
    }

    @Override
    protected ScanningPanel<XmlViewUI.Result> createUI() {
        return new XmlViewUI();
    }
}
