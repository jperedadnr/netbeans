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
package com.gluonhq.netbeans.nbfx.vcs;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import org.openide.util.NbBundle;

/** Window ▸ Git: opens or fronts the Git view. Always enabled. */
final class SelectGitCommand extends AbstractCommand {

    SelectGitCommand() {
        super(ActionIds.SELECT_GIT,
                NbBundle.getMessage(SelectGitCommand.class, "CTL_SelectGitCommand"), null, false);
    }

    @Override
    public void run() {
        GitStatusViewProvider view = GitStatusViewProvider.instance();
        if (view != null) {
            view.show();
        }
    }
}
