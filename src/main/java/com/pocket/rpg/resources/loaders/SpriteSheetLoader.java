package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.Assets;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loader for sprite sheet definitions from JSON files.
 * <p>
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
 * <p>
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

        // Parse texture path (relative to asset root, NOT to sprite sheet file)
        String texturePath = json.get("texture").getAsString();
        // Assets.load() will resolve this relative to the asset root

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
        Path filePath = Paths.get(path);

        // Create parent directories if they don't exist
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.write(filePath, jsonString.getBytes());
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
     * Gets the texture path, attempting to make it relative to asset root.
     * This makes the JSON portable - you can move the asset root and it still works.
     */
    private String getRelativeTexturePath(SpriteSheet spriteSheet) {
        Texture texture = spriteSheet.getTexture();
        if (texture == null) {
            return "texture.png";
        }

        return Assets.getRelativePath(texture.getFilePath());
    }

    // ========================================================================
    // SUB-ASSET SUPPORT
    // ========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <S> S getSubAsset(SpriteSheet parent, String subId, Class<S> subType) {
        // Accept Sprite.class or Object.class (wildcard for type inference)
        if (subType != Sprite.class && subType != Object.class) {
            throw new IllegalArgumentException(
                    "SpriteSheet only provides Sprite sub-assets, not " + subType.getSimpleName()
            );
        }

        int index;
        try {
            index = Integer.parseInt(subId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid sprite index: " + subId);
        }

        if (index < 0 || index >= parent.getTotalFrames()) {
            throw new IllegalArgumentException(
                    "Sprite index " + index + " out of range [0, " + parent.getTotalFrames() + ")"
            );
        }

        return (S) parent.getSprite(index);
    }

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    /**
     * Creates an EditorEntity from a sprite sheet.
     * Note: This method creates an entity using the first sprite (index 0).
     * For specific sprite selection, use {@link #instantiateWithIndex(SpriteSheet, String, Vector3f, int)}.
     */
    @Override
    public EditorGameObject instantiate(SpriteSheet asset, String assetPath, Vector3f position) {
        return instantiateWithIndex(asset, assetPath, position, 0);
    }

    /**
     * Creates an EditorEntity from a specific sprite in the sheet.
     *
     * @param asset       The sprite sheet
     * @param assetPath   Path to the sprite sheet file
     * @param position    World position
     * @param spriteIndex Index of the sprite within the sheet
     * @return New EditorEntity with SpriteRenderer configured for the specific sprite
     */
    public EditorGameObject instantiateWithIndex(SpriteSheet asset, String assetPath, Vector3f position, int spriteIndex) {
        // Validate sprite index
        if (asset != null && (spriteIndex < 0 || spriteIndex >= asset.getTotalFrames())) {
            spriteIndex = 0;
        }

        // Extract entity name from filename + sprite index
        String baseName = extractEntityName(assetPath);
        String entityName = baseName + "_" + spriteIndex;

        // Get the actual Sprite from the SpriteSheet
        Sprite sprite = asset.getSprite(spriteIndex);

        // Create scratch entity
        EditorGameObject entity = new EditorGameObject(entityName, position, false);

        // Add SpriteRenderer component with the actual Sprite
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(sprite);
        spriteRenderer.setZIndex(0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(SpriteSheet asset) {
        // Return first sprite as preview
        if (asset != null && asset.getTotalFrames() > 0) {
            return asset.getSprite(0);
        }
        return null;
    }

    /**
     * Gets a specific sprite from the sheet for preview.
     *
     * @param asset       The sprite sheet
     * @param spriteIndex Index of the sprite
     * @return The sprite at that index, or null
     */
    public Sprite getPreviewSprite(SpriteSheet asset, int spriteIndex) {
        if (asset != null && spriteIndex >= 0 && spriteIndex < asset.getTotalFrames()) {
            return asset.getSprite(spriteIndex);
        }
        return null;
    }

    @Override
    public String getIconCodepoint() {
        return FontAwesomeIcons.ThLarge;
    }

    /**
     * Extracts entity name from asset path.
     * Example: "sprites/player.spritesheet" -> "player"
     */
    private String extractEntityName(String assetPath) {
        // Get filename
        int lastSlash = Math.max(assetPath.lastIndexOf('/'), assetPath.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? assetPath.substring(lastSlash + 1) : assetPath;

        // Remove all extensions (handle .spritesheet.json)
        int firstDot = filename.indexOf('.');
        return firstDot >= 0 ? filename.substring(0, firstDot) : filename;
    }
}