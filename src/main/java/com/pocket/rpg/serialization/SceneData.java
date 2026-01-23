package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
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
 * - v3: Entity placement support (separate entities list)
 * - v4: Unified GameObjectData (merged gameObjects + entities, Transform-based position)
 */
@Setter
@Getter
public class SceneData {

    /**
     * Scene format version for migration support.
     */
    private int version = 4;

    /**
     * Scene name (display name, not file name).
     */
    private String name;

    /**
     * All GameObjects in the scene.
     * Includes tilemaps, prefab instances, and scratch entities.
     * Hierarchy is stored via parentId references.
     */
    private List<GameObjectData> gameObjects = new ArrayList<>();

    /**
     * Camera settings.
     */
    private CameraData camera;

    /**
     * Collision map data (Base64 encoded).
     */
    private String collisionData;

    /**
     * Trigger data map (JSON map of "x,y,z" -> TriggerData).
     */
    private java.util.Map<String, Object> triggerData;

    /**
     * Scene-level metadata (can store custom data).
     */
    private java.util.Map<String, Object> metadata;

    // ========================================================================
    // LEGACY SUPPORT (for loading v3 scenes)
    // ========================================================================

    /**
     * Legacy entities list from v3 format.
     * Only populated when loading old scenes, then migrated to gameObjects.
     */
    private List<LegacyEntityData> entities;

    /**
     * Gets legacy entities (for migration only).
     */
    public List<LegacyEntityData> getLegacyEntities() {
        return entities;
    }

    /**
     * Checks if this scene needs migration from v3 format.
     */
    public boolean needsMigration() {
        return version < 4 && entities != null && !entities.isEmpty();
    }

    // ========================================================================
    // LEGACY DATA STRUCTURES (for v3 migration)
    // ========================================================================

    /**
     * Legacy entity data structure from v3 format.
     * Position was stored as separate field, not in Transform.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LegacyEntityData {
        private String id;
        private String name;
        private float[] position;  // Old format: separate field
        private String prefabId;
        private Map<String, Map<String, Object>> componentOverrides;
        private List<Component> components;  // For scratch entities
        private String parentId;
        private int order;

        public boolean isPrefabInstance() {
            return prefabId != null && !prefabId.isEmpty();
        }

        public boolean isScratchEntity() {
            return prefabId == null || prefabId.isEmpty();
        }
    }

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public SceneData() {
    }

    public SceneData(String name) {
        this.name = name;
    }

    // ========================================================================
    // GAMEOBJECT MANAGEMENT
    // ========================================================================

    public void addGameObject(GameObjectData gameObject) {
        this.gameObjects.add(gameObject);
    }

    // ========================================================================
    // V3 -> V4 MIGRATION
    // ========================================================================

    /**
     * Migrates v3 scene data to v4 format.
     * Converts legacy entities (with separate position field) to GameObjectData
     * (with position in Transform component/override).
     */
    public void migrateToV4() {
        if (version >= 4) {
            return;  // Already migrated
        }

        if (entities != null && !entities.isEmpty()) {
            for (LegacyEntityData legacy : entities) {
                GameObjectData migrated = migrateLegacyEntity(legacy);
                gameObjects.add(migrated);
            }
            System.out.println("Migrated " + entities.size() + " entities from v3 to v4 format");
        }

        // Clear legacy data
        entities = null;
        version = 4;
    }

    /**
     * Converts a single legacy entity to GameObjectData.
     */
    private GameObjectData migrateLegacyEntity(LegacyEntityData legacy) {
        float[] pos = legacy.getPosition();
        if (pos == null) {
            pos = new float[]{0, 0, 0};
        }

        GameObjectData data;

        if (legacy.isPrefabInstance()) {
            // Prefab instance: add position to Transform override
            Map<String, Map<String, Object>> overrides = legacy.getComponentOverrides();
            if (overrides == null) {
                overrides = new HashMap<>();
            } else {
                overrides = deepCopyOverrides(overrides);
            }

            // Add Transform override with position
            Map<String, Object> transformOverride = overrides.computeIfAbsent(
                    "com.pocket.rpg.components.Transform", k -> new HashMap<>());
            transformOverride.put("localPosition", pos);

            data = new GameObjectData(
                    legacy.getId(),
                    legacy.getName(),
                    legacy.getPrefabId(),
                    overrides
            );
        } else {
            // Scratch entity: ensure Transform component with position
            List<Component> components = legacy.getComponents();
            if (components == null) {
                components = new ArrayList<>();
            } else {
                components = new ArrayList<>(components);
            }

            // Find or create Transform
            Transform transform = null;
            for (Component comp : components) {
                if (comp instanceof Transform t) {
                    transform = t;
                    break;
                }
            }

            if (transform == null) {
                transform = new Transform();
                components.add(0, transform);
            }

            // Set position on Transform
            transform.setPosition(pos[0],
                    pos.length > 1 ? pos[1] : 0,
                    pos.length > 2 ? pos[2] : 0);

            data = new GameObjectData(
                    legacy.getId(),
                    legacy.getName(),
                    components
            );
        }

        data.setParentId(legacy.getParentId());
        data.setOrder(legacy.getOrder());

        return data;
    }

    private static Map<String, Map<String, Object>> deepCopyOverrides(
            Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
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
        /**
         * Initial camera position [x, y, z].
         */
        private float[] position = {0, 0, 0};

        /**
         * Orthographic size (half-height in world units).
         */
        private float orthographicSize = 15f;

        /**
         * Whether camera should follow a target entity.
         */
        private boolean followPlayer = true;

        /**
         * Name of the entity to follow.
         */
        private String followTargetName = "Player";

        /**
         * Whether to clamp camera to bounds.
         */
        private boolean useBounds = false;

        /**
         * Camera bounds [minX, minY, maxX, maxY].
         */
        private float[] bounds = {0, 0, 0, 0};

        public CameraData() {
        }

        public CameraData(float x, float y, float z, float orthographicSize) {
            this.position = new float[]{x, y, z};
            this.orthographicSize = orthographicSize;
        }

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