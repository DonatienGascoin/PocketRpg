package com.pocket.rpg.editor.scene;

import com.pocket.rpg.editor.serialization.EntityData;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentData;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Editor-side representation of a placed entity instance.
 * Supports parent-child hierarchy and sibling ordering.
 */
public class EditorEntity {

    @Getter
    private String id;

    @Getter
    private String prefabId;

    @Getter
    @Setter
    private String name;

    private final Vector3f position;
    private final Vector3f rotation;
    private final Vector3f scale;

    private List<ComponentData> components;

    private Map<String, Map<String, Object>> componentOverrides;

    @Getter
    @Setter
    private String parentId;

    @Getter
    @Setter
    private int order;

    @Getter
    private transient EditorEntity parent;

    private transient List<EditorEntity> children;

    private static final float DEFAULT_ENTITY_Z_INDEX = 100f;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public EditorEntity(String prefabId, Vector3f position) {
        this.id = generateId();
        this.prefabId = prefabId;
        this.name = prefabId + "_" + id;
        this.position = new Vector3f(position);
        this.componentOverrides = new HashMap<>();
        this.children = new ArrayList<>();
        this.order = 0;
        this.rotation = new Vector3f(0);
        this.scale = new Vector3f(1);
    }

    public EditorEntity(String name, Vector3f position, boolean isPrefab) {
        this.id = generateId();
        this.prefabId = isPrefab ? name : null;
        this.name = isPrefab ? (name + "_" + id) : name;
        this.position = new Vector3f(position);
        this.children = new ArrayList<>();
        this.order = 0;
        this.rotation = new Vector3f(0);
        this.scale = new Vector3f(1);

        if (isPrefab) {
            this.componentOverrides = new HashMap<>();
        } else {
            this.components = new ArrayList<>();
        }
    }

    private EditorEntity(String id, String prefabId, String name, Vector3f position,
                         List<ComponentData> components,
                         Map<String, Map<String, Object>> componentOverrides,
                         String parentId, int order) {
        this.id = (id != null && !id.isEmpty()) ? id : generateId();
        this.prefabId = prefabId;
        this.name = name;
        this.position = new Vector3f(position);
        this.rotation = new Vector3f(0);
        this.scale = new Vector3f(1);
        this.components = components;
        this.componentOverrides = componentOverrides != null ? new HashMap<>(componentOverrides) : new HashMap<>();
        this.parentId = parentId;
        this.order = order;
        this.children = new ArrayList<>();
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ========================================================================
    // HIERARCHY MANAGEMENT
    // ========================================================================

    public void setParent(EditorEntity newParent) {
        if (newParent == this) {
            System.err.println("Cannot set entity as its own parent!");
            return;
        }

        if (newParent != null && isAncestorOf(newParent)) {
            System.err.println("Cannot set descendant as parent (circular reference)!");
            return;
        }

        if (this.parent != null) {
            this.parent.children.remove(this);
        }

        this.parent = newParent;
        this.parentId = (newParent != null) ? newParent.getId() : null;

        if (newParent != null) {
            if (newParent.children == null) {
                newParent.children = new ArrayList<>();
            }
            newParent.children.add(this);
        }
    }

    public boolean isAncestorOf(EditorEntity other) {
        EditorEntity current = other.parent;
        while (current != null) {
            if (current == this) return true;
            current = current.parent;
        }
        return false;
    }

    public List<EditorEntity> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return Collections.unmodifiableList(children);
    }

    public List<EditorEntity> getChildrenMutable() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public int getDepth() {
        int depth = 0;
        EditorEntity current = parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }

    void clearParent() {
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        this.parent = null;
        this.parentId = null;
    }

    void setParentDirect(EditorEntity newParent) {
        this.parent = newParent;
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
    // ROTATION
    // ========================================================================

    public Vector3f getRotation() {
        return new Vector3f(rotation);
    }

    public void setRotation(Vector3f rotation) {
        this.rotation.set(rotation);
    }

    public void setRotation(float z) {
        rotation.set(0, 0, z);
    }

    // ========================================================================
    // SCALE
    // ========================================================================

    public Vector3f getScale() {
        return new Vector3f(scale);
    }

    public void setScale(Vector3f scale) {
        this.scale.set(scale);
    }

    public void setScale(Vector2f scale) {
        this.scale.set(scale, this.scale.z);
    }

    public void setScale(float x, float y) {
        scale.set(x, y, 1);
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
    // COMPONENT MANAGEMENT
    // ========================================================================

    public List<ComponentData> getComponents() {
        if (isScratchEntity()) {
            if (components == null) {
                components = new ArrayList<>();
            }
            return components;
        } else {
            return getMergedComponents();
        }
    }

    private List<ComponentData> getMergedComponents() {
        Prefab prefab = getPrefab();
        if (prefab == null) {
            return new ArrayList<>();
        }

        List<ComponentData> result = new ArrayList<>();
        for (ComponentData baseComp : prefab.getComponents()) {
            ComponentData merged = new ComponentData(baseComp.getType());
            merged.getFields().putAll(baseComp.getFields());

            Map<String, Object> overrides = componentOverrides.get(baseComp.getType());
            if (overrides != null) {
                merged.getFields().putAll(overrides);
            }

            result.add(merged);
        }
        return result;
    }

    public void addComponent(ComponentData componentData) {
        if (isPrefabInstance()) {
            throw new IllegalStateException(
                    "Cannot add components to prefab instance. Convert to scratch entity first.");
        }
        getComponents().add(componentData);
    }

    public boolean removeComponent(ComponentData componentData) {
        if (components == null) {
            return false;
        }
        return components.remove(componentData);
    }

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

    public boolean hasComponent(Class<?> clazz) {
        return getComponentByType(clazz.getSimpleName()) != null;
    }

    // ========================================================================
    // FIELD OVERRIDES
    // ========================================================================

    public Object getFieldValue(String componentType, String fieldName) {
        if (isScratchEntity()) {
            ComponentData comp = findComponentByType(componentType);
            return comp != null ? comp.getFields().get(fieldName) : null;
        }

        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides != null && overrides.containsKey(fieldName)) {
            return overrides.get(fieldName);
        }

        Prefab prefab = getPrefab();
        return prefab != null ? prefab.getFieldDefault(componentType, fieldName) : null;
    }

    public void setFieldValue(String componentType, String fieldName, Object value) {
        if (isScratchEntity()) {
            ComponentData comp = findComponentByType(componentType);
            if (comp != null) {
                comp.getFields().put(fieldName, value);
            }
            return;
        }

        componentOverrides.computeIfAbsent(componentType, k -> new HashMap<>())
                .put(fieldName, value);
    }

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

    public Object getFieldDefault(String componentType, String fieldName) {
        Prefab prefab = getPrefab();
        return prefab != null ? prefab.getFieldDefault(componentType, fieldName) : null;
    }

    public void resetFieldToDefault(String componentType, String fieldName) {
        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides != null) {
            overrides.remove(fieldName);
            if (overrides.isEmpty()) {
                componentOverrides.remove(componentType);
            }
        }
    }

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

