# EditorGameObject Refactor — Design Review

## Reviewers
- Senior Software Engineer (code-level implementation review)
- Software Architect (architectural/design-level review)

## Status: Round 5 Complete — All Implementation Readiness Gaps Resolved (R54-R90)

---

## Approved Items (Both Reviewers Agree)

1. **Core thesis is sound.** `EditorGameObject extends GameObject` eliminates the `EditorUIBridge` wrapper pattern and its reflection hacks (`getDeclaredField("components")`, `getDeclaredField("transform")`).
2. **Phase ordering is correct.** Pre-work → A → B → C dependency chain is well-structured.
3. **IGameObject reference count is accurate.** 14 files confirmed.
4. **Undo commands correctly identified.** Only `SetComponentFieldCommand`, `ResetFieldOverrideCommand`, `ResetAllOverridesCommand` need changes.
5. **Bug analysis is accurate.** `Transform` hierarchy traversal, stale caches, enabled state sync are real bugs caused by the bridge.
6. **JSON round-trip play mode boundary is safe.** `RuntimeSceneLoader` serializes → fresh GameObjects. EditorGameObjects never leak to runtime.
7. **`HierarchyItem` as interface (not abstract class) is correct.** Avoids diamond inheritance with `RuntimeGameObjectAdapter`.
8. **Keeping `RuntimeGameObjectAdapter` is correct.** Play mode inspectors still need it.

---

## Review Items

### R1: `SceneManager.getCurrentScene()` static pattern

**Raised by:** Architect (HIGH concern)

**Issue:** The pre-work proposes replacing `gameObject.getScene()` with static `SceneManager.getCurrentScene()`. This is a Service Locator anti-pattern:
- Hidden global state — components silently depend on it
- Blocks future additive scene loading / multiple scenes
- Hurts testability — unit tests must set up global SceneManager state

**Architect's alternative:** Keep `scene` field on `GameObject`, have `EditorGameObject` override scene-related methods to no-op. Or inject a `SceneContext` interface through the ownership chain.

**Resolution:** APPROVED — Keep static `SceneManager.getCurrentScene()` (Unity-like pattern). Add a comment in `SceneManager` documenting its limitations (single active scene, not thread-safe, static setter for test setup). Multi-scene support can evolve later if needed.

---

### R2: `setParent()` signature creates overload, not override

**Raised by:** Senior Engineer (concrete bug)

**Issue:** `GameObject.setParent(GameObject)` and `EditorGameObject.setParent(EditorGameObject)` would coexist as **overloads**, not an override. Code calling `ego.setParent(aGameObject)` would invoke the `GameObject` version, bypassing editor logic (parentId update, circular reference check).

**Architect adds:** The `IllegalArgumentException` guard in the override violates Liskov Substitution Principle — a subclass imposes stricter preconditions than its parent.

**Proposed fix:** Override `setParent(GameObject)` with runtime type check. Remove or make private the `setParent(EditorGameObject)` overload. All editor call sites pass `EditorGameObject` which satisfies the `GameObject` parameter type. Optionally add `setEditorParent(EditorGameObject)` as a compile-time-safe convenience.

**Resolution:** APPROVED — Override `setParent(GameObject)` with type guard + editor logic (parentId update, UITransform cache clear, skip Scene registration). Delete the old `setParent(EditorGameObject)` overload. Design doc updated.

---

### R3: `children` is `private final`, not just `private`

**Raised by:** Senior Engineer

**Issue:** `GameObject` line 47: `private final List<GameObject> children = new ArrayList<>()`. After `extends`, `super()` already creates the list. EditorGameObject constructors must NOT re-initialize `children`. Also, `getChildren()` returns `Collections.unmodifiableList(children)` — `getEditorChildren()` must cast the raw field, not the getter return value.

**Proposed fix:** Make `children` `protected` (final is fine — reference is reused, not reassigned). In `EditorGameObject`, access `children` field directly for `getEditorChildren()`.

**Resolution:** APPROVED (revised by R4) — `children` stays **private**. `getEditorChildren()` returns `Collections.unmodifiableList((List<EditorGameObject>)(List<?>) getChildrenMutable())` — casts protected helper, returns unmodifiable. Children change via `setParent()`, not list mutation. EGO constructors must not re-initialize `children`. Design doc updated.

---

### R4: Protected fields vs protected helper methods

**Raised by:** Architect

**Issue:** Making `parent`/`children` `protected` allows `EditorGameObject` to bypass `GameObject`'s invariants (scene registration, layout clearing) by writing directly to the fields.

**Alternative:** Keep fields `private`. Add protected helper methods:
- `setParentDirect(GameObject)` — raw field mutation without lifecycle
- `addChildDirect(GameObject)` — raw list add without lifecycle
- `removeChildDirect(GameObject)` — raw list remove without lifecycle

This documents intent and prevents accidental invariant bypass.

**Resolution:** APPROVED — Keep fields private. Add 3 protected helpers: `setParentDirect(GameObject)`, `getParentDirect()`, `getChildrenMutable()`. Each with explicit javadoc comment marking them as editor-only, not for runtime use. R3 updated accordingly (no protected fields). Design doc updated.

---

### R5: `isRuntime()` default is a Phase B ordering hazard

**Raised by:** Both

**Issue:** `IGameObject.isRuntime()` has default `return this instanceof GameObject`. After `extends`, this returns `true` for EditorGameObject. `AlphaGroup` line 56 checks `owner.isRuntime()` to dispatch — wrong branch would execute.

**Proposed fix:** Override `isRuntime()` → `false` in `EditorGameObject` in the **same commit** as the `extends` change. Not deferrable. Add regression test: `assertFalse(new EditorGameObject(...).isRuntime())`.

**Resolution:** APPROVED — Not a real hazard since B+C are merged (IGameObject deleted in same phase as `extends`). `isRuntime()`/`isEditor()` on `GameObject` with overrides in `EditorGameObject` is already documented in the design. Add regression test.

---

### R6: GameObject constructor auto-creates Transform — double Transform for prefabs

**Raised by:** Senior Engineer

**Issue:** `GameObject(String name)` auto-creates a `Transform` via `addComponentInternal()`. When `EditorGameObject extends GameObject`, `super(name)` creates a Transform. But prefab instances clone components from template, which includes its own Transform. Result: two Transforms.

**Options:**
- (a) Add `protected GameObject(String name, boolean skipTransform)` constructor
- (b) Have EditorGameObject call `super(name)` then remove auto-Transform before cloning

Option (a) is cleaner.

