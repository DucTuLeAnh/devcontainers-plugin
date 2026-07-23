package org.example.devcontainer.actions;

import org.example.devcontainer.service.DevContainerService;

public final class RebuildDevContainerAction extends DevContainerActionBase {
    @Override
    protected String title() {
        return "Rebuilding Dev Container";
    }

    @Override
    protected void perform(DevContainerService service) {
        service.rebuild();
    }
}
