# EditorGameObject Refactor: Consolidated Design

## Status: Design Complete — Ready for Implementation Plan

## Goal

Make `EditorGameObject extends GameObject` to eliminate the dual-hierarchy architecture. This removes the `EditorUIBridge` wrapper pattern, the `IGameObject` interface, and an entire class of stale-cache bugs — while simplifying ~30+ files across the codebase.

## Background

The editor and runtime use two unrelated types (`EditorGameObject` and `GameObject`) to represent entities. They share an `IGameObject` interface but have incompatible hierarchies, component storage, and enabled-state semantics. The `UIRenderer` only accepts `GameObject`, so `EditorUIBridge` creates temporary wrapper GameObjects that mirror the EditorGameObject hierarchy, moving components back and forth via `setOwner()`. This wrapper pattern is the root cause of stale cache bugs (scrollbar visibility, hierarchy caching, ownership confusion).

The fix: EditorGameObject extends GameObject. It IS a GameObject, so UIRenderer traverses it directly. No wrappers, no bridge, no dual ownership.

### Architecture: Before vs After

```
BEFORE (current):
                    IGameObject (interface)
                   /            \
        GameObject               EditorGameObject
        (runtime)                (editor, flat list)
             |                         |
        UIRenderer               EditorUIBridge
        traverses GO              creates wrapper GOs
        hierarchy                 mirrors EGO hierarchy
                                  moves components via setOwner()
                                       |
                                  UIRenderer
                                  traverses wrapper GOs

    Component has two fields: IGameObject owner + GameObject gameObject
    gameObject is null when owned by EditorGameObject

AFTER (refactored):
                      GameObject
                          |
                  EditorGameObject
                  (extends GameObject)
                          |
                     UIRenderer
                     traverses EGOs directly
                     (they ARE GameObjects)

    Component has one field: GameObject gameObject (always non-null)
    No wrappers, no bridge, no dual ownership
```

### Implementation Order

```
Pre-work ─────────┐
(SceneManager)     ├──→ Phase B ──→ Phase C
Phase A ──────────┘    (hierarchy    (cleanup +
(prefab model)          + bridge      rendering
                        deletion)     rewrite)

Pre-work and Phase A can run in parallel (different code areas).
Phase B requires both. Phase C requires Phase B.
```

---

## Systems That Need Changes

### 1. EditorGameObject (HIGH complexity)

**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

**Why:** Core of the refactor. Must become a subclass of GameObject while preserving editor-specific behavior (prefab overrides, flat serialization, persistent IDs).

**Changes:**
- Class declaration: `extends GameObject implements HierarchyItem`
- Remove own `name`, `enabled`, `parent`, `children` fields — use inherited from GameObject (see Section 12 field shadowing rule)
- Override ~8 methods to customize behavior:

| Method | Override reason |
|--------|---------------|
| `getId()` | Return persistent UUID instead of computed hash |
| `setEnabled(boolean)` | Calls `setEnabledDirect()` — skip runtime component notifications and Scene cache updates |
| `getTransform()` | Return the real Transform from inherited components list (no special handling needed after Phase A) |
| `setParent(GameObject)` | **Override** (not overload) `GameObject.setParent(GameObject)`. Calls `super.setParent()` for core hierarchy mutation (scene registration naturally skipped via null `SceneManager.getActiveScene()`). Additionally: reject non-EditorGameObject parents with `IllegalArgumentException`, update `parentId` for serialization, call `UITransform.clearLayoutOverrides()` if present. **Auto-swap Transform↔UITransform:** if new parent is a UI context (has `UICanvas` or `UITransform`), swap `Transform` → `UITransform`; if moving out of UI context, swap `UITransform` → `Transform`. Replaces manual inspector button. The old `setParent(EditorGameObject)` overload is **deleted**. |
| `update(float)` | No-op — editor entities don't run component lifecycle |
| `lateUpdate(float)` | No-op |
| `start()` | No-op |
| `destroy()` | No-op (or editor-specific cleanup if needed later) |

**Not overridden** (inherited from GO):
- `isEnabled()` — raw field getter. See Section 12 for `isActiveInHierarchy()`.
- `getComponents(Class)` — all entities have real components after Phase A.

- Delete `getChildrenMutable()`, `getEditorChildren()`, `getEditorParent()` from EditorGameObject — not needed. `getChildren()` returns `List<GameObject>`, editor code uses it directly. The ~5 prefab serialization sites that need `EditorGameObject` type cast inline. Children mutations go through `setParent()` or dedicated methods (e.g. `removeAllChildren()`)
- Remove reimplemented methods that now come from `super`: `hasChildren()`, `isAncestorOf()` (made public on GameObject), basic scratch-entity component management
- Add `replaceComponent(Component old, Component new)` on EditorGameObject — used by `SwapTransformCommand` and `ReparentEntityCommand` instead of direct list mutation
- Rework `addComponent(Component)` to use inherited components list directly
- Remove `GameObject`'s unsafe generic no-arg `<T> List<T> getComponents()` — add `getAllComponents()` returning `List<Component>` instead. Callers of the no-arg version migrate to `getAllComponents()`
- `isRuntime()` returns `false`, `isEditor()` returns `true`

**Call-site changes (~50+ mechanical):**
- `entity.getChildren()` returns `List<GameObject>` — most editor code works without change (methods like `getId()`, `getOrder()`, `setParent()`, `getComponent()` are all on GameObject)
- ~5 prefab serialization sites cast inline: `(EditorGameObject) child`
- `entity.getParent()` returns `GameObject` — cast inline where EGO-specific methods needed (rare)
- Runtime code (`UIRenderer`, `LayoutGroup`, etc.) uses `getChildren()` returning `List<GameObject>` — unchanged

### 2. Prefab Override Model (HIGH complexity)

**File:** `EditorGameObject.java` (internal storage model)

**Why:** The current virtual-component model (no actual components for prefab instances, just override maps) conflicts with inheriting from GameObject, which expects real components. Simplifying the model removes the main obstacle.

**Current model:**
```java
Map<String, Map<String, Object>> componentOverrides;  // virtual components
transient List<Component> cachedMergedComponents;       // cloned on demand from template
```

**New model:**
```java
List<Component> components;                             // real instances (inherited from GameObject)
Map<String, Set<String>> overriddenFields;              // componentType -> {field1, field2}
```

