package com.pocket.rpg.editor.scene;

import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.EntityData;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Editor-side representation of a placed entity instance.
 * <p>
 * EditorEntity stores the minimal data needed to reconstruct
 * a GameObject at runtime:
 * - Prefab reference (determines base components)
 * - Position (where to place it)
 * - Component field overrides (per-instance customization)
 * <p>
 * For prefab instances, overrides are stored per-component-type, per-field.
 * For scratch entities, components are stored directly.
 */
public class EditorEntity {

    /**
     * Unique instance ID (generated, not user-editable).
     */
    @Getter
    private final String id;

    /**
     * Reference to the prefab definition.
     * Null for scratch entities.
     */
    @Getter
    private String prefabId;

    /**
     * Display name (editable by user).
     */
    @Getter
    @Setter
    private String name;

    /**
     * World position.
     */
    private final Vector3f position;

    /**
     * Component data for scratch entities (when prefabId is null).
     */
    private List<ComponentData> components;

    /**
     * Component field overrides for prefab instances.
     * Structure: componentType -> (fieldName -> value)
     * Only stores values different from prefab defaults.
     */
    private Map<String, Map<String, Object>> componentOverrides;

    // ========================================================================
    // CACHED PREVIEW DATA (not serialized)
    // ========================================================================

    private transient Sprite previewSprite;
    private transient Vector2f previewSize;
    private transient boolean previewCached = false;

    private static final float DEFAULT_PPU = 16f;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates a new prefab instance.
     *
     * @param prefabId Reference to the prefab
     * @param position World position
     */
    public EditorEntity(String prefabId, Vector3f position) {
        this.id = generateId();
        this.prefabId = prefabId;
        this.name = prefabId + "_" + id;
        this.position = new Vector3f(position);
        this.componentOverrides = new HashMap<>();
    }

    /**
     * Creates a new entity (scratch or prefab based on isPrefab flag).
     */
    public EditorEntity(String name, Vector3f position, boolean isPrefab) {
        this.id = generateId();
        this.prefabId = isPrefab ? name : null;
        this.name = isPrefab ? (name + "_" + id) : name;
        this.position = new Vector3f(position);

        if (isPrefab) {
            this.componentOverrides = new HashMap<>();
        } else {
            this.components = new ArrayList<>();
        }
    }

