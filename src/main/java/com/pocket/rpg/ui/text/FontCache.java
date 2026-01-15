package com.pocket.rpg.ui.text;

import com.pocket.rpg.resources.Assets;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches Font instances by path and size.
 * Allows multiple UIText components to share the same Font instance
 * when they use the same font file at the same size.
 */
public class FontCache {

    private static final Map<String, Font> cache = new HashMap<>();

    /**
     * Gets or creates a Font for the given path and size.
     *
     * @param path Font asset path (e.g., "fonts/zelda.ttf")
     * @param size Font size in pixels
     * @return Cached or newly created Font instance
     */
    public static Font get(String path, int size) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String key = path + "@" + size;
        return cache.computeIfAbsent(key, k -> {
            // Resolve the full file path using asset root
            String fullPath = resolveAssetPath(path);
            return new Font(fullPath, size);
        });
    }

    /**
     * Resolves an asset path to a full file path.
     *
     * @param assetPath Relative asset path (e.g., "fonts/zelda.ttf")
     * @return Full file path
     */
    private static String resolveAssetPath(String assetPath) {
        try {
            String assetRoot = Assets.getContext().getAssetRoot();
            return assetRoot + assetPath;
        } catch (IllegalStateException e) {
            // Assets not initialized, use path as-is (for tests or standalone use)
            return assetPath;
        }
    }

    /**
     * Checks if a font is cached.
     *
     * @param path Font file path
     * @param size Font size
     * @return true if cached
     */
    public static boolean isCached(String path, int size) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return cache.containsKey(path + "@" + size);
    }

    /**
     * Clears all cached fonts and releases their resources.
     * Call this when shutting down or when fonts need to be reloaded.
     */
    public static void clear() {
        cache.values().forEach(Font::destroy);
        cache.clear();
    }

    /**
     * Gets the number of cached fonts.
     *
     * @return Cache size
     */
    public static int size() {
        return cache.size();
    }
}
