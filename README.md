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
- **Open Terminal in Dev Container**: opens a real system terminal attached via `docker exec -it`,
  for full TTY fidelity (colors, `vim`, `htop`, ...). Tries `gnome-terminal`/`konsole`/
  `xfce4-terminal`/`xterm` on Linux, Windows Terminal (`wt.exe`, falling back to `cmd.exe`) on
  Windows.
- **Run / Debug in Dev Container**: runs a configured main class inside the container via the
  `exec-maven-plugin`, and for Debug attaches IntelliJ's built-in "Remote JVM Debug" to a JDWP
  agent started in the container (breakpoints, stepping, etc. all work normally).

## Requirements

- Docker (and `docker compose` for Compose-based devcontainers) available on `PATH`.
- A local IntelliJ IDEA installation (Community or Ultimate - both ship the same platform/Java
  modules this plugin needs), used at build time to compile against the IntelliJ Platform API.
  No particular `JAVA_HOME`/system JDK is required - the build compiles and runs tests with the
  IDE's own bundled JetBrains Runtime (see below).

## Building

This plugin is built with plain Maven - no Gradle, no IntelliJ Platform Gradle Plugin. Instead,
`pom.xml` declares `system`-scope dependencies pointing directly at jars inside a local IntelliJ
IDEA installation.

1. Set `<idea.home>` in `pom.xml` to your IntelliJ IDEA installation directory (the folder
   containing `bin/idea.sh` and `lib/`).
2. `<maven.compiler.release>` in `pom.xml` must match (or exceed) the classfile version of that
   IDE's jars - in practice, the Java version of its bundled JetBrains Runtime (check
   `<idea.home>/jbr/release`). javac refuses to read newer classfiles under an older `--release`,
   so a mismatch here fails the build outright. The plugin bytecode produced will only run on
   IDEs whose JBR is at least this version - `since-build` in `plugin.xml` is set accordingly.
3. Build:
   ```
   ./mvnw clean package
   ```
   This produces `target/devcontainers-plugin-1.0-SNAPSHOT.zip`. `maven-compiler-plugin` and
   `maven-surefire-plugin` are configured to compile and run tests with
   `${idea.home}/jbr/bin/javac`/`java` rather than whatever `JAVA_HOME` is active, so this works
   regardless of the host's default JDK.

**Note on IntelliJ's internal jar layout:** it can change between major versions - it did
between 2025.2 (a handful of monolithic jars like `app-client.jar`) and 2026.2 (many
fine-grained `intellij.platform.*.jar`/`intellij.java.*.jar` modules). Upgrading `<idea.home>`
to a new major version may require re-resolving which jar each dependency lives in, not just
updating the path. A quick way to find a class: build an index once with
`find <idea.home> -name "*.jar" | while read j; do unzip -l "$j" | awk -v j="$j" '{print j"\t"$4}'; done > /tmp/idea_jar_index.txt`
then `grep "Some/Class.class" /tmp/idea_jar_index.txt`.

## Platform support

Runs on Linux, macOS and Windows hosts (with Docker Desktop) - the devcontainer itself is always
a Linux container regardless of host OS, so `bash`/`sh` inside it is unaffected by the host. Two
host-OS-specific points:

- **Volume mounts**: Docker Desktop on Windows auto-detects and translates native Windows paths
  (e.g. `C:\Users\name\project`) passed to `-v`, so no path conversion is needed on our side.
- **Open Terminal in Dev Container**: platform-appropriate terminal emulators are tried (see
  Features above); if none is found, an error notification says so.

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
