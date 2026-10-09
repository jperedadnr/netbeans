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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A thin client over the {@code git} command line: repository detection, branch, status and commit.
 * Runs the command in {@code dir} and captures its merged output.
 *
 * @since 1.0
 */
final class GitClient {

    /** One changed file: a human-readable status and its path. */
    record Change(String status, String path) {
    }

    /** The exit code and merged output of a git command. */
    record Result(int exitCode, String output) {
    }

    private GitClient() {
    }

    static boolean isRepository(Path dir) {
        return result(dir, "rev-parse", "--is-inside-work-tree").exitCode() == 0;
    }

    static Path rootOf(Path dir) {
        Result result = result(dir, "rev-parse", "--show-toplevel");
        return result.exitCode() == 0 ? Path.of(result.output().trim()) : dir;
    }

    static String branch(Path root) {
        Result result = result(root, "rev-parse", "--abbrev-ref", "HEAD");
        return result.exitCode() == 0 ? result.output().trim() : "";
    }

    static List<Change> status(Path root) throws IOException {
        Result result = result(root, "status", "--porcelain");
        if (result.exitCode() != 0) {
            throw new IOException(result.output());
        }
        List<Change> changes = new ArrayList<>();
        for (String line : result.output().split("\n")) {
            if (line.length() < 4) {
                continue;
            }
            changes.add(new Change(describe(line.substring(0, 2)), line.substring(3).trim()));
        }
        return changes;
    }

    static Result addAll(Path root) {
        return result(root, "add", "-A");
    }

    static Result commit(Path root, String message) {
        return result(root, "commit", "-m", message);
    }

    private static Result result(Path dir, String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-C");
        command.add(dir.toString());
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            return new Result(process.waitFor(), output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(1, "Interrupted");
        } catch (IOException ex) {
            return new Result(1, ex.getMessage());
        }
    }

    /** A human-readable description of a two-character porcelain status. */
    private static String describe(String code) {
        if (code.equals("??")) {
            return "Untracked";
        }
        if (code.equals("!!")) {
            return "Ignored";
        }
        String index = describeOne(code.charAt(0));
        String worktree = describeOne(code.charAt(1));
        if (index.isEmpty()) {
            return worktree.isEmpty() ? code.trim() : worktree;
        }
        return worktree.isEmpty() ? index : index + ", " + worktree;
    }

    private static String describeOne(char status) {
        return switch (status) {
            case 'A' -> "Added";
            case 'M' -> "Modified";
            case 'D' -> "Deleted";
            case 'R' -> "Renamed";
            case 'C' -> "Copied";
            case 'U' -> "Conflicted";
            default -> "";
        };
    }
}
