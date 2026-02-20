package com.pocket.rpg.editor;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.AudioContext;
import com.pocket.rpg.audio.EditorSharedAudioContext;
import com.pocket.rpg.audio.music.MusicManager;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.application.GameEngine;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.editor.serialization.EditorSceneSerializer;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.PlayerPlacementHandler;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.PlayModePausedEvent;
import com.pocket.rpg.editor.events.PlayModeStartedEvent;
import com.pocket.rpg.editor.events.PlayModeStoppedEvent;
import com.pocket.rpg.editor.events.PrefabEditStartedEvent;
import com.pocket.rpg.editor.events.PrefabEditStoppedEvent;
import com.pocket.rpg.editor.events.SceneWillChangeEvent;
import com.pocket.rpg.editor.ui.fields.FieldUndoTracker;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.platform.glfw.GLFWPlatformFactory;
import com.pocket.rpg.time.DefaultTimeContext;
import com.pocket.rpg.time.Time;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controls Play Mode lifecycle in the Scene Editor.
 * <p>
 * Uses {@link GameEngine} to manage all game subsystems (pipeline, scene manager,
 * game loop, transitions, etc.). Play mode shares the editor's Audio context
 * and initializes its own Time context.
 *
 * @see GameEngine
 */
public class PlayModeController {

    public enum PlayState {
        STOPPED,
        PLAYING,
        PAUSED
    }

    private final EditorContext context;
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;
    private final InputConfig inputConfig;

    @Getter
    private PlayState state = PlayState.STOPPED;

    // Editor scene backup
    private SceneData snapshot;
    private String snapshotFilePath;

    // Runtime systems
    private GameEngine engine;
    private PlayModeInputManager inputManager;

    // Saved editor audio context (restored after play mode)
    private AudioContext editorAudioContext;

    // Play mode selection (separate from editor selection)
    @Getter
    private PlayModeSelectionManager playModeSelectionManager;

    // Display area within editor window (set by GameViewPanel each frame)
    private float displayX, displayY, displayWidth, displayHeight;

    // Rendering (play mode renders to framebuffer, not screen)
    private EditorFramebuffer outputFramebuffer;

    private Consumer<String> messageCallback;

    // Prefab edit mode state (blocks play mode)
    private boolean prefabEditActive = false;


    public PlayModeController(EditorContext context, GameConfig gameConfig, InputConfig inputConfig) {
        this.context = context;
        this.gameConfig = gameConfig;
        this.renderingConfig = context.getRenderingConfig();
        this.inputConfig = inputConfig;

        // Stop play mode when scene is about to change
        EditorEventBus.get().subscribe(SceneWillChangeEvent.class, event -> {
            if (isActive()) {
                stop();
            }
        });

        // Subscribe to prefab edit events to block play mode
        EditorEventBus.get().subscribe(PrefabEditStartedEvent.class, e -> prefabEditActive = true);
        EditorEventBus.get().subscribe(PrefabEditStoppedEvent.class, e -> prefabEditActive = false);
    }

    public void setMessageCallback(Consumer<String> callback) {
        this.messageCallback = callback;
    }

    // ========================================================================
    // STATE TRANSITIONS
    // ========================================================================

