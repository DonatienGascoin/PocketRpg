package com.pocket.rpg.editor.tileset;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import lombok.Getter;

import java.util.*;

/**
 * Manages available sprite grids (tilesets) for the editor.
 * <p>
 * Scans for {@code .png} files that have {@code spriteMode: MULTIPLE} in their metadata.
 * <p>
 * New tilesets can be created via the CreateSpritesheetDialog.
 *
 * @see SpriteGrid
 * @see SpriteMetadata
 */
public class TilesetRegistry {

    private static TilesetRegistry instance;

    /** Loaded tilesets, keyed by display name */
    private final Map<String, TilesetEntry> tilesets = new LinkedHashMap<>();

    /** Path to display name mapping */
    private final Map<String, String> pathToName = new HashMap<>();

    private TilesetRegistry() {
    }

    public static TilesetRegistry getInstance() {
        if (instance == null) {
            instance = new TilesetRegistry();
        }
        return instance;
    }

    public static void initialize() {
        getInstance();
    }

    public static void destroy() {
        if (instance != null) {
            instance.tilesets.clear();
            instance.pathToName.clear();
            instance = null;
        }
    }

    // ========================================================================
    // SCANNING
    // ========================================================================

    /**
     * Scans for all tilesets (MULTIPLE-mode .png files).
     */
    public void scanAndLoad() {
        tilesets.clear();
        pathToName.clear();

        int count = 0;

        // Load .png files with MULTIPLE mode metadata
        List<String> spritePaths = Assets.scanByType(Sprite.class);
        for (String path : spritePaths) {
            if (loadMultipleModeSprite(path)) {
                count++;
            }
        }

        System.out.println("TilesetRegistry: Loaded " + count + " tilesets");
    }

    /**
     * Checks if a .png file has MULTIPLE mode metadata and loads it as a tileset.
     *
     * @param path Path to the .png file
     * @return true if loaded successfully as a MULTIPLE-mode sprite
     */
    private boolean loadMultipleModeSprite(String path) {
        try {
            // Check if this sprite has MULTIPLE mode metadata
            SpriteMetadata meta = AssetMetadata.load(path, SpriteMetadata.class);
            if (meta == null || !meta.isMultiple()) {
                return false; // Not a tileset, skip
            }

            // Load the parent sprite and get its grid
            Sprite parent = Assets.load(path, Sprite.class);
            if (parent == null) {
                return false;
            }

            SpriteGrid grid = Assets.getSpriteGrid(parent);
            if (grid == null) {
                return false;
            }

            String displayName = extractDisplayName(path);
            displayName = ensureUniqueName(displayName, path);

            TilesetEntry entry = new TilesetEntry(displayName, path, grid);
            tilesets.put(displayName, entry);
            pathToName.put(path, displayName);

            System.out.println("TilesetRegistry: Loaded " + displayName +
                    " (" + entry.getTotalSprites() + " tiles, " +
                    entry.getSpriteWidth() + "x" + entry.getSpriteHeight() + ")");

            return true;

        } catch (Exception e) {
            // Not an error - most .png files won't be MULTIPLE mode
            return false;
        }
    }

    /**
     * Loads a single tileset by path.
     *
     * @param path Path to the tileset file (.png with MULTIPLE metadata)
     */
    public void loadSpritesheet(String path) {
        loadMultipleModeSprite(path);
    }

    /**
     * Ensures a display name is unique by appending hash if needed.
     */
    private String ensureUniqueName(String displayName, String path) {
        if (tilesets.containsKey(displayName)) {
            return displayName + " (" + Math.abs(path.hashCode() % 10000) + ")";
        }
        return displayName;
    }

    /**
     * Extracts a display name from a path.
     * Example: "spritesheets/outdoor.png" → "outdoor"
     */
    private String extractDisplayName(String path) {
        // Get filename
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        // Remove extension
        if (filename.endsWith(".png")) {
            filename = filename.substring(0, filename.length() - ".png".length());
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            int dotIndex = filename.lastIndexOf('.');
            filename = filename.substring(0, dotIndex);
        }

        return filename;
    }

    /**
     * Reloads all tilesets (rescan + reload).
     */
    public void reload() {
        scanAndLoad();
    }

    // ========================================================================
    // ACCESS
    // ========================================================================

    /**
     * Gets all available tileset names (for dropdown).
     */
    public List<String> getTilesetNames() {
        return new ArrayList<>(tilesets.keySet());
    }

    /**
     * Gets the number of available tilesets.
     */
    public int getTilesetCount() {
        return tilesets.size();
    }

    /**
     * Gets a tileset entry by display name.
     */
    public TilesetEntry getTileset(String name) {
        return tilesets.get(name);
    }

    /**
     * Gets a tileset entry by file path.
     */
    public TilesetEntry getTilesetByPath(String path) {
        String name = pathToName.get(path);
        return name != null ? tilesets.get(name) : null;
    }

    /**
     * Gets a sprite from a tileset.
     *
     * @param tilesetName Tileset display name
     * @param spriteIndex Index of sprite in the tileset
     * @return Sprite, or null if not found
     */
    public Sprite getSprite(String tilesetName, int spriteIndex) {
        TilesetEntry entry = tilesets.get(tilesetName);
        if (entry == null) return null;

        if (spriteIndex < 0 || spriteIndex >= entry.getTotalSprites()) return null;

        return entry.getSprite(spriteIndex);
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers a newly created tileset.
     * Call this after creating and saving a new tileset.
     */
    public void registerNew(String path) {
        loadSpritesheet(path);
    }

    // ========================================================================
    // INNER CLASS
    // ========================================================================

    /**
     * Represents a loaded tileset backed by a SpriteGrid.
     */
    @Getter
    public static class TilesetEntry {
        private final String displayName;
        private final String path;
        private final SpriteGrid spriteGrid;

        public TilesetEntry(String displayName, String path, SpriteGrid spriteGrid) {
            this.displayName = displayName;
            this.path = path;
            this.spriteGrid = spriteGrid;
        }

        /**
         * Gets a specific sprite by index.
         */
        public Sprite getSprite(int index) {
            return spriteGrid != null ? spriteGrid.getSprite(index) : null;
        }

        /**
         * Gets all sprites from this tileset.
         */
        public List<Sprite> getSprites() {
            return spriteGrid != null ? spriteGrid.getAllSprites() : List.of();
        }

        /**
         * Gets the total number of sprites.
         */
        public int getTotalSprites() {
            return spriteGrid != null ? spriteGrid.getTotalSprites() : 0;
        }

        /**
         * Gets the texture from the tileset.
         */
        public Texture getTexture() {
            return spriteGrid != null ? spriteGrid.getTexture() : null;
        }

        /**
         * Gets sprite width.
         */
        public int getSpriteWidth() {
            return spriteGrid != null ? spriteGrid.getSpriteWidth() : 0;
        }

        /**
         * Gets sprite height.
         */
        public int getSpriteHeight() {
            return spriteGrid != null ? spriteGrid.getSpriteHeight() : 0;
        }

        /**
         * Gets the number of columns in the grid.
         */
        public int getColumns() {
            return spriteGrid != null ? spriteGrid.getColumns() : 0;
        }

        /**
         * Gets the number of rows in the grid.
         */
        public int getRows() {
            return spriteGrid != null ? spriteGrid.getRows() : 0;
        }
    }
}
