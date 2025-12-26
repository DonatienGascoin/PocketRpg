package com.pocket.rpg.editor;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.ToolManager;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared state container for the editor.
 * <p>
 * Holds references to all core systems and provides a single point of access
 * for controllers. Supports listeners for scene and mode changes.
 */
public class EditorContext {

    @Getter
    private EditorConfig config;

    @Getter
    private GameConfig gameConfig;

    @Getter
    private InputConfig inputConfig;

    @Getter
    private RenderingConfig renderingConfig;

    @Getter
    private EditorWindow window;

    @Getter
    private EditorCamera camera;

    @Getter
    private ToolManager toolManager;

    @Getter
    private EditorModeManager modeManager;

    @Getter
    private EditorScene currentScene;

    @Getter
    @Setter
    private boolean running = true;

    // Scene change listeners
    private final List<Consumer<EditorScene>> sceneChangedListeners = new ArrayList<>();

    // Mode change listeners
    private final List<Consumer<EditorModeManager.Mode>> modeChangedListeners = new ArrayList<>();

    /**
     * Initializes the context with core systems.
     */
    public void init(EditorConfig config, RenderingConfig renderingConfig,
                     GameConfig gameConfig, InputConfig inputConfig, EditorWindow window, EditorCamera camera) {
        this.config = config;
        this.renderingConfig = renderingConfig;
        this.gameConfig = gameConfig;
        this.inputConfig = inputConfig;
        this.window = window;
        this.camera = camera;
        this.toolManager = new ToolManager();
        this.modeManager = new EditorModeManager();
    }

    /**
     * Sets the current scene and notifies listeners.
     */
    public void setCurrentScene(EditorScene scene) {
        this.currentScene = scene;
        notifySceneChanged(scene);
    }

    /**
     * Registers a listener for scene changes.
     */
    public void onSceneChanged(Consumer<EditorScene> listener) {
        sceneChangedListeners.add(listener);
    }

    /**
     * Notifies all listeners of a scene change.
     */
    private void notifySceneChanged(EditorScene scene) {
        for (var listener : sceneChangedListeners) {
            listener.accept(scene);
        }
    }

    // ========================================================================
    // MODE MANAGEMENT
    // ========================================================================

    /**
     * Registers a listener for mode changes.
     * Listener is called after mode has been switched.
     */
    public void onModeChanged(Consumer<EditorModeManager.Mode> listener) {
        modeChangedListeners.add(listener);
    }

    /**
     * Switches to a new editor mode and notifies all listeners.
     * Use this instead of calling modeManager directly.
     */
    public void switchMode(EditorModeManager.Mode mode) {
        if (modeManager.getCurrentMode() == mode) {
            // Still notify listeners even if mode unchanged (for selection sync)
            notifyModeChanged(mode);
            return;
        }

        modeManager.switchTo(mode);

        // Set default tool for mode
        switch (mode) {
            case TILEMAP -> toolManager.setActiveTool("Brush");
            case COLLISION -> toolManager.setActiveTool("Collision Brush");
            case ENTITY -> toolManager.setActiveTool("Select");
        }

        notifyModeChanged(mode);
    }

    /**
     * Convenience method to switch to tilemap mode.
     */
    public void switchToTilemapMode() {
        switchMode(EditorModeManager.Mode.TILEMAP);
    }

    /**
     * Convenience method to switch to collision mode.
     */
    public void switchToCollisionMode() {
        switchMode(EditorModeManager.Mode.COLLISION);
    }

    /**
     * Convenience method to switch to entity mode.
     */
    public void switchToEntityMode() {
        switchMode(EditorModeManager.Mode.ENTITY);
    }

    /**
     * Notifies all listeners of a mode change.
     */
    private void notifyModeChanged(EditorModeManager.Mode mode) {
        for (var listener : modeChangedListeners) {
            listener.accept(mode);
        }
    }

    /**
     * Requests application exit.
     */
    public void requestExit() {
        running = false;
    }
}