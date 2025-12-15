package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.StatusBar;

public class NewSceneCommand implements Command {
    private final EditorApplication application;
    private final StatusBar statusBar;

    public NewSceneCommand(EditorApplication application, StatusBar statusBar) {
        this.application = application;
        this.statusBar = statusBar;
    }

    @Override
    public String id() {
        return "NewScene";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void execute() {
        application.checkUnsavedChanges(this::newScene);
    }

    private void newScene() {
        System.out.println("Creating new scene...");

        if (application.getCurrentScene() != null) {
            application.getCurrentScene().destroy();
        }

        application.setCurrentScene(new EditorScene("Untitled"));

        application.onNewScene();
        statusBar.showMessage("New scene created");
    }
}