**Changes:**
- Prefab instances clone components from template **at creation time** and store them in the inherited `components` list
- `overriddenFields` tracks which fields the user changed (for inspector bold/reset/serialization)
- Eliminate `getMergedComponents()`, `cachedMergedComponents`, `invalidateComponentCache()`
- Eliminate most of the 12+ `isScratchEntity()`/`isPrefabInstance()` branches (unified component path)
- Position/rotation/scale stored in actual Transform component, not as float arrays in override map
- `ensureOwnerSet()` deferred ownership eliminated — components owned immediately
- Delete `getFieldValue(componentType, fieldName)` and `setFieldValue(componentType, fieldName, value)` — callers use `ComponentReflectionUtils` directly on real components. 4 callers migrate to direct component access + `markFieldOverridden()`
- Delete dead `SetFieldCommand` undo command (zero callers)

**What stays:**
- `isPrefabInstance()` — still has `prefabId`
- `markFieldOverridden(type, field)` — adds field name to `overriddenFields` Set
- `isFieldOverridden(type, field)` — checks `overriddenFields` Set
- `resetFieldToDefault(type, field)` — loads prefab default, applies it onto real component, removes from Set
- `getFieldDefault(type, field)` — reads default value from prefab template
- Inspector bold styling, reset buttons — work the same way
- Serialization: save `prefabId` + only overridden field values (read from real components at save time)

**Template change propagation:** When a prefab template is saved, instances are explicitly refreshed via `refreshFromTemplate()` (see Phase A for full design). Current silent auto-refresh via cache invalidation is replaced by a clearer mechanism — instances re-clone from the updated template while preserving overridden field values.

### 3. EditorUIBridge (LOW complexity — deletion)

**File:** `src/main/java/com/pocket/rpg/rendering/ui/EditorUIBridge.java` (~308 lines)

**Why:** The wrapper pattern exists solely because EditorGameObject wasn't a GameObject. With inheritance, UIRenderer traverses EditorGameObjects directly.

**Change:** Delete entirely. All ~308 lines removed.

**What it did that's no longer needed:**
- Created temporary `GameObject` wrappers mirroring EditorGameObject hierarchy
- Moved components via `comp.setOwner(wrapper)` (root cause of stale caches)
- Used reflection hacks (`getDeclaredField("components")`, `getDeclaredField("transform")`) to bypass lifecycle
- Rebuilt wrapper hierarchy every frame

### 4. IGameObject Interface (LOW complexity — deletion)

**File:** `src/main/java/com/pocket/rpg/core/IGameObject.java` (~125 lines)

**Why:** Created specifically because EditorGameObject and GameObject didn't share a class hierarchy. With inheritance, the interface serves no purpose.

**Change:** Delete entirely. Update files that reference it (mechanical — signature change from `IGameObject` to `GameObject`):
- `Component.owner` field deleted — `gameObject` field kept as single reference
- `Component.setOwner(IGameObject)` → `setGameObject(GameObject)` — single field assignment
- `UIComponent.setOwner(IGameObject)` → override `setGameObject(GameObject)` (calls super + marks cache dirty)
- `UIScrollbar.setOwner(IGameObject)` → override `setGameObject(GameObject)` (calls super + marks cache dirty)
- `UIScrollView.setOwner(IGameObject)` → override `setGameObject(GameObject)` (calls super + marks cache dirty)
- `UITransform` uses `owner.getParent()`, `owner.getChildren()` → `GameObject` (lines 648, 828, 1096 — iteration variable type change)
- `AlphaGroup.applyAlphaToComponents(IGameObject)` → `GameObject`
- `UIDesignerPanel.submitPickingHierarchy(IGameObject)` → `GameObject`
- `ReflectionFieldEditor.hasComponentOfType(IGameObject)` → `GameObject`
- `HierarchyItem extends IGameObject` → standalone (see below)
- `EntityInspector` — import removal
- `CustomComponentEditorRegistry` line 86 — `instanceof EditorGameObject` check (move to Phase C cleanup)

### 5. HierarchyItem Interface (MEDIUM complexity)

**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyItem.java`

**Why:** Currently `extends IGameObject`. When IGameObject is deleted, HierarchyItem must declare its own methods.

**Change:** Standalone interface declaring:
```java
public interface HierarchyItem {
    String getName();
    String getId();
    <T extends Component> T getComponent(Class<T> type);
    <T extends Component> List<T> getComponents(Class<T> type);
    List<Component> getAllComponents();
    boolean isEnabled();             // raw own-enabled flag (for toggle checkboxes)
    boolean isActiveInHierarchy();   // walks parent chain (for graying out disabled items)
    boolean hasChildren();
    HierarchyItem getHierarchyParent();
    List<? extends HierarchyItem> getHierarchyChildren();
    boolean isEditor();
    boolean isEditable(); // default delegates to isEditor()
    // existing hierarchy-specific methods stay (findComponentInParent, hasHierarchyChildren)
}
```

**Implementors:**
- `EditorGameObject` — implements via `GameObject` methods + own hierarchy methods
- `RuntimeGameObjectAdapter` — implements via delegation to wrapped `GameObject` (same as today, fewer methods)

### 6. Component Base Class (MEDIUM complexity)

**File:** `src/main/java/com/pocket/rpg/components/Component.java`

**Why:** Currently has two fields (`IGameObject owner` + `GameObject gameObject`) because EditorGameObject wasn't a GameObject. With inheritance, only one field is needed.

**Changes:**
- Delete `protected IGameObject owner` field
- Keep `protected GameObject gameObject` as the single field
- Rename `setOwner(IGameObject)` → `setGameObject(GameObject)` — single field assignment (`this.gameObject = gameObject`), no instanceof check
- Update ~10 references to `owner` in `Component.java` to `gameObject`
- All 91 existing `this.gameObject.xyz()` usages across components remain unchanged
- `isRuntime()` / `isEditor()` move to `GameObject` with defaults (`isRuntime() = true`), EditorGameObject overrides

### 7. RuntimeGameObjectAdapter (LOW complexity — simplification)

**File:** `src/main/java/com/pocket/rpg/editor/scene/RuntimeGameObjectAdapter.java`

**Why:** NOT deleted. Still needed to wrap runtime GameObjects for play mode hierarchy display AND property editing. Play mode inspectors allow editing component properties.

**Change:** Implements standalone `HierarchyItem` (instead of `HierarchyItem extends IGameObject`). Fewer methods to delegate since IGameObject methods it implemented are removed. Add defensive guard in `of(GameObject)`: if `gameObject instanceof EditorGameObject`, throw `IllegalArgumentException` — EGO IS-A GO after refactor but should never be wrapped by the adapter.

### 8. AlphaGroup (LOW complexity)

**File:** `src/main/java/com/pocket/rpg/components/ui/AlphaGroup.java`

**Why:** Has separate `applyAlphaToGameObject()` and `applyAlphaToEditorGameObject()` — identical logic, different types. With type unification, one method suffices.

**Change:** Merge into a single `applyAlpha(GameObject)` recursive method (~18 lines removed).

### 9. RenderDispatcher (LOW complexity)

**File:** `src/main/java/com/pocket/rpg/rendering/RenderDispatcher.java` (or similar)

**Why:** Has `instanceof EditorGameObject` dispatch to a dedicated `submitEditorGameObject()` method.

**Change:** Unified rendering path. Remove `instanceof` check and dedicated editor method.

### 10. UIDesignerPanel / GameViewPanel (MEDIUM complexity)

**Files:** `src/main/java/com/pocket/rpg/editor/panels/UIDesignerPanel.java`, GameViewPanel

**Why:** Currently create and manage `EditorUIBridge` instances, call `bridge.getRoot()` for wrapper hierarchy, pass wrappers to UIRenderer.

**Change:** Remove all bridge code. Collect UI canvases directly from EditorGameObjects and pass them to UIRenderer. UIRenderer traverses EditorGameObjects directly since they ARE GameObjects.

### 11. EditorScene.resolveHierarchy() (LOW complexity)

**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorScene.java`

