package com.pocket.rpg.components;

import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.IGameObject;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
import com.pocket.rpg.editor.gizmos.GizmoDrawableSelected;
import com.pocket.rpg.logging.Log;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * Base class for all components that can be attached to GameObjects.
 * Components define behavior and data for GameObjects.
 * <p>
 * Components can override {@link #onDrawGizmos} and {@link #onDrawGizmosSelected}
 * to visualize their properties in the editor scene view.
 */
public abstract class Component implements GizmoDrawable, GizmoDrawableSelected {

    /**
     * The owning game object. Works in both runtime and editor contexts.
     */
    @Getter
    protected IGameObject owner;

    /**
     * Use {@link #owner} via {@link #getOwner()} instead.
     */
    @Getter
    protected GameObject gameObject;

    /**
     * Optional key for referencing this component from other components via
     * {@code @ComponentReference(source = Source.KEY)}. Null by default.
     * Only serialized when non-null.
     */
    @Getter @Setter
    protected String componentKey;

    protected boolean enabled = true;

    @Getter
    protected boolean started = false;

    public boolean isEnabled() {
        return enabled && owner != null && owner.isEnabled();
    }

    /**
     * Returns the component's own enabled state without checking the parent hierarchy.
     * Use this when you need the raw field value (e.g., serialization, inspector UI).
     * Use {@link #isEnabled()} for runtime behavior checks.
     */
    public boolean isOwnEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled state of the component.
     * Triggers onEnable() or onDisable() callbacks when state changes.
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        // Only trigger callbacks if owner is also enabled and component has started
        if (owner != null && owner.isEnabled() && started) {
            try {
                if (enabled) {
                    onEnable();
                } else {
                    onDisable();
                }
            } catch (Exception e) {
                Log.error(logTag(), enabled ? "onEnable() failed" : "onDisable() failed", e);
            }
        }
    }

    /**
     * Sets the owning game object. Works with both runtime and editor objects.
     */
    public void setOwner(IGameObject owner) {
        this.owner = owner;
        this.gameObject = (owner instanceof GameObject go) ? go : null;
    }

    /**
     * @deprecated Use {@link #setOwner(IGameObject)} instead.
     */
    @Deprecated
    public void setGameObject(GameObject gameObject) {
        setOwner(gameObject);
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
            try {
                onEnable();
            } catch (Exception e) {
                Log.error(logTag(), "onEnable() failed", e);
            }
        }
    }

    /**
     * Internal method called by GameObject when GameObject is disabled.
     * Triggers onDisable() if conditions are met.
     */
    public void triggerDisable() {
        if (started && enabled) {
            try {
                onDisable();
            } catch (Exception e) {
                Log.error(logTag(), "onDisable() failed", e);
            }
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
            try {
                onStart();
            } catch (Exception e) {
                Log.error(logTag(), "onStart() failed", e);
            }
            started = true;

            // Trigger onEnable after start if component is enabled
            if (enabled && owner != null && owner.isEnabled()) {
                try {
                    onEnable();
                } catch (Exception e) {
                    Log.error(logTag(), "onEnable() failed after start", e);
                }
            }
        }
    }

    /**
     * Called when the component is destroyed.
     * This method is final - override onDestroy() instead.
     */
    public final void destroy() {
        if (enabled && started) {
            try {
                onDisable();
            } catch (Exception e) {
                Log.error(logTag(), "onDisable() failed during destroy", e);
            }
        }
        try {
            onDestroy();
        } catch (Exception e) {
            Log.error(logTag(), "onDestroy() failed", e);
        }
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
    // Gizmos (Editor Visualization)
    // =======================================================================

    /**
     * Called every frame in the editor to draw gizmos for this component.
     * Gizmos drawn here are visible for ALL entities, not just selected ones.
     * <p>
     * Override this to visualize component properties that should always be visible,
     * such as trigger zones, waypoint connections, or debug information.
     *
     * @param ctx The gizmo drawing context
     */
    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        // Override in subclasses
    }

    /**
     * Called every frame in the editor to draw gizmos when this entity is selected.
     * <p>
     * Override this to visualize component properties that should only appear
     * when editing, such as pivot points, bounds, collision shapes, or radii.
     *
     * @param ctx The gizmo drawing context
     */
    @Override
    public void onDrawGizmosSelected(GizmoContext ctx) {
        // Override in subclasses
    }

    // =======================================================================
    // Helper Methods
    // =======================================================================

    /**
     * Returns a log tag identifying this component and its owner for error messages.
     * Format: "ClassName(OwnerName)" or "ClassName(?)" if no owner.
     */
    public String logTag() {
        String ownerName = owner != null ? owner.getName() : "?";
        return getClass().getSimpleName() + "(" + ownerName + ")";
    }

    protected Transform getTransform() {
        return owner != null ? owner.getTransform() : null;
    }

    /**
     * Gets a sibling component of the specified type.
     * Works in both runtime and editor contexts.
     */
    protected <T extends Component> T getComponent(Class<T> type) {
        return owner != null ? owner.getComponent(type) : null;
    }

    /**
     * Gets all sibling components of the specified type.
     */
    protected <T extends Component> List<T> getComponents(Class<T> type) {
        return owner != null ? owner.getComponents(type) : Collections.emptyList();
    }
}