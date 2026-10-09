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
package com.gluonhq.netbeans.nbfx.plugins;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import org.openide.util.NbBundle;

/** Window ▸ Plugins: opens the Plugins dialog. Always enabled. */
final class SelectPluginsCommand extends AbstractCommand {

    SelectPluginsCommand() {
        super(ActionIds.SELECT_PLUGINS,
                NbBundle.getMessage(SelectPluginsCommand.class, "CTL_SelectPluginsCommand"), null, false);
    }

    @Override
    public void run() {
        PluginsDialog.show();
    }
}
