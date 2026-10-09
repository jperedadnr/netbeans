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
package com.gluonhq.netbeans.nbfx.debug;

import com.gluonhq.netbeans.nbfx.output.FxConsole;
import com.gluonhq.netbeans.nbfx.output.FxOutput;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.netbeans.api.debugger.ActionsManager;
import org.netbeans.api.debugger.DebuggerEngine;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.jpda.CallStackFrame;
import org.netbeans.api.debugger.jpda.DebuggerStartException;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.api.debugger.jpda.JPDAThreadGroup;
import org.netbeans.api.debugger.jpda.LineBreakpoint;
import org.netbeans.api.debugger.jpda.LocalVariable;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * A JPDA debug session driven through the headless NetBeans debugger API. It launches the debuggee
 * JVM with a JDWP agent, attaches to it, and exposes the current threads, call stack and variables
 * as observable state for the JavaFX Debug view.
 *
 * @since 1.0
 */
public final class DebugSession {

    private static final Logger LOG = Logger.getLogger(DebugSession.class.getName());
    private static final String CONSOLE_PREFIX = "Debug ";

    private static volatile DebugSession current;

    private final JPDADebugger debugger;
    private final Process process;
    private final DebuggerEngine engine;

    private final ObservableList<JPDAThread> threads = FXCollections.observableArrayList();
    private final ObjectProperty<JPDAThread> selectedThread = new SimpleObjectProperty<>();
    private final ObservableList<CallStackFrame> frames = FXCollections.observableArrayList();
    private final ObjectProperty<CallStackFrame> selectedFrame = new SimpleObjectProperty<>();
    private final ObservableList<VariableRow> variables = FXCollections.observableArrayList();
    private final StringProperty status = new SimpleStringProperty("");

    private DebugSession(JPDADebugger debugger, Process process) {
        this.debugger = debugger;
        this.process = process;
        this.engine = engineFor(debugger);
        debugger.addPropertyChangeListener(event -> refresh());
        selectedThread.addListener((observable, old, now) -> {
            if (now != null) {
                loadFrames(now);
            }
        });
        selectedFrame.addListener((observable, old, now) -> {
            if (now != null) {
                loadVariables(now);
            }
        });
    }

    /** The current session, or {@code null} when no debug session is active. */
    public static DebugSession current() {
        return current;
    }

    /**
     * Starts and attaches to a debug session for {@code request}.
     *
     * @throws IOException if the debuggee process cannot be started
     * @throws DebuggerStartException if the debugger cannot attach
     */
    public static DebugSession launch(LaunchRequest request) throws IOException, DebuggerStartException {
        Process process = startProcess(request);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        int port = readPort(reader, request);
        FxConsole console = console(request);
        startPump(reader, console);

        Map<String, Object> services = new HashMap<>();
        services.put("sourcepath", request.sourcePath());
        services.put("baseDir", request.workDir().toFile());
        services.put("name", request.mainClass());
        JPDADebugger debugger = JPDADebugger.attach("localhost", port, new Object[] {services});

        DebugSession session = new DebugSession(debugger, process);
        if (request.mainFile() != null && request.breakpointLine() > 0) {
            session.addBreakpoint(request.mainFile(), request.breakpointLine());
        }
        current = session;
        DebugStatus.ACTIVE.set(true);
        session.refresh();
        return session;
    }

    /** The current debugger, for the view to bind to. */
    public JPDADebugger debugger() {
        return debugger;
    }

    public ObservableList<JPDAThread> threads() {
        return threads;
    }

    public ObjectProperty<JPDAThread> selectedThreadProperty() {
        return selectedThread;
    }

    public ObservableList<CallStackFrame> frames() {
        return frames;
    }

    public ObjectProperty<CallStackFrame> selectedFrameProperty() {
        return selectedFrame;
    }

    public ObservableList<VariableRow> variables() {
        return variables;
    }

    public StringProperty statusProperty() {
        return status;
    }

    public boolean canControl() {
        int state = debugger.getState();
        return state == JPDADebugger.STATE_STOPPED || state == JPDADebugger.STATE_RUNNING;
    }

    public void resume() {
        action(ActionsManager.ACTION_CONTINUE);
    }

    public void stepOver() {
        action(ActionsManager.ACTION_STEP_OVER);
    }

    public void stepInto() {
        action(ActionsManager.ACTION_STEP_INTO);
    }

    public void stepOut() {
        action(ActionsManager.ACTION_STEP_OUT);
    }

    /** Stops the session and kills the debuggee. */
    public void stop() {
        if (engine != null) {
            engine.getActionsManager().doAction(ActionsManager.ACTION_KILL);
        }
        if (process.isAlive()) {
            process.destroy();
        }
        if (current == this) {
            current = null;
            DebugStatus.ACTIVE.set(false);
        }
    }

