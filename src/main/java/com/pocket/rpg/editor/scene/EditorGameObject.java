package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.*;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Editor-side representation of a placed game object instance.
 * Supports parent-child hierarchy and sibling ordering.
 * <p>
 * Components are stored as actual Component instances, enabling direct
 * field access via reflection and proper type safety.
 * <p>
 * Position, rotation, and scale are stored in the Transform component,
 * not as separate fields.
 */
public class EditorGameObject implements Renderable {

    @Getter
    @Setter
    private String id;

    @Getter
    private final String prefabId;

    @Getter
    @Setter
    private String name;

    /**
     * Components for scratch entities. Includes Transform.
     */
    private List<Component> components;

    /**
     * Component field overrides for prefab instances.
     * Structure: componentType -> (fieldName -> value)
     * Transform overrides (position/rotation/scale) go here too.
     */
    private Map<String, Map<String, Object>> componentOverrides;

    @Getter
    @Setter
    private String parentId;

    @Getter
    @Setter
    private int order;

    @Getter
    private transient EditorGameObject parent;

    private transient List<EditorGameObject> children;

    private static final int DEFAULT_ENTITY_Z_INDEX = 100; // TODO: Shouldn't it be 0 ?
    private static final String TRANSFORM_TYPE = Transform.class.getName();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Constructor for prefab instances.
     */
    public EditorGameObject(String prefabId, Vector3f position) {
        this.id = generateId();
        this.prefabId = prefabId;
        this.name = prefabId + "_" + id;
        this.order = 0;
        this.children = new ArrayList<>();
        this.componentOverrides = new HashMap<>();
        this.components = new ArrayList<>();

        // Store position as Transform override for prefab instances
        Map<String, Object> transformOverrides = new HashMap<>();
        transformOverrides.put("localPosition", new float[]{position.x, position.y, position.z});
        componentOverrides.put(TRANSFORM_TYPE, transformOverrides);
    }

    /**
     * Constructor for scratch or prefab entities.
     */
    public EditorGameObject(String name, Vector3f position, boolean isPrefab) {
        this.id = generateId();
        this.prefabId = isPrefab ? name : null;
        this.name = isPrefab ? (name + "_" + id) : name;
        this.order = 0;
        this.children = new ArrayList<>();
        this.componentOverrides = new HashMap<>();
        this.components = new ArrayList<>();

        if (isPrefab) {
            // Prefab instance: store position in overrides
            Map<String, Object> transformOverrides = new HashMap<>();
            transformOverrides.put("localPosition", new float[]{position.x, position.y, position.z});
            componentOverrides.put(TRANSFORM_TYPE, transformOverrides);
        } else {
            // Scratch entity: add Transform component
            components.add(new Transform(position));
        }
    }

