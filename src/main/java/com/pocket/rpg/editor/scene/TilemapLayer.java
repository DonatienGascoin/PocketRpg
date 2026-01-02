package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Wraps a tilemap layer for editor use.
 *
 * Each layer consists of:
 * - A GameObject with a TilemapRenderer component
 * - Editor-specific metadata (name, visibility, etc.)
 *
 * Tiles are stored directly in the TilemapRenderer with their Sprite references.
 * The layer no longer requires a "default" spritesheet - tiles from any tileset
 * can be placed on any layer.
 *
 * For serialization, tiles need to store (tilesetName, spriteIndex) pairs,
 * which will be implemented when save/load is added.
 */
@Getter
public class TilemapLayer {

    /** The underlying GameObject */
    private final GameObject gameObject;

    /** The TilemapRenderer component */
    private final TilemapRenderer tilemap;

    /** Display name in the editor */
    @Setter
    private String name;

    /** Whether this layer is visible in the editor */
    @Setter
    private boolean visible = true;

    /** Whether this layer is locked (cannot be edited) */
    @Setter
    private boolean locked = false;

    // Legacy spritesheet support (for layers created with Add Layer dialog)
    @Setter
    private SpriteSheet spriteSheet;
    @Setter
    private String spriteSheetPath;
    @Setter
    private int spriteWidth = 16;
    @Setter
    private int spriteHeight = 16;
    private List<Sprite> sprites;

    /**
     * Creates a new tilemap layer.
     *
     * @param name Display name
     * @param zIndex Render order (lower = behind)
     */
    public TilemapLayer(String name, int zIndex) {
        this.name = name;
        this.gameObject = new GameObject(name);
        this.tilemap = new TilemapRenderer(1.0f); // 1 world unit per tile
        this.tilemap.setZIndex(zIndex);

        this.gameObject.addComponent(tilemap);
    }

    /**
     * Creates a layer from an existing GameObject with TilemapRenderer.
     *
     * @param gameObject Existing GameObject
     * @param name Display name
     */
    public TilemapLayer(GameObject gameObject, String name) {
        this.gameObject = gameObject;
        this.tilemap = gameObject.getComponent(TilemapRenderer.class);
        if (this.tilemap == null) {
            throw new IllegalArgumentException("GameObject must have a TilemapRenderer component");
        }
        this.name = name;
    }

    // ========================================================================
    // SPRITESHEET MANAGEMENT (Legacy - for Add Layer dialog)
    // ========================================================================

    /**
     * Sets a default spritesheet for this layer.
     * This is used by the Add Layer dialog for convenience, but tiles
     * can be placed from any tileset regardless of this setting.
     */
    public void setSpriteSheet(SpriteSheet sheet, String path, int spriteW, int spriteH) {
        this.spriteSheet = sheet;
        this.spriteSheetPath = path;
        this.spriteWidth = spriteW;
        this.spriteHeight = spriteH;
        this.sprites = sheet != null ? sheet.generateAllSprites() : null;
    }

    /**
     * Gets a sprite by index from the layer's default spritesheet.
     *
     * @param index Tile index
     * @return Sprite at that index, or null if invalid or no spritesheet
     */
    public Sprite getSprite(int index) {
        if (sprites == null || index < 0 || index >= sprites.size()) {
            return null;
        }
        return sprites.get(index);
    }

    /**
     * Gets the number of available tiles in the layer's default spritesheet.
     */
    public int getTileCount() {
        return sprites != null ? sprites.size() : 0;
    }

    // ========================================================================
    // TILE OPERATIONS
    // ========================================================================

    /**
     * Sets a tile at the given position using the layer's default spritesheet.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param tileIndex Index into the default spritesheet (-1 to clear)
     * @deprecated Use setTile(x, y, Sprite) or place tiles via TileBrushTool
     */
    @Deprecated
    public void setTile(int tileX, int tileY, int tileIndex) {
        if (tileIndex < 0) {
            tilemap.clear(tileX, tileY);
        } else {
            Sprite sprite = getSprite(tileIndex);
            if (sprite != null) {
                tilemap.set(tileX, tileY, new TilemapRenderer.Tile(sprite));
            }
        }
    }

    /**
     * Sets a tile at the given position with a specific sprite.
     * This is the preferred method - allows tiles from any tileset.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param sprite Sprite to place (null to clear)
     */
    public void setTile(int tileX, int tileY, Sprite sprite) {
        if (sprite == null) {
            tilemap.clear(tileX, tileY);
        } else {
            tilemap.set(tileX, tileY, new TilemapRenderer.Tile(sprite));
        }
    }

    /**
     * Gets the tile at the given position.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @return The tile, or null if empty
     */
    public TilemapRenderer.Tile getTile(int tileX, int tileY) {
        return tilemap.get(tileX, tileY);
    }

    /**
     * Gets the tile index at the given position (legacy method).
     * Only works if the tile's sprite is from this layer's default spritesheet.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @return Tile index, or -1 if empty or from different tileset
     * @deprecated Tiles may come from any tileset now
     */
    @Deprecated
    public int getTileIndex(int tileX, int tileY) {
        TilemapRenderer.Tile tile = tilemap.get(tileX, tileY);
        if (tile == null || tile.sprite() == null) {
            return -1;
        }

        // Find sprite index in our list
        if (sprites != null) {
            for (int i = 0; i < sprites.size(); i++) {
                if (sprites.get(i) == tile.sprite()) {
                    return i;
                }
            }
        }

        return -1; // Sprite not found in our sheet (from different tileset)
    }

    /**
     * Clears a tile at the given position.
     */
    public void clearTile(int tileX, int tileY) {
        tilemap.clear(tileX, tileY);
    }

    // ========================================================================
    // VISIBILITY
    // ========================================================================

    /**
     * Sets visibility and updates the GameObject accordingly.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        gameObject.setEnabled(visible);
    }

    // ========================================================================
    // Z-INDEX
    // ========================================================================

    /**
     * Gets the render order (zIndex).
     */
    public int getZIndex() {
        return tilemap.getZIndex();
    }

    /**
     * Sets the render order.
     */
    public void setZIndex(int zIndex) {
        tilemap.setZIndex(zIndex);
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    @Override
    public String toString() {
        return String.format("TilemapLayer[name=%s, zIndex=%d, visible=%b, locked=%b]",
                name, getZIndex(), visible, locked);
    }
}