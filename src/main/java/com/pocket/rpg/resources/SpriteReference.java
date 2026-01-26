package com.pocket.rpg.resources;

import com.pocket.rpg.rendering.resources.Sprite;

/**
 * Utility class for sprite path serialization and deserialization.
 * <p>
 * Centralizes the logic for converting between Sprite objects and path strings,
 * including support for both old and new path formats:
 * <ul>
 *   <li>Old format: {@code "sheets/chars.spritesheet#3"}</li>
 *   <li>New format: {@code "sheets/chars.png#3"} (unified model)</li>
 * </ul>
 * <p>
 * During migration, this class transparently handles the old .spritesheet format
 * by converting it to the new .png format when loading.
 * <p>
 * Used by:
 * <ul>
 *   <li>{@link com.pocket.rpg.serialization.custom.SpriteTypeAdapter}</li>
 *   <li>{@link com.pocket.rpg.serialization.custom.ComponentTypeAdapterFactory} (for TilemapRenderer)</li>
 * </ul>
 */
public final class SpriteReference {

    /** Old spritesheet file extension (being phased out) */
    private static final String OLD_SPRITESHEET_EXT = ".spritesheet";

    private SpriteReference() {
        // Utility class
    }

    /**
     * Converts a Sprite to its path reference string.
     * <p>
     * Returns the full path including #index for spritesheet sprites.
     * Returns null if the sprite is not tracked by Assets.
     * <p>
     * Note: This always returns the new format (e.g., "player.png#3"),
     * never the old .spritesheet format.
     *
     * @param sprite The sprite to convert
     * @return Path string (e.g., "sprites/player.png" or "sheets/chars.png#3"), or null
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
     * Handles multiple formats for backwards compatibility:
     * <ul>
     *   <li>Direct paths: "sprites/player.png"</li>
     *   <li>New sub-asset format: "sheets/chars.png#3"</li>
     *   <li>Old spritesheet format: "sheets/chars.spritesheet#3" (auto-converted to .png)</li>
     * </ul>
     * <p>
     * Old .spritesheet paths are automatically converted to .png format before loading.
     * This enables seamless migration from the old format.
     * <p>
     * The loaded sprite is automatically registered in the Assets resourcePaths map.
     *
     * @param path Path string (any of the supported formats)
     * @return Loaded Sprite, or null if path is null/empty
     */
    public static Sprite fromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // Let AssetManager handle path migration - it has the full mapping table
        // Don't pre-convert here, as the mapping depends on knowing original spritesheet names
        return Assets.load(path, Sprite.class);
    }

    /**
     * Converts an old .spritesheet path to the new .png format.
     * <p>
     * Examples:
     * <ul>
     *   <li>"player.spritesheet#3" → "player.png#3"</li>
     *   <li>"sheets/outdoor.spritesheet" → "sheets/outdoor.png"</li>
     *   <li>"sprites/icon.png" → "sprites/icon.png" (unchanged)</li>
     * </ul>
     *
     * @param path The path to potentially convert
     * @return The converted path (or original if no conversion needed)
     */
    public static String migratePathIfNeeded(String path) {
        if (path == null) {
            return null;
        }

        // Check if this is an old .spritesheet path
        String basePath = getBasePath(path);
        if (basePath != null && basePath.toLowerCase().endsWith(OLD_SPRITESHEET_EXT)) {
            // Convert .spritesheet to .png
            String newBasePath = basePath.substring(0, basePath.length() - OLD_SPRITESHEET_EXT.length()) + ".png";

            // Preserve the sub-asset index if present
            String subId = getSubAssetId(path);
            if (subId != null) {
                return newBasePath + AssetManager.SUB_ASSET_SEPARATOR + subId;
            }
            return newBasePath;
        }

        return path;
    }

    /**
     * Checks if a path uses the old .spritesheet format.
     *
     * @param path The path to check
     * @return true if this is an old-format path that needs migration
     */
    public static boolean isOldFormat(String path) {
        if (path == null) {
            return false;
        }
        String basePath = getBasePath(path);
        return basePath != null && basePath.toLowerCase().endsWith(OLD_SPRITESHEET_EXT);
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
