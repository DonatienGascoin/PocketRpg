package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for SpriteSheet that provides named access to sprites.
 * <p>
 * Instead of accessing sprites by index, you can use meaningful names:
 * <pre>
 * SpriteSheetData sheet = ...;
 * Sprite idleSprite = sheet.getSprite("idle");
 * Sprite attackSprite = sheet.getSprite("attack");
 * </pre>
 * <p>
 * This class is typically created by SpriteSheetLoader from JSON definitions.
 */
public class SpriteSheetData {

    private final SpriteSheet sheet;
    private final Map<String, Integer> namedFrames;
    private final Map<String, Object> metadata;

    /**
     * Creates sprite sheet data with named frames.
     *
     * @param sheet       The underlying sprite sheet
     * @param namedFrames Map of frame names to frame indices
     */
    public SpriteSheetData(SpriteSheet sheet, Map<String, Integer> namedFrames) {
        this(sheet, namedFrames, new HashMap<>());
    }

    /**
     * Creates sprite sheet data with named frames and metadata.
     *
     * @param sheet       The underlying sprite sheet
     * @param namedFrames Map of frame names to frame indices
     * @param metadata    Additional metadata from JSON
     */
    public SpriteSheetData(SpriteSheet sheet, Map<String, Integer> namedFrames, Map<String, Object> metadata) {
        this.sheet = sheet;
        this.namedFrames = namedFrames != null ? namedFrames : new HashMap<>();
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Gets a sprite by name.
     *
     * @param name Frame name
     * @return Sprite for that frame
     * @throws IllegalArgumentException if name not found
     */
    public Sprite getSprite(String name) {
        Integer frameIndex = namedFrames.get(name);
        if (frameIndex == null) {
            throw new IllegalArgumentException("No frame named '" + name + "' in sprite sheet");
        }
        return sheet.getSprite(frameIndex);
    }

    /**
     * Gets a sprite by name with custom size.
     *
     * @param name   Frame name
     * @param width  Sprite width
     * @param height Sprite height
     * @return Sprite for that frame
     * @throws IllegalArgumentException if name not found
     */
    public Sprite getSprite(String name, float width, float height) {
        Integer frameIndex = namedFrames.get(name);
        if (frameIndex == null) {
            throw new IllegalArgumentException("No frame named '" + name + "' in sprite sheet");
        }
        return sheet.getSprite(frameIndex, width, height);
    }

    /**
     * Gets a sprite by frame index.
     *
     * @param frameIndex Frame index
     * @return Sprite for that frame
     */
    public Sprite getSpriteByIndex(int frameIndex) {
        return sheet.getSprite(frameIndex);
    }

    /**
     * Checks if a frame name exists.
     *
     * @param name Frame name
     * @return true if frame exists
     */
    public boolean hasFrame(String name) {
        return namedFrames.containsKey(name);
    }

    /**
     * Gets all frame names.
     *
     * @return Set of all frame names
     */
    public Set<String> getFrameNames() {
        return namedFrames.keySet();
    }

    /**
     * Gets the frame index for a name.
     *
     * @param name Frame name
     * @return Frame index, or null if not found
     */
    public Integer getFrameIndex(String name) {
        return namedFrames.get(name);
    }

    /**
     * Gets the underlying sprite sheet.
     *
     * @return The sprite sheet
     */
    public SpriteSheet getSpriteSheet() {
        return sheet;
    }

    /**
     * Gets metadata value.
     *
     * @param key Metadata key
     * @return Metadata value, or null
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Gets metadata as string.
     *
     * @param key Metadata key
     * @return Metadata value as string, or null
     */
    public String getMetadataString(String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets metadata as integer.
     *
     * @param key Metadata key
     * @return Metadata value as integer, or 0
     */
    public int getMetadataInt(String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * Gets metadata as float.
     *
     * @param key Metadata key
     * @return Metadata value as float, or 0.0f
     */
    public float getMetadataFloat(String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets metadata as boolean.
     *
     * @param key Metadata key
     * @return Metadata value as boolean, or false
     */
    public boolean getMetadataBoolean(String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    /**
     * Gets the number of named frames.
     *
     * @return Frame count
     */
    public int getNamedFrameCount() {
        return namedFrames.size();
    }

    /**
     * Gets the total number of frames in the sheet.
     *
     * @return Total frame count
     */
    public int getTotalFrames() {
        return sheet.getTotalFrames();
    }

    @Override
    public String toString() {
        return String.format("SpriteSheetData[frames=%d, named=%d, texture=%dx%d]",
                getTotalFrames(), getNamedFrameCount(),
                sheet.getTexture().getWidth(), sheet.getTexture().getHeight());
    }
}
