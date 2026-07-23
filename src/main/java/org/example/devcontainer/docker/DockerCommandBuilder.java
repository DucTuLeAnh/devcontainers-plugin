package org.example.devcontainer.docker;

import org.example.devcontainer.config.DevContainerConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds {@code docker} / {@code docker compose} argument lists from a {@link DevContainerConfig}.
 * Pure functions (no process execution, no IntelliJ API) so they can be unit tested directly.
 * Every returned list is the argv <em>after</em> the leading {@code docker} executable name.
 */
public final class DockerCommandBuilder {

    public static final String MANAGED_BY_LABEL = "devcontainer.managed-by";
    public static final String MANAGED_BY_VALUE = "devcontainers-plugin";
    public static final String PROJECT_PATH_LABEL = "devcontainer.project-path";

    /** Fixed JDWP debug port, always published on single-container devcontainers so "Debug in
     *  Dev Container" can attach without needing to recreate the container per debug session. */
    public static final int DEBUG_PORT = 5005;

    private static final String EXEC_MAVEN_GOAL = "org.codehaus.mojo:exec-maven-plugin:3.1.0:java";

    private DockerCommandBuilder() {
    }

    public static String sanitizeName(String name) {
        String sanitized = name.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        return sanitized.isBlank() ? "devcontainer" : sanitized;
    }

    public static String containerName(String projectName) {
        return "devcontainer-" + sanitizeName(projectName);
    }

    public static String composeProjectName(String projectName) {
        return sanitizeName(projectName);
    }

    public static String workspaceFolder(DevContainerConfig config, String projectName) {
        if (config.workspaceFolder != null && !config.workspaceFolder.isBlank()) {
            return config.workspaceFolder;
        }
        return "/workspaces/" + sanitizeName(projectName);
    }

    /** {@code docker ps -a --filter label=... --format {{.ID}}\t{{.State}}} for this project's container. */
    public static List<String> psFilterArgs(String projectPath) {
        List<String> args = new ArrayList<>();
        args.add("ps");
        args.add("-a");
        args.add("--filter");
        args.add("label=" + PROJECT_PATH_LABEL + "=" + projectPath);
        args.add("--format");
        args.add("{{.ID}}\t{{.State}}");
        return args;
    }

    public static List<String> startExistingArgs(String containerId) {
        return List.of("start", containerId);
    }

    public static List<String> stopArgs(String containerId) {
        return List.of("stop", containerId);
    }

    public static List<String> pullArgs(String image) {
        return List.of("pull", image);
    }

    public static List<String> buildImageArgs(DevContainerConfig config, Path projectDir, String tag, boolean noCache) {
        List<String> args = new ArrayList<>();
        args.add("build");
        if (noCache) {
            args.add("--no-cache");
        }
        args.add("-f");
        args.add(projectDir.resolve(".devcontainer").resolve(config.build.dockerfile).toString());
        args.add("-t");
        args.add(tag);
        args.add(projectDir.resolve(".devcontainer").resolve(config.build.context).normalize().toString());
        return args;
    }

    public static String imageTag(String projectName) {
        return "devcontainer-" + sanitizeName(projectName) + ":latest";
    }

    public static List<String> imageInspectArgs(String tag) {
        return List.of("image", "inspect", tag);
    }

    /** {@code docker run -d ...} to (re)create the single-container devcontainer. */
    public static List<String> runArgs(DevContainerConfig config, Path projectDir, String projectName, String imageToRun) {
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("-d");
        args.add("--name");
        args.add(containerName(projectName));
        args.add("--label");
        args.add(MANAGED_BY_LABEL + "=" + MANAGED_BY_VALUE);
        args.add("--label");
        args.add(PROJECT_PATH_LABEL + "=" + projectDir.toString());
        args.add("-v");
        args.add(projectDir + ":" + workspaceFolder(config, projectName));
        args.add("-w");
        args.add(workspaceFolder(config, projectName));

        if (config.containerEnv != null) {
            for (Map.Entry<String, String> env : config.containerEnv.entrySet()) {
                args.add("-e");
                args.add(env.getKey() + "=" + env.getValue());
            }
        }
        boolean debugPortAlreadyMapped = false;
        if (config.forwardPorts != null) {
            for (String port : config.forwardPorts) {
                String hostPart = port.contains(":") ? port.substring(0, port.indexOf(':')) : port;
                if (hostPart.equals(String.valueOf(DEBUG_PORT))) {
                    debugPortAlreadyMapped = true;
                }
                args.add("-p");
                args.add(port.contains(":") ? port : port + ":" + port);
            }
        }
        if (!debugPortAlreadyMapped) {
            args.add("-p");
            args.add(DEBUG_PORT + ":" + DEBUG_PORT);
        }
        if (config.runArgs != null) {
            args.addAll(config.runArgs);
        }

        args.add(imageToRun);
        args.add("sleep");
        args.add("infinity");
        return args;
    }

