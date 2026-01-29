package com.pocket.rpg.rendering;

import com.pocket.rpg.rendering.core.OverlayRenderer;
import org.joml.Vector4f;

/**
 * Mock implementation of OverlayRenderer for testing.
 * Lightweight, deterministic, and doesn't require OpenGL.
 */
public class MockOverlayRenderer implements OverlayRenderer {

    private int drawCallCount = 0;
    private Vector4f lastDrawnColor = null;
    private int lumaWipeCallCount = 0;
    private float lastLumaCutoff = 0;
    private int lastLumaTextureId = 0;

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
    public void drawLumaWipe(Vector4f color, float cutoff, int textureId) {
        drawCallCount++;
        lumaWipeCallCount++;
        lastDrawnColor = new Vector4f(color);
        lastLumaCutoff = cutoff;
        lastLumaTextureId = textureId;
    }

    @Override
    public void setScreenSize(int width, int height) {

    }

    @Override
    public void destroy() {
        // No-op for testing
    }

    @Override
    public boolean isInitialized() {
        return false;
    }

    /**
     * Gets the number of times any draw method was called.
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
     * Gets the number of times drawLumaWipe was called.
     */
    public int getLumaWipeCallCount() {
        return lumaWipeCallCount;
    }

    /**
     * Gets the last luma cutoff value.
     */
    public float getLastLumaCutoff() {
        return lastLumaCutoff;
    }

    /**
     * Gets the last luma texture ID.
     */
    public int getLastLumaTextureId() {
        return lastLumaTextureId;
    }

    /**
     * Resets the mock state.
     */
    public void reset() {
        drawCallCount = 0;
        lastDrawnColor = null;
        lumaWipeCallCount = 0;
        lastLumaCutoff = 0;
        lastLumaTextureId = 0;
    }
}
