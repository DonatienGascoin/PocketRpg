package com.pocket.rpg.components;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Transform component that defines position, rotation, and scale in 3D space.
 * Every GameObject has exactly one Transform component.
 *
 * <h2>Hierarchy Support</h2>
 * Transform supports parent-child relationships through GameObject hierarchy.
 * Position, rotation, and scale are all inherited from parents.
 *
 * <ul>
 *   <li>{@link #getPosition()} - World position (computed from parent chain)</li>
 *   <li>{@link #getLocalPosition()} - Local position (relative to parent)</li>
 *   <li>{@link #setPosition(float, float, float)} - Sets local position</li>
 *   <li>{@link #setWorldPosition(float, float, float)} - Sets position to achieve desired world position</li>
 *   <li>{@link #getScale()} - World scale (computed from parent chain)</li>
 *   <li>{@link #getLocalScale()} - Local scale (relative to parent)</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * World position/rotation/scale are lazily computed and cached. The cache is invalidated
 * when this transform or any ancestor changes.
 */
public class Transform extends Component {

    // Local transform (relative to parent)
    private final Vector3f localPosition;
    private final Vector3f localRotation;
    private final Vector3f localScale;

    // Cached world transform - recomputed at runtime
    private transient final Vector3f worldPosition;
    private transient final Vector3f worldRotation;
    private transient final Vector3f worldScale;
    private transient final Matrix4f worldMatrix;
    private transient boolean worldDirty = true;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public Transform() {
        this.localPosition = new Vector3f(0, 0, 0);
        this.localRotation = new Vector3f(0, 0, 0);
        this.localScale = new Vector3f(1, 1, 1);
        this.worldPosition = new Vector3f(0, 0, 0);
        this.worldRotation = new Vector3f(0, 0, 0);
        this.worldScale = new Vector3f(1, 1, 1);
        this.worldMatrix = new Matrix4f();
    }

    public Transform(Vector3f position) {
        this.localPosition = new Vector3f(position);
        this.localRotation = new Vector3f(0, 0, 0);
        this.localScale = new Vector3f(1, 1, 1);
        this.worldPosition = new Vector3f(0, 0, 0);
        this.worldRotation = new Vector3f(0, 0, 0);
        this.worldScale = new Vector3f(1, 1, 1);
        this.worldMatrix = new Matrix4f();
    }

    public Transform(Vector3f position, Vector3f rotation, Vector3f scale) {
        this.localPosition = new Vector3f(position);
        this.localRotation = new Vector3f(rotation);
        this.localScale = new Vector3f(scale);
        this.worldPosition = new Vector3f(0, 0, 0);
        this.worldRotation = new Vector3f(0, 0, 0);
        this.worldScale = new Vector3f(1, 1, 1);
        this.worldMatrix = new Matrix4f();
    }

    // ========================================================================
    // WORLD POSITION (computed from hierarchy)
    // ========================================================================

    /**
     * Gets the world position (absolute position in world space).
     * This accounts for all parent transforms in the hierarchy.
     *
     * @return World position (new Vector3f instance)
     */
    public Vector3f getPosition() {
        return getWorldPosition();
    }

    /**
     * Gets the world position (absolute position in world space).
     *
     * @return World position (new Vector3f instance)
     */
    public Vector3f getWorldPosition() {
        if (worldDirty) {
            recalculateWorldTransform();
        }
        return new Vector3f(worldPosition);
    }

    /**
     * Gets the world rotation (absolute rotation in world space).
     * This accounts for all parent rotations in the hierarchy.
     *
     * @return World rotation in degrees (new Vector3f instance)
     */
    public Vector3f getWorldRotation() {
        if (worldDirty) {
            recalculateWorldTransform();
        }
        return new Vector3f(worldRotation);
    }

    /**
     * Gets the cached world transformation matrix.
     * Useful for rendering and physics calculations.
     *
     * @return World matrix (new Matrix4f instance)
     */
    public Matrix4f getWorldMatrix() {
        if (worldDirty) {
            recalculateWorldTransform();
        }
        return new Matrix4f(worldMatrix);
    }

    /**
     * Sets position such that the world position equals the given value.
     * Reverse-calculates the required local position based on parent chain.
     *
     * @param x World X position
     * @param y World Y position
     * @param z World Z position
     */
    public void setWorldPosition(float x, float y, float z) {
        Transform parentTransform = getParentTransform();

        if (parentTransform == null) {
            // No parent - world position equals local position
            setLocalPosition(x, y, z);
        } else {
            // Calculate required local position
            Vector3f parentWorld = parentTransform.getWorldPosition();
            Vector3f parentRotation = parentTransform.getWorldRotation();

            // If parent has rotation, we need to inverse-rotate the offset
            float localX = x - parentWorld.x;
            float localY = y - parentWorld.y;
            float localZ = z - parentWorld.z;

            if (parentRotation.z != 0) {
                // Inverse rotate around Z axis
                float angle = (float) Math.toRadians(-parentRotation.z);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float rotatedX = localX * cos - localY * sin;
                float rotatedY = localX * sin + localY * cos;
                localX = rotatedX;
                localY = rotatedY;
            }

            setLocalPosition(localX, localY, localZ);
        }
    }

    /**
     * Sets position such that the world position equals the given value.
     *
     * @param worldPosition Desired world position
     */
    public void setWorldPosition(Vector3f worldPosition) {
        setWorldPosition(worldPosition.x, worldPosition.y, worldPosition.z);
    }

    // ========================================================================
    // LOCAL POSITION (relative to parent)
    // ========================================================================

    /**
     * Gets the local position (relative to parent, or world if no parent).
     *
     * @return Local position (new Vector3f instance)
     */
    public Vector3f getLocalPosition() {
        return new Vector3f(localPosition);
    }

    /**
     * Sets the local position (relative to parent).
     * This is the primary way to move objects.
     *
     * @param position New local position
     */
    public void setPosition(Vector3f position) {
        setLocalPosition(position.x, position.y, position.z);
    }

    /**
     * Sets the local position (relative to parent).
     *
     * @param x Local X position
     * @param y Local Y position
     * @param z Local Z position
     */
    public void setPosition(float x, float y, float z) {
        setLocalPosition(x, y, z);
    }

    /**
     * Sets the local position (relative to parent).
     *
     * @param x Local X position
     * @param y Local Y position
     * @param z Local Z position
     */
    public void setLocalPosition(float x, float y, float z) {
        if (this.localPosition.x == x && this.localPosition.y == y && this.localPosition.z == z) {
            return;
        }

        this.localPosition.set(x, y, z);
        markDirtyAndNotify();
    }

    /**
     * Sets the local position (relative to parent).
     *
     * @param position New local position
     */
    public void setLocalPosition(Vector3f position) {
        setLocalPosition(position.x, position.y, position.z);
    }

    /**
     * Translates the local position by the given amount.
     *
     * @param x X translation
     * @param y Y translation
     * @param z Z translation
     */
    public void translate(float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) {
            return;
        }

        this.localPosition.add(x, y, z);
        markDirtyAndNotify();
    }

    /**
     * Translates the local position by the given amount.
     *
     * @param translation Translation vector
     */
    public void translate(Vector3f translation) {
        translate(translation.x, translation.y, translation.z);
    }

    // ========================================================================
    // ROTATION (local + world)
    // ========================================================================

    /**
     * Gets the rotation. For compatibility, returns world rotation.
     *
     * @return World rotation in degrees (new Vector3f instance)
     */
    public Vector3f getRotation() {
        return getWorldRotation();
    }

    /**
     * Gets the local rotation (relative to parent).
     *
     * @return Local rotation in degrees (new Vector3f instance)
     */
    public Vector3f getLocalRotation() {
        return new Vector3f(localRotation);
    }

    /**
     * Sets the local rotation.
     *
     * @param rotation New local rotation in degrees
     */
    public void setRotation(Vector3f rotation) {
        setLocalRotation(rotation.x, rotation.y, rotation.z);
    }

    /**
     * Sets the local rotation.
     *
     * @param x X rotation in degrees
     * @param y Y rotation in degrees
     * @param z Z rotation in degrees
     */
    public void setRotation(float x, float y, float z) {
        setLocalRotation(x, y, z);
    }

    /**
     * Sets the local rotation.
     *
     * @param x X rotation in degrees
     * @param y Y rotation in degrees
     * @param z Z rotation in degrees
     */
    public void setLocalRotation(float x, float y, float z) {
        if (this.localRotation.x == x && this.localRotation.y == y && this.localRotation.z == z) {
            return;
        }

        this.localRotation.set(x, y, z);
        markDirtyAndNotify();
    }

    /**
     * Sets the local rotation.
     *
     * @param rotation New local rotation in degrees
     */
    public void setLocalRotation(Vector3f rotation) {
        setLocalRotation(rotation.x, rotation.y, rotation.z);
    }

    /**
     * Rotates by the given amount (adds to current local rotation).
     *
     * @param x X rotation delta in degrees
     * @param y Y rotation delta in degrees
     * @param z Z rotation delta in degrees
     */
    public void rotate(float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) {
            return;
        }

        this.localRotation.add(x, y, z);
        markDirtyAndNotify();
    }

    /**
     * Rotates by the given amount.
     *
     * @param rotation Rotation delta in degrees
     */
    public void rotate(Vector3f rotation) {
        rotate(rotation.x, rotation.y, rotation.z);
    }

    // ========================================================================
    // SCALE (inherited from parents)
    // ========================================================================

    /**
     * Gets the world scale (absolute scale in world space).
     * This is the local scale multiplied by all parent scales.
     *
     * @return World scale (new Vector3f instance)
     */
    public Vector3f getScale() {
        return getWorldScale();
    }

    /**
     * Gets the world scale (absolute scale in world space).
     *
     * @return World scale (new Vector3f instance)
     */
    public Vector3f getWorldScale() {
        if (worldDirty) {
            recalculateWorldTransform();
        }
        return new Vector3f(worldScale);
    }

    /**
     * Gets the local scale (relative to parent).
     *
     * @return Local scale (new Vector3f instance)
     */
    public Vector3f getLocalScale() {
        return new Vector3f(localScale);
    }

    /**
     * Sets the local scale.
     *
     * @param scale New scale
     */
    public void setScale(Vector3f scale) {
        setLocalScale(scale.x, scale.y, scale.z);
    }

    /**
     * Sets the local scale.
     *
     * @param x X scale
     * @param y Y scale
     * @param z Z scale
     */
    public void setScale(float x, float y, float z) {
        setLocalScale(x, y, z);
    }

    /**
     * Sets the local scale.
     *
     * @param x X scale
     * @param y Y scale
     * @param z Z scale
     */
    public void setLocalScale(float x, float y, float z) {
        if (this.localScale.x == x && this.localScale.y == y && this.localScale.z == z) {
            return;
        }

        this.localScale.set(x, y, z);
        markDirtyAndNotify();
    }

    /**
     * Sets the local scale.
     *
     * @param localScale localScale
     */
    public void setLocalScale(Vector3f localScale) {
        if (this.localScale.x == localScale.x && this.localScale.y == localScale.y && this.localScale.z == localScale.z) {
            return;
        }

        this.localScale.set(new Vector3f(localScale));
        markDirtyAndNotify();
    }

    /**
     * Sets uniform local scale on all axes.
     *
     * @param uniformScale Scale value for all axes
     */
    public void setScale(float uniformScale) {
        setLocalScale(uniformScale, uniformScale, uniformScale);
    }

    // ========================================================================
    // HIERARCHY SUPPORT
    // ========================================================================

    /**
     * Gets the parent's Transform, or null if no parent.
     */
    private Transform getParentTransform() {
        if (gameObject == null) {
            return null;
        }

        var parent = gameObject.getParent();
        if (parent == null) {
            return null;
        }

        return parent.getTransform();
    }

    /**
     * Marks this transform's world cache as dirty.
     * Called when a parent transform changes.
     */
    public void markWorldDirty() {
        if (!worldDirty) {
            worldDirty = true;
            // Propagate to children
            markChildrenDirty();
        }
    }

    /**
     * Marks all children's transforms as dirty.
     */
    private void markChildrenDirty() {
        if (gameObject == null) {
            return;
        }

        for (var child : gameObject.getChildren()) {
            if (child != null) {
                Transform childTransform = child.getTransform();
                if (childTransform != null) {
                    childTransform.markWorldDirty();
                }
            }
        }
    }

    /**
     * Marks dirty, notifies components, and propagates to children.
     */
    private void markDirtyAndNotify() {
        worldDirty = true;
        markChildrenDirty();
        notifyTransformChanged();
    }

    /**
     * Notifies all components on this GameObject and children that transform changed.
     */
    private void notifyTransformChanged() {
        if (gameObject != null) {
            gameObject.notifyTransformChanged();
            // Also notify children's components
            notifyChildrenTransformChanged();
        }
    }

    /**
     * Recursively notifies children that their world transform has changed.
     */
    private void notifyChildrenTransformChanged() {
        if (gameObject == null) {
            return;
        }

        for (var child : gameObject.getChildren()) {
            if (child != null) {
                child.notifyTransformChanged();
                // Continue recursively through child's transform
                Transform childTransform = child.getTransform();
                if (childTransform != null) {
                    childTransform.notifyChildrenTransformChanged();
                }
            }
        }
    }

    // ========================================================================
    // WORLD TRANSFORM CALCULATION
    // ========================================================================

    /**
     * Recalculates the world position, rotation, scale, and matrix from the parent chain.
     */
    private void recalculateWorldTransform() {
        Transform parentTransform = getParentTransform();

        if (parentTransform == null) {
            // No parent - local equals world
            worldPosition.set(localPosition);
            worldRotation.set(localRotation);
            worldScale.set(localScale);
        } else {
            // Get parent's world transform (may trigger recursive calculation)
            Vector3f parentWorldPos = parentTransform.getWorldPosition();
            Vector3f parentWorldRot = parentTransform.getWorldRotation();
            Vector3f parentWorldScale = parentTransform.getWorldScale();

            // Inherit scale (multiplicative)
            worldScale.set(
                    localScale.x * parentWorldScale.x,
                    localScale.y * parentWorldScale.y,
                    localScale.z * parentWorldScale.z
            );

            // Inherit rotation (additive)
            worldRotation.set(
                    localRotation.x + parentWorldRot.x,
                    localRotation.y + parentWorldRot.y,
                    localRotation.z + parentWorldRot.z
            );

            // Inherit position (with parent's rotation AND scale applied to local offset)
            float scaledLocalX = localPosition.x * parentWorldScale.x;
            float scaledLocalY = localPosition.y * parentWorldScale.y;
            float scaledLocalZ = localPosition.z * parentWorldScale.z;

            if (parentWorldRot.z != 0) {
                // Rotate scaled local position around parent's Z axis
                float angle = (float) Math.toRadians(parentWorldRot.z);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                float rotatedX = scaledLocalX * cos - scaledLocalY * sin;
                float rotatedY = scaledLocalX * sin + scaledLocalY * cos;

                worldPosition.set(
                        parentWorldPos.x + rotatedX,
                        parentWorldPos.y + rotatedY,
                        parentWorldPos.z + scaledLocalZ
                );
            } else {
                // No rotation - simple addition
                worldPosition.set(
                        parentWorldPos.x + scaledLocalX,
                        parentWorldPos.y + scaledLocalY,
                        parentWorldPos.z + scaledLocalZ
                );
            }
        }

        // Build world matrix (for rendering)
        buildWorldMatrix();

        worldDirty = false;
    }

    /**
     * Builds the world transformation matrix.
     * Uses world position, world rotation, and world scale.
     */
    private void buildWorldMatrix() {
        worldMatrix.identity();

        // Translate to world position
        worldMatrix.translate(worldPosition);

        // Apply world rotation (Z only for 2D)
        if (worldRotation.z != 0) {
            worldMatrix.rotateZ((float) Math.toRadians(worldRotation.z));
        }
        if (worldRotation.x != 0) {
            worldMatrix.rotateX((float) Math.toRadians(worldRotation.x));
        }
        if (worldRotation.y != 0) {
            worldMatrix.rotateY((float) Math.toRadians(worldRotation.y));
        }

        // Apply world scale (inherited)
        worldMatrix.scale(worldScale);
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Checks if this transform has a parent.
     *
     * @return true if there is a parent transform
     */
    public boolean hasParent() {
        return getParentTransform() != null;
    }

    /**
     * Gets the depth of this transform in the hierarchy.
     * Root transforms have depth 0.
     *
     * @return Hierarchy depth
     */
    public int getHierarchyDepth() {
        int depth = 0;
        Transform current = getParentTransform();
        while (current != null) {
            depth++;
            current = current.getParentTransform();
        }
        return depth;
    }

    @Override
    public String toString() {
        if (worldDirty) {
            return String.format("Transform[local=(%.2f, %.2f, %.2f), rot=(%.2f, %.2f, %.2f), scale=(%.2f, %.2f, %.2f), worldDirty=true]",
                    localPosition.x, localPosition.y, localPosition.z,
                    localRotation.x, localRotation.y, localRotation.z,
                    localScale.x, localScale.y, localScale.z);
        } else {
            return String.format("Transform[world=(%.2f, %.2f, %.2f), local=(%.2f, %.2f, %.2f), rot=(%.2f, %.2f, %.2f)]",
                    worldPosition.x, worldPosition.y, worldPosition.z,
                    localPosition.x, localPosition.y, localPosition.z,
                    localRotation.x, localRotation.y, localRotation.z);
        }
    }
}