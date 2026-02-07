# Enable/Disable Feature - Plan Review

Code review of `enable-disable-feature.md` against the actual codebase. Each issue includes the evidence from the code, why it matters, and a proposed fix. Each proposed fix is then challenged in a "Self-review" subsection.

---

## Critical Issues

### 1. Scene Cache Re-registration Ignores Child Enabled State

**Location:** Phase 1 — `GameObject.setEnabled()` proposed code, lines 83-88.

**Evidence:** `Scene.registerCachedComponents()` (Scene.java:339-366) recurses into ALL children unconditionally:

```java
// Scene.java:363-365
for (GameObject child : gameObject.getChildren()) {
    registerCachedComponents(child);  // No enabled check
}
```

**Scenario:**
```
Parent (enabled=true) -> Child (enabled=false, has SpriteRenderer)
```
1. Disable Parent -> `unregisterCachedComponents(parent)` removes everything recursively. Correct.
2. Re-enable Parent -> `registerCachedComponents(parent)` re-registers EVERYTHING recursively — including Child's SpriteRenderer, even though Child is still individually disabled.

**Result:** Disabled child's renderer is back in the scene cache.

#### Self-review: Severity is overstated — rendering consumers already guard

All rendering consumers double-check enabled state before acting:
- `RenderDispatcher.submit()` (RenderDispatcher.java:81) checks `renderable.isRenderVisible()` — which calls `isEnabled()` on `SpriteRenderer`/`TilemapRenderer`
- `UIRenderer.render()` (UIRenderer.java:210) checks `canvas.isEnabled()` before rendering
- `UIRenderer.renderCanvasSubtree()` (UIRenderer.java:241) checks `root.isEnabled()` before recursing

So a disabled child's renderable being in the scene cache **does not cause it to render**. The rendering pipeline has its own guards. This downgrades the severity from "critical rendering bug" to "optimization waste + one real correctness concern."

**The real correctness issue is `ComponentKeyRegistry`:** When `registerCachedComponents()` runs, it registers components with `ComponentKeyRegistry` (Scene.java:350-353). `ComponentKeyRegistry.get()` returns components without checking `isEnabled()`. Game code using `@ComponentReference` would resolve to a disabled component and interact with it as if active. This is the actual bug — it's about key-based lookups, not rendering.

#### Proposed fixes — reconsidered

**Option A (split into non-recursive helpers):** Correct but introduces new API surface (`registerCachedComponents_self`) and makes `propagateParentEnabledChange()` do double duty (callbacks + caches). The method name stops describing what it does. Workable but noisy.

**Option B (add enabled check to `Scene.registerCachedComponents`):** Simpler. Other callers:
- `Scene.addGameObject()` (Scene.java:303) — called when a GO first enters the scene. At this point, if the GO is disabled, should its components be cached? The current code caches them unconditionally. With the enabled check, disabled GOs added to a scene won't have cached components. This is correct: they're disabled, so they shouldn't be in lookups.
- `GameObject.setParent()` (GameObject.java:111) — called during reparenting across scenes. Same reasoning: if a child is disabled, don't cache it in the new scene.

Both callers benefit from the enabled check. **Option B is the right choice.** The concern about "other callers" is resolved — there are exactly two, and both want the same behavior.

**Revised recommendation:** Option B. Add `if (!child.isEnabled()) continue;` to the recursion in `registerCachedComponents()`. Also apply the same check to the root GO itself if called on a disabled object:

```java
public void registerCachedComponents(GameObject gameObject) {
    if (!gameObject.isEnabled()) return;  // Skip disabled root too

    // ... register this GO's components ...

    for (GameObject child : gameObject.getChildren()) {
        registerCachedComponents(child);  // Recursion handles enabled check via line above
    }
}
```

This way the enabled check is at the top of the method, not just in the recursion. Cleaner, one check point, and `setParent()` + `addGameObject()` both benefit automatically.

**But wait — is this safe for `addGameObject()`?** If a disabled GO is added to a scene, its components won't be cached. When it's later enabled, `setEnabled(true)` must call `registerCachedComponents(this)` to populate the cache. The plan already proposes this. So the flow works:
1. `addGameObject(disabledGO)` → `registerCachedComponents` skips (disabled) → cache empty. Correct.
2. Later: `disabledGO.setEnabled(true)` → `registerCachedComponents(this)` → cache populated. Correct.

Confirmed safe.

---

### 2. Missing File: `EditorScene.findEntityAt()` Doesn't Skip Disabled Entities

**Location:** Design Decision #1 says disabled entities are "not selectable via scene view click." No file listed to implement this.

