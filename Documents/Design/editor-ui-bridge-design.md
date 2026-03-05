# Design: EditorUIBridge Wrapper System

## Status: Exploring

## The Problem We're Solving

UIScrollView needs to disable its scrollbar child's EditorGameObject when visibility is NEVER/AUTO(no overflow). Currently it can only disable the wrapper GO, which the hierarchy panel doesn't see.

## Q&A

### Q1: Can you disable an EditorGameObject from the wrapper?

**No.** When the bridge calls `comp.setOwner(wrapper)`, the component's `owner` reference is overwritten. The link to the EditorGameObject is lost. There's no back-reference from wrapper or component to the EditorGameObject.

### Q2: What would be needed to preserve that link?

A `transient` field on `Component` that the bridge sets before overwriting `owner`:

```java
// Component.java
@Getter @Setter
protected transient IGameObject editorOwner;
```

Bridge sets it in `createWrapperGameObject()`:
```java
comp.setEditorOwner(entity);  // preserve the link
comp.setOwner(wrapper);       // move to wrapper
```

Component then has both: `gameObject` → wrapper (rendering), `editorOwner` → EditorGameObject (editor state). EditorGameObject already implements IGameObject so the type fits. Transient = not serialized.

### Q3: Why does the wrapper exist in the first place?

The `UIRenderer` expects to traverse a `GameObject` hierarchy — it calls `root.getChildren()`, `root.getComponent(UIScrollView.class)`, `root.isEnabled()`, etc. These are all `GameObject` methods.

`EditorGameObject` is NOT a `GameObject`. It's a separate class (`implements Renderable, HierarchyItem`) with its own component storage, its own parent/children management, and its own enabled state. It was designed for the editor's flat-list serialization model (entities stored in a flat list with `parentId` references, hierarchy rebuilt at load time from those IDs).

The `UIRenderer` can't work with `EditorGameObject` directly because:
- `EditorGameObject` doesn't extend `GameObject`
- `GameObject.getChildren()` returns `List<GameObject>`, not `List<EditorGameObject>`
- Component lifecycle (`onStart`, `onEnable`) is tied to `GameObject`
- `UITransform.getScreenPosition()` and layout code assume `GameObject` parent chain

So the bridge creates temporary `GameObject` wrappers that mirror the `EditorGameObject` hierarchy, moves the real components onto them, and hands them to the `UIRenderer`.

**The fundamental issue**: Two parallel hierarchies (EditorGameObject and GameObject) that need to stay in sync, with components shared between them.

### Q4: What is the benefit of two separate hierarchies?

EditorGameObject adds over GameObject:
1. **Prefab system** — `prefabId`, `prefabNodeId`, `componentOverrides` (tracks which fields the user changed vs prefab defaults)
2. **Flat serialization** — `parentId` as string instead of object references, avoids circular refs in JSON
3. **Sibling ordering** — explicit `order` field

**None of these require a separate class.** GameObject could be extended to support prefab overrides and flat serialization. The two-hierarchy split is the root cause of the entire wrapper problem: stale caches, state not propagating between sides, bridge rebuild complexity.

Possible directions:
- **EditorGameObject extends GameObject** — gets lifecycle, renderer compatibility for free. Adds prefab/serialization on top.
- **Editor uses GameObjects directly** — add prefab override tracking and flat serialization support to GameObject itself.
- **Keep two hierarchies but preserve the link** — add `editorOwner` field (Q2). Least disruptive but doesn't fix the root cause.

### Q5: GameObject already supports prefabs in game mode. What's missing?

`Prefab.instantiate()` creates GameObjects with overrides baked in. At runtime, prefabs work fine with plain GameObjects. They don't track overrides after instantiation.

What EditorGameObject adds that GameObject lacks:
1. **Override tracking** — `componentOverrides` map (which fields differ from prefab defaults, for inspector bold styling, reset-to-default, saving only deltas)
2. **Prefab identity** — `prefabId`/`prefabNodeId` (so editor knows which prefab this came from)
3. **Flat serialization** — `parentId` as string (serialization concern, not class hierarchy)

That's it. These are additive features — they could live on a GameObject subclass.

