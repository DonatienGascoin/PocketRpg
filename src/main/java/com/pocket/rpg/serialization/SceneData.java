package com.pocket.rpg.serialization;

import com.pocket.rpg.editor.serialization.EntityData;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Root data structure for scene serialization.
 * Contains all data needed to reconstruct a scene at runtime.
 * <p>
 * This is the object that gets serialized to/from .scene JSON files.
 * <p>
 * Version history:
 * - v1: Initial format
 * - v2: Z-level collision support
 * - v3: Entity placement support
 */
@Setter
@Getter
public class SceneData {

    /**
     * Scene format version for migration support
     */
    private int version = 3;  // Incremented for entity support

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
     * Collision map data with Z-level support (sparse format).
     * <p>
     * Format: Map<"z-level", Map<"cx,cy", Map<"tx,ty", collisionTypeId>>>
     * <p>
     * Example:
     * <pre>
     * {
     *   "0": {                    // Ground level
     *     "0,0": {                // Chunk at (0,0)
     *       "5,5": 1,             // SOLID at tile (5,5)
     *       "10,10": 2            // LEDGE_DOWN at tile (10,10)
     *     }
     *   },
     *   "1": {                    // Bridge level
     *     "0,0": {
     *       "5,5": 0              // NONE (walkable bridge)
     *     }
     *   }
     * }
     * </pre>
     */
    private Map<String, Map<String, Map<String, Integer>>> collision;

    /**
     * Placed entity instances.
     * Each entity references a prefab and stores position + property overrides.
     */
    private List<EntityData> entities = new ArrayList<>();

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

    public void addEntity(EntityData entity) {
        this.entities.add(entity);
    }

    // ========================================================================
    // NESTED CLASSES
    // ========================================================================

    /**
     * Camera configuration for the scene.
     * <p>
     * Defines initial camera state and runtime behavior.
     */
    @Setter
    @Getter
    public static class CameraData {
        /**
         * Initial camera position [x, y, z]
         */
        private float[] position = {0, 0, 0};

        /**
         * Orthographic size (half-height in world units)
         */
        private float orthographicSize = 15f;

        /**
         * Whether camera should follow a target entity
         */
        private boolean followPlayer = true;

        /**
         * Name of the entity to follow
         */
        private String followTargetName = "Player";

        /**
         * Whether to clamp camera to bounds
         */
        private boolean useBounds = false;

        /**
         * Camera bounds [minX, minY, maxX, maxY]
         */
        private float[] bounds = {0, 0, 0, 0};

        public CameraData() {
        }

        public CameraData(float x, float y, float z, float orthographicSize) {
            this.position = new float[]{x, y, z};
            this.orthographicSize = orthographicSize;
        }

        /**
         * Full constructor with all parameters.
         */
        public CameraData(float x, float y, float z, float orthographicSize,
                          boolean followPlayer, String followTargetName,
                          boolean useBounds, float[] bounds) {
            this.position = new float[]{x, y, z};
            this.orthographicSize = orthographicSize;
            this.followPlayer = followPlayer;
            this.followTargetName = followTargetName;
            this.useBounds = useBounds;
            this.bounds = bounds != null ? bounds : new float[]{0, 0, 0, 0};
        }
    }
}