**Evidence:** `EditorScene.findEntityAt()` (EditorScene.java:373-381):

```java
public EditorGameObject findEntityAt(float worldX, float worldY) {
    for (int i = entities.size() - 1; i >= 0; i--) {
        EditorGameObject entity = entities.get(i);
        if (isPointInsideEntity(entity, worldX, worldY)) {
            return entity; // No enabled check
        }
    }
    return null;
}
```

**Result:** Clicking where a disabled entity used to be (it's hidden) would still select it — invisible selection.

**Original proposed fix:** Add `if (!entity.isEnabled()) continue;` directly to `findEntityAt()`.

#### Self-review: The fix would break MoveTool free-drag

`findEntityAt()` is called by 8 different sites across 4 scene tools:
- `SelectionTool.onMouseDown()` — click-to-select
- `MoveTool.onMouseDown()` — click-to-select AND free-drag initiation (line 110)
- `RotateTool.onMouseDown()` — click-to-select AND interaction check
- `ScaleTool.onMouseDown()` — click-to-select AND interaction check
- All 4 tools' `renderOverlay()` — hover highlighting

`MoveTool.onMouseDown()` (MoveTool.java:110-120) uses `findEntityAt()` to check if the clicked entity matches the current selection before starting a free drag:
```java
EditorGameObject clickedEntity = scene.findEntityAt(worldX, worldY);
if (clickedEntity == selected) {
    startDrag(DragAxis.FREE);  // Only reaches here if findEntityAt returned the entity
}
```

If the entity is disabled and `findEntityAt()` skips it, `clickedEntity` is null, and free drag never starts. But gizmo-axis drag (lines 102-105) uses `hoveredAxis`, not `findEntityAt()`, so it still works... except the plan also hides gizmos for disabled entities (Phase 5). So there's no way to transform a disabled entity in the scene view at all.

**Is this actually a problem?** Disabled entities are invisible. You can't see them, you can't see their gizmos. You'd have to blindly click where you remember the entity was. The plan says to use the hierarchy to find disabled entities and the inspector to edit their transform. So the "break" is consistent with the design intent.

**Revised recommendation:** Add a separate method rather than modifying `findEntityAt()`, to be explicit about the intent and avoid surprising other callers:

```java
public EditorGameObject findEnabledEntityAt(float worldX, float worldY) {
    for (int i = entities.size() - 1; i >= 0; i--) {
        EditorGameObject entity = entities.get(i);
        if (!entity.isEnabled()) continue;
        if (isPointInsideEntity(entity, worldX, worldY)) {
            return entity;
        }
    }
    return null;
}
```

Then update SelectionTool and the selection paths in MoveTool/RotateTool/ScaleTool to use `findEnabledEntityAt()`, while keeping `findEntityAt()` unchanged for any future code that needs to find entities regardless of enabled state.

**Counterpoint:** This is arguably over-engineering. Since disabled entities are invisible and gizmos are hidden, `findEntityAt()` can never find a disabled entity anyway — it relies on `isPointInsideEntity()` which checks the sprite bounds, and `isRenderVisible()` returning false means there's nothing to hit. Wait — `isPointInsideEntity()` (EditorScene.java:387-419) checks `entity.getCurrentSize()` and position. It does NOT check `isRenderVisible()`. So it CAN match an invisible disabled entity that still has a size and position. The fix is needed.

**Final recommendation:** Either approach works (modify `findEntityAt` or add `findEnabledEntityAt`). The separate method is cleaner but the direct modification is fine too — the "break" of disabled-entity drag is not actually a break because the user can't see or interact with disabled entities by design. Go with whichever the implementor finds cleaner.

---

### 3. Context Menu Uses Hierarchical `isEnabled()` — Wrong Toggle Behavior

**Location:** Phase 4 context menu code.

**Evidence:** The proposed code uses `entity.isEnabled()` which is hierarchical (chains through parent):

```java
String enableLabel = entity.isEnabled()
    ? MaterialIcons.VisibilityOff + " Disable"
    : MaterialIcons.Visibility + " Enable";
```

But `EditorGameObject.isEnabled()` (Phase 4 proposal) chains through parent:

```java
public boolean isEnabled() {
    if (!enabled) return false;
    if (parent != null) return parent.isEnabled();
    return true;
}
```

**Scenario:**
- Parent is disabled -> Child's own `enabled=true` -> `child.isEnabled()` returns `false`
- Context menu shows "Enable" for the child
- User clicks "Enable" -> `ToggleEntityEnabledCommand` toggles `enabled` from `true` to `false`
- The child is now DOUBLY disabled (own flag + parent), opposite of user intent

**Proposed fix:** Use `entity.isOwnEnabled()` instead of `entity.isEnabled()` in the context menu and toggle command.

#### Self-review: Technically correct, UX is debatable

Using `isOwnEnabled()` fixes the data-integrity issue — the toggle always flips the entity's own flag, matching the label.

But consider the user experience: a child whose parent is disabled appears grayed-out in the hierarchy. The user right-clicks it, sees "Disable". They think "it already looks disabled, why does the menu say Disable?" They might expect "Enable" because the entity *appears* disabled.

Alternative UX approaches:
1. **Disable the menu item** when the entity is effectively disabled due to parent (grey out the toggle, add tooltip "Parent is disabled")
2. **Show both states**: "Disable (entity is enabled, parent is disabled)" — too verbose
3. **Keep `isOwnEnabled()`** and accept the mild confusion — the checkbox in the inspector would show the same own-state, so it's consistent

**Verdict:** `isOwnEnabled()` is correct. The inspector checkbox (Phase 3) will also reflect the own state, making the context menu consistent with it. The hierarchy graying already communicates that the entity is effectively disabled. Users who understand the parent-child relationship won't be confused. This is the standard approach (Unity does the same: checkbox reflects own state, graying reflects effective state).

---

## Moderate Issues

### 4. `Component.enabled` — Public Field vs Getter

**Location:** Phase 1 — "Make `enabled` field `public`".

**Proposed fix:** Add `isOwnEnabled()` getter instead of making the field public.

#### Self-review: Correct and consistent

The only place that needs the raw field is the serializer (write path). The read path already uses `setEnabled()`. Adding `isOwnEnabled()` to `Component` mirrors the same method on `EditorGameObject` (from issue #3), creating a consistent API across both. No callers need write access to the field itself — `setEnabled()` handles that. Confirmed this is the better approach.

### 5. `EditorSceneAdapter` — Verify `getEntities()` Returns Flat or Hierarchical List

**Location:** Phase 5 verification task.

#### Self-review: Dropped — low value

This was flagged as "verify during implementation." It's not a concrete issue, just a question. The implementor will see immediately whether `getEntities()` is flat when they test the feature. Not worth keeping in the review.

### 6. GridMovement `onEnable()` — Stale Grid Position

**Location:** Phase 7 — GridMovement `onEnable()`/`onDisable()`.

**Original fix:** Recalculate `gridX`/`gridY` from transform and call `snapToGrid()` in `onEnable()`.

#### Self-review: `snapToGrid()` may be unwanted

`snapToGrid()` physically repositions the entity's transform to the nearest grid cell. If game code deliberately placed the entity at a non-grid-aligned position while GridMovement was disabled (e.g., a cutscene moving a character to a precise pixel position), re-enabling GridMovement would snap it to the nearest tile — potentially visible as a position jump.

Two options:
1. **Recalculate + snap**: safe for grid-based games where off-grid positions are always errors
2. **Recalculate only, no snap**: derives which tile the entity is on for collision purposes, but doesn't move the entity

For a grid-movement system where entities are expected to always be tile-aligned, option 1 is probably correct. But this is a game design decision, not a plan bug. The plan should document the choice either way.

**Revised recommendation:** Recalculate `gridX`/`gridY` from transform — yes, always. Call `snapToGrid()` — document as intentional (grid entities must be tile-aligned). If the game later needs non-snapping behavior, that's a future feature, not a current bug.

### 7. Phase Dependency: Duplication Verification

No change. Phases are sequential. Just a documentation note.

---

## Minor Issues

### 8. "Unchanged" Table Contradictions

No change. Still confusing, still worth fixing.

### 9. Deferred Start Window

No change. Still harmless, still worth a comment.

### 10. Copy/Paste

#### Self-review: Dropped — not implemented

Copy/paste is not implemented (EditorShortcutHandlersImpl.java:172-187 shows TODO stubs for cut/copy/paste). Only duplicate exists, and it goes through `toData()`/`fromData()`. Not a concern for this plan.

### 11. `GameObjectData.active` — Already in JSON?

No change. Worth verifying during implementation.

---

## What's Solid in the Plan

- The audit of existing `isEnabled()` call sites is thorough and accurate
- `triggerEnable()`/`triggerDisable()` guard analysis is correct — the short-circuiting logic works
- Component serialization approach (write only when `false`) is clean and backwards-compatible
- `RuntimeSceneLoader` verification is correct — no changes needed
- The hierarchical propagation logic (stopping at disabled children) is correct for callbacks
- The `onTransformChanged()` claim is verified: GameObject.java:316 checks `component.isEnabled()` before calling it
- Backwards compatibility story is well thought out
