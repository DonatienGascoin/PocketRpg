package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;

import java.util.List;

/**
 * Common interface for game object types.
 * Implemented by both runtime {@link GameObject} and editor {@code EditorGameObject}.
 * <p>
 * Allows {@link Component} to access transform and sibling components
 * without knowing whether it's in runtime or editor context.
 */
public interface IGameObject {

    // ========================================================================
    // IDENTITY
    // ========================================================================

    /**
     * Returns the name of this game object.
     */
    String getName();

    /**
     * Returns a unique identifier for this game object.
     * For GameObject: name or identity hash.
     * For EditorGameObject: the entity ID.
     */
    String getId();

    // ========================================================================
    // TRANSFORM
    // ========================================================================

    /**
     * Returns the Transform component.
     * Always non-null for runtime objects. May be null for editor objects
     * in rare edge cases (missing prefab).
     */
    Transform getTransform();

    // ========================================================================
    // COMPONENT ACCESS
    // ========================================================================

    /**
     * Gets the first component of the specified type.
     *
     * @param type The component class to find
     * @return The component, or null if not found
     */
    <T extends Component> T getComponent(Class<T> type);

    /**
     * Gets all components of the specified type.
     *
     * @param type The component class to find
     * @return List of matching components (may be empty)
     */
    <T extends Component> List<T> getComponents(Class<T> type);

    /**
     * Gets all components attached to this game object.
     */
    List<Component> getAllComponents();

    /**
     * Checks if this game object has a component of the specified type.
     */
    default boolean hasComponent(Class<? extends Component> type) {
        return getComponent(type) != null;
    }

    // ========================================================================
    // STATE
    // ========================================================================

    /**
     * Returns whether this game object is enabled.
     * For EditorGameObject: always returns true.
     */
    boolean isEnabled();

    // ========================================================================
    // CONTEXT
    // ========================================================================

    /**
     * Returns true if this is a runtime game object (in a running scene).
     * <p>
     * The default uses {@code instanceof GameObject}, which is correct for
     * {@code GameObject} and {@code EditorGameObject} but <b>not</b> for
     * wrappers/adapters around runtime objects (e.g. {@code RuntimeGameObjectAdapter}).
     * Such implementations must override this method to return {@code true}.
     */
    default boolean isRuntime() {
        return this instanceof GameObject;
    }

    /**
     * Returns true if this is an editor game object (in the scene editor).
     */
    default boolean isEditor() {
        return !isRuntime();
    }
}
