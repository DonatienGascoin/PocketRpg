package com.pocket.rpg.ui;

import org.joml.Vector2f;

/**
 * Preset anchor positions for common UI layouts.
 * Anchor (0,0) = bottom-left, (1,1) = top-right.
 */
public enum AnchorPreset {
    BOTTOM_LEFT(0, 0),
    BOTTOM_CENTER(0.5f, 0),
    BOTTOM_RIGHT(1, 0),
    CENTER_LEFT(0, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1, 0.5f),
    TOP_LEFT(0, 1),
    TOP_CENTER(0.5f, 1),
    TOP_RIGHT(1, 1);

    private final float x;
    private final float y;

    AnchorPreset(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public Vector2f toVector() {
        return new Vector2f(x, y);
    }
}