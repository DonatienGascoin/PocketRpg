package com.pocket.rpg.engine;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.scenes.Scene;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * GameObject is the fundamental entity in the game.
 * It holds components that define its behavior and properties.
 * 
 * FIXED: Now supports safe component add/remove during update loop
 */
public class GameObject {
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private boolean enabled = true;
    @Setter
    @Getter
    private Scene scene;

    private List<Component> components;
    @Getter
    private Transform transform;
    @Getter
    private boolean started = false;

    // FIX: Deferred execution for safe add/remove during update
    private Queue<Runnable> deferredActions = new LinkedList<>();
    private boolean isUpdating = false;

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
     * Safe to call during update() - will be executed after update completes.
     * Note: Transform cannot be added this way as it's automatically created.
     */
    public <T extends Component> T addComponent(T component) {
        if (component instanceof Transform) {
            System.err.println("Cannot add Transform component - it's automatically created!");
            return component;
        }

        if (isUpdating) {
            // Defer execution until after update
            deferredActions.add(() -> addComponentImmediate(component));
        } else {
            addComponentImmediate(component);
        }

        return component;
    }

    /**
     * Internal method to immediately add a component.
     */
    private <T extends Component> void addComponentImmediate(T component) {
        addComponentInternal(component);

        // FIX: Call start() if GameObject is already started and component is enabled
        if (started && component.isEnabled()) {
            component.start();
        }

        // If adding to an active scene, notify the scene
        if (scene != null && component instanceof SpriteRenderer) {
            scene.registerSpriteRenderer((SpriteRenderer) component);
        }
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
     * Safe to call during update() - will be executed after update completes.
     */
    public void removeComponent(Component component) {
        if (component == transform) {
            System.err.println("Cannot remove Transform component!");
            return;
        }

        if (isUpdating) {
            // Defer execution until after update
            deferredActions.add(() -> removeComponentImmediate(component));
        } else {
            removeComponentImmediate(component);
        }
    }

    /**
     * Internal method to immediately remove a component.
     */
    private void removeComponentImmediate(Component component) {
        if (!components.contains(component)) {
            return; // Already removed
        }

        // FIX: Unregister from scene BEFORE destroying
        if (scene != null && component instanceof SpriteRenderer) {
            scene.unregisterSpriteRenderer((SpriteRenderer) component);
        }

        // Then remove and destroy
        if (components.remove(component)) {
            component.destroy();
            component.setGameObject(null);
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

    // Lifecycle Methods

    /**
     * Called when the GameObject is added to a scene.
     */
    public void start() {
        if (started) {
            return; // Already started
        }

        for (Component component : components) {
            if (component.isEnabled()) {
                component.start();
            }
        }

        started = true;
    }

    /**
     * Called every frame.
     * FIX: Now uses safe iteration to prevent ConcurrentModificationException
     */
    public void update(float deltaTime) {
        if (!enabled) return;

        isUpdating = true;

        // FIX: Create copy to avoid ConcurrentModificationException
        List<Component> componentsToUpdate = new ArrayList<>(components);
        
        for (Component component : componentsToUpdate) {
            // Check if component is still in list (might have been removed)
            if (!components.contains(component)) {
                continue;
            }

            if (component.isEnabled()) {
                // Ensure start is called before first update
                if (!component.isStarted()) {
                    component.start();
                }
                component.update(deltaTime);
            }
        }

        isUpdating = false;

        // FIX: Process deferred actions after update completes
        processDeferredActions();
    }

    /**
     * Processes all deferred actions accumulated during update.
     */
    private void processDeferredActions() {
        while (!deferredActions.isEmpty()) {
            Runnable action = deferredActions.poll();
            action.run();
        }
    }

    /**
     * Called when the GameObject is destroyed.
     */
    public void destroy() {
        // Create copy to avoid modification during iteration
        List<Component> componentsToDestroy = new ArrayList<>(components);
        for (Component component : componentsToDestroy) {
            component.destroy();
        }
        components.clear();
        deferredActions.clear();
    }
}
