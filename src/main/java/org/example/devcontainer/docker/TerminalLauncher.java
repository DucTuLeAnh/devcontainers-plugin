package org.example.devcontainer.docker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the user's system terminal emulator running a given command. Used to give full TTY
 * fidelity (colors, interactive full-screen programs like vim/htop) for the devcontainer shell,
 * which the embedded, non-PTY console in the tool window cannot provide.
 */
public final class TerminalLauncher {

    private TerminalLauncher() {
    }

    /** Tries a list of common terminal emulators in order; returns false if none is available. */
    public static boolean open(List<String> dockerArgs) throws IOException {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("docker");
        fullCommand.addAll(dockerArgs);

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

        for (List<String> candidate : candidates) {
            if (!isOnPath(candidate.get(0))) {
                continue;
            }
            new ProcessBuilder(candidate).start();
            return true;
        }
        return false;
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
