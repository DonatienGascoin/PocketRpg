package com.pocket.rpg.tween;

import com.pocket.rpg.components.ui.UITransform;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Factory methods for creating common tweens, especially for UI.
 * <p>
 * Example usage:
 * <pre>
 * // Slide panel in from right
 * UITransform transform = panel.getComponent(UITransform.class);
 * Tweens.offsetX(transform, 0f, 0.3f).setEase(Ease.OUT_BACK);
 *
 * // Fade in
 * Tweens.alpha(image, 1f, 0.2f).setEase(Ease.OUT_QUAD);
 *
 * // Scale bounce
 * Tweens.size(transform, new Vector2f(120, 120), 0.15f)
 *       .setEase(Ease.OUT_ELASTIC);
 *
 * // Sequence: slide in, wait, slide out
 * Tweens.offsetX(transform, 0f, 0.3f)
 *       .setEase(Ease.OUT_BACK)
 *       .onComplete(() -> {
 *           Tweens.offsetX(transform, 400f, 0.3f)
 *                 .setDelay(2f)
 *                 .setEase(Ease.IN_BACK);
 *       });
 * </pre>
 */
public class Tweens {

    private Tweens() {
        // Static utility class
    }

    // ========================================================================
    // FLOAT TWEENS
    // ========================================================================

    /**
     * Tweens a float value.
     *
     * @param getter   Function to get current value
     * @param setter   Function to set value
     * @param endValue Target value
     * @param duration Duration in seconds
     */
    public static Tween<Float> value(float startValue, float endValue, float duration,
                                     java.util.function.Consumer<Float> setter) {
        return new Tween<>(
                startValue,
                endValue,
                duration,
                (start, end, t) -> start + (end - start) * t,
                setter
        );
    }

    // ========================================================================
    // UITRANSFORM OFFSET TWEENS
    // ========================================================================

    /**
     * Tweens UITransform offset (position relative to anchor).
     */
    public static Tween<Vector2f> offset(UITransform transform, Vector2f endValue, float duration) {
        Vector2f start = new Vector2f(transform.getOffset());
        return new Tween<>(
                start,
                endValue,
                duration,
                Tweens::lerpVector2f,
                transform::setOffset
        ).setTarget(transform);
    }

    /**
     * Tweens UITransform offset X only.
     */
    public static Tween<Float> offsetX(UITransform transform, float endX, float duration) {
        float startX = transform.getOffset().x;
        return new Tween<>(
                startX,
                endX,
                duration,
                Tweens::lerpFloat,
                x -> transform.getOffset().x = x
        ).setTarget(transform);
    }

    /**
     * Tweens UITransform offset Y only.
     */
    public static Tween<Float> offsetY(UITransform transform, float endY, float duration) {
        float startY = transform.getOffset().y;
        return new Tween<>(
                startY,
                endY,
                duration,
                Tweens::lerpFloat,
                y -> transform.getOffset().y = y
        ).setTarget(transform);
    }

    // ========================================================================
    // UITRANSFORM SIZE TWEENS
    // ========================================================================

    /**
     * Tweens UITransform size (width and height).
     */
    public static Tween<Vector2f> size(UITransform transform, Vector2f endSize, float duration) {
        Vector2f start = new Vector2f(transform.getWidth(), transform.getHeight());
        return new Tween<>(
                start,
                endSize,
                duration,
                Tweens::lerpVector2f,
                size -> {
                    transform.setWidth(size.x);
                    transform.setHeight(size.y);
                }
        ).setTarget(transform);
    }

    /**
     * Tweens UITransform width only.
     */
    public static Tween<Float> width(UITransform transform, float endWidth, float duration) {
        return new Tween<>(
                transform.getWidth(),
                endWidth,
                duration,
                Tweens::lerpFloat,
                transform::setWidth
        ).setTarget(transform);
    }

    /**
     * Tweens UITransform height only.
     */
    public static Tween<Float> height(UITransform transform, float endHeight, float duration) {
        return new Tween<>(
                transform.getHeight(),
                endHeight,
                duration,
                Tweens::lerpFloat,
                transform::setHeight
        ).setTarget(transform);
    }

    /**
     * Tweens UITransform scale uniformly (multiplies current size).
     */
    public static Tween<Float> scale(UITransform transform, float endScale, float duration) {
        float startWidth = transform.getWidth();
        float startHeight = transform.getHeight();
        float startScale = 1f;

        return new Tween<>(
                startScale,
                endScale,
                duration,
                Tweens::lerpFloat,
                scale -> {
                    transform.setWidth(startWidth * scale);
                    transform.setHeight(startHeight * scale);
                }
        ).setTarget(transform);
    }

    // ========================================================================
    // UITRANSFORM ANCHOR/PIVOT TWEENS
    // ========================================================================

    /**
     * Tweens UITransform anchor.
     */
    public static Tween<Vector2f> anchor(UITransform transform, Vector2f endAnchor, float duration) {
        Vector2f start = new Vector2f(transform.getAnchor());
        return new Tween<>(
                start,
                endAnchor,
                duration,
                Tweens::lerpVector2f,
                transform::setAnchor
        ).setTarget(transform);
    }

