package com.pocket.rpg.components;

import com.pocket.rpg.core.GameObject;
import lombok.Getter;

/**
 * Base class for all components that can be attached to GameObjects.
 * Components define behavior and data for GameObjects.
 */
public abstract class Component {

    /** NOT AVAILABLE IN EDITOR */
    @Getter
    protected GameObject gameObject;

    protected boolean enabled = true;

    @Getter
    protected boolean started = false;

    public boolean isEnabled() {
        return enabled && gameObject != null && gameObject.isEnabled();
    }

    /**
     * Sets the enabled state of the component.
     * Triggers onEnable() or onDisable() callbacks when state changes.
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        // Only trigger callbacks if GameObject is also enabled and component has started
        if (gameObject != null && gameObject.isEnabled() && started) {
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    /**
     * Internal method used by GameObject to set the reference.
     */
    public void setGameObject(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    // =======================================================================
    // Internal Lifecycle Triggers (called by GameObject)
    // =======================================================================

    /**
     * Internal method called by GameObject when GameObject is enabled.
     * Triggers onEnable() if conditions are met.
     */
    public void triggerEnable() {
        if (started && enabled) {
            onEnable();
        }
    }

    /**
     * Internal method called by GameObject when GameObject is disabled.
     * Triggers onDisable() if conditions are met.
     */
    public void triggerDisable() {
        if (started && enabled) {
            onDisable();
        }
    }

    // =======================================================================
    // Lifecycle Methods (Final - cannot be overridden)
    // =======================================================================

    /**
     * Called when the component is first initialized.
     * Only called once when the component becomes active.
     * This method is final - override onStart() instead.
     */
    public final void start() {
        if (!started) {
            onStart();
            started = true;

            // Trigger onEnable after start if component is enabled
            if (enabled && gameObject != null && gameObject.isEnabled()) {
                onEnable();
            }
        }
    }

    /**
     * Called when the component is destroyed.
     * This method is final - override onDestroy() instead.
     */
    public final void destroy() {
        if (enabled && started) {
            onDisable();
        }
        onDestroy();
    }

    // =======================================================================
    // Lifecycle Hooks (Override these in subclasses)
    // =======================================================================

    /**
     * Called once when the component is first initialized.
     * Use this for one-time setup.
     */
    protected void onStart() {
        // Override in subclasses
    }

    /**
     * Called when the component becomes enabled.
     * This can be called multiple times if the component is disabled and re-enabled.
     */
    protected void onEnable() {
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
     * Called every frame after all update() calls, if the component is enabled.
     * Use this for operations that depend on other components' updates being complete.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public void lateUpdate(float deltaTime) {
        // Override in subclasses
    }

    /**
     * Called when the GameObject's transform changes.
     * Override to respond to position, rotation, or scale changes.
     */
    public void onTransformChanged() {
        // Override in subclasses if needed
    }

    /**
     * Called when the component becomes disabled.
     * This can be called multiple times if the component is disabled and re-enabled.
     */
    protected void onDisable() {
        // Override in subclasses
    }

    /**
     * Called when the component is destroyed.
     * Use this for final cleanup and resource disposal.
     */
    protected void onDestroy() {
        // Override in subclasses
    }

    // =======================================================================
    // Helper Methods
    // =======================================================================

    protected Transform getTransform() {
        return gameObject.getTransform();
    }
}