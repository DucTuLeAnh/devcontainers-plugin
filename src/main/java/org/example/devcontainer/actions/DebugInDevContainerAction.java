package org.example.devcontainer.actions;

import org.example.devcontainer.service.DevContainerService;

public final class DebugInDevContainerAction extends DevContainerActionBase {
    @Override
    protected String title() {
        return "Debugging App in Dev Container";
    }

    @Override
    protected void perform(DevContainerService service) {
        service.debugApp(service.configuredMainClass());
    }
}
