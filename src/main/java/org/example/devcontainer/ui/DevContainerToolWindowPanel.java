package org.example.devcontainer.ui;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.example.devcontainer.service.DevContainerService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Content of the "Dev Container" tool window: a toolbar (Start/Stop/Rebuild/Open Shell/Run/
 * Debug), a "Main class" field, a console showing command output, and an input line to send
 * commands to an interactive shell running inside the container.
 *
 * <p>The shell is attached via a plain {@code docker exec -i}, not IntelliJ's Terminal plugin -
 * it has no real PTY, so there is no coloring and full-screen programs (vim, htop, ...) will not
 * render correctly. Use "Open Terminal in Dev Container" (system terminal) for those.
 *
 * <p>"Run"/"Debug" execute the configured main class inside the container via the exec-maven-
 * plugin; "Debug" additionally attaches IntelliJ's Remote JVM Debug to the JDWP agent started
 * inside the container (see {@link DevContainerService#debugApp}).
 */
public final class DevContainerToolWindowPanel {

    private static final String SHELL = "bash";

    private final Project project;
    private final DevContainerService service;
    private final ConsoleView console;
    private final JPanel component;
    private volatile OutputStream shellStdin;

    public DevContainerToolWindowPanel(Project project) {
        this.project = project;
        this.service = DevContainerService.getInstance(project);
        this.console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        this.service.setOutputSink(this::printLine);

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildToolbar(), BorderLayout.NORTH);
        north.add(buildMainClassRow(), BorderLayout.SOUTH);

        this.component = new JPanel(new BorderLayout());
        component.add(north, BorderLayout.NORTH);
        component.add(console.getComponent(), BorderLayout.CENTER);
        component.add(buildInputRow(), BorderLayout.SOUTH);
    }

    public JComponent getComponent() {
        return component;
    }

    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(button("Start", e -> runBackground("Starting Dev Container", service::start)));
        toolbar.add(button("Stop", e -> runBackground("Stopping Dev Container", service::stop)));
        toolbar.add(button("Rebuild", e -> runBackground("Rebuilding Dev Container", service::rebuild)));
        toolbar.add(button("Open Shell", e -> runBackground("Opening Shell", this::openShell)));
        toolbar.add(button("Run", e -> runBackground("Running App", () -> service.runApp(service.configuredMainClass()))));
        toolbar.add(button("Debug", e -> runBackground("Debugging App", () -> service.debugApp(service.configuredMainClass()))));
        toolbar.add(button("Stop App", e -> runBackground("Stopping App", service::stopApp)));
        return toolbar;
    }

    private JComponent buildMainClassRow() {
        JPanel row = new JPanel(new BorderLayout());
        JTextField mainClassField = new JTextField(service.configuredMainClass());
        mainClassField.getDocument().addDocumentListener((SimpleDocumentListener) () ->
                service.setConfiguredMainClass(mainClassField.getText()));
        row.add(new JLabel("Main class: "), BorderLayout.WEST);
        row.add(mainClassField, BorderLayout.CENTER);
        return row;
    }

    private JComponent buildInputRow() {
        JPanel row = new JPanel(new BorderLayout());
        JTextField input = new JTextField();
        input.addActionListener(e -> sendToShell(input));
        row.add(new JLabel("Shell: "), BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);
        return row;
    }

    private JButton button(String text, ActionListener onClick) {
        JButton button = new JButton(text);
        button.addActionListener(onClick);
        return button;
    }

    private void runBackground(String title, Runnable task) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                task.run();
            }
        });
    }

    private void openShell() {
        try {
            Process process = service.openInteractiveShell(SHELL);
            if (process == null) {
                printLine("No running dev container found. Click Start first.");
                return;
            }
            shellStdin = process.getOutputStream();
            printLine("-- shell attached, type commands below and press Enter --");
            Thread pump = new Thread(() -> pumpOutput(process), "devcontainer-shell-output");
            pump.setDaemon(true);
            pump.start();
        } catch (IOException | InterruptedException e) {
            printLine("ERROR: " + e.getMessage());
        }
    }

    private void pumpOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                printLine(line);
            }
        } catch (IOException ignored) {
            // process ended / stream closed
        } finally {
            shellStdin = null;
            printLine("-- shell disconnected --");
        }
    }

    private void sendToShell(JTextField input) {
        String text = input.getText();
        input.setText("");
        OutputStream stdin = shellStdin;
        if (stdin == null) {
            printLine("No shell attached. Click 'Open Shell' first.");
            return;
        }
        printLine("> " + text);
        try {
            stdin.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            printLine("ERROR writing to shell: " + e.getMessage());
        }
    }

    private void printLine(String line) {
        ApplicationManager.getApplication().invokeLater(() ->
                console.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT));
    }

    /** Collapses insert/remove/change into a single callback - we don't care which happened. */
    @FunctionalInterface
    private interface SimpleDocumentListener extends DocumentListener {
        void onChange();

        @Override
        default void insertUpdate(DocumentEvent e) {
            onChange();
        }

        @Override
        default void removeUpdate(DocumentEvent e) {
            onChange();
        }

        @Override
        default void changedUpdate(DocumentEvent e) {
            onChange();
        }
    }
}
