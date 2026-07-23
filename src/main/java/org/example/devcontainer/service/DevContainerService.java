package org.example.devcontainer.service;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.example.devcontainer.config.DevContainerConfig;
import org.example.devcontainer.config.DevContainerConfigParser;
import org.example.devcontainer.docker.DockerCommandBuilder;
import org.example.devcontainer.docker.DockerContainerRuntime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Project-level orchestration: loads devcontainer.json and drives the Docker CLI to start,
 * stop and rebuild the devcontainer. All public methods here do blocking I/O and must be
 * called from a background thread (e.g. from a {@code Task.Backgroundable}), never the UI thread.
 */
@Service(Service.Level.PROJECT)
public final class DevContainerService {

    private static final String NOTIFICATION_GROUP = "Dev Container";
    private static final String MAIN_CLASS_PROPERTY = "org.example.devcontainer.mainClass";

    private final Project project;
    private final DockerContainerRuntime runtime = new DockerContainerRuntime();
    private volatile Consumer<String> outputSink = line -> { };
    private volatile DevContainerConfig lastConfig;
    private volatile Process appProcess;

    public DevContainerService(Project project) {
        this.project = project;
    }

    public static DevContainerService getInstance(Project project) {
        return project.getService(DevContainerService.class);
    }

    /** Registers where console output lines should go; the tool window panel sets this. */
    public void setOutputSink(Consumer<String> sink) {
        this.outputSink = sink != null ? sink : line -> { };
    }

    private void log(String line) {
        outputSink.accept(line);
    }

    private void notify(String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(content, type)
                .notify(project);
    }

