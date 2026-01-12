package com.pocket.rpg.scenes.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.core.OverlayRenderer;
import lombok.Getter;
import org.joml.Vector4f;

/**
 * Basic fade transition that fades out to a color, then fades back in.
 * <p>
 * Timeline:
 * - 0% to 50%: Fade OUT (scene becomes more obscured, alpha 0 → 1)
 * - 50%: Scene switch occurs
 * - 50% to 100%: Fade IN (scene becomes visible, alpha 1 → 0)
 * <p>
 * UPDATED: Now implements ISceneTransition and uses OverlayRenderer.
 */
public class FadeTransition implements ISceneTransition {

    protected final float fadeOutDuration;
    protected final float fadeInDuration;
    protected final Vector4f fadeColor;

    protected float currentTime;
    @Getter
    protected float alpha;
    protected boolean midpointReached;

    /**
     * Creates a fade transition from configuration.
     *
     * @param config transition configuration
     */
    public FadeTransition(TransitionConfig config) {
        this.fadeOutDuration = config.getFadeOutDuration();
        this.fadeInDuration = config.getFadeInDuration();
        this.fadeColor = new Vector4f(config.getFadeColor());
        this.currentTime = 0;
        this.alpha = 0;
        this.midpointReached = false;
    }

    /**
     * Creates a fade transition with specific parameters.
     *
     * @param fadeOutDuration duration of fade out in seconds
     * @param fadeInDuration  duration of fade in in seconds
     * @param fadeColor       color to fade to (RGBA)
     */
    public FadeTransition(float fadeOutDuration, float fadeInDuration, Vector4f fadeColor) {
        this.fadeOutDuration = fadeOutDuration;
        this.fadeInDuration = fadeInDuration;
        this.fadeColor = new Vector4f(fadeColor);
        this.currentTime = 0;
        this.alpha = 0;
        this.midpointReached = false;
    }

    @Override
    public void update(float deltaTime) {
        currentTime += deltaTime;

        // Calculate which phase we're in
        if (currentTime < fadeOutDuration) {
            // Fade OUT phase: alpha increases from 0 to 1
            float fadeOutProgress = currentTime / fadeOutDuration;
            alpha = easeInOut(fadeOutProgress);
        } else {
            // Fade IN phase: alpha decreases from 1 to 0
            float fadeInTime = currentTime - fadeOutDuration;
            if (fadeInTime < fadeInDuration) {
                float fadeInProgress = fadeInTime / fadeInDuration;
                alpha = 1.0f - easeInOut(fadeInProgress);
            } else {
                alpha = 0;
            }
        }
    }

    @Override
    public void render(OverlayRenderer overlayRenderer) {
        if (alpha > 0.001f) { // Only render if visible
            Vector4f colorWithAlpha = new Vector4f(fadeColor.x, fadeColor.y, fadeColor.z, alpha);
            overlayRenderer.drawFullscreenQuad(colorWithAlpha);
        }
    }

    @Override
    public boolean isComplete() {
        return currentTime >= (fadeOutDuration + fadeInDuration);
    }

    @Override
    public void reset() {
        currentTime = 0;
        alpha = 0;
        midpointReached = false;
    }

    @Override
    public float getProgress() {
        float totalDuration = fadeOutDuration + fadeInDuration;
        if (totalDuration <= 0) {
            return 1.0f;
        }
        return Math.min(1.0f, currentTime / totalDuration);
    }

    @Override
    public boolean isAtMidpoint() {
        if (!midpointReached && currentTime >= fadeOutDuration) {
            midpointReached = true;  // Set it HERE when returning true
            return true;
        }
        return false;
    }

    /**
     * Easing function for smooth transitions.
     * Uses smoothstep (3t^2 - 2t^3) for smooth acceleration and deceleration.
     *
     * @param t input value (0 to 1)
     * @return eased value (0 to 1)
     */
    protected float easeInOut(float t) {
        // Clamp to 0-1 range
        t = Math.max(0, Math.min(1, t));
        // Smoothstep formula
        return t * t * (3.0f - 2.0f * t);
    }

}