package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Frustum culler for perspective (3D) cameras.
 * Extracts frustum planes and performs AABB vs plane tests.
 */
public class PerspectiveFrustumCuller extends FrustumCuller {

    // Six frustum planes: left, right, bottom, top, near, far
    // Each plane stored as (a, b, c, d) where ax + by + cz + d = 0
    private final Vector4f[] frustumPlanes = new Vector4f[6];

    public PerspectiveFrustumCuller() {
        for (int i = 0; i < 6; i++) {
            frustumPlanes[i] = new Vector4f();
        }
    }

    /**
     * Updates the perspective frustum planes from the camera.
     * Extracts planes from the combined view-projection matrix.
     */
    @Override
    public void updateFromCamera(Camera camera) {
        this.camera = camera;

        if (camera == null) return;

        // Get view-projection matrix
        Matrix4f viewProj = new Matrix4f(camera.getProjectionMatrix())
                .mul(camera.getViewMatrix());

        // Extract frustum planes using Gribb-Hartmann method
        // Left plane: add 4th column to 1st column
        extractPlane(frustumPlanes[0], viewProj, 3, 0);

        // Right plane: subtract 1st column from 4th column
        extractPlane(frustumPlanes[1], viewProj, 3, 0, true);

        // Bottom plane: add 4th column to 2nd column
        extractPlane(frustumPlanes[2], viewProj, 3, 1);

        // Top plane: subtract 2nd column from 4th column
        extractPlane(frustumPlanes[3], viewProj, 3, 1, true);

        // Near plane: add 4th column to 3rd column
        extractPlane(frustumPlanes[4], viewProj, 3, 2);

        // Far plane: subtract 3rd column from 4th column
        extractPlane(frustumPlanes[5], viewProj, 3, 2, true);

        // Normalize all planes
        for (Vector4f plane : frustumPlanes) {
            float length = (float) Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
            if (length > 0) {
                plane.div(length);
            }
        }
    }

    /**
     * Extracts a frustum plane from the view-projection matrix.
     */
    private void extractPlane(Vector4f plane, Matrix4f matrix, int col1, int col2) {
        extractPlane(plane, matrix, col1, col2, false);
    }

    /**
     * Extracts a frustum plane from the view-projection matrix.
     *
     * @param subtract If true, subtracts col2 from col1; otherwise adds them
     */
    private void extractPlane(Vector4f plane, Matrix4f matrix, int col1, int col2, boolean subtract) {
        float sign = subtract ? -1.0f : 1.0f;

        // Extract plane equation coefficients from matrix columns
        plane.x = matrix.m00() * (col1 == 0 ? 1 : 0) + matrix.m10() * (col1 == 1 ? 1 : 0) +
                matrix.m20() * (col1 == 2 ? 1 : 0) + matrix.m30() * (col1 == 3 ? 1 : 0) +
                sign * (matrix.m00() * (col2 == 0 ? 1 : 0) + matrix.m10() * (col2 == 1 ? 1 : 0) +
                        matrix.m20() * (col2 == 2 ? 1 : 0) + matrix.m30() * (col2 == 3 ? 1 : 0));

        plane.y = matrix.m01() * (col1 == 0 ? 1 : 0) + matrix.m11() * (col1 == 1 ? 1 : 0) +
                matrix.m21() * (col1 == 2 ? 1 : 0) + matrix.m31() * (col1 == 3 ? 1 : 0) +
                sign * (matrix.m01() * (col2 == 0 ? 1 : 0) + matrix.m11() * (col2 == 1 ? 1 : 0) +
                        matrix.m21() * (col2 == 2 ? 1 : 0) + matrix.m31() * (col2 == 3 ? 1 : 0));

        plane.z = matrix.m02() * (col1 == 0 ? 1 : 0) + matrix.m12() * (col1 == 1 ? 1 : 0) +
                matrix.m22() * (col1 == 2 ? 1 : 0) + matrix.m32() * (col1 == 3 ? 1 : 0) +
                sign * (matrix.m02() * (col2 == 0 ? 1 : 0) + matrix.m12() * (col2 == 1 ? 1 : 0) +
                        matrix.m22() * (col2 == 2 ? 1 : 0) + matrix.m32() * (col2 == 3 ? 1 : 0));

        plane.w = matrix.m03() * (col1 == 0 ? 1 : 0) + matrix.m13() * (col1 == 1 ? 1 : 0) +
                matrix.m23() * (col1 == 2 ? 1 : 0) + matrix.m33() * (col1 == 3 ? 1 : 0) +
                sign * (matrix.m03() * (col2 == 0 ? 1 : 0) + matrix.m13() * (col2 == 1 ? 1 : 0) +
                        matrix.m23() * (col2 == 2 ? 1 : 0) + matrix.m33() * (col2 == 3 ? 1 : 0));
    }

    /**
     * Tests if a sprite is visible in the perspective frustum.
     * Uses AABB vs frustum plane tests.
     */
    @Override
    public boolean isVisible(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return false;
        }

        // Calculate sprite AABB (no rotation padding needed for plane tests)
        float[] aabb = calculateAABB(spriteRenderer, false);
        if (aabb == null) return false;

        Transform transform = spriteRenderer.getGameObject().getTransform();
        Vector3f pos = transform.getPosition();
        float minZ = pos.z - 0.5f; // Assume sprites have minimal depth
        float maxZ = pos.z + 0.5f;

        // Test AABB against each frustum plane
        for (Vector4f plane : frustumPlanes) {
            // Find the "positive vertex" (furthest point in plane's normal direction)
            float px = (plane.x > 0) ? aabb[2] : aabb[0];
            float py = (plane.y > 0) ? aabb[3] : aabb[1];
            float pz = (plane.z > 0) ? maxZ : minZ;

            // If positive vertex is outside (negative side of plane), AABB is completely outside
            if (plane.x * px + plane.y * py + plane.z * pz + plane.w < 0) {
                return false; // Culled
            }
        }

        return true; // At least partially visible
    }
}