**Resolution:** RESOLVED — No special constructor needed. `super(name)` auto-creates Transform. Scratch entities and prefab clones reuse it (set values, don't add a second). Implementation detail, not a design decision.

---

### R7: `ResetFieldOverrideCommand.undo()` and `ResetAllOverridesCommand.undo()` incomplete

**Raised by:** Senior Engineer

**Issue:** Design doc describes the `execute()` side of undo commands but not the `undo()` side.

`ResetFieldOverrideCommand.undo()` currently (line 48-51):
```java
entity.getComponentOverrides()
    .computeIfAbsent(componentType, k -> new HashMap<>())
    .put(fieldName, savedOverrideValue);
entity.invalidateComponentCache();
```

After Phase A, `undo()` must: (1) restore saved value on real component, (2) add field back to `overriddenFields`. The saved value is already available (line 37).

`ResetAllOverridesCommand.undo()` similarly: must restore actual component field values AND rebuild `overriddenFields`, not just restore a map.

**Resolution:** APPROVED — Documentation gap. Design doc updated with full execute/undo behavior for all 3 commands.

---

### R8: `SwapTransformCommand` directly mutates component list

**Raised by:** Senior Engineer

**Issue:** `SwapTransformCommand.java` line 128-132 calls `entity.getComponents().set(index, newTransform)`. After Phase B, `getComponents()` may return an unmodifiable copy from `GameObject`.

**Proposed fix:** Add `replaceComponent(Component old, Component new)` method on `EditorGameObject` that mutates the backing list directly.

**Resolution:** APPROVED — Add `replaceComponent(old, new)` on `EditorGameObject`. `SwapTransformCommand` uses it instead of direct list mutation.

---

### R9: Missing files in update plan

**Raised by:** Senior Engineer

**Issue:** Several files not listed in the design doc:
- `UIComponent.setOwner(IGameObject)` — overrides `setOwner`, needs signature change
- `UIScrollbar.setOwner(IGameObject)` — same
- `UIScrollView.setOwner(IGameObject)` — same
- `UITransform` — iterates `owner.getChildren()` using `IGameObject` type (lines 648, 828, 1096)
- `CustomComponentEditorRegistry` — has `instanceof EditorGameObject` check (line 86), not listed in Phase C cleanup
- Undo command count is 34, not 23 as stated in design doc

**Resolution:** APPROVED — All missing files added to design doc. All `setOwner` overrides are mechanical (call super + dirty flag). `UITransform` iteration is a type change. `CustomComponentEditorRegistry` instanceof added to Phase C. Undo count corrected to 34. No complications — all changes are straightforward signature updates.

---

### R10: Missing test — Transform hierarchy traversal

**Raised by:** Senior Engineer

**Issue:** `Transform.getParentTransform()` and `Transform.markChildrenDirty()` use the `gameObject` field (which will now be non-null for editor entities). This is the root cause of the scrollbar bugs. Phase B test strategy should include explicit test for this.

**Resolution:** APPROVED — Added to Phase B test strategy: `Transform.getParentTransform()` and `Transform.markChildrenDirty()` on EditorGameObject hierarchies.

---

### R11: `overriddenFields` edge cases — template drift

**Raised by:** Architect

**Issue:** If a prefab template is modified externally while instances exist in a scene:
- Save-time diffing (`toData()`) compares against the **new** template defaults, not the ones that were cloned
- Undo after template change restores the old default, not the new template default
- Deep equality for complex field types (`List`, nested objects) may be error-prone with Gson types

**Suggestion:** Consider adding `templateVersion` or `lastTemplateHash` to detect stale clones. At minimum, document expected behavior.

**Resolution:** ACCEPTED as known limitation. Already exists in current model (no external change detection today either). Phase A doesn't make it worse. `templateVersion` is future work if needed.

---

### R12: Pre-work and Phase A parallelization opportunity

**Raised by:** Architect

**Issue:** Both Pre-work and Phase A are marked as "can be done independently." They touch different areas (Pre-work: `GameObject.scene` + component scene access; Phase A: `EditorGameObject` internals + prefab serialization). Running them in parallel in separate worktrees would reduce calendar time.

**Resolution:** NOTED — Pre-work and Phase A can run in parallel (different code areas). Added as option in design doc.

---

### R13: `SceneManager` does not currently support static access

**Raised by:** Senior Engineer

**Issue:** `SceneManager` is a regular class (not a singleton), has no `getInstance()`, and `currentScene` is private with no getter. The pre-work must:
1. Add `static getCurrentScene()` method
2. Wire it up so the active scene is set/cleared during scene transitions
3. Handle edge cases during scene transitions (two scenes briefly alive)

**Resolution:** APPROVED — Implementation details added to pre-work phase in design doc (static field, lifecycle wiring, transition edge case, test setter, limitation comments).

---

## Round 2 Review

### R14: `gameObject` field deletion strategy

**Raised by:** Architect (Round 2, HIGH)

**Issue:** The design says delete `Component.gameObject` field. But ~50+ component files use `this.gameObject` directly (`gameObject.getChildren()`, `gameObject.getComponent(...)`, etc.). Mass rename to `this.owner` is high blast radius.

**Recommendation:** Keep `gameObject` as a field set alongside `owner` in simplified `setOwner(GameObject)`, or add a `getGameObject()` alias getter. Minimizes changes across component files.

**Resolution:** APPROVED — Delete `owner` field, keep `gameObject` typed as `GameObject`. Rename `setOwner()` → `setGameObject()`. Update ~10 `owner` references in `Component.java` to `gameObject`. 91 existing `this.gameObject` usages across components unchanged. Design doc updated.

---

### R15: Pre-work MUST complete before Phase B

**Raised by:** Senior Engineer (Round 2, HIGH)

**Issue:** Design says Pre-work "can run in parallel with Phase A." But if Phase B goes in before Pre-work completes, `gameObject` becomes non-null for editor entities while the `scene` field still exists → 30+ NPEs from `gameObject.getScene()` where `gameObject` is now non-null (passes first null check) but `scene` is null (fails second access).

**Recommendation:** Make Pre-work a **blocking prerequisite** for Phase B. Update dependency chain: Pre-work AND Phase A must BOTH complete before Phase B.

**Resolution:** APPROVED — Phase B now depends on both Pre-work AND Phase A. Design doc updated.

---

### R16: `HierarchyItem.isEditable()` breaks when IGameObject deleted

**Raised by:** Senior Engineer (Round 2, HIGH)

**Issue:** `HierarchyItem.isEditable()` default method calls `isEditor()` inherited from `IGameObject`. When IGameObject is deleted and HierarchyItem becomes standalone, `isEditor()` is no longer available — compilation error.

**Recommendation:** Add `isEditor()` to the standalone `HierarchyItem` interface declaration, or make `isEditable()` abstract. Both `EditorGameObject` and `RuntimeGameObjectAdapter` already implement `isEditor()`.

**Resolution:** APPROVED — Added `isEditor()` to standalone `HierarchyItem` interface declaration. `isEditable()` can default to `isEditor()`. Design doc updated.

---

### R17: RenderDispatcher EditorGameObject branch can't be fully removed in Phase C

**Raised by:** Both (Round 2, MEDIUM)

**Issue:** `EditorSceneRenderer` submits `EditorGameObject` instances to `RenderDispatcher.submit()`. The `instanceof EditorGameObject` branch does editor-specific sprite resolution (from prefab system, broken prefab icons, etc.). Can't just delete it — editor rendering would break.

**Recommendation:** Either (a) keep the branch in Phase C with documentation explaining why, or (b) post-Phase C, move the logic to `EditorSceneRenderer` and have editor entities use real `SpriteRenderer` components for rendering. Option (b) is the cleaner long-term solution but is a separate effort.

**Resolution:** APPROVED — Phase C: `EditorGameObject` stops implementing `Renderable`. `EditorSceneRenderer` submits `SpriteRenderer` components instead of entities. `RenderDispatcher` drops `instanceof EditorGameObject` branch, `submitEditorGameObject()`, `resolveEntityGeometry()`, and the editor import. Broken prefab icon removed. Editor and runtime rendering unified — both submit `SpriteRenderer` components through the same path.

---

### R18: `ReparentEntityCommand` also mutates `getComponents()` list

**Raised by:** Senior Engineer (Round 2, MEDIUM)

**Issue:** Same issue as R8 (`SwapTransformCommand`). `ReparentEntityCommand.performTransformSwap()` at line 154 calls `target.getComponents().set(index, newTransform)` — directly mutating the list. After Phase B, if `getComponents()` returns an unmodifiable copy, this silently fails.

**Recommendation:** Use the same `replaceComponent(old, new)` method proposed in R8. Audit for any other callers that mutate `getComponents()` results.

**Resolution:** APPROVED — Use `replaceComponent()` from R8. Audit all `getComponents()` callers for direct mutation during Phase B implementation.

---

### R19: UITransform cache clearing missing from EditorGameObject.setParent()

**Raised by:** Senior Engineer (Round 2, MEDIUM)

**Issue:** `GameObject.setParent()` clears UITransform layout overrides (lines 98-103). Current `EditorGameObject.setParent()` does NOT include this. After refactor with `setParentDirect()` bypassing `super.setParent()`, this gap persists. Could cause layout glitches when reparenting UI entities in the editor.

**Recommendation:** Add UITransform cache clearing to `EditorGameObject.setParent()` override. Or extract the cache-clearing logic into a shared helper both paths call.

**Resolution:** APPROVED — `EditorGameObject.setParent()` will include: (1) `UITransform.clearLayoutOverrides()` call, (2) auto-swap Transform↔UITransform based on whether new parent is a UI context (has `UICanvas` or `UITransform`). Replaces the manual inspector swap button. Design doc updated.

---

### R20: UICanvas.setSortOrder() with null SceneManager

**Raised by:** Architect (Round 2, MEDIUM)

**Issue:** After Pre-work removes `scene` field, `UICanvas.setSortOrder()` calls `SceneManager.getCurrentScene().markCanvasSortDirty()`. In editor context, `getCurrentScene()` returns null — sort-dirty notification silently lost. After bridge deletion in Phase B, editor needs a different mechanism for canvas sort changes.

**Recommendation:** Audit during Pre-work. Verify that editor UI rendering handles sort order independently (through `EditorScene` or direct sort in `UIDesignerPanel`).

**Resolution:** NOT AN ISSUE — Editor already sorts canvases on-the-fly each frame (`EditorUIBridge` line 190-191, `UIPreviewRenderer` line 48-52). `markCanvasSortDirty()` is a runtime-only deferred sort optimization. After bridge deletion, `UIDesignerPanel` collects and sorts canvases directly — same pattern.

---

### R21: addComponent() lifecycle safety for editor entities

**Raised by:** Architect (Round 2, MEDIUM)

**Issue:** After `extends`, `EditorGameObject` inherits `GameObject.addComponent()` which may trigger lifecycle callbacks and scene cache registration. Currently guarded by `scene != null` checks. After Pre-work replaces `scene` with `SceneManager.getCurrentScene()`, these guards still work (returns null in editor). Must be verified.

**Recommendation:** Audit `GameObject.addComponent()` during Phase B to confirm all lifecycle paths are guarded by scene null checks that evaluate correctly for editor entities.

**Resolution:** APPROVED — Verify during Phase B implementation. After Pre-work, `SceneManager.getCurrentScene()` returns null in editor, so lifecycle guards should skip correctly. Needs explicit audit.

---

### Follow-up Opportunities (post-refactor, not blocking)

These are improvement opportunities identified during round 2 review. Not blocking the current refactor.

| ID | Opportunity | Impact | When |
|----|------------|--------|------|
| F1 | ~~Extract editor rendering from `RenderDispatcher`~~ — addressed in R17, included in Phase C | ~~MEDIUM~~ | Phase C |
| F2 | ~~`EditorGameObject` stop implementing `Renderable`~~ — addressed in R17, included in Phase C | ~~LOW~~ | Phase C |
| F3 | ~~Unify editor/runtime rendering paths~~ — addressed in R17, included in Phase C | ~~MEDIUM~~ | Phase C |
| F4 | `RuntimeGameObjectAdapter` — remove orphaned `getParent()`/`getChildren()` after IGameObject deletion | LOW | Phase B |
| F5 | Remove `ensureOwnerSet()` guard — stale after bridge deletion | LOW | Phase C |
| F6 | `hierarchyVersion` in EditorScene — may become dead code after bridge deletion | LOW | Phase C |
| F7 | `selectedObject: GameObject` in EditorScene — legacy field, evaluate removal | LOW | Phase C |
| F8 | Editor canvas sort: replace per-frame sort with dirty-flag pattern (like runtime `markCanvasSortDirty()`) | LOW | Post-Phase C |

---

## Round 3 Review

**Reviewers:** New Senior Software Engineer + New Software Architect

### R22: `name` field shadowing after `extends`

**Raised by:** Senior Engineer (CRITICAL)

**Issue:** Both `GameObject` (line 26) and `EditorGameObject` (line 51) declare `private String name` with Lombok `@Getter @Setter`. After `extends`, EditorGameObject has its own `name` field **shadowing** `GameObject.name`. The Lombok-generated `getName()`/`setName()` on EditorGameObject operate on the shadow field, while `super.getName()` and any `GameObject` code referencing `this.name` reads the parent's (unset) field.

**Evidence:** `GameObject.java:26`, `EditorGameObject.java:51`

**Resolution:** APPROVED — Delete `name` field from EditorGameObject. Use inherited `getName()`/`setName()` from GameObject (already has Lombok `@Getter @Setter`). No helper needed.

---

### R23: `enabled` field shadowing after `extends`

**Raised by:** Architect + Senior Engineer (CRITICAL)

**Issue:** `GameObject.enabled` is `private boolean enabled = true` (line 29). `EditorGameObject.enabled` is `private boolean enabled = true` (line 76) with Lombok `@Getter @Setter`. After `extends`, two separate fields. `EditorGameObject.setEnabled()` override writes its own shadow field, but `GameObject.update()` (line 405) checks `if (!enabled) return;` reading its own private `enabled` — never updated. Also `propagateParentEnabledChange()` reads `this.enabled` from the parent class.

**Evidence:** `GameObject.java:29`, `EditorGameObject.java:76`

**Resolution:** APPROVED — Delete `enabled` field from EditorGameObject. Add `protected void setEnabledDirect(boolean)` helper on GameObject (sets field without lifecycle). EGO's `setEnabled()` override calls `setEnabledDirect()`. Design doc updated (Section 12 + field shadowing rule).

---

### R24: `parent` field shadowing after `extends`

**Raised by:** Senior Engineer (CRITICAL)

**Issue:** EGO has its own `EditorGameObject parent` field (line 79). GO has `private GameObject parent`. After `extends`, these are two separate fields. GO's `destroy()` (line 466-468) directly accesses `this.parent` and `parent.children.remove(this)` — reads GO's null parent, not EGO's. The design proposes protected helpers (`setParentDirect`, `getParentDirect`) but the `destroy()` edge case isn't addressed.

**Evidence:** `GameObject.java:466-468`, `EditorGameObject.java:79`

**Resolution:** APPROVED — Delete `parent` field from EditorGameObject. Use inherited field via `setParentDirect()`/`getParentDirect()` (from R4). `parentId` stays for serialization. Override `destroy()` as no-op or editor-specific cleanup — editor entities don't use GO lifecycle.

---

### R25: `getChildren()` return type is a compile error

**Raised by:** Architect + Senior Engineer (CRITICAL)

**Issue:** `EditorGameObject.getChildren()` returns `List<EditorGameObject>` (line 397). `GameObject.getChildren()` returns `List<GameObject>` (line 151). `List<EditorGameObject>` is NOT a subtype of `List<GameObject>` in Java (generics are invariant). This is a **compile error** after `extends`. Also, the call-site impact is ~30+ for `getChildren()` alone, not the ~15 estimated in the design doc.

**Evidence:** `EditorGameObject.java:397`, `GameObject.java:151`

**Resolution:** APPROVED — Already the design doc's approach (inherit `getChildren()`, use `getEditorChildren()` for typed access). Update effort estimate from ~15 to ~50+ mechanical call-site changes (`getChildren()` ~30+ and `getParent()` ~20+).

---

### R26: `getComponents()` method erasure conflict

**Raised by:** Senior Engineer (CRITICAL)

**Issue:** `GameObject` has `public <T extends Component> List<T> getComponents()` (line 356, generic no-arg). `EditorGameObject` has `public List<Component> getComponents()` (line 466, non-generic no-arg). Both have the same erasure (`List getComponents()`) but neither overrides the other. **Compile error.**

**Evidence:** `GameObject.java:356`, `EditorGameObject.java:466`

**Resolution:** APPROVED — Remove GO's generic no-arg `<T> List<T> getComponents()` (unsafe unchecked cast). Keep `<T> List<T> getComponents(Class<T>)` (typed with parameter) and `getAllComponents()` returning `List<Component>`. Callers of the no-arg version migrate to `getAllComponents()`.

---

### R27: `getChildrenMutable()` name collision

**Raised by:** Senior Engineer (CRITICAL)

**Issue:** `EditorGameObject` already has `public List<EditorGameObject> getChildrenMutable()` (line 404). The design proposes adding `protected List<GameObject> getChildrenMutable()` to `GameObject`. Same name, different return types, same erasure → **compile error**.

**Evidence:** `EditorGameObject.java:404`, Design doc Section 12

**Resolution:** APPROVED — Delete both `getChildrenMutable()` (on EGO) and the proposed `getChildrenDirect()` (on GO). Also delete proposed `getEditorChildren()`. Only `getChildren()` inherited from GO exists, returning `List<GameObject>`. Mutations go through `setParent()` or dedicated methods (e.g. `removeAllChildren()` for EditorScene's `clear()` case). Move `getOrder()`/`setOrder()` to GameObject. The ~5 prefab serialization sites that need `EditorGameObject` type cast inline. Prefab data stays on EGO only (not GO) — runtime has no prefab concept, keeping engine/editor separation clean.

---

### R28: Double Transform on prefab construction

**Raised by:** Senior Engineer (HIGH — revisiting R6)

**Issue:** R6 was resolved as "No special constructor needed — set values, don't add a second." But there's no mechanism for that. `GameObject(String name)` constructor (line 54-59) does `this.transform = new Transform()` + `addComponentInternal(transform)` which unconditionally adds to the components list. Prefab instance creation in Phase A clones components from template (including a Transform). Result: **two Transforms** — one auto-created by `super(name)`, one cloned from template. The R6 resolution doesn't address this concrete mechanics issue.

**Evidence:** `GameObject.java:54-59`

**Resolution:** APPROVED — `super(name)` auto-creates a Transform. Prefab clone doesn't add a second — it updates the existing Transform's values (position/rotation/scale) from the template. Phase A's `fromData()` calls `getTransform()` on the already-constructed EGO and sets fields on the existing instance. Design doc updated.

---

### R29: Inherited lifecycle methods (`update`, `start`, `destroy`)

**Raised by:** Architect (HIGH)

**Issue:** `GameObject` has `update(float)`, `lateUpdate(float)`, `start()`, `destroy()` that iterate components and propagate to children. After `extends`, these are inherited by EditorGameObject. If any code accidentally calls `update()` or `start()` on an EditorGameObject, it triggers component lifecycle callbacks (`onStart()`, `onEnable()`) on editor entities — never intended. The "Systems That DON'T Need Changes" section doesn't mention these.

**Evidence:** `GameObject.java` — `update()`, `start()`, `destroy()`, `lateUpdate()`

**Resolution:** APPROVED — Override `update(float)`, `lateUpdate(float)`, `start()`, and `destroy()` as no-ops on `EditorGameObject`. Prevents accidental component lifecycle triggers on editor entities. `destroy()` can include editor-specific cleanup if needed later. Design doc updated (added to ~6 overrides list).

---

### R30: `getComponents()` list mutation — additional callers not in R8/R18

**Raised by:** Architect + Senior Engineer (HIGH)

**Issue:** R8 identified `SwapTransformCommand` and R18 identified `ReparentEntityCommand` as direct list mutators. But additional callers were found:
- `RemoveComponentCommand.undo()` (line 34): `entity.getComponents().add(originalIndex, component)`
- `PrefabEditController.buildWorkingScene()` (line 398): `workingEntity.getComponents().add(0, new Transform())`
- `EditorGameObject.addComponent()` (lines 607, 617): `getComponents().add(component)` on its own return value

These will silently fail if `getComponents()` returns a defensive copy after Phase B.

**Evidence:** `RemoveComponentCommand.java:34`, `PrefabEditController.java:398`, `EditorGameObject.java:607,617`

**Resolution:** APPROVED — All list mutation goes through 2 methods on `EditorGameObject`: `addComponent()` (already exists, reworked to use inherited list) and `replaceComponent(old, new)` (from R8). `RemoveComponentCommand.undo()` uses `addComponent()` (append; positional index not critical since components are looked up by type). `PrefabEditController` uses `addComponent()` similarly. `getComponents()`/`getAllComponents()` return defensive copies — no external mutation. Design doc updated.

---

### R31: `SceneManager.getCurrentScene()` play mode cross-contamination

**Raised by:** Architect (HIGH)

**Issue:** During play mode, `GameEngine` creates its own `SceneManager` which calls `loadScene()`. If this sets the static `currentScene`, editor components on EditorGameObjects that call `SceneManager.getCurrentScene()` would get the **runtime** scene instead of null. This could cause subtle cross-contamination (editor components accessing runtime collision systems, etc.).

**Evidence:** `SceneManager` is not a singleton — multiple instances can exist. Play mode creates a separate `GameEngine`.

**Resolution:** NOT AN ISSUE — Editor code never calls `SceneManager.getCurrentScene()`. Editor uses `EditorContext.getCurrentScene()` for EditorScene access. Only runtime components (GridMovement, Door, etc.) are migrated to the static method, and these only execute during play mode. Components on EGOs don't have lifecycle methods called in the editor (R29 ensures `update()`/`start()` are no-ops). No cross-contamination path exists.

---

### R32: `isAncestorOf` is private on GameObject

**Raised by:** Architect (HIGH)

**Issue:** `GameObject.isAncestorOf(GameObject)` is `private` (line 158). `EditorGameObject.isAncestorOf(EditorGameObject)` is `public` (line 388). After `extends`, the overridden `setParent(GameObject)` receives a `GameObject` parameter and needs circular reference detection. But GO's private `isAncestorOf(GameObject)` is inaccessible from the subclass, and EGO's version takes the wrong type (`EditorGameObject`).

**Evidence:** `GameObject.java:158`, `EditorGameObject.java:388`

**Resolution:** APPROVED — Make `GameObject.isAncestorOf(GameObject)` **public**. Delete `EditorGameObject.isAncestorOf(EditorGameObject)` — inherited version works since EGO IS-A GameObject. `HierarchyDragDropHandler` call sites unchanged (EGO satisfies `GameObject` parameter). Design doc updated.

---

### R33: `RuntimeGameObjectAdapter.of(GameObject)` accepts EditorGameObjects

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `RuntimeGameObjectAdapter.of(GameObject)` (line 36) accepts any `GameObject`. After `extends`, `EditorGameObject IS-A GameObject`, so it would accept EditorGameObjects and create an adapter wrapping an editor entity — never intended.

**Evidence:** `RuntimeGameObjectAdapter.java:36`

**Resolution:** NOT AN ISSUE — `EditorGameObject` implements `HierarchyItem` directly, so hierarchy/inspector code never wraps it in `RuntimeGameObjectAdapter`. The adapter only exists for plain runtime GOs that don't implement `HierarchyItem`. The two external call sites (`InspectorPanel:144`, `HierarchyPanel:235`) only receive runtime GOs from the play mode scene. No code path passes an EGO to `of()`.

---

### R34: `getParent()` and `getChildren()` call-site count understated

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** Design doc estimates "~15 mechanical changes" for typed accessor migration. Actual counts: ~20+ for `getParent()` alone, ~30+ for `getChildren()`. Combined effort is ~50+ call sites, significantly more than estimated.

**Resolution:** RESOLVED by R25 — Estimate already corrected to ~50+ mechanical call-site changes in R25's resolution.

---

### R35: `getScene()` call count understated

**Raised by:** Senior Engineer + Type Audit (MEDIUM)

**Issue:** Design doc Pre-work table lists ~12 components. Actual count is 48 call sites across 17 files. Files like `PlayerDialogueManager` (multiple usages) and `GridMovement` (4 usages) are listed as single entries. Also, `WarpZone` line 238-239 calls `player.getScene()` on a different `GameObject` reference (the parameter, not `this.gameObject`).

**Resolution:** APPROVED — Estimation corrected: 48 call sites across 17 files (not ~12). All mechanical replacements (`gameObject.getScene()` → `SceneManager.getCurrentScene()`). Note `WarpZone` line 238-239 calls `player.getScene()` on a parameter GO, not `this.gameObject`. Design doc pre-work table updated with accurate counts.

---

### R36: Prefab auto-propagation UX gap

**Raised by:** Architect (MEDIUM)

**Issue:** Design says template change propagation becomes "explicit refresh from prefab action." But no UI affordance is specified: (a) what triggers the refresh, (b) how the user knows instances are stale, (c) whether saving a prefab template should auto-refresh open scenes. Without a clear path, users may edit templates and not realize instances are stale.

**Resolution:** APPROVED — Phase A includes prefab propagation mechanism:

**Open scene:** `EditorGameObject.refreshFromTemplate()` method: (1) save current overridden field values, (2) re-clone all components from updated template, (3) re-apply overridden values. Called from `PrefabEditController.save()` for all instances matching `prefabId` — replaces `invalidateInstanceCaches()`. Status bar message shown after refresh (e.g., "Refreshed N prefab instances").

**Closed scenes:** No action needed — scene files store `prefabId` + sparse overridden field diffs. On load, `fromData()` clones from the latest template and applies overrides. Always up to date.

**Edge cases:** New template component → instance gets it (no override). Removed template component → instance loses it, stale overrides silently dropped. Renamed field → old override silently dropped. Hierarchical prefabs → same logic per-node via `prefabNodeId`.

Design doc updated with propagation mechanism in Phase A scope.

---

### R37: Phase B scope contradiction — Section 4 vs Section 12

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** Section 4 says "Component.owner field deleted — gameObject field kept as single reference." Section 12 (Phase B bullets, line 443) says "Update Component: remove `gameObject` field, change `owner` to `GameObject`, simplify `setOwner()`." These directly contradict each other. The R14 resolution is correct (keep `gameObject`, delete `owner`).

**Evidence:** Design doc Section 4 (line 106-107) vs Section 12 Phase B bullet (line 443)

**Resolution:** APPROVED — Stale text. Section 12 Phase B bullet updated to match R14 resolution: delete `owner` field, keep `gameObject` typed as `GameObject`, rename `setOwner()` → `setGameObject()`. Design doc updated.

---

## Round 4 Review — Cleanup Completeness Audit

**Reviewers:** Senior Software Engineer (codebase audit) + Software Architect (architectural audit)

**Purpose:** Verify that after all phases (Pre-work + A + B + C) are implemented, no dangling methods, dead code, stale references, or architectural violations remain.

---

### R38: EGO's own `components` field shadows GO's after `extends`

**Raised by:** Senior Engineer (CRITICAL)

**Issue:** `EditorGameObject` has `private List<Component> components` (line 56). `GameObject` also has `private final List<Component> components` (line 38). After `extends`, EGO's field shadows GO's — all internal `this.components` access in EGO reads the shadow field, while inherited methods from GO use the parent's (empty) field.

**Evidence:** `EditorGameObject.java:56`, `GameObject.java:38`

**Resolution:** APPROVED — Add `components` to the field shadowing rule. Delete EGO's `components` field. All internal access goes through inherited methods (`getAllComponents()`, `addComponentInternal()`, etc.) or the reworked `addComponent()`/`replaceComponent()`. Design doc Section 12 field shadowing rule updated.

---

### R39: `getOwner()` callers break when `owner` field deleted

**Raised by:** Senior Engineer (CRITICAL)

**Issue:** Lombok generates `getOwner()` from the `owner` field on Component. When the field is deleted, the getter disappears. `ReparentEntityCommand` calls `transform.getOwner()` and `uiTransform.getOwner()` (lines 186, 211). Also `EditorGameObject.ensureOwnerSet()` calls `comp.getOwner() == null` (line 489).

**Evidence:** `ReparentEntityCommand.java:186,211`, `EditorGameObject.java:489`

**Resolution:** APPROVED — Callers migrate from `getOwner()` → `getGameObject()` (already exists on Component, currently deprecated — becomes the primary getter after `owner` field deletion). `ReparentEntityCommand` lines 186, 211 and `EditorGameObject.ensureOwnerSet()` line 489 updated in Phase B. Design doc Section 6 already covers the rename.

---

### R40: `isEditor()` missing on RuntimeGameObjectAdapter

**Raised by:** Architect (CRITICAL)

**Issue:** `RuntimeGameObjectAdapter` currently inherits `isEditor()` as a default method from `IGameObject` (`return !isRuntime()` → `false`). After `IGameObject` deletion and `HierarchyItem` becoming standalone with `isEditor()` declared, `RuntimeGameObjectAdapter` has no implementation. Compile error.

**Evidence:** `RuntimeGameObjectAdapter.java` — no `isEditor()` override. `IGameObject.java:105` — default method.

**Resolution:** ALREADY COVERED — R16 added `isEditor()` to standalone `HierarchyItem`. `RuntimeGameObjectAdapter` must implement it (`return false`). This is a mechanical addition during Phase B when HierarchyItem becomes standalone. Design doc Section 7 already says "fewer methods to delegate" — this is one new method to add.

---

### R41: `getTransform()` not in standalone HierarchyItem

**Raised by:** Architect (CRITICAL → downgraded to NOT AN ISSUE)

**Issue:** The design doc's standalone `HierarchyItem` interface (Section 5) omits `getTransform()`. Currently inherited from `IGameObject`. Both `EditorGameObject` and `RuntimeGameObjectAdapter` implement it. If any `HierarchyItem`-typed code calls `getTransform()`, compile error after migration.

**Evidence:** `IGameObject.java:42` — declares `getTransform()`. Design doc Section 5 interface listing — not included.

**Resolution:** NOT AN ISSUE — Grep confirms no code calls `getTransform()` on a `HierarchyItem`-typed variable. All calls are on `EditorGameObject`, `GameObject`, or `RuntimeGameObjectAdapter` directly. Safe to omit from standalone interface. Both implementors retain `getTransform()` as a concrete method — it's just not on the interface contract. Can be added later if a consumer needs it.

---

### R42: `clearParent()` on EGO becomes redundant

**Raised by:** Senior Engineer (HIGH)

**Issue:** `EditorGameObject.clearParent()` (lines 425-431) manually removes from parent's children list and nulls the parent field. After `extends`, `setParent(null)` via inherited `super.setParent()` handles this. Method is not mentioned for deletion in the design doc.

**Evidence:** `EditorGameObject.java:425-431`. One caller: `EditorScene.java:372`.

**Resolution:** APPROVED — Delete `clearParent()`. Migrate `EditorScene.java:372` to `entity.setParent(null)`. Phase B cleanup.

---

### R43: `getDepth()` uses `parent` field directly

**Raised by:** Senior Engineer (HIGH)

**Issue:** `EditorGameObject.getDepth()` (lines 415-423) walks `this.parent` directly. After the `parent` field is deleted (R24), this must use `getParent()` instead. Method still useful for hierarchy indentation in UI.

**Evidence:** `EditorGameObject.java:415-423`

**Resolution:** APPROVED — Rewrite `getDepth()` to use `getParent()` instead of `this.parent`. Method stays (used for hierarchy indentation). Phase B mechanical change.

---

### R44: Phase A dead code — transform helpers for virtual components

**Raised by:** Senior Engineer (HIGH)

**Issue:** Several methods on EGO exist solely for the virtual component model (`componentOverrides` map). After Phase A replaces virtual components with real ones, these become dead code:

- `getTransformVector()` / `setTransformVector()` (lines 306-330) — read/write float arrays from override map
- `findCachedTransform()` / `syncCachedTransformPosition()` / `syncCachedTransformRotation()` / `syncCachedTransformScale()` (lines 335-356) — sync `cachedMergedComponents` Transform
- `TRANSFORM_TYPE` constant (line 89) — only used by the above methods
- Position/rotation/scale helpers with `isScratchEntity()` branches (lines 194-300, ~100 lines) — can be simplified to delegate to `getTransform()`

None of these are explicitly listed for Phase A deletion in the design doc.

**Evidence:** `EditorGameObject.java:89, 194-356`

**Resolution:** APPROVED — Phase A deletes: `getTransformVector()`, `setTransformVector()`, `findCachedTransform()`, `syncCachedTransformPosition()`, `syncCachedTransformRotation()`, `syncCachedTransformScale()`, `TRANSFORM_TYPE` constant. Phase A simplifies: position/rotation/scale helpers (`getPosition()`, `setPosition()`, etc.) — remove `isScratchEntity()` branches, delegate to `getTransform()` directly (~100 lines simplified). Added to Phase A scope in design doc.

---

### R45: `ensureOwnerSet()` not assigned to a phase

**Raised by:** Architect (HIGH)

**Issue:** `ensureOwnerSet()` (lines 487-493) lazily sets component ownership. Listed in follow-up F5 as "stale after bridge deletion" but not assigned to any phase. Called at lines 471 and 572.

**Evidence:** `EditorGameObject.java:487-493`, F5 in follow-up table

**Resolution:** APPROVED — Delete `ensureOwnerSet()` and its call sites (lines 471, 572) in Phase B. After bridge deletion, components are owned immediately via `setGameObject()` in `addComponent()`. F5 follow-up resolved.

---

### R46: `DEFAULT_ENTITY_Z_INDEX` dead after Phase C

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** Static constant (line 88) only used by `getZIndex()` which is deleted when EGO stops implementing `Renderable` in Phase C. Not listed for deletion.

**Evidence:** `EditorGameObject.java:88`

**Resolution:** APPROVED — Delete `DEFAULT_ENTITY_Z_INDEX` in Phase C alongside `getZIndex()`. Added to Phase C cleanup list.

---

### R47: `hierarchyVersion` on EditorScene — dead after bridge deletion

**Raised by:** Architect (MEDIUM)

**Issue:** `EditorScene.hierarchyVersion` (line 69, incremented at lines 355, 380, 727) is only consumed by `EditorUIBridge` for cache invalidation. After bridge deletion, no remaining consumer. Tests in `EditorSceneHierarchyTest` (lines 52-54, 155-159) test it directly and would also need updating.

**Evidence:** `EditorScene.java:69`, `EditorUIBridge.java:56`

**Resolution:** APPROVED — Delete `hierarchyVersion` field, its increments (lines 355, 380, 727), and `getHierarchyVersion()` in Phase B. Delete corresponding tests in `EditorSceneHierarchyTest` (lines 52-54, 155-159). F6 follow-up resolved.

---

### R48: `selectedObject: GameObject` on EditorScene — legacy dead field

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `EditorScene.selectedObject` (line 146) is a legacy `GameObject` field. Only written to clear it (set null). Selection is managed via `selectedEntities` (`Set<EditorGameObject>`). Dead code predating the refactor.

**Evidence:** `EditorScene.java:146`

**Resolution:** APPROVED — Delete `selectedObject` field and its clearing code in Phase B. Legacy dead code. F7 follow-up resolved.

---

### R49: `getParent()`/`getChildren()` orphaned on RuntimeGameObjectAdapter

**Raised by:** Architect (MEDIUM)

**Issue:** `RuntimeGameObjectAdapter.getParent()` (line 96-98) and `getChildren()` (line 102-104) currently satisfy `IGameObject` contract. After `IGameObject` deletion and standalone `HierarchyItem` (which uses `getHierarchyParent()`/`getHierarchyChildren()`), these methods have no interface requiring them. Already identified as F4 follow-up.

**Evidence:** `RuntimeGameObjectAdapter.java:96-104`

**Resolution:** APPROVED — Delete `getParent()` and `getChildren()` from RuntimeGameObjectAdapter in Phase B. Replaced by `getHierarchyParent()`/`getHierarchyChildren()` on standalone HierarchyItem. F4 follow-up resolved.

---

### R50: `TilemapLayerRenderable` import in RenderDispatcher — engine→editor dependency persists

**Raised by:** Architect (MEDIUM)

**Issue:** `RenderDispatcher.java` line 5 imports `com.pocket.rpg.editor.rendering.TilemapLayerRenderable` and has a branch at lines 102-106. The design doc removes the `EditorGameObject` branch but not the `TilemapLayerRenderable` branch. After Phase C, this is the only remaining engine→editor import in RenderDispatcher.

**Evidence:** `RenderDispatcher.java:5,102-106`

**Resolution:** APPROVED — Phase C. `TilemapLayerRenderable` depends on `TilemapLayer` from `editor.scene`, so a simple package move won't work. Options: (a) move the `instanceof TilemapLayerRenderable` branch out of `RenderDispatcher` into `EditorSceneRenderer`, or (b) move both `TilemapLayerRenderable` and `TilemapLayer` to a shared package. Decide during Phase C implementation.

---

### R51: `EditorFramebuffer` import in FramebufferTarget — engine→editor dependency

**Raised by:** Architect (MEDIUM)

**Issue:** `FramebufferTarget.java` line 3 imports `com.pocket.rpg.editor.rendering.EditorFramebuffer`. Rendering-layer dependency on editor package.

**Evidence:** `FramebufferTarget.java:3`

**Resolution:** APPROVED — Phase C. `FramebufferTarget` is only used by editor panels — move it to `editor.rendering` package where its dependency lives. Alternatively, move `EditorFramebuffer` to a shared rendering package.

---

### R52: Engine components import `HierarchyItem` from editor package

**Raised by:** Architect (MEDIUM)

**Issue:** `LayoutGroup`, `UIScrollbar`, `UIScrollView`, and `UITransformDriver` import `com.pocket.rpg.editor.panels.hierarchy.HierarchyItem`. These are engine components depending on an editor interface — architectural violation predating this refactor but not addressed by it.

**Evidence:** `LayoutGroup.java`, `UIScrollbar.java`, `UIScrollView.java`, `UITransformDriver.java`

**Resolution:** APPROVED — Phase C. Change `UITransformDriver.getChildDriverInfo(HierarchyItem)` → `getChildDriverInfo(GameObject)`. All 3 implementors update parameter type. `UIScrollView` uses `child.getComponent()` which is on `GameObject` — works as-is. `TransformDriverDetector.detect()` parameter changes from `HierarchyItem` to `GameObject` (or casts internally). This is editor-only inspector code — never runs at runtime. Play mode inspectors are read-only so `TransformDriverDetector` doesn't run for `RuntimeGameObjectAdapter`. Removes all `HierarchyItem` imports from engine components.

---

### R53: Stale comments referencing bridge/wrapper/IGameObject

**Raised by:** Senior Engineer (LOW)

**Issue:** ~12 locations across the codebase have comments/javadoc referencing "bridge", "wrapper", or "IGameObject" that become stale after the refactor. Key files: `Component.java` (lines 32, 87, 95), `UIComponent.java` (line 70), `EditorGameObject.java` (lines 484-485, 682), `EditorScene.java` (line 66), `HierarchyItem.java` (lines 9-14), `RuntimeGameObjectAdapter.java` (line 47).

**Resolution:** APPROVED — Phase B. Clean up stale comments/javadoc referencing "bridge", "wrapper", or "IGameObject" as files are touched during Phase B implementation. Key locations: `Component.java` (lines 32, 87, 95), `UIComponent.java` (line 70), `EditorGameObject.java` (lines 484-485, 682), `EditorScene.java` (line 66), `HierarchyItem.java` (lines 9-14), `RuntimeGameObjectAdapter.java` (line 47).

---

## Round 5 Review — Implementation Readiness Audit

**Reviewers:** Senior Software Engineer (per-phase implementation review) + Software Architect (per-phase implementation review)

**Purpose:** For each phase, verify that the design contains enough information to implement without ambiguity. Identify missing details, contradictions, and unaddressed edge cases.

---

### Pre-work Gaps

---

### R54: `setParent()` scene-propagation logic rewrite not specified

**Raised by:** Senior Engineer (HIGH)

**Issue:** `GameObject.setParent()` (lines 77-126) contains significant scene-registration logic: reads `this.scene` and `newParent.scene` (private field access), calls `oldScene.unregisterCachedComponents()`, `setSceneRecursive()`, and `newScene.registerCachedComponents()`. The design says "scene registration naturally skipped via null `SceneManager.getCurrentScene()`" for the EGO case, but doesn't specify how the runtime `setParent()` logic itself is rewritten after removing the `scene` field. Cross-scene parenting semantics are not addressed.

**Evidence:** `GameObject.java:77-126`

**Resolution:** APPROVED — After Pre-work, `setParent()` is simplified: delete `this.scene`/`newParent.scene` field access, delete `setSceneRecursive()`, delete old-scene/new-scene comparison logic. Replace with single `Scene scene = SceneManager.getCurrentScene(); if (scene != null) { scene.unregisterCachedComponents(this); scene.registerCachedComponents(this); }` after the parent assignment. One scene at a time, no cross-scene parenting. Editor case: `getCurrentScene()` returns null, block skipped naturally. Design doc Pre-work updated.

---

### R55: Double-destroy re-entrancy risk

**Raised by:** Senior Engineer (HIGH)

**Issue:** Design proposes `destroy()` self-removes from scene. But `Scene.removeGameObject()` currently calls `obj.destroy()`. If a component calls `gameObject.destroy()`, and `destroy()` calls `scene.removeGameObject()`, and `removeGameObject()` calls `destroy()` again — recursive double call. `GameObject.destroy()` has no `if (destroyed) return;` guard.

**Evidence:** `GameObject.java:449-470`, `Scene.java:331-337`

**Resolution:** APPROVED — Add `if (destroyed) return;` guard at top of `destroy()`. Add `Scene.removeFromScene(GameObject)` helper (removes from gameObjects list + unregisters cached components, no destroy call). `destroy()` calls `scene.removeFromScene(this)` via `SceneManager.getCurrentScene()`. `Scene.removeGameObject()` becomes a thin wrapper: `obj.destroy()` (which internally calls `removeFromScene`). No re-entrancy possible. Design doc Pre-work updated.

---

### R56: `addGameObject()` duplicate-add guard removed with `scene` field

**Raised by:** Senior Engineer (HIGH)

**Issue:** `Scene.addGameObject()` checks `if (obj.getScene() != null) throw IllegalStateException` to prevent adding a GO to two scenes. The design removes `getScene()` from GameObject but doesn't specify what replaces this guard.

**Evidence:** `Scene.java:315`

**Resolution:** APPROVED — Replace `obj.getScene() != null` guard with `gameObjects.contains(obj)`. O(n) on CopyOnWriteArrayList but `addGameObject()` is a scene setup operation, not hot path. Guard changes from "belongs to a scene" to "belongs to this scene" — same protection with single active scene. `obj.setScene(this)` deleted. Design doc Pre-work updated.

---

### R57: `Scene.destroy()` interaction with self-removing `destroy()`

**Raised by:** Architect (MEDIUM)

**Issue:** `Scene.destroy()` iterates `gameObjects` and calls `destroy()` on each. If `destroy()` now self-removes from the scene list, this causes redundant removals. The list is `CopyOnWriteArrayList` (safe from CME), but the contract is unclear: does `Scene.destroy()` still call per-object `destroy()`, or just `clear()`?

**Evidence:** `Scene.java:289-303`

**Resolution:** NOT AN ISSUE — `Scene.destroy()` already copies the list into a snapshot (`new ArrayList<>(gameObjects)`) before iterating. Each `destroy()` self-removes from `gameObjects` via `removeFromScene()`, but iteration is on the snapshot — no CME, no skipped elements. `gameObjects.clear()` at the end is redundant but harmless. No changes needed.

---

### R58: Multiple `SceneManager` instances during play mode transitions

**Raised by:** Architect (MEDIUM)

**Issue:** `SceneManager` is an instance per `GameEngine`. `PlayModeController` creates a new `GameEngine` on play. The static `currentScene` could have two writers during transitions. The design doesn't specify lifecycle ordering (is old engine fully destroyed before new one sets the static?).

**Evidence:** `PlayModeController.java`, `SceneManager.java`

**Resolution:** NOT AN ISSUE — `PlayModeController` lifecycle is sequential: `play()` creates engine + loads scene, `stop()` calls `engine.destroy()` which nulls everything. No overlap. Editor never sets the static (uses `EditorContext.getCurrentScene()`). Only play mode's `SceneManager.loadScene()` sets/clears the static. One writer at a time.

---

### R59: Static `currentScene` timing relative to `Scene.initialize()`

**Raised by:** Architect (MEDIUM)

**Issue:** In `SceneManager.loadSceneInternal()`, the static must be set before `Scene.initialize()` calls `onLoad()` → `addGameObject()` → component `start()` methods that use `SceneManager.getCurrentScene()`. The design doesn't specify where in the sequence the static is set.

**Evidence:** `SceneManager.java:173-196`

**Resolution:** APPROVED — Static `activeScene` set in `loadSceneInternal()` at the same point as instance `currentScene` (line 180, before `initialize()`). Private setters for clarity: `setActiveScene(scene)` on load, `clearActiveScene()` on destroy/unload. Components use `SceneManager.getActiveScene()`. Named `activeScene`/`getActiveScene()` to distinguish from instance `currentScene`/`getCurrentScene()`. Design doc Pre-work updated.

---

### R60: `PlayerCameraFollow` has no null guard — will NPE after migration

**Raised by:** Senior Engineer (LOW)

**Issue:** `PlayerCameraFollow.lateUpdate()` chains `getGameObject().getScene().getCamera().setPosition(...)` with no null check. After migration to `SceneManager.getCurrentScene().getCamera()`, an NPE can occur during scene transitions if `getCurrentScene()` returns null.

**Evidence:** `PlayerCameraFollow.java:14`

**Resolution:** NOT AN ISSUE — Implementation detail, not a design gap. When migrating each component, the implementer adds `Scene scene = SceneManager.getActiveScene(); if (scene == null) return;` as needed. `PlayerCameraFollow` is already in the component table.

---

### R61: Component table incomplete — 17 files, not all listed

**Raised by:** Senior Engineer (LOW)

**Issue:** Grep finds 17 files with `getScene()` usage. Some like `DialogueEventListener` have two different usage patterns (removeGameObject AND getComponentsImplementing) but only one is listed.

**Resolution:** NOT AN ISSUE — Table is representative, not exhaustive. Implementer greps for `getScene()` during migration. Note added to design doc.

---

### R62: Test state leaking from `setCurrentScene()` static

**Raised by:** Architect (LOW)

**Issue:** Tests that set `SceneManager.setCurrentScene(scene)` risk leaking state between tests if they don't clean up.

**Resolution:** NOT AN ISSUE — Standard test practice. Tests use `setActiveSceneForTest(scene)` in setup and null it in `@AfterEach`. No design gap.

---

### Phase A Gaps

---

### R63: `SetFieldCommand` undo command not mentioned in design

**Raised by:** Senior Engineer (HIGH)

**Issue:** A second field-editing undo command `SetFieldCommand` exists alongside `SetComponentFieldCommand`. It uses `entity.setFieldValue(componentType, fieldName, value)` — the string-based API. Not mentioned in the design doc's undo command table at all. Will break if not updated for the new override model.

**Evidence:** `SetFieldCommand.java`

**Resolution:** NOT AN ISSUE — `SetFieldCommand` has zero callers (dead code). Delete in Phase A alongside the virtual component model it was built for. Added to Phase A cleanup.

---

### R64: String-based API fate — `getFieldValue()`/`setFieldValue()`

**Raised by:** Senior Engineer (HIGH)

**Issue:** `EditorGameObject.getFieldValue(String componentType, String fieldName)` and `setFieldValue(...)` are called from 6+ places: `ComponentFieldEditor`, `FieldEditorContext`, `SetFieldCommand`, `EditorSceneSerializer`, and tests. The design says "replace all componentOverrides reads/writes with direct component field access" but doesn't specify whether this string-based API is kept (reimplemented internally to operate on real components + overriddenFields) or deleted (requiring all callers to change).

**Evidence:** `EditorGameObject.java`, `ComponentFieldEditor.java:110`, `FieldEditorContext.java:125,149`, `SetFieldCommand.java:30,35`

**Resolution:** APPROVED — Delete `getFieldValue()` and `setFieldValue()` from EGO. After Phase A, components are real — callers use `ComponentReflectionUtils` directly. The 4 callers migrate to direct component access + `entity.markFieldOverridden(componentType, fieldName)`:
- `SetComponentFieldCommand:84` → set on component + `markFieldOverridden()`
- `ComponentFieldEditor:110` → already reads from component, replace `setFieldValue` with `markFieldOverridden()`
- `FieldEditorContext:125` → already named `markFieldOverridden`, stop passing value, delegate to `entity.markFieldOverridden()`
- `EntityCreationService:268` → read from component, set on copy's component, `copy.markFieldOverridden()`

Dead `SetFieldCommand` deleted (R63). EGO keeps only: `markFieldOverridden()`, `isFieldOverridden()`, `resetFieldToDefault()`, `getFieldDefault()`. Design doc Phase A updated.

---

### R65: `toData()` diff computation strategy unspecified

**Raised by:** Senior Engineer (HIGH)

**Issue:** The design says `toData()` "diffs current field values against template defaults to compute overrides." But doesn't specify: (a) diff all fields or just fields in `overriddenFields`? (b) How to convert typed Java objects (Vector3f, int) back to Gson-compatible format (float[], Double) for the componentOverrides JSON map?

**Evidence:** `EditorGameObject.java:toData()` (line 1009-1048)

**Resolution:** APPROVED — `toData()` iterates `overriddenFields` (component type → field names), reads each value from the real component via `ComponentReflectionUtils.getFieldValue()`, writes as `componentOverrides` to JSON. `fromData()` reads JSON `componentOverrides` (has values), clones components from template, applies values via reflection, builds `overriddenFields` Set from the keys. No diff against template needed at save time — `overriddenFields` already tracks what changed. Design doc serialization strategy updated with explicit load/save conversion steps.

---

### R66: Component identity after `ResetAllOverridesCommand` re-clone

**Raised by:** Architect (HIGH)

**Issue:** `ResetAllOverridesCommand` execute re-clones all components from template — creating new `Component` instances. Inspectors and other undo commands on the stack may hold references to the old components, causing stale reference bugs (the same class of bug the refactor aims to fix).

**Resolution:** APPROVED — Real concern. `SetComponentFieldCommand` and `ResetFieldOverrideCommand` both hold `Component` references that go stale after re-clone. Fix: drop `Component` field from both commands, store `componentType` string instead. Execute/undo look up the component each time via `entity.findComponentByType(componentType)`. Same pattern the deleted `SetFieldCommand` used. Pre-existing bug (today's `invalidateComponentCache()` already orphans cached clones) — fixed as part of Phase A undo rework. Design doc undo command table updated.

---

### R67: Clone timing — eager constructor vs lazy access

**Raised by:** Architect (HIGH)

**Issue:** The design says "Prefab instances clone components at creation time." But `PrefabRegistry` must be populated before the constructor runs. Current `fromData()` lazy approach avoids this timing issue. The design doesn't specify the exact constructor contract or when cloning happens.

**Evidence:** `EditorGameObject.java` constructors (lines 98-119, 141)

**Resolution:** NOT AN ISSUE — `PrefabRegistry.initialize()` is called at `EditorApplication:156` during editor startup, before any scene is loaded. By the time `fromData()` constructs an EGO, the registry is fully populated. Eager cloning in the constructor works fine.

---

### R68: Auto-pruning semantics for `overriddenFields`

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** Current `isFieldOverridden()` returns false even if a value is in overrides but matches the default. The design mentions "remove from overriddenFields if value matches template default" in undo commands, but doesn't specify whether this pruning is centralized in `setFieldValue()` or left to each caller.

**Resolution:** APPROVED — Pruning lives in the undo command, not in `markFieldOverridden()`. The command compares new value against `getFieldDefault()` — if equal, removes from `overriddenFields`; if different, calls `markFieldOverridden()`. `markFieldOverridden()` itself is a simple add to the Set.

---

### R69: Transform field name verification

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `TransformEditors.java` uses string keys `"localPosition"`, `"localRotation"`, `"localScale"` with `TRANSFORM_TYPE`. After Phase A, these strings must match field names in `ComponentMeta` for `Transform`. If `ComponentMeta` registers them as `"position"` instead of `"localPosition"`, inspector code breaks.

**Resolution:** NOT AN ISSUE — Transform fields are named `localPosition`, `localRotation`, `localScale` (lines 34-36). `ComponentMeta` uses actual Java field names. `TransformEditors` uses the same strings. They match.

---

### R70: `FieldEditorContext` double-write after Phase A

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `FieldEditorContext.resetFieldToDefault()` calls both `ComponentReflectionUtils.setFieldValue(component, ...)` and `entity.resetFieldToDefault(componentType, fieldName)`. After Phase A, if `entity.resetFieldToDefault()` already sets the field on the real component, `FieldEditorContext` is doing a redundant double-write that could cause ordering bugs.

**Evidence:** `FieldEditorContext.java:149`

**Resolution:** RESOLVED by R64 — `setFieldValue` is deleted. `FieldEditorContext.markFieldOverridden()` calls `entity.markFieldOverridden(componentType, fieldName)` instead. No double-write: field editor widget sets the value on the component, `markFieldOverridden()` only tracks the override name.

---

### R71: Hierarchical prefab child node construction path

**Raised by:** Architect (MEDIUM)

**Issue:** `PrefabHierarchyHelper.expandChildren()` creates child nodes via `new EditorGameObject(prefabId, nodeId, position)`. After Phase A, this constructor must clone the node's components, requiring access to the prefab definition. The design doesn't specify whether `expandChildren()` passes pre-cloned components or the constructor resolves them.

**Evidence:** `PrefabHierarchyHelper.java`

**Resolution:** NOT AN ISSUE — The child node constructor already receives `prefabId` + `prefabNodeId`. It looks up the node via `PrefabRegistry.getInstance().getPrefab(prefabId).findNode(prefabNodeId)` and clones its components. Registry is populated at startup (R67). `expandChildren()` unchanged — cloning is internal to the constructor.

---

### R72: Code-defined prefabs (`PlayerPrefab`, `DialogueUIPrefab`)

**Raised by:** Senior Engineer (LOW)

**Issue:** Code-defined prefabs implement `Prefab` directly. Their `getComponents()` returns fresh instances each time. After Phase A, `toData()` diffs against template defaults via `getComponents()` — creates garbage each call but functionally correct. Not a blocker but not covered by tests.

**Resolution:** NOT AN ISSUE — Code-defined prefabs return fresh instances from `getComponents()`, which works for cloning and `getFieldDefault()`. Functionally correct. Add test coverage for code-defined prefabs in Phase A tests.

---

### Phase B Gaps

---

### R73: Components list mutability — 5 call sites directly mutate the list

**Raised by:** Senior Engineer (HIGH)

**Issue:** After EGO uses inherited `components` from GO (private), `getAllComponents()` returns a defensive copy. But 5 call sites directly mutate the list: `ReparentEntityCommand.performTransformSwap()` (`components.set(index, ...)`), `SwapTransformCommand.replaceTransform()` (`components.set()`), `RemoveComponentCommand.undo()` (`add(index, ...)`), `PrefabEditController.buildWorkingScene()` (`add(0, ...)`), `EditorGameObject.addComponent()`. The design adds `replaceComponent()` for the first two, but no path for index-based insertion.

**Evidence:** `ReparentEntityCommand.java:157`, `SwapTransformCommand.java:131`, `RemoveComponentCommand.java:34`, `PrefabEditController.java:398`

**Resolution:** APPROVED — Add `protected addComponentAt(int index, Component)` on `GameObject` for index-based insertion. EGO's `replaceComponent(old, new)` and `addComponent()` also go through inherited list. The 5 call sites migrate:
- `SwapTransformCommand` / `ReparentEntityCommand` → `replaceComponent(old, new)`
- `RemoveComponentCommand.undo()` → `addComponentAt(index, component)`
- `PrefabEditController` → `addComponentAt(0, transform)`
- `EditorGameObject.addComponent()` → reworked to use inherited list via `super`

Design doc Section 12 updated.

---

### R74: `insertEntityAtPosition()` uses bypass methods not addressed

**Raised by:** Senior Engineer (HIGH)

**Issue:** `EditorScene.insertEntityAtPosition()` (lines 658-729) uses `getChildrenMutable()` (direct list mutation) and `setParentDirect()` (sets parent without touching children lists) for ordered reparenting. Both are deleted in the design. This is a critical path used by `ReparentEntityCommand` and `HierarchyDragDropHandler`.

**Evidence:** `EditorScene.java:658-729`

**Resolution:** NOT AN ISSUE — `insertEntityAtPosition()` rewrites to use `setParent()` + `setOrder()`. No direct list mutation needed. `getChildrenMutable()` and `setParentDirect()` deleted because `setParent()` handles children list, `setOrder()` handles display order. Method gets simpler.

---

### R75: `getChildren()` return type impact undercounted

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** Design estimates "~5 prefab serialization sites" need casts. Actual count: 18+ enhanced for-loops with `EditorGameObject` loop variable + 7+ local variable assignments typed as `List<EditorGameObject>`. Total ~25-30 sites, not ~5. Some loops call EGO-specific methods (`isPrefabChildNode()`, `getOrder()`).

**Resolution:** NOT AN ISSUE — Scope undercount, not a design gap. Most loops just change loop variable to `GameObject` since `getId()`, `getOrder()`, `setParent()` are all on `GameObject`. The ~5 cast sites are specifically for EGO-only methods like `isPrefabChildNode()`. Already covered by R34's "~50+ mechanical changes" estimate.

---

### R76: `getParent()` return type — 13+ cast sites, chain-walking is unwieldy

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** 13+ sites assign `entity.getParent()` to `EditorGameObject` variable. Some walk the parent chain in a loop (`parent = parent.getParent()`), which becomes unwieldy with casts at every step. Design says "cast inline where needed (rare)" but it's not rare.

**Resolution:** NOT AN ISSUE — Same as R75. Most sites change variable type to `GameObject`. Chain-walking loops use `(EditorGameObject)` cast where EGO-specific methods needed. Mechanical.

---

### R77: Double Transform construction in EGO constructors

**Raised by:** Architect (MEDIUM)

**Issue:** `super(name)` auto-creates Transform. EGO scratch constructors also create Transform (`components.add(new Transform(position))`). After `extends`, both run — double Transform. The "Resolved" risk note says "prefab clone updates existing Transform's values" but doesn't show the actual constructor code pattern.

**Evidence:** `EditorGameObject.java:141`, `GameObject.java` constructor

**Resolution:** ALREADY RESOLVED — Design doc already covers this (Section 12, Risk Assessment "RESOLVED: Transform usage"). `super(name)` creates Transform, EGO sets values on it via `getTransform().setPosition()`. No second Transform.

---

### R78: `isRuntime()` broken intermediate state between commits

**Raised by:** Architect (MEDIUM)

**Issue:** `IGameObject.isRuntime()` default is `return this instanceof GameObject`. If Commit 1 (EGO extends GO) lands before Commit 2 (delete IGameObject), EGOs return `isRuntime() == true` in the gap. `AlphaGroup` dispatches on `isRuntime()` — takes wrong branch.

**Resolution:** NOT AN ISSUE — The design doc shouldn't prescribe commit structure. Phase B is one unit of work — EGO extends GO, delete IGameObject, delete bridge all land together. No intermediate state where `isRuntime()` returns wrong value. Remove "Commit 1"/"Commit 2" labels from design doc.

---

### R79: `isOwnEnabled()` can't access private `enabled` field

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `EditorGameObject.isOwnEnabled()` returns the raw `enabled` field. After `extends`, `enabled` is private on GO. Used by inspector checkboxes, context menus, serialization, and the `isEnabled()` override itself.

**Evidence:** `EditorGameObject.java:704`

**Resolution:** RESOLVED — Replaced with a cleaner two-method model on `GameObject`:
- `isEnabled()` — raw field getter (Lombok). Returns this object's own flag only.
- `isActiveInHierarchy()` — new method on GO, walks parent chain. True only if this AND all ancestors are enabled.
- EGO inherits both, overrides neither. Delete `isOwnEnabled()` from EGO.
- ~20 editor call sites migrated: visibility/rendering checks → `isActiveInHierarchy()`, toggle/serialize checks → `isEnabled()`.
- Design doc Section 1 override table, Section 12, Phase B changes and unit tests all updated.

---

### R80: `getComponents(Class)` prefab note contradicts Phase A

**Raised by:** Architect (LOW)

**Issue:** Phase B Section 1 says `getComponents(Class)` override: "Prefab instances return merged/cached list; scratch entities delegate to super." But Phase A eliminates the cached merged components pattern — all entities have real components. This note is stale.

**Resolution:** RESOLVED — Removed `getComponents(Class)` from the override table entirely. After Phase A all entities have real components, so no override needed — inherited from GO. Override count updated from ~9 to ~8.

---

### R81: `hierarchyVersion` is on `EditorScene`, not `EditorGameObject`

**Raised by:** Senior Engineer (LOW)

**Issue:** R47 says "Delete `hierarchyVersion` field from EditorGameObject." But the field is on `EditorScene`, not EGO. Design targets wrong class.

**Evidence:** `EditorScene.java:69`

**Resolution:** RESOLVED — Already fixed in earlier round. Design doc Phase B line correctly says "from EditorScene".

---

### R82: `RuntimeGameObjectAdapter.of()` needs guard against EGO

**Raised by:** Architect (LOW)

**Issue:** After EGO IS-A GO, `RuntimeGameObjectAdapter.of(GameObject)` could accidentally accept an EditorGameObject. Should add defensive `instanceof EditorGameObject` check.

**Resolution:** RESOLVED — Added guard to Section 7 (RuntimeGameObjectAdapter): `of(GameObject)` throws `IllegalArgumentException` if `gameObject instanceof EditorGameObject`.

---

### Phase C Gaps

---

### R83: No replacement pattern for EGO entity rendering in editor

**Raised by:** Senior Engineer (HIGH)

**Issue:** Design says EGO stops implementing `Renderable` and `EditorSceneRenderer` submits `SpriteRenderer` components instead. But current rendering handles: (a) sprite tinting for selected/hovered entities, (b) z-index fallback from `DEFAULT_ENTITY_Z_INDEX`, (c) broken-prefab-link icon overlay. None of these have a specified replacement. `SpriteRenderer` doesn't know about selection state or broken prefab links.

**Evidence:** `EditorSceneRenderer.java`, `RenderDispatcher.java:submitEditorGameObject()`

**Resolution:** RESOLVED — Phase C section rewritten with full rendering replacement:
- `EditorSceneRenderer` reads `SpriteRenderer` directly from EGOs. Visibility filtering, tinting, z-index all handled inline.
- Visibility-mode tinting stays in `getEntityTint()` (unchanged).
- Z-index from `SpriteRenderer.getZIndex()` directly (no fallback constant needed).
- Broken-prefab icon rendered as overlay by `EditorSceneRenderer`.
- Picking pass reads SpriteRenderer directly, `ResolvedGeometry` deleted.

---

### R84: `PickingPass` uses `resolveEntityGeometry()` and `ResolvedGeometry` — both being deleted

**Raised by:** Senior Engineer (HIGH)

**Issue:** `PickingPass` (or picking code in `EditorSceneRenderer`) calls `RenderDispatcher.resolveEntityGeometry()` which returns a `ResolvedGeometry` record. The design deletes both from `RenderDispatcher` but doesn't mention the picking pass or where this logic moves.

**Evidence:** `EditorSceneRenderer.java` picking code, `RenderDispatcher.java:resolveEntityGeometry()`

**Resolution:** RESOLVED — Covered in R83 resolution. Picking pass reads SpriteRenderer directly. `resolveEntityGeometry()` and `ResolvedGeometry` both deleted.

---

### R85: Broken-prefab-link icon rendering has no new home

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `EditorGameObject.getCurrentSprite()` returns a broken-link icon when the prefab template can't be found. After deleting this method (EGO stops implementing `Renderable`), there's no mechanism to show the broken prefab indicator.

**Evidence:** `EditorGameObject.java:getCurrentSprite()`

**Resolution:** RESOLVED — `EditorSceneRenderer` renders broken-link icon as overlay when `entity.isPrefabInstance() && !PrefabRegistry.has(entity.getPrefabId())`. Added to Phase C design.

---

### R86: `isRenderVisible()` called in `EditorSceneRenderer`

**Raised by:** Senior Engineer (MEDIUM)

**Issue:** `EditorSceneRenderer` calls `entity.isRenderVisible()` to filter which entities get submitted. After EGO stops implementing `Renderable`, this method is deleted. The filtering logic needs a replacement.

**Evidence:** `EditorSceneRenderer.java`

**Resolution:** RESOLVED — Inline check in `EditorSceneRenderer`: `entity.isActiveInHierarchy() && spriteRenderer != null && spriteRenderer.getCurrentSprite() != null`. Added to Phase C design.

---

### R87: `instanceof EditorGameObject` checks serve real semantic purposes

**Raised by:** Architect (MEDIUM)

**Issue:** Design says "remove remaining instanceof checks" in ReflectionFieldEditor, UITransformInspector, CustomComponentInspector, CustomComponentEditorRegistry. But some checks guard real behavior: `ReflectionFieldEditor.hasComponentOfType(IGameObject)` distinguishes editor vs runtime for component lookup. `UITransformInspector` checks to enable prefab-specific UI. These aren't just type narrowing — they're feature gates.

**Evidence:** `ReflectionFieldEditor.java`, `UITransformInspector.java`, `CustomComponentInspector.java`

**Resolution:** RESOLVED — Replace `instanceof EditorGameObject` with `entity.isEditor()` / `entity.isRuntime()` (from Section 13). Feature gates preserved, just using the new API. Updated Phase C design with per-file detail.

---

### R88: `ResolvedGeometry` record — destination unclear

**Raised by:** Senior Engineer (LOW)

**Issue:** `ResolvedGeometry` is a public inner record on `RenderDispatcher`. Picking pass imports it. Design deletes `resolveEntityGeometry()` but doesn't say what happens to the record type itself.

**Evidence:** `RenderDispatcher.java:135`

**Resolution:** RESOLVED — `ResolvedGeometry` record deleted along with `resolveEntityGeometry()`. Both consumers (render pass, picking pass) read SpriteRenderer directly. Covered in Phase C design update.

---

### R89: `TilemapLayerRenderable` may be dead code

**Raised by:** Senior Engineer (LOW)

**Issue:** Design says to move `TilemapLayerRenderable` dispatch out of `RenderDispatcher`. But need to verify it's actually used — if it's dead code, the move is unnecessary and it should just be deleted.

**Resolution:** RESOLVED — Confirmed used in `EditorSceneAdapter.java` lines 63 and 94 (dimmed inactive layers, active layer rendering). Not dead code. Move to `editor/rendering/` as designed.

---

### R90: AlphaGroup dispatch method also needs cleanup

**Raised by:** Architect (LOW)

**Issue:** Design says "3 methods → 1" for AlphaGroup. But `applyAlphaInternal()` (the dispatch method checking `isRuntime()`) also needs simplification — it's not just the two type-specific methods being merged, the dispatch itself is eliminated.

**Resolution:** RESOLVED — Phase C design updated to explicitly state: merge `applyAlphaToGameObject()` + `applyAlphaToEditorGameObject()` into single `applyAlpha(GameObject)`, AND delete `applyAlphaInternal()` dispatch. 3 methods → 1.