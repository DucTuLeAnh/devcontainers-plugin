# Simple Dev Containers for IntelliJ IDEA Community Edition

A minimal [Dev Containers](https://containers.dev/) plugin for IntelliJ IDEA **Community
Edition**. JetBrains' official Dev Containers support requires Ultimate + Gateway; this plugin
gives you a lightweight alternative for CE: manage the container lifecycle, get a shell inside
it, and run/debug your Java application in the container's environment - all driven by the
Docker CLI, no remote-dev backend involved.

> **Note:** This project was built with AI assistance (Claude/Claude Code). Review the code
> before relying on it, especially before using it in security-sensitive environments.

## Features

- **Start / Stop / Rebuild** the devcontainer defined in `.devcontainer/devcontainer.json`,
  for both single-container (`image` or `build.dockerfile`) and Docker Compose
  (`dockerComposeFile` + `service`) setups.
- Runs `postCreateCommand` automatically after (re)creating the container.
- **Embedded shell** in the tool window (`docker exec -i`) to run commands inside the container.
- **Open Terminal in Dev Container**: opens a real system terminal (`gnome-terminal`/`konsole`/
  `xterm`/...) attached via `docker exec -it`, for full TTY fidelity (colors, `vim`, `htop`, ...).
- **Run / Debug in Dev Container**: runs a configured main class inside the container via the
  `exec-maven-plugin`, and for Debug attaches IntelliJ's built-in "Remote JVM Debug" to a JDWP
  agent started in the container (breakpoints, stepping, etc. all work normally).

## Requirements

- Docker (and `docker compose` for Compose-based devcontainers) available on `PATH`.
- A local IntelliJ IDEA Community Edition installation, used at build time to compile against
  the IntelliJ Platform API.

## Building

This plugin is built with plain Maven - no Gradle, no IntelliJ Platform Gradle Plugin. Instead,
`pom.xml` declares `system`-scope dependencies pointing directly at jars inside a local IntelliJ
IDEA Community installation.

1. Set `<idea.home>` in `pom.xml` to your IntelliJ IDEA Community installation directory (the
   folder containing `bin/idea.sh` and `lib/`).
2. Build:
   ```
   ./mvnw clean package
   ```
   This produces `target/devcontainers-plugin-1.0-SNAPSHOT.zip`.

## Installing

In IntelliJ IDEA: **Settings → Plugins → gear icon → Install Plugin from Disk...**, select the
zip from `target/`, then restart the IDE.

## Usage

Open a project containing `.devcontainer/devcontainer.json` and open the **"Dev Container"** tool
window (bottom). From there:

- **Start / Stop / Rebuild** manage the container.
- **Open Shell** attaches a basic (non-PTY) shell in the console below; **Open Terminal in Dev
  Container** opens a full system terminal instead.
- Enter a fully-qualified class name in **Main class**, then use **Run** / **Debug** / **Stop
  App** to run it inside the container.

All actions are also available under **Tools → Dev Container**.

## Supported `devcontainer.json` fields

`name`, `image`, `build.dockerfile` + `build.context`, `dockerComposeFile`, `service`,
`workspaceFolder`, `postCreateCommand` (string or array), `containerEnv`, `forwardPorts`,
`runArgs`.

**Not supported:** `features`, additional `mounts`, `remoteUser`, lifecycle hooks other than
`postCreateCommand`, `${...}` variable substitution.

## Known limitations

- The embedded shell has no real PTY (no colors, no full-screen programs) - use the external
  terminal button for those.
- The debug port (5005) is only published when the container is *created*. If you add Debug
  support to an already-running container, click **Rebuild** once to recreate it with the port
  mapping. For Compose-based devcontainers, publish port 5005 yourself in `docker-compose.yml`.
- `Rebuild` always builds Dockerfile-based images with `--no-cache`, re-running every build step
  from scratch.
- No registry authentication handling - the Docker daemon must already be configured/logged in
  for any private registries referenced in `image`/`build.dockerfile`.

## License

[MIT](LICENSE)
