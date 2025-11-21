package com.pocket.rpg.components;

import com.pocket.rpg.engine.GameObject;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all components that can be attached to GameObjects.
 * Components define behavior and data for GameObjects.
 */
public abstract class Component {

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

    // =======================================================================
    // Workflow
    // =======================================================================

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
     * Called when the component is first initialized.
     * Only called once when the component becomes active.
     */
    public void start() {
        if (!started) {
            startInternal();
            started = true;
        }
    }

    protected void startInternal() {
        // Override in subclasses
    }
}