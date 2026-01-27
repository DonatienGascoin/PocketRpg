# IGameObject Interface Refactor

## Overview

Extract a common interface from `GameObject` that both `GameObject` and `EditorGameObject` can implement. This allows `Component` to work seamlessly in both runtime and editor contexts without null checks or special-case code.

## Problem Statement

Currently, `Component` has a `gameObject` field typed as `GameObject`:

```java
public abstract class Component {
    protected GameObject gameObject;

    protected Transform getTransform() {
        return gameObject.getTransform();  // NPE in editor mode!
    }
}
```

In editor mode, components stored in `EditorGameObject`:
- Have `gameObject = null` (never set)
- Cannot use `getTransform()`, `getComponent()`, or any `gameObject` methods
- Must use workarounds like `GizmoContext.getTransform()` for gizmos

This forces developers to remember which methods are "safe" in editor mode - a maintenance burden and source of bugs.

---

## Design Goals

1. Components can use `getTransform()` and `getComponent()` in both runtime and editor
2. Minimal changes to existing `GameObject` and `Component` code
3. No performance regression at runtime
4. Clean, type-safe interface
5. Support future features (play-mode inspection, etc.)

---

## Implementation

### Phase 1: Define IGameObject Interface

**File:** `src/main/java/com/pocket/rpg/core/IGameObject.java` (NEW)

```java
package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;

import java.util.List;

/**
 * Common interface for game object types.
 * Implemented by both runtime {@link GameObject} and editor {@link EditorGameObject}.
 * <p>
 * This allows {@link Component} to access transform and sibling components
 * without knowing whether it's in runtime or editor context.
 */
public interface IGameObject {

    // ========================================================================
    // IDENTITY
    // ========================================================================

    /**
     * Returns the name of this game object.
     */
    String getName();

    /**
     * Returns a unique identifier for this game object.
     * For GameObject: may be name or generated ID.
     * For EditorGameObject: the entity ID.
     */
    String getId();

    // ========================================================================
    // TRANSFORM
    // ========================================================================

    /**
     * Returns the Transform component.
     * Always non-null - every game object has a transform.
     */
    Transform getTransform();

    // ========================================================================
    // COMPONENT ACCESS
    // ========================================================================

    /**
     * Gets the first component of the specified type.
     *
     * @param type The component class to find
     * @return The component, or null if not found
     */
    <T extends Component> T getComponent(Class<T> type);

    /**
     * Gets all components of the specified type.
     *
     * @param type The component class to find
     * @return List of matching components (may be empty)
     */
    <T extends Component> List<T> getComponents(Class<T> type);

    /**
     * Gets all components attached to this game object.
     */
    List<Component> getAllComponents();

    /**
     * Checks if this game object has a component of the specified type.
     */
    default boolean hasComponent(Class<? extends Component> type) {
        return getComponent(type) != null;
    }

    // ========================================================================
    // STATE
    // ========================================================================

    /**
     * Returns whether this game object is enabled.
     * For EditorGameObject: always returns true (editor entities are always "enabled").
     */
    boolean isEnabled();

    // ========================================================================
    // CONTEXT
    // ========================================================================

    /**
     * Returns true if this is a runtime game object (in a running scene).
     * Returns false if this is an editor-only object.
     */
    default boolean isRuntime() {
        return this instanceof GameObject;
    }

    /**
     * Returns true if this is an editor game object (in the scene editor).
     */
    default boolean isEditor() {
        return !isRuntime();
    }
}
```

### Phase 2: Update GameObject

**File:** `src/main/java/com/pocket/rpg/core/GameObject.java` (MODIFY)

```java
// Add implements clause
public class GameObject implements IGameObject {

    // Add getId() method (new requirement from interface)
    @Override
    public String getId() {
        // Runtime objects don't have persistent IDs - use name or identity hash
        return name != null ? name : ("obj_" + System.identityHashCode(this));
    }

    // Existing methods already satisfy interface:
    // - getName() ✓
    // - getTransform() ✓
    // - getComponent(Class<T>) ✓
    // - getComponents(Class<T>) ✓
    // - getAllComponents() ✓
    // - isEnabled() ✓
}
```

Changes required:
- Add `implements IGameObject`
- Add `getId()` method
- No other changes needed - existing methods match interface

### Phase 3: Update EditorGameObject

**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java` (MODIFY)

```java
// Add implements clause (already implements Renderable)
public class EditorGameObject implements Renderable, IGameObject {

    // getId() already exists ✓
    // getName() already exists ✓
    // getTransform() already exists ✓
    // getComponent(Class<T>) already exists ✓
    // getComponents() - need to add typed version
    // getAllComponents() - need to add

