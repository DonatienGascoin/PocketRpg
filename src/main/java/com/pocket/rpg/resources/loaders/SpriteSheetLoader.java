package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.ResourceHandle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loader for sprite sheet definitions from JSON files.
 * 
 * JSON Format:
 * <pre>
 * {
 *   "texture": "characters/player.png",
 *   "spriteWidth": 32,
 *   "spriteHeight": 32,
 *   "spacingX": 2,
 *   "spacingY": 2,
 *   "offsetX": 0,
 *   "offsetY": 0,
 *   "frames": {
 *     "idle": 0,
 *     "walk1": 1,
 *     "walk2": 2,
 *     "attack": 3
 *   },
 *   "meta": {
 *     "author": "Artist Name",
 *     "version": "1.0"
 *   }
 * }
 * </pre>
 * 
 * Features:
 * - Named frame access (use "idle" instead of frame 0)
 * - Automatic texture loading through AssetManager
 * - Configurable spacing and offset
 * - Optional metadata storage
 * 
 * Usage:
 * <pre>
 * AssetManager manager = AssetManager.getInstance();
 * manager.registerLoader("spritesheet", new SpriteSheetLoader());
 * 
 * ResourceHandle&lt;SpriteSheetData&gt; sheet = manager.load("player.spritesheet.json");
 * Sprite idle = sheet.get().getSprite("idle");
 * </pre>
 */
public class SpriteSheetLoader implements AssetLoader<SpriteSheetData> {

    private final Gson gson = new Gson();

    /**
     * Loads a sprite sheet from a JSON definition file.
     *
     * @param path Path to the JSON file
     * @return SpriteSheetData with named frame access
     * @throws IOException if loading fails
     */
    @Override
    public SpriteSheetData load(String path) throws IOException {
        // Read JSON file
        String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
        JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

        // Parse texture path (relative to sprite sheet file)
        String texturePath = json.get("texture").getAsString();
        texturePath = resolveRelativePath(path, texturePath);

        // Load texture through AssetManager
        ResourceHandle<Texture> textureHandle = AssetManager.getInstance().load(texturePath, "texture");
        if (!textureHandle.isReady()) {
            throw new IOException("Failed to load texture for sprite sheet: " + texturePath);
        }

        Texture texture = textureHandle.get();
        if (texture == null) {
            throw new IOException("Texture is null for sprite sheet: " + texturePath);
        }

        // Parse sprite dimensions
        int spriteWidth = json.get("spriteWidth").getAsInt();
        int spriteHeight = json.get("spriteHeight").getAsInt();

        // Parse optional spacing and offset
        int spacingX = json.has("spacingX") ? json.get("spacingX").getAsInt() : 0;
        int spacingY = json.has("spacingY") ? json.get("spacingY").getAsInt() : 0;
        int offsetX = json.has("offsetX") ? json.get("offsetX").getAsInt() : 0;
        int offsetY = json.has("offsetY") ? json.get("offsetY").getAsInt() : 0;

        // Create sprite sheet
        SpriteSheet sheet = new SpriteSheet(
                texture,
                spriteWidth, spriteHeight,
                spacingX, spacingY,
                offsetX, offsetY
        );

        // Parse named frames
        Map<String, Integer> namedFrames = new HashMap<>();
        if (json.has("frames")) {
            JsonObject frames = json.getAsJsonObject("frames");
            for (String frameName : frames.keySet()) {
                int frameIndex = frames.get(frameName).getAsInt();
                namedFrames.put(frameName, frameIndex);
            }
        }

        // Parse optional metadata
        Map<String, Object> metadata = new HashMap<>();
        if (json.has("meta")) {
            JsonObject meta = json.getAsJsonObject("meta");
            for (String key : meta.keySet()) {
                metadata.put(key, meta.get(key).getAsString());
            }
        }

        return new SpriteSheetData(sheet, namedFrames, metadata);
    }

    /**
     * Unloads a sprite sheet.
     * Note: Texture is managed separately by AssetManager.
     *
     * @param spriteSheetData The sprite sheet data to unload
     */
    @Override
    public void unload(SpriteSheetData spriteSheetData) {
        // Sprite sheet doesn't own GPU resources
        // Texture is managed separately
        if (spriteSheetData != null) {
            spriteSheetData.getSpriteSheet().clearCache();
        }
    }

    /**
     * Returns supported sprite sheet file extensions.
     *
     * @return Array of supported extensions
     */
    @Override
    public String[] getSupportedExtensions() {
        return new String[]{
                ".spritesheet",
                ".spritesheet.json",
                ".ss.json"
        };
    }

    /**
     * Sprite sheets don't have a meaningful placeholder.
     *
     * @return null
     */
    @Override
    public SpriteSheetData getPlaceholder() {
        return null;
    }

    /**
     * Sprite sheets support hot reloading.
     *
     * @return true
     */
    @Override
    public boolean supportsHotReload() {
        return true;
    }

    /**
     * Reloads a sprite sheet from JSON.
     *
     * @param existing The existing sprite sheet data
     * @param path     Path to reload from
     * @return Updated sprite sheet data
     * @throws IOException if reload fails
     */
    @Override
    public SpriteSheetData reload(SpriteSheetData existing, String path) throws IOException {
        // Unload old sprite sheet
        if (existing != null) {
            unload(existing);
        }

        // Load fresh
        return load(path);
    }

    /**
     * Estimates sprite sheet memory usage.
     * Sprite sheet itself is lightweight - texture is counted separately.
     *
     * @param spriteSheetData The sprite sheet data to measure
     * @return Estimated size in bytes
     */
    @Override
    public long estimateSize(SpriteSheetData spriteSheetData) {
        if (spriteSheetData == null) return 0;

        // Sprite sheet data + cached sprites
        int cachedCount = spriteSheetData.getSpriteSheet().getCachedSpriteCount();
        return 1024 + (cachedCount * 100L); // 1KB base + 100 bytes per cached sprite
    }

    /**
     * Resolves a relative path from a base file path.
     * Example: base="gameData/assets/sheets/player.json", relative="textures/player.png"
     * Result: "gameData/assets/sheets/textures/player.png"
     *
     * @param basePath     Base file path
     * @param relativePath Relative path to resolve
     * @return Resolved absolute path
     */
    private String resolveRelativePath(String basePath, String relativePath) {
        // If relative path is already absolute, return it
        if (relativePath.startsWith("/") || relativePath.contains(":")) {
            return relativePath;
        }

        // Get directory of base path
        int lastSlash = basePath.lastIndexOf('/');
        if (lastSlash == -1) {
            lastSlash = basePath.lastIndexOf('\\');
        }

        if (lastSlash != -1) {
            String baseDir = basePath.substring(0, lastSlash + 1);
            return baseDir + relativePath;
        }

        return relativePath;
    }

    /**
     * Gets the loader type name.
     *
     * @return "spritesheet"
     */
    @Override
    public String getTypeName() {
        return "spritesheet";
    }
}
