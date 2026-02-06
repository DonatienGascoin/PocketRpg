package com.pocket.rpg.editor;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.SceneChangedEvent;
import com.pocket.rpg.editor.core.EditorWindow;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.inspectors.ComponentKeyField;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared state container for the editor.
 * <p>
 * Holds references to all core systems and provides a single point of access
 * for controllers. Supports listeners for scene changes.
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
    private EditorSelectionManager selectionManager;

    @Getter
    private SelectionGuard selectionGuard;

    @Getter
    private EditorScene currentScene;

    @Getter
    @Setter
    private boolean running = true;

    // Scene change listeners
    private final List<Consumer<EditorScene>> sceneChangedListeners = new ArrayList<>();

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
        this.selectionManager = new EditorSelectionManager();
        this.selectionGuard = new SelectionGuard(selectionManager, modeManager);
    }

    /**
     * Sets the current scene and notifies listeners.
     */
    public void setCurrentScene(EditorScene scene) {
        this.currentScene = scene;
        if (selectionManager != null) {
            selectionManager.setScene(scene);
        }
        // Update FieldEditorContext so inspectors can access the scene
        FieldEditorContext.setCurrentScene(scene);
        ComponentKeyField.clearExpandedState();
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
        // Notify legacy listeners
        for (var listener : sceneChangedListeners) {
            listener.accept(scene);
        }
        // Publish event
        EditorEventBus.get().publish(new SceneChangedEvent(scene));
    }

    /**
     * Requests application exit.
     */
    public void requestExit() {
        running = false;
    }
}