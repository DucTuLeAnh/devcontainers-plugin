package org.example.devcontainer.config;

import java.util.List;
import java.util.Map;

/**
 * Parsed subset of a devcontainer.json that this plugin understands. Supports both the
 * single-container variant ({@code image} or {@code build.dockerfile}) and the Docker Compose
 * variant ({@code dockerComposeFile} + {@code service}). Fields not listed here (features,
 * mounts, remoteUser, lifecycle hooks other than postCreateCommand, ${...} substitution) are
 * intentionally not supported.
 */
public final class DevContainerConfig {

    public String name;
    public String image;
    public Build build;
    public List<String> dockerComposeFile;
    public String service;
    public String workspaceFolder;

    /** Either a {@code String} (run via a shell) or a {@code List<String>} (run as argv). */
    public Object postCreateCommand;

    public Map<String, String> containerEnv;
    public List<String> forwardPorts;
    public List<String> runArgs;

    public static final class Build {
        public String dockerfile;
        public String context = ".";
    }

    public boolean isCompose() {
        return dockerComposeFile != null && !dockerComposeFile.isEmpty();
    }

    public boolean isDockerfileBased() {
        return build != null && build.dockerfile != null && !build.dockerfile.isBlank();
    }

    public boolean isImageBased() {
        return !isCompose() && !isDockerfileBased();
    }
}