### Q6: What would be the benefit of EditorGameObject extending GameObject?

**The bridge is eliminated entirely.** UIRenderer traverses EditorGameObjects directly since they ARE GameObjects.

Specific benefits:
- **No stale cache problem** — components stay on one GO, no `setOwner` shuffling
- **Single hierarchy** — one source of truth for enabled, parent/children, everything
- **`setEnabled(false)` just works** — hierarchy and renderer see the same object
- **Component lifecycle works** — no bypassing `onStart`/`onEnable` like the bridge does
- **Every future feature is simpler** — no "which side does state live on?" questions

Cost: significant refactor. Payoff: eliminates an entire class of bugs permanently.

### Q7: How would each panel and game mode be impacted?

**Major Simplification:**
- **EditorUIBridge** (~300 lines) — **eliminated entirely**. UIRenderer traverses EditorGameObjects directly. No wrapper creation, no `setOwner` shuffling, no stale caches.
- **UIDesignerPanel** — simplified. Currently calls `bridge.getRoot()` for wrapper hierarchy; would pass EditorGameObjects directly to UIRenderer.
- **EditorSceneRenderer / RenderDispatcher** — simplified. No bridge rebuild step before rendering.

**Neutral (works the same or better):**
- **HierarchyPanel** — already uses `HierarchyItem` interface. No changes needed.
- **InspectorPanel** — works on components via `HierarchyItem.getComponent()`. No changes.
- **Selection / Tools / Gizmos / Undo** — operate on `EditorGameObject` references or component fields. Unaffected.
- **Prefab system** — `componentOverrides`, `prefabId`, `prefabNodeId` stay as added fields on the subclass.
- **Serialization** — `EditorSceneSerializer` reads/writes `SceneData` (flat JSON with `parentId`). Format unchanged.

**Critical Risk:**
- **Play mode isolation** — `RuntimeSceneLoader` serializes EditorScene → SceneData → fresh GameObjects. This boundary must be preserved. The existing JSON round-trip path already creates new objects, so it should be safe, but needs careful verification that EditorGameObjects are never reused at runtime.

**Minor Work:**
- **Component lifecycle** — EditorGameObject inherits `start()`, `update()`, `destroy()`. Editor doesn't run game loop, so no behavioral change. Methods exist but aren't called.
- **`getChildren()` return type** — GameObject returns `List<GameObject>`. EditorGameObject's children list would need to integrate with this. Could override to return its children cast appropriately, or use GameObject's built-in children management directly.

### Q8: Full codebase impact — what improves, what conflicts, what's eliminated?

#### Improvements (anti-patterns eliminated)

1. **EditorUIBridge** (~300 lines) — eliminated. Reflection hacks (`getDeclaredField("components")`), wrapper creation, `setOwner()` shuffling all gone.
2. **`instanceof EditorGameObject` checks** (5 locations: AlphaGroup, RenderDispatcher, ReflectionFieldEditor, UITransformInspector, CustomComponentInspector) — most become unnecessary.
3. **AlphaGroup** has separate `applyAlphaToGameObject()` and `applyAlphaToEditorGameObject()` — can be unified into one method.
4. **Component dual-field ownership** — `Component.owner: IGameObject` and `Component.gameObject: GameObject` exist because EditorGameObject isn't a GameObject. With inheritance, `gameObject` is always non-null, and `owner` becomes redundant (see Q9).

#### Conflicts (solvable via overrides)

| Conflict | GameObject | EditorGameObject | Solution |
|----------|-----------|-----------------|----------|
| **ID system** | Computed `"go_" + identityHashCode` | Persistent UUID field | Override `getId()` |
| **setEnabled()** | Component notifications + scene cache + child propagation | Simple field setter | Override with simple logic |
| **isEnabled()** | Own field only | Checks parent chain | Override (already has this) |
| **Transform** | Stored as field, auto-created in constructor | Computed from components/overrides | Override `getTransform()` |
| **Parent/Children types** | `GameObject` / `List<GameObject>` | `EditorGameObject` / `List<EditorGameObject>` | Covariant return types (Java supports) |
| **Component storage** | Direct list | Prefab-aware merging with cached merged components | Override `getComponents()` |

