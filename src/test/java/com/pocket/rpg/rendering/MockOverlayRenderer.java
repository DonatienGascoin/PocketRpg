package com.pocket.rpg.rendering;

import org.joml.Vector4f;

/**
 * Mock implementation of OverlayRenderer for testing.
 * Lightweight, deterministic, and doesn't require OpenGL.
 */
public class MockOverlayRenderer extends OverlayRenderer {

    private int drawCallCount = 0;
    private Vector4f lastDrawnColor = null;

    @Override
    public void init() {
        // No-op for testing
    }

    @Override
    public void drawFullscreenQuad(Vector4f color) {
        drawCallCount++;
        lastDrawnColor = new Vector4f(color);
    }

    @Override
    public void destroy() {
        // No-op for testing
    }

    /**
     * Gets the number of times drawFullscreenQuad was called.
     */
    public int getDrawCallCount() {
        return drawCallCount;
    }

    /**
     * Gets the last color that was drawn.
     */
    public Vector4f getLastDrawnColor() {
        return lastDrawnColor;
    }

    /**
     * Resets the mock state.
     */
    public void reset() {
        drawCallCount = 0;
        lastDrawnColor = null;
    }
}