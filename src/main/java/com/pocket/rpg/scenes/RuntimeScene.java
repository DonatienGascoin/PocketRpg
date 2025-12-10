package com.pocket.rpg.scenes;

/**
 * Concrete Scene implementation for scenes loaded from .scene files.
 * <p>
 * Unlike game-specific scenes that override onLoad() to create GameObjects
 * programmatically, RuntimeScene is populated by SceneLoader after instantiation.
 */
public class RuntimeScene extends Scene {

    public RuntimeScene(String name) {
        super(name);
    }

    @Override
    public void onLoad() {
        // Empty - GameObjects are added by SceneLoader after construction
    }
}