    /**
     * Internal constructor for deserialization.
     */
    private EditorGameObject(String id, String prefabId, String name,
                             List<Component> components,
                             Map<String, Map<String, Object>> componentOverrides,
                             String parentId, int order) {
        this.id = (id != null && !id.isEmpty()) ? id : generateId();
        this.prefabId = prefabId;
        this.name = name;
        this.parentId = parentId;
        this.order = order;
        this.children = new ArrayList<>();
        this.componentOverrides = componentOverrides != null ? new HashMap<>(componentOverrides) : new HashMap<>();
        this.components = components != null ? components : new ArrayList<>();

        // Ensure scratch entities have a Transform
        if (isScratchEntity() && getTransform() == null) {
            this.components.add(0, new Transform());
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ========================================================================
    // TRANSFORM ACCESS
    // ========================================================================

    /**
     * Gets the Transform component (scratch entities only).
     */
    public Transform getTransform() {
        if (components != null) {
            for (Component comp : components) {
                if (comp instanceof Transform t) {
                    return t;
                }
            }
        }
        return null;
    }

    // ========================================================================
    // POSITION
    // ========================================================================

    public Vector3f getPosition() {
        if (isScratchEntity()) {
            Transform t = getTransform();
            return t != null ? new Vector3f(t.getPosition()) : new Vector3f();
        } else {
            // Prefab instance: get from overrides
            Vector3f result = getTransformVector("localPosition");
            return result;
        }
    }

    public Vector3f getPositionRef() {
        if (isScratchEntity()) {
            Transform t = getTransform();
            return t != null ? t.getPosition() : null;
        }
        // Prefab instances don't have a direct reference
        return getPosition();
    }

    public void setPosition(float x, float y) {
        setPosition(x, y, getPosition().z);
    }

    public void setPosition(float x, float y, float z) {
        setPosition(new Vector3f(x, y, z));
    }

    public void setPosition(Vector3f pos) {
        if (isScratchEntity()) {
            Transform t = getTransform();
            if (t != null) {
                t.setPosition(pos);
            }
        } else {
            // Prefab instance: store in overrides
            setTransformVector("localPosition", pos);
        }
    }

    // ========================================================================
    // ROTATION
    // ========================================================================

    public Vector3f getRotation() {
        if (isScratchEntity()) {
            Transform t = getTransform();
            return t != null ? new Vector3f(t.getRotation()) : new Vector3f();
        } else {
            return getTransformVector("localRotation");
        }
    }

    public void setRotation(Vector3f rotation) {
        if (isScratchEntity()) {
            Transform t = getTransform();
            if (t != null) {
                t.setRotation(rotation);
            }
        } else {
            setTransformVector("localRotation", rotation);
        }
    }

    public void setRotation(float z) {
        setRotation(new Vector3f(0, 0, z));
    }

    // ========================================================================
    // SCALE
    // ========================================================================

    public Vector3f getScale() {
        if (isScratchEntity()) {
            Transform t = getTransform();
            return t != null ? new Vector3f(t.getScale()) : new Vector3f(1, 1, 1);
        } else {
            Vector3f scale = getTransformVector("localScale");
            // Default to 1,1,1 if not set
            if (scale.x == 0 && scale.y == 0 && scale.z == 0) {
                return new Vector3f(1, 1, 1);
            }
            return scale;
        }
    }

    public void setScale(Vector3f scale) {
        if (isScratchEntity()) {
            Transform t = getTransform();
            if (t != null) {
                t.setScale(scale);
            }
        } else {
            setTransformVector("localScale", scale);
        }
    }

    public void setScale(Vector2f scale) {
        setScale(new Vector3f(scale.x, scale.y, 1));
    }

    public void setScale(float x, float y) {
        setScale(new Vector3f(x, y, 1));
    }

    // ========================================================================
    // TRANSFORM OVERRIDE HELPERS
    // ========================================================================

    private Vector3f getTransformVector(String fieldName) {
        Map<String, Object> overrides = componentOverrides.get(TRANSFORM_TYPE);
        if (overrides != null && overrides.containsKey(fieldName)) {
            Object value = overrides.get(fieldName);
            if (value instanceof float[] arr) {
                return new Vector3f(
                        arr.length > 0 ? arr[0] : 0,
                        arr.length > 1 ? arr[1] : 0,
                        arr.length > 2 ? arr[2] : 0
                );
            } else if (value instanceof List<?> list) {
                return new Vector3f(
                        !list.isEmpty() ? ((Number) list.get(0)).floatValue() : 0,
                        list.size() > 1 ? ((Number) list.get(1)).floatValue() : 0,
                        list.size() > 2 ? ((Number) list.get(2)).floatValue() : 0
                );
            }
        }
        return new Vector3f();
    }

    private void setTransformVector(String fieldName, Vector3f value) {
        componentOverrides.computeIfAbsent(TRANSFORM_TYPE, k -> new HashMap<>())
                .put(fieldName, new float[]{value.x, value.y, value.z});
    }

    // ========================================================================
    // HIERARCHY MANAGEMENT
    // ========================================================================

    public void setParent(EditorGameObject newParent) {
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

    public boolean isAncestorOf(EditorGameObject other) {
        EditorGameObject current = other.parent;
        while (current != null) {
            if (current == this) return true;
            current = current.parent;
        }
        return false;
    }

    public List<EditorGameObject> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return Collections.unmodifiableList(children);
    }

    public List<EditorGameObject> getChildrenMutable() {
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
        EditorGameObject current = parent;
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

    void setParentDirect(EditorGameObject newParent) {
        this.parent = newParent;
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
    private transient List<Component> cachedMergedComponents = null;

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
            if (cachedMergedComponents == null) {
                cachedMergedComponents = getMergedComponents();
            }
            return cachedMergedComponents;
        }
    }

    public void invalidateComponentCache() {
        cachedMergedComponents = null;
    }

    /**
     * Gets components excluding Transform (for inspector display).
     */
    public List<Component> getComponentsWithoutTransform() {
        List<Component> result = new ArrayList<>();
        for (Component comp : getComponents()) {
            if (!(comp instanceof Transform)) {
                result.add(comp);
            }
        }
        return result;
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
        boolean hasTransform = false;

        // Clone all components from prefab
        for (Component baseComp : prefab.getComponents()) {
            Component cloned = cloneComponent(baseComp);
            if (cloned != null) {
                String compType = baseComp.getClass().getName();
                Map<String, Object> overrides = componentOverrides.get(compType);
                if (overrides != null) {
                    applyOverrides(cloned, overrides);
                }

                // Apply transform overrides from entity
                if (cloned instanceof Transform t) {
                    hasTransform = true;
                    t.setPosition(getPosition());
                    t.setRotation(getRotation());
                    t.setScale(getScale());
                }

                result.add(cloned);
            }
        }

        // Defensive: add Transform if prefab didn't have one
        if (!hasTransform) {
            Transform transform = new Transform();
            transform.setPosition(getPosition());
            transform.setRotation(getRotation());
            transform.setScale(getScale());
            result.add(0, transform);
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
                    Object converted = SerializationUtils.fromSerializable(value, fieldMeta.type());
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
        if (component instanceof Transform) {
            System.err.println("Cannot add Transform - already exists");
            return;
        }
        getComponents().add(component);
    }

    public boolean removeComponent(Component component) {
        if (component instanceof Transform) {
            System.err.println("Cannot remove Transform component");
            return false;
        }
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

    @SuppressWarnings("unchecked")
    public boolean hasComponent(Class<?> clazz) {
        return getComponent((Class<? extends Component>) clazz) != null;
    }

    // ========================================================================
    // FIELD ACCESS VIA REFLECTION
    // ========================================================================

    /**
     * Gets a field value from a component.
     */
    public Object getFieldValue(String componentType, String fieldName) {
        if (isScratchEntity()) {
            Component comp = findComponentByType(componentType);
            return comp != null ? getFieldFromComponent(comp, fieldName) : null;
        }

        Map<String, Object> overrides = componentOverrides.get(componentType);
        if (overrides != null && overrides.containsKey(fieldName)) {
            return overrides.get(fieldName);
        }

        Prefab prefab = getPrefab();
        return prefab != null ? prefab.getFieldDefault(componentType, fieldName) : null;
    }

    /**
     * Sets a field value on a component.
     */
    public void setFieldValue(String componentType, String fieldName, Object value) {
        if (isScratchEntity()) {
            Component comp = findComponentByType(componentType);
            if (comp != null) {
                setFieldOnComponent(comp, fieldName, value);
            }
            return;
        }

        componentOverrides.computeIfAbsent(componentType, k -> new HashMap<>())
                .put(fieldName, value);
    }

    private Object getFieldFromComponent(Component component, String fieldName) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) return null;

        FieldMeta fieldMeta = findFieldMeta(meta, fieldName);
        if (fieldMeta == null) return null;

        try {
            Field field = fieldMeta.field();
            field.setAccessible(true);
            return field.get(component);
        } catch (IllegalAccessException e) {
            System.err.println("Failed to read field " + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    private void setFieldOnComponent(Component component, String fieldName, Object value) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) return;

        FieldMeta fieldMeta = findFieldMeta(meta, fieldName);
        if (fieldMeta == null) return;

        try {
            Field field = fieldMeta.field();
            field.setAccessible(true);
            Object converted = SerializationUtils.fromSerializable(value, fieldMeta.type());
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

        return !valuesEqual(currentValue, defaultValue);
    }

    /**
     * Compares values with type normalization.
     * Handles float[]/List vs Vector3f comparisons.
     */
    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;

        // Try Vector2f
        Vector2f vec2A = toVector2f(a);
        Vector2f vec2B = toVector2f(b);
        if (vec2A != null && vec2B != null) {
            return vec2A.equals(vec2B);
        }

        // Try Vector3f
        Vector3f vec3A = toVector3f(a);
        Vector3f vec3B = toVector3f(b);
        if (vec3A != null && vec3B != null) {
            return vec3A.equals(vec3B);
        }

        // Try Vector4f
        Vector4f vec4A = toVector4f(a);
        Vector4f vec4B = toVector4f(b);
        if (vec4A != null && vec4B != null) {
            return vec4A.equals(vec4B);
        }

        // Primitives, Strings, Enums - equals() already checked above
        return false;
    }

    private Vector2f toVector2f(Object value) {
        if (value instanceof Vector2f v) return v;
        if (value instanceof float[] arr && arr.length >= 2) {
            return new Vector2f(arr[0], arr[1]);
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            return new Vector2f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue()
            );
        }
        return null;
    }

    /**
     * Converts various representations to Vector3f.
     */
    private Vector3f toVector3f(Object value) {
        if (value instanceof Vector3f v) {
            return v;
        }
        if (value instanceof float[] arr) {
            return new Vector3f(
                    arr.length > 0 ? arr[0] : 0,
                    arr.length > 1 ? arr[1] : 0,
                    arr.length > 2 ? arr[2] : 0
            );
        }
        if (value instanceof List<?> list) {
            return new Vector3f(
                    !list.isEmpty() ? ((Number) list.get(0)).floatValue() : 0,
                    list.size() > 1 ? ((Number) list.get(1)).floatValue() : 0,
                    list.size() > 2 ? ((Number) list.get(2)).floatValue() : 0
            );
        }
        return null;
    }

    private Vector4f toVector4f(Object value) {
        if (value instanceof Vector4f v) return v;
        if (value instanceof float[] arr && arr.length >= 4) {
            return new Vector4f(arr[0], arr[1], arr[2], arr[3]);
        }
        if (value instanceof List<?> list && list.size() >= 4) {
            return new Vector4f(
                    ((Number) list.get(0)).floatValue(),
                    ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue(),
                    ((Number) list.get(3)).floatValue()
            );
        }
        return null;
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
                if (componentOverrides.remove(componentType) != null) {
                    invalidateComponentCache();
                }
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

    public Vector2f getCurrentSize() {
        Sprite sprite = getCurrentSprite();
        if (sprite != null) {
            return new Vector2f(sprite.getWorldWidth(), sprite.getWorldHeight());
        }
        return new Vector2f(1f, 1f);
    }

    // ========================================================================
    // RENDERABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public int getZIndex() {
        Vector3f pos = getPosition();
        if (pos.z != 0) return (int) pos.z;

        SpriteRenderer spriteRenderer = getComponent(SpriteRenderer.class);
        if (spriteRenderer != null) {
            return spriteRenderer.getZIndex();
        }
        return DEFAULT_ENTITY_Z_INDEX;
    }

    @Override
    public boolean isRenderVisible() {
        return getCurrentSprite() != null;
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    /**
     * Converts to GameObjectData for serialization.
     */
    public GameObjectData toData() {
        GameObjectData data;

        if (isPrefabInstance()) {
            // Prefab instance: store prefabId + all overrides (including Transform)
            data = new GameObjectData(id, name, prefabId, copyOverrides(componentOverrides));
        } else {
            // Scratch entity: deep copy all components
            List<Component> clonedComponents = new ArrayList<>();
            if (components != null) {
                for (Component comp : components) {
                    Component clone = cloneComponent(comp);
                    if (clone != null) {
                        clonedComponents.add(clone);
                    }
                }
            }
            data = new GameObjectData(id, name, clonedComponents);
        }

        data.setParentId(parentId);
        data.setOrder(order);
        return data;
    }

    /**
     * Creates EditorGameObject from GameObjectData.
     */
    public static EditorGameObject fromData(GameObjectData data) {
        if (data == null) {
            throw new IllegalArgumentException("GameObjectData cannot be null");
        }

        EditorGameObject entity;

        if (data.isPrefabInstance()) {
            entity = new EditorGameObject(
                    data.getId(),
                    data.getPrefabId(),
                    data.getName(),
                    null,  // No components for prefab instance
                    data.getComponentOverrides(),
                    data.getParentId(),
                    data.getOrder()
            );
        } else {
            // Components are already resolved by ComponentTypeAdapterFactory
            List<Component> components = data.getComponents() != null
                    ? new ArrayList<>(data.getComponents())
                    : new ArrayList<>();

            entity = new EditorGameObject(
                    data.getId(),
                    null,  // No prefabId
                    data.getName() != null ? data.getName() : "Entity",
                    components,
                    null,  // No overrides
                    data.getParentId(),
                    data.getOrder()
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
        Vector3f pos = getPosition();
        return String.format("EditorGameObject[id=%s, prefab=%s, name=%s, pos=(%.1f,%.1f), parent=%s, order=%d]",
                id, prefabId, name, pos.x, pos.y, parentId, order);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return id.equals(((EditorGameObject) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}