    /**
     * Private constructor for deserialization.
     */
    private EditorEntity(String id, String prefabId, String name, Vector3f position,
                         List<ComponentData> components,
                         Map<String, Map<String, Object>> componentOverrides) {
        this.id = id;
        this.prefabId = prefabId;
        this.name = name;
        this.position = new Vector3f(position);
        this.components = components;
        this.componentOverrides = componentOverrides != null ? new HashMap<>(componentOverrides) : new HashMap<>();
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ========================================================================
    // POSITION
    // ========================================================================

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public Vector3f getPositionRef() {
        return position;
    }

    public void setPosition(float x, float y) {
        position.set(x, y, position.z);
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public void setPosition(Vector3f pos) {
        position.set(pos);
    }

    // ========================================================================
    // ENTITY TYPE CHECKS
    // ========================================================================

    public boolean isScratchEntity() {
        return prefabId == null || prefabId.isEmpty();
    }

    public boolean isPrefabInstance() {
        return prefabId != null && !prefabId.isEmpty();
    }

    // ========================================================================
    // SCRATCH ENTITY COMPONENT MANAGEMENT
    // ========================================================================

    /**
     * Gets the component data list for scratch entities.
     * For prefab instances, returns the prefab's components merged with overrides.
     */
    public List<ComponentData> getComponents() {
        if (isScratchEntity()) {
            if (components == null) {
                components = new ArrayList<>();
            }
            return components;
        } else {
            // For prefab instances, return merged view
            return getMergedComponents();
        }
    }

    /**
     * Gets components with overrides applied (for prefab instances).
     */
    private List<ComponentData> getMergedComponents() {
        Prefab prefab = getPrefab();
        if (prefab == null) {
            return new ArrayList<>();
        }

        List<ComponentData> result = new ArrayList<>();
        for (ComponentData baseComp : prefab.getComponents()) {
            ComponentData merged = new ComponentData(baseComp.getType());
            merged.getFields().putAll(baseComp.getFields());

            // Apply overrides
            Map<String, Object> overrides = componentOverrides.get(baseComp.getType());
            if (overrides != null) {
                merged.getFields().putAll(overrides);
            }

            result.add(merged);
        }
        return result;
    }

    /**
     * Adds a component to this scratch entity.
     *
     * @throws IllegalStateException if this is a prefab instance
     */
    public void addComponent(ComponentData componentData) {
        if (isPrefabInstance()) {
            throw new IllegalStateException(
                    "Cannot add components to prefab instance. Convert to scratch entity first.");
        }
        getComponents().add(componentData);
    }

    /**
     * Removes a component from this scratch entity.
     */
    public boolean removeComponent(ComponentData componentData) {
        if (components == null) {
            return false;
        }
        return components.remove(componentData);
    }

    /**
     * Gets a component by its simple type name.
     */
    public ComponentData getComponentByType(String simpleName) {
        for (ComponentData comp : getComponents()) {
            if (comp.getSimpleName().equals(simpleName)) {
                return comp;
            }
        }
        return null;
    }

    public boolean hasComponent(String simpleName) {
        return getComponentByType(simpleName) != null;
    }

    // ========================================================================
    // PREFAB INSTANCE FIELD OVERRIDES
    // ========================================================================

    /**
     * Gets a field value from a component.
     * For prefab instances, returns override if set, otherwise prefab default.
     *
     * @param componentType Full class name of the component
     * @param fieldName     Name of the field
     */
    public Object getFieldValue(String componentType, String fieldName) {
        if (isScratchEntity()) {
            ComponentData comp = findComponentByType(componentType);
            return comp != null ? comp.getFields().get(fieldName) : null;
        }

        // Check overrides first
        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides != null && overrides.containsKey(fieldName)) {
            return overrides.get(fieldName);
        }

        // Fall back to prefab default
        Prefab prefab = getPrefab();
        return prefab != null ? prefab.getFieldDefault(componentType, fieldName) : null;
    }

    /**
     * Sets a field value on a component.
     * For prefab instances, stores as override.
     *
     * @param componentType Full class name of the component
     * @param fieldName     Name of the field
     * @param value         New value
     */
    public void setFieldValue(String componentType, String fieldName, Object value) {
        if (isScratchEntity()) {
            ComponentData comp = findComponentByType(componentType);
            if (comp != null) {
                comp.getFields().put(fieldName, value);
            }
            return;
        }

        // For prefab instances, store as override
        componentOverrides.computeIfAbsent(componentType, k -> new HashMap<>())
                .put(fieldName, value);
    }

    /**
     * Checks if a field is overridden from the prefab default.
     */
    public boolean isFieldOverridden(String componentType, String fieldName) {
        if (isScratchEntity()) {
            return false;
        }

        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides == null || !overrides.containsKey(fieldName)) {
            return false;
        }

        Object currentValue = overrides.get(fieldName);
        Object defaultValue = getFieldDefault(componentType, fieldName);

        if (currentValue == null && defaultValue == null) {
            return false;
        }
        if (currentValue == null || defaultValue == null) {
            return true;
        }
        return !currentValue.equals(defaultValue);
    }

    /**
     * Gets the default value for a field from the prefab.
     */
    public Object getFieldDefault(String componentType, String fieldName) {
        Prefab prefab = getPrefab();
        return prefab != null ? prefab.getFieldDefault(componentType, fieldName) : null;
    }