**Why:** Currently builds the EditorGameObject parent/children hierarchy from `parentId` strings into EditorGameObject-specific fields. With unified hierarchy, uses the inherited `setParent()`.

**Change:** Use EditorGameObject's overridden `setParent()` which manages the inherited list AND updates `parentId`.

### 12. GameObject Protected Helpers and Field Access (LOW complexity)

**File:** `src/main/java/com/pocket/rpg/core/GameObject.java`

**Why:** All fields on `GameObject` stay **private**. `EditorGameObject` needs controlled access for its `setEnabled()` override without triggering runtime lifecycle. All other access goes through inherited public methods.

**Changes:**

Two protected helpers:
```java
/**
 * Editor-only: Sets enabled field without triggering component lifecycle notifications,
 * Scene cache updates, or child propagation. Used by EditorGameObject.setEnabled() override.
 * Do NOT use in runtime code — use setEnabled(boolean) instead.
 */
protected void setEnabledDirect(boolean enabled) { ... }

/**
 * Inserts a component at a specific index in the components list.
 * Used by EditorGameObject for undo operations that restore component order.
 */
protected void addComponentAt(int index, Component component) { ... }
```

New public method on `GameObject`:
```java
/**
 * Returns true if this GameObject and ALL ancestors are enabled.
 * Walks the parent chain. Returns false if any ancestor is disabled.
 * Use isEnabled() for the raw field value on this object only.
 */
public boolean isActiveInHierarchy() {
    if (!enabled) return false;
    GameObject current = parent;
    while (current != null) {
        if (!current.enabled) return false;
        current = current.parent;
    }
    return true;
}
```

**Enabled state semantics after refactor:**
- `isEnabled()` — raw field getter (Lombok). Returns this object's own enabled flag only. Does NOT check parents.
- `isActiveInHierarchy()` — walks parent chain. Returns true only if this AND all ancestors are enabled.
- Both methods live on `GameObject`. EGO inherits both, overrides neither.
- EGO's current `isEnabled()` override (walks parents) is **deleted**. EGO's `isOwnEnabled()` is **deleted**.

**Migration impact (~20 call sites in editor):**

Calls that check visibility/rendering (currently `entity.isEnabled()` which walks parents via EGO override) → migrate to `entity.isActiveInHierarchy()`:
- `GizmoRenderer.java` — 4 sites (skip disabled entities for gizmo drawing)
- `EditorScene.java` — 2 sites (render visibility checks)
- `UIDesignerPanel.java` — 1 site (skip disabled GOs)
- `UIDesignerGizmoDrawer.java` — 2 sites (skip disabled in UI gizmos)
- `UIDesignerInputHandler.java` — 1 site (skip disabled in input handling)
- `HierarchyTreeRenderer.java` — 1 site (gray out hierarchically disabled items)
- `EntityInspector.java` — 1 site (styling for disabled entity)

Calls that check the entity's own toggle (currently `entity.isOwnEnabled()`) → migrate to `entity.isEnabled()`:
- `EditorGameObject.java` — 1 site (sprite renderer check)
- `EditorSceneSerializer.java` — 1 site (serialize own enabled flag)
- `HierarchyTreeRenderer.java` — 3 sites (toggle checkbox, eye icon)
- `EditorShortcutHandlersImpl.java` — 1 site (Ctrl+E toggle)
- `ComponentListRenderer.java` — 1 site (component enable checkbox)
- `EntityInspector.java` — 1 site (inspector enabled checkbox)

No change needed:
- `component.isOwnEnabled()` calls (on `Component`, not EGO) — Component has its own method, unaffected.
- `UIRenderer.java` calls `isEnabled()` on Components, not GOs — unaffected.

Make `isAncestorOf(GameObject)` **public** (currently private) — used by EGO's `setParent()` override for circular reference detection. EGO deletes its own `isAncestorOf(EditorGameObject)`.

**Access patterns after refactor:**
- `EditorGameObject.setEnabled()` → calls `setEnabledDirect()`
- `EditorGameObject.setParent()` → calls `super.setParent()` (scene registration naturally skipped via null `SceneManager.getActiveScene()`) + EGO-specific logic (parentId update, UITransform cache clear, auto-swap)
- `getParent()` and `getChildren()` → inherited directly, no helpers needed
- `getName()`/`setName()` → inherited via Lombok, no helpers needed
- `isEnabled()` and `isActiveInHierarchy()` → inherited directly, no helpers needed
- `isAncestorOf()` → inherited (now public), no helpers needed
- EditorGameObject constructors must NOT re-initialize `children` — `super()` already creates the list

**Field shadowing rule:** EditorGameObject must NOT declare its own `name`, `enabled`, `parent`, `children`, or `components` fields. All are inherited from GameObject. Access is through inherited getters/setters, `super.setParent()`, `setEnabledDirect()`, or the reworked `addComponent()`/`replaceComponent()`.

### 13. isRuntime() / isEditor() (LOW complexity)

**Files:** `GameObject.java`, `EditorGameObject.java`

**Why:** Currently defined on `IGameObject`. Move to `GameObject` when IGameObject is deleted.

**Change:**
- `GameObject`: `isRuntime() = true`, `isEditor() = false` (defaults)
- `EditorGameObject`: overrides `isRuntime() = false`, `isEditor() = true`

### 14. `getOrder()` / `setOrder()` move to GameObject (LOW complexity)

**Files:** `GameObject.java`, `EditorGameObject.java`

**Why:** Sibling order matters for both runtime (render order, child iteration) and editor (hierarchy display, sorting). Currently only on EditorGameObject.

**Change:** Move `order` field and `getOrder()`/`setOrder()` to `GameObject`. Eliminates the need for `EditorGameObject` typed children in sorting code (~8 call sites).

---

## Systems That DON'T Need Changes

### Undo System (34 commands)

