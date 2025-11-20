package com.pocket.rpg.components;

import com.pocket.rpg.engine.GameObject;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all components that can be attached to GameObjects.
 * Components define behavior and data for GameObjects.
 */
public abstract class Component {
    /**
     * -- SETTER --
     *  Called when the component is added to a GameObject.
     */
    @Getter
    @Setter
    protected GameObject gameObject;
    @Setter
    protected boolean enabled = true;
    @Getter
    protected boolean started = false;

    public boolean isEnabled() {
        return enabled && gameObject != null && gameObject.isEnabled();
    }

    /**
     * Called when the component is first initialized.
     * Only called once when the component becomes active.
     */
    public void startInternal() {
        // Override in subclasses
    }

    /**
     * Called every frame if the component is enabled.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public void update(float deltaTime) {
        // Override in subclasses
    }

    /**
     * Called when the component is destroyed.
     */
    public void destroy() {
        // Override in subclasses
    }

    /**
     * Internal method to ensure start() is only called once.
     */
    void start() {
        if (!started) {
            startInternal();
            started = true;
        }
    }
}