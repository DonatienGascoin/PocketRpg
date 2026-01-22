package com.pocket.rpg.editor;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.resources.Assets;
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

    public EditorSceneController(EditorContext context) {
        this.context = context;
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

        // Create new scene
        EditorScene newScene = new EditorScene("Untitled");
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
            // Load scene data
            SceneData sceneData = Assets.load(path);
            EditorScene loadedScene = EditorSceneSerializer.fromSceneData(sceneData, path);

            context.setCurrentScene(loadedScene);

            // Reset camera
            context.getCamera().reset();

            showMessage("Opened: " + sceneData.getName());
        } catch (Exception e) {
            System.err.println("Failed to open scene: " + e.getMessage());
            e.printStackTrace();
            showMessage("Error opening scene: " + e.getMessage());
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
            Assets.persist(data, currentScene.getFilePath());

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
     */
    public void saveSceneAs(String path) {
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene == null) {
            return;
        }

        System.out.println("Saving scene as: " + path);

        currentScene.setFilePath(path);

        // Extract name from path
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSep >= 0 ? path.substring(lastSep + 1) : path;
        if (fileName.endsWith(".scene")) {
            fileName = fileName.substring(0, fileName.length() - 6);
        }
        currentScene.setName(fileName);

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