package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.ui.StatusBar;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.SceneData;

public class SaveCommand implements Command {
    private final EditorApplication application;
    private final StatusBar statusBar;
    private final SaveAsCommand saveAsCommand;

    public SaveCommand(EditorApplication application, StatusBar statusBar, SaveAsCommand saveAsCommand) {
        this.application = application;
        this.statusBar = statusBar;
        this.saveAsCommand = saveAsCommand;
    }

    @Override
    public String id() {
        return "Save";
    }

    @Override
    public boolean isEnabled() {
        return application.getCurrentScene().isDirty();
    }

    @Override
    public void execute() {
        if (application.getCurrentScene() == null) return;

        if (application.getCurrentScene().getFilePath() != null) {
            saveScene();
        } else {
            saveAsCommand.execute();
        }
    }

    private void saveScene() {
        if (application.getCurrentScene() == null || application.getCurrentScene().getFilePath() == null) {
            return;
        }

        try {
            System.out.println("Saving scene: " + application.getCurrentScene().getFilePath());
            SceneData data = EditorSceneSerializer.toSceneData(application.getCurrentScene());
            Assets.persist(data, application.getCurrentScene().getFilePath());

            application.getCurrentScene().clearDirty();
            statusBar.showMessage("Saved: " + application.getCurrentScene().getName());
        } catch (Exception e) {
            statusBar.showMessage("Error saving: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
