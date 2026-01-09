package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.serialization.EntityData;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Editor-side representation of a placed entity instance.
 * Supports parent-child hierarchy and sibling ordering.
 * <p>
 * Components are stored as actual Component instances, enabling direct
 * field access via reflection and proper type safety.
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

    /**
     * Components for scratch entities. Stored as actual Component instances.
     */
    private List<Component> components;

    /**
     * Component field overrides for prefab instances.
     * Structure: componentType -> (fieldName -> value)
     */
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
                         List<Component> components,
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

    /**
     * Gets all components for this entity.
     * For scratch entities: returns the component list directly.
     * For prefab instances: returns cloned prefab components with overrides applied.
     */
    public List<Component> getComponents() {
        if (isScratchEntity()) {
            if (components == null) {
                components = new ArrayList<>();
            }
            return components;
        } else {
            return getMergedComponents();
        }
    }

    /**
     * Creates component instances from prefab with overrides applied.
     */
    private List<Component> getMergedComponents() {
        Prefab prefab = getPrefab();
        if (prefab == null) {
            return new ArrayList<>();
        }

        List<Component> result = new ArrayList<>();
        for (Component baseComp : prefab.getComponentInstances()) {
            Component cloned = cloneComponent(baseComp);
            if (cloned != null) {
                // Apply overrides
                Map<String, Object> overrides = componentOverrides.get(baseComp.getClass().getName());
                if (overrides != null) {
                    applyOverrides(cloned, overrides);
                }
                result.add(cloned);
            }
        }
        return result;
    }

    /**
     * Clones a component by instantiating a new one and copying field values.
     */
    private Component cloneComponent(Component source) {
        ComponentMeta meta = ComponentRegistry.getByClassName(source.getClass().getName());
        if (meta == null) {
            return null;
        }

        Component clone = ComponentRegistry.instantiateByClassName(source.getClass().getName());
        if (clone == null) {
            return null;
        }

        for (FieldMeta fieldMeta : meta.fields()) {
            try {
                Field field = fieldMeta.field();
                field.setAccessible(true);
                Object value = field.get(source);
                field.set(clone, value);
            } catch (IllegalAccessException e) {
                System.err.println("Failed to clone field " + fieldMeta.name() + ": " + e.getMessage());
            }
        }

        return clone;
    }

    /**
     * Applies field overrides to a component via reflection.
     */
    private void applyOverrides(Component component, Map<String, Object> overrides) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            FieldMeta fieldMeta = findFieldMeta(meta, fieldName);
            if (fieldMeta != null) {
                try {
                    Field field = fieldMeta.field();
                    field.setAccessible(true);
                    // Use ComponentData.fromSerializable for full conversion (assets, vectors, etc.)
                    Object converted = com.pocket.rpg.serialization.ComponentData.fromSerializable(value, fieldMeta.type());
                    field.set(component, converted);
                } catch (Exception e) {
                    System.err.println("Failed to apply override for " + fieldName + ": " + e.getMessage());
                }
            }
        }
    }

    public void addComponent(Component component) {
        if (isPrefabInstance()) {
            throw new IllegalStateException(
                    "Cannot add components to prefab instance. Convert to scratch entity first.");
        }
        getComponents().add(component);
    }

    public boolean removeComponent(Component component) {
        if (components == null) {
            return false;
        }
        return components.remove(component);
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> type) {
        for (Component comp : getComponents()) {
            if (type.isInstance(comp)) {
                return (T) comp;
            }
        }
        return null;
    }

    public Component getComponentByType(String simpleName) {
        for (Component comp : getComponents()) {
            if (comp.getClass().getSimpleName().equals(simpleName)) {
                return comp;
            }
        }
        return null;
    }

    public boolean hasComponent(String simpleName) {
        return getComponentByType(simpleName) != null;
    }

    public boolean hasComponent(Class<?> clazz) {
        return getComponent((Class<? extends Component>) clazz) != null;
    }

    // ========================================================================
    // FIELD ACCESS VIA REFLECTION
    // ========================================================================

    /**
     * Gets a field value from a component.
     * For scratch entities: reads from component directly.
     * For prefab instances: returns override if present, otherwise prefab default.
     */
    public Object getFieldValue(String componentType, String fieldName) {
        if (isScratchEntity()) {
            Component comp = findComponentByType(componentType);
            return comp != null ? getFieldFromComponent(comp, fieldName) : null;
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
     * For scratch entities: sets on component directly.
     * For prefab instances: stores as override.
     */
    public void setFieldValue(String componentType, String fieldName, Object value) {
        if (isScratchEntity()) {
            Component comp = findComponentByType(componentType);
            if (comp != null) {
                setFieldOnComponent(comp, fieldName, value);
            }
            return;
        }

        // Store as override for prefab instances
        componentOverrides.computeIfAbsent(componentType, k -> new HashMap<>())
                .put(fieldName, value);
    }

    /**
     * Gets a field value from a component via reflection.
     */
    private Object getFieldFromComponent(Component component, String fieldName) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return null;
        }

        FieldMeta fieldMeta = findFieldMeta(meta, fieldName);
        if (fieldMeta == null) {
            return null;
        }

        try {
            Field field = fieldMeta.field();
            field.setAccessible(true);
            return field.get(component);
        } catch (IllegalAccessException e) {
            System.err.println("Failed to read field " + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Sets a field value on a component via reflection.
     */
    private void setFieldOnComponent(Component component, String fieldName, Object value) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return;
        }

        FieldMeta fieldMeta = findFieldMeta(meta, fieldName);
        if (fieldMeta == null) {
            return;
        }

        try {
            Field field = fieldMeta.field();
            field.setAccessible(true);
            Object converted = com.pocket.rpg.serialization.ComponentData.fromSerializable(value, fieldMeta.type());
            field.set(component, converted);
        } catch (Exception e) {
            System.err.println("Failed to set field " + fieldName + ": " + e.getMessage());
        }
    }

    private FieldMeta findFieldMeta(ComponentMeta meta, String fieldName) {
        for (FieldMeta fm : meta.fields()) {
            if (fm.name().equals(fieldName)) {
                return fm;
            }
        }
        return null;
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

    private Component findComponentByType(String componentType) {
        if (components == null) {
            return null;
        }
        for (Component comp : components) {
            if (comp.getClass().getName().equals(componentType)) {
                return comp;
            }
        }
        return null;
    }

    // ========================================================================
    // SPRITE ACCESS (for rendering)
    // ========================================================================

    /**
     * Gets the current sprite for rendering.
     * Resolves from SpriteRenderer component if present.
     */
    public Sprite getCurrentSprite() {
        SpriteRenderer spriteRenderer = getComponent(SpriteRenderer.class);
        if (spriteRenderer != null) {
            Sprite sprite = spriteRenderer.getSprite();
            if (sprite != null) {
                return sprite;
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

        SpriteRenderer spriteRenderer = getComponent(SpriteRenderer.class);
        if (spriteRenderer != null) {
            return spriteRenderer.getZIndex();
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

        EditorEntity entity;
        if (data.isPrefabInstance()) {
            entity = new EditorEntity(
                    data.getId(), data.getPrefabId(), data.getName(), position,
                    null, data.getComponentOverrides(), data.getParentId(), data.getOrder()
            );
        } else {
            // Components are already resolved by ComponentTypeAdapterFactory
            List<Component> components = data.getComponents() != null
                    ? new ArrayList<>(data.getComponents())
                    : new ArrayList<>();

            entity = new EditorEntity(
                    data.getId(), null, data.getName() != null ? data.getName() : "Entity", position,
                    components,
                    null, data.getParentId(), data.getOrder()
            );
        }

        return entity;
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