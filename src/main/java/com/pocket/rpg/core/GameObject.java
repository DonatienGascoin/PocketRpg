package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GameObject is the fundamental entity in the game.
 * It holds components that define its behavior and properties.
 *
 * UPDATED: Now supports parent-child hierarchy.
 */
public class GameObject implements IGameObject {
    @Setter
    @Getter
    private String name;

    @Getter
    private boolean enabled = true;

    @Getter
    private boolean destroyed = false;

    @Setter
    @Getter
    private Scene scene;

    private final List<Component> components;

    @Getter
    private Transform transform;

    // Parent-child hierarchy
    @Getter
    private GameObject parent;

    private final List<GameObject> children = new ArrayList<>();

    @Override
    public String getId() {
        return "go_" + System.identityHashCode(this);
    }

    public GameObject(String name) {
        this.name = name;
        this.components = new ArrayList<>();
        this.transform = new Transform();
        addComponentInternal(transform);
    }

    public GameObject(String name, Vector3f position) {
        this.name = name;
        this.components = new ArrayList<>();
        this.transform = new Transform(position);
        addComponentInternal(transform);
    }

    // =======================================================================
    // Parent-Child Hierarchy
    // =======================================================================

    /**
     * Sets the parent of this GameObject.
     * Automatically updates both parent's and previous parent's children lists.
     * Handles scene registration when parented to an object in a scene.
     */
    public void setParent(GameObject newParent) {
        if (newParent == this) {
            System.err.println("Cannot set GameObject as its own parent!");
            return;
        }

        // Check for circular reference
        if (newParent != null && isAncestorOf(newParent)) {
            System.err.println("Cannot set descendant as parent (circular reference)!");
            return;
        }

        Scene oldScene = this.scene;

        // Remove from old parent
        if (this.parent != null) {
            this.parent.children.remove(this);
        }

        this.parent = newParent;

        // Add to new parent
        if (newParent != null) {
            newParent.children.add(this);

            Scene newScene = newParent.scene;

            // Handle scene changes
            if (newScene != null && newScene != oldScene) {
                // Moving to a different scene
                if (oldScene != null) {
                    oldScene.unregisterCachedComponents(this);
                }
                setSceneRecursive(newScene);
                newScene.registerCachedComponents(this);
            } else if (newScene != null && oldScene == null) {
                // First time being added to a scene via parenting
                setSceneRecursive(newScene);
                newScene.registerCachedComponents(this);
            }
            // If newScene == oldScene, components are already registered - do nothing
        }
    }

    /**
     * Adds a child GameObject.
     * Convenience method - equivalent to child.setParent(this).
     */
    public void addChild(GameObject child) {
        if (child != null) {
            child.setParent(this);
        }
    }

    /**
     * Removes a child GameObject.
     * Convenience method - equivalent to child.setParent(null).
     */
    public void removeChild(GameObject child) {
        if (child != null && children.contains(child)) {
            child.setParent(null);
        }
    }

    /**
     * Returns an unmodifiable list of children.
     */
    public List<GameObject> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Checks if this GameObject is an ancestor of the given GameObject.
     */
    private boolean isAncestorOf(GameObject other) {
        GameObject current = other.parent;
        while (current != null) {
            if (current == this) return true;
            current = current.parent;
        }
        return false;
    }

    /**
     * Sets scene recursively for this object and all children.
     */
    private void setSceneRecursive(Scene scene) {
        this.scene = scene;
        for (GameObject child : children) {
            child.setSceneRecursive(scene);
        }
    }

    // =======================================================================
    // GameObject State Management
    // =======================================================================

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        // Notify components on this GameObject (snapshot for iteration safety)
        for (Component component : new ArrayList<>(components)) {
            if (enabled) {
                component.triggerEnable();
            } else {
                component.triggerDisable();
            }
        }

        // Invalidate Scene caches (renderables, uiCanvases, ComponentKeyRegistry)
        if (scene != null) {
            if (enabled) {
                scene.registerCachedComponents(this);
            } else {
                scene.unregisterCachedComponents(this);
            }
        }

