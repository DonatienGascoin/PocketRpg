package com.pocket.rpg.editor.serialization;

import com.pocket.rpg.serialization.ComponentData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable data for an entity.
 * Supports both prefab instances and scratch entities.
 * <p>
 * For prefab instances:
 * - prefabId: reference to the prefab
 * - componentOverrides: field overrides per component type
 * <p>
 * For scratch entities:
 * - components: inline component definitions
 */
@Getter
@Setter
@NoArgsConstructor
public class EntityData {

    /**
     * Unique entity ID (preserved across save/load).
     */
    @Getter
    @Setter
    private String id;

    /**
     * Parent entity ID for hierarchy (null for root entities).
     */
    @Getter
    @Setter
    private String parentId;

    /**
     * Sibling order (lower = earlier in list).
     */
    @Getter
    @Setter
    private int order;

    // Common fields
    private String name;
    private float[] position;

    // Prefab instance fields (when prefabId is set)
    private String prefabId;
    /**
     * Component field overrides.
     * Structure: componentType -> (fieldName -> value)
     */
    private Map<String, Map<String, Object>> componentOverrides;

    // Scratch entity fields (when prefabId is null/empty)
    private List<ComponentData> components;

    /**
     * Constructor for prefab instances.
     */
    public EntityData(String prefabId, String name, float[] position,
                      Map<String, Map<String, Object>> componentOverrides) {
        this.prefabId = prefabId;
        this.name = name;
        this.position = position;
        this.componentOverrides = componentOverrides != null
                ? deepCopyOverrides(componentOverrides)
                : new HashMap<>();
    }

    /**
     * Constructor for scratch entities.
     */
    public EntityData(String name, float[] position, List<ComponentData> components) {
        this.prefabId = null;
        this.name = name;
        this.position = position;
        this.components = components != null ? new ArrayList<>(components) : new ArrayList<>();
    }

    /**
     * Checks if this is a scratch entity (no prefab reference).
     */
    public boolean isScratchEntity() {
        return prefabId == null || prefabId.isEmpty();
    }

    /**
     * Checks if this is a prefab instance.
     */
    public boolean isPrefabInstance() {
        return prefabId != null && !prefabId.isEmpty();
    }

    private static Map<String, Map<String, Object>> deepCopyOverrides(
            Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    // ========================================================================
    // BACKWARD COMPATIBILITY - Record-style accessors
    // ========================================================================

    public String prefabId() {
        return prefabId;
    }

    public String name() {
        return name;
    }

    public float[] position() {
        return position;
    }
}
