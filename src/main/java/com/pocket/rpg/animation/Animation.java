package com.pocket.rpg.animation;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.SpriteReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Animation asset containing a sequence of frames.
 * Frames are stored as paths for serialization, sprites resolved lazily at runtime.
 */
public class Animation {

    private String name;
    private boolean looping;
    private List<AnimationFrame> frames = new ArrayList<>();

    // Runtime cache - not serialized
    private transient List<Sprite> cachedSprites;

    public Animation() {
        this.looping = true;
    }

    public Animation(String name) {
        this();
        this.name = name;
    }

    // ========================================================================
    // FRAME ACCESS
    // ========================================================================

    public int getFrameCount() {
        return frames.size();
    }

    public AnimationFrame getFrame(int index) {
        return frames.get(index);
    }

    public List<AnimationFrame> getFrames() {
        return Collections.unmodifiableList(frames);
    }

    /**
     * Gets the resolved Sprite for a frame index.
     * Sprites are lazily loaded and cached on first access.
     */
    public Sprite getFrameSprite(int index) {
        ensureSpritesResolved();
        return cachedSprites.get(index);
    }

    private void ensureSpritesResolved() {
        if (cachedSprites == null) {
            cachedSprites = new ArrayList<>(frames.size());
            for (AnimationFrame frame : frames) {
                Sprite sprite = SpriteReference.fromPath(frame.spritePath());
                cachedSprites.add(sprite);
            }
        }
    }

    /**
     * Invalidates the sprite cache. Called on hot reload.
     */
    public void invalidateCache() {
        cachedSprites = null;
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    /**
     * Calculates total duration of all frames.
     */
    public float getTotalDuration() {
        float total = 0;
        for (AnimationFrame frame : frames) {
            total += frame.duration();
        }
        return total;
    }

    // ========================================================================
    // FRAME MANIPULATION (for editor)
    // ========================================================================

    public void addFrame(AnimationFrame frame) {
        frames.add(frame);
        invalidateCache();
    }

    public void addFrame(int index, AnimationFrame frame) {
        frames.add(index, frame);
        invalidateCache();
    }

    public void removeFrame(int index) {
        frames.remove(index);
        invalidateCache();
    }

    public void setFrame(int index, AnimationFrame frame) {
        frames.set(index, frame);
        invalidateCache();
    }

    public void moveFrame(int fromIndex, int toIndex) {
        AnimationFrame frame = frames.remove(fromIndex);
        frames.add(toIndex, frame);
        invalidateCache();
    }

    public void clearFrames() {
        frames.clear();
        invalidateCache();
    }

    public void setFrames(List<AnimationFrame> frames) {
        this.frames.clear();
        this.frames.addAll(frames);
        invalidateCache();
    }

    /**
     * Copies data from another animation (for hot reload).
     */
    public void copyFrom(Animation other) {
        this.name = other.name;
        this.looping = other.looping;
        this.frames.clear();
        this.frames.addAll(other.frames);
        invalidateCache();
    }

    // ========================================================================
    // PROGRAMMATIC CREATION (for runtime-created animations)
    // ========================================================================

    /**
     * Creates an animation from sprites with uniform frame duration.
     * For runtime-created animations that don't come from asset files.
     * <p>
     * Note: Sprites must be registered in the asset system for serialization to work.
     * For purely runtime animations, serialization will not preserve the sprite references.
     *
     * @param name          Animation name
     * @param sprites       List of sprites for each frame
     * @param frameDuration Duration for each frame in seconds
     * @param looping       Whether the animation loops
     * @return New Animation instance with pre-cached sprites
     */
    public static Animation fromSprites(String name, List<Sprite> sprites, float frameDuration, boolean looping) {
        Animation anim = new Animation(name);
        anim.setLooping(looping);

        // Pre-cache the sprites directly
        anim.cachedSprites = new ArrayList<>(sprites);

        // Create frames with placeholder paths (won't serialize properly, but works at runtime)
        for (int i = 0; i < sprites.size(); i++) {
            // Try to get actual path from asset system
            String path = com.pocket.rpg.resources.Assets.getPathForResource(sprites.get(i));
            if (path == null) {
                path = "runtime://sprite_" + i; // Placeholder for unregistered sprites
            }
            anim.frames.add(new AnimationFrame(path, frameDuration));
        }

        return anim;
    }
}
