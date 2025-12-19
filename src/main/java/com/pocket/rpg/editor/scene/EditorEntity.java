package com.pocket.rpg.editor.scene;

import com.pocket.rpg.editor.serialization.EntityData;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.prefab.PropertyDefinition;
import com.pocket.rpg.rendering.Sprite;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Editor-side representation of a placed entity instance.
 * <p>
 * EditorEntity stores the minimal data needed to reconstruct
 * a GameObject at runtime:
 * - Prefab reference (determines components and behavior)
 * - Position (where to place it)
 * - Property overrides (per-instance customization)
 * <p>
 * This class also caches preview data for efficient editor rendering.
 */
public class EditorEntity {

    /**
     * Unique instance ID (generated, not user-editable).
     */
    @Getter
    private final String id;

    /**
     * Reference to the prefab definition.
     */
    @Getter
    private final String prefabId;

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
     * Property overrides (only stores values different from prefab defaults).
     */
    private final Map<String, Object> properties;

    // ========================================================================
    // CACHED PREVIEW DATA (not serialized)
    // ========================================================================

    private transient Sprite previewSprite;
    private transient Vector2f previewSize;
    private transient boolean previewCached = false;

    // Default PPU for size calculations
    private static final float DEFAULT_PPU = 16f;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates a new entity instance.
     *
     * @param prefabId Reference to the prefab
     * @param position World position
     */
    public EditorEntity(String prefabId, Vector3f position) {
        this.id = generateId();
        this.prefabId = prefabId;
        this.name = prefabId + "_" + id;
        this.position = new Vector3f(position);
        this.properties = new HashMap<>();

        initializeDefaultProperties();
    }

    /**
     * Private constructor for deserialization.
     */
    private EditorEntity(String id, String prefabId, String name, Vector3f position, Map<String, Object> properties) {
        this.id = id;
        this.prefabId = prefabId;
        this.name = name;
        this.position = new Vector3f(position);
        this.properties = new HashMap<>(properties);
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Initializes properties with defaults from the prefab.
     */
    private void initializeDefaultProperties() {
        Prefab prefab = PrefabRegistry.getInstance().getPrefab(prefabId);
        if (prefab == null) {
            return;
        }

        for (PropertyDefinition prop : prefab.getEditableProperties()) {
            properties.put(prop.name(), prop.defaultValue());
        }
    }

    // ========================================================================
    // POSITION
    // ========================================================================

    /**
     * Gets a copy of the position.
     */
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    /**
     * Gets direct reference to position (for efficient reading).
     */
    public Vector3f getPositionRef() {
        return position;
    }

    /**
     * Sets position.
     */
    public void setPosition(float x, float y) {
        position.set(x, y, position.z);
    }

    /**
     * Sets position with Z.
     */
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    /**
     * Sets position from vector.
     */
    public void setPosition(Vector3f pos) {
        position.set(pos);
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    /**
     * Gets a property value.
     *
     * @param key Property name
     * @return Property value, or null if not set
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Gets a property value with type casting.
     *
     * @param key  Property name
     * @param type Expected type
     * @return Property value cast to type, or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Sets a property value.
     *
     * @param key   Property name
     * @param value Property value
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * Gets a copy of all properties.
     */
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    /**
     * Checks if a property is set.
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    // ========================================================================
    // PREVIEW CACHING
    // ========================================================================

    /**
     * Gets the preview sprite for editor rendering.
     * Caches on first access.
     */
    public Sprite getPreviewSprite() {
        ensurePreviewCached();
        return previewSprite;
    }

    /**
     * Gets the preview size in world units.
     * Caches on first access.
     */
    public Vector2f getPreviewSize() {
        ensurePreviewCached();
        return previewSize;
    }

    /**
     * Forces preview cache refresh.
     * Call if the prefab's preview sprite changes.
     */
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
            previewSize = new Vector2f(1f, 1f); // Default 1x1 tile
        }

        previewCached = true;
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    /**
     * Converts to serializable EntityData.
     */
    public EntityData toData() {
        return new EntityData(
                prefabId,
                name,
                new float[]{position.x, position.y, position.z},
                new HashMap<>(properties)
        );
    }

    /**
     * Creates EditorEntity from serialized data.
     */
    public static EditorEntity fromData(EntityData data) {
        if (data == null) {
            throw new IllegalArgumentException("EntityData cannot be null");
        }

        float[] pos = data.position();
        Vector3f position = new Vector3f(
                pos != null && pos.length > 0 ? pos[0] : 0,
                pos != null && pos.length > 1 ? pos[1] : 0,
                pos != null && pos.length > 2 ? pos[2] : 0
        );

        // Generate new ID for loaded entities (ID is instance-specific)
        EditorEntity entity = new EditorEntity(data.prefabId(), position);
        entity.name = data.name() != null ? data.name() : entity.name;

        // Load properties
        if (data.properties() != null) {
            entity.properties.clear();
            entity.properties.putAll(data.properties());
        }

        return entity;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Gets the prefab definition for this entity.
     *
     * @return Prefab, or null if not registered
     */
    public Prefab getPrefab() {
        return PrefabRegistry.getInstance().getPrefab(prefabId);
    }

    /**
     * Checks if this entity's prefab is registered.
     */
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