    /**
     * Gets all components of the specified type.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Component> List<T> getComponents(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component comp : getComponents()) {
            if (type.isInstance(comp)) {
                result.add((T) comp);
            }
        }
        return result;
    }

    /**
     * Gets all components (alias for getComponents() to match interface).
     */
    @Override
    public List<Component> getAllComponents() {
        return getComponents();
    }

    /**
     * Editor entities are always considered "enabled".
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

Changes required:
- Add `implements IGameObject`
- Add `getComponents(Class<T>)` method (typed version)
- Add `getAllComponents()` method (alias)
- Add `isEnabled()` method (always true)

### Phase 4: Update Component

**File:** `src/main/java/com/pocket/rpg/components/Component.java` (MODIFY)

```java
public abstract class Component implements GizmoDrawable, GizmoDrawableSelected {

    // Change field type from GameObject to IGameObject
    @Getter
    protected IGameObject owner;

    // Keep gameObject for backward compatibility (deprecated)
    /**
     * @deprecated Use {@link #owner} instead. This field will be removed in a future version.
     */
    @Deprecated
    protected GameObject gameObject;

    /**
     * Returns the owning game object.
     * Works in both runtime and editor contexts.
     */
    public IGameObject getOwner() {
        return owner;
    }

    /**
     * Returns the runtime GameObject, or null if in editor context.
     * Prefer using {@link #getOwner()} for context-agnostic code.
     */
    public GameObject getGameObject() {
        return (owner instanceof GameObject go) ? go : null;
    }

    /**
     * Internal method used by GameObject/EditorGameObject to set the owner reference.
     */
    public void setOwner(IGameObject owner) {
        this.owner = owner;
        // Maintain backward compatibility
        this.gameObject = (owner instanceof GameObject go) ? go : null;
    }

    /**
     * @deprecated Use {@link #setOwner(IGameObject)} instead.
     */
    @Deprecated
    public void setGameObject(GameObject gameObject) {
        setOwner(gameObject);
    }

    // Update helper methods to use owner

    protected Transform getTransform() {
        return owner != null ? owner.getTransform() : null;
    }

    /**
     * Gets a sibling component of the specified type.
     * Works in both runtime and editor contexts.
     */
    protected <T extends Component> T getComponent(Class<T> type) {
        return owner != null ? owner.getComponent(type) : null;
    }

    /**
     * Gets all sibling components of the specified type.
     */
    protected <T extends Component> List<T> getComponents(Class<T> type) {
        return owner != null ? owner.getComponents(type) : Collections.emptyList();
    }

    // Update isEnabled to use owner
    public boolean isEnabled() {
        return enabled && owner != null && owner.isEnabled();
    }

    // ... rest of class unchanged ...
}
```

### Phase 5: Update GameObject to Use setOwner

**File:** `src/main/java/com/pocket/rpg/core/GameObject.java` (MODIFY)

Update `addComponentInternal`:

```java
private void addComponentInternal(Component component) {
    component.setOwner(this);  // Changed from setGameObject
    components.add(component);
}

public void removeComponent(Component component) {
    if (component == transform) {
        System.err.println("Cannot remove Transform component!");
        return;
    }

    if (components.remove(component)) {
        component.destroy();
        component.setOwner(null);  // Changed from setGameObject

        if (scene != null) {
            scene.unregisterCachedComponent(component);
        }
    }
}
```

### Phase 6: Update EditorGameObject to Set Owner

**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java` (MODIFY)

Components need to have their owner set when accessed. Two approaches:

**Approach A: Lazy initialization (recommended)**

```java
/**
 * Gets all components for this entity.
 * Sets owner reference on components for editor use.
 */
public List<Component> getComponents() {
    if (isScratchEntity()) {
        if (components == null) {
            components = new ArrayList<>();
        }
        // Ensure owner is set for editor context
        for (Component comp : components) {
            if (comp.getOwner() != this) {
                comp.setOwner(this);
            }
        }
        return components;
    } else {
        if (cachedMergedComponents == null) {
            cachedMergedComponents = getMergedComponents();
        }
        return cachedMergedComponents;
    }
}

private List<Component> getMergedComponents() {
    // ... existing code ...

    // After creating cloned components, set owner
    for (Component comp : result) {
        comp.setOwner(this);
    }

    return result;
}
```

**Approach B: Set owner on add/load**

```java
public void addComponent(Component component) {
    // ... existing validation ...

    component.setOwner(this);  // Set owner when adding
    getComponents().add(component);
}

// In fromData():
public static EditorGameObject fromData(GameObjectData data) {
    // ... existing code ...

    // After creating entity, set owner on all components
    for (Component comp : entity.getComponents()) {
        comp.setOwner(entity);
    }

    return entity;
}
```

