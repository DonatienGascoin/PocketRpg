package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable representation of a GameObject.
 * <p>
 * Preserves hierarchy by nesting children within parent objects.
 * Components are stored as a list of polymorphic Component objects.
 * <p>
 * Note: The Transform component is included in the components list like any other.
 */
@Setter
@Getter
public class GameObjectData {

    /**
     * Unique name within the scene
     */
    private String name;

    /**
     * Whether the GameObject is active
     */
    private boolean active = true;

    /**
     * Tag for grouping/identification
     */
    private String tag;

    /**
     * All components attached to this GameObject
     */
    private List<Component> components = new ArrayList<>();

    /**
     * Child GameObjects (for hierarchy preservation)
     */
    private List<GameObjectData> children = new ArrayList<>();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public GameObjectData() {
    }

    public GameObjectData(String name) {
        this.name = name;
    }

    // ========================================================================
    // GETTERS / SETTERS
    // ========================================================================

    public void addComponent(Component component) {
        this.components.add(component);
    }

    public void addChild(GameObjectData child) {
        this.children.add(child);
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Finds a component by type in this GameObject's components.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> type) {
        for (Component component : components) {
            if (type.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }

    /**
     * Checks if this GameObject has a component of the given type.
     */
    public boolean hasComponent(Class<? extends Component> type) {
        return getComponent(type) != null;
    }

    @Override
    public String toString() {
        return "GameObjectData{" +
                "name='" + name + '\'' +
                ", components=" + components.size() +
                ", children=" + children.size() +
                '}';
    }
}
