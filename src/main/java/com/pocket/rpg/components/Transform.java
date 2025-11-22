package com.pocket.rpg.components;

import org.joml.Vector3f;

import java.util.Objects;

/**
 * Transform component that defines position, rotation, and scale.
 * This is a mandatory component for all GameObjects.
 */
public class Transform extends Component {
    private Vector3f position;
    private Vector3f rotation;
    private Vector3f scale;

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

    // Position
    public Vector3f getPosition() {
        return position;
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    public void translate(Vector3f delta) {
        this.position.add(delta);
    }

    public void translate(float x, float y, float z) {
        this.position.add(x, y, z);
    }

    // Rotation
    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(Vector3f rotation) {
        this.rotation.set(rotation);
    }

    public void setRotation(float x, float y, float z) {
        this.rotation.set(x, y, z);
    }

    public void rotate(Vector3f delta) {
        this.rotation.add(delta);
    }

    public void rotate(float x, float y, float z) {
        this.rotation.add(x, y, z);
    }

    // Scale
    public Vector3f getScale() {
        return scale;
    }

    public void setScale(Vector3f scale) {
        this.scale.set(scale);
    }

    public void setScale(float x, float y, float z) {
        this.scale.set(x, y, z);
    }

    public void setScale(float uniform) {
        this.scale.set(uniform, uniform, uniform);
    }

    @Override
    public String toString() {
        return String.format("Transform[pos=%s, rot=%s, scale=%s]",
                position, rotation, scale);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transform transform)) return false;
        return Objects.equals(position, transform.position)
                && Objects.equals(rotation, transform.rotation)
                && Objects.equals(scale, transform.scale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, rotation, scale);
    }
}