Each conflict is solvable via method overrides. The point isn't code reuse from GameObject — it's **type compatibility**. UIRenderer accepts `GameObject`, and EditorGameObject becomes a valid `GameObject`.

#### Risk: `Component.gameObject` becoming non-null

Currently `setOwner()` sets `gameObject = (owner instanceof GameObject go) ? go : null`. If EditorGameObject extends GameObject, `gameObject` is always non-null. Code like `gameObject.getScene()` (5+ places) would run but return null. Existing null-checks already guard this — but needs audit.

### Q9: Can IGameObject be removed?

**Yes.** IGameObject was created specifically because EditorGameObject and GameObject didn't share a class hierarchy. If EditorGameObject extends GameObject, IGameObject's purpose disappears.

Current usages of IGameObject as a type (14 files):
- `Component.owner: IGameObject` → becomes `GameObject`
- `Component.setOwner(IGameObject)` → becomes `setOwner(GameObject)`
- `UITransform` uses `owner.getParent()`, `owner.getChildren()` → works via `GameObject`
- `AlphaGroup.applyAlphaToComponents(IGameObject)` → becomes `GameObject`
- `UIDesignerPanel.submitPickingHierarchy(IGameObject)` → becomes `GameObject`
- `ReflectionFieldEditor.hasComponentOfType(IGameObject)` → becomes `GameObject`
- `HierarchyItem extends IGameObject` → see below

**The edge case: `RuntimeGameObjectAdapter`.**

`RuntimeGameObjectAdapter` wraps a `GameObject` for hierarchy panel display AND property editing during play mode. It implements `HierarchyItem extends IGameObject` but is NOT a `GameObject` — it's a wrapper. **It cannot be deleted** because play mode inspectors allow editing component properties.

**Solution:** Make `HierarchyItem` a standalone interface (not extending `IGameObject`). It declares the methods it needs directly (`getName()`, `getComponent()`, `isEnabled()`, etc.). Both `EditorGameObject` (via `GameObject` inheritance) and `RuntimeGameObjectAdapter` (via delegation, same as today) implement it. RuntimeGameObjectAdapter is simplified (fewer methods to delegate) but not eliminated.

**Additional cleanup when IGameObject is removed:**
- `Component.gameObject` field eliminated — `owner` becomes `GameObject`, serves both purposes
- `isRuntime()` / `isEditor()` move to `GameObject` (default `isRuntime() = true`) with EditorGameObject overriding (`isEditor() = true`)
- `Component.setOwner()` simplifies to a single field assignment

### Q10: Full inventory — what's removed, simplified, improved, unified?

#### Removed entirely (~430+ lines)

| What | Lines | Why it existed |
|------|-------|----------------|
| **EditorUIBridge.java** | ~308 | Wrapper pattern for UIRenderer compatibility |
| **IGameObject.java** | ~125 | Bridge interface between unrelated types |
| **AlphaGroup dual methods** | ~18 | Separate `applyAlphaToGameObject()` + `applyAlphaToEditorGameObject()` — identical logic, different types |
| **Reflection hacks in bridge** | ~40 | `getDeclaredField("components")`, `getDeclaredField("transform")` to bypass lifecycle |
| **Component.gameObject field** | scattered | Cached `GameObject` reference, null for editor. Redundant when owner IS a GameObject |

**RuntimeGameObjectAdapter is NOT deleted** — still needed to wrap runtime GameObjects for play mode hierarchy display and property editing. Play mode inspectors allow editing component properties (even though `isEditable() = false` blocks hierarchy operations like rename/delete/reparent). Simplified when IGameObject is removed (implements standalone HierarchyItem instead).

#### Simplified (~200+ lines)

