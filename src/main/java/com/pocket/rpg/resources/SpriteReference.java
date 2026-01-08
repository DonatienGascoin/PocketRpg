package com.pocket.rpg.resources;

import com.pocket.rpg.rendering.Sprite;

/**
 * Utility class for sprite path serialization and deserialization.
 * <p>
 * Centralizes the logic for converting between Sprite objects and path strings,
 * including support for spritesheet#index format.
 * <p>
 * Used by:
 * <ul>
 *   <li>{@link com.pocket.rpg.serialization.custom.SpriteTypeAdapter}</li>
 *   <li>{@link com.pocket.rpg.serialization.custom.ComponentTypeAdapterFactory} (for TilemapRenderer)</li>
 *   <li>{@link com.pocket.rpg.serialization.ComponentData}</li>
 * </ul>
 */
public final class SpriteReference {

    private SpriteReference() {
        // Utility class
    }

    /**
     * Converts a Sprite to its path reference string.
     * <p>
     * Returns the full path including #index for spritesheet sprites.
     * Returns null if the sprite is not tracked by Assets.
     *
     * @param sprite The sprite to convert
     * @return Path string (e.g., "sprites/player.png" or "sheets/chars.spritesheet#3"), or null
     */
    public static String toPath(Sprite sprite) {
        if (sprite == null) {
            return null;
        }
        return Assets.getPathForResource(sprite);
    }

    /**
     * Loads a Sprite from a path reference string.
     * <p>
     * Handles both direct paths and spritesheet#index format.
     * The loaded sprite is automatically registered in the Assets resourcePaths map.
     *
     * @param path Path string (e.g., "sprites/player.png" or "sheets/chars.spritesheet#3")
     * @return Loaded Sprite, or null if path is null/empty
     */
    public static Sprite fromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return Assets.load(path, Sprite.class);
    }

    /**
     * Checks if a path represents a sub-asset (spritesheet sprite).
     *
     * @param path The path to check
     * @return true if path contains '#' separator
     */
    public static boolean isSubAssetPath(String path) {
        return path != null && path.indexOf(AssetManager.SUB_ASSET_SEPARATOR) >= 0;
    }

    /**
     * Extracts the base path (without #index) from a full path.
     *
     * @param path Full path (may include #index)
     * @return Base path without sub-asset identifier
     */
    public static String getBasePath(String path) {
        if (path == null) return null;
        int hashIndex = path.indexOf(AssetManager.SUB_ASSET_SEPARATOR);
        return hashIndex >= 0 ? path.substring(0, hashIndex) : path;
    }

    /**
     * Extracts the sub-asset ID from a full path.
     *
     * @param path Full path (may include #index)
     * @return Sub-asset ID (e.g., "3"), or null if not a sub-asset path
     */
    public static String getSubAssetId(String path) {
        if (path == null) return null;
        int hashIndex = path.indexOf(AssetManager.SUB_ASSET_SEPARATOR);
        return hashIndex >= 0 ? path.substring(hashIndex + 1) : null;
    }

    /**
     * Builds a full path for a spritesheet sprite.
     *
     * @param sheetPath   Path to the spritesheet
     * @param spriteIndex Index of the sprite
     * @return Full path with #index (e.g., "sheets/chars.spritesheet#3")
     */
    public static String buildPath(String sheetPath, int spriteIndex) {
        return sheetPath + AssetManager.SUB_ASSET_SEPARATOR + spriteIndex;
    }
}
