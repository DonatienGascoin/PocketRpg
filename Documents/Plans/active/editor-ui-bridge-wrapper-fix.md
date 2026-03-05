# Plan: Fix EditorUIBridge Wrapper Stale Cache Problem

## Problem Statement

The `EditorUIBridge` creates temporary `GameObject` wrappers so the `UIRenderer` can traverse editor UI hierarchies. When the bridge rebuilds (due to hierarchy/component/enabled changes), it creates **fresh wrapper GOs** and moves components onto them via `setOwner()`. But components that cache child/parent wrapper references (like `UIScrollView.cachedScrollbarGo`) never learn about the new wrappers — their caches go stale, silently operating on orphaned GOs.

This causes real bugs: UIScrollView's scrollbar visibility toggle disables an orphaned wrapper instead of the one being rendered.

## Root Cause

`Component.setOwner()` notifies the component that its owner changed, but:
- Only `UIComponent` overrides it (invalidates canvas + transform caches)
- `UIScrollView` and `UIScrollbar` don't override it — their hierarchy caches are never invalidated
- The bridge has no post-rebuild notification mechanism

## Affected Components

| Component | Cached References | Stale After Rebuild? |
|-----------|------------------|---------------------|
| `UIComponent` | `cachedCanvas`, `cachedTransform` | No (invalidated in `setOwner`) |
| `UIScrollView` | `cachedViewport`, `cachedContent`, `cachedScrollbarGo` | **YES** |
| `UIScrollbar` | `cachedScrollView` | **YES** |
| `UIText` | `cachedFont`, `cachedFontKey` | No (not GO references) |
| `UITransform` | `cachedWorldRotation`, `cachedWorldScale` | No (not GO references) |

## Current Architecture

```
EditorScene (flat list of EditorGameObjects)
    |
    v
EditorUIBridge.rebuildWrappers()
    |-- Pass 1: create wrapper GOs, move components via setOwner(wrapper)
    |-- Pass 2: link parent-child via wrapper.setParent(parentWrapper)
    |-- Pass 3: sort children by order
    |
    v
UIRenderer traverses wrapper GO hierarchy
    |-- renderCanvasSubtree checks root.isEnabled()
    |-- calls updateMetrics(), updateHandle(), applyLayout() on components
    |-- components use cached references to find children/parents
```

Two independent bridge instances exist:
- `UIDesignerPanel.uiBridge` — UI designer preview
- `GameViewPanel.uiBridge` — game view preview

## Plan

### Phase 1: Proper Cache Invalidation via setOwner Hook

**Goal**: When the bridge moves a component to a new wrapper, all hierarchy caches on that component are automatically invalidated.

**Approach**: Override `setOwner()` in UIScrollView and UIScrollbar to invalidate their hierarchy caches. This is the same pattern UIComponent already uses for canvas/transform caches.

#### UIScrollView.java

Add `setOwner` override:

```java
@Override
public void setOwner(IGameObject owner) {
    super.setOwner(owner);
    hierarchyCacheDirty = true;
}
```

Remove the `invalidateHierarchyCache()` call from `updateMetrics()` (the per-frame workaround added earlier). The `setOwner` hook makes it unnecessary — caches are only invalidated when the owner actually changes.

#### UIScrollbar.java

Add `setOwner` override:

```java
@Override
public void setOwner(IGameObject owner) {
    super.setOwner(owner);
    scrollViewCacheDirty = true;
}
```

#### Verification
- `mvn test -Dtest=UIScrollViewTest,UIScrollbarTest` — all tests pass
- Manual: select ScrollView in editor, change to NEVER — scrollbar hides
- Manual: change to AUTO with no overflow — scrollbar hides
- Manual: change to AUTO with overflow — scrollbar shows

### Phase 2: Bridge Post-Rebuild Notification

**Goal**: Give all components a chance to react after a full wrapper rebuild, not just when their own owner changes. This covers edge cases where a component's cached reference points to a sibling/ancestor wrapper that was rebuilt (but the component's own owner didn't change — e.g., if the bridge reuses some wrappers in the future).

**Approach**: After `rebuildWrappers()` completes, iterate all wrapper components and call a new `onHierarchyRebuilt()` hook.

#### Component.java

Add empty hook method:

```java
/**
 * Called by EditorUIBridge after wrapper hierarchy is rebuilt.
 * Override to invalidate any cached hierarchy references.
 */
public void onHierarchyRebuilt() {
    // Default: no-op
}
```

#### UIScrollView.java

Override to invalidate:

```java
@Override
public void onHierarchyRebuilt() {
    hierarchyCacheDirty = true;
}
```

#### UIScrollbar.java

Override to invalidate:

```java
@Override
public void onHierarchyRebuilt() {
    scrollViewCacheDirty = true;
}
```

#### UIComponent.java

Override to invalidate canvas/transform (belt-and-suspenders with setOwner):

