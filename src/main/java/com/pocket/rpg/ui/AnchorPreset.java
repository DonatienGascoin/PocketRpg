package com.pocket.rpg.ui;

import org.joml.Vector2f;

/**
 * Preset anchor positions for common UI layouts.
 *
 * Coordinate system (matches Camera):
 * - Origin (0,0) = TOP-LEFT
 * - X increases to the right
 * - Y increases downward
 * - (1,1) = BOTTOM-RIGHT
 */
public enum AnchorPreset {
    TOP_LEFT(0, 0),
    TOP_CENTER(0.5f, 0),
    TOP_RIGHT(1, 0),
    CENTER_LEFT(0, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1, 0.5f),
    BOTTOM_LEFT(0, 1),
    BOTTOM_CENTER(0.5f, 1),
    BOTTOM_RIGHT(1, 1);

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