    public void play() {
        if (state != PlayState.STOPPED) {
            showMessage("Already playing");
            return;
        }

        // Guard: Cannot start play mode while editing prefab
        if (prefabEditActive) {
            showMessage("Cannot start play mode while editing prefab");
            return;
        }

        EditorScene editorScene = context.getCurrentScene();
        if (editorScene == null) {
            showMessage("No scene to play");
            return;
        }

        try {
            // 1. Snapshot editor scene (deep copy via JSON serialization)
            SceneData tempData = EditorSceneSerializer.toSceneData(editorScene);
            snapshot = Serializer.deepCopy(tempData, SceneData.class);
            snapshotFilePath = editorScene.getFilePath();

            // 2. Save editor's audio context (restored in cleanup)
            editorAudioContext = Audio.getContext();

            // 3. Initialize input (sets up GLFW callbacks, creates DefaultInputContext)
            long windowHandle = context.getWindow().getWindowHandle();
            inputManager = new PlayModeInputManager(windowHandle, inputConfig);
            inputManager.init();

            // 4. Create engine with all 3 contexts
            engine = GameEngine.builder()
                    .gameConfig(gameConfig)
                    .renderingConfig(renderingConfig)
                    .window(context.getWindow())
                    .platformFactory(new GLFWPlatformFactory())
                    .timeContext(new DefaultTimeContext())
                    .audioContext(new EditorSharedAudioContext(editorAudioContext))
                    .inputContext(inputManager.getInputContext())
                    .build();
            engine.init();

            // 5. Create output framebuffer
            outputFramebuffer = new EditorFramebuffer(gameConfig.getGameWidth(), gameConfig.getGameHeight());
            outputFramebuffer.init();

            // 6. Configure scene loading and load from snapshot
            RuntimeSceneLoader sceneLoader = new RuntimeSceneLoader();
            engine.getSceneManager().setSceneLoader(sceneLoader, "gameData/scenes/");
            // TODO: SaveManager, MusicManager, and PlayerPlacementHandler are game-level concerns
            //  and should not be initialized here. Move to a game-specific bootstrap once one exists.
            SaveManager.initialize(engine.getSceneManager());
            MusicManager.initialize(engine.getSceneManager(), Assets.getContext());
            engine.getSceneManager().addLifecycleListener(new PlayerPlacementHandler(engine.getSceneManager()));

            SceneData runtimeCopy = Serializer.deepCopy(snapshot, SceneData.class);
            RuntimeScene runtimeScene = sceneLoader.load(runtimeCopy);
            engine.getSceneManager().loadScene(runtimeScene);

            // 7. Create play mode selection manager
            playModeSelectionManager = new PlayModeSelectionManager();

            // 8. Clear stale undo state from editor
            FieldUndoTracker.clear();

            // 9. Switch state
            state = PlayState.PLAYING;
            context.getModeManager().setMode(EditorMode.PLAY);
            EditorEventBus.get().publish(new PlayModeStartedEvent());

            showMessage("Play mode started");
            System.out.println("Play mode started: " + editorScene.getName());

        } catch (Exception e) {
            System.err.println("Failed to start play mode: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            showMessage("Error: " + e.getMessage());
        }
    }

    public void pause() {
        if (state != PlayState.PLAYING) return;
        state = PlayState.PAUSED;
        Audio.pauseAll();
        EditorEventBus.get().publish(new PlayModePausedEvent());
        showMessage("Paused");
    }

    public void resume() {
        if (state != PlayState.PAUSED) return;
        state = PlayState.PLAYING;
        Audio.resumeAll();
        EditorEventBus.get().publish(new PlayModeStartedEvent());
        showMessage("Resumed");
    }

    public void stop() {
        if (state == PlayState.STOPPED) return;

        System.out.println("Stopping play mode...");

        FieldUndoTracker.clear();
        cleanup();

        snapshot = null;
        snapshotFilePath = null;
        state = PlayState.STOPPED;
        context.getModeManager().setMode(EditorMode.SCENE);
        EditorEventBus.get().publish(new PlayModeStoppedEvent());

        showMessage("Play mode stopped");
        System.out.println("Play mode stopped");
    }

    // ========================================================================
    // UPDATE / RENDER
    // ========================================================================

    public void update(float deltaTime) {
        if (state != PlayState.PLAYING) return;

        // Prune destroyed objects from selection once per frame
        if (playModeSelectionManager != null) {
            playModeSelectionManager.pruneDestroyedObjects();
        }

        // Update input (axis smoothing, gamepad polling)
        if (inputManager != null) {
            inputManager.update(Time.deltaTime());
        }

        // UI input (hover, clicks) - convert editor window mouse to game coordinates
        updateUIInput();

        // Update game logic (GameLoop handles transition freeze)
        if (engine != null) {
            engine.update();
        }
    }

    /**
     * Sets the display area of the game image within the editor window.
     * Called by GameViewPanel after rendering the game texture.
     * Uses previous frame's values, which is standard for input.
     */
    public void setDisplayArea(float x, float y, float width, float height) {
        this.displayX = x;
        this.displayY = y;
        this.displayWidth = width;
        this.displayHeight = height;
    }

    private void updateUIInput() {
        if (engine == null) return;
        if (displayWidth <= 0 || displayHeight <= 0) return;

        // Convert editor window mouse position to game coordinates
        var mousePos = Input.getMousePosition();
        float gameMouseX = (mousePos.x - displayX) / displayWidth * gameConfig.getGameWidth();
        float gameMouseY = (mousePos.y - displayY) / displayHeight * gameConfig.getGameHeight();

        engine.updateUIInput(gameMouseX, gameMouseY);
    }

    public void render() {
        if (state == PlayState.STOPPED) return;
        if (engine == null || outputFramebuffer == null) return;

        FramebufferTarget target = new FramebufferTarget(outputFramebuffer);
        engine.render(target);
        engine.endFrame();
    }

    public int getOutputTexture() {
        return outputFramebuffer != null ? outputFramebuffer.getTextureId() : 0;
    }

    public int getGameWidth() {
        return gameConfig.getGameWidth();
    }

    public int getGameHeight() {
        return gameConfig.getGameHeight();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void cleanup() {
        // Stop music playback
        if (Audio.music() != null) {
            Audio.music().stop();
        }

        if (inputManager != null) {
            inputManager.destroy();
            inputManager = null;
        }

        if (engine != null) {
            engine.destroy();  // nulls Audio and Input singletons
            engine = null;
        }

        // Restore editor's audio context
        if (editorAudioContext != null) {
            Audio.setContext(editorAudioContext);
            editorAudioContext = null;
        }

        if (outputFramebuffer != null) {
            outputFramebuffer.destroy();
            outputFramebuffer = null;
        }

        if (playModeSelectionManager != null) {
            playModeSelectionManager.clearSelection();
            playModeSelectionManager = null;
        }
        RuntimeGameObjectAdapter.clearCache();
    }

    private void showMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * Returns the currently running runtime scene, or null if not in play mode.
     */
    public Scene getRuntimeScene() {
        return engine != null ? engine.getSceneManager().getCurrentScene() : null;
    }

    public boolean isActive() {
        return state != PlayState.STOPPED;
    }

    public boolean isPlaying() {
        return state == PlayState.PLAYING;
    }

    public boolean isPaused() {
        return state == PlayState.PAUSED;
    }

    public boolean isStopped() {
        return state == PlayState.STOPPED;
    }

    /**
     * Gets all available scene names from the scenes directory.
     *
     * @return list of scene names (without .scene extension)
     */
    public List<String> getAvailableScenes() {
        List<String> scenes = new ArrayList<>();
        File scenesDir = new File("gameData/scenes/");

        if (scenesDir.exists() && scenesDir.isDirectory()) {
            File[] files = scenesDir.listFiles((dir, name) -> name.endsWith(".scene"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    scenes.add(name.substring(0, name.length() - 6));
                }
            }
        }

        return scenes;
    }

    /**
     * Gets the transition manager (only available during play mode).
     *
     * @return the transition manager, or null if not in play mode
     */
    public TransitionManager getTransitionManager() {
        return engine != null ? engine.getTransitionManager() : null;
    }

    /**
     * Gets the post processor (only available during play mode).
     *
     * @return the post processor, or null if not in play mode
     */
    public PostProcessor getPostProcessor() {
        return engine != null ? engine.getPostProcessor() : null;
    }
}