```java
@Override
public void onHierarchyRebuilt() {
    canvasCacheDirty = true;
    transformCacheDirty = true;
}
```

#### EditorUIBridge.java

Call the hook after rebuild completes (end of `rebuildWrappers()`, after all 3 passes):

```java
// Fourth pass: notify components that hierarchy was rebuilt
for (GameObject wrapper : wrapperCache.values()) {
    for (Component comp : wrapper.getAllComponents()) {
        comp.onHierarchyRebuilt();
    }
}
```

#### Verification
- Same manual tests as Phase 1
- Add unit test: create a wrapper, populate caches, call `onHierarchyRebuilt()`, verify caches are dirty

### Phase 3: Reuse Wrapper GOs Instead of Recreating

**Goal**: Eliminate the stale-cache problem at the source. Instead of destroying all wrappers and creating new ones on every rebuild, reuse existing wrapper GOs when possible. Components keep their cached references because the underlying GOs don't change.

**Approach**: Change `rebuildWrappers()` to an incremental update model.

#### Current flow (destructive rebuild)

```
rebuildWrappers():
    wrapperCache.clear()           // destroy all
    for entity: create new wrapper // recreate all
    for entity: link parents       // relink all
    for wrapper: sort children     // resort all
```

#### New flow (incremental update)

```
updateWrappers():
    // 1. Collect current entity IDs with UI components
    Set<String> currentIds = ...

    // 2. Remove wrappers for entities that no longer exist or lost UI components
    wrapperCache.keys().removeIf(id -> !currentIds.contains(id))

    // 3. Create wrappers only for NEW entities (not in cache)
    for entity in scene:
        if !wrapperCache.containsKey(entity.id):
            wrapper = createWrapperGameObject(entity)
            wrapperCache.put(entity.id, wrapper)
        else:
            syncWrapperComponents(wrapperCache.get(entity.id), entity)

    // 4. Update parent-child relationships (only if changed)
    for entity in scene:
        wrapper = wrapperCache.get(entity.id)
        expectedParent = wrapperCache.get(entity.parentId)
        if wrapper.getParent() != expectedParent:
            wrapper.setParent(expectedParent)

    // 5. Sort children (only if order changed)
    ...

    // 6. Rebuild canvas list
    ...
```

#### syncWrapperComponents(wrapper, entity)

Handles the case where an entity's components changed but the entity itself still exists:

```java
private void syncWrapperComponents(GameObject wrapper, EditorGameObject entity) {
    // Check if component list changed
    // If yes: clear wrapper components, re-add from entity (same as createWrapperGameObject but on existing GO)
    // If no: just ensure setOwner points to this wrapper (should already)
}
```

#### Key benefit

Wrapper GOs persist across rebuilds. Components that cached `wrapper.getChildren().get(0)` still hold valid references because the child wrapper is the same object. No cache invalidation needed for the common case (field edits, enable/disable toggles).

#### When wrappers DO get recreated

Only when:
- Entity is added to scene (new wrapper)
- Entity is removed from scene (wrapper removed)
- Entity's components change (wrapper's component list synced)

These are infrequent operations where cache invalidation is expected.

#### Verification
- All existing tests pass
- Performance: measure frame time with 50+ UI elements, verify no regression
- Manual: add/remove entities while UIDesigner is open — no stale rendering
- Manual: scrollbar visibility toggle works across entity add/remove cycles

#### Risk

This is the most complex phase. The current destructive rebuild is simple and correct (after Phase 1/2 fixes). Phase 3 optimizes but adds complexity. Consider deferring until the bridge causes measurable performance issues.

## File Summary

| Phase | File | Change |
|-------|------|--------|
| 1 | `UIScrollView.java` | Override `setOwner()` to invalidate hierarchy cache, remove per-frame workaround |
| 1 | `UIScrollbar.java` | Override `setOwner()` to invalidate scroll view cache |
| 2 | `Component.java` | Add `onHierarchyRebuilt()` hook (empty default) |
| 2 | `UIComponent.java` | Override `onHierarchyRebuilt()` |
| 2 | `UIScrollView.java` | Override `onHierarchyRebuilt()` |
| 2 | `UIScrollbar.java` | Override `onHierarchyRebuilt()` |
| 2 | `EditorUIBridge.java` | Call `onHierarchyRebuilt()` after rebuild |
| 3 | `EditorUIBridge.java` | Rewrite `rebuildWrappers()` → `updateWrappers()` with incremental model |

## Ordering

Phase 1 is the minimum viable fix — solves the immediate scrollbar bug and any similar future bugs.

Phase 2 is defense-in-depth — catches cases Phase 1 misses (sibling/ancestor wrapper changes).

Phase 3 is an optimization — eliminates the root cause but is higher risk. Can be deferred.

Recommend implementing Phase 1 + 2 together, Phase 3 as a separate follow-up.
