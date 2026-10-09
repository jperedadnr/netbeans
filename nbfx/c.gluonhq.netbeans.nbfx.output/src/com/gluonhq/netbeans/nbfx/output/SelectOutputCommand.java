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
package com.gluonhq.netbeans.nbfx.output;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import org.openide.util.NbBundle;

/** Window ▸ Output: opens or fronts the Output view. Always enabled. */
final class SelectOutputCommand extends AbstractCommand {

    SelectOutputCommand() {
        super(ActionIds.SELECT_OUTPUT,
                NbBundle.getMessage(SelectOutputCommand.class, "CTL_SelectOutputCommand"), null, false);
    }

    @Override
    public void run() {
        OutputViewProvider view = OutputViewProvider.instance();
        if (view != null) {
            view.show();
        }
    }
}
