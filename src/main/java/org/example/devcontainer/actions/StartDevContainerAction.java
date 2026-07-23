package org.example.devcontainer.actions;

import org.example.devcontainer.service.DevContainerService;

public final class StartDevContainerAction extends DevContainerActionBase {
    @Override
    protected String title() {
        return "Starting Dev Container";
    }

    @Override
    protected void perform(DevContainerService service) {
        service.start();
    }
}
