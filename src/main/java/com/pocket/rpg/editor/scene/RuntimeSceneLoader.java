package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.LoadOptions;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.serialization.*;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads SceneData into a playable RuntimeScene.
 * <p>
 * Handles:
 * <ul>
 *   <li>All GameObjects (tilemaps, prefab instances, scratch entities)</li>
 *   <li>Collision map loading</li>
 *   <li>Parent-child relationships</li>
 *   <li>Camera data storage (applied by SceneManager after initialize)</li>
 *   <li>Component reference resolution</li>
 * </ul>
 * <p>
 * <b>Important:</b> This loader does NOT call scene.initialize().
 * SceneManager is responsible for initialization and camera configuration.
 */
public class RuntimeSceneLoader {

    /**
     * Loads a RuntimeScene from SceneData.
     * <p>
     * The scene is NOT initialized - SceneManager will call initialize()
     * and then apply camera settings from cameraData.
     *
     * @param data Scene data to load
     * @return Uninitialized RuntimeScene ready for SceneManager
     */
    public RuntimeScene load(SceneData data) {
        if (data == null) {
            throw new IllegalArgumentException("SceneData cannot be null");
        }

        RuntimeScene scene = new RuntimeScene(data.getName());

        // Store camera data for SceneManager to apply after initialize()
        if (data.getCamera() != null) {
            scene.setCameraData(data.getCamera());
        }

        // Load collision map (can be done before initialize)
        if (data.getCollisionData() != null) {
            scene.getCollisionMap().fromBase64(data.getCollisionData());
        }

        // Load trigger data map
        if (data.getTriggerData() != null) {
            scene.getTriggerDataMap().fromSerializableMap(data.getTriggerData());
        }

        // Load all GameObjects with hierarchy support
        // Note: GameObjects are added but not started (no scene.initialize() yet)
        loadGameObjectsWithHierarchy(scene, data.getGameObjects());

        return scene;
    }

    /**
     * Loads a RuntimeScene from a file path.
     * Uses LoadOptions.raw() to bypass asset root prepending.
     *
     * @param scenePath Path to .scene file (e.g., "gameData/scenes/Test.scene")
     * @return Uninitialized RuntimeScene
     */
    public RuntimeScene loadFromPath(String scenePath) {
        SceneData data = Assets.load(scenePath, LoadOptions.raw());
        if (data == null) {
            throw new RuntimeException("Failed to load scene from path: " + scenePath);
        }
        return load(data);
    }

    // ========================================================================
    // GAMEOBJECT LOADING WITH HIERARCHY
    // ========================================================================

