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
 * - A SpriteSheet providing the available tiles
 * - Editor-specific metadata (name, visibility, etc.)
 *
 * The layer stores tile indices (into the spritesheet) rather than
 * direct Sprite references, enabling serialization.
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

    /** The spritesheet used for this layer's tiles */
    @Setter
    private SpriteSheet spriteSheet;

    /** Path to the spritesheet image (for serialization) */
    @Setter
    private String spriteSheetPath;

    /** Sprite width in pixels (for SpriteSheet reconstruction) */
    @Setter
    private int spriteWidth = 16;

    /** Sprite height in pixels (for SpriteSheet reconstruction) */
    @Setter
    private int spriteHeight = 16;

    /** Cached sprites from the spritesheet */
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

        // IMPORTANT: Set to non-static for editor so tiles render immediately
        // Static batching caches tiles and won't show new ones until rebatch
        this.tilemap.setStatic(false);

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

        // Set non-static for editor
        this.tilemap.setStatic(false);
    }

    // ========================================================================
    // SPRITESHEET MANAGEMENT
    // ========================================================================

    /**
     * Sets the spritesheet and caches its sprites.
     */
    public void setSpriteSheet(SpriteSheet sheet, String path, int spriteW, int spriteH) {
        this.spriteSheet = sheet;
        this.spriteSheetPath = path;
        this.spriteWidth = spriteW;
        this.spriteHeight = spriteH;
        this.sprites = sheet.generateAllSprites();
    }

    /**
     * Gets a sprite by index from the spritesheet.
     *
     * @param index Tile index
     * @return Sprite at that index, or null if invalid
     */
    public Sprite getSprite(int index) {
        if (sprites == null || index < 0 || index >= sprites.size()) {
            return null;
        }
        return sprites.get(index);
    }

    /**
     * Gets the number of available tiles in the spritesheet.
     */
    public int getTileCount() {
        return sprites != null ? sprites.size() : 0;
    }

    // ========================================================================
    // TILE OPERATIONS
    // ========================================================================

    /**
     * Sets a tile at the given position.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param tileIndex Index into the spritesheet (-1 to clear)
     */
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
     * Gets the tile index at the given position.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @return Tile index, or -1 if empty
     */
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

        return -1; // Sprite not found in our sheet
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
        return String.format("TilemapLayer[name=%s, zIndex=%d, visible=%b, locked=%b, tiles=%d]",
                name, getZIndex(), visible, locked,
                spriteSheet != null ? spriteSheet.getTotalFrames() : 0);
    }
}