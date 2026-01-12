package com.pocket.rpg.editor.assets;

import com.pocket.rpg.rendering.resources.Sprite;

/**
 * Payload data for asset drag-drop operations.
 * <p>
 * Carried through ImGui drag-drop system with type identifier "ASSET_DRAG".
 * <p>
 * Path format:
 * <ul>
 *   <li>Direct assets: "sprites/player.png"</li>
 *   <li>Spritesheet sprites: "sheets/player.spritesheet#3"</li>
 * </ul>
 * <p>
 * The path can be used directly with {@code Assets.load(path, type)} to load
 * the asset. Sub-asset references (with #) are handled automatically by AssetManager.
 * <p>
 * Usage:
 * <pre>
 * // Source (AssetBrowserPanel)
 * if (ImGui.beginDragDropSource()) {
 *     AssetDragPayload payload = AssetDragPayload.of(path, type);
 *     ImGui.setDragDropPayload("ASSET_DRAG", payload.serialize());
 *     ImGui.endDragDropSource();
 * }
 *
 * // Target (SceneViewport/HierarchyPanel)
 * if (ImGui.beginDragDropTarget()) {
 *     byte[] data = ImGui.acceptDragDropPayload("ASSET_DRAG");
 *     if (data != null) {
 *         AssetDragPayload payload = AssetDragPayload.deserialize(data);
 *         Object asset = Assets.load(payload.path(), payload.type());
 *         // Asset is now registered in resourcePaths automatically
 *     }
 *     ImGui.endDragDropTarget();
 * }
 * </pre>
 *
 * @param path Full asset path including #index for sub-assets (e.g., "sheets/player.spritesheet#3")
 * @param type Asset type class (e.g., Sprite.class for spritesheet sprites, SpriteSheet.class for whole sheets)
 */
public record AssetDragPayload(
        String path,
        Class<?> type
) {
    /**
     * ImGui drag-drop type identifier.
     */
    public static final String DRAG_TYPE = "ASSET_DRAG";

    /**
     * Creates a payload for an asset.
     *
     * @param path Full path to the asset
     * @param type Asset type class
     */
    public static AssetDragPayload of(String path, Class<?> type) {
        return new AssetDragPayload(path, type);
    }

    /**
     * Creates a payload for a sprite from a sprite sheet.
     * Constructs the full path with #index format.
     *
     * @param sheetPath   Path to the spritesheet file
     * @param spriteIndex Index of the sprite within the sheet
     * @return Payload with path "sheetPath#spriteIndex" and type Sprite.class
     */
    public static AssetDragPayload ofSpriteSheetSprite(String sheetPath, int spriteIndex) {
        String fullPath = sheetPath + "#" + spriteIndex;
        return new AssetDragPayload(fullPath, Sprite.class);
    }

    /**
     * Checks if this payload is for a sub-asset (contains #).
     */
    public boolean isSubAsset() {
        return path != null && path.contains("#");
    }

    /**
     * Gets the base path (without #index) for sub-assets.
     * Returns the full path for non-sub-assets.
     */
    public String getBasePath() {
        if (path == null) return null;
        int hashIndex = path.indexOf('#');
        return hashIndex >= 0 ? path.substring(0, hashIndex) : path;
    }

    /**
     * Gets the sub-asset ID (the part after #).
     * Returns null for non-sub-assets.
     */
    public String getSubAssetId() {
        if (path == null) return null;
        int hashIndex = path.indexOf('#');
        return hashIndex >= 0 ? path.substring(hashIndex + 1) : null;
    }

    /**
     * Gets the filename from the path (without directory, with extension and #index).
     * Example: "sprites/player.spritesheet#3" -> "player.spritesheet#3"
     */
    public String getFilename() {
        if (path == null) return null;
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Gets a suitable entity name from the path.
     * Example: "sprites/player.spritesheet#3" -> "player_3"
     * Example: "sprites/enemy.png" -> "enemy"
     */
    public String getEntityName() {
        if (path == null) return "Entity";

        // Get base filename without path
        String basePath = getBasePath();
        int lastSlash = Math.max(basePath.lastIndexOf('/'), basePath.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? basePath.substring(lastSlash + 1) : basePath;

        // Remove extension(s)
        int firstDot = filename.indexOf('.');
        String baseName = firstDot >= 0 ? filename.substring(0, firstDot) : filename;

        // Append sub-asset ID if present
        String subId = getSubAssetId();
        if (subId != null) {
            return baseName + "_" + subId;
        }
        return baseName;
    }

    /**
     * Serializes payload to bytes for ImGui drag-drop.
     */
    public byte[] serialize() {
        // Format: "path|typeClassName"
        String data = path + "|" + type.getName();
        return data.getBytes();
    }

    /**
     * Deserializes payload from ImGui drag-drop bytes.
     */
    public static AssetDragPayload deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        String data = new String(bytes);
        String[] parts = data.split("\\|");

        if (parts.length < 2) {
            return null;
        }

        String path = parts[0];
        String typeName = parts[1];

        Class<?> type;
        try {
            type = Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            type = Object.class;
        }

        return new AssetDragPayload(path, type);
    }
}