    /**
     * Loads all GameObjects respecting parent-child relationships.
     */
    private void loadGameObjectsWithHierarchy(RuntimeScene scene, List<GameObjectData> gameObjects) {
        if (gameObjects == null || gameObjects.isEmpty()) {
            return;
        }

        // Phase 1: Create all GameObjects
        Map<String, GameObject> gameObjectsById = new HashMap<>();
        Map<String, GameObjectData> dataById = new HashMap<>();

        for (GameObjectData goData : gameObjects) {
            try {
                GameObject go = createGameObject(goData);
                if (go != null) {
                    String id = goData.getId();
                    if (id != null) {
                        gameObjectsById.put(id, go);
                        dataById.put(id, goData);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to create GameObject '" + goData.getName() + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Phase 2: Set up parent-child relationships
        for (Map.Entry<String, GameObject> entry : gameObjectsById.entrySet()) {
            String id = entry.getKey();
            GameObject child = entry.getValue();
            GameObjectData goData = dataById.get(id);

            if (goData != null && goData.getParentId() != null) {
                GameObject parent = gameObjectsById.get(goData.getParentId());
                if (parent != null) {
                    parent.addChild(child);
                } else {
                    System.err.println("Parent not found for '" + goData.getName() +
                            "' (parentId: " + goData.getParentId() + ")");
                }
            }
        }

        // Phase 3: Resolve references BEFORE adding to scene
        for (GameObject go : gameObjectsById.values()) {
            ComponentRefResolver.resolveReferences(go);
        }

        // Phase 4: Add root objects to scene (sorted by order)
        List<Map.Entry<String, GameObject>> sortedEntries = new ArrayList<>(gameObjectsById.entrySet());
        sortedEntries.sort((a, b) -> {
            GameObjectData dataA = dataById.get(a.getKey());
            GameObjectData dataB = dataById.get(b.getKey());
            int orderA = dataA != null ? dataA.getOrder() : 0;
            int orderB = dataB != null ? dataB.getOrder() : 0;
            return Integer.compare(orderA, orderB);
        });

        for (Map.Entry<String, GameObject> entry : sortedEntries) {
            String id = entry.getKey();
            GameObject go = entry.getValue();
            GameObjectData goData = dataById.get(id);

            // Only add root objects (no parent or parent not found)
            if (goData != null && goData.getParentId() == null) {
                scene.addGameObject(go);
            } else if (goData != null && !gameObjectsById.containsKey(goData.getParentId())) {
                scene.addGameObject(go);
            }
        }

        System.out.println("Loaded " + scene.getGameObjects().size() + " root GameObjects");
    }

    // ========================================================================
    // GAMEOBJECT CREATION
    // ========================================================================

    /**
     * Creates a GameObject from GameObjectData.
     * Handles tilemaps, prefab instances, and scratch entities.
     */
    private GameObject createGameObject(GameObjectData goData) {
        // Check for tilemap (special handling for binary data)
        if (goData.hasComponent(TilemapRenderer.class)) {
            return createTilemapGameObject(goData);
        }

        // Prefab instance or scratch entity
        if (goData.isPrefabInstance()) {
            return createPrefabInstance(goData);
        } else {
            return createScratchGameObject(goData);
        }
    }

    /**
     * Creates a tilemap GameObject.
     */
    private GameObject createTilemapGameObject(GameObjectData goData) {
        GameObject go = new GameObject(goData.getName());
        go.setEnabled(goData.isActive());

        TilemapRenderer tilemapData = goData.getComponent(TilemapRenderer.class);
        if (tilemapData != null) {
            TilemapRenderer tilemap = new TilemapRenderer(tilemapData.getTileSize());
            tilemap.setZIndex(tilemapData.getZIndex());
            copyTilemapData(tilemapData, tilemap);
            go.addComponent(tilemap);
        }

        return go;
    }

    /**
     * Creates a scratch GameObject (no prefab).
     * Components are CLONED to prevent runtime from corrupting the source SceneData.
     */
    private GameObject createScratchGameObject(GameObjectData goData) {
        String name = goData.getName();
        if (name == null || name.isBlank()) {
            name = "GameObject";
        }

        GameObject gameObject = new GameObject(name);
        gameObject.setEnabled(goData.isActive());

        // Add all components (cloned to protect snapshot)
        List<Component> components = goData.getComponents();
        if (components != null) {
            for (Component comp : components) {
                if (comp == null) continue;

                // UITransform replaces Transform - add it as component (preserves all UI fields)
                if (comp instanceof UITransform) {
                    gameObject.addComponent(comp);  // Will replace auto-created Transform
                }
                // Plain Transform - copy values to existing Transform
                else if (comp instanceof Transform t) {
                    gameObject.getTransform().setPosition(new Vector3f(t.getPosition()));
                    gameObject.getTransform().setRotation(new Vector3f(t.getRotation()));
                    gameObject.getTransform().setScale(new Vector3f(t.getScale()));
                } else {
                    gameObject.addComponent(comp);
                }
            }
        }

        System.out.println("Created scratch entity: " + name + " with " +
                (components != null ? components.size() : 0) + " components");

        return gameObject;
    }

    /**
     * Creates a prefab instance.
     */
    private GameObject createPrefabInstance(GameObjectData goData) {
        String prefabId = goData.getPrefabId();
        Prefab prefab = PrefabRegistry.getInstance().getPrefab(prefabId);

        if (prefab == null) {
            System.err.println("Prefab not found: " + prefabId);
            return null;
        }

        // Get position from Transform overrides
        float[] pos = goData.getPosition();
        Vector3f position = new Vector3f(
                pos != null && pos.length > 0 ? pos[0] : 0,
                pos != null && pos.length > 1 ? pos[1] : 0,
                pos != null && pos.length > 2 ? pos[2] : 0
        );

        // Instantiate with overrides
        GameObject gameObject = prefab.instantiate(position, goData.getComponentOverrides());

        if (gameObject != null) {
            String instanceName = goData.getName();
            if (instanceName != null && !instanceName.isBlank()) {
                gameObject.setName(instanceName);
            }
            gameObject.setEnabled(goData.isActive());
        } else {
            System.err.println("prefab.instantiate() returned null for " + prefabId);
        }

        return gameObject;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Copies tile data from source to destination tilemap.
     */
    private void copyTilemapData(TilemapRenderer source, TilemapRenderer dest) {
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
}
