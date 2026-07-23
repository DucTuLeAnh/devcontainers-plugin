package org.example.devcontainer.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes {@code docker} commands. Kept free of IntelliJ Platform classes so it can run both
 * inside a background task (blocking, streaming output line by line) and be reused for
 * interactive processes attached to the tool window console.
 */
public final class DockerContainerRuntime {

    /**
     * Container state as reported by {@code docker ps -a --filter ... --format {{.ID}}\t{{.State}}}.
     */
    public record ContainerStatus(String id, String state) {
        public boolean isRunning() {
            return "running".equalsIgnoreCase(state);
        }
    }

    /**
     * Runs a docker command to completion, streaming each output line (stdout+stderr merged)
     * to {@code onLine} as it arrives. Blocks the calling thread - callers must run this off
     * the UI thread (e.g. inside {@code Task.Backgroundable}).
     */
    public int runAndStream(List<String> dockerArgs, Path workingDir, Consumer<String> onLine) throws IOException, InterruptedException {
        Process process = startProcess(dockerArgs, workingDir);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onLine.accept(line);
            }
        }
        return process.waitFor();
    }

    /** Runs a docker command to completion and returns the captured stdout, trimmed. */
    public String runAndCapture(List<String> dockerArgs, Path workingDir) throws IOException, InterruptedException {
        StringBuilder out = new StringBuilder();
        runAndStream(dockerArgs, workingDir, line -> out.append(line).append('\n'));
        return out.toString().trim();
    }

    /** Starts a docker command without waiting for it, with stdout+stderr merged into one stream. */
    public Process startProcess(List<String> dockerArgs, Path workingDir) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(dockerArgs);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    /** Returns whether a local image with this tag already exists. */
    public boolean imageExists(String tag, Path workingDir) throws IOException, InterruptedException {
        int exitCode = runAndStream(DockerCommandBuilder.imageInspectArgs(tag), workingDir, line -> { });
        return exitCode == 0;
    }

    /** Looks up the single-container devcontainer for this project via its label, if any. */
    public ContainerStatus findContainer(String projectPath, Path workingDir) throws IOException, InterruptedException {
        String output = runAndCapture(DockerCommandBuilder.psFilterArgs(projectPath), workingDir);
        if (output.isBlank()) {
            return null;
        }
        String firstLine = output.lines().findFirst().orElse("");
        String[] parts = firstLine.split("\t", 2);
        if (parts.length < 2) {
            return null;
        }
        return new ContainerStatus(parts[0], parts[1]);
    }
}
