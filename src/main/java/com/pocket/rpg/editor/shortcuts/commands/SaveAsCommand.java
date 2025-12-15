package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;
import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.ui.StatusBar;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.SceneData;

import java.util.Optional;

public class SaveAsCommand implements Command {
    private EditorApplication application;
    private final StatusBar statusBar;

    public SaveAsCommand(EditorApplication application, StatusBar statusBar) {
        this.application = application;
        this.statusBar = statusBar;
    }

    @Override
    public String id() {
        return "SaveAs";
    }

    @Override
    public boolean isEnabled() {
        return true; // Can always save as
    }

    @Override
    public void execute() {
        String defaultName = application.getCurrentScene() != null ? application.getCurrentScene().getName() : "scene";
        Optional<String> path = FileDialogs.saveSceneFile(
                FileDialogs.getScenesDirectory(),
                defaultName + ".scene"
        );

        path.ifPresent(this::saveSceneAs);
    }

    private void saveSceneAs(String path) {
        if (application.getCurrentScene() == null) {
            return;
        }

        System.out.println("Saving scene as: " + path);

        application.getCurrentScene().setFilePath(path);

        // Extract name from path
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSep >= 0 ? path.substring(lastSep + 1) : path;
        if (fileName.endsWith(".scene")) {
            fileName = fileName.substring(0, fileName.length() - 6);
        }
        application.getCurrentScene().setName(fileName);

        saveScene();
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