    /** Adds a line breakpoint on {@code file}. */
    public void addBreakpoint(FileObject file, int line) {
        LineBreakpoint breakpoint = LineBreakpoint.create(urlOf(file), line);
        DebuggerManager.getDebuggerManager().addBreakpoint(breakpoint);
    }

    private void action(Object action) {
        if (engine == null || !canControl()) {
            return;
        }
        engine.getActionsManager().doAction(action);
    }

    private void refresh() {
        Platform.runLater(() -> {
            status.set(stateText(debugger.getState()));
            try {
                JPDAThread thread = debugger.getCurrentThread();
                threads.setAll(allThreads(thread));
                selectedThread.set(thread);
                if (thread != null) {
                    loadFrames(thread);
                } else {
                    frames.clear();
                    variables.clear();
                }
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Cannot read the debugger state", ex);
            }
        });
    }

    /** Every thread, collected from the root thread group of {@code thread}. */
    private static List<JPDAThread> allThreads(JPDAThread thread) {
        if (thread == null) {
            return List.of();
        }
        JPDAThreadGroup group = thread.getParentThreadGroup();
        while (group != null && group.getParentThreadGroup() != null) {
            group = group.getParentThreadGroup();
        }
        List<JPDAThread> result = new ArrayList<>();
        collectThreads(group, result);
        return result;
    }

    private static void collectThreads(JPDAThreadGroup group, List<JPDAThread> out) {
        if (group == null) {
            return;
        }
        out.addAll(Arrays.asList(group.getThreads()));
        for (JPDAThreadGroup child : group.getThreadGroups()) {
            collectThreads(child, out);
        }
    }

    private void loadFrames(JPDAThread thread) {
        try {
            CallStackFrame[] stack = thread.getCallStack();
            frames.setAll(stack);
            if (stack.length > 0) {
                selectedFrame.set(stack[0]);
                loadVariables(stack[0]);
            } else {
                selectedFrame.set(null);
                variables.clear();
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Cannot read the call stack", ex);
        }
    }

    private void loadVariables(CallStackFrame frame) {
        List<VariableRow> rows = new ArrayList<>();
        try {
            for (LocalVariable variable : frame.getLocalVariables()) {
                rows.add(row(variable));
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Cannot read the variables", ex);
        }
        variables.setAll(rows);
    }

    private static VariableRow row(LocalVariable variable) {
        String value;
        try {
            value = variable.getValue();
        } catch (RuntimeException ex) {
            value = "?";
        }
        return new VariableRow(variable.getName(), variable.getType(), value);
    }

    private static DebuggerEngine engineFor(JPDADebugger debugger) {
        for (DebuggerEngine candidate : DebuggerManager.getDebuggerManager().getDebuggerEngines()) {
            if (candidate.lookupFirst(null, JPDADebugger.class) == debugger) {
                return candidate;
            }
        }
        return null;
    }

    private static Process startProcess(LaunchRequest request) throws IOException {
        List<String> command = List.of(
                javaBinary(),
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:0",
                "-cp", request.classpath(),
                request.mainClass());
        return new ProcessBuilder(command)
                .directory(request.workDir().toFile())
                .redirectErrorStream(true)
                .start();
    }

    private static int readPort(BufferedReader reader, LaunchRequest request) throws IOException {
        StringBuilder seen = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            seen.append(line).append('\n');
            int marker = line.indexOf("address:");
            if (marker >= 0) {
                String address = line.substring(marker + "address:".length()).trim();
                int colon = address.lastIndexOf(':');
                String portText = (colon >= 0 ? address.substring(colon + 1) : address).trim();
                try {
                    return Integer.parseInt(portText);
                } catch (NumberFormatException ex) {
                    // Not the listening line; keep reading.
                }
            }
        }
        throw new IOException("The debuggee did not start: " + seen);
    }

    private static void startPump(BufferedReader reader, FxConsole console) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (console != null) {
                        console.append(line + "\n");
                    }
                }
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Debuggee output ended", ex);
            }
        }, "nbfx-debug-output");
        thread.setDaemon(true);
        thread.start();
    }

    private static FxConsole console(LaunchRequest request) {
        FxOutput output = Lookup.getDefault().lookup(FxOutput.class);
        return output == null ? null : output.console(CONSOLE_PREFIX + request.mainClass());
    }

    private static String urlOf(FileObject file) {
        try {
            return file.toURI().toURL().toString();
        } catch (IOException ex) {
            return file.toString();
        }
    }

    private static String javaBinary() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    private static String stateText(int state) {
        return switch (state) {
            case JPDADebugger.STATE_STARTING -> message("Debug.state.starting");
            case JPDADebugger.STATE_RUNNING -> message("Debug.state.running");
            case JPDADebugger.STATE_STOPPED -> message("Debug.state.stopped");
            case JPDADebugger.STATE_DISCONNECTED -> message("Debug.state.disconnected");
            default -> "";
        };
    }

    private static String message(String key) {
        return NbBundle.getMessage(DebugSession.class, key);
    }
}
