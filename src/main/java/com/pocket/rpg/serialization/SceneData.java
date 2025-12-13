package com.pocket.rpg.serialization;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Root data structure for scene serialization.
 * Contains all data needed to reconstruct a scene at runtime.
 * <p>
 * This is the object that gets serialized to/from .scene JSON files.
 */
@Setter
@Getter
public class SceneData {

    /**
     * Scene format version for migration support
     */
    private int version = 1;

    /**
     * Scene name (display name, not file name)
     */
    private String name;

    /**
     * Root-level GameObjects (children are nested within)
     */
    private List<GameObjectData> gameObjects = new ArrayList<>();

    /**
     * Camera settings
     */
    private CameraData camera;

    /**
     * Scene-level metadata (can store custom data)
     */
    private java.util.Map<String, Object> metadata;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public SceneData() {
    }

    public SceneData(String name) {
        this.name = name;
    }

    // ========================================================================
    // GETTERS / SETTERS
    // ========================================================================

    public void addGameObject(GameObjectData gameObject) {
        this.gameObjects.add(gameObject);
    }

    // ========================================================================
    // NESTED CLASSES
    // ========================================================================

    /**
     * Camera configuration for the scene.
     */
    @Setter
    @Getter
    public static class CameraData {
        private float[] position = {0, 0, 0};
        private float orthographicSize = 15f;

        public CameraData() {
        }

        public CameraData(float x, float y, float z, float orthographicSize) {
            this.position = new float[]{x, y, z};
            this.orthographicSize = orthographicSize;
        }

    }
}
