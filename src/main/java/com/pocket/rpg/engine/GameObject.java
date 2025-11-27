package com.pocket.rpg.engine;

import com.pocket.rpg.components.Component;
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

    @Getter
    private boolean enabled = true;

    /**
     * Internal method called by Scene when GameObject is added.
     */
    @Setter
    @Getter
    private Scene scene;

    private final List<Component> components;

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

    // =======================================================================
    // GameObject State Management
    // =======================================================================

    /**
     * Sets the enabled state of this GameObject.
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;

//        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
/*
        // Notify all components of state change
        for (Component component : components) {
            if (enabled && component.isEnabled()) {
                // GameObject was disabled, now enabled - trigger onEnable
                component.triggerEnable();
            } else if (!enabled && wasEnabled && component.isEnabled()) {
                // GameObject was enabled, now disabled - trigger onDisable
                component.triggerDisable();
            }
        }*/
    }

    // =======================================================================
    // Component Management
    // =======================================================================

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
        if (scene != null) {
            scene.registerCachedComponent(component);
        }

        // Auto-start if GameObject is already in active scene and enabled
        if (scene != null && enabled) {
            component.start();
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
            if (scene != null) {
                scene.unregisterCachedComponent(component);
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

    /**
     * Gets all components of the specified type.
     * Useful when multiple components of the same type exist.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> List<T> getComponents(Class<T> componentClass) {
        List<T> result = new ArrayList<>();
        for (Component component : components) {
            if (componentClass.isInstance(component)) {
                result.add((T) component);
            }
        }
        return result;
    }

    /**
     * Gets all components attached to this GameObject.
     */
    public List<Component> getAllComponents() {
        return new ArrayList<>(components);
    }

    // =======================================================================
    // Transform Change Notification
    // =======================================================================

    /**
     * Notifies all components that the transform has changed.
     * Called automatically by Transform when position, rotation, or scale changes.
     */
    public void notifyTransformChanged() {
        // Create snapshot to avoid ConcurrentModificationException
        List<Component> snapshot = new ArrayList<>(components);

        for (Component component : snapshot) {
            if (component.isEnabled()) {
                component.onTransformChanged();
            }
        }
    }

    // =======================================================================
    // Lifecycle Methods
    // =======================================================================

    /**
     * Called when the GameObject is added to a scene.
     */
    public void start() {
        // Create snapshot to avoid ConcurrentModificationException
        List<Component> snapshot = new ArrayList<>(components);

        for (Component component : snapshot) {
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

        // Create snapshot to allow adding/removing components during update
        List<Component> snapshot = new ArrayList<>(components);

        for (Component component : snapshot) {
            if (component.isEnabled()) {
                // Ensure start is called before first update
                if (!component.isStarted()) {
                    component.start();
                }
                if (component.isEnabled()) {
                    component.update(deltaTime);
                }
            }
        }
    }

    /**
     * Called every frame after all update() calls.
     * Use this for operations that depend on other updates being complete.
     */
    public void lateUpdate(float deltaTime) {
        if (!enabled) return;

        // Create snapshot to avoid ConcurrentModificationException
        List<Component> snapshot = new ArrayList<>(components);

        for (Component component : snapshot) {
            if (component.isEnabled()) {
                component.lateUpdate(deltaTime);
            }
        }
    }

    /**
     * Called when the GameObject is destroyed.
     */
    public void destroy() {
        // Create snapshot to avoid ConcurrentModificationException during destruction
        List<Component> snapshot = new ArrayList<>(components);

        for (Component component : snapshot) {
            component.destroy();
        }
        components.clear();
    }
}