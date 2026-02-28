package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable representation of a GameObject.
 * <p>
 * Supports both prefab instances and scratch entities:
 * <ul>
 *   <li>Prefab instance: prefab (asset path) + componentOverrides + childOverrides</li>
 *   <li>Scratch entity: components list (includes Transform)</li>
 * </ul>
 * <p>
 * Position, rotation, and scale are stored in the Transform component,
 * not as separate fields. For prefab instances, transform values
 * go in componentOverrides.
 * <p>
 * Hierarchy is stored via parentId references (resolved after loading).
 */
@Getter
@Setter
@NoArgsConstructor
public class GameObjectData {

    private static final String TRANSFORM_TYPE = "com.pocket.rpg.components.core.Transform";

    // ========================================================================
    // IDENTITY
    // ========================================================================

    /**
     * Unique ID (preserved across save/load).
     */
    private String id;

    /**
     * Display name.
     */
    private String name;

    /**
     * Optional tag for grouping/identification.
     */
    private String tag;

    /**
     * Whether the GameObject is active.
     */
    private boolean active = true;

    // ========================================================================
    // HIERARCHY
    // ========================================================================

    /**
     * Parent entity ID (null for root entities).
     */
    private String parentId;

    /**
     * Sibling order (lower = earlier in list).
     */
    private int order;

    /**
     * Child entity snapshots.
     * Scene files use flat lists with parentId references instead.
     */
    private List<GameObjectData> children;

    // ========================================================================
    // PREFAB INSTANCE
    // ========================================================================

    /**
     * Asset path to the prefab file (e.g. "gameData/prefabs/guard_tower.prefab").
     * Null for scratch entities. Replaces the old prefabId field.
     */
    private String prefab;

    /**
     * Reference to prefab template by ID (null for scratch entities).
     * Used at runtime to resolve the prefab from PrefabRegistry.
     * Legacy format: the primary prefab reference.
     * New format: derived from the loaded prefab (prefab path is preferred).
     */
    private String prefabId;

    /**
     * Identifies which node within the prefab hierarchy this entity represents.
     * Null for root prefab instances and scratch entities.
     * Only used in the legacy serialization format (new format uses childOverrides).
     */
    private String prefabNodeId;

    /**
     * Component field overrides for prefab instances.
     * Structure: componentType -> (fieldName -> value)
     * <p>
     * Transform overrides go here too:
     * "com.pocket.rpg.components.core.Transform" -> {"localPosition": [x, y, z]}
     */
    private Map<String, Map<String, Object>> componentOverrides;

    /**
     * Per-child-node overrides for prefab instances with hierarchy.
     * Structure: nodeId -> ChildNodeOverrides
     * <p>
     * Children without overrides are omitted (instantiated with prefab defaults).
     */
    private Map<String, ChildNodeOverrides> childOverrides;

    // ========================================================================
    // SCRATCH ENTITY (when prefabId is null)
    // ========================================================================

    /**
     * All components including Transform.
     * Only used for scratch entities (prefabId == null).
     */
    private List<Component> components;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Constructor for scratch entities.
     */
    public GameObjectData(String id, String name, List<Component> components) {
        this.id = id;
        this.name = name;
        this.prefabId = null;
        this.components = components != null ? new ArrayList<>(components) : new ArrayList<>();
    }

    /**
     * Constructor for prefab instances.
     */
    public GameObjectData(String id, String name, String prefabId,
                          Map<String, Map<String, Object>> componentOverrides) {
        this.id = id;
        this.name = name;
        this.prefabId = prefabId;
        this.componentOverrides = componentOverrides != null
                ? deepCopyOverrides(componentOverrides)
                : new HashMap<>();
    }

    // ========================================================================
    // TYPE CHECKS
    // ========================================================================

    /**
     * Checks if this is a scratch entity (no prefab reference).
     */
    public boolean isScratchEntity() {
        return !isPrefabInstance();
    }

    /**
     * Checks if this is a prefab instance.
     * True if either the new-format 'prefab' path or legacy 'prefabId' is set.
     */
    public boolean isPrefabInstance() {
        return (prefab != null && !prefab.isEmpty())
                || (prefabId != null && !prefabId.isEmpty());
    }

    // ========================================================================
    // COMPONENT ACCESS
    // ========================================================================

    /**
     * Adds a component (scratch entities only).
     */
    public void addComponent(Component component) {
        if (components == null) {
            components = new ArrayList<>();
        }
        components.add(component);
    }

    /**
     * Finds a component by type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> type) {
        if (components == null) return null;
        for (Component component : components) {
            if (type.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }

    /**
     * Checks if this has a component of the given type.
     */
    public boolean hasComponent(Class<? extends Component> type) {
        return getComponent(type) != null;
    }

    // ========================================================================
    // TRANSFORM CONVENIENCE
    // ========================================================================

    /**
     * Gets the Transform component (scratch entities only).
     */
    public Transform getTransform() {
        return getComponent(Transform.class);
    }

    /**
     * Gets position from Transform component or overrides.
     * Returns [0,0,0] if not found.
     */
    public float[] getPosition() {
        if (isScratchEntity()) {
            Transform transform = getTransform();
            if (transform != null) {
                var pos = transform.getPosition();
                return new float[]{pos.x, pos.y, pos.z};
            }
        } else {
            // Check overrides for prefab instance
            if (componentOverrides != null) {
                var transformOverrides = componentOverrides.get(TRANSFORM_TYPE);
                if (transformOverrides != null && transformOverrides.containsKey("localPosition")) {
                    Object pos = transformOverrides.get("localPosition");
                    if (pos instanceof float[] arr) return arr;
                    if (pos instanceof List<?> list) {
                        return new float[]{
                                !list.isEmpty() ? ((Number) list.get(0)).floatValue() : 0,
                                list.size() > 1 ? ((Number) list.get(1)).floatValue() : 0,
                                list.size() > 2 ? ((Number) list.get(2)).floatValue() : 0
                        };
                    }
                }
            }
        }
        return new float[]{0, 0, 0};
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    private static Map<String, Map<String, Object>> deepCopyOverrides(
            Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public String toString() {
        if (isPrefabInstance()) {
            String ref = prefab != null ? prefab : prefabId;
            return String.format("GameObjectData[id=%s, name=%s, prefab=%s, overrides=%d]",
                    id, name, ref,
                    componentOverrides != null ? componentOverrides.size() : 0);
        } else {
            return String.format("GameObjectData[id=%s, name=%s, components=%d]",
                    id, name,
                    components != null ? components.size() : 0);
        }
    }

    // ========================================================================
    // CHILD NODE OVERRIDES
    // ========================================================================

    /**
     * Per-child-node overrides within a prefab instance.
     * Stores name, active, and component field overrides for a specific child node.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChildNodeOverrides {
        private String name;
        private Boolean active;
        private Map<String, Map<String, Object>> componentOverrides;
    }
}