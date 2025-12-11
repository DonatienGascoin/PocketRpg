package com.pocket.rpg.editor.tileset;

import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;

import java.util.*;

/**
 * Manages available spritesheets for the editor.
 *
 * Uses Assets.scanByType(SpriteSheet.class) to find all .spritesheet files,
 * then loads them via Assets.load().
 *
 * Spritesheets are JSON files defining:
 * - texture path
 * - sprite dimensions
 * - spacing/offset
 *
 * New spritesheets can be created via the CreateSpritesheetDialog.
 */
public class TilesetRegistry {

    private static TilesetRegistry instance;

    /** Loaded spritesheets, keyed by display name */
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
     * Scans for all .spritesheet files and loads them.
     */
    public void scanAndLoad() {
        tilesets.clear();
        pathToName.clear();

        // Get all spritesheet paths
        List<String> paths = Assets.scanByType(SpriteSheet.class);

        System.out.println("TilesetRegistry: Found " + paths.size() + " spritesheet files");

        for (String path : paths) {
            loadSpritesheet(path);
        }

        System.out.println("TilesetRegistry: Loaded " + tilesets.size() + " spritesheets");
    }

    /**
     * Loads a single spritesheet by path.
     */
    public void loadSpritesheet(String path) {
        try {
            SpriteSheet sheet = Assets.load(path, SpriteSheet.class);
            if (sheet == null) {
                System.err.println("TilesetRegistry: Failed to load " + path);
                return;
            }

            // Extract display name from path
            String displayName = extractDisplayName(path);

            // Handle duplicates
            if (tilesets.containsKey(displayName)) {
                displayName = displayName + " (" + path.hashCode() + ")";
            }

            TilesetEntry entry = new TilesetEntry(displayName, path, sheet);
            tilesets.put(displayName, entry);
            pathToName.put(path, displayName);

            System.out.println("TilesetRegistry: Loaded " + displayName +
                    " (" + sheet.getTotalFrames() + " tiles, " +
                    sheet.getSpriteWidth() + "x" + sheet.getSpriteHeight() + ")");

        } catch (Exception e) {
            System.err.println("TilesetRegistry: Error loading " + path + ": " + e.getMessage());
        }
    }

    /**
     * Extracts a display name from a path.
     * Example: "gameData/assets/spritesheets/Road_16x16.spritesheet" → "Road_16x16"
     */
    private String extractDisplayName(String path) {
        // Get filename
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        // Remove extensions
        if (filename.endsWith(".spritesheet.json")) {
            filename = filename.substring(0, filename.length() - ".spritesheet.json".length());
        } else if (filename.endsWith(".spritesheet")) {
            filename = filename.substring(0, filename.length() - ".spritesheet".length());
        } else if (filename.endsWith(".ss.json")) {
            filename = filename.substring(0, filename.length() - ".ss.json".length());
        }

        return filename;
    }

    /**
     * Reloads all spritesheets (rescan + reload).
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
     * @param spriteIndex Index of sprite in the sheet
     * @return Sprite, or null if not found
     */
    public Sprite getSprite(String tilesetName, int spriteIndex) {
        TilesetEntry entry = tilesets.get(tilesetName);
        if (entry == null) return null;

        SpriteSheet sheet = entry.getSpriteSheet();
        if (spriteIndex < 0 || spriteIndex >= sheet.getTotalFrames()) return null;

        return sheet.getSprite(spriteIndex);
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers a newly created spritesheet.
     * Call this after creating and saving a new .spritesheet file.
     */
    public void registerNew(String path) {
        loadSpritesheet(path);
    }

    // ========================================================================
    // INNER CLASS
    // ========================================================================

    /**
     * Represents a loaded tileset/spritesheet.
     */
    @Getter
    public static class TilesetEntry {
        private final String displayName;
        private final String path;
        private final SpriteSheet spriteSheet;
        private List<Sprite> sprites;

        public TilesetEntry(String displayName, String path, SpriteSheet spriteSheet) {
            this.displayName = displayName;
            this.path = path;
            this.spriteSheet = spriteSheet;
        }

        /**
         * Gets all sprites from this tileset (cached).
         */
        public List<Sprite> getSprites() {
            if (sprites == null) {
                sprites = spriteSheet.generateAllSprites();
            }
            return sprites;
        }

        /**
         * Gets the texture from the spritesheet.
         */
        public com.pocket.rpg.rendering.Texture getTexture() {
            return spriteSheet.getTexture();
        }

        /**
         * Gets sprite width.
         */
        public int getSpriteWidth() {
            return spriteSheet.getSpriteWidth();
        }

        /**
         * Gets sprite height.
         */
        public int getSpriteHeight() {
            return spriteSheet.getSpriteHeight();
        }
    }
}