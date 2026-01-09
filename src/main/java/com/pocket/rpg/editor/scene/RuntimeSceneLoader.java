package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.serialization.ComponentRefResolver;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads SceneData into a playable RuntimeScene.
 * <p>
 * Handles:
 * - All GameObjects (tilemaps, prefab instances, scratch entities)
 * - Collision map loading
 * - Parent-child relationships
 * - Camera configuration
 * - Component reference resolution
 * <p>
 * Uses unified GameObjectData for all entity types.
 */
public class RuntimeSceneLoader {

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    public RuntimeSceneLoader(ViewportConfig viewportConfig, RenderingConfig renderingConfig) {
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Loads a RuntimeScene from SceneData.
     */
    public RuntimeScene load(SceneData data) {
        if (data == null) {
            throw new IllegalArgumentException("SceneData cannot be null");
        }

        RuntimeScene scene = new RuntimeScene(data.getName());
        scene.initialize(viewportConfig, renderingConfig);

        // Load collision map
        if (data.getCollisionData() != null) {
            scene.getCollisionMap().fromBase64(data.getCollisionData());
        }

        // Load all GameObjects with hierarchy support
        loadGameObjectsWithHierarchy(scene, data.getGameObjects());

        // Configure camera
        if (data.getCamera() != null) {
            configureCamera(scene, data.getCamera());
        }

        System.out.println("Loaded runtime scene: " + data.getName() +
                " (objects=" + scene.getGameObjects().size() + ")");

        return scene;
    }

    /**
     * Loads a RuntimeScene from a file path.
     */
    public RuntimeScene loadFromPath(String scenePath) {
        SceneData data = Assets.load(scenePath);
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

        System.out.println("DEBUG: Scene object count: " + scene.getGameObjects().size());
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
     */
    private GameObject createScratchGameObject(GameObjectData goData) {
        String name = goData.getName();
        if (name == null || name.isBlank()) {
            name = "GameObject";
        }

        GameObject gameObject = new GameObject(name);
        gameObject.setEnabled(goData.isActive());

        // Add all components
        List<Component> components = goData.getComponents();
        if (components != null) {
            for (Component comp : components) {
                if (comp == null) continue;

                // Transform is handled specially - copy values to existing Transform
                if (comp instanceof Transform t) {
                    gameObject.getTransform().setPosition(t.getPosition());
                    gameObject.getTransform().setRotation(t.getRotation());
                    gameObject.getTransform().setScale(t.getScale());
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

        System.out.println("DEBUG: Instantiating prefab '" + prefabId + "' at " + position);

        // Instantiate with overrides
        GameObject gameObject = prefab.instantiate(position, goData.getComponentOverrides());

        if (gameObject != null) {
            String instanceName = goData.getName();
            if (instanceName != null && !instanceName.isBlank()) {
                gameObject.setName(instanceName);
            }
            gameObject.setEnabled(goData.isActive());

            System.out.println("DEBUG: Created prefab instance: " + gameObject.getName() +
                    " components=" + gameObject.getAllComponents().size());

            // Debug sprite renderer
            var sr = gameObject.getComponent(SpriteRenderer.class);
            if (sr != null) {
                System.out.println("DEBUG: SpriteRenderer - sprite=" + sr.getSprite() +
                        " zIndex=" + sr.getZIndex() +
                        " visible=" + sr.isRenderVisible());
            }
        } else {
            System.err.println("DEBUG: prefab.instantiate() returned null for " + prefabId);
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

    /**
     * Configures the scene camera from serialized data.
     */
    private void configureCamera(RuntimeScene scene, SceneData.CameraData cameraData) {
        if (scene.getCamera() == null) {
            return;
        }

        float[] pos = cameraData.getPosition();
        if (pos != null && pos.length >= 2) {
            scene.getCamera().setPosition(pos[0], pos[1]);
        }

        scene.getCamera().setOrthographicSize(cameraData.getOrthographicSize());
    }
}