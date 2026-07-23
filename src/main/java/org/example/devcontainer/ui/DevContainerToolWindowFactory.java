package org.example.devcontainer.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class DevContainerToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DevContainerToolWindowPanel panel = new DevContainerToolWindowPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
