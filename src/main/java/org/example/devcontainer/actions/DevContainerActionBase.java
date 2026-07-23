package org.example.devcontainer.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.example.devcontainer.service.DevContainerService;
import org.jetbrains.annotations.NotNull;

/**
 * Shared behavior for the Start/Stop/Rebuild actions: makes sure the "Dev Container" tool
 * window is visible (so its console shows the command output) and runs the actual work in a
 * background task, never on the UI thread.
 */
abstract class DevContainerActionBase extends AnAction {

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
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Dev Container");
        if (toolWindow != null) {
            toolWindow.activate(() -> runInBackground(project));
        } else {
            runInBackground(project);
        }
    }

    private void runInBackground(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title(), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                perform(DevContainerService.getInstance(project));
            }
        });
    }

    protected abstract String title();

    protected abstract void perform(DevContainerService service);
}
