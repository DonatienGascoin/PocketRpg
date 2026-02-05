package com.pocket.rpg.editor.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.rendering.TilemapRenderer;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
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
        for (EditorGameObject entity : editorScene.getEntities()) {
            GameObjectData goData = entity.toData();
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
            } else {
                // Regular entity → EditorGameObject
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

        scene.clearDirty();
        return scene;
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