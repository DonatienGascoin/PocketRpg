package com.pocket.rpg.editor;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.RecentScenesChangedEvent;
import com.pocket.rpg.editor.events.RegistriesRefreshRequestEvent;
import com.pocket.rpg.editor.events.SceneWillChangeEvent;
import com.pocket.rpg.editor.events.StatusMessageEvent;
import com.pocket.rpg.editor.panels.StaleReferencesPopup;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.LoadOptions;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.SceneData;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Handles scene operations: new, open, save.
 * <p>
 * Manages scene lifecycle and notifies the context when scenes change.
 */
public class EditorSceneController {

    private final EditorContext context;

    @Setter
    private PlayModeController playModeController;

    // Callback when recent scenes list changes (legacy - will be migrated to event)
    private Runnable onRecentScenesChanged;

    @Getter
    private final StaleReferencesPopup staleReferencesPopup = new StaleReferencesPopup();

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
     * Creates a new empty scene.
     */
    public void newScene() {
        // Notify subscribers before scene changes (e.g., stop play mode)
        SceneWillChangeEvent event = new SceneWillChangeEvent();
        EditorEventBus.get().publish(event);
        if (event.isCancelled()) {
            return;
        }

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
        // Notify subscribers before scene changes (e.g., stop play mode)
        SceneWillChangeEvent event = new SceneWillChangeEvent();
        EditorEventBus.get().publish(event);
        if (event.isCancelled()) {
            return;
        }

        System.out.println("Opening scene: " + path);

        // Destroy current scene
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene != null) {
            currentScene.destroy();
        }

        // Clear undo history (actions from previous scene don't apply)
        UndoManager.getInstance().clear();

        // Reset tracking BEFORE load to capture all refs (scene + nested prefabs)
        ComponentRegistry.resetFallbackTracking();

        try {
            // Load scene data (use raw() to skip asset root - scenes are in gameData/scenes/)
            SceneData sceneData = Assets.load(path, LoadOptions.raw());
            EditorScene loadedScene = EditorSceneSerializer.fromSceneData(sceneData, path);

            // Capture all fallback resolutions (scene + any prefabs loaded during deserialization)
            List<String> resolutions = ComponentRegistry.getFallbackResolutions();

            context.setCurrentScene(loadedScene);

            // Reset camera
            context.getCamera().reset();

            // Track as recent scene
            addToRecentScenes(path);

            // Show popup if any stale references were found
            if (!resolutions.isEmpty()) {
                Log.warn("EditorSceneController", "Scene has " + resolutions.size() + " stale component reference(s): " + path);
                staleReferencesPopup.open(resolutions, () -> {
                    try {
                        saveScene();
                        showMessage("Scene saved - stale references updated");
                    } catch (Exception e) {
                        showMessage("Save failed: " + e.getMessage());
                        Log.error("EditorSceneController", "Failed to save scene after stale reference prompt", e);
                    }
                });
            }

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

        // Notify legacy listener
        if (onRecentScenesChanged != null) {
            onRecentScenesChanged.run();
        }
        // Publish event
        EditorEventBus.get().publish(new RecentScenesChangedEvent());
    }

    /**
     * Reloads the current scene by refreshing all registries and
     * rebuilding the scene from its serialized state.
     * <p>
     * Preserves camera position, zoom, selection, and dirty flag.
     * Uses swap-then-destroy: the old scene is only destroyed after the
     * new scene is successfully built from the snapshot.
     */
    public void reloadScene() {
        EditorScene currentScene = context.getCurrentScene();
        if (currentScene == null) {
            showMessage("No scene to reload");
            return;
        }

        // Guard: do nothing during play mode.
        // Shortcuts are suppressed during play mode, but menu bar bypasses shortcuts.
        if (playModeController != null && playModeController.isActive()) {
            showMessage("Cannot reload during play mode");
            return;
        }

        // 1. Capture editor state
        EditorStateSnapshot stateSnapshot = EditorStateSnapshot.capture(context);

        // 2. Snapshot scene to SceneData (deep-copies components)
        SceneData sceneSnapshot = EditorSceneSerializer.toSceneData(currentScene);

        // 3. Publish event so all registries re-scan (OCP: no hard-coded list)
        EditorEventBus.get().publish(new RegistriesRefreshRequestEvent());

        // 4. Rebuild scene from snapshot
        EditorScene newScene;
        try {
            newScene = EditorSceneSerializer.fromSceneData(sceneSnapshot, stateSnapshot.scenePath());
        } catch (Exception e) {
            System.err.println("Scene reload failed: " + e.getMessage());
            e.printStackTrace();
            showMessage("Reload failed: " + e.getMessage());
            // Old scene is still alive — abort reload, no data loss.
            return;
        }

        // 5. Success — now safe to tear down the old scene.
        //    Publish SceneWillChangeEvent for subscribers (play mode, etc.)
        EditorEventBus.get().publish(new SceneWillChangeEvent());
        currentScene.destroy();
        UndoManager.getInstance().clear();

        // 6. Clear selection before swapping scene.
        //    context.setCurrentScene() does NOT clear selection state (it only
        //    assigns the scene field via Lombok setter). Without this, the
        //    selection manager would hold stale entity references.
        context.getSelectionManager().clearSelection();

        // 7. Swap scene on context (notifies all panels via onSceneChanged)
        context.setCurrentScene(newScene);

        // 8. Restore editor state (camera, selection, dirty flag)
        stateSnapshot.restore(context, newScene);

        showMessage("Scene reloaded - registries refreshed");
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
        EditorEventBus.get().publish(new StatusMessageEvent(message));
    }
}