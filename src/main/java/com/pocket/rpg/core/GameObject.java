package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GameObject is the fundamental entity in the game.
 * It holds components that define its behavior and properties.
 * Supports parent-child hierarchy.
 */
public class GameObject {
    @Setter
    @Getter
    private String name;

    @Getter
    private boolean enabled = true;

    @Getter
    private boolean destroyed = false;

    private final List<Component> components;

    @Getter
    private Transform transform;

    @Getter
    private int order = 0;

    // Parent-child hierarchy
    @Getter
    private GameObject parent;

    private final List<GameObject> children = new ArrayList<>();

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

        // Remove from old parent
        if (this.parent != null) {
            this.parent.children.remove(this);
        }

        this.parent = newParent;

        // Clear stale layout overrides when reparenting
        // (the old parent's layout group no longer manages this child)
        var uiTransform = getComponent(com.pocket.rpg.components.ui.UITransform.class);
        if (uiTransform != null) {
            uiTransform.clearLayoutOverrides();
        }

        // Add to new parent
        if (newParent != null) {
            newParent.children.add(this);
        }

        // Invalidate world transform cache (parent chain changed)
        if (transform != null) {
            transform.markWorldDirty();
        }

        // Re-register with active scene (handles cache updates for reparenting)
        Scene scene = SceneManager.getActiveScene();
        if (scene != null) {
            scene.unregisterCachedComponents(this);
            scene.registerCachedComponents(this);
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
    public boolean isAncestorOf(GameObject other) {
        GameObject current = other.parent;
        while (current != null) {
            if (current == this) return true;
            current = current.parent;
        }
        return false;
    }

    /**
     * Returns true if this game object and all its ancestors are enabled.
     */
    public boolean isActiveInHierarchy() {
        if (!enabled) return false;
        GameObject current = parent;
        while (current != null) {
            if (!current.enabled) return false;
            current = current.parent;
        }
        return true;
    }

    /**
     * Returns true if this is a runtime game object (in a running scene).
     * Overridden by EditorGameObject to return false.
     */
    public boolean isRuntime() {
        return true;
    }

    /**
     * Returns true if this is an editor game object (in the scene editor).
     * Overridden by EditorGameObject to return true.
     */
    public boolean isEditor() {
        return false;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Sets the sibling order and re-sorts the parent's children list
     * so that getChildren() always returns children in order.
     */
    public void setOrder(int order) {
        this.order = order;
        if (parent != null) {
            parent.sortChildrenByOrder();
        }
    }

    /**
     * Sorts the children list to match logical order values.
     * Ensures getChildren() returns children in the correct order for layouts/rendering.
     */
    public void sortChildrenByOrder() {
        children.sort(java.util.Comparator.comparingInt(GameObject::getOrder));
    }

    // =======================================================================
    // Protected Accessors for Subclasses
    // =======================================================================

    /**
     * Sets the enabled field directly without triggering callbacks or propagation.
     * Used by EditorGameObject which doesn't need runtime lifecycle management.
     */
    protected void setEnabledDirect(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the raw mutable component list.
     * Used by EditorGameObject for its special construction and override logic.
     */
    protected List<Component> getComponentsInternal() {
        return components;
    }

    /**
     * Returns the raw mutable children list.
     * Used by EditorGameObject and EditorScene for hierarchy management.
     */
    protected List<GameObject> getChildrenInternal() {
        return children;
    }

    /**
     * Sets the transform reference.
     * Used by EditorGameObject after prefab cloning to update the cached transform.
     */
    protected void setTransformRef(Transform t) {
        this.transform = t;
    }

    /**
     * Sets the parent reference directly without modifying children lists.
     * Used by EditorGameObject for hierarchy reconstruction where children
     * lists are managed separately.
     */
    protected void setParentRef(GameObject newParent) {
        this.parent = newParent;
    }

    /**
     * Adds a component at a specific index in the component list.
     */
    protected void addComponentAt(int index, Component component) {
        component.setGameObject(this);
        components.add(index, component);
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
        Scene scene = SceneManager.getActiveScene();
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
        Scene scene = SceneManager.getActiveScene();
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

            Scene scene = SceneManager.getActiveScene();
            if (scene != null) {
                scene.registerCachedComponent(component);
            }

            // Only auto-start if this GO is part of the active scene
            // (i.e., it has been added via scene.addGameObject and the scene is initialized)
            if (scene != null && scene.getGameObjects().contains(this) && enabled) {
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

        Scene scene = SceneManager.getActiveScene();
        if (scene != null) {
            scene.registerCachedComponent(component);
        }

        // Only auto-start if this GO is part of the active scene
        // (i.e., it has been added via scene.addGameObject and the scene is initialized)
        if (scene != null && scene.getGameObjects().contains(this) && enabled) {
            component.start();
        }

        return component;
    }

    private void addComponentInternal(Component component) {
        component.setGameObject(this);
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
            component.setGameObject(null);

            Scene scene = SceneManager.getActiveScene();
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

    public List<Component> getAllComponents() {
        return new ArrayList<>(components);
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
        if (destroyed) return; // Re-entrancy guard
        destroyed = true;

        // Self-remove from the active scene
        Scene scene = SceneManager.getActiveScene();
        if (scene != null) {
            scene.removeFromScene(this);
        }

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