Recommend **Approach A** for safety - ensures owner is always set when accessed.

### Phase 7: Update GizmoContext (Optional Cleanup)

The `GizmoContext.setTransform()` workaround is no longer strictly necessary, but can be kept for convenience. Components can now use either:

```java
// Old way (still works)
@Override
public void onDrawGizmos(GizmoContext ctx) {
    Vector3f pos = ctx.getTransform().getPosition();
}

// New way (now works!)
@Override
public void onDrawGizmos(GizmoContext ctx) {
    Vector3f pos = getTransform().getPosition();
}
```

---

## Migration Guide

### For Component Authors

**Before:**
```java
// Had to check for null or use context
public void someMethod() {
    if (gameObject != null) {
        Transform t = gameObject.getTransform();
    }
}
```

**After:**
```java
// Just use helper methods - they work everywhere
public void someMethod() {
    Transform t = getTransform();  // Safe in runtime AND editor
    if (t != null) {
        // ...
    }
}
```

### Runtime-Only Code

If you need runtime-specific features (Scene access, etc.):

```java
public void runtimeOnlyMethod() {
    GameObject go = getGameObject();  // Returns null in editor
    if (go != null && go.getScene() != null) {
        Scene scene = go.getScene();
        // ... runtime-only operations ...
    }
}
```

Or use the context check:

```java
if (owner.isRuntime()) {
    GameObject go = (GameObject) owner;
    Scene scene = go.getScene();
}
```

---

## Files Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `core/IGameObject.java` | NEW | Common interface for game objects |
| `core/GameObject.java` | MODIFY | Implement IGameObject, add getId(), use setOwner() |
| `editor/scene/EditorGameObject.java` | MODIFY | Implement IGameObject, add missing methods, set owner on components |
| `components/Component.java` | MODIFY | Change to IGameObject owner, add helper methods |

---

## Verification Checklist

- [ ] Components can call `getTransform()` in gizmo methods without NPE
- [ ] Components can call `getComponent()` in gizmo methods without NPE
- [ ] Runtime behavior unchanged (all tests pass)
- [ ] Editor scene loads without errors
- [ ] Gizmos render correctly for all component types
- [ ] Inspector shows component fields correctly
- [ ] No deprecation warnings in new code (only in legacy compatibility)
- [ ] `owner.isRuntime()` returns correct value in both contexts
- [ ] `owner.isEditor()` returns correct value in both contexts

---

## Related Plans

### HierarchyItem Interface (play-mode-inspection)

The `play-mode-inspection` plan defines a `HierarchyItem` interface that **extends IGameObject**:

```java
public interface HierarchyItem extends IGameObject {
    List<? extends HierarchyItem> getHierarchyChildren();
    default boolean hasHierarchyChildren() { return !getHierarchyChildren().isEmpty(); }
    default boolean isEditable() { return isEditor(); }
}
```

This means:
- `EditorGameObject implements HierarchyItem` (gets both IGameObject and hierarchy methods)
- `GameObject implements IGameObject` (just core interface, no hierarchy UI concerns)
- `RuntimeGameObjectAdapter implements HierarchyItem` (wraps GameObject for hierarchy display)

The adapter pattern is necessary because `getHierarchyChildren()` returns `List<? extends HierarchyItem>`, and GameObject's children are GameObjects (which don't implement HierarchyItem).

**Implementation order**: Complete IGameObject first, then implement HierarchyItem in play-mode-inspection.

---

## Future Considerations

### Scene Access

`IGameObject` intentionally does NOT include scene access because:
- `EditorGameObject` belongs to `EditorScene`, not `Scene`
- Scene-level operations are fundamentally different between runtime and editor

If needed later, add a separate `IScene` interface.

### Transform World Position

Both `GameObject.Transform` and `EditorGameObject.Transform` handle position differently:
- Runtime: World position computed from parent chain
- Editor: Local position stored (world position varies by parent)

The interface provides `getTransform()` but world position calculation remains context-specific.

---

## Implementation Order

1. Create `IGameObject.java` interface
2. Update `GameObject.java` to implement interface
3. Update `EditorGameObject.java` to implement interface
4. Update `Component.java` to use `IGameObject` owner
5. Update `GameObject.java` to use `setOwner()`
6. Update `EditorGameObject.java` to set owner on components
7. Test all gizmo-drawing components
8. Test inspector/hierarchy functionality
9. Remove/update any remaining `gameObject` null checks in components
