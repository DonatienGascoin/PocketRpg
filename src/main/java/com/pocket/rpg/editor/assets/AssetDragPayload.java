package com.pocket.rpg.editor.assets;

/**
 * Payload data for asset drag-drop operations.
 * <p>
 * Carried through ImGui drag-drop system with type identifier "ASSET_DRAG".
 * <p>
 * Usage:
 * <pre>
 * // Source (AssetBrowserPanel)
 * if (ImGui.beginDragDropSource()) {
 *     AssetDragPayload payload = new AssetDragPayload(path, type, asset, -1);
 *     ImGui.setDragDropPayload("ASSET_DRAG", payload.serialize());
 *     ImGui.endDragDropSource();
 * }
 *
 * // Target (SceneViewport/HierarchyPanel)
 * if (ImGui.beginDragDropTarget()) {
 *     byte[] data = ImGui.acceptDragDropPayload("ASSET_DRAG");
 *     if (data != null) {
 *         AssetDragPayload payload = AssetDragPayload.deserialize(data);
 *         // Use payload to create entity
 *     }
 *     ImGui.endDragDropTarget();
 * }
 * </pre>
 *
 * @param path        Relative asset path (e.g., "sprites/player.png")
 * @param type        Asset type class (e.g., Sprite.class)
 * @param asset       Loaded asset reference (may be null for lazy loading)
 * @param spriteIndex For SpriteSheet: selected sprite index; -1 for other types or entire sheet
 */
public record AssetDragPayload(
        String path,
        Class<?> type,
        Object asset,
        int spriteIndex
) {
    /**
     * ImGui drag-drop type identifier.
     */
    public static final String DRAG_TYPE = "ASSET_DRAG";

    /**
     * Creates a payload for a simple asset (non-spritesheet).
     */
    public static AssetDragPayload of(String path, Class<?> type, Object asset) {
        return new AssetDragPayload(path, type, asset, -1);
    }

    /**
     * Creates a payload for a sprite from a sprite sheet.
     */
    public static AssetDragPayload ofSpriteSheet(String path, Object spriteSheet, int spriteIndex) {
        return new AssetDragPayload(path, spriteSheet.getClass(), spriteSheet, spriteIndex);
    }

    /**
     * Checks if this payload is for a specific sprite within a sprite sheet.
     */
    public boolean isSpriteSheetSprite() {
        return spriteIndex >= 0;
    }

    /**
     * Gets the filename from the path (without extension).
     * Example: "sprites/player.png" -> "player"
     */
    public String getEntityName() {
        // Get filename
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        // Remove extension(s)
        int firstDot = filename.indexOf('.');
        String baseName = firstDot >= 0 ? filename.substring(0, firstDot) : filename;

        // Append sprite index if applicable
        if (spriteIndex >= 0) {
            return baseName + "_" + spriteIndex;
        }
        return baseName;
    }

    /**
     * Gets just the filename with extension.
     */
    public String getFilename() {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Serializes payload to bytes for ImGui drag-drop.
     * Uses a simple format: path is sufficient since we can reload the asset.
     */
    public byte[] serialize() {
        // Format: "path|typeClassName|spriteIndex"
        String data = path + "|" + type.getName() + "|" + spriteIndex;
        return data.getBytes();
    }

    /**
     * Deserializes payload from ImGui drag-drop bytes.
     * Note: Asset is not included, caller should load it if needed.
     */
    public static AssetDragPayload deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        String data = new String(bytes);
        String[] parts = data.split("\\|");

        if (parts.length < 3) {
            return null;
        }

        String path = parts[0];
        String typeName = parts[1];
        int spriteIndex = Integer.parseInt(parts[2]);

        Class<?> type;
        try {
            type = Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            type = Object.class;
        }

        // Asset is null - caller should load it if needed
        return new AssetDragPayload(path, type, null, spriteIndex);
    }
}
