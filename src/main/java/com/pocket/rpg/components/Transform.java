package com.pocket.rpg.components;

import org.joml.Vector3f;

/**
 * Transform component that defines position, rotation, and scale in 3D space.
 * Every GameObject has exactly one Transform component.
 *
 * UPDATED: Automatically notifies components when transform changes.
 */
public class Transform extends Component {

    private final Vector3f position;
    private final Vector3f rotation;
    private final Vector3f scale;

    public Transform() {
        this.position = new Vector3f(0, 0, 0);
        this.rotation = new Vector3f(0, 0, 0);
        this.scale = new Vector3f(1, 1, 1);
    }

    public Transform(Vector3f position) {
        this.position = new Vector3f(position);
        this.rotation = new Vector3f(0, 0, 0);
        this.scale = new Vector3f(1, 1, 1);
    }

    public Transform(Vector3f position, Vector3f rotation, Vector3f scale) {
        this.position = new Vector3f(position);
        this.rotation = new Vector3f(rotation);
        this.scale = new Vector3f(scale);
    }

    // ========== Position ==========

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void setPosition(Vector3f position) {
        setPosition(position.x, position.y, position.z);
    }

    public void setPosition(float x, float y, float z) {
        // Check if actually changed
        if (this.position.x == x && this.position.y == y && this.position.z == z) {
            return;
        }

        this.position.set(x, y, z);
        notifyTransformChanged();
    }

    public void translate(float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) {
            return;
        }

        this.position.add(x, y, z);
        notifyTransformChanged();
    }

    public void translate(Vector3f translation) {
        translate(translation.x, translation.y, translation.z);
    }

    // ========== Rotation ==========

    public Vector3f getRotation() {
        return new Vector3f(rotation);
    }

    public void setRotation(Vector3f rotation) {
        setRotation(rotation.x, rotation.y, rotation.z);
    }

    public void setRotation(float x, float y, float z) {
        // Check if actually changed
        if (this.rotation.x == x && this.rotation.y == y && this.rotation.z == z) {
            return;
        }

        this.rotation.set(x, y, z);
        notifyTransformChanged();
    }

    public void rotate(float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) {
            return;
        }

        this.rotation.add(x, y, z);
        notifyTransformChanged();
    }

    public void rotate(Vector3f rotation) {
        rotate(rotation.x, rotation.y, rotation.z);
    }

    // ========== Scale ==========

    public Vector3f getScale() {
        return new Vector3f(scale);
    }

    public void setScale(Vector3f scale) {
        setScale(scale.x, scale.y, scale.z);
    }

    public void setScale(float x, float y, float z) {
        // Check if actually changed
        if (this.scale.x == x && this.scale.y == y && this.scale.z == z) {
            return;
        }

        this.scale.set(x, y, z);
        notifyTransformChanged();
    }

    public void setScale(float uniformScale) {
        setScale(uniformScale, uniformScale, uniformScale);
    }

    // ========== Change Notification ==========

    /**
     * Notifies all components on the GameObject that the transform has changed.
     * This is called automatically when position, rotation, or scale is modified.
     */
    private void notifyTransformChanged() {
        if (gameObject != null) {
            gameObject.notifyTransformChanged();
        }
    }

    // ========== Utility Methods ==========

    @Override
    public String toString() {
        return String.format("Transform[pos=(%.2f, %.2f, %.2f), rot=(%.2f, %.2f, %.2f), scale=(%.2f, %.2f, %.2f)]",
                position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z,
                scale.x, scale.y, scale.z);
    }
}