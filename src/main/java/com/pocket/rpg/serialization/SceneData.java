package com.pocket.rpg.serialization;

import java.util.ArrayList;
import java.util.List;

/**
 * Root data structure for scene serialization.
 * Contains all data needed to reconstruct a scene at runtime.
 * <p>
 * This is the object that gets serialized to/from .scene JSON files.
 */
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<GameObjectData> getGameObjects() {
        return gameObjects;
    }

    public void setGameObjects(List<GameObjectData> gameObjects) {
        this.gameObjects = gameObjects;
    }

    public void addGameObject(GameObjectData gameObject) {
        this.gameObjects.add(gameObject);
    }

    public CameraData getCamera() {
        return camera;
    }

    public void setCamera(CameraData camera) {
        this.camera = camera;
    }

    public java.util.Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // ========================================================================
    // NESTED CLASSES
    // ========================================================================

    /**
     * Camera configuration for the scene.
     */
    public static class CameraData {
        private float[] position = {0, 0, 0};
        private float orthographicSize = 15f;

        public CameraData() {
        }

        public CameraData(float x, float y, float z, float orthographicSize) {
            this.position = new float[]{x, y, z};
            this.orthographicSize = orthographicSize;
        }

        public float[] getPosition() {
            return position;
        }

        public void setPosition(float[] position) {
            this.position = position;
        }

        public float getOrthographicSize() {
            return orthographicSize;
        }

        public void setOrthographicSize(float orthographicSize) {
            this.orthographicSize = orthographicSize;
        }
    }
}
