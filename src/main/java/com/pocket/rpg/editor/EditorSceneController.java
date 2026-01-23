package com.pocket.rpg.editor;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.LoadOptions;
import com.pocket.rpg.serialization.SceneData;

import java.util.function.Consumer;

/**
 * Handles scene operations: new, open, save.
 * <p>
 * Manages scene lifecycle and notifies the context when scenes change.
 */
public class EditorSceneController {

    private final EditorContext context;

    // Optional message callback for status updates
    private Consumer<String> messageCallback;

    // Callback when recent scenes list changes
    private Runnable onRecentScenesChanged;

    public EditorSceneController(EditorContext context) {
        this.context = context;
    }

    /**
     * Sets a callback to be invoked when the recent scenes list changes.
     */
    public void setOnRecentScenesChanged(Runnable callback) {
        this.onRecentScenesChanged = callback;
    }

    /**
     * Sets a callback for status messages.
     */
    public void setMessageCallback(Consumer<String> callback) {
        this.messageCallback = callback;
    }

    /**
     * Creates a new empty scene.
     */
    public void newScene() {
        System.out.println("Creating new scene...");

        // Destroy current scene
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene != null) {
            currentScene.destroy();
        }

        // Clear undo history (actions from previous scene don't apply)
        UndoManager.getInstance().clear();

        // Create new scene (name will be "Untitled" since no filePath)
        EditorScene newScene = new EditorScene();
        context.setCurrentScene(newScene);

        // Reset camera
        context.getCamera().reset();

        showMessage("New scene created");
    }

    /**
     * Opens a scene from a file path.
     */
    public void openScene(String path) {
        System.out.println("Opening scene: " + path);

        // Destroy current scene
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene != null) {
            currentScene.destroy();
        }

        // Clear undo history (actions from previous scene don't apply)
        UndoManager.getInstance().clear();

        try {
            // Load scene data (use raw() to skip asset root - scenes are in gameData/scenes/)
            SceneData sceneData = Assets.load(path, LoadOptions.raw());
            EditorScene loadedScene = EditorSceneSerializer.fromSceneData(sceneData, path);

            context.setCurrentScene(loadedScene);

            // Reset camera
            context.getCamera().reset();

            // Track as recent scene
            addToRecentScenes(path);

            showMessage("Opened: " + sceneData.getName());
        } catch (Exception e) {
            System.err.println("Failed to open scene: " + e.getMessage());
            e.printStackTrace();
            showMessage("Error opening scene: " + e.getMessage());
        }
    }

    /**
     * Adds a scene path to the recent scenes list and persists the config.
     */
    private void addToRecentScenes(String path) {
        EditorConfig config = context.getConfig();
        config.addRecentScene(path);
        ConfigLoader.saveConfigToFile(config, ConfigLoader.ConfigType.EDITOR);

        // Notify listeners
        if (onRecentScenesChanged != null) {
            onRecentScenesChanged.run();
        }
    }

    /**
     * Saves the current scene to its file path.
     */
    public void saveScene() {
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene == null || currentScene.getFilePath() == null) {
            return;
        }

        System.out.println("Saving scene: " + currentScene.getFilePath());

        try {
            SceneData data = EditorSceneSerializer.toSceneData(currentScene);
            // Use raw() to skip asset root - scenes are in gameData/scenes/, not gameData/assets/
            Assets.persist(data, currentScene.getFilePath(), LoadOptions.raw());

            currentScene.clearDirty();
            showMessage("Saved: " + currentScene.getName());
        } catch (Exception e) {
            System.err.println("Failed to save scene: " + e.getMessage());
            e.printStackTrace();
            showMessage("Error saving: " + e.getMessage());
        }
    }

    /**
     * Saves the current scene to a new file path.
     * Scene name is automatically derived from the file path.
     */
    public void saveSceneAs(String path) {
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene == null) {
            return;
        }

        System.out.println("Saving scene as: " + path);

        // Set file path - name is derived from it automatically
        currentScene.setFilePath(path);

        saveScene();
    }

    /**
     * Checks if the current scene has unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        EditorScene currentScene = context.getCurrentScene();
        return currentScene != null && currentScene.hasUnsavedChanges();
    }

    /**
     * Gets the current scene's display name.
     */
    public String getSceneDisplayName() {
        EditorScene currentScene = context.getCurrentScene();
        return currentScene != null ? currentScene.getDisplayName() : "No Scene";
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }
}