        // Propagate to children — only notify children whose effective state changed
        for (GameObject child : new ArrayList<>(children)) {
            child.propagateParentEnabledChange(enabled);
        }
    }

    /**
     * Called when an ancestor's enabled state changes.
     * Only fires callbacks on components whose effective state actually changed.
     * Respects this child's own enabled flag — if child is individually disabled,
     * its components won't receive triggerEnable() when parent is re-enabled.
     */
    void propagateParentEnabledChange(boolean parentNowEnabled) {
        // If this child is individually disabled, its effective state didn't change
        if (!this.enabled) return;

        // This child is individually enabled, so parent change affects it
        for (Component component : new ArrayList<>(components)) {
            if (parentNowEnabled) {
                component.triggerEnable();
            } else {
                component.triggerDisable();
            }
        }

        // Invalidate Scene caches for this child
        if (scene != null) {
            if (parentNowEnabled) {
                scene.registerCachedComponents(this);
            } else {
                scene.unregisterCachedComponents(this);
            }
        }

        // Recurse to grandchildren
        for (GameObject child : new ArrayList<>(children)) {
            child.propagateParentEnabledChange(parentNowEnabled);
        }
    }

    // =======================================================================
    // Component Management
    // =======================================================================

    public <T extends Component> T addComponent(T component) {
        // Allow UITransform to replace the auto-created Transform
        if (component instanceof UITransform) {
            // Remove the auto-created Transform
            components.remove(transform);
            // UITransform becomes the new transform (it IS-A Transform)
            transform = (Transform) component;
            addComponentInternal(component);

            if (scene != null) {
                scene.registerCachedComponent(component);
            }

            if (scene != null && enabled) {
                component.start();
            }

            return component;
        }

        if (component instanceof Transform) {
            System.err.println("Cannot add Transform component - it's automatically created!");
            return component;
        }

        // Auto-add required components before adding the main component
        addRequiredComponents(component.getClass());

        addComponentInternal(component);

        if (scene != null) {
            scene.registerCachedComponent(component);
        }

        if (scene != null && enabled) {
            component.start();
        }

        return component;
    }

    private void addComponentInternal(Component component) {
        component.setOwner(this);
        components.add(component);
    }

    /**
     * Adds any components declared by @RequiredComponent on the given class
     * or its superclasses, if they are not already present on this GameObject.
     */
    private void addRequiredComponents(Class<?> componentClass) {
        Class<?> clazz = componentClass;
        while (clazz != null && clazz != Component.class && clazz != Object.class) {
            RequiredComponent[] requirements = clazz.getDeclaredAnnotationsByType(RequiredComponent.class);
            for (RequiredComponent req : requirements) {
                Class<? extends Component> requiredType = req.value();
                if (getComponent(requiredType) != null) {
                    continue;
                }
                try {
                    Component dependency = requiredType.getDeclaredConstructor().newInstance();
                    addComponent(dependency);
                } catch (Exception e) {
                    System.err.println("[RequiredComponent] Failed to auto-add " +
                            requiredType.getSimpleName() + ": " + e.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public void removeComponent(Component component) {
        if (component == transform) {
            System.err.println("Cannot remove Transform component!");
            return;
        }

        if (components.remove(component)) {
            component.destroy();
            component.setOwner(null);

            if (scene != null) {
                scene.unregisterCachedComponent(component);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> componentClass) {
        for (Component component : components) {
            if (componentClass.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }

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

    @SuppressWarnings("unchecked")
    public <T extends Component> List<T> getComponents() {
        List<T> result = new ArrayList<>();
        for (Component component : components) {
                result.add((T) component);
        }
        return result;
    }

    public List<Component> getAllComponents() {
        return new ArrayList<>(components);
    }

    @Override
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    // =======================================================================
    // Transform Change Notification
    // =======================================================================

    public void notifyTransformChanged() {
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

    public void start() {
        List<Component> snapshot = new ArrayList<>(components);
        for (Component component : snapshot) {
            if (component.isEnabled()) {
                component.start();
            }
        }

        // Start children too
        for (GameObject child : new ArrayList<>(children)) {
            child.start();
        }
    }

    public void update(float deltaTime) {
        if (!enabled) return;

        List<Component> snapshot = new ArrayList<>(components);
        for (Component component : snapshot) {
            if (component.isEnabled()) {
                if (!component.isStarted()) {
                    component.start();
                }
                if (component.isEnabled()) {
                    try {
                        component.update(deltaTime);
                    } catch (Exception e) {
                        Log.error(component.logTag(), "update() failed", e);
                    }
                }
            }
        }

        // Update children
        for (GameObject child : new ArrayList<>(children)) {
            child.update(deltaTime);
        }
    }

    public void lateUpdate(float deltaTime) {
        if (!enabled) return;

        List<Component> snapshot = new ArrayList<>(components);
        for (Component component : snapshot) {
            if (component.isEnabled()) {
                try {
                    component.lateUpdate(deltaTime);
                } catch (Exception e) {
                    Log.error(component.logTag(), "lateUpdate() failed", e);
                }
            }
        }

        // Late update children
        for (GameObject child : new ArrayList<>(children)) {
            child.lateUpdate(deltaTime);
        }
    }

    public void destroy() {
        destroyed = true;

        // Destroy children first
        for (GameObject child : new ArrayList<>(children)) {
            child.destroy();
        }
        children.clear();

        // Destroy components
        List<Component> snapshot = new ArrayList<>(components);
        for (Component component : snapshot) {
            component.destroy();
        }
        components.clear();

        // Remove from parent
        if (parent != null) {
            parent.children.remove(this);
            parent = null;
        }
    }
}