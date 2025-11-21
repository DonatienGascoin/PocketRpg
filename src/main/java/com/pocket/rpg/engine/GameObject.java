package com.pocket.rpg.engine;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * GameObject is the fundamental entity in the game.
 * It holds components that define its behavior and properties.
 */
public class GameObject {
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private boolean enabled = true;
    /**
     * Internal method called by Scene when GameObject is added.
     */
    @Setter
    @Getter
    private Scene scene;

    private List<Component> components;
    /**
     * -- GETTER --
     * Gets the Transform component (always present).
     */
    @Getter
    private Transform transform; // Cached reference to mandatory Transform

    public GameObject(String name) {
        this.name = name;
        this.components = new ArrayList<>();

        // Every GameObject must have a Transform
        this.transform = new Transform();
        addComponentInternal(transform);
    }

    public GameObject(String name, Vector3f position) {
        this.name = name;
        this.components = new ArrayList<>();

        // Every GameObject must have a Transform
        this.transform = new Transform(position);
        addComponentInternal(transform);
    }

    // Component Management

    /**
     * Adds a component to this GameObject.
     * Note: Transform cannot be added this way as it's automatically created.
     */
    public <T extends Component> T addComponent(T component) {
        if (component instanceof Transform) {
            System.err.println("Cannot add Transform component - it's automatically created!");
            return component;
        }

        addComponentInternal(component);

        // If adding to an active scene, notify the scene
        if (scene != null && component instanceof SpriteRenderer) {
            scene.registerSpriteRenderer((SpriteRenderer) component);
        }

        return component;
    }

    /**
     * Internal method to add components without scene notification.
     */
    private void addComponentInternal(Component component) {
        component.setGameObject(this);
        components.add(component);
    }

    /**
     * Removes a component from this GameObject.
     */
    public void removeComponent(Component component) {
        if (component == transform) {
            System.err.println("Cannot remove Transform component!");
            return;
        }

        if (components.remove(component)) {
            component.destroy();
            component.setGameObject(null);

            // If removing from an active scene, notify the scene
            if (scene != null && component instanceof SpriteRenderer) {
                scene.unregisterSpriteRenderer((SpriteRenderer) component);
            }
        }
    }

    /**
     * Gets the first component of the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> componentClass) {
        for (Component component : components) {
            if (componentClass.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }

    // Lifecycle Methods

    /**
     * Called when the GameObject is added to a scene.
     */
    public void start() {
        for (Component component : components) {
            if (component.isEnabled()) {
                component.start();
            }
        }
    }

    /**
     * Called every frame.
     */
    public void update(float deltaTime) {
        if (!enabled) return;

        for (Component component : components) {
            if (component.isEnabled()) {
                // Ensure start is called before first update
                if (!component.isStarted()) {
                    component.start();

                }
                component.update(deltaTime);
            }
        }
    }

    /**
     * Called when the GameObject is destroyed.
     */
    public void destroy() {
        for (Component component : components) {
            component.destroy();
        }
        components.clear();
    }
}