    /**
     * Tweens UITransform pivot.
     */
    public static Tween<Vector2f> pivot(UITransform transform, Vector2f endPivot, float duration) {
        Vector2f start = new Vector2f(transform.getPivot());
        return new Tween<>(
                start,
                endPivot,
                duration,
                Tweens::lerpVector2f,
                transform::setPivot
        ).setTarget(transform);
    }

    // ========================================================================
    // COLOR TWEENS
    // ========================================================================

    /**
     * Tweens a Vector4f color (RGBA).
     */
    public static Tween<Vector4f> color(Vector4f current, Vector4f endColor, float duration,
                                        java.util.function.Consumer<Vector4f> setter) {
        Vector4f start = new Vector4f(current);
        return new Tween<>(
                start,
                endColor,
                duration,
                Tweens::lerpVector4f,
                setter
        );
    }

    /**
     * Tweens alpha (opacity) of a Vector4f color.
     */
    public static Tween<Float> alpha(Vector4f color, float endAlpha, float duration) {
        float startAlpha = color.w;
        return new Tween<>(
                startAlpha,
                endAlpha,
                duration,
                Tweens::lerpFloat,
                alpha -> color.w = alpha
        );
    }

    // ========================================================================
    // DELAY / SEQUENCE HELPERS
    // ========================================================================

    /**
     * Creates a delay (empty tween that just waits).
     * Useful for sequences.
     */
    public static Tween<Float> delay(float duration, Runnable onComplete) {
        return new Tween<>(
                0f, 0f, duration,
                (s, e, t) -> 0f,
                v -> {}
        ).onComplete(onComplete);
    }

    /**
     * Runs an action after a delay.
     */
    public static void delayedCall(float delay, Runnable action) {
        delay(delay, action);
    }

    // ========================================================================
    // INTERPOLATION HELPERS
    // ========================================================================

    private static Float lerpFloat(Float start, Float end, float t) {
        return start + (end - start) * t;
    }

    private static Vector2f lerpVector2f(Vector2f start, Vector2f end, float t) {
        return new Vector2f(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t
        );
    }

    private static Vector4f lerpVector4f(Vector4f start, Vector4f end, float t) {
        return new Vector4f(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t,
                start.z + (end.z - start.z) * t,
                start.w + (end.w - start.w) * t
        );
    }

    // ========================================================================
    // PRESET ANIMATIONS
    // ========================================================================

    /**
     * Punch scale effect (quick scale up then back).
     */
    public static Tween<Float> punchScale(UITransform transform, float punch, float duration) {
        float startWidth = transform.getWidth();
        float startHeight = transform.getHeight();

        return scale(transform, 1f + punch, duration / 2)
                .setEase(Ease.OUT_QUAD)
                .onComplete(() -> {
                    new Tween<>(
                            1f + punch, 1f, duration / 2,
                            Tweens::lerpFloat,
                            scale -> {
                                transform.setWidth(startWidth * scale);
                                transform.setHeight(startHeight * scale);
                            }
                    ).setEase(Ease.IN_QUAD).setTarget(transform);
                });
    }

    /**
     * Shake effect (oscillating offset).
     */
    public static Tween<Float> shake(UITransform transform, float intensity, float duration) {
        Vector2f originalOffset = new Vector2f(transform.getOffset());

        return new Tween<>(
                0f, 1f, duration,
                Tweens::lerpFloat,
                t -> {
                    float decay = 1f - t;
                    float offsetX = (float) (Math.sin(t * 50) * intensity * decay);
                    float offsetY = (float) (Math.cos(t * 47) * intensity * decay * 0.5f);
                    transform.setOffset(new Vector2f(
                            originalOffset.x + offsetX,
                            originalOffset.y + offsetY
                    ));
                }
        ).setTarget(transform).setEase(Ease.LINEAR);
    }

    /**
     * Slide in from direction.
     */
    public static Tween<Float> slideIn(UITransform transform, Direction from, float distance, float duration) {
        float startX = transform.getOffset().x;
        float startY = transform.getOffset().y;

        return switch (from) {
            case LEFT -> {
                transform.getOffset().x = startX - distance;
                yield offsetX(transform, startX, duration);
            }
            case RIGHT -> {
                transform.getOffset().x = startX + distance;
                yield offsetX(transform, startX, duration);
            }
            case TOP -> {
                transform.getOffset().y = startY - distance;
                yield offsetY(transform, startY, duration);
            }
            case BOTTOM -> {
                transform.getOffset().y = startY + distance;
                yield offsetY(transform, startY, duration);
            }
        };
    }

    /**
     * Slide out to direction.
     */
    public static Tween<Float> slideOut(UITransform transform, Direction to, float distance, float duration) {
        float startX = transform.getOffset().x;
        float startY = transform.getOffset().y;

        return switch (to) {
            case LEFT -> offsetX(transform, startX - distance, duration);
            case RIGHT -> offsetX(transform, startX + distance, duration);
            case TOP -> offsetY(transform, startY - distance, duration);
            case BOTTOM -> offsetY(transform, startY + distance, duration);
        };
    }

    public enum Direction {
        LEFT, RIGHT, TOP, BOTTOM
    }
}