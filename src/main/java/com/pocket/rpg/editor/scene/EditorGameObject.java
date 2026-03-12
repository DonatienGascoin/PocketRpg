package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.serialization.*;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Editor-side representation of a placed game object instance.
 * Extends GameObject to share hierarchy, component storage, and identity with runtime.
 * <p>
 * All entities (scratch and prefab) store real Component instances.
 * Prefab instances clone components from the template at creation time
 * and track which fields are overridden via {@link #overriddenFields}.
 * <p>
 * Position, rotation, and scale are stored in the Transform component.
 */
public class EditorGameObject extends GameObject implements HierarchyItem {

    /**
     * Persistent UUID-based ID (overrides GO's computed hash ID).
     */
    @Getter
    @Setter
    private String id;

    @Getter
    private final String prefabId;

    /**
     * Identifies which node within the prefab hierarchy this entity represents.
     * Null for root prefab instances and scratch entities.
     */
    @Getter
    private final String prefabNodeId;

    /**
     * Tracks which fields are overridden from prefab defaults.
     * Structure: componentType (fully-qualified class name) -> set of field names.
     * Only meaningful for prefab instances; empty for scratch entities.
     */
    private Map<String, Set<String>> overriddenFields;

    @Getter
    @Setter
    private String parentId;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Base constructor — initializes GO with a placeholder.
     */
    private EditorGameObject(String name, String prefabId, String prefabNodeId) {
        super(name);
        this.prefabId = prefabId;
        this.prefabNodeId = prefabNodeId;
        this.id = generateId();
        this.overriddenFields = new HashMap<>();
    }

    /**
     * Constructor for prefab instances (root node).
     */
    public EditorGameObject(String prefabId, Vector3f position) {
        this(prefabId, null, position);
    }

    /**
     * Constructor for prefab instances with optional node ID for hierarchy support.
     */
    public EditorGameObject(String prefabId, String prefabNodeId, Vector3f position) {
        this(prefabId + "_temp", prefabId, prefabNodeId);
        setName(prefabId + "_" + id);

        // Clone components from prefab template
        initComponentsFromTemplate();

        // Set position on the real Transform
        Transform t = getTransform();
        if (t != null) {
            // Only mark as overridden if position differs from template default
            Vector3f templatePos = new Vector3f(t.getPosition());
            t.setPosition(position);
            if (!position.equals(templatePos)) {
                markFieldOverridden(Transform.class.getName(), "localPosition");
            }
        }
    }

    /**
     * Constructor for scratch or prefab entities.
     */
    public EditorGameObject(String name, Vector3f position, boolean isPrefab) {
        this(name, isPrefab ? name : null, (String) null);
        setName(isPrefab ? (name + "_" + id) : name);

        if (isPrefab) {
            // Prefab instance: clone components from template
            initComponentsFromTemplate();
            Transform t = getTransform();
            if (t != null) {
                Vector3f templatePos = new Vector3f(t.getPosition());
                t.setPosition(position);
                if (!position.equals(templatePos)) {
                    markFieldOverridden(Transform.class.getName(), "localPosition");
                }
            }
        } else {
            // Scratch entity: set position on auto-created Transform
            Transform t = getTransform();
            if (t != null) {
                t.setPosition(position);
            }
        }
    }

    /**
     * Internal constructor for deserialization.
     */
    private EditorGameObject(String id, String prefabId, String prefabNodeId, String name,
                             List<Component> components,
                             String parentId, int order) {
        this(name != null ? name : "Entity", prefabId, prefabNodeId);
        this.id = (id != null && !id.isEmpty()) ? id : generateId();
        this.parentId = parentId;
        setOrder(order);

        if (components != null) {
            for (Component comp : components) {
                comp.setGameObject(this);
                if (comp instanceof Transform t) {
                    // Replace the auto-created Transform with the deserialized one
                    // TODO: Never remove the Transform, update its values instead
                    getComponentsInternal().removeIf(c -> c instanceof Transform);
                    getComponentsInternal().add(0, comp);
                    setTransformRef(t);
                } else {
                    getComponentsInternal().add(comp);
                }
            }
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Initializes components by cloning from prefab template.
     * If an auto-created Transform exists (from super()), its values are updated
     * from the template rather than adding a second Transform.
     * UITransform replaces the auto-created Transform (different type).
     */
    private void initComponentsFromTemplate() {
        List<Component> cloned = cloneComponentsFromTemplate();
        Transform existing = getTransform();

        for (Component comp : cloned) {
            if (comp instanceof UITransform) {
                // UITransform replaces the auto-created plain Transform at index 0
                if (existing != null) {
                    int idx = getComponentsInternal().indexOf(existing);
                    getComponentsInternal().set(idx, comp);
                } else {
                    getComponentsInternal().add(0, comp);
                }
                setTransformRef((Transform) comp);
                existing = (Transform) comp;
            } else if (comp instanceof Transform clonedTransform) {
                if (existing != null) {
                    // Copy template values onto existing Transform (don't add a second)
                    existing.setPosition(new Vector3f(clonedTransform.getPosition()));
                    existing.setRotation(new Vector3f(clonedTransform.getRotation()));
                    existing.setScale(new Vector3f(clonedTransform.getScale()));
                } else {
                    // No existing Transform (e.g., after clear in refreshFromTemplate) — add the clone
                    getComponentsInternal().add(comp);
                    setTransformRef(clonedTransform);
                    existing = clonedTransform;
                }
            } else {
                getComponentsInternal().add(comp);
            }
        }
    }

    // ========================================================================
    // OVERRIDES — Identity & Type
    // ========================================================================

    @Override
    public boolean isRuntime() {
        return false;
    }

    @Override
    public boolean isEditor() {
        return true;
    }

    // ========================================================================
    // OVERRIDES — Lifecycle (no-ops in editor)
    // ========================================================================

    @Override
    public void start() {
        // Editor entities don't run component lifecycle
    }

    @Override
    public void update(float deltaTime) {
        // Editor entities don't run component lifecycle
    }

    @Override
    public void lateUpdate(float deltaTime) {
        // Editor entities don't run component lifecycle
    }

    @Override
    public void destroy() {
        // Editor entities don't use runtime destroy
    }

    // ========================================================================
    // OVERRIDES — Enabled State
    // ========================================================================

    /**
     * Sets enabled without triggering runtime component notifications or scene registration.
     */
    @Override
    public void setEnabled(boolean enabled) {
        setEnabledDirect(enabled);
    }

    // ========================================================================
    // OVERRIDES — Hierarchy
    // ========================================================================

    /**
     * Override setParent to update parentId for serialization and skip scene registration.
     */
    @Override
    public void setParent(GameObject newParent) {
        if (newParent != null && !(newParent instanceof EditorGameObject)) {
            throw new IllegalArgumentException("EditorGameObject parent must be an EditorGameObject");
        }
        // Use the parent class logic for hierarchy mutation
        // (scene registration is skipped because SceneManager.getActiveScene() is null in editor)
        super.setParent(newParent);
        this.parentId = (newParent != null) ? newParent.getId() : null;
    }

    /**
     * Package-private: sets parent reference directly without updating children lists.
     * Used by EditorScene during hierarchy reconstruction.
     */
    void setParentDirect(EditorGameObject newParent) {
        // Set parent reference only — children lists are managed by caller
        setParentRef(newParent);
        this.parentId = (newParent != null) ? newParent.getId() : null;
    }

    /**
     * Package-private: clears parent reference without modifying parentId or triggering side effects.
     * Used by EditorScene.resolveHierarchy() to reset transient relationships.
     */
    void clearParentRef() {
        setParentRef(null);
    }

    /**
     * Package-private: clears the children list directly.
     * Used by EditorScene.resolveHierarchy() to reset transient relationships.
     */
    void clearChildrenDirect() {
        getChildrenInternal().clear();
    }

    public int getDepth() {
        int depth = 0;
        GameObject current = getParent();
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    // ========================================================================
    // HierarchyItem IMPLEMENTATION
    // ========================================================================

    @Override
    public HierarchyItem getHierarchyParent() {
        return (HierarchyItem) getParent();
    }

    @Override
    public List<? extends HierarchyItem> getHierarchyChildren() {
        return getChildren().stream()
                .filter(c -> c instanceof HierarchyItem)
                .map(c -> (HierarchyItem) c)
                .toList();
    }

    // ========================================================================
    // POSITION (convenience — delegates to Transform)
    // ========================================================================

    public Vector3f getPosition() {
        Transform t = getTransform();
        return t != null ? new Vector3f(t.getPosition()) : new Vector3f();
    }

    public Vector3f getPositionRef() {
        Transform t = getTransform();
        return t != null ? t.getPosition() : null;
    }

    public void setPosition(float x, float y) {
        setPosition(x, y, getPosition().z);
    }

    public void setPosition(float x, float y, float z) {
        setPosition(new Vector3f(x, y, z));
    }

    public void setPosition(Vector3f pos) {
        Transform t = getTransform();
        if (t != null) {
            t.setPosition(pos);
        }
    }

    // ========================================================================
    // ROTATION
    // ========================================================================

    public Vector3f getRotation() {
        Transform t = getTransform();
        return t != null ? new Vector3f(t.getRotation()) : new Vector3f();
    }

    public void setRotation(Vector3f rotation) {
        Transform t = getTransform();
        if (t != null) {
            t.setRotation(rotation);
        }
    }

    public void setRotation(float z) {
        setRotation(new Vector3f(0, 0, z));
    }

    // ========================================================================
    // SCALE
    // ========================================================================

    public Vector3f getScale() {
        Transform t = getTransform();
        return t != null ? new Vector3f(t.getScale()) : new Vector3f(1, 1, 1);
    }

    public void setScale(Vector3f scale) {
        Transform t = getTransform();
        if (t != null) {
            t.setScale(scale);
        }
    }

    public void setScale(Vector2f scale) {
        setScale(new Vector3f(scale.x, scale.y, 1));
    }

    public void setScale(float x, float y) {
        setScale(new Vector3f(x, y, 1));
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

    /**
     * Returns true if this entity represents a child/grandchild node within a prefab hierarchy.
     */
    public boolean isPrefabChildNode() {
        return prefabNodeId != null && !prefabNodeId.isEmpty();
    }

    // ========================================================================
    // COMPONENT MANAGEMENT
    // ========================================================================

    /**
     * Gets the raw mutable component list.
     * Used by editor code for direct manipulation (e.g., SwapTransformCommand).
     */
    public List<Component> getComponents() {
        return getComponentsInternal();
    }

    /**
     * Gets components excluding Transform (for inspector display).
     */
    public List<Component> getComponentsWithoutTransform() {
        List<Component> result = new ArrayList<>();
        for (Component comp : getComponentsInternal()) {
            if (!(comp instanceof Transform)) {
                result.add(comp);
            }
        }
        return result;
    }

    /**
     * Adds a component (editor-specific logic: prefab guard, UITransform swap, duplicate check).
     */
    @Override
    public <T extends Component> T addComponent(T component) {
        if (isPrefabInstance()) {
            throw new IllegalStateException(
                    "Cannot add components to prefab instance. Convert to scratch entity first.");
        }

        component.setGameObject(this);

        // Allow UITransform to replace Transform
        if (component instanceof UITransform) {
            getComponentsInternal().removeIf(c -> c.getClass() == Transform.class);
            getComponentsInternal().add(component);
            setTransformRef((Transform) component);
            addRequiredComponents(component.getClass());
            return component;
        }

        if (component instanceof Transform) {
            System.err.println("Cannot add Transform - already exists");
            return component;
        }

        // Prevent duplicate component types
        if (hasComponent(component.getClass())) {
            System.err.println("Cannot add " + component.getClass().getSimpleName()
                    + " - entity already has one");
            return component;
        }

        addRequiredComponents(component.getClass());
        getComponentsInternal().add(component);
        return component;
    }

    /**
     * Removes a component (editor-specific: no lifecycle callbacks).
     */
    @Override
    public void removeComponent(Component component) {
        if (component instanceof Transform) {
            System.err.println("Cannot remove Transform component");
            return;
        }
        getComponentsInternal().remove(component);
    }

    /**
     * Replaces one component with another in the component list (same index).
     * Used by SwapTransformCommand and ReparentEntityCommand.
     */
    public void replaceComponent(Component oldComp, Component newComp) {
        List<Component> comps = getComponentsInternal();
        int index = comps.indexOf(oldComp);
        if (index >= 0) {
            comps.set(index, newComp);
            newComp.setGameObject(this);
            if (newComp instanceof Transform t) {
                setTransformRef(t);
            }
        }
    }

    /**
     * Adds any components declared by @RequiredComponent on the given class
     * or its superclasses, if they are not already present on this entity.
     */
    private void addRequiredComponents(Class<?> componentClass) {
        Class<?> clazz = componentClass;
        while (clazz != null && clazz != Component.class && clazz != Object.class) {
            RequiredComponent[] requirements = clazz.getDeclaredAnnotationsByType(RequiredComponent.class);
            for (RequiredComponent req : requirements) {
                Class<? extends Component> requiredType = req.value();
                if (hasComponent(requiredType)) {
                    continue;
                }
                try {
                    Component dependency = requiredType.getDeclaredConstructor().newInstance();
                    addComponent(dependency);
                } catch (Exception e) {
                    System.err.println("[RequiredComponent] Failed to auto-add " +
                            requiredType.getSimpleName() + ": " + e.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public Component getComponentByType(String simpleName) {
        for (Component comp : getComponentsInternal()) {
            if (comp.getClass().getSimpleName().equals(simpleName)) {
                return comp;
            }
        }
        return null;
    }

    /**
     * Finds a component by its fully-qualified class name.
     */
    public Component findComponentByType(String componentType) {
        for (Component comp : getComponentsInternal()) {
            if (comp.getClass().getName().equals(componentType)) {
                return comp;
            }
        }
        return null;
    }

    // ========================================================================
    // FIELD OVERRIDE TRACKING
    // ========================================================================

    /**
     * Marks a field as overridden from prefab default.
     */
    public void markFieldOverridden(String componentType, String fieldName) {
        overriddenFields.computeIfAbsent(componentType, k -> new LinkedHashSet<>())
                .add(fieldName);
    }

    /**
     * Removes a field from override tracking without changing the component value.
     */
    public void clearFieldOverride(String componentType, String fieldName) {
        Set<String> fields = overriddenFields.get(componentType);
        if (fields != null) {
            fields.remove(fieldName);
            if (fields.isEmpty()) {
                overriddenFields.remove(componentType);
            }
        }
    }

    /**
     * Compares a field's current value against the prefab default and
     * marks or clears the override accordingly.
     */
    public void syncFieldOverride(String componentType, String fieldName, Object value) {
        if (!isPrefabInstance()) return;
        Object defaultValue = getFieldDefault(componentType, fieldName);
        if (defaultValue == null ? value == null : defaultValue.equals(value)) {
            clearFieldOverride(componentType, fieldName);
        } else {
            markFieldOverridden(componentType, fieldName);
        }
    }

    /**
     * Checks if a field is overridden from prefab default.
     */
    public boolean isFieldOverridden(String componentType, String fieldName) {
        if (isScratchEntity()) {
            return false;
        }
        Set<String> fields = overriddenFields.get(componentType);
        return fields != null && fields.contains(fieldName);
    }

    /**
     * Gets the default value for a field from the prefab template.
     */
    public Object getFieldDefault(String componentType, String fieldName) {
        Prefab prefab = getPrefab();
        if (prefab == null) {
            return null;
        }
        if (isPrefabChildNode()) {
            return prefab.getChildFieldDefault(prefabNodeId, componentType, fieldName);
        }
        return prefab.getFieldDefault(componentType, fieldName);
    }

    /**
     * Resets a field to its prefab default value.
     * Applies the default onto the real component and removes from override tracking.
     */
    public void resetFieldToDefault(String componentType, String fieldName) {
        clearFieldOverride(componentType, fieldName);

        Object defaultValue = getFieldDefault(componentType, fieldName);
        Component comp = findComponentByType(componentType);
        if (comp != null && defaultValue != null) {
            // Deep copy to avoid sharing mutable objects (e.g. Vector3f) with the template
            Object copied = ComponentReflectionUtils.deepCopyValue(defaultValue);
            ComponentReflectionUtils.setFieldValue(comp, fieldName, copied);
        }
    }

    /**
     * Gets the list of overridden field names for a component type.
     */
    public List<String> getOverriddenFields(String componentType) {
        if (isScratchEntity()) {
            return List.of();
        }
        Set<String> fields = overriddenFields.get(componentType);
        return fields != null ? new ArrayList<>(fields) : List.of();
    }

    /**
     * Resets all overrides and re-clones all components from the prefab template.
     */
    public void resetAllOverrides() {
        overriddenFields.clear();
        getComponentsInternal().clear();
        setTransformRef(null);
        initComponentsFromTemplate();
    }

    /**
     * Gets the total number of overridden fields across all component types.
     */
    public int getOverrideCount() {
        int count = 0;
        for (Set<String> fields : overriddenFields.values()) {
            count += fields.size();
        }
        return count;
    }

    /**
     * Builds a serializable override map from real components.
     * Reads current values from components for fields tracked in overriddenFields.
     * Used for serialization compatibility with the on-disk format.
     */
    public Map<String, Map<String, Object>> getComponentOverrides() {
        if (isScratchEntity()) {
            return new LinkedHashMap<>();
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (var entry : overriddenFields.entrySet()) {
            String componentType = entry.getKey();
            Set<String> fields = entry.getValue();
            if (fields.isEmpty()) continue;

            Component comp = findComponentByType(componentType);
            if (comp == null) continue;

            Map<String, Object> fieldValues = new LinkedHashMap<>();
            for (String fieldName : fields) {
                Object value = ComponentReflectionUtils.getFieldValue(comp, fieldName);
                if (value != null) {
                    fieldValues.put(fieldName, ComponentReflectionUtils.deepCopyValue(value));
                }
            }
            if (!fieldValues.isEmpty()) {
                result.put(componentType, fieldValues);
            }
        }
        return result;
    }

    /**
     * Applies override values from serialized data (JSON format) onto real components.
     * Sets the value on each component and marks the field as overridden.
     * Used during deserialization and for copying overrides between entities.
     */
    public void applySerializedOverrides(Map<String, Map<String, Object>> overrides) {
        if (overrides == null) return;
        for (var entry : overrides.entrySet()) {
            String componentType = entry.getKey();
            Component comp = findComponentByType(componentType);
            if (comp == null) continue;
            for (var field : entry.getValue().entrySet()) {
                ComponentReflectionUtils.setFieldValue(comp, field.getKey(), field.getValue());
                markFieldOverridden(componentType, field.getKey());
            }
        }
    }

    /**
     * Re-clones components from the prefab template while preserving overridden field values.
     * Called when a prefab template is saved to pick up template changes on non-overridden fields.
     */
    public void refreshFromTemplate() {
        if (isScratchEntity()) return;

        // Capture current override values before re-cloning
        Map<String, Map<String, Object>> savedValues = buildDeepCopyOverrides();

        // Re-clone from template (gets fresh defaults)
        getComponentsInternal().clear();
        setTransformRef(null);
        initComponentsFromTemplate();
        this.overriddenFields.clear();

        // Re-apply overridden values on top of fresh clones
        applySerializedOverrides(savedValues);
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    /**
     * Converts to GameObjectData for serialization.
     * Components are DEEP COPIED to prevent runtime modifications
     * from corrupting editor state.
     */
    public GameObjectData toData() {
        GameObjectData data;

        if (isPrefabInstance()) {
            // Prefab instance: store prefabId + overridden field values
            data = new GameObjectData(id, getName(), prefabId, getComponentOverrides());

            // Set prefab asset path if available (new serialization format)
            Prefab p = getPrefab();
            if (p instanceof com.pocket.rpg.prefab.JsonPrefab jsonPrefab && jsonPrefab.getSourcePath() != null) {
                data.setPrefab(jsonPrefab.getSourcePath());
            }
        } else {
            // Scratch entity: DEEP COPY all components
            // Filter out base Transform if UITransform exists (they should not co-exist)
            List<Component> clonedComponents = new ArrayList<>();
            List<Component> components = getComponentsInternal();
            boolean hasUITransform = components.stream()
                    .anyMatch(c -> c instanceof UITransform);

            for (Component comp : components) {
                // Skip plain Transform if UITransform exists (UITransform extends Transform)
                if (hasUITransform && comp.getClass() == Transform.class) {
                    continue;
                }
                Component clone = ComponentReflectionUtils.cloneComponent(comp);
                if (clone != null) {
                    clonedComponents.add(clone);
                }
            }
            data = new GameObjectData(id, getName(), clonedComponents);
        }

        data.setParentId(parentId);
        data.setOrder(getOrder());
        data.setActive(isEnabled());
        data.setPrefabNodeId(prefabNodeId);
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
            // Create shell entity, then clone components from template and apply overrides
            entity = new EditorGameObject(
                    data.getId(),
                    data.getPrefabId(),
                    data.getPrefabNodeId(),
                    data.getName(),
                    null, // Components will be cloned below
                    data.getParentId(),
                    data.getOrder()
            );
            // Clone from prefab template
            entity.initComponentsFromTemplate();
            // Apply serialized overrides (sets values and marks fields as overridden)
            entity.applySerializedOverrides(data.getComponentOverrides());
        } else {
            // Components are already resolved by ComponentTypeAdapterFactory
            List<Component> components = data.getComponents() != null
                    ? new ArrayList<>(data.getComponents())
                    : new ArrayList<>();

            entity = new EditorGameObject(
                    data.getId(),
                    null,  // No prefabId
                    null,  // No prefabNodeId
                    data.getName() != null ? data.getName() : "Entity",
                    components,
                    data.getParentId(),
                    data.getOrder()
            );
        }

        // Set owner on all components
        for (Component comp : entity.getComponentsInternal()) {
            if (comp.getGameObject() == null) {
                comp.setGameObject(entity);
            }
        }

        // Ensure @RequiredComponent dependencies are satisfied for loaded entities
        if (!entity.isPrefabInstance()) {
            for (Component comp : new ArrayList<>(entity.getComponentsInternal())) {
                entity.addRequiredComponents(comp.getClass());
            }
        }

        entity.setEnabled(data.isActive());

        return entity;
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    /**
     * Clones components from the prefab template.
     * For child nodes, resolves from the corresponding hierarchy node.
     */
    private List<Component> cloneComponentsFromTemplate() {
        Prefab prefab = getPrefab();
        if (prefab == null) {
            return new ArrayList<>();
        }

        // Resolve the correct component source based on prefabNodeId
        List<Component> sourceComponents;
        if (isPrefabChildNode()) {
            GameObjectData node = prefab.findNode(prefabNodeId);
            if (node == null) {
                System.err.println("Prefab node '" + prefabNodeId + "' not found in prefab '" + prefabId + "'");
                return new ArrayList<>();
            }
            sourceComponents = node.getComponents();
            if (sourceComponents == null) {
                sourceComponents = List.of();
            }
        } else {
            sourceComponents = prefab.getComponents();
        }

        List<Component> result = new ArrayList<>();
        boolean hasTransform = false;

        for (Component baseComp : sourceComponents) {
            Component cloned = ComponentReflectionUtils.cloneComponent(baseComp);
            if (cloned != null) {
                if (cloned instanceof Transform) {
                    hasTransform = true;
                }
                cloned.setGameObject(this);
                result.add(cloned);
            }
        }

        // Defensive: add Transform if source didn't have one
        if (!hasTransform) {
            Transform transform = new Transform();
            transform.setGameObject(this);
            result.add(0, transform);
        }

        return result;
    }

    /**
     * Builds a deep copy of the current override values.
     * Used by refreshFromTemplate to preserve values across re-cloning.
     */
    private Map<String, Map<String, Object>> buildDeepCopyOverrides() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (var entry : overriddenFields.entrySet()) {
            String componentType = entry.getKey();
            Set<String> fields = entry.getValue();
            if (fields.isEmpty()) continue;

            Component comp = findComponentByType(componentType);
            if (comp == null) continue;

            Map<String, Object> fieldValues = new LinkedHashMap<>();
            for (String fieldName : fields) {
                Object value = ComponentReflectionUtils.getFieldValue(comp, fieldName);
                if (value != null) {
                    fieldValues.put(fieldName, ComponentReflectionUtils.deepCopyValue(value));
                }
            }
            if (!fieldValues.isEmpty()) {
                result.put(componentType, fieldValues);
            }
        }
        return result;
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
                id, prefabId, getName(), pos.x, pos.y, parentId, getOrder());
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