| What | Before | After |
|------|--------|-------|
| **Component.setOwner()** | Takes `IGameObject`, instanceof check, sets two fields | Takes `GameObject`, single field |
| **UIDesignerPanel / GameViewPanel** | Create bridge, manage lifecycle, call `uiBridge.getUICanvases()` | Collect canvases directly from EditorGameObjects |
| **RenderDispatcher** | `instanceof EditorGameObject` dispatch + dedicated `submitEditorGameObject()` | Unified rendering path |
| **AlphaGroup** | 3 methods (dispatch + 2 type-specific) | 1 recursive method on `GameObject` |
| **HierarchyItem** | Extends `IGameObject` | Standalone interface |
| **~30+ components** | Null-check `gameObject` before use | `gameObject` always non-null |
| **5 instanceof checks** | AlphaGroup, RenderDispatcher, ReflectionFieldEditor, UITransformInspector, CustomComponentInspector | Most eliminated |
| **UIComponent.setOwner()** | Cache invalidation "critical for wrapper GameObjects" | Wrapper concern gone |

#### Bugs fixed / gaps closed

| Issue | Current | After |
|-------|---------|-------|
| **Scrollbar visibility in hierarchy** | `setEnabled(false)` on wrapper doesn't reach EditorGameObject | Single object, setEnabled works everywhere |
| **Stale hierarchy caches** | Components cache wrapper refs that become orphaned on rebuild | No wrappers, no stale refs |
| **Enabled state propagation** | EditorGameObject does NOT propagate enabled changes to children | Can inherit or override with proper propagation |
| **Component lifecycle** | Bridge bypasses onStart/onEnable | Components have proper ownership |

#### Duplication that unifies (~300+ lines)

EditorGameObject reimplements these from GameObject:
- Hierarchy methods: `setParent()`, `getChildren()`, `isAncestorOf()`, `hasChildren()` (~120 lines)
- Component management: `addComponent()`, `removeComponent()`, `getComponent()`, `addRequiredComponents()` (~150 lines)
- Enabled state: `setEnabled()` / `isEnabled()` (~70 lines)

Not all unifies automatically — EditorGameObject's prefab-aware component merging is genuinely different. But hierarchy and basic component methods can delegate to `super` for scratch entities.

#### Net impact

- **~566 lines deleted** (3 entire classes + duplication)
- **~200+ lines simplified** across 10+ files
- **~300+ lines of duplication reduced** in EditorGameObject
- **1 class of bugs eliminated permanently** (stale wrappers, ownership confusion)
- **isRuntime()/isEditor()** — EGO overrides to return `isEditor() = true` (default on GameObject is `isRuntime() = true`)

### Q11: How does the prefab system interact with extending GameObject?

#### The core conflict

EditorGameObject has **two completely different component storage models**:

- **Scratch entities**: Store actual `List<Component>` instances (like GameObject)
- **Prefab instances**: Store `Map<String, Map<String, Object>> componentOverrides` — components are cloned on-demand from prefab template via `getMergedComponents()`

For prefab instances, Transform position/rotation/scale live in `componentOverrides` as float arrays, not in a Transform component. `addComponent()` throws an exception. `getComponents()` returns a cached merged list rebuilt when overrides change.

**GameObject's constructor auto-creates a Transform and adds it to its `components` list.** For prefab instances, this is unwanted — position comes from overrides.

#### Solution: targeted overrides

1. **Constructor**: Add a protected no-Transform constructor to GameObject, or have EditorGameObject's prefab constructor ignore the auto-created Transform.
2. **getComponents()**: Override — prefab instances return merged/cached list; scratch entities return actual list. Inherited `components` field unused for prefab instances.
3. **getTransform()**: Already overridden (searches components list).
4. **Position/Rotation/Scale**: EditorGameObject's accessors already branch on `isScratchEntity()`. No change.
5. **Serialization**: `toData()` / `fromData()` are explicit builders — no field leakage.

#### What does NOT change

- Override tracking (`componentOverrides`, `getFieldValue()`, `setFieldValue()`, inspector bold/reset)
- Prefab merging (`getMergedComponents()`, clone + apply overrides)
- Serialization format (scratch = components, prefab = ID + overrides)
- Runtime instantiation (`RuntimeSceneLoader` creates plain GameObjects)
- The 12+ `isScratchEntity()` / `isPrefabInstance()` branch points — all internal to EditorGameObject

#### Trade-off

