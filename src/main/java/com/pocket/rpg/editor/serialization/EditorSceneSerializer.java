package com.pocket.rpg.editor.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.rendering.TilemapRenderer;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabHierarchyHelper;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;

import java.util.*;

/**
 * Handles conversion between EditorScene (editor runtime) and SceneData (serialization).
 * <p>
 * Architecture:
 * - EditorScene uses TilemapLayer wrappers and EditorGameObject for editor features
 * - SceneData uses unified GameObjectData for all objects
 * - This serializer bridges the two representations
 * <p>
 * Version 4: Unified GameObjectData (no separate entities list)
 */
public class EditorSceneSerializer {

    /**
     * Converts EditorScene to SceneData for saving.
     */
    public static SceneData toSceneData(EditorScene editorScene) {
        SceneData data = new SceneData(editorScene.getName());
        data.setVersion(4);

        // Camera settings
        data.setCamera(editorScene.getCameraSettings().toData());

        // Convert tilemap layers to GameObjectData
        for (TilemapLayer layer : editorScene.getLayers()) {
            GameObjectData goData = convertTilemapLayer(layer);
            data.addGameObject(goData);
        }

        // Collision map
        if (editorScene.getCollisionMap() != null) {
            data.setCollisionData(editorScene.getCollisionMap().toBase64());
        }

        // Trigger data map
        if (editorScene.getTriggerDataMap() != null && !editorScene.getTriggerDataMap().isEmpty()) {
            data.setTriggerData(editorScene.getTriggerDataMap().toSerializableMap());
        }

        // Convert EditorGameObjects to GameObjectData
        // Skip prefab child nodes — they are encoded in the root's childOverrides
        for (EditorGameObject entity : editorScene.getEntities()) {
            if (entity.isPrefabChildNode()) {
                continue;
            }

            GameObjectData goData = entity.toData();

            // For root prefab instances with children, build childOverrides
            if (entity.isPrefabInstance() && entity.hasChildren()) {
                Map<String, GameObjectData.ChildNodeOverrides> childOverridesMap = buildChildOverrides(entity);
                if (!childOverridesMap.isEmpty()) {
                    goData.setChildOverrides(childOverridesMap);
                }
            }

            data.addGameObject(goData);
        }

        return data;
    }

