package com.pocket.rpg.animation.tween;

/**
 * Easing functions for tweens.
 * <p>
 * Visual reference: https://easings.net/
 */
public enum Ease {

    LINEAR,

    // Quad
    IN_QUAD,
    OUT_QUAD,
    IN_OUT_QUAD,

    // Cubic
    IN_CUBIC,
    OUT_CUBIC,
    IN_OUT_CUBIC,

    // Quart
    IN_QUART,
    OUT_QUART,
    IN_OUT_QUART,

    // Sine
    IN_SINE,
    OUT_SINE,
    IN_OUT_SINE,

    // Expo
    IN_EXPO,
    OUT_EXPO,
    IN_OUT_EXPO,

    // Back (overshoot)
    IN_BACK,
    OUT_BACK,
    IN_OUT_BACK,

    // Bounce
    IN_BOUNCE,
    OUT_BOUNCE,
    IN_OUT_BOUNCE,

    // Elastic
    IN_ELASTIC,
    OUT_ELASTIC,
    IN_OUT_ELASTIC;

    /**
     * Applies the easing function to a normalized time value (0-1).
     *
     * @param t Normalized time (0 = start, 1 = end)
     * @return Eased value
     */
    public float apply(float t) {
        return switch (this) {
            case LINEAR -> t;

            // Quad
            case IN_QUAD -> t * t;
            case OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case IN_OUT_QUAD -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;

            // Cubic
            case IN_CUBIC -> t * t * t;
            case OUT_CUBIC -> 1 - (float) Math.pow(1 - t, 3);
            case IN_OUT_CUBIC -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;

            // Quart
            case IN_QUART -> t * t * t * t;
            case OUT_QUART -> 1 - (float) Math.pow(1 - t, 4);
            case IN_OUT_QUART -> t < 0.5f ? 8 * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 4) / 2;

            // Sine
            case IN_SINE -> 1 - (float) Math.cos(t * Math.PI / 2);
            case OUT_SINE -> (float) Math.sin(t * Math.PI / 2);
            case IN_OUT_SINE -> -(float) (Math.cos(Math.PI * t) - 1) / 2;

            // Expo
            case IN_EXPO -> t == 0 ? 0 : (float) Math.pow(2, 10 * t - 10);
            case OUT_EXPO -> t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
            case IN_OUT_EXPO -> t == 0 ? 0 : t == 1 ? 1 : t < 0.5f
                    ? (float) Math.pow(2, 20 * t - 10) / 2
                    : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;

            // Back
            case IN_BACK -> {
                float c1 = 1.70158f;
                float c3 = c1 + 1;
                yield c3 * t * t * t - c1 * t * t;
            }
            case OUT_BACK -> {
                float c1 = 1.70158f;
                float c3 = c1 + 1;
                yield 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
            }
            case IN_OUT_BACK -> {
                float c1 = 1.70158f;
                float c2 = c1 * 1.525f;
                yield t < 0.5f
                        ? (float) (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                        : (float) (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
            }

            // Bounce
            case OUT_BOUNCE -> {
                float n1 = 7.5625f;
                float d1 = 2.75f;
                float x = t;
                if (x < 1 / d1) {
                    yield n1 * x * x;
                } else if (x < 2 / d1) {
                    x -= 1.5f / d1;
                    yield n1 * x * x + 0.75f;
                } else if (x < 2.5 / d1) {
                    x -= 2.25f / d1;
                    yield n1 * x * x + 0.9375f;
                } else {
                    x -= 2.625f / d1;
                    yield n1 * x * x + 0.984375f;
                }
            }
            case IN_BOUNCE -> 1 - OUT_BOUNCE.apply(1 - t);
            case IN_OUT_BOUNCE -> t < 0.5f
                    ? (1 - OUT_BOUNCE.apply(1 - 2 * t)) / 2
                    : (1 + OUT_BOUNCE.apply(2 * t - 1)) / 2;

            // Elastic
            case IN_ELASTIC -> {
                float c4 = (float) (2 * Math.PI) / 3;
                yield t == 0 ? 0 : t == 1 ? 1
                        : (float) (-Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * c4));
            }
            case OUT_ELASTIC -> {
                float c4 = (float) (2 * Math.PI) / 3;
                yield t == 0 ? 0 : t == 1 ? 1
                        : (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
            }
            case IN_OUT_ELASTIC -> {
                float c5 = (float) (2 * Math.PI) / 4.5f;
                yield t == 0 ? 0 : t == 1 ? 1 : t < 0.5f
                        ? (float) (-(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2)
                        : (float) (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5) / 2 + 1);
            }
        };
    }
}