**Cost:** Prefab instances carry an unused inherited `components` list and auto-created Transform. ~6 method overrides needed to prevent inherited behavior from interfering. Semantics slightly misleading (IS-A GameObject but doesn't fully behave like one for prefab instances).

**Payoff:** Type compatibility with UIRenderer, elimination of bridge pattern (~308 lines), IGameObject interface (~125 lines), RuntimeGameObjectAdapter (~133 lines), stale cache bugs, and ~1000+ lines of workaround code.

### Q12: Can the prefab override model be simplified alongside the inheritance change?

#### Current model: virtual components

Prefab instances don't store actual components. They store `prefabId` + `Map<String, Map<String, Object>> componentOverrides`. Every `getComponents()` call clones from the prefab template and applies overrides. This exists for one feature: **auto-propagation** — when a prefab template changes, instances invalidate their cache and re-merge.

This drives the 12+ `isScratchEntity()` branches, the dual storage model, `getMergedComponents()`, deferred ownership, position-as-float-arrays, and the entire conflict with extending GameObject (Q11).

#### Simplified model: actual components + override tracking

```java
// Replace:
Map<String, Map<String, Object>> componentOverrides;  // virtual components
transient List<Component> cachedMergedComponents;       // cloned on demand

// With:
List<Component> components;                             // real instances (same as scratch)
Map<String, Set<String>> overriddenFields;              // componentType → {field1, field2}
```

Prefab instances clone components from template **at creation time** and store them directly. `overriddenFields` tracks which fields the user changed (for inspector bold/reset/serialization).

#### What this eliminates

- `getMergedComponents()` (~60 lines)
- `cachedMergedComponents` cache + `invalidateComponentCache()`
- 12+ `isScratchEntity()` / `isPrefabInstance()` branches — unified into one path
- Position/rotation/scale as float arrays in override map — just use Transform
- `ensureOwnerSet()` deferred ownership — components owned immediately
- The Q11 conflict with extending GameObject disappears entirely (prefab instances have real components, same model as GameObject)

#### What stays

- `isPrefabInstance()` — still has `prefabId`
- `isFieldOverridden(type, field)` — checks `overriddenFields` Set
- `resetFieldToDefault(type, field)` — loads prefab default, applies it, removes from Set
- `getOverriddenFields(type)` — returns the Set
- Inspector bold styling, reset buttons — work the same
- Serialization: save `prefabId` + only overridden field values (diff at save time)

#### Auto-propagation trade-off

Template change propagation becomes an explicit "refresh from prefab" action:
1. Clone fresh components from updated template
2. For each overridden field: carry over the user's value
3. Replace the instance's component list

Arguably better UX — users see what changed. Current silent auto-refresh can cause surprises.

#### How this enables the GameObject inheritance

With actual components, prefab instances have the same component model as scratch entities, which is the same as GameObject. No conflicting storage, no unused inherited lists, no constructor conflicts. The `extends GameObject` change becomes straightforward.

### Q13: Complete system inventory — what needs refactoring?

#### Systems that NEED changes

| System | Change | Complexity |
|--------|--------|------------|
| **EditorGameObject** | Extends GameObject, overrides ~6 methods (getId, setEnabled, isEnabled, getTransform, getComponents, getChildren) | HIGH — core of the refactor |
| **Prefab override model** (Q12) | Actual components + `Map<String, Set<String>> overriddenFields` instead of virtual components | HIGH — redesign of prefab storage |
| **EditorUIBridge** | Deleted entirely (~308 lines) | LOW — just delete |
| **IGameObject** | Deleted (~125 lines) | LOW — delete + update imports |
| **RuntimeGameObjectAdapter** | Simplified (implements standalone HierarchyItem, fewer delegation methods) — NOT deleted, needed for play mode property editing | LOW |
| **HierarchyItem** | Standalone interface (not extending IGameObject). Declares its own methods: getName, getId, getComponent, getComponents, getAllComponents, isEnabled, hasChildren, getParent, getChildren | MEDIUM |
| **Component** | Remove `gameObject` field, change `owner` to type `GameObject`, simplify `setOwner()` | MEDIUM — touches base class |
| **AlphaGroup** | Unify 3 methods into 1 recursive method on `GameObject` | LOW |
| **RenderDispatcher** | Remove `instanceof EditorGameObject` dispatch + `submitEditorGameObject()` | LOW |
| **UIDesignerPanel / GameViewPanel** | Remove bridge code, collect canvases directly | MEDIUM |
| **Parent/Children type management** | See below | MEDIUM |
| **EditorScene.resolveHierarchy()** | Use unified setParent that updates both inherited hierarchy and parentId | LOW |
| **isRuntime()/isEditor()** | Move from IGameObject to GameObject. EditorGameObject overrides `isEditor() = true` | LOW |

#### Parent/Children relationship — detailed analysis

**Current state: two independent implementations**

GameObject has:
- `private GameObject parent` — direct reference
- `private final List<GameObject> children` — always initialized
- `setParent(GameObject)` — manages both lists + Scene registration + UITransform layout clearing

EditorGameObject has:
- `private transient EditorGameObject parent` — transient, rebuilt from parentId
- `private transient List<EditorGameObject> children` — transient, lazily initialized
- `private String parentId` — serialized string reference (flat serialization model)
- `setParent(EditorGameObject)` — manages both lists + updates parentId string
- Extra: `getChildrenMutable()`, `getDepth()`, `clearParent()`, `setParentDirect()`

**Key differences:**
- EditorGameObject's parent/children are transient (rebuilt from `parentId` after deserialization)
- EditorGameObject's `setParent()` updates `parentId` for serialization (GameObject doesn't have this)
- GameObject's `setParent()` handles Scene registration (irrelevant for editor — no Scene)
- GameObject's `setParent()` clears UITransform layout overrides (editor needs this too)

