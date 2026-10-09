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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

/**
 * Verifies the console registry and the accumulation of console text.
 *
 * @since 1.0
 */
public class FxOutputTest {

    @Test
    public void sameNameYieldsSameConsole() {
        FxOutputImpl output = new FxOutputImpl();

        assertSame(output.console("Build"), output.console("Build"));
        assertEquals(1, output.consoles().size());
    }

    @Test
    public void consoleAccumulatesAndClears() {
        FxOutputImpl output = new FxOutputImpl();
        ConsoleModel console = (ConsoleModel) output.console("Run");
        StringBuilder sink = new StringBuilder();

        console.attach(sink::append, () -> sink.setLength(0), () -> {
        });
        console.append("hello");
        console.append(" world");
        assertEquals("hello world", sink.toString());

        console.clear();
        assertEquals("", sink.toString());
    }
}
