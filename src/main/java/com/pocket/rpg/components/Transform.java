package com.pocket.rpg.components;

import lombok.Getter;
import org.joml.Vector3f;

/**
 * Transform component that defines position, rotation, and scale.
 * This is a mandatory component for all GameObjects.
 */
@Getter
public class Transform extends Component {
    // Position
    private Vector3f position;
    // Rotation
    private Vector3f rotation;
    // Scale
    private Vector3f scale;

    public Transform() {
        this.position = new Vector3f();
        this.rotation = new Vector3f();
        this.scale = new Vector3f(1.0f, 1.0f, 1.0f);
    }

    public Transform(Vector3f position) {
        this.position = new Vector3f(position);
        this.rotation = new Vector3f();
        this.scale = new Vector3f(1.0f, 1.0f, 1.0f);
    }

    public Transform(Vector3f position, Vector3f rotation, Vector3f scale) {
        this.position = new Vector3f(position);
        this.rotation = new Vector3f(rotation);
        this.scale = new Vector3f(scale);
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    public void translate(Vector3f delta) {
        this.position.set(this.position.add(delta));
    }

    public void translate(float x, float y, float z) {
        this.position.x += x;
        this.position.y += y;
        this.position.z += z;
    }

    public void setRotation(Vector3f rotation) {
        this.rotation.set(rotation);
    }

    public void setRotation(float x, float y, float z) {
        this.rotation.set(x, y, z);
    }

    public void rotate(Vector3f delta) {
        this.rotation.set(this.rotation.add(delta));
    }

    public void rotate(float x, float y, float z) {
        this.rotation.x += x;
        this.rotation.y += y;
        this.rotation.z += z;
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
}