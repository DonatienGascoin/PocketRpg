package com.pocket.rpg.scenes.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.core.OverlayRenderer;
import org.joml.Vector4f;

/**
 * Luma wipe transition that uses a grayscale texture to define the wipe pattern.
 * Black pixels in the luma texture reveal first, white pixels reveal last.
 * <p>
 * Timeline:
 * - 0% to 50%: Wipe OUT (cutoff 0 -> 1, screen fills with color)
 * - 50%: Scene switch occurs
 * - 50% to 100%: Wipe IN (cutoff 1 -> 0, screen reveals new scene)
 * <p>
 * Extends FadeTransition to reuse timing logic, midpoint detection, and easing.
 */
public class LumaTransition extends FadeTransition {

    private final int lumaTextureId;

    /**
     * Creates a luma transition from configuration.
     *
     * @param config        transition configuration (timing, color)
     * @param lumaTextureId OpenGL texture ID of the grayscale luma texture
     */
    public LumaTransition(TransitionConfig config, int lumaTextureId) {
        super(config);
        this.lumaTextureId = lumaTextureId;
    }

    /**
     * Creates a luma transition with specific parameters.
     *
     * @param fadeOutDuration duration of wipe out in seconds
     * @param fadeInDuration  duration of wipe in in seconds
     * @param fadeColor       color of the wipe overlay
     * @param lumaTextureId  OpenGL texture ID of the grayscale luma texture
     */
    public LumaTransition(float fadeOutDuration, float fadeInDuration,
                           Vector4f fadeColor, int lumaTextureId) {
        super(fadeOutDuration, fadeInDuration, fadeColor);
        this.lumaTextureId = lumaTextureId;
    }

    @Override
    public void render(OverlayRenderer overlayRenderer) {
        float cutoff = calculateCutoff();

        if (cutoff > 0.001f) {
            overlayRenderer.drawLumaWipe(fadeColor, cutoff, lumaTextureId);
        }
    }

    /**
     * Calculates the luma cutoff value.
     * During fade out: 0.0 -> 1.0 (screen fills with color)
     * During fade in: 1.0 -> 0.0 (screen reveals new scene)
     *
     * @return cutoff from 0.0 (nothing drawn) to 1.0 (fully drawn)
     */
    private float calculateCutoff() {
        if (currentTime < fadeOutDuration) {
            // Wipe OUT phase: cutoff increases from 0 to 1
            float fadeOutProgress = currentTime / fadeOutDuration;
            return easeInOut(fadeOutProgress);
        } else {
            // Wipe IN phase: cutoff decreases from 1 to 0
            float fadeInTime = currentTime - fadeOutDuration;
            if (fadeInTime < fadeInDuration) {
                float fadeInProgress = fadeInTime / fadeInDuration;
                return 1.0f - easeInOut(fadeInProgress);
            } else {
                return 0.0f;
            }
        }
    }

    /**
     * Gets the luma texture ID.
     *
     * @return the OpenGL texture ID
     */
    public int getLumaTextureId() {
        return lumaTextureId;
    }
}
