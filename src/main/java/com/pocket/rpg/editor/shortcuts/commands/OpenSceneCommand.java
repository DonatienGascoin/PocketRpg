package com.pocket.rpg.editor.shortcuts.commands;

import com.pocket.rpg.editor.EditorApplication;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.ui.StatusBar;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.SceneData;

/**
 * TODO: Not used: How to handle the execute method: It needs a file path !
 */
public class OpenSceneCommand implements Command {
    private final EditorApplication application;
    private final StatusBar statusBar;

    public OpenSceneCommand(EditorApplication application, StatusBar statusBar) {
        this.application = application;
        this.statusBar = statusBar;
    }

    @Override
    public String id() {
        return "OpenScene";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void execute() {
//        application.checkUnsavedChanges(() -> {
//            if (onOpenScene != null) {
//                onOpenScene.accept(file);
//            }
//        });
    }

    private void openScene(String path) {
        System.out.println("Opening scene: " + path);

        // TODO: Implement scene loading in Phase 2
        // For now, just create a new scene with the filename

        if (application.getCurrentScene() != null) {
            application.getCurrentScene().destroy();
        }
        SceneData scene = Assets.load(path);
        application.setCurrentScene(EditorSceneSerializer.fromSceneData(scene, path));


        application.onNewScene();

        statusBar.showMessage("Opened: " + scene.getName());
    }
}
