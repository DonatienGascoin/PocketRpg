package com.pocket.rpg.scenes;

import com.pocket.rpg.serialization.SceneData;
import lombok.Getter;
import lombok.Setter;

/**
 * Concrete Scene implementation for scenes loaded from .scene files.
 * <p>
 * Unlike game-specific scenes that override onLoad() to create GameObjects
 * programmatically, RuntimeScene is populated by RuntimeSceneLoader after instantiation.
 * <p>
 * Camera configuration is stored separately and applied by SceneManager
 * after initialize() creates the camera with defaults.
 */
public class RuntimeScene extends Scene {

    /**
     * Camera configuration from scene file.
     * Applied by SceneManager after initialize() creates the camera.
     */
    @Getter
    @Setter
    private SceneData.CameraData cameraData;

    public RuntimeScene(String name) {
        super(name);
    }

    @Override
    public void onLoad() {
        // Empty - GameObjects are added by RuntimeSceneLoader after construction
    }
}
