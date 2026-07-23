package org.example.devcontainer.actions;

import org.example.devcontainer.service.DevContainerService;

public final class RunInDevContainerAction extends DevContainerActionBase {
    @Override
    protected String title() {
        return "Running App in Dev Container";
    }

    @Override
    protected void perform(DevContainerService service) {
        service.runApp(service.configuredMainClass());
    }
}
