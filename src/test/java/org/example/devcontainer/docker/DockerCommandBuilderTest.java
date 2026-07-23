package org.example.devcontainer.docker;

import org.example.devcontainer.config.DevContainerConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerCommandBuilderTest {

    @Test
    void buildsRunArgsForImageBasedConfig() {
        DevContainerConfig config = new DevContainerConfig();
        config.image = "eclipse-temurin:21-jdk-jammy";
        config.containerEnv = Map.of("FOO", "bar");
        config.forwardPorts = List.of("8080");
        Path projectDir = Path.of("/home/user/java-test");

        List<String> args = DockerCommandBuilder.runArgs(config, projectDir, "java-test", config.image);

        assertEquals("run", args.get(0));
        assertTrue(args.contains("--name"));
        assertEquals("devcontainer-java-test", args.get(args.indexOf("--name") + 1));
        assertTrue(args.contains("-v"));
        assertEquals(projectDir + ":/workspaces/java-test", args.get(args.indexOf("-v") + 1));
        assertTrue(args.contains("-e"));
        assertEquals("FOO=bar", args.get(args.indexOf("-e") + 1));
        assertTrue(args.contains("-p"));
        assertEquals("8080:8080", args.get(args.indexOf("-p") + 1));
        assertEquals("eclipse-temurin:21-jdk-jammy", args.get(args.size() - 3));
        assertEquals(List.of("sleep", "infinity"), args.subList(args.size() - 2, args.size()));
    }

    @Test
    void convertsStringPostCreateCommandToShellInvocation() {
        List<String> argv = DockerCommandBuilder.commandToArgv("mvn -q dependency:go-offline");
        assertEquals(List.of("sh", "-c", "mvn -q dependency:go-offline"), argv);
    }

    @Test
    void convertsListPostCreateCommandAsIs() {
        List<String> argv = DockerCommandBuilder.commandToArgv(List.of("mvn", "-q", "dependency:go-offline"));
        assertEquals(List.of("mvn", "-q", "dependency:go-offline"), argv);
    }

    @Test
    void buildsComposeUpArgsWithProjectNameAndComposeFile() {
        DevContainerConfig config = new DevContainerConfig();
        config.dockerComposeFile = List.of("docker-compose.yml");
        config.service = "app";
        Path projectDir = Path.of("/home/user/compose-test");

        List<String> args = DockerCommandBuilder.composeUpArgs(config, projectDir, "compose-test");

        assertEquals(List.of("compose", "-f", projectDir.resolve(".devcontainer").resolve("docker-compose.yml").toString(),
                "-p", "compose-test", "up", "-d", "app"), args);
    }

    @Test
    void sanitizesProjectNamesForContainerAndComposeNames() {
        assertEquals("devcontainer-my-cool-app", DockerCommandBuilder.containerName("My Cool App"));
        assertEquals("my-cool-app", DockerCommandBuilder.composeProjectName("My Cool App"));
    }

    @Test
    void execArgsUsesNonInteractiveFlagForPostCreateCommand() {
        List<String> args = DockerCommandBuilder.execArgs("abc123", List.of("sh", "-c", "echo hi"), false);
        assertEquals(List.of("exec", "-i", "abc123", "sh", "-c", "echo hi"), args);
    }

    @Test
    void runArgsAlwaysPublishesDebugPortWhenNotAlreadyMapped() {
        DevContainerConfig config = new DevContainerConfig();
        config.image = "eclipse-temurin:21-jdk-jammy";
        config.forwardPorts = List.of("8080");

        List<String> args = DockerCommandBuilder.runArgs(config, Path.of("/home/user/java-test"), "java-test", config.image);

        assertEquals(1, args.stream().filter("5005:5005"::equals).count());
    }

    @Test
    void runArgsDoesNotDuplicateDebugPortIfAlreadyForwarded() {
        DevContainerConfig config = new DevContainerConfig();
        config.image = "eclipse-temurin:21-jdk-jammy";
        config.forwardPorts = List.of("5005");

        List<String> args = DockerCommandBuilder.runArgs(config, Path.of("/home/user/java-test"), "java-test", config.image);

        assertEquals(1, args.stream().filter(a -> a.startsWith("5005")).count());
    }

    @Test
    void execAppArgsRunsMainClassViaExecMavenPluginWithoutDebugAgent() {
        List<String> args = DockerCommandBuilder.execAppArgs("abc123", "Main", null);

        assertEquals(List.of("exec", "-i", "abc123", "mvn", "-q", "compile",
                "org.codehaus.mojo:exec-maven-plugin:3.1.0:java",
                "-Dexec.mainClass=Main", "-Dexec.classpathScope=compile"), args);
    }

    @Test
    void execAppArgsAlwaysCompilesBeforeRunningSoCodeChangesAreReflected() {
        List<String> args = DockerCommandBuilder.execAppArgs("abc123", "Main", null);

        assertTrue(args.contains("compile"));
        assertTrue(args.indexOf("compile") < args.indexOf("org.codehaus.mojo:exec-maven-plugin:3.1.0:java"));
    }

    @Test
    void execAppArgsAttachesJdwpAgentWhenDebugPortGiven() {
        List<String> args = DockerCommandBuilder.execAppArgs("abc123", "Main", 5005);

        assertTrue(args.contains("-e"));
        assertEquals("MAVEN_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",
                args.get(args.indexOf("-e") + 1));
        assertTrue(args.contains("-Dexec.mainClass=Main"));
    }

    @Test
    void composeExecAppArgsUsesNonTtyFlagAndService() {
        DevContainerConfig config = new DevContainerConfig();
        config.dockerComposeFile = List.of("docker-compose.yml");
        config.service = "app";
        Path projectDir = Path.of("/home/user/compose-test");

        List<String> args = DockerCommandBuilder.composeExecAppArgs(config, projectDir, "compose-test", "Main", null);

        assertTrue(args.contains("-iT"));
        assertTrue(args.contains("app"));
        assertTrue(args.contains("-Dexec.mainClass=Main"));
    }
}