    public Path projectDir() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new IllegalStateException("Project has no base path");
        }
        return Path.of(basePath);
    }

    public String projectName() {
        return project.getName();
    }

    public Path devcontainerJsonPath() {
        return projectDir().resolve(".devcontainer").resolve("devcontainer.json");
    }

    public DevContainerConfig loadConfig() throws IOException {
        Path path = devcontainerJsonPath();
        if (!Files.exists(path)) {
            throw new IOException("No .devcontainer/devcontainer.json found at " + path);
        }
        lastConfig = DevContainerConfigParser.parse(path);
        return lastConfig;
    }

    public DevContainerConfig lastConfig() {
        return lastConfig;
    }

    /** The FQCN to run/debug inside the container, as configured in the tool window. */
    public String configuredMainClass() {
        String stored = PropertiesComponent.getInstance(project).getValue(MAIN_CLASS_PROPERTY, "");
        if (!stored.isBlank()) {
            return stored;
        }
        return guessMainClassFromExistingRunConfigurations();
    }

    public void setConfiguredMainClass(String mainClass) {
        PropertiesComponent.getInstance(project).setValue(MAIN_CLASS_PROPERTY, mainClass);
    }

    /** Best-effort: reuse the main class from an existing local "Application" run configuration. */
    private String guessMainClassFromExistingRunConfigurations() {
        for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
            if (settings.getConfiguration() instanceof ApplicationConfiguration appConfig) {
                String mainClass = appConfig.getMainClassName();
                if (mainClass != null && !mainClass.isBlank()) {
                    return mainClass;
                }
            }
        }
        return "";
    }

    /** Starts the devcontainer, creating and running postCreateCommand only on first creation. */
    public void start() {
        try {
            DevContainerConfig config = loadConfig();
            Path projectDir = projectDir();
            String projectName = projectName();

            if (config.isCompose()) {
                startCompose(config, projectDir, projectName);
            } else {
                startSingleContainer(config, projectDir, projectName);
            }
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            notify("Failed to start dev container: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    private void startSingleContainer(DevContainerConfig config, Path projectDir, String projectName)
            throws IOException, InterruptedException {
        DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
        if (status != null && status.isRunning()) {
            log("Dev container is already running (" + status.id() + ").");
            notify("Dev container already running", NotificationType.INFORMATION);
            return;
        }
        if (status != null) {
            run(DockerCommandBuilder.startExistingArgs(status.id()), projectDir);
            notify("Dev container started", NotificationType.INFORMATION);
            return;
        }

        String image = resolveImage(config, projectDir, projectName, false);
        run(DockerCommandBuilder.runArgs(config, projectDir, projectName, image), projectDir);
        runPostCreateSingleContainer(config, projectName, projectDir);
        notify("Dev container created and started", NotificationType.INFORMATION);
    }

    private void startCompose(DevContainerConfig config, Path projectDir, String projectName)
            throws IOException, InterruptedException {
        run(DockerCommandBuilder.composeUpArgs(config, projectDir, projectName), projectDir);
        runPostCreateCompose(config, projectDir, projectName);
        notify("Dev container (compose) started", NotificationType.INFORMATION);
    }

    public void stop() {
        stopApp();
        try {
            DevContainerConfig config = loadConfig();
            Path projectDir = projectDir();
            String projectName = projectName();

            if (config.isCompose()) {
                run(DockerCommandBuilder.composeStopArgs(config, projectDir, projectName), projectDir);
            } else {
                DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
                if (status == null) {
                    log("No dev container found for this project.");
                    notify("No dev container to stop", NotificationType.INFORMATION);
                    return;
                }
                run(DockerCommandBuilder.stopArgs(status.id()), projectDir);
            }
            notify("Dev container stopped", NotificationType.INFORMATION);
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            notify("Failed to stop dev container: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    /** Rebuilds the image (or pulls the latest one) and force-recreates the container. */
    public void rebuild() {
        try {
            DevContainerConfig config = loadConfig();
            Path projectDir = projectDir();
            String projectName = projectName();

            if (config.isCompose()) {
                run(DockerCommandBuilder.composeBuildArgs(config, projectDir, projectName), projectDir);
                run(DockerCommandBuilder.composeUpForceRecreateArgs(config, projectDir, projectName), projectDir);
                runPostCreateCompose(config, projectDir, projectName);
            } else {
                DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
                if (status != null) {
                    run(DockerCommandBuilder.removeContainerArgs(status.id()), projectDir);
                }
                String image = resolveImage(config, projectDir, projectName, true);
                run(DockerCommandBuilder.runArgs(config, projectDir, projectName, image), projectDir);
                runPostCreateSingleContainer(config, projectName, projectDir);
            }
            notify("Dev container rebuilt", NotificationType.INFORMATION);
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            notify("Failed to rebuild dev container: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    private String resolveImage(DevContainerConfig config, Path projectDir, String projectName, boolean forceRebuild)
            throws IOException, InterruptedException {
        if (!config.isDockerfileBased()) {
            if (forceRebuild) {
                run(DockerCommandBuilder.pullArgs(config.image), projectDir);
            }
            return config.image;
        }
        String tag = DockerCommandBuilder.imageTag(projectName);
        if (forceRebuild || !runtime.imageExists(tag, projectDir)) {
            run(DockerCommandBuilder.buildImageArgs(config, projectDir, tag, forceRebuild), projectDir);
        }
        return tag;
    }

    private void runPostCreateSingleContainer(DevContainerConfig config, String projectName, Path projectDir)
            throws IOException, InterruptedException {
        if (config.postCreateCommand == null) {
            return;
        }
        DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
        if (status == null) {
            log("WARNING: could not locate container to run postCreateCommand");
            return;
        }
        List<String> command = DockerCommandBuilder.commandToArgv(config.postCreateCommand);
        run(DockerCommandBuilder.execArgs(status.id(), command, false), projectDir);
    }

    private void runPostCreateCompose(DevContainerConfig config, Path projectDir, String projectName)
            throws IOException, InterruptedException {
        if (config.postCreateCommand == null) {
            return;
        }
        List<String> command = DockerCommandBuilder.commandToArgv(config.postCreateCommand);
        run(DockerCommandBuilder.composeExecArgs(config, projectDir, projectName, command, false), projectDir);
    }

    private void run(List<String> dockerArgs, Path workingDir) throws IOException, InterruptedException {
        log("$ docker " + String.join(" ", dockerArgs));
        int exitCode = runtime.runAndStream(dockerArgs, workingDir, this::log);
        if (exitCode != 0) {
            throw new IOException("docker " + dockerArgs.get(0) + " failed with exit code " + exitCode);
        }
    }

    /**
     * Starts an interactive shell process attached to the running devcontainer, for the caller
     * to wrap with an {@code OSProcessHandler}. Returns {@code null} if no container is running.
     */
    public Process openInteractiveShell(String shell) throws IOException, InterruptedException {
        DevContainerConfig config = loadConfig();
        Path projectDir = projectDir();
        String projectName = projectName();
        List<String> shellCommand = List.of(shell);

        if (config.isCompose()) {
            // No "-t": this process is driven by plain Java pipes (no PTY), so docker must not
            // try to allocate a pseudo-terminal - it would refuse with "stdin is not a terminal".
            List<String> args = DockerCommandBuilder.composeExecArgs(config, projectDir, projectName, shellCommand, false);
            return runtime.startProcess(args, projectDir);
        }
        DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
        if (status == null || !status.isRunning()) {
            return null;
        }
        List<String> args = DockerCommandBuilder.execArgs(status.id(), shellCommand, false);
        return runtime.startProcess(args, projectDir);
    }

    /** Resolves the container/service target for "open external terminal", or {@code null} if not running. */
    public List<String> externalTerminalDockerArgs(String shell) throws IOException, InterruptedException {
        DevContainerConfig config = loadConfig();
        Path projectDir = projectDir();
        String projectName = projectName();
        List<String> shellCommand = List.of(shell);

        if (config.isCompose()) {
            return DockerCommandBuilder.composeExecArgs(config, projectDir, projectName, shellCommand, true);
        }
        DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
        if (status == null || !status.isRunning()) {
            return null;
        }
        return DockerCommandBuilder.execArgs(status.id(), shellCommand, true);
    }

    /** Runs {@code mainClass} inside the devcontainer via Maven's exec plugin, streaming output. */
    public void runApp(String mainClass) {
        if (mainClass == null || mainClass.isBlank()) {
            notify("Set a main class in the Dev Container tool window first", NotificationType.WARNING);
            return;
        }
        try {
            stopApp();
            List<String> args = resolveAppExecArgs(mainClass, null);
            if (args == null) {
                log("No running dev container found. Start it first.");
                notify("Dev container is not running", NotificationType.WARNING);
                return;
            }
            log("$ docker " + String.join(" ", args));
            Process process = runtime.startProcess(args, projectDir());
            appProcess = process;
            pumpAppOutput(process);
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            notify("Failed to run app: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    /**
     * Runs {@code mainClass} inside the devcontainer with a JDWP agent attached, waits for the
     * debug port to open and then attaches IntelliJ's "Remote JVM Debug" to it.
     */
    public void debugApp(String mainClass) {
        if (mainClass == null || mainClass.isBlank()) {
            notify("Set a main class in the Dev Container tool window first", NotificationType.WARNING);
            return;
        }
        try {
            stopApp();
            int debugPort = DockerCommandBuilder.DEBUG_PORT;
            List<String> args = resolveAppExecArgs(mainClass, debugPort);
            if (args == null) {
                log("No running dev container found. Start it first.");
                notify("Dev container is not running", NotificationType.WARNING);
                return;
            }
            log("$ docker " + String.join(" ", args));
            Process process = runtime.startProcess(args, projectDir());
            appProcess = process;
            pumpAppOutput(process);

            if (!waitForPort("localhost", debugPort, Duration.ofSeconds(15))) {
                log("ERROR: debug port " + debugPort + " was not reachable within 15s. If this is a "
                        + "Docker Compose devcontainer, make sure docker-compose.yml publishes port " + debugPort + ".");
                notify("Could not reach debug port " + debugPort, NotificationType.ERROR);
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> attachRemoteDebugger(debugPort));
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            notify("Failed to debug app: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    /** Stops the app process previously started by {@link #runApp} or {@link #debugApp}, if any. */
    public void stopApp() {
        Process process = appProcess;
        appProcess = null;
        if (process != null && process.isAlive()) {
            process.destroy();
            log("-- app process stopped --");
        }
    }

    private List<String> resolveAppExecArgs(String mainClass, Integer debugPort) throws IOException, InterruptedException {
        DevContainerConfig config = loadConfig();
        Path projectDir = projectDir();
        String projectName = projectName();

        if (config.isCompose()) {
            return DockerCommandBuilder.composeExecAppArgs(config, projectDir, projectName, mainClass, debugPort);
        }
        DockerContainerRuntime.ContainerStatus status = runtime.findContainer(projectDir.toString(), projectDir);
        if (status == null || !status.isRunning()) {
            return null;
        }
        return DockerCommandBuilder.execAppArgs(status.id(), mainClass, debugPort);
    }

    private void pumpAppOutput(Process process) {
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log(line);
                }
            } catch (IOException ignored) {
                // process ended / stream closed
            } finally {
                log("-- app process finished --");
            }
        }, "devcontainer-app-output");
        pump.setDaemon(true);
        pump.start();
    }

    private boolean waitForPort(String host, int port, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 500);
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private void attachRemoteDebugger(int port) {
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings settings =
                runManager.createConfiguration("Dev Container Debug", RemoteConfigurationType.getInstance().getFactory());
        RemoteConfiguration configuration = (RemoteConfiguration) settings.getConfiguration();
        configuration.HOST = "localhost";
        configuration.PORT = String.valueOf(port);
        configuration.USE_SOCKET_TRANSPORT = true;
        configuration.SERVER_MODE = false;
        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance());
    }
}
