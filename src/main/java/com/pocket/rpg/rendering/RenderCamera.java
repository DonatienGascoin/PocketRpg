package com.pocket.rpg.rendering;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Unified camera interface for all rendering contexts.
 * <p>
 * Provides the minimal contract needed for renderers to work with any camera type:
 * game cameras, editor cameras, preview cameras, etc.
 *
 * <h2>Coordinate System</h2>
 * All implementations use Y-up world coordinates:
 * <ul>
 *   <li>+X = right</li>
 *   <li>+Y = up</li>
 *   <li>Camera position is at the CENTER of the visible area</li>
 * </ul>
 *
 * <h2>World Bounds Format</h2>
 * {@link #getWorldBounds()} returns {@code [left, bottom, right, top]} in world units.
 */
public interface RenderCamera {

    /**
     * Gets the projection matrix for this camera.
     *
     * @return Copy of the projection matrix
     */
    Matrix4f getProjectionMatrix();

    /**
     * Gets the view matrix for this camera.
     *
     * @return Copy of the view matrix
     */
    Matrix4f getViewMatrix();

    /**
     * Gets the visible world bounds.
     *
     * @return [left, bottom, right, top] in world coordinates
     */
    float[] getWorldBounds();

    /**
     * Converts world coordinates to screen coordinates.
     *
     * @param worldX World X position
     * @param worldY World Y position
     * @return Screen position in pixels
     */
    Vector2f worldToScreen(float worldX, float worldY);

    /**
     * Converts screen coordinates to world coordinates.
     *
     * @param screenX Screen X position in pixels
     * @param screenY Screen Y position in pixels
     * @return World position
     */
    Vector3f screenToWorld(float screenX, float screenY);
}