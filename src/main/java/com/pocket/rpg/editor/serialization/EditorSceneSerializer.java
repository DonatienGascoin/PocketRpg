package com.pocket.rpg.editor.serialization;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;

/**
 * Handles conversion between EditorScene (editor runtime) and SceneData (serialization).
 * <p>
 * Architecture:
 * - EditorScene uses TilemapLayer wrappers for editor features
 * - SceneData uses GameObjectData with TilemapRenderer components for runtime
 * - This serializer bridges the two representations
 */
public class EditorSceneSerializer {

    /**
     * Converts EditorScene to SceneData for saving.
     */
    public static SceneData toSceneData(EditorScene editorScene) {
        SceneData data = new SceneData(editorScene.getName());

        // Set default camera (editor camera state not saved yet)
        data.setCamera(new SceneData.CameraData(0, 0, 0, 15f));

        // Convert tilemap layers to GameObjectData
        for (TilemapLayer layer : editorScene.getLayers()) {
            GameObjectData goData = convertTilemapLayer(layer);
            data.addGameObject(goData);
        }

        // TODO: Convert collision map (Phase 4+)
        // if (editorScene.getCollisionMap() != null) {
        //     data.setCollision(convertCollisionMap(editorScene.getCollisionMap()));
        // }

        // TODO: Convert entities (Phase 5)
        // TODO: Convert triggers (Phase 7)

        return data;
    }

    /**
     * Converts SceneData to EditorScene for editing.
     */
    public static EditorScene fromSceneData(SceneData data, String filePath) {
        EditorScene scene = new EditorScene(data.getName());
        scene.setFilePath(filePath);

        // Convert GameObjects with TilemapRenderer to TilemapLayers
        for (GameObjectData goData : data.getGameObjects()) {
            if (hasTilemapRenderer(goData)) {
                TilemapLayer layer = convertToTilemapLayer(goData);
                scene.addExistingLayer(layer);
            }
        }

        // TODO: Load collision map (Phase 4+)
        // if (data.getCollision() != null) {
        //     scene.setCollisionMap(convertToCollisionMap(data.getCollision()));
        // }

        // TODO: Load entities (Phase 5)
        // TODO: Load triggers (Phase 7)

        scene.clearDirty();
        return scene;
    }

    // ========================================================================
    // LAYER -> GAMEOBJECT CONVERSION
    // ========================================================================

    /**
     * Converts a TilemapLayer to GameObjectData for serialization.
     */
    private static GameObjectData convertTilemapLayer(TilemapLayer layer) {
        GameObjectData goData = new GameObjectData();
        goData.setName(layer.getName());
        goData.setActive(layer.isVisible());

        // Convert TilemapRenderer component
        TilemapRenderer tilemap = layer.getTilemap();
        TilemapRenderer componentForSerialization = new TilemapRenderer(tilemap.getTileSize());
        componentForSerialization.setZIndex(tilemap.getZIndex());
        componentForSerialization.setStatic(false); // Editor layers are always non-static

        // Copy all tiles from original tilemap
        for (Long chunkKey : tilemap.chunkKeys()) {
            int cx = TilemapRenderer.chunkKeyToX(chunkKey);
            int cy = TilemapRenderer.chunkKeyToY(chunkKey);
            TilemapRenderer.TileChunk chunk = tilemap.getChunk(cx, cy);

            // Copy each tile
            for (int tx = 0; tx < TilemapRenderer.TileChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < TilemapRenderer.TileChunk.CHUNK_SIZE; ty++) {
                    TilemapRenderer.Tile tile = chunk.get(tx, ty);
                    if (tile != null) {
                        int worldTx = cx * TilemapRenderer.TileChunk.CHUNK_SIZE + tx;
                        int worldTy = cy * TilemapRenderer.TileChunk.CHUNK_SIZE + ty;
                        componentForSerialization.set(worldTx, worldTy, tile);
                    }
                }
            }
        }

        goData.addComponent(componentForSerialization);

        return goData;
    }

    // ========================================================================
    // GAMEOBJECT -> LAYER CONVERSION
    // ========================================================================

    /**
     * Checks if a GameObjectData has a TilemapRenderer component.
     */
    private static boolean hasTilemapRenderer(GameObjectData goData) {
        return goData.getComponent(TilemapRenderer.class) != null;
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
        newTilemap.setStatic(false); // Editor always uses non-static

        // Copy all tiles
        for (Long chunkKey : tilemap.chunkKeys()) {
            int cx = TilemapRenderer.chunkKeyToX(chunkKey);
            int cy = TilemapRenderer.chunkKeyToY(chunkKey);
            TilemapRenderer.TileChunk chunk = tilemap.getChunk(cx, cy);

            for (int tx = 0; tx < TilemapRenderer.TileChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < TilemapRenderer.TileChunk.CHUNK_SIZE; ty++) {
                    TilemapRenderer.Tile tile = chunk.get(tx, ty);
                    if (tile != null) {
                        int worldTx = cx * TilemapRenderer.TileChunk.CHUNK_SIZE + tx;
                        int worldTy = cy * TilemapRenderer.TileChunk.CHUNK_SIZE + ty;
                        newTilemap.set(worldTx, worldTy, tile);
                    }
                }
            }
        }

        gameObject.addComponent(newTilemap);

        // Create TilemapLayer wrapper
        TilemapLayer layer = new TilemapLayer(gameObject, goData.getName());

        // TODO: Restore spritesheet metadata if present
        // This would require reading it from TilemapComponentData metadata

        return layer;
    }
}