All undo commands store `EditorGameObject` references explicitly. No command accepts generic `GameObject`. EditorGameObject is still EditorGameObject — just also a GameObject now. Play mode creates a separate `GameEngine` with deep-copied data via JSON serialization — never touches the undo stack. `pushScope()`/`popScope()` isolates prefab edits. Type-safe by construction.

### Selection System

Stores `Set<EditorGameObject>`. EditorGameObject is still the same type, just with a superclass. No changes.

### Tools (move, rotate, scale, select)

Operate on `EditorGameObject` references from Selection. Unchanged.

### Inspector Panels

Custom inspectors receive `HierarchyItem` — this stays the same. Play mode allows editing component properties, so inspectors must work with both `EditorGameObject` (editor) and `RuntimeGameObjectAdapter` (play mode). `CustomComponentInspector.entity` stays typed as `HierarchyItem`. The `editorEntity()` helper stays for prefab-specific operations. `FieldEditorContext` already stores `EditorGameObject` — unchanged.

### Picking System

Maps `int -> EditorGameObject`. ID mapping unchanged. Note: the picking *rendering* pass changes in Phase C (reads SpriteRenderer directly instead of `resolveEntityGeometry()`) but the picking data model stays the same.

### Gizmo System

Iterates components on entities. Works the same.

### Serialization Format

`GameObjectData`/`SceneData` are explicit builders — `toData()` / `fromData()`. JSON format unchanged. No field leakage from GameObject superclass (serialization is manual, not reflection-based).

### Play Mode Boundary

`RuntimeSceneLoader` serializes EditorScene -> SceneData (JSON) -> fresh GameObjects. This intentional separation is preserved. EditorGameObjects are never reused at runtime.

### PrefabEditController

Uses EditorGameObject for working entity. Unchanged.

### EntityCreationService / UIEntityFactory

Create EditorGameObjects. Unchanged — EditorGameObject still exists as a class.

### Copy/Paste/Duplicate

Uses EntityCreationService. Unchanged.

### HierarchyPanel

Uses `HierarchyItem` interface. The interface is refactored (standalone instead of extending IGameObject) but panel code is the same.

---

## Bugs Fixed by This Refactor

### HIGH Severity: Component hierarchy access (4 components)

These components use `gameObject.getChildren()` which is null in the editor because `Component.gameObject` is only set when `owner instanceof GameObject`. They ONLY work today because EditorUIBridge creates temporary wrapper GameObjects. With EGO extending GO, `gameObject` is always non-null.

| Component | What fails without bridge | After refactor |
|-----------|--------------------------|----------------|
| **LayoutGroup.getLayoutChildren()** | Can't iterate children — layout broken | Works directly |
| **UIScrollView.refreshHierarchyCache()** | Can't find viewport/content/scrollbar | Works — no stale wrapper refs |
| **UIScrollbar.getHandle()** | Can't find handle child | Works — direct child access |
| **Transform.updateWorldMatrix()** | Recursive children traversal fails | Works — children accessible |

These are the root cause of the original scrollbar visibility bug that motivated this exploration.

### MEDIUM Severity: Stale wrapper caches

Components cache references to wrapper GameObjects. When the bridge rebuilds wrappers (every frame or on hierarchy change), cached references become stale. UIScrollView caches viewport/content/scrollbar GameObjects — if the wrapper is recreated, the cache points to the old wrapper. With no wrappers, no stale refs.

### MEDIUM Severity: Enabled state propagation

`EditorGameObject.setEnabled(false)` on a scrollbar doesn't propagate through the wrapper system. The hierarchy panel sees the EditorGameObject (disabled), but the renderer sees the wrapper (still enabled until next bridge rebuild). With one object, `setEnabled()` is seen by everyone.

### LOW Severity: Component lifecycle bypass

EditorUIBridge moves components to wrappers without calling `onStart()`/`onEnable()`. Components on wrapper GameObjects skip lifecycle. With real ownership, lifecycle works correctly if/when it's needed.

---

## Code Impact Summary

### Deleted (~540+ lines)

| File | Lines | Reason |
|------|-------|--------|
| EditorUIBridge.java | ~308 | Wrapper pattern eliminated |
| IGameObject.java | ~125 | Bridge interface unnecessary |
| AlphaGroup dual methods | ~18 | Type unification |
| Component.owner field + instanceof | scattered | Single owner type |
| EGO Renderable impl | ~60 | `getCurrentSprite()`, `getCurrentSize()`, `getZIndex()`, `isRenderVisible()`, `DEFAULT_ENTITY_Z_INDEX` |
| RenderDispatcher EGO methods | ~50 | `submitEditorGameObject()`, `resolveEntityGeometry()`, `ResolvedGeometry` |

### Simplified (~200+ lines across 10+ files)

| Area | Before | After |
|------|--------|-------|
| Component.setGameObject() | IGameObject, instanceof, two fields → `setOwner()` | GameObject, single field → `setGameObject()` |
| UIDesignerPanel/GameViewPanel | Bridge creation/management | Direct canvas collection |
| RenderDispatcher | instanceof dispatch + dedicated method | Unified path |
| AlphaGroup | 3 methods (dispatch + 2 type-specific) | 1 recursive method |
| HierarchyItem | Extends IGameObject | Standalone interface |
| ~30+ components | Null-check gameObject | gameObject always non-null |
| 5 instanceof checks | AlphaGroup, RenderDispatcher, ReflectionFieldEditor, UITransformInspector, CustomComponentInspector | Eliminated or replaced with `isEditor()`/`isRuntime()` |

### EditorGameObject internal reduction (~300+ lines)

Reimplemented methods that can delegate to `super` or be removed:
- Hierarchy: `hasChildren()`, `isAncestorOf()`, basic `setParent()` logic (~120 lines)
- Component management: `addComponent()`, `removeComponent()`, `getComponent()` for scratch entities (~150 lines)
- Enabled state: `setEnabled()` / `isEnabled()` / `isOwnEnabled()` base logic (~70 lines) — replaced by inherited `isEnabled()` (raw field) + `isActiveInHierarchy()` (parent chain) from GO, plus `setEnabledDirect()` for the `setEnabled()` override

Not all unifies — prefab-aware component merging is genuinely different. But scratch-entity paths can use inherited behavior directly.

---

## Implementation Phases

### Pre-work: Remove `scene` field from GameObject

**Scope:** Decouple components from `GameObject.scene` back-reference. Adopt Unity-style static `SceneManager.getActiveScene()`.

**Why:** `GameObject` currently holds a `Scene scene` field set by `Scene.addGameObject()`. After EGO extends GO, editor entities would inherit this field but it's never set → null. Rather than adding null guards everywhere, remove the coupling entirely.

