package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;

public class ExitCommand implements Command {

    private final EditorApplication application;

    public ExitCommand(EditorApplication application) {
        this.application = application;
    }

    @Override
    public String id() {
        return "Exit";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void execute() {
        application.checkUnsavedChanges(() -> {
            application.setRunning(false);
        });
    }
}
