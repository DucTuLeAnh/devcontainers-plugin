package org.example.devcontainer.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.example.devcontainer.docker.TerminalLauncher;
import org.example.devcontainer.service.DevContainerService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Opens a real system terminal (full TTY, colors, interactive programs) attached to the
 * running devcontainer via {@code docker exec -it}. Complements the embedded, non-PTY console
 * in the tool window.
 */
public final class OpenExternalTerminalAction extends AnAction {

    private static final String SHELL = "bash";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Opening Terminal in Dev Container", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    DevContainerService service = DevContainerService.getInstance(project);
                    List<String> args = service.externalTerminalDockerArgs(SHELL);
                    if (args == null) {
                        showNotification(project, "Dev container is not running. Start it first.", NotificationType.WARNING);
                        return;
                    }
                    if (!TerminalLauncher.open(args)) {
                        showNotification(project, "No supported terminal emulator found (tried $TERMINAL, gnome-terminal, konsole, xfce4-terminal, x-terminal-emulator, xterm).",
                                NotificationType.ERROR);
                    }
                } catch (Exception ex) {
                    showNotification(project, "Failed to open terminal: " + ex.getMessage(), NotificationType.ERROR);
                }
            }
        });
    }

    private static void showNotification(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Dev Container")
                .createNotification(content, type)
                .notify(project);
    }
}
