package com.pocket.rpg.editor.assets;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache for asset preview thumbnails.
 * <p>
 * Stores OpenGL texture IDs for ImGui.image() calls.
 * Lazy loads thumbnails on first access.
 */
public class ThumbnailCache {

    private final Map<String, Integer> textureIds = new HashMap<>();
    private final Map<String, float[]> uvCoords = new HashMap<>(); // [u0, v0, u1, v1]

    /**
     * Gets the cached texture ID for an asset, or caches it if not present.
     *
     * @param path   Asset path (cache key)
     * @param sprite Preview sprite (may be null)
     * @return OpenGL texture ID, or 0 if no preview available
     */
    public int getTextureId(String path, Sprite sprite) {
        if (sprite == null || sprite.getTexture() == null) {
            return 0;
        }

        Integer cached = textureIds.get(path);
        if (cached != null) {
            return cached;
        }

        // Cache the texture ID and UV coords
        Texture texture = sprite.getTexture();
        int texId = texture.getTextureId();
        textureIds.put(path, texId);

        // Store UV coordinates for sprite sheets
        uvCoords.put(path, new float[]{
                sprite.getU0(),
                sprite.getV0(),
                sprite.getU1(),
                sprite.getV1()
        });

        return texId;
    }

    /**
     * Gets the cached texture ID for a spritesheet sprite.
     *
     * @param sheetPath   Spritesheet path
     * @param spriteIndex Sprite index within the sheet
     * @param sprite      The sprite at that index
     * @return OpenGL texture ID, or 0 if no preview available
     */
    public int getTextureId(String sheetPath, int spriteIndex, Sprite sprite) {
        String key = sheetPath + "#" + spriteIndex;
        return getTextureId(key, sprite);
    }

    /**
     * Gets UV coordinates for a cached sprite.
     *
     * @param path Asset path (same as used in getTextureId)
     * @return [u0, v0, u1, v1] or default [0,0,1,1] if not cached
     */
    public float[] getUVCoords(String path) {
        float[] coords = uvCoords.get(path);
        return coords != null ? coords : new float[]{0f, 0f, 1f, 1f};
    }

    /**
     * Gets UV coordinates for a spritesheet sprite.
     */
    public float[] getUVCoords(String sheetPath, int spriteIndex) {
        String key = sheetPath + "#" + spriteIndex;
        return getUVCoords(key);
    }

    /**
     * Checks if a path is cached.
     */
    public boolean isCached(String path) {
        return textureIds.containsKey(path);
    }

    /**
     * Invalidates a cached thumbnail.
     */
    public void invalidate(String path) {
        textureIds.remove(path);
        uvCoords.remove(path);
    }

    /**
     * Clears all cached thumbnails.
     */
    public void clear() {
        textureIds.clear();
        uvCoords.clear();
    }

    /**
     * Gets the number of cached thumbnails.
     */
    public int size() {
        return textureIds.size();
    }
}