    /**
     * Converts SceneData to EditorScene for editing.
     * Scene name is derived from the file path.
     */
    public static EditorScene fromSceneData(SceneData data, String filePath) {
        EditorScene scene = new EditorScene();
        scene.setFilePath(filePath);  // Name is derived from this

        // Camera settings
        if (data.getCamera() != null) {
            scene.getCameraSettings().fromData(data.getCamera());
        }

        // Process all GameObjects
        for (GameObjectData goData : data.getGameObjects()) {
            if (hasTilemapRenderer(goData)) {
                // Tilemap → TilemapLayer
                TilemapLayer layer = convertToTilemapLayer(goData);
                scene.addExistingLayer(layer);
            } else if (goData.getPrefab() != null && !goData.getPrefab().isEmpty()) {
                // New format: prefab instance with asset path
                try {
                    loadPrefabInstance(goData, scene);
                } catch (Exception e) {
                    System.err.println("Failed to load prefab instance from '" + goData.getPrefab() + "': " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Regular entity or legacy format → EditorGameObject
                try {
                    EditorGameObject entity = EditorGameObject.fromData(goData);
                    scene.addEntity(entity);
                } catch (Exception e) {
                    System.err.println("Failed to load entity: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Collision map
        if (data.getCollisionData() != null) {
            scene.getCollisionMap().fromBase64(data.getCollisionData());
        }

        // Trigger data map
        if (data.getTriggerData() != null) {
            scene.getTriggerDataMap().fromSerializableMap(data.getTriggerData());
        }

        // Rebuild parent-child hierarchy and validate IDs
        validateUniqueIds(scene);
        scene.resolveHierarchy();

        // Reconcile legacy-format prefab instances (old format without 'prefab' path)
        scene.reconcilePrefabInstances();

        scene.clearDirty();
        return scene;
    }

    // ========================================================================
    // PREFAB INSTANCE SERIALIZATION
    // ========================================================================

    /**
     * Builds the childOverrides map from a root prefab instance's descendant children.
     * Only includes children that have overrides, name changes, or enabled changes.
     */
    private static Map<String, GameObjectData.ChildNodeOverrides> buildChildOverrides(EditorGameObject root) {
        Map<String, GameObjectData.ChildNodeOverrides> result = new LinkedHashMap<>();
        collectChildOverrides(root, result);
        return result;
    }

    private static void collectChildOverrides(EditorGameObject parent,
                                               Map<String, GameObjectData.ChildNodeOverrides> result) {
        for (EditorGameObject child : parent.getChildren()) {
            if (!child.isPrefabChildNode()) continue;

            String nodeId = child.getPrefabNodeId();
            GameObjectData.ChildNodeOverrides overrides = new GameObjectData.ChildNodeOverrides();
            boolean hasOverrides = false;

            // Check name override (compare with prefab default name)
            Prefab prefab = child.getPrefab();
            if (prefab != null) {
                GameObjectData prefabNode = prefab.findNode(nodeId);
                if (prefabNode != null) {
                    // Name: only record if different from prefab's node name
                    String prefabName = prefabNode.getName();
                    String childName = child.getName();
                    // Strip auto-generated suffix for comparison
                    if (childName != null && !childName.equals(prefabName)
                            && !childName.startsWith(prefabName + "_")) {
                        overrides.setName(childName);
                        hasOverrides = true;
                    }
                }
            }

            // Active override
            if (!child.isOwnEnabled()) {
                overrides.setActive(false);
                hasOverrides = true;
            }

            // Component overrides
            Map<String, Map<String, Object>> compOverrides = child.getComponentOverrides();
            if (compOverrides != null && !compOverrides.isEmpty()) {
                // Filter to only actually overridden fields
                Map<String, Map<String, Object>> filteredOverrides = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, Object>> entry : compOverrides.entrySet()) {
                    List<String> overriddenFields = child.getOverriddenFields(entry.getKey());
                    if (!overriddenFields.isEmpty()) {
                        Map<String, Object> fieldMap = new LinkedHashMap<>();
                        for (String field : overriddenFields) {
                            fieldMap.put(field, entry.getValue().get(field));
                        }
                        filteredOverrides.put(entry.getKey(), fieldMap);
                    }
                }
                if (!filteredOverrides.isEmpty()) {
                    overrides.setComponentOverrides(filteredOverrides);
                    hasOverrides = true;
                }
            }

            if (hasOverrides) {
                result.put(nodeId, overrides);
            }

            // Recurse into grandchildren
            collectChildOverrides(child, result);
        }
    }

    /**
     * Loads a prefab instance from the new format (asset path + childOverrides).
     * Expands the full hierarchy from the prefab definition and applies overrides.
     */
    private static void loadPrefabInstance(GameObjectData goData, EditorScene scene) {
        String prefabPath = goData.getPrefab();
        JsonPrefab jsonPrefab = null;

        // Try loading from asset path
        try {
            jsonPrefab = Assets.load(prefabPath, JsonPrefab.class);
        } catch (Exception e) {
            // Fall through to registry fallback
        }

        // Fallback: resolve from PrefabRegistry using prefabId
        if (jsonPrefab == null && goData.getPrefabId() != null) {
            Prefab registryPrefab = com.pocket.rpg.prefab.PrefabRegistry.getInstance()
                    .getPrefab(goData.getPrefabId());
            if (registryPrefab instanceof JsonPrefab jp) {
                jsonPrefab = jp;
            }
        }

        if (jsonPrefab == null) {
            System.err.println("Failed to load prefab from path '" + prefabPath +
                    "' and registry fallback for '" + goData.getPrefabId() + "'");
            return;
        }

        // Create root EditorGameObject
        String prefabId = jsonPrefab.getId();
        float[] pos = goData.getPosition();
        org.joml.Vector3f position = new org.joml.Vector3f(
                pos.length > 0 ? pos[0] : 0,
                pos.length > 1 ? pos[1] : 0,
                pos.length > 2 ? pos[2] : 0
        );

        EditorGameObject root = new EditorGameObject(prefabId, position);
        root.setId(goData.getId());
        if (goData.getName() != null) {
            root.setName(goData.getName());
        }
        root.setEnabled(goData.isActive());
        root.setParentId(goData.getParentId());
        root.setOrder(goData.getOrder());

        // Apply root component overrides
        if (goData.getComponentOverrides() != null) {
            for (Map.Entry<String, Map<String, Object>> entry : goData.getComponentOverrides().entrySet()) {
                for (Map.Entry<String, Object> field : entry.getValue().entrySet()) {
                    root.setFieldValue(entry.getKey(), field.getKey(), field.getValue());
                }
            }
        }

        scene.addEntity(root);

        // Expand children from prefab definition
        List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, jsonPrefab);
        for (EditorGameObject child : descendants) {
            scene.addEntity(child);
        }

        // Apply child overrides
        Map<String, GameObjectData.ChildNodeOverrides> childOverrides = goData.getChildOverrides();
        if (childOverrides != null) {
            // Build lookup: prefabNodeId -> EditorGameObject
            Map<String, EditorGameObject> nodeIdMap = new HashMap<>();
            for (EditorGameObject desc : descendants) {
                if (desc.getPrefabNodeId() != null) {
                    nodeIdMap.put(desc.getPrefabNodeId(), desc);
                }
            }

            for (Map.Entry<String, GameObjectData.ChildNodeOverrides> entry : childOverrides.entrySet()) {
                EditorGameObject child = nodeIdMap.get(entry.getKey());
                if (child == null) {
                    System.err.println("childOverrides references unknown node: " + entry.getKey());
                    continue;
                }

                GameObjectData.ChildNodeOverrides overrides = entry.getValue();

                if (overrides.getName() != null) {
                    child.setName(overrides.getName());
                }
                if (overrides.getActive() != null) {
                    child.setEnabled(overrides.getActive());
                }
                if (overrides.getComponentOverrides() != null) {
                    for (Map.Entry<String, Map<String, Object>> compEntry : overrides.getComponentOverrides().entrySet()) {
                        for (Map.Entry<String, Object> field : compEntry.getValue().entrySet()) {
                            child.setFieldValue(compEntry.getKey(), field.getKey(), field.getValue());
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // TILEMAP LAYER CONVERSION
    // ========================================================================

    /**
     * Checks if a GameObjectData has a TilemapRenderer component.
     */
    private static boolean hasTilemapRenderer(GameObjectData goData) {
        return goData.hasComponent(TilemapRenderer.class);
    }

    /**
     * Converts a TilemapLayer to GameObjectData for serialization.
     */
    private static GameObjectData convertTilemapLayer(TilemapLayer layer) {
        List<Component> components = new ArrayList<>();

        // Add Transform at origin
        components.add(new Transform());

        // Convert TilemapRenderer component
        TilemapRenderer tilemap = layer.getTilemap();
        TilemapRenderer componentForSerialization = new TilemapRenderer(tilemap.getTileSize());
        componentForSerialization.setZIndex(tilemap.getZIndex());

        // Copy all tiles
        copyTilemapData(tilemap, componentForSerialization);
        components.add(componentForSerialization);

        // Use the layer's existing ID instead of generating a new one
        GameObjectData goData = new GameObjectData(
                layer.getId(),
                layer.getName(),
                components
        );
        goData.setActive(layer.isVisible());

        return goData;
    }

    /**
     * Converts GameObjectData to TilemapLayer for editing.
     */
    private static TilemapLayer convertToTilemapLayer(GameObjectData goData) {
        TilemapRenderer tilemap = goData.getComponent(TilemapRenderer.class);
        if (tilemap == null) {
            throw new IllegalArgumentException("GameObjectData must have TilemapRenderer component");
        }

        // Create GameObject with the tilemap component
        GameObject gameObject = new GameObject(goData.getName());
        gameObject.setEnabled(goData.isActive());

        // Copy tilemap data to new component instance
        TilemapRenderer newTilemap = new TilemapRenderer(tilemap.getTileSize());
        newTilemap.setZIndex(tilemap.getZIndex());
        copyTilemapData(tilemap, newTilemap);

        gameObject.addComponent(newTilemap);

        // Preserve the ID from the saved data
        TilemapLayer layer = new TilemapLayer(gameObject, goData.getName(), goData.getId());
        layer.setVisible(goData.isActive());
        return layer;
    }

    /**
     * Copies tile data from source to destination tilemap.
     */
    private static void copyTilemapData(TilemapRenderer source, TilemapRenderer dest) {
        for (Long chunkKey : source.chunkKeys()) {
            int cx = TilemapRenderer.chunkKeyToX(chunkKey);
            int cy = TilemapRenderer.chunkKeyToY(chunkKey);
            TilemapRenderer.TileChunk chunk = source.getChunk(cx, cy);

            for (int tx = 0; tx < TilemapRenderer.TileChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < TilemapRenderer.TileChunk.CHUNK_SIZE; ty++) {
                    TilemapRenderer.Tile tile = chunk.get(tx, ty);
                    if (tile != null) {
                        int worldTx = cx * TilemapRenderer.TileChunk.CHUNK_SIZE + tx;
                        int worldTy = cy * TilemapRenderer.TileChunk.CHUNK_SIZE + ty;
                        dest.set(worldTx, worldTy, tile);
                    }
                }
            }
        }
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    private static void validateUniqueIds(EditorScene scene) {
        Set<String> ids = new HashSet<>();
        for (EditorGameObject e : scene.getEntities()) {
            if (!ids.add(e.getId())) {
                System.err.println("DUPLICATE ENTITY ID: " + e.getId() + " - " + e.getName());
                e.regenerateId();
            }
        }
    }
}