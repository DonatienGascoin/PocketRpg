package com.pocket.rpg.editor.serialization;

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
 * MIGRATION NOTE: This was previously a record. Changed to class
 * to support scratch entities with components.
 */
@Getter
@Setter
@NoArgsConstructor
public class EntityData {

    // Common fields
    private String name;
    private float[] position;

    // Prefab instance fields (when prefabId is set)
    private String prefabId;
    private Map<String, Object> properties;

    // Scratch entity fields (when prefabId is null/empty)
    private List<ComponentData> components;

    /**
     * Constructor for prefab instances (backward compatible).
     */
    public EntityData(String prefabId, String name, float[] position, Map<String, Object> properties) {
        this.prefabId = prefabId;
        this.name = name;
        this.position = position;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
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

    // ========================================================================
    // BACKWARD COMPATIBILITY - Record-style accessors
    // ========================================================================
    // These methods match the old record accessor names so existing code
    // like `data.prefabId()` still compiles. You can remove these later
    // once you've updated all call sites to use `data.getPrefabId()`.

    public String prefabId() {
        return prefabId;
    }

    public String name() {
        return name;
    }

    public float[] position() {
        return position;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}