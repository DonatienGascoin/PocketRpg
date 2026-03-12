package com.pocket.rpg.scenes;

import com.pocket.rpg.components.interaction.CameraBoundsZone;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.ui.ComponentKeyRegistry;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production implementation of {@link SceneManagerContext}.
 * Handles scene loading, lifecycle, and management.
 */
public class DefaultSceneManagerContext implements SceneManagerContext {

    @Getter
    private Scene activeScene;

    private final Map<String, Scene> scenes;
    private final List<SceneLifecycleListener> lifecycleListeners;

    @Getter
    private Scene currentScene;

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    // File-based scene loading
    private RuntimeSceneLoader sceneLoader;
    private String scenesBasePath;

    // Spawn point to teleport to after loading a scene
    @Getter
    private String pendingSpawnId;

    public DefaultSceneManagerContext(@NonNull ViewportConfig viewportConfig, @NonNull RenderingConfig renderingConfig) {
        this.scenes = new HashMap<>();
        this.lifecycleListeners = new ArrayList<>();
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;
    }

    // ========================================================================
    // SCENE LOADER CONFIGURATION
    // ========================================================================

    @Override
    public void setSceneLoader(RuntimeSceneLoader loader, String basePath) {
        this.sceneLoader = loader;
        this.scenesBasePath = basePath != null ? basePath : "";
    }

    // ========================================================================
    // SCENE REGISTRATION
    // ========================================================================

    @Override
    public void registerScene(Scene scene) {
        scenes.put(scene.getName(), scene);
    }

    // ========================================================================
    // LIFECYCLE LISTENERS
    // ========================================================================

    @Override
    public void addLifecycleListener(SceneLifecycleListener listener) {
        if (!lifecycleListeners.contains(listener)) {
            lifecycleListeners.add(listener);
        }
    }

    @Override
    public void removeLifecycleListener(SceneLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    // ========================================================================
    // SCENE LOADING
    // ========================================================================

    @Override
    public void loadScene(String sceneName) {
        ComponentKeyRegistry.clear();

        Scene scene = scenes.get(sceneName);

        if (scene == null && sceneLoader != null) {
            scene = loadSceneFromFile(sceneName);
        }

        if (scene == null) {
            System.err.println("Scene not found: " + sceneName);
            return;
        }

        loadScene(scene);
    }

    @Override
    public void loadScene(String sceneName, String spawnId) {
        this.pendingSpawnId = spawnId;
        loadScene(sceneName);
        this.pendingSpawnId = null;
    }

    @Override
    public void loadScene(Scene scene) {
        loadSceneInternal(scene, pendingSpawnId);
    }

    private void loadSceneInternal(Scene scene, String spawnId) {
        if (currentScene != null) {
            currentScene.notifyBeforeUnload();
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
            activeScene = null;
        }

        currentScene = scene;
        activeScene = scene;

        currentScene.initialize(viewportConfig, renderingConfig);

        if (scene instanceof RuntimeScene runtimeScene) {
            applyCameraData(runtimeScene);
        }

        firePostSceneInitialize(currentScene);
        fireSceneLoaded(currentScene);

        System.out.println("Loaded scene: " + scene.getName());
    }

    private Scene loadSceneFromFile(String sceneName) {
        String[] pathsToTry = {
                scenesBasePath + sceneName + ".scene",
                scenesBasePath + sceneName,
                sceneName + ".scene",
                sceneName
        };

        for (String path : pathsToTry) {
            try {
                RuntimeScene scene = sceneLoader.loadFromPath(path);
                if (scene != null) {
                    System.out.println("Loaded scene from file: " + path);
                    return scene;
                }
            } catch (Exception e) {
                // Try next path
            }
        }

        System.err.println("Could not find scene file for: " + sceneName);
        System.err.println("  Tried paths: " + String.join(", ", pathsToTry));
        return null;
    }

    private void applyCameraData(RuntimeScene scene) {
        SceneData.CameraData cameraData = scene.getCameraData();
        if (cameraData == null || scene.getCamera() == null) {
            return;
        }

        float[] pos = cameraData.getPosition();
        if (pos != null && pos.length >= 2) {
            scene.getCamera().setPosition(pos[0], pos[1]);
        }

        float orthoSize = cameraData.getOrthographicSize();
        if (orthoSize > 0) {
            scene.getCamera().setOrthographicSize(orthoSize);
        }

        String boundsId = SaveManager.getGlobal("camera", "activeBoundsId", "");
        if (boundsId.isEmpty()) {
            boundsId = cameraData.getInitialBoundsId();
        }
        if (boundsId != null && !boundsId.isEmpty()) {
            applyCameraBoundsZone(scene, boundsId);
        }

        System.out.println("Applied camera data: orthoSize=" + orthoSize);
    }

    private void applyCameraBoundsZone(Scene scene, String boundsId) {
        for (GameObject obj : scene.getGameObjects()) {
            CameraBoundsZone zone = obj.getComponent(CameraBoundsZone.class);
            if (zone != null && boundsId.equals(zone.getBoundsId())) {
                zone.applyBounds(scene.getCamera());
                System.out.println("Applied camera bounds zone: " + boundsId);
                return;
            }
        }
        System.err.println("CameraBoundsZone not found: " + boundsId);
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    @Override
    public void destroy() {
        if (currentScene != null) {
            activeScene = null;
            currentScene.destroy();
            fireSceneUnloaded(currentScene);
        }
        ComponentKeyRegistry.clear();
        GameCamera.setMainCamera(null);
        scenes.clear();
        lifecycleListeners.clear();
    }

    // ========================================================================
    // EVENTS
    // ========================================================================

    private void fireSceneLoaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneLoaded(scene);
        }
    }

    private void fireSceneUnloaded(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onSceneUnloaded(scene);
        }
    }

    private void firePostSceneInitialize(Scene scene) {
        for (SceneLifecycleListener listener : lifecycleListeners) {
            listener.onPostSceneInitialize(scene);
        }
    }
}
