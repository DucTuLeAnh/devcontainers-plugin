package org.example.devcontainer.actions;

import org.example.devcontainer.service.DevContainerService;

public final class StopDevContainerAction extends DevContainerActionBase {
    @Override
    protected String title() {
        return "Stopping Dev Container";
    }

    @Override
    protected void perform(DevContainerService service) {
        service.stop();
    }
}