    /**
     * Resets a field to its prefab default value.
     */
    public void resetFieldToDefault(String componentType, String fieldName) {
        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides != null) {
            overrides.remove(fieldName);
            if (overrides.isEmpty()) {
                componentOverrides.remove(componentType);
            }
        }
    }

    /**
     * Gets all overridden fields for a component.
     * Returns list of field names that differ from defaults.
     */
    public List<String> getOverriddenFields(String componentType) {
        List<String> result = new ArrayList<>();

        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides == null) {
            return result;
        }

        for (String fieldName : overrides.keySet()) {
            if (isFieldOverridden(componentType, fieldName)) {
                result.add(fieldName);
            }
        }
        return result;
    }

    /**
     * Resets all overrides for this prefab instance.
     */
    public void resetAllOverrides() {
        componentOverrides.clear();
    }

    /**
     * Gets the total count of overridden fields across all components.
     */
    public int getOverrideCount() {
        int count = 0;
        for (String componentType : componentOverrides.keySet()) {
            count += getOverriddenFields(componentType).size();
        }
        return count;
    }

    private ComponentData findComponentByType(String componentType) {
        if (components == null) {
            return null;
        }
        for (ComponentData comp : components) {
            if (comp.getType().equals(componentType)) {
                return comp;
            }
        }
        return null;
    }

    // ========================================================================
    // PREVIEW CACHING
    // ========================================================================

    public Sprite getPreviewSprite() {
        ensurePreviewCached();
        return previewSprite;
    }

    public Vector2f getPreviewSize() {
        ensurePreviewCached();
        return previewSize;
    }

    public void refreshPreviewCache() {
        previewCached = false;
        ensurePreviewCached();
    }

    private void ensurePreviewCached() {
        if (previewCached) {
            return;
        }

        previewSprite = PrefabRegistry.getInstance().getPreviewSprite(prefabId);

        if (previewSprite != null) {
            previewSize = new Vector2f(
                    previewSprite.getWidth() / DEFAULT_PPU,
                    previewSprite.getHeight() / DEFAULT_PPU
            );
        } else {
            previewSize = new Vector2f(1f, 1f);
        }

        previewCached = true;
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    public EntityData toData() {
        if (isPrefabInstance()) {
            return new EntityData(
                    prefabId,
                    name,
                    new float[]{position.x, position.y, position.z},
                    copyOverrides(componentOverrides)
            );
        } else {
            return new EntityData(
                    name,
                    new float[]{position.x, position.y, position.z},
                    components != null ? new ArrayList<>(components) : new ArrayList<>()
            );
        }
    }

    public static EditorEntity fromData(EntityData data) {
        if (data == null) {
            throw new IllegalArgumentException("EntityData cannot be null");
        }

        float[] pos = data.getPosition();
        Vector3f position = new Vector3f(
                pos != null && pos.length > 0 ? pos[0] : 0,
                pos != null && pos.length > 1 ? pos[1] : 0,
                pos != null && pos.length > 2 ? pos[2] : 0
        );

        if (data.isPrefabInstance()) {
            EditorEntity entity = new EditorEntity(data.getPrefabId(), position);
            entity.name = data.getName() != null ? data.getName() : entity.name;

            if (data.getComponentOverrides() != null) {
                entity.componentOverrides = copyOverrides(data.getComponentOverrides());
            }

            return entity;
        } else {
            String name = data.getName() != null ? data.getName() : "Entity";
            EditorEntity entity = new EditorEntity(name, position, false);

            if (data.getComponents() != null) {
                entity.components = new ArrayList<>(data.getComponents());
            }

            return entity;
        }
    }

    private static Map<String, Map<String, Object>> copyOverrides(Map<String, Map<String, Object>> source) {
        if (source == null) {
            return new HashMap<>();
        }
        Map<String, Map<String, Object>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    public Prefab getPrefab() {
        return PrefabRegistry.getInstance().getPrefab(prefabId);
    }

    public boolean isPrefabValid() {
        return PrefabRegistry.getInstance().hasPrefab(prefabId);
    }

    @Override
    public String toString() {
        return String.format("EditorEntity[id=%s, prefab=%s, name=%s, pos=(%.1f,%.1f)]",
                id, prefabId, name, position.x, position.y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EditorEntity other = (EditorEntity) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
