package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.EditorCapability;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loader for sprite assets supporting both single and multiple (spritesheet) modes.
 * <p>
 * Creates sprites from textures, with behavior determined by the sprite's metadata:
 * <ul>
 *   <li><b>Single mode</b>: Returns a single sprite from the full texture</li>
 *   <li><b>Multiple mode</b>: Returns a sprite from the full texture, but also supports
 *       sub-asset access (e.g., "player.png#3") to get individual grid sprites</li>
 * </ul>
 * <p>
 * For multiple mode sprites, grid sprites are lazily generated and cached internally.
 * Use {@link #getSubAsset(Sprite, String, Class)} to access individual sprites by index.
 * <p>
 * Note: Path tracking is handled centrally by {@link com.pocket.rpg.resources.AssetManager#getPathForResource(Object)}.
 * Sprites loaded through this loader are automatically registered in the resourcePaths map.
 *
 * @see SpriteMetadata
 * @see SpriteMetadata.SpriteMode
 */
public class SpriteLoader implements AssetLoader<Sprite> {

    /**
     * Cache for SpriteGrid instances per parent sprite.
     * Used for multiple mode sprites to generate and cache grid sprites.
     */
    private final Map<Sprite, SpriteGrid> gridCache = new ConcurrentHashMap<>();

    /**
     * Cache for metadata per parent sprite.
     * Used to answer queries about the sprite mode and settings.
     */
    private final Map<Sprite, SpriteMetadata> metadataCache = new ConcurrentHashMap<>();

    @Override
    public Sprite load(String path) throws IOException {
        try {
            // Load texture directly (path is already fully resolved by AssetManager)
            Texture texture = new Texture(path);

            // Create sprite from texture
            // Path tracking is handled by AssetManager.resourcePaths
            Sprite sprite = new Sprite(texture, path);

            // Apply metadata if it exists (pivot, ppu override, 9-slice)
            String relativePath = Assets.getRelativePath(path);
            if (relativePath != null) {
                SpriteMetadata meta = AssetMetadata.load(relativePath, SpriteMetadata.class);
                if (meta != null) {
                    applyMetadata(sprite, texture, meta);
                    metadataCache.put(sprite, meta);

                    // For MULTIPLE mode, prepare grid sprite generation (lazy)
                    // Grid sprites are generated on first sub-asset access
                }
            }

            return sprite;
        } catch (RuntimeException e) {
            throw new IOException("Failed to load texture for sprite: " + path, e);
        }
    }

    /**
     * Applies metadata to a sprite based on its mode.
     *
     * @param sprite  The sprite to configure
     * @param texture The source texture (needed for multiple mode grid calculations)
     * @param meta    The metadata to apply
     */
    private void applyMetadata(Sprite sprite, Texture texture, SpriteMetadata meta) {
        // Apply PPU override (applies to both modes)
        if (meta.pixelsPerUnitOverride != null) {
            sprite.setPixelsPerUnitOverride(meta.pixelsPerUnitOverride);
        }

        if (meta.isSingle()) {
            // Single mode: apply direct pivot and 9-slice to the main sprite
            if (meta.hasPivot()) {
                sprite.setPivot(meta.pivotX, meta.pivotY);
            }
            if (meta.hasNineSlice()) {
                sprite.setNineSliceData(meta.nineSlice.copy());
            }
        } else {
            // Multiple mode: the parent sprite represents the full texture
            // Individual grid sprites get their own pivot/9-slice when accessed via getSubAsset
            // Apply default pivot to parent (for preview purposes)
            if (meta.defaultPivot != null) {
                sprite.setPivot(meta.defaultPivot.x, meta.defaultPivot.y);
            }
        }
    }

    /**
     * Gets or creates a SpriteGrid for a multiple mode sprite.
     *
     * @param parent The parent sprite
     * @param meta   The sprite metadata
     * @return The SpriteGrid for this sprite
     */
    private SpriteGrid getOrCreateGrid(Sprite parent, SpriteMetadata meta) {
        return gridCache.computeIfAbsent(parent, p -> {
            String basePath = Assets.getPathForResource(p);
            return new SpriteGrid(p.getTexture(), meta, basePath);
        });
    }

    // ========================================================================
    // SUB-ASSET SUPPORT (MULTIPLE MODE)
    // ========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <S> S getSubAsset(Sprite parent, String subId, Class<S> subType) {
        // Accept Sprite.class or Object.class (wildcard for type inference)
        if (subType != Sprite.class && subType != Object.class) {
            throw new IllegalArgumentException(
                    "Sprite only provides Sprite sub-assets, not " + subType.getSimpleName()
            );
        }

        // Check if this sprite is in multiple mode
        SpriteMetadata meta = metadataCache.get(parent);
        if (meta == null || meta.isSingle()) {
            throw new IllegalArgumentException(
                    "Cannot get sub-asset: sprite is not in MULTIPLE mode"
            );
        }

        if (meta.grid == null) {
            throw new IllegalArgumentException(
                    "Cannot get sub-asset: sprite has no grid settings"
            );
        }

        // Parse the index
        int index;
        try {
            index = Integer.parseInt(subId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid sprite index: " + subId);
        }

        // Get or create SpriteGrid (lazy initialization)
        SpriteGrid grid = getOrCreateGrid(parent, meta);

        // Validate index and return sprite
        if (index < 0 || index >= grid.getTotalSprites()) {
            throw new IllegalArgumentException(
                    "Sprite index " + index + " out of range [0, " + grid.getTotalSprites() + ")"
            );
        }

        return (S) grid.getSprite(index);
    }

    // ========================================================================
    // MULTIPLE MODE HELPERS
    // ========================================================================

    /**
     * Checks if a sprite is in multiple (spritesheet) mode.
     *
     * @param sprite The sprite to check
     * @return true if the sprite is in multiple mode
     */
    public boolean isMultipleMode(Sprite sprite) {
        SpriteMetadata meta = metadataCache.get(sprite);
        return meta != null && meta.isMultiple();
    }

    /**
     * Gets the total number of sprites in a multiple mode sprite.
     *
     * @param sprite The parent sprite
     * @return Number of grid sprites, or 1 if single mode or not loaded through this loader
     */
    public int getSpriteCount(Sprite sprite) {
        SpriteMetadata meta = metadataCache.get(sprite);
        if (meta == null || meta.isSingle() || meta.grid == null) {
            return 1;
        }

        Texture texture = sprite.getTexture();
        return meta.grid.calculateTotalSprites(texture.getWidth(), texture.getHeight());
    }

    /**
     * Gets the grid settings for a multiple mode sprite.
     *
     * @param sprite The parent sprite
     * @return Grid settings, or null if single mode
     */
    public GridSettings getGridSettings(Sprite sprite) {
        SpriteMetadata meta = metadataCache.get(sprite);
        return (meta != null && meta.isMultiple()) ? meta.grid : null;
    }

    /**
     * Gets a grid sprite by index. Convenience method that wraps getSubAsset.
     *
     * @param parent The parent sprite (must be in multiple mode)
     * @param index  The sprite index
     * @return The grid sprite at that index
     */
    public Sprite getGridSprite(Sprite parent, int index) {
        return getSubAsset(parent, String.valueOf(index), Sprite.class);
    }

    /**
     * Gets all grid sprites for a multiple mode sprite.
     * Generates them if not already cached.
     *
     * @param parent The parent sprite
     * @return List of all grid sprites, or single-element list containing parent if single mode
     */
    public List<Sprite> getAllGridSprites(Sprite parent) {
        SpriteMetadata meta = metadataCache.get(parent);
        if (meta == null || meta.isSingle()) {
            return List.of(parent);
        }

        SpriteGrid grid = getOrCreateGrid(parent, meta);
        return grid.getAllSprites();
    }

    /**
     * Gets the SpriteGrid for a multiple mode sprite.
     *
     * @param parent The parent sprite
     * @return The SpriteGrid, or null if single mode
     */
    public SpriteGrid getSpriteGrid(Sprite parent) {
        SpriteMetadata meta = metadataCache.get(parent);
        if (meta == null || meta.isSingle()) {
            return null;
        }
        return getOrCreateGrid(parent, meta);
    }

    /**
     * Clears the grid cache for a specific sprite.
     * Use when metadata changes and grid needs regeneration.
     *
     * @param parent The parent sprite
     */
    public void clearGridCache(Sprite parent) {
        SpriteGrid grid = gridCache.remove(parent);
        if (grid != null) {
            grid.clearCache();
        }
    }

    /**
     * Clears all caches. Use during hot-reload.
     */
    public void clearAllCaches() {
        for (SpriteGrid grid : gridCache.values()) {
            grid.clearCache();
        }
        gridCache.clear();
        metadataCache.clear();
    }

    @Override
    public void save(Sprite sprite, String path) throws IOException {
        throw new UnsupportedOperationException("Sprite saving not supported");
    }

    @Override
    public Sprite getPlaceholder() {
        // Could create a sprite from texture placeholder
        // For now, return null
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        // Same as TextureLoader - sprites are created from images
        return new String[]{".png", ".jpg", ".jpeg", ".bmp", ".tga"};
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public Sprite reload(Sprite existing, String path) throws IOException {
        if (existing == null) {
            return load(path);
        }

        try {
            // 1. Reload the underlying texture in place
            Texture texture = existing.getTexture();
            if (texture != null) {
                texture.reloadFromDisk(path);
            }

            // 2. Reload metadata and apply to existing sprite
            String relativePath = Assets.getRelativePath(path);
            SpriteMetadata meta = null;
            if (relativePath != null) {
                meta = AssetMetadata.load(relativePath, SpriteMetadata.class);
            }
            existing.reloadMetadata(meta);

            // 3. Clear grid cache (grid sprites share parent's texture, so they're updated too)
            SpriteGrid grid = gridCache.remove(existing);
            if (grid != null) {
                grid.clearCache();
            }

            // 4. Update metadata cache
            if (meta != null) {
                metadataCache.put(existing, meta);
            } else {
                metadataCache.remove(existing);
            }

            return existing; // Same reference
        } catch (RuntimeException e) {
            throw new IOException("Failed to reload sprite: " + path, e);
        }
    }

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    @Override
    public EditorGameObject instantiate(Sprite asset, String assetPath, Vector3f position) {
        return instantiateWithIndex(asset, assetPath, position, 0);
    }

    /**
     * Creates an EditorEntity from a specific sprite in a multiple mode sprite.
     * For single mode sprites, the index is ignored.
     *
     * @param asset       The sprite (can be single or multiple mode)
     * @param assetPath   Path to the sprite file
     * @param position    World position
     * @param spriteIndex Index of the sprite within the grid (for multiple mode)
     * @return New EditorEntity with SpriteRenderer configured for the specific sprite
     */
    public EditorGameObject instantiateWithIndex(Sprite asset, String assetPath, Vector3f position, int spriteIndex) {
        // Get the sprite to use
        Sprite spriteToUse = asset;
        String entityName = extractEntityName(assetPath);

        SpriteMetadata meta = metadataCache.get(asset);
        if (meta != null && meta.isMultiple()) {
            // For multiple mode, use the grid sprite at the specified index
            List<Sprite> gridSprites = getAllGridSprites(asset);
            if (spriteIndex >= 0 && spriteIndex < gridSprites.size()) {
                spriteToUse = gridSprites.get(spriteIndex);
                entityName = entityName + "_" + spriteIndex;
            } else if (!gridSprites.isEmpty()) {
                spriteToUse = gridSprites.get(0);
                entityName = entityName + "_0";
            }
        }

        // Create scratch entity
        EditorGameObject entity = new EditorGameObject(entityName, position, false);

        // Add SpriteRenderer component with actual Sprite object
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(spriteToUse);
        spriteRenderer.setZIndex(0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(Sprite asset) {
        // For multiple mode, return the first grid sprite as preview
        SpriteMetadata meta = metadataCache.get(asset);
        if (meta != null && meta.isMultiple()) {
            List<Sprite> gridSprites = getAllGridSprites(asset);
            if (!gridSprites.isEmpty()) {
                return gridSprites.get(0);
            }
        }
        return asset; // Sprite is its own preview
    }

    /**
     * Gets a specific sprite from a multiple mode sprite for preview.
     *
     * @param asset       The sprite (can be single or multiple mode)
     * @param spriteIndex Index of the sprite (ignored for single mode)
     * @return The sprite at that index, or the asset itself for single mode
     */
    public Sprite getPreviewSprite(Sprite asset, int spriteIndex) {
        SpriteMetadata meta = metadataCache.get(asset);
        if (meta != null && meta.isMultiple()) {
            List<Sprite> gridSprites = getAllGridSprites(asset);
            if (spriteIndex >= 0 && spriteIndex < gridSprites.size()) {
                return gridSprites.get(spriteIndex);
            }
        }
        return asset;
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.Image;
    }

    @Override
    public Set<EditorCapability> getEditorCapabilities() {
        return Set.of(EditorCapability.PIVOT_EDITING);
    }

    @Override
    public EditorPanelType getEditorPanelType() {
        return EditorPanelType.ASSET_EDITOR;
    }

    /**
     * Extracts entity name from asset path.
     * Example: "sprites/player.png" -> "player"
     */
    private String extractEntityName(String assetPath) {
        // Get filename
        int lastSlash = Math.max(assetPath.lastIndexOf('/'), assetPath.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? assetPath.substring(lastSlash + 1) : assetPath;

        // Remove extension
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(0, lastDot) : filename;
    }
}