**Changes:**
1. Add static `SceneManager.getActiveScene()` method. `SceneManager` is currently a regular class (not a singleton, no static access). Must add:
   - `private static Scene activeScene` field + `public static Scene getActiveScene()` getter
   - `private static void setActiveScene(Scene)` and `private static void clearActiveScene()` — private setters for clear intent
   - Set via `setActiveScene(scene)` in `loadSceneInternal()` at line 180, **before** `initialize()` — so components can access the scene during `start()`
   - Cleared via `clearActiveScene()` in destroy/unload path
   - Named `activeScene`/`getActiveScene()` to distinguish from instance `currentScene`/`getCurrentScene()`
   - Add `public static void setActiveSceneForTest(Scene)` for test setup (package-private or public)
   - Comment documenting limitations: single active scene, not thread-safe, no additive scene support yet
2. Replace all `gameObject.getScene()` calls in components with `SceneManager.getActiveScene()`
3. Remove `scene` field, `getScene()`, `setScene()` from `GameObject`
4. Update `Scene.addGameObject()` / `removeGameObject()`:
   - Delete `obj.setScene(this)` / `obj.setScene(null)` calls
   - Replace `obj.getScene() != null` guard with `gameObjects.contains(obj)` — same protection with single active scene
   - `removeGameObject()` becomes a thin wrapper: calls `obj.destroy()` (self-removing via `removeFromScene`)
5. Update `GameObject.setEnabled()` to use `SceneManager.getActiveScene()` for cache registration (replaces `this.scene`). If no active scene (editor), cache block is skipped naturally.
6. Simplify `GameObject.setParent()`: delete `this.scene`/`newParent.scene` field access, delete `setSceneRecursive()`, delete old-scene/new-scene comparison logic. Replace with single unregister+register against the current scene: `Scene scene = SceneManager.getActiveScene(); if (scene != null) { scene.unregisterCachedComponents(this); scene.registerCachedComponents(this); }`. One scene at a time — no cross-scene parenting. Editor case: `getCurrentScene()` returns null, block skipped naturally.
7. Make `GameObject.destroy()` self-removing from the scene. Currently `destroy()` only cleans up children/components/parent — callers must separately call `scene.removeGameObject()` which is error-prone and easy to forget. After this change:
   - Add `if (destroyed) return;` re-entrancy guard at top of `destroy()`
   - Add `Scene.removeFromScene(GameObject)` helper — removes from `gameObjects` list + calls `unregisterCachedComponents()`. No `destroy()` call. Clean separation of scene-detachment from object cleanup.
   - `GameObject.destroy()` calls `SceneManager.getActiveScene()` → `scene.removeFromScene(this)` before cleaning up children/components/parent
   - `Scene.removeGameObject()` becomes a thin wrapper: calls `obj.destroy()` (which internally calls `removeFromScene`)
   - Components that need to remove themselves just call `gameObject.destroy()` — no scene access needed

**Affected components:**

| Component | Usage | Replacement |
|-----------|-------|-------------|
| `GridMovement` | `getCollisionSystem()`, `getTriggerSystem()` | `SceneManager.getActiveScene().getCollisionSystem()` |
| `Door`, `InteractableComponent`, `InteractionController`, `StaticOccupant`, `TriggerZone` | `getCollisionSystem().getTileEntityMap()` | Same pattern |
| `WarpZone` | `getCamera()`, `getGameObjects()` | `SceneManager.getActiveScene().getCamera()` etc. |
| `SpawnPoint` | `getGameObjects()` | `SceneManager.getActiveScene().getGameObjects()` |
| `ItemPickup`, `DialogueEventListener`, `SaveManager` | `gameObject.getScene().removeGameObject(gameObject)` | `gameObject.destroy()` — self-removing |
| `PlayerDialogueManager` | `getComponentsImplementing(IPausable.class)` | `SceneManager.getActiveScene().getComponentsImplementing()` |
| `PlayerCameraFollow` | `getCamera()` | `SceneManager.getActiveScene().getCamera()` |
| `PlayerMovement` | `getScene()` | `SceneManager.getActiveScene()` |
| `UICanvas` | `markCanvasSortDirty()` | `SceneManager.getActiveScene().markCanvasSortDirty()` |
| `TriggerContext` | `entity.getScene()` | `SceneManager.getActiveScene()` |
| `Scene` | `setScene(this)` / `setScene(null)` | Remove — no longer needed |

**Note:** This table is representative, not exhaustive. Grep for `getScene()` during implementation — there are 48 call sites across 17 files. Some components have multiple usage patterns (e.g., `DialogueEventListener` uses both `removeGameObject` and `getComponentsImplementing`). Add null guards (`Scene scene = SceneManager.getActiveScene(); if (scene == null) return;`) as needed.

**Can be done independently** — no dependency on other phases. Purely runtime-side cleanup. **Can run in parallel with Phase A** (different code areas: Pre-work touches `GameObject.scene` + component scene access; Phase A touches `EditorGameObject` internals + prefab serialization).

### Phase A: Prefab Simplification

**Scope:** EditorGameObject internal refactor only. No class hierarchy change.

- Replace virtual component model with actual components + `overriddenFields` Set
- Prefab instances clone components at creation time
- Remove `getMergedComponents()`, `cachedMergedComponents`, `invalidateComponentCache()`
- Simplify `isScratchEntity()` branches where possible
- Update serialization: save `prefabId` + diff overridden fields at save time
- Delete dead transform helpers that become unnecessary with real components:
  - `getTransformVector(String)`, `setTransformVector(String, Vector3f)` — virtual-component position/rotation/scale access; replaced by direct `Transform` field access
  - `findCachedTransform()` — finds Transform in cached merged components; no longer needed since components are real
  - `syncCachedTransformPosition()`, `syncCachedTransformRotation()`, `syncCachedTransformScale()` — syncs override map values to cached clone; eliminated with override map
  - `TRANSFORM_TYPE` constant — string key for Transform in override map
  - Simplify remaining position/rotation/scale helpers to delegate directly to the real Transform component (~100 lines removed)

**Serialization strategy:** No scene file migration needed. The `.scene` JSON format stays the same (`prefabId` + `componentOverrides`). Load and save convert between the on-disk format (values) and the in-memory format (names only):

**On load (`fromData()`):**
1. Read `componentOverrides` from JSON — `{ "SpriteRenderer": { "zIndex": 15 } }` (has values)
2. Clone all components from prefab template (gets defaults)
3. Apply each override value onto the cloned component via reflection
4. Build `overriddenFields` Set from the override keys (field names only — values are now on the components)

**On save (`toData()`):**
1. For each entry in `overriddenFields` (component type → set of field names)
2. Read the current value from the real component via `ComponentReflectionUtils.getFieldValue()`
3. Write as `componentOverrides` to JSON — same format as before

