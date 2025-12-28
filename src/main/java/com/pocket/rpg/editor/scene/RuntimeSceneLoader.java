package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.editor.serialization.EntityData;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.serialization.ComponentData;
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
 * - Tilemap layer conversion
 * - Collision map loading
 * - Entity instantiation (both prefab instances and scratch entities)
 * - Parent-child relationships for UI hierarchy
 * - Camera configuration
 * - Component reference resolution
 */
public class RuntimeSceneLoader {

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    public RuntimeSceneLoader(ViewportConfig viewportConfig, RenderingConfig renderingConfig) {
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Loads a RuntimeScene from SceneData (for initial scene from editor).
     *
     * @param data The scene data to load
     * @return Initialized RuntimeScene ready for play
     */
    public RuntimeScene load(SceneData data) {
        if (data == null) {
            throw new IllegalArgumentException("SceneData cannot be null");
        }

        // Create and initialize scene
        RuntimeScene scene = new RuntimeScene(data.getName());
        scene.initialize(viewportConfig, renderingConfig);

        // Load collision map
        if (data.getCollision() != null) {
            scene.getCollisionMap().fromSparseFormat(data.getCollision());
        }

        // Create GameObjects from tilemap layers
        for (GameObjectData goData : data.getGameObjects()) {
            try {
                GameObject go = createGameObject(goData);
                scene.addGameObject(go);
            } catch (Exception e) {
                System.err.println("Failed to create GameObject '" + goData.getName() + "': " + e.getMessage());
            }
        }

        // Instantiate entities with proper parent-child handling
        if (data.getEntities() != null) {
            instantiateEntitiesWithHierarchy(scene, data.getEntities());
        }

        // Configure camera from scene settings
        if (data.getCamera() != null) {
            configureCamera(scene, data.getCamera());
        }

        // Resolve component references after all GameObjects are established
        for (GameObject go : scene.getGameObjects()) {
            ComponentRefResolver.resolveReferences(go);
        }

        System.out.println("Loaded runtime scene: " + data.getName() +
                " (objects=" + scene.getGameObjects().size() + ")");

        return scene;
    }

    /**
     * Instantiates all entities respecting parent-child relationships.
     * Creates all GameObjects first, then sets up hierarchy, then adds to scene.
     */
    private void instantiateEntitiesWithHierarchy(RuntimeScene scene, List<EntityData> entities) {
        // Phase 1: Create all GameObjects (without adding to scene)
        Map<String, GameObject> gameObjectsById = new HashMap<>();
        Map<String, EntityData> entityDataById = new HashMap<>();

        for (EntityData entityData : entities) {
            try {
                GameObject go = createEntityGameObject(entityData);
                if (go != null) {
                    String id = entityData.getId();
                    if (id != null) {
                        gameObjectsById.put(id, go);
                        entityDataById.put(id, entityData);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to create entity '" + entityData.getName() + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Phase 2: Set up parent-child relationships
        for (Map.Entry<String, GameObject> entry : gameObjectsById.entrySet()) {
            String id = entry.getKey();
            GameObject child = entry.getValue();
            EntityData entityData = entityDataById.get(id);

            if (entityData != null && entityData.getParentId() != null) {
                String parentId = entityData.getParentId();
                GameObject parent = gameObjectsById.get(parentId);

                if (parent != null) {
                    parent.addChild(child);
                } else {
                    System.err.println("Parent not found for entity '" + entityData.getName() +
                            "' (parentId: " + parentId + ")");
                }
            }
        }

        // Phase 3: Add only root entities to scene (children are added via parent)
        // Sort by order if available
        List<Map.Entry<String, GameObject>> sortedEntries = new ArrayList<>(gameObjectsById.entrySet());
        sortedEntries.sort((a, b) -> {
            EntityData dataA = entityDataById.get(a.getKey());
            EntityData dataB = entityDataById.get(b.getKey());
            int orderA = dataA != null ? dataA.getOrder() : 0;
            int orderB = dataB != null ? dataB.getOrder() : 0;
            return Integer.compare(orderA, orderB);
        });

        for (Map.Entry<String, GameObject> entry : sortedEntries) {
            String id = entry.getKey();
            GameObject go = entry.getValue();
            EntityData entityData = entityDataById.get(id);

            // Only add root entities (no parent or parent not found in this batch)
            if (entityData != null && entityData.getParentId() == null) {
                try {
                    scene.addGameObject(go);
                } catch (Exception e) {
                    System.err.println("Failed to add entity '" + entityData.getName() +
                            "' to scene: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (entityData != null && !gameObjectsById.containsKey(entityData.getParentId())) {
                // Parent wasn't in this batch, add as root
                try {
                    scene.addGameObject(go);
                } catch (Exception e) {
                    System.err.println("Failed to add orphan entity '" + entityData.getName() +
                            "' to scene: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Creates a GameObject from entity data without adding to scene.
     */
    private GameObject createEntityGameObject(EntityData entityData) {
        // Get position
        float[] pos = entityData.getPosition();
        Vector3f position = new Vector3f(
                pos != null && pos.length >= 1 ? pos[0] : 0,
                pos != null && pos.length >= 2 ? pos[1] : 0,
                pos != null && pos.length >= 3 ? pos[2] : 0
        );

        if (entityData.isScratchEntity()) {
            return createScratchEntity(entityData, position);
        } else {
            return createPrefabInstance(entityData, position);
        }
    }

    /**
     * Loads a RuntimeScene from a file path (for scene transitions).
     *
     * @param scenePath Path to the .scene file
     * @return Initialized RuntimeScene
     */
    public RuntimeScene loadFromPath(String scenePath) {
        SceneData data = Assets.load(scenePath);
        if (data == null) {
            throw new RuntimeException("Failed to load scene from path: " + scenePath);
        }
        return load(data);
    }

    /**
     * Creates a GameObject from serialized data.
     */
    private GameObject createGameObject(GameObjectData goData) {
        GameObject go = new GameObject(goData.getName());
        go.setEnabled(goData.isActive());

        // Handle TilemapRenderer component
        TilemapRenderer tilemapData = goData.getComponent(TilemapRenderer.class);
        if (tilemapData != null) {
            TilemapRenderer tilemap = new TilemapRenderer(tilemapData.getTileSize());
            tilemap.setZIndex(tilemapData.getZIndex());
            tilemap.setStatic(true); // Runtime tilemaps are static for batching

            // Copy all tiles
            copyTilemapData(tilemapData, tilemap);

            go.addComponent(tilemap);
        }

        return go;
    }

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
     * Creates a GameObject from scratch entity data (no prefab).
     */
    private GameObject createScratchEntity(EntityData entityData, Vector3f position) {
        String name = entityData.getName();
        if (name == null || name.isBlank()) {
            name = "ScratchEntity";
        }

        GameObject entity = new GameObject(name);
        entity.getTransform().setPosition(position);

        // Add components from serialized data
        List<ComponentData> components = entityData.getComponents();
        if (components != null) {
            for (ComponentData compData : components) {
                try {
                    Component component = compData.toComponent();
                    if (component != null) {
                        entity.addComponent(component);
                    } else {
                        System.err.println("Failed to create component: " + compData.getType());
                    }
                } catch (Exception e) {
                    System.err.println("Error creating component " + compData.getType() + ": " + e.getMessage());
                }
            }
        }

        System.out.println("Created scratch entity: " + name + " with " +
                (components != null ? components.size() : 0) + " components");

        return entity;
    }

    /**
     * Creates a GameObject from prefab instance data.
     */
    private GameObject createPrefabInstance(EntityData entityData, Vector3f position) {
        String prefabId = entityData.getPrefabId();
        Prefab prefab = PrefabRegistry.getInstance().getPrefab(prefabId);

        if (prefab == null) {
            System.err.println("Prefab not found: " + prefabId);
            return null;
        }

        // Instantiate prefab with properties as overrides
        GameObject entity = prefab.instantiate(position, entityData.getComponentOverrides());

        if (entity != null) {
            // Apply instance name if set
            String instanceName = entityData.getName();
            if (instanceName != null && !instanceName.isBlank()) {
                entity.setName(instanceName);
            }
        }

        return entity;
    }

    /**
     * Configures the scene camera from serialized data.
     */
    private void configureCamera(RuntimeScene scene, SceneData.CameraData cameraData) {
        if (scene.getCamera() == null) {
            return;
        }

        // Set position
        float[] pos = cameraData.getPosition();
        if (pos != null && pos.length >= 2) {
            scene.getCamera().setPosition(pos[0], pos[1]);
        }

        // Set orthographic size
        scene.getCamera().setOrthographicSize(cameraData.getOrthographicSize());
    }
}