**If EditorGameObject extends GameObject: two sets of parent/children fields**

The inherited `parent: GameObject` and `children: List<GameObject>` would coexist with EditorGameObject's `parent: EditorGameObject` and `children: List<EditorGameObject>`. `setParent(EditorGameObject)` is an overload, not an override. The inherited fields stay empty/stale.

**Solution: One list, typed accessors**

EditorGameObject uses the **inherited** parent/children from GameObject. No second list — one source of truth.

```java
// GameObject — change private → protected for subclass access
protected GameObject parent;
protected final List<GameObject> children = new ArrayList<>();
```

```java
// EditorGameObject — NO own parent/children fields
public class EditorGameObject extends GameObject {

    // getChildren() inherited — returns List<GameObject> (works for UIRenderer)

    // Typed view of the SAME underlying list (no copy)
    @SuppressWarnings("unchecked")
    public List<EditorGameObject> getEditorChildren() {
        return (List<EditorGameObject>)(List<?>) children;
    }

    // Typed parent access
    public EditorGameObject getEditorParent() {
        return (EditorGameObject) parent;
    }

    @Override
    public void setParent(GameObject newParent) {
        // Override: skip Scene registration (no runtime Scene in editor)
        // Manage the ONE inherited children list
        // Also update parentId for serialization
        this.parentId = (newParent != null) ? newParent.getId() : null;
    }
}
```

The unchecked cast in `getEditorChildren()` is safe — editor code only puts EditorGameObjects into the list. Same underlying `ArrayList`, different generic view.

**Call-site changes (~15 mechanical find-and-replace):**
- Runtime code: `for (GameObject child : go.getChildren())` — **unchanged**
- Editor code: `for (EditorGameObject child : entity.getChildren())` → `entity.getEditorChildren()`
- Editor parent access: `entity.getParent()` → `entity.getEditorParent()` where EditorGameObject-specific methods needed

**setParent() override behavior:**
- Skip Scene registration/unregistration (editor has no runtime Scene)
- Keep circular reference guard and self-parent guard (same logic)
- Keep UITransform layout override clearing (editor needs this)
- Add `parentId` update for serialization

#### Systems that DON'T need changes

