package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.Assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loader for sprite sheet definitions from JSON files.
 *
 * JSON Format (minimal - only essential fields):
 * <pre>
 * {
 *   "texture": "player.png",
 *   "spriteWidth": 32,
 *   "spriteHeight": 32,
 *   "spacingX": 2,
 *   "spacingY": 2,
 *   "offsetX": 0,
 *   "offsetY": 0
 * }
 * </pre>
 *
 * Frames are automatically named: {filename}_{index}
 * Example: player_0, player_1, player_2, ...
 */
public class SpriteSheetLoader implements AssetLoader<SpriteSheet> {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public SpriteSheet load(String path) throws IOException {
        // Read JSON file
        String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
        JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

        // Parse texture path (relative to sprite sheet file)
        String texturePath = json.get("texture").getAsString();
        texturePath = resolveRelativePath(path, texturePath);

        // Load texture through Assets
        Texture texture = Assets.load(texturePath, Texture.class);
        if (texture == null) {
            throw new IOException("Failed to load texture for sprite sheet: " + texturePath);
        }

        // Parse sprite dimensions
        int spriteWidth = json.get("spriteWidth").getAsInt();
        int spriteHeight = json.get("spriteHeight").getAsInt();

        // Parse optional spacing and offset (default to 0)
        int spacingX = json.has("spacingX") ? json.get("spacingX").getAsInt() : 0;
        int spacingY = json.has("spacingY") ? json.get("spacingY").getAsInt() : 0;
        int offsetX = json.has("offsetX") ? json.get("offsetX").getAsInt() : 0;
        int offsetY = json.has("offsetY") ? json.get("offsetY").getAsInt() : 0;

        // Create sprite sheet (calculates columns/rows/totalFrames internally)
        return new SpriteSheet(
                texture,
                spriteWidth, spriteHeight,
                spacingX, spacingY,
                offsetX, offsetY
        );
    }

    @Override
    public void save(SpriteSheet spriteSheet, String path) throws IOException {
        // Create minimal JSON with only essential fields
        JsonObject json = new JsonObject();

        // Get texture path (relative if possible)
        String texturePath = getRelativeTexturePath(spriteSheet);
        json.addProperty("texture", texturePath);

        // Add dimensions
        json.addProperty("spriteWidth", spriteSheet.getSpriteWidth());
        json.addProperty("spriteHeight", spriteSheet.getSpriteHeight());

        // Add spacing and offset (only if non-zero)
        if (spriteSheet.getSpacingX() != 0) {
            json.addProperty("spacingX", spriteSheet.getSpacingX());
        }
        if (spriteSheet.getSpacingY() != 0) {
            json.addProperty("spacingY", spriteSheet.getSpacingY());
        }
        if (spriteSheet.getOffsetX() != 0) {
            json.addProperty("offsetX", spriteSheet.getOffsetX());
        }
        if (spriteSheet.getOffsetY() != 0) {
            json.addProperty("offsetY", spriteSheet.getOffsetY());
        }

        // Write to file
        String jsonString = gson.toJson(json);
        Files.write(Paths.get(path), jsonString.getBytes());
    }

    @Override
    public SpriteSheet getPlaceholder() {
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".spritesheet", ".spritesheet.json", ".ss.json"};
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public SpriteSheet reload(SpriteSheet existing, String path) throws IOException {
        // Clear cached sprites
        if (existing != null) {
            existing.clearCache();
        }
        // Load fresh
        return load(path);
    }

    /**
     * Resolves a relative path from a base file path.
     * Example: base="assets/sheets/player.spritesheet", relative="textures/player.png"
     * Result: "assets/sheets/textures/player.png"
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
     * Gets the texture path, attempting to make it relative to asset root.
     * This makes the JSON portable - you can move the asset root and it still works.
     */
    private String getRelativeTexturePath(SpriteSheet spriteSheet) {
        Texture texture = spriteSheet.getTexture();
        if (texture == null) {
            return "texture.png";
        }

        String fullPath = texture.getFilePath();
        if (fullPath == null) {
            return "texture.png";
        }

        // Try to strip common prefixes to make path relative
        // Example: "gameData/assets/sprites/player.png" → "sprites/player.png"

        // Common asset root patterns to strip
        String[] possibleRoots = {
                "gameData/assets/",
                "assets/",
                "./gameData/assets/",
                "./assets/"
        };

        for (String root : possibleRoots) {
            if (fullPath.startsWith(root)) {
                return fullPath.substring(root.length());
            }
        }

        // If no common root found, return the full path
        // This is safe - the loader will handle both relative and absolute paths
        return fullPath;
    }
}