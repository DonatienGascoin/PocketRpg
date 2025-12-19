package com.pocket.rpg.editor.serialization;

import java.util.Map;

/**
 * Serializable representation of a placed entity instance.
 * <p>
 * Stores only the prefab reference and overridden properties,
 * keeping scene files lightweight. The full entity is reconstructed
 * at runtime by instantiating the prefab with these overrides.
 *
 * @param prefabId   Reference to the prefab definition
 * @param name       Instance name (unique within scene)
 * @param position   World position [x, y, z]
 * @param properties Overridden property values (keys match PropertyDefinition names)
 */
public record EntityData(
        String prefabId,
        String name,
        float[] position,
        Map<String, Object> properties
) {
    /**
     * Creates EntityData with default empty properties.
     */
    public EntityData(String prefabId, String name, float[] position) {
        this(prefabId, name, position, Map.of());
    }
}