**Migration: None required.** The on-disk format is unchanged:
- `.scene` files still store `prefabId` + `componentOverrides` map with values — the load/save boundary converts to/from the in-memory model
- `.prefab.json` files are untouched — they define the template that gets cloned
- Existing scenes open without any conversion step. The first save after the refactor produces identical JSON.

**Undo command rework:** All prefab-related undo commands need updating:
- Drop `Component` field from all commands — store `componentType` string instead. Look up the real component each time via `entity.findComponentByType(componentType)`. This prevents stale references after re-cloning.
- Commands check `isPrefabInstance()`:
  - `false` (scratch entity or prefab template working copy) → set/restore field on real component only
  - `true` (prefab instance in scene) → set/restore field on real component + add/remove from `overriddenFields` Set

Commands to update:

| Command | Today (execute / undo) | After Phase A (execute / undo) |
|---------|------------------------|--------------------------------|
| `SetComponentFieldCommand` | **exec:** set field on cached clone + `syncOverride()` + `invalidateComponentCache()` / **undo:** restore old value + `syncOverride()` + `invalidateComponentCache()` | **exec:** look up component by type, set field + `markFieldOverridden()` / **undo:** look up component by type, restore old value + remove from `overriddenFields` (if value matches template default) |
| `ResetFieldOverrideCommand` | **exec:** remove from override map + `invalidateComponentCache()` / **undo:** put saved value back in override map + `invalidateComponentCache()` | **exec:** look up component by type, copy default from template onto it + remove from `overriddenFields` / **undo:** look up component by type, restore saved value + add back to `overriddenFields` |
| `ResetAllOverridesCommand` | **exec:** clear override map + `invalidateComponentCache()` / **undo:** restore saved override map + `invalidateComponentCache()` | **exec:** re-clone all components from template + clear `overriddenFields` / **undo:** restore saved field values on re-looked-up components + rebuild `overriddenFields` from saved set |

The core change: drop `Component` references in favor of `componentType` string lookup, replace all `componentOverrides` map reads/writes with `overriddenFields` Set updates, and replace `invalidateComponentCache()` calls with `markFieldOverridden()`/removal.

The `overriddenFields` Set is needed to distinguish "user explicitly changed this" from "still at template default" — so that template updates propagate correctly to non-overridden fields.

**Clarification on prefab edit mode:** When editing a prefab template, `PrefabEditController` creates temporary scratch entities (no `prefabId`) from the template data. These are regular `EditorGameObject`s — `isPrefabInstance()` returns `false`. The controller manages the "this represents a prefab template" context externally and writes changes back to the `.prefab` file on save.

**Template change propagation:** When a prefab template is saved, all instances of that prefab must be updated to reflect the new template.

*Open scene:* `EditorGameObject.refreshFromTemplate()` handles this:
1. Capture current values of all fields in `overriddenFields`
2. Re-clone all components from the updated template (replacing the stale clones)
3. Re-apply the captured overridden values on top of the fresh clones

`PrefabEditController.save()` calls `refreshFromTemplate()` on every entity in the open scene whose `prefabId` matches the saved template. A status bar message is shown (e.g., "Refreshed N prefab instances"). This replaces the current `invalidateInstanceCaches()` call — that method and the lazy cache rebuild via `cachedMergedComponents` are deleted (no longer needed since instances hold real components).

For hierarchical prefabs, the same logic applies per child node using `prefabNodeId`.

Edge cases:
- Template adds a new component → instance gets it (fresh clone, no override)
- Template removes a component → instance loses it; any overrides on it are silently dropped
- Template renames a field → override for old name is silently dropped

*Closed scenes:* No action needed. Scene files store `prefabId` + sparse overridden field diffs (not full component snapshots). When a closed scene is opened, `fromData()` clones from the latest template and applies overrides. Closed scenes are always up to date by default.

**Testing:** Include a round-trip test that loads an existing `.scene` file with prefab instances, verifies all override values are correct on the cloned components, saves back, and confirms JSON output matches the expected format. Additionally, test `refreshFromTemplate()`: save a template with a changed default, verify non-overridden fields pick up the new value while overridden fields are preserved.

**Risk:** Highest risk phase. Prefab system touches inspector, serialization, undo, creation. Thorough testing needed.

**Can be done independently** — no dependency on other phases.

### Phase B: EditorGameObject extends GameObject + Bridge Elimination

**Scope:** Class hierarchy change and bridge deletion.

**Changes:**

Hierarchy change:
- `EditorGameObject extends GameObject implements HierarchyItem`
- All fields stay **private** on GameObject; add `setEnabledDirect(boolean)` and `addComponentAt(int, Component)` protected helpers
- Make `isAncestorOf(GameObject)` public on GameObject
- Remove GO's unsafe generic no-arg `<T> List<T> getComponents()`; add `getAllComponents()` returning `List<Component>`
- Override ~8 methods (getId, setEnabled, getTransform, setParent + 4 lifecycle no-ops: update, lateUpdate, start, destroy). Note: `isEnabled()` and `getComponents(Class)` are NOT overridden — inherited from GO.
- Add `replaceComponent(Component old, Component new)` on EditorGameObject
- Rework `addComponent()` to use inherited components list
- Remove reimplemented methods that duplicate `super` (hasChildren, isAncestorOf, scratch-entity component management)
- Delete EGO's own `components` field — use inherited from GameObject (field shadowing rule)
- Move `getOrder()`/`setOrder()` to GameObject
- Add `isRuntime()`/`isEditor()` to GameObject, override in EditorGameObject
- Delete `clearParent()` from EditorGameObject — migrate the single caller to `setParent(null)` (the overridden `setParent()` handles `parentId` update and child list removal)
- Rewrite `getDepth()` to walk `getParent()` chain instead of using `editorParent` field (no longer exists)
- Delete `selectedObject` field from EditorScene — legacy dead field, selection managed via `selectedEntities`
- Delete `hierarchyVersion` field, `getHierarchyVersion()`, and `bumpHierarchyVersion()` from EditorScene — bridge-specific invalidation signal, unnecessary without wrapper pattern. Delete associated tests
- Delete `ensureOwnerSet()` and all call sites — deferred ownership hack for virtual component model; after Phase A all components have real owners immediately
- Update call sites (~50+ mechanical changes — `getChildren()` ~30+, `getParent()` ~20+, with inline casts at ~5 prefab serialization sites)