    public void resetAllOverrides() {
        componentOverrides.clear();
    }

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
    // SPRITE ACCESS (for rendering) - DYNAMIC RESOLUTION
    // ========================================================================

    /**
     * Gets the current sprite for rendering.
     * Resolves dynamically from component data to support animation.
     *
     * @return Current sprite, or null if not renderable
     */
    public Sprite getCurrentSprite() {
        ComponentData spriteRenderer = getComponentByType("SpriteRenderer");
        if (spriteRenderer != null) {
            Object spriteObj = spriteRenderer.getFields().get("sprite");
            if (spriteObj instanceof Sprite sprite) {
                return sprite;
            }
            if (spriteObj instanceof Texture texture) {
                return new Sprite(texture);
            }
            // Handle String path (from JSON deserialization)
            if (spriteObj instanceof String path && !path.isEmpty()) {
                int hashIndex = path.indexOf('#');
                if (hashIndex != -1) {
                    String sheetPath = path.substring(0, hashIndex);
                    int spriteIndex = Integer.parseInt(path.substring(hashIndex + 1));
                    var sheet = Assets.load(sheetPath, com.pocket.rpg.rendering.SpriteSheet.class);
                    return sheet.getSprite(spriteIndex);
                }
                return Assets.load(path, Sprite.class);
            }
        }

        if (isPrefabInstance()) {
            if (isPrefabValid()) {
                return PrefabRegistry.getInstance().getPreviewSprite(prefabId);
            } else {
                return Assets.load("editor/brokenPrefabLink.png");
            }
        }

        return null;
    }

    /**
     * Gets the current size for rendering.
     * Resolves dynamically from getCurrentSprite().
     *
     * @return Size in world units, or (1,1) if no sprite
     */
    public Vector2f getCurrentSize() {
        Sprite sprite = getCurrentSprite();
        if (sprite != null) {
            return new Vector2f(sprite.getWorldWidth(), sprite.getWorldHeight());
        }
        return new Vector2f(1f, 1f);
    }

    public float getZIndex() {
        if (position.z != 0) return position.z;

        ComponentData spriteRenderer = getComponentByType("SpriteRenderer");
        if (spriteRenderer != null) {
            Object z = spriteRenderer.getFields().get("zIndex");
            if (z instanceof Number) return ((Number) z).floatValue();
        }
        return DEFAULT_ENTITY_Z_INDEX;
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    public EntityData toData() {
        EntityData data;
        if (isPrefabInstance()) {
            data = new EntityData(
                    prefabId,
                    name,
                    new float[]{position.x, position.y, position.z},
                    copyOverrides(componentOverrides)
            );
        } else {
            data = new EntityData(
                    name,
                    new float[]{position.x, position.y, position.z},
                    components != null ? new ArrayList<>(components) : new ArrayList<>()
            );
        }
        data.setId(id);
        data.setParentId(parentId);
        data.setOrder(order);
        return data;
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
            return new EditorEntity(
                    data.getId(), data.getPrefabId(), data.getName(), position,
                    null, data.getComponentOverrides(), data.getParentId(), data.getOrder()
            );
        } else {
            return new EditorEntity(
                    data.getId(), null, data.getName() != null ? data.getName() : "Entity", position,
                    data.getComponents() != null ? new ArrayList<>(data.getComponents()) : new ArrayList<>(),
                    null, data.getParentId(), data.getOrder()
            );
        }
    }

    private static Map<String, Map<String, Object>> copyOverrides(Map<String, Map<String, Object>> source) {
        if (source == null) return new HashMap<>();
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

    /**
     * Regenerates this entity's ID. Used when duplicate IDs are detected.
     */
    public void regenerateId() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public String toString() {
        return String.format("EditorEntity[id=%s, prefab=%s, name=%s, pos=(%.1f,%.1f), parent=%s, order=%d]",
                id, prefabId, name, position.x, position.y, parentId, order);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return id.equals(((EditorEntity) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