| System | Why unchanged |
|--------|---------------|
| **Undo system** (23 commands) | All store `EditorGameObject` references — still valid, it's just also a GameObject now |
| **Selection system** | Stores `Set<EditorGameObject>` — unchanged |
| **Tools** (move, rotate, scale, select) | Use EditorGameObject type — unchanged |
| **Inspector** | Uses EditorGameObject for prefab metadata — unchanged |
| **Picking system** | Maps `int → EditorGameObject` — unchanged |
| **Gizmo system** | Iterates components on EditorGameObject — unchanged |
| **EditorScene** | Returns EditorGameObject from all methods — unchanged |
| **Serialization format** | GameObjectData/SceneData unchanged — explicit builders |
| **Play mode boundary** | PlayModeController snapshots EditorScene → SceneData → fresh GameObjects. Intentional separation preserved |
| **PrefabEditController** | Uses EditorGameObject for working entity — unchanged |
| **EntityCreationService** | Creates EditorGameObjects — unchanged |
| **UIEntityFactory** | Creates EditorGameObjects — unchanged |
| **Copy/Paste/Duplicate** | Uses EntityCreationService — unchanged |
| **Undo system** | All commands store `EditorGameObject` references explicitly. No command accepts generic `GameObject`. Play mode creates separate GameEngine with deep-copied data — never touches undo stack. `pushScope()`/`popScope()` isolates prefab edits. Type-safe by construction — no changes needed |
| **HierarchyPanel** | Uses HierarchyItem interface — unchanged (HierarchyItem refactored but panel code same) |

### Q14: What component and inspector issues get fixed?

#### Components where gameObject is null in editor (HIGH severity)

These components use `gameObject.getChildren()` which returns null in the editor because EditorGameObject isn't a GameObject. They ONLY work because EditorUIBridge creates wrapper GameObjects:

| Component | What fails without bridge | After (EGO extends GO) |
|-----------|--------------------------|------------------------|
| **LayoutGroup.getLayoutChildren()** | Can't iterate children | Works — gameObject always non-null |
| **UIScrollView.refreshHierarchyCache()** | Can't find viewport/content/scrollbar | Works — no stale wrapper refs |
| **UIScrollbar.getHandle()** | Can't find handle child | Works — direct child access |
| **Transform.updateWorldMatrix()** | Recursive children fails silently | Works — children accessible |

These are the root cause of the original scrollbar visibility bug. The bridge was a workaround for this exact problem.

#### Components with dual code paths

| Component | Current | After |
|-----------|---------|-------|
| **AlphaGroup** | Two identical recursive methods (runtime + editor) | One method on GameObject |
| **Component.setOwner()** | instanceof check, two fields | Single field assignment |
| **UIComponent.findCanvasInAncestors()** | Uses IGameObject, works but indirect | Uses GameObject directly |
| **UITransform hierarchy walking** | Uses `owner.getParent()` returning IGameObject | Uses `gameObject.getParent()` returning GameObject |

#### Inspector situation

Custom inspectors receive `HierarchyItem`. This **stays the same** — play mode allows editing component properties, so inspectors must work with both `EditorGameObject` (editor) and `RuntimeGameObjectAdapter` (play mode).

**What stays:**
- `CustomComponentInspector.entity` stays typed as `HierarchyItem`
- `editorEntity()` helper stays — needed for prefab-specific operations (override styling, reset-to-default)
- `RuntimeGameObjectAdapter` stays — wraps runtime GameObjects for play mode hierarchy + inspection

**What improves:**
- HierarchyItem becomes standalone (not extending IGameObject) — cleaner interface
- EditorGameObject implements HierarchyItem via GameObject inheritance + its own hierarchy methods
- `FieldEditorContext` already stores `EditorGameObject` — unchanged
- The 4 HIGH severity component bugs (LayoutGroup, UIScrollView, UIScrollbar, Transform) are fixed at the component level, not the inspector level — components can now traverse hierarchy directly since `gameObject` is always non-null

#### Implementation order (recommended)

1. **Phase A: Prefab simplification** (Q12) — actual components + override tracking. Can be done independently. Removes the main obstacle to inheritance.
2. **Phase B: EditorGameObject extends GameObject** — class hierarchy change, method overrides, parent/children unification.
3. **Phase C: Bridge elimination** — delete EditorUIBridge, IGameObject, RuntimeGameObjectAdapter. Simplify Component, AlphaGroup, RenderDispatcher.
4. **Phase D: Cleanup** — remove instanceof checks, unify code paths, simplify UIDesignerPanel/GameViewPanel.

Phases A and B are the hard work. Phases C and D are the payoff (deleting code).