Bridge elimination:
- Delete `EditorUIBridge.java` (~308 lines)
- Delete `IGameObject.java` (~125 lines)
- Update `Component`: delete `owner` field, keep `gameObject` typed as `GameObject`, rename `setOwner()` → `setGameObject()`
- Migrate all `getOwner()` callers to `getGameObject()` — mechanical rename across ~20 call sites
- Make `HierarchyItem` standalone interface (with `isEditor()` method)
- Simplify `RuntimeGameObjectAdapter`: delete orphaned `getParent()` and `getChildren()` methods (these were `IGameObject` interface methods; standalone `HierarchyItem` uses `getHierarchyParent()` / `getHierarchyChildren()` instead)
- Update UIDesignerPanel/GameViewPanel: remove bridge code, pass EditorGameObjects directly to UIRenderer
- Update 14 files referencing IGameObject (mechanical signature changes)
- Clean stale comments referencing deleted patterns (wrapper caches, bridge rebuilds, dual-owner model, `IGameObject`)

**Depends on:** Pre-work (scene field removal — without it, 30+ `gameObject.getScene()` calls become NPE vectors since `gameObject` is now non-null but `scene` is null) AND Phase A (prefab simplification removes the component storage conflict)

### Phase C: Cleanup

**Scope:** Remove dead code, unify paths, fix engine→editor dependency violations.

- **Entity rendering rewrite:** `EditorGameObject` stops implementing `Renderable` — delete `getCurrentSprite()`, `getCurrentSize()`, `getZIndex()`, `isRenderVisible()`, `DEFAULT_ENTITY_Z_INDEX`. `EditorSceneRenderer` reads `SpriteRenderer` components directly from EGOs (which are now real GOs with real components after Phase A). Replaces `RenderDispatcher.submitEditorGameObject()` and `resolveEntityGeometry()`.
  - **Visibility filtering:** Replace `entity.isRenderVisible()` with inline check in `EditorSceneRenderer`: `entity.isActiveInHierarchy() && spriteRenderer != null && spriteRenderer.getCurrentSprite() != null`
  - **Tinting:** Visibility-mode tinting (SELECTED_DIMMED etc.) stays in `EditorSceneRenderer.getEntityTint()` — unchanged. SpriteRenderer's own tint is combined at submit time (same as current `submitEditorGameObject()` logic, just inlined).
  - **Z-index:** Read from `SpriteRenderer.getZIndex()` directly. No fallback to `DEFAULT_ENTITY_Z_INDEX` needed (SpriteRenderer has its own default).
  - **Broken-prefab icon:** `EditorSceneRenderer` renders a broken-link icon overlay when `entity.isPrefabInstance() && !PrefabRegistry.has(entity.getPrefabId())`. Replaces the fallback in `getCurrentSprite()`. Icon rendered as a separate sprite submission after the entity's normal sprite (if any).
  - **Picking pass:** `EditorSceneRenderer.renderPickingPass()` reads `SpriteRenderer` directly instead of calling `resolveEntityGeometry()`. Position/size/rotation from Transform + SpriteRenderer. Delete `ResolvedGeometry` record from `RenderDispatcher`.
- `RenderDispatcher` drops `instanceof EditorGameObject` branch, `submitEditorGameObject()`, `resolveEntityGeometry()`, `ResolvedGeometry` record, and `import EditorGameObject` — no more engine→editor dependency
- Unify AlphaGroup: `applyAlphaToGameObject()` and `applyAlphaToEditorGameObject()` merged into single `applyAlpha(GameObject)`. Delete the `applyAlphaInternal()` dispatch method (checked `isRuntime()` to pick which method to call — unnecessary when both types are GO). 3 methods → 1.
- Remove remaining `instanceof EditorGameObject` checks — replace with `entity.isEditor()` / `entity.isRuntime()`:
  - `ReflectionFieldEditor.hasComponentOfType()` — uses `isEditor()` to distinguish component lookup strategy
  - `UITransformInspector` — uses `isEditor()` to enable prefab-specific UI
  - `CustomComponentInspector` — uses `isEditor()` for editor-specific behavior
  - `CustomComponentEditorRegistry` — uses `isEditor()` for registration
- Simplify UIComponent.setGameObject() (remove wrapper-related cache invalidation comments)
- Remove null-checks on `gameObject` that are now guaranteed non-null
- Move `TilemapLayerRenderable` out of `RenderDispatcher` — confirmed used in `EditorSceneAdapter.java` (lines 63, 94) for dimmed/active tilemap layers. It's an editor-only record (`TilemapLayer` + `EditorGameObject`) that creates an engine→editor dependency. Move to `editor/rendering/` package. `RenderDispatcher` keeps a generic `submitRenderable()` method; `EditorSceneRenderer` calls it with `TilemapLayerRenderable` instances
- Move `FramebufferTarget` to editor package — wraps `EditorFramebuffer`, only used by editor rendering. Currently in `rendering/pipeline/` (engine package), creating an engine→editor dependency via the import. Move to `editor/rendering/`
- Change `UITransformDriver.getChildDriverInfo(HierarchyItem)` parameter to `GameObject` — the interface is in the engine package (`components/ui/`) but `HierarchyItem` is in the editor package. After the refactor, the only caller (`TransformDriverDetector`) passes `EditorGameObject` which IS-A `GameObject`, so `GameObject` is the correct parameter type. This removes the engine→editor dependency

**Depends on:** Phase B

---

## Test Strategy

Prioritize unit tests. Manual testing only for visual/rendering verification that can't be automated.

### Pre-work (SceneManager)
**Unit tests:**
- `SceneManager.getActiveScene()` returns null when no scene loaded
- `SceneManager.getActiveScene()` returns active scene after `loadScene()`
- `SceneManager.getActiveScene()` returns null after scene unloaded
- `GameObject.setEnabled()` works when no active scene (cache block skipped, no NPE)
- Components that use `SceneManager.getActiveScene()` handle null gracefully (existing null-guard pattern)

### Phase A (Prefab Simplification)
**Unit tests:**
- Round-trip: load `.scene` with prefab instances → verify override values on cloned components → save → verify JSON matches input
- `isFieldOverridden(type, field)` returns true after setting a field, false after reset
- `resetFieldToDefault()` copies template value, removes from `overriddenFields`
- `resetAllOverrides()` re-clones all components, clears `overriddenFields`
- `SetComponentFieldCommand` execute/undo on prefab instance: field value + `overriddenFields` updated and reverted correctly
- `ResetFieldOverrideCommand` execute/undo: field and `overriddenFields` correct
- `ResetAllOverridesCommand` execute/undo: all fields and `overriddenFields` correct
- Scratch entity field edit: no `overriddenFields` interaction
- Prefab edit mode working copy (`isPrefabInstance() == false`): no `overriddenFields` interaction
- Existing `EditorGameObjectPrefabNodeTest`, `PrefabSerializationFormatTest`, `PrefabEditControllerTest` pass without modification

