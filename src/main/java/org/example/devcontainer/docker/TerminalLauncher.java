package org.example.devcontainer.docker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opens the user's system terminal emulator running a given command. Used to give full TTY
 * fidelity (colors, interactive full-screen programs like vim/htop) for the devcontainer shell,
 * which the embedded, non-PTY console in the tool window cannot provide.
 *
 * <p>The {@code docker} command itself runs identically on every host OS (it's a plain process
 * invocation resolved via PATH, and Docker Desktop accepts native Windows paths for bind mounts
 * directly - no path translation needed there). Only the terminal emulator to launch differs.
 */
public final class TerminalLauncher {

    private TerminalLauncher() {
    }

    /** Tries a list of common terminal emulators in order; returns false if none is available. */
    public static boolean open(List<String> dockerArgs) throws IOException {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("docker");
        fullCommand.addAll(dockerArgs);

        List<List<String>> candidates = isWindows() ? windowsCandidates(fullCommand) : unixCandidates(fullCommand);

        for (List<String> candidate : candidates) {
            if (!isOnPath(candidate.get(0))) {
                continue;
            }
            new ProcessBuilder(candidate).start();
            return true;
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static List<List<String>> unixCandidates(List<String> fullCommand) {
        List<List<String>> candidates = new ArrayList<>();
        String envTerminal = System.getenv("TERMINAL");
        if (envTerminal != null && !envTerminal.isBlank()) {
            candidates.add(prefixed(List.of(envTerminal, "-e"), fullCommand));
        }
        candidates.add(prefixed(List.of("gnome-terminal", "--"), fullCommand));
        candidates.add(prefixed(List.of("konsole", "-e"), fullCommand));
        candidates.add(prefixed(List.of("xfce4-terminal", "-e"), fullCommand));
        candidates.add(prefixed(List.of("x-terminal-emulator", "-e"), fullCommand));
        candidates.add(prefixed(List.of("xterm", "-e"), fullCommand));
        return candidates;
    }

    private static List<List<String>> windowsCandidates(List<String> fullCommand) {
        List<List<String>> candidates = new ArrayList<>();
        // Windows Terminal: everything after "wt.exe" is run as the command line of the
        // default shell profile in a new window.
        candidates.add(prefixed(List.of("wt.exe"), fullCommand));
        // cmd.exe is always present, used as a universal fallback if Windows Terminal isn't
        // installed. "start "" cmd /k ..." opens a new console window and keeps it open (/k)
        // after the docker command finishes; the empty "" is required so that "start" doesn't
        // mistake the actual command for a window title.
        List<String> cmdFallback = new ArrayList<>(List.of("cmd.exe", "/c", "start", "", "cmd.exe", "/k"));
        cmdFallback.addAll(fullCommand);
        candidates.add(cmdFallback);
        return candidates;
    }

    private static List<String> prefixed(List<String> prefix, List<String> command) {
        List<String> result = new ArrayList<>(prefix);
        result.addAll(command);
        return result;
    }

    private static boolean isOnPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, binary).canExecute()) {
                return true;
            }
        }
        return false;
    }
}