    public static List<String> removeContainerArgs(String containerId) {
        return List.of("rm", "-f", containerId);
    }

    /** {@code docker exec ...} running postCreateCommand or an interactive shell in the container. */
    public static List<String> execArgs(String containerId, List<String> command, boolean interactive) {
        List<String> args = new ArrayList<>();
        args.add("exec");
        args.add(interactive ? "-it" : "-i");
        args.add(containerId);
        args.addAll(command);
        return args;
    }

    /**
     * Runs {@code mainClass} inside the container via the exec-maven-plugin (invoked ad-hoc, no
     * entry needed in the project's own pom.xml), using Maven's own resolved classpath. If
     * {@code debugPort} is non-null, a JDWP agent listening on that port is attached via
     * {@code MAVEN_OPTS} so a "Remote JVM Debug" configuration can connect to it.
     */
    public static List<String> execAppArgs(String containerId, String mainClass, Integer debugPort) {
        List<String> args = new ArrayList<>();
        args.add("exec");
        args.add("-i");
        if (debugPort != null) {
            args.add("-e");
            args.add(jdwpMavenOpts(debugPort));
        }
        args.add(containerId);
        args.addAll(execMavenGoalArgs(mainClass));
        return args;
    }

    public static List<String> composeExecAppArgs(DevContainerConfig config, Path projectDir, String projectName,
                                                   String mainClass, Integer debugPort) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("exec");
        args.add("-iT");
        if (debugPort != null) {
            args.add("-e");
            args.add(jdwpMavenOpts(debugPort));
        }
        args.add(config.service);
        args.addAll(execMavenGoalArgs(mainClass));
        return args;
    }

    private static String jdwpMavenOpts(int debugPort) {
        return "MAVEN_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:" + debugPort;
    }

    private static List<String> execMavenGoalArgs(String mainClass) {
        return List.of("mvn", "-q", EXEC_MAVEN_GOAL, "-Dexec.mainClass=" + mainClass, "-Dexec.classpathScope=compile");
    }

    /** Converts postCreateCommand (String or List&lt;String&gt;) into an argv suitable for {@link #execArgs}. */
    @SuppressWarnings("unchecked")
    public static List<String> commandToArgv(Object postCreateCommand) {
        if (postCreateCommand instanceof List) {
            return (List<String>) postCreateCommand;
        }
        if (postCreateCommand instanceof String s) {
            return List.of("sh", "-c", s);
        }
        return List.of();
    }

    // --- Docker Compose variant ---

    public static List<String> composeBaseArgs(DevContainerConfig config, Path projectDir, String projectName) {
        List<String> args = new ArrayList<>();
        args.add("compose");
        for (String file : config.dockerComposeFile) {
            args.add("-f");
            args.add(projectDir.resolve(".devcontainer").resolve(file).toString());
        }
        args.add("-p");
        args.add(composeProjectName(projectName));
        return args;
    }

    public static List<String> composeUpArgs(DevContainerConfig config, Path projectDir, String projectName) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("up");
        args.add("-d");
        args.add(config.service);
        return args;
    }

    public static List<String> composeUpForceRecreateArgs(DevContainerConfig config, Path projectDir, String projectName) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("up");
        args.add("-d");
        args.add("--force-recreate");
        args.add(config.service);
        return args;
    }

    public static List<String> composeBuildArgs(DevContainerConfig config, Path projectDir, String projectName) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("build");
        args.add("--no-cache");
        args.add(config.service);
        return args;
    }

    public static List<String> composeStopArgs(DevContainerConfig config, Path projectDir, String projectName) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("stop");
        args.add(config.service);
        return args;
    }

    public static List<String> composePsIdArgs(DevContainerConfig config, Path projectDir, String projectName) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("ps");
        args.add("-q");
        args.add(config.service);
        return args;
    }

    public static List<String> composeExecArgs(DevContainerConfig config, Path projectDir, String projectName,
                                                List<String> command, boolean interactive) {
        List<String> args = new ArrayList<>(composeBaseArgs(config, projectDir, projectName));
        args.add("exec");
        if (interactive) {
            args.add("-it");
        } else {
            args.add("-iT");
        }
        args.add(config.service);
        args.addAll(command);
        return args;
    }
}