### Phase B (Hierarchy Change + Bridge Elimination)
**Unit tests:**
- `editorGameObject instanceof GameObject` returns true
- `assertFalse(new EditorGameObject(...).isRuntime())` — regression test
- `getChildren()` returns `List<GameObject>` containing EditorGameObject instances
- `setParent(plainGameObject)` throws `IllegalArgumentException`
- `setParent(editorGameObject)` works, updates `parentId`
- `Component.setGameObject(editorGameObject)` → `gameObject` is non-null, typed as `GameObject`
- `EditorGameObject.setEnabled()` is simple field set (via `setEnabledDirect`), no component notifications
- `isEnabled()` returns raw field (inherited from GO, NOT overridden by EGO)
- `isActiveInHierarchy()` walks parent chain correctly (inherited from GO)
- Lifecycle no-ops: `update()`, `start()`, `destroy()` on EditorGameObject do nothing
- `HierarchyItem` standalone interface: both `EditorGameObject` and `RuntimeGameObjectAdapter` implement it
- `RuntimeGameObjectAdapter` delegates correctly to wrapped `GameObject`
- No references to `IGameObject` compile (verified by successful build)
- `Transform.getParentTransform()` works on EditorGameObject hierarchy (returns parent's Transform)
- `Transform.markChildrenDirty()` propagates through EditorGameObject children (root cause of scrollbar bugs — verify fixed)
- `getDepth()` returns correct depth using `getParent()` chain
- `setParent(null)` correctly clears parentId (replaces deleted `clearParent()`)
- No compilation references to `ensureOwnerSet`, `hierarchyVersion`, `selectedObject`, `clearParent`, `getOwner` (verified by grep + build)

**Manual tests (minimal):**
- UI Designer renders UI hierarchy correctly without bridge
- Selecting/editing entities in hierarchy panel works in edit and play mode

### Phase C (Cleanup)
**Unit tests:**
- `AlphaGroup.applyAlpha(GameObject)` works for both runtime and editor GameObjects (single method, no dispatch)
- No `instanceof EditorGameObject` in RenderDispatcher (verified by grep + build)
- No engine→editor imports in `RenderDispatcher`, `UITransformDriver`, or `rendering/pipeline/` package (verified by grep)
- `UITransformDriver.getChildDriverInfo(GameObject)` works with EditorGameObject argument
- `TransformDriverDetector.detect()` still functions correctly after parameter type change
- Entity with SpriteRenderer renders correctly through `EditorSceneRenderer` (no `submitEditorGameObject` path)
- Entity without SpriteRenderer is skipped (visibility filtering)
- Broken prefab instance renders broken-link icon overlay

**Manual tests (minimal):**
- Alpha propagation renders correctly in UI Designer
- Entity rendering in scene view: sprites, tinting, z-ordering all correct
- Broken prefab icon visible for invalid prefab instances
- GPU picking still selects correct entities
- Tilemap layers render with correct tinting (dimmed inactive, full active)
- Tilemap rendering works in editor (TilemapLayerRenderable moved to editor package)

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Prefab serialization regression | HIGH | Phase A has comprehensive test coverage for save/load/override round-trips |
| `instanceof GameObject` silent behavior change | HIGH | Audit required during Phase B — see TODO below |
| `gameObject.getScene()` returns null for editor | ~~MEDIUM~~ RESOLVED | Pre-work removes `scene` field; components use `SceneManager.getActiveScene()` |
| EditorGameObject constructor vs GameObject constructor | ~~MEDIUM~~ RESOLVED | `super(name)` auto-creates Transform; prefab clone updates existing Transform's values, doesn't add a second |
| Play mode isolation | LOW | JSON round-trip boundary already creates fresh GameObjects — EGOs never leak to runtime |
| Performance (unchecked casts) | NONE | Java generics erasure — zero runtime cost, `List<EditorGameObject>` and `List<GameObject>` are the same `ArrayList` at runtime |

### TODO: Audit `instanceof GameObject` across the codebase

**Why this matters:** After the refactor, `EditorGameObject IS-A GameObject`. Any existing `instanceof GameObject` check will now also match EditorGameObjects. This is a **silent behavior change** — no compiler error, just different control flow at runtime.

**The dangerous pattern** is dispatch chains where `instanceof GameObject` is checked *before* `instanceof EditorGameObject`:
```java
// BEFORE refactor: EditorGameObject falls through to else-if
// AFTER refactor: EditorGameObject matches the FIRST branch (wrong path)
if (obj instanceof GameObject go) {
    handleRuntime(go);           // <-- EditorGameObject would land here
} else if (obj instanceof EditorGameObject ego) {
    handleEditor(ego);           // <-- never reached
}
```

**How to audit:**
1. `grep -rn "instanceof GameObject" src/` — find every occurrence
2. For each match, check if the same if-else chain also checks `instanceof EditorGameObject`
   - If yes: ensure `instanceof EditorGameObject` comes **first** (more specific type first), or add an `isRuntime()` guard to the GameObject branch
   - If no: determine whether the code is runtime-only (safe) or could receive an EditorGameObject (needs a guard)
3. Check `instanceof Renderable` dispatch in RenderDispatcher — resolved in Phase C: EGO stops implementing `Renderable`, dispatch branch and `submitEditorGameObject()` deleted

**Known safe:** `AlphaGroup` (line ~56) — guarded by `owner.isRuntime()` which returns false for editor entities.
**Known safe:** `RenderDispatcher` — `instanceof EditorGameObject` branch and `submitEditorGameObject()` deleted in Phase C. `EditorSceneRenderer` reads `SpriteRenderer` directly.

### RESOLVED: Transform usage in EditorGameObject and GameObject

`super(name)` auto-creates a Transform. Prefab clone updates the existing Transform's values (position/rotation/scale) rather than adding a second. After Phase A, all EditorGameObject constructors call `super(name)` and set values on the auto-created Transform. No special constructor needed.

## Key Design Decisions

1. **One children list, not two.** EditorGameObject uses the inherited private `children` list from GameObject via the public `getChildren()` getter (returns `List<GameObject>`). No typed accessors — the ~5 prefab serialization sites that need `EditorGameObject` type cast inline. Avoids sync conflicts between parallel lists.

2. **RuntimeGameObjectAdapter stays.** Play mode inspectors allow editing component properties. The adapter wraps runtime GameObjects for HierarchyItem interface. Simplified but not deleted.

3. **HierarchyItem becomes standalone.** Not extending IGameObject (which is deleted). Declares its own methods. Both EditorGameObject and RuntimeGameObjectAdapter implement it.

4. **Prefab simplification first.** Phase A removes the main obstacle (virtual component model) before the class hierarchy change (Phase B). Can be developed and tested independently.

5. **Inspector entity stays HierarchyItem.** `CustomComponentInspector.entity` is NOT changed to `EditorGameObject` — must support both editor entities and play mode RuntimeGameObjectAdapters.
