package org.example.devcontainer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevContainerConfigParserTest {

    @Test
    void parsesImageBasedConfigWithComments(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  // this is a comment
                  "name": "java-test",
                  "image": "eclipse-temurin:21-jdk-jammy",
                  "postCreateCommand": "apt-get update && apt-get install -y --no-install-recommends maven",
                  "customizations": {
                    "vscode": { "extensions": ["vscjava.vscode-java-pack"] } /* trailing comma below */
                  },
                }
                """;
        Path file = tempDir.resolve("devcontainer.json");
        Files.writeString(file, json);

        DevContainerConfig config = DevContainerConfigParser.parse(file);

        assertEquals("java-test", config.name);
        assertEquals("eclipse-temurin:21-jdk-jammy", config.image);
        assertEquals("apt-get update && apt-get install -y --no-install-recommends maven", config.postCreateCommand);
        assertTrue(config.isImageBased());
        assertNull(config.dockerComposeFile);
    }

    @Test
    void parsesComposeBasedConfig(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  "name": "compose-test",
                  "dockerComposeFile": ["docker-compose.yml"],
                  "service": "app",
                  "workspaceFolder": "/workspace",
                  "postCreateCommand": ["mvn", "-q", "dependency:go-offline"],
                  "forwardPorts": [8080, "5432:5432"]
                }
                """;
        Path file = tempDir.resolve("devcontainer.json");
        Files.writeString(file, json);

        DevContainerConfig config = DevContainerConfigParser.parse(file);

        assertTrue(config.isCompose());
        assertEquals(List.of("docker-compose.yml"), config.dockerComposeFile);
        assertEquals("app", config.service);
        assertEquals("/workspace", config.workspaceFolder);
        assertEquals(List.of("mvn", "-q", "dependency:go-offline"), config.postCreateCommand);
        assertEquals(List.of("8080", "5432:5432"), config.forwardPorts);
    }

    @Test
    void stripJsonCommentsPreservesUrlsInStrings() {
        String input = "{ \"a\": \"https://example.com\" /* comment */ }";
        String result = DevContainerConfigParser.stripJsonComments(input);
        assertTrue(result.contains("https://example.com"));
    }
}
