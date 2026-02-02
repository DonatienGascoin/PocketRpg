# Option 2: Scene Reload with Registry Re-scan

## Overview

**Problem:** When component classes are modified (new fields, new components, annotation changes), the editor's static registries hold stale cached metadata. The inspector doesn't reflect new fields, new component classes aren't discoverable, and custom inspectors aren't picked up. A full restart is required.

**Approach:** Add a "Reload Scene" command to the editor that:
1. Publishes a `RegistriesRefreshRequestEvent` so all registries self-reinitialize (event-driven, OCP-compliant)
2. Serializes the current scene to SceneData (in-memory deep copy)
3. Rebuilds the scene from the snapshot using the updated class definitions
4. Swaps scenes only on success (destroy-after-swap for data safety)
5. Restores editor state from a captured `EditorStateSnapshot`

This leverages the existing serialization pipeline — the same code path used by play mode snapshots and scene saving.

---

## Design Decisions

### Event-Driven Registry Refresh (addresses OCP + DIP)

Rather than having `reloadScene()` hard-code calls to each registry's `reinitialize()`, the reload publishes a `RegistriesRefreshRequestEvent` on the `EditorEventBus`. Each registry subscribes to this event and reinitializes itself. This means:
- Adding a new registry requires zero changes to the reload flow (Open/Closed)
- `EditorSceneController` has no imports from `ComponentRegistry`, `PostEffectRegistry`, etc. (Dependency Inversion)
- Consistent with the existing event-driven architecture (`SceneWillChangeEvent`, `SceneChangedEvent`, `PlayModeStartedEvent`, etc.)

### EditorStateSnapshot Record (addresses SRP + cohesion)

State capture and restore is encapsulated in an `EditorStateSnapshot` record rather than scattered as local variables. This:
- Makes the capture/restore logic testable in isolation
- Makes it easy to add new state fields later (active tool, scroll positions, etc.)
- Keeps `reloadScene()` focused on orchestration

### Swap-Then-Destroy (addresses error recovery)

The old scene is destroyed **after** the new scene is successfully built, not before. If deserialization fails, the old scene remains intact and the user loses nothing. This is safer than the current pattern in `openScene()` (which destroys first), but `openScene()` loads from disk (recoverable), whereas reload may be working with an unsaved scene.

---

## Registries That Need Refreshing

Four places in the codebase use the Reflections library and cache results at startup. Each will subscribe to `RegistriesRefreshRequestEvent` to self-reinitialize.

### 1. ComponentRegistry (`serialization/ComponentRegistry.java`)
- **Scans for:** All `Component` subclasses in `com.pocket.rpg`
- **Caches:** `bySimpleName` (Map), `byFullName` (Map), `allComponents` (List), `categories` (Map)
- **Guard:** `static boolean initialized` — `initialize()` returns early if true (line 37)
- **Impact:** Component instantiation, inspector field discovery, "Add Component" menu, serialization metadata

### 2. PostEffectRegistry (`rendering/postfx/PostEffectRegistry.java`)
- **Scans for:** All `PostEffect` subclasses in `com.pocket.rpg.rendering.postfx`
- **Caches:** `effects` (List, final), `bySimpleName` (Map, final) — `clear()` works on final collections
- **Guard:** `static boolean initialized` — `initialize()` returns early if true (line 27)
- **Impact:** Post-effect dropdown in rendering pipeline configuration

### 3. CustomComponentEditorRegistry (`editor/ui/inspectors/CustomComponentEditorRegistry.java`)
- **Scans for:** All classes annotated with `@InspectorFor` in `com.pocket.rpg`
- **Caches:** `editors` (Map of component class name → inspector instance)
- **Guard:** None (no `initialized` flag), only called once via `initBuiltInEditors()` (line 112)
- **Note:** Already has a `clear()` method (line 172) that calls `unbindCurrent()` and `editors.clear()`
- **Impact:** Custom inspector panels for specific component types

### 4. AssetManager (`resources/AssetManager.java`) — SKIP
- **Scans for:** All `AssetLoader` implementations in `com.pocket.rpg`
- **Caches:** `loaders` (Map), `extensionMap` (Map) — **instance** fields, not static
- **Assessment:** AssetLoader implementations are engine infrastructure, not user-authored components. Unlikely to change during a development session. Skip unless a use case arises.

### 5. TilesetRegistry (`editor/tileset/TilesetRegistry.java`) — SKIP
- **Does NOT use Reflections.** Uses `Assets.scanByType(Sprite.class)` to find sprite files.
- **Caches:** tilesets (Map) and pathToName (Map). Already has a `reload()` method.
- **Assessment:** Caches asset files on disk, not Java class metadata. Not affected by component class changes. If tileset PNGs change on disk, the user can call `TilesetRegistry.getInstance().reload()` separately — this is a different concern from class hot-reload.

### Additional Static Caches to Clear

These are not Reflections-based but hold scene-derived data that becomes stale:

- **`RuntimeGameObjectAdapter.adapterCache`** (`editor/scene/RuntimeGameObjectAdapter.java:23`) — `WeakHashMap`. Has `clearCache()` (line 42). Self-cleaning via weak refs, but explicit clear is safer.
- **`SceneUtils.spawnPointCache`** (`editor/utils/SceneUtils.java:25`) — `HashMap`. Has `clearCache()` (line 94).

These will also subscribe to `RegistriesRefreshRequestEvent` to clear themselves.

---

## Pre-existing Bug: EditorScene.destroy() Does Not Destroy Entities

### The Problem

`EditorScene.destroy()` (line 766) delegates to `clear()` (line 751):

```java
// EditorScene.java lines 751-764
public void clear() {
    for (TilemapLayer layer : new ArrayList<>(layers)) {
        layer.getGameObject().destroy();   // ← Tilemap layers: destroy() IS called
    }
    layers.clear();
    entities.clear();                       // ← Entities: just clears the list, NO destroy()
    selectedEntities.clear();
    // ...
}
```

`EditorGameObject` has **no `destroy()` method** at all. Component instances held by entities are dropped without `onDestroy()` being called.

### Impact on Reload

**Not a blocker.** `toSceneData()` calls `entity.toData()` which **deep-copies** all components via `cloneComponent()` (EditorGameObject.java line 907). The snapshot is independent. Editor components don't allocate GL handles — rendering is done by EditorSceneRenderer.

### Recommendation

Document as a separate issue. The reload flow works correctly because `toSceneData()` deep-copies before any cleanup occurs.

---

## Phase 1: Event and State Infrastructure

### 1a. Create `RegistriesRefreshRequestEvent`

**File:** `src/main/java/com/pocket/rpg/editor/events/RegistriesRefreshRequestEvent.java` — **NEW**

```java
package com.pocket.rpg.editor.events;

/**
 * Published when registries should re-scan the classpath.
 * <p>
 * Subscribers (ComponentRegistry, PostEffectRegistry, CustomComponentEditorRegistry,
 * and any future scannable registries) should clear their caches and re-scan.
 * <p>
 * This event is published by the scene reload flow, before the scene is rebuilt.
 * Subscribers must NOT access or modify the current scene.
 */
public record RegistriesRefreshRequestEvent() implements EditorEvent {}
```

### 1b. Create `EditorStateSnapshot`

**File:** `src/main/java/com/pocket/rpg/editor/EditorStateSnapshot.java` — **NEW**

```java
package com.pocket.rpg.editor;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of editor UI state that should be preserved across
 * operations like scene reload.
 * <p>
 * Captures state that is NOT part of the scene data itself (camera position,
 * selection, dirty flag) and can restore it to a new scene instance.
 */
public record EditorStateSnapshot(
    String scenePath,
    boolean dirty,
    Vector3f cameraPosition,
    float cameraZoom,
    List<String> selectedEntityIds
) {
    /**
     * Captures the current editor state.
     */
    public static EditorStateSnapshot capture(EditorContext context) {
        EditorScene scene = context.getCurrentScene();
        EditorCamera camera = context.getCamera();
        EditorSelectionManager selection = context.getSelectionManager();

        List<String> selectedIds = selection.getSelectedEntities().stream()
            .map(EditorGameObject::getId)
            .toList();

        return new EditorStateSnapshot(
            scene.getFilePath(),
            scene.isDirty(),
            camera.getPosition(),   // returns copy (Vector3f)
            camera.getZoom(),
            selectedIds
        );
    }

    /**
     * Restores editor state to a newly loaded scene.
     * Must be called AFTER context.setCurrentScene(newScene).
     */
    public void restore(EditorContext context, EditorScene newScene) {
        // Restore camera
        EditorCamera camera = context.getCamera();
        camera.setPosition(cameraPosition);
        camera.setZoom(cameraZoom);

        // Restore selection — use selectEntities() with all at once.
        // selectEntity() replaces the selection each call; calling it in a
        // loop would only keep the last entity.
        EditorSelectionManager selection = context.getSelectionManager();
        Set<EditorGameObject> entitiesToSelect = new LinkedHashSet<>();
        for (String id : selectedEntityIds) {
            EditorGameObject entity = newScene.getEntityById(id);
            if (entity != null) {
                entitiesToSelect.add(entity);
            }
        }
        if (!entitiesToSelect.isEmpty()) {
            selection.selectEntities(entitiesToSelect);
        }

        // Restore dirty flag.
        // fromSceneData() calls clearDirty(), so if it was dirty before
        // we re-mark it. If it was clean, it stays clean.
        if (dirty) {
            newScene.markDirty();
        }
    }
}
```

### 1c. Make Registries Re-scannable and Event-Subscribed

Each registry gets a `reinitialize()` method and subscribes to `RegistriesRefreshRequestEvent`.

#### ComponentRegistry

**File:** `src/main/java/com/pocket/rpg/serialization/ComponentRegistry.java`

- [ ] Add `reinitialize()` method
- [ ] Add event subscription in `initialize()` (called once at startup, safe to subscribe there)

```java
/**
 * Clears all cached component metadata and re-scans the classpath.
 * <p>
 * Must only be called from the main thread.
 * <p>
 * On a standard JVM (without DCEVM), this only discovers NEW classes.
 * Modified classes remain loaded with their old definitions. With DCEVM,
 * structural changes are applied by the JVM before this re-scan.
 */
public static void reinitialize() {
    bySimpleName.clear();
    byFullName.clear();
    allComponents.clear();
    categories.clear();
    initialized = false;
    initialize();
    System.out.println("ComponentRegistry reinitialized: " + allComponents.size() + " components");
}

// At the end of initialize(), after `initialized = true`:
EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class,
    event -> reinitialize());
```

**Thread safety note:** `reinitialize()` is documented as main-thread-only. The editor is single-threaded for all UI/registry operations. The `initialized` flag is a plain `boolean`, which is sufficient given single-threaded access. If background threads ever read registries, this should become `volatile` — but that is a future concern.

#### PostEffectRegistry

**File:** `src/main/java/com/pocket/rpg/rendering/postfx/PostEffectRegistry.java`

- [ ] Add `reinitialize()` method
- [ ] Add event subscription in `initialize()`

```java
public static void reinitialize() {
    effects.clear();       // final ArrayList — clear() works
    bySimpleName.clear();  // final HashMap — same
    initialized = false;
    initialize();
}

// At end of initialize():
EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class,
    event -> reinitialize());
```

#### CustomComponentEditorRegistry

**File:** `src/main/java/com/pocket/rpg/editor/ui/inspectors/CustomComponentEditorRegistry.java`

- [ ] Add `reinitialize()` method (calls existing private `scanAndRegisterInspectors()`)
- [ ] Add event subscription in `initBuiltInEditors()`

```java
public static void reinitialize() {
    clear();  // unbindCurrent() + editors.clear()
    scanAndRegisterInspectors();
}

// At end of initBuiltInEditors():
EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class,
    event -> reinitialize());
```

#### Auxiliary Caches

- [ ] Subscribe `RuntimeGameObjectAdapter.clearCache()` in its class or in `EditorApplication.init()`
- [ ] Subscribe `SceneUtils.clearCache()` similarly

```java
// In EditorApplication.init(), after registries are initialized:
EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class,
    event -> {
        RuntimeGameObjectAdapter.clearCache();
        SceneUtils.clearCache();
    });
```

> **Why subscribe from EditorApplication for these?** These utility classes are not registries and don't have an `initialize()` lifecycle method. Subscribing from the wiring hub (`EditorApplication`) is appropriate and avoids adding event bus awareness to simple utility classes.

### Files to Create/Modify

| File | Change |
|------|--------|
| `editor/events/RegistriesRefreshRequestEvent.java` | **NEW** — Event record |
| `editor/EditorStateSnapshot.java` | **NEW** — State capture/restore record |
| `serialization/ComponentRegistry.java` | Add `reinitialize()`, subscribe to event |
| `rendering/postfx/PostEffectRegistry.java` | Add `reinitialize()`, subscribe to event |
| `editor/ui/inspectors/CustomComponentEditorRegistry.java` | Add `reinitialize()`, subscribe to event |
| `editor/EditorApplication.java` | Subscribe auxiliary cache clearing |

---

## Phase 2: Scene Reload Logic

### Current Serialization Path

The editor already has the full round-trip pipeline:
- `EditorSceneSerializer.toSceneData(EditorScene)` → produces `SceneData` (line 30)
- `EditorSceneSerializer.fromSceneData(SceneData, String filePath)` → produces `EditorScene` (line 66)

### Snapshot Safety

`EditorGameObject.toData()` (line 883) **deep-copies** all component instances via `cloneComponent()` for scratch entities and `copyOverrides()` for prefab instances. The snapshot is fully independent.

### Play Mode Guard

`reloadScene()` can be called from both the shortcut system (which suppresses during play mode) and the menu bar (which does not). The guard must be inside `reloadScene()` itself.

`EditorContext` does not expose `PlayModeController`. Rather than expanding `EditorContext` (which is a shared state container, not a service locator for controllers), pass `PlayModeController` to `EditorSceneController` via setter — following the existing pattern in `EditorShortcutHandlersImpl` (which uses `@Setter private PlayModeController playModeController`).

### Required Changes

- [ ] **Add `reloadScene()` to `EditorSceneController`**

```java
@Setter
private PlayModeController playModeController;

/**
 * Reloads the current scene by refreshing all registries and
 * rebuilding the scene from its serialized state.
 * <p>
 * Preserves camera position, zoom, selection, and dirty flag.
 * Uses swap-then-destroy: the old scene is only destroyed after the
 * new scene is successfully built from the snapshot.
 */
public void reloadScene() {
    EditorScene currentScene = context.getCurrentScene();
    if (currentScene == null) {
        showMessage("No scene to reload");
        return;
    }

    // Guard: do nothing during play mode.
    // Shortcuts are suppressed during play mode, but menu bar bypasses shortcuts.
    if (playModeController != null && playModeController.isActive()) {
        showMessage("Cannot reload during play mode");
        return;
    }

    // 1. Capture editor state
    EditorStateSnapshot stateSnapshot = EditorStateSnapshot.capture(context);

    // 2. Snapshot scene to SceneData (deep-copies components)
    SceneData sceneSnapshot = EditorSceneSerializer.toSceneData(currentScene);

    // 3. Publish event so all registries re-scan (OCP: no hard-coded list)
    EditorEventBus.get().publish(new RegistriesRefreshRequestEvent());

    // 4. Rebuild scene from snapshot
    EditorScene newScene;
    try {
        newScene = EditorSceneSerializer.fromSceneData(sceneSnapshot, stateSnapshot.scenePath());
    } catch (Exception e) {
        System.err.println("Scene reload failed: " + e.getMessage());
        e.printStackTrace();
        showMessage("Reload failed: " + e.getMessage());
        // Old scene is still alive — abort reload, no data loss.
        return;
    }

    // 5. Success — now safe to tear down the old scene.
    //    Publish SceneWillChangeEvent for subscribers (play mode, etc.)
    EditorEventBus.get().publish(new SceneWillChangeEvent());
    currentScene.destroy();
    UndoManager.getInstance().clear();

    // 6. Clear selection before swapping scene.
    //    context.setCurrentScene() does NOT clear selection state (it only
    //    assigns the scene field via Lombok setter). Without this, the
    //    selection manager would hold stale entity references.
    context.getSelectionManager().clearSelection();

    // 7. Swap scene on context (notifies all panels via onSceneChanged)
    context.setCurrentScene(newScene);

    // 8. Restore editor state (camera, selection, dirty flag)
    stateSnapshot.restore(context, newScene);

    showMessage("Scene reloaded — registries refreshed");
}
```

**Key design choices in this method:**
- **Swap-then-destroy** (step 5): `fromSceneData()` must succeed before the old scene is destroyed. If it throws, we `return` and the old scene remains intact. No data loss.
- **Event-driven registry refresh** (step 3): A single `publish()` call replaces hard-coded calls to each registry. Adding future registries requires zero changes here.
- **`EditorStateSnapshot`** (steps 1, 8): State capture/restore is encapsulated. Adding new state fields (active tool, etc.) only changes the record, not `reloadScene()`.
- **`SceneWillChangeEvent` after snapshot** (step 5): Published after snapshot succeeds but before destroy — consistent with the event's contract ("scene is about to change"). The snapshot happens earlier (step 2) to capture clean state, but the event fires in the same relative position to destroy as in `openScene()`.

### Files to Modify

| File | Change |
|------|--------|
| `editor/EditorSceneController.java` | Add `@Setter PlayModeController`, add `reloadScene()` |

---

## Phase 3: Wire Up Shortcut and Menu

### Shortcut ID Convention

Existing IDs use the pattern `editor.{category}.{action}` with camelCase (e.g., `editor.file.save`, `editor.file.saveAs`, `editor.play.toggle`). The reload action belongs in the `file` category.

### Required Changes

- [ ] **Register shortcut action in `EditorShortcuts.registerDefaults()`**

```java
public static final String FILE_RELOAD = "editor.file.reloadScene";

// In registerDefaults(), FILE section:
registry.register(ShortcutAction.builder()
    .id(FILE_RELOAD)
    .displayName("Reload Scene")
    .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.R))
    .scope(ShortcutScope.GLOBAL)
    .build());
```

> `ImGuiKey.R` is already used by `TOOL_SCALE` as plain `key(ImGuiKey.R)`. `ctrlShift(ImGuiKey.R)` does not conflict — the shortcut system sorts by modifier count.

- [ ] **Add `onReloadScene()` to `EditorShortcutHandlers` interface**

**File:** `src/main/java/com/pocket/rpg/editor/shortcut/EditorShortcutHandlers.java`

```java
// In the FILE section (after onOpenConfiguration):
void onReloadScene();
```

- [ ] **Implement in `EditorShortcutHandlersImpl`**

```java
@Setter
private EditorSceneController sceneController;

@Override
public void onReloadScene() {
    if (sceneController != null) {
        sceneController.reloadScene();
    }
}
```

- [ ] **Bind handler in `EditorShortcuts.bindHandlers()`**

```java
registry.setHandler(FILE_RELOAD, handlers::onReloadScene);
```

- [ ] **Wire in `EditorApplication.createControllers()`**

After `sceneController` is created (line 251) and `handlers` is created (line 307):
```java
handlers.setSceneController(sceneController);
sceneController.setPlayModeController(playModeController);
```

- [ ] **Add menu item in `EditorMenuBar`**

**File:** `src/main/java/com/pocket/rpg/editor/ui/EditorMenuBar.java`

```java
// Field (following existing setOn* pattern at lines 26-32):
private Runnable onReloadScene;

// Setter (following pattern at lines 631-666):
public void setOnReloadScene(Runnable callback) {
    this.onReloadScene = callback;
}

// In File menu rendering, after Save As:
if (ImGui.menuItem("Reload Scene", "Ctrl+Shift+R", false, !playModeActive)) {
    if (onReloadScene != null) onReloadScene.run();
}
```

Wire in `EditorApplication.createControllers()`:
```java
uiController.getMenuBar().setOnReloadScene(sceneController::reloadScene);
```

### Files to Modify

| File | Change |
|------|--------|
| `editor/shortcut/EditorShortcuts.java` | Register `FILE_RELOAD` action, bind handler |
| `editor/shortcut/EditorShortcutHandlers.java` | Add `onReloadScene()` to interface |
| `editor/shortcut/EditorShortcutHandlersImpl.java` | Implement `onReloadScene()`, add `@Setter EditorSceneController` |
| `editor/EditorApplication.java` | Wire `handlers.setSceneController()`, `sceneController.setPlayModeController()`, `menuBar.setOnReloadScene()` |
| `editor/ui/EditorMenuBar.java` | Add `onReloadScene` callback and menu item |

---

## Phase 4: Handle Edge Cases

### Serialization Robustness

- [ ] **Removed fields** — Gson ignores unknown JSON keys by default. Old values silently discarded. **Verify** with a manual test.
- [ ] **Added fields** — New fields get Java default values. **Verify** with a manual test.
- [ ] **Renamed/retyped fields** — Old value silently dropped, new field gets default. Acceptable.
- [ ] **New component classes** — `ComponentRegistry.reinitialize()` discovers them. "Add Component" menu reads from `ComponentRegistry.getCategories()` dynamically. **Verify** this works.
- [ ] **Removed component classes** — `ComponentTypeAdapterFactory.readComponentProperties()` (line 216) calls `ComponentRegistry.getByClassName()`. If null, check whether it skips gracefully or throws. Add error handling if needed.

### Undo History

- [ ] **Clear undo history** — Handled in `reloadScene()` step 5. Undo actions reference stale object instances. Consistent with `openScene()` (line 83).

### Dirty State

- [ ] **Preserve dirty flag** — `fromSceneData()` calls `clearDirty()`. `EditorStateSnapshot.restore()` re-marks if needed.

### Scene with No File Path

- [ ] **"Untitled" scenes** — `scenePath` will be null. `fromSceneData(snapshot, null)` calls `scene.setFilePath(null)`. `EditorScene.getName()` returns "Untitled" for null path. Should work.

### PrefabRegistry

- [ ] **Not re-scanned** — `PrefabRegistry` loads `.prefab.json` files from disk (not Reflections-based). Prefab file changes are a separate concern from class hot-reload. If needed later, `PrefabRegistry` can subscribe to `RegistriesRefreshRequestEvent`.

### Duplicate Event Subscription

- [ ] **Prevent double-subscribe on multiple `reinitialize()` calls** — Each registry subscribes to the event during its `initialize()`. Since `reinitialize()` calls `initialize()` again, it could add a duplicate subscription. Guard against this: subscribe only once, using a static `boolean subscribed` flag, or subscribe outside of `initialize()` (in a separate `subscribeToEvents()` method called from `EditorApplication`).

  **Recommended approach:** Subscribe from `EditorApplication.init()` rather than inside each registry. This keeps event wiring in the application wiring hub and avoids the double-subscribe problem entirely.

  ```java
  // In EditorApplication.init(), after all registries are initialized:
  EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class, event -> {
      ComponentRegistry.reinitialize();
      PostEffectRegistry.reinitialize();
      CustomComponentEditorRegistry.reinitialize();
      RuntimeGameObjectAdapter.clearCache();
      SceneUtils.clearCache();
  });
  ```

  > **Trade-off:** This centralizes the subscription (easier to reason about, no double-subscribe risk) but means new registries must be added here. However, since `EditorApplication.init()` already lists all registries explicitly at lines 122-146, this is consistent with the existing pattern. The event still provides the extension point for non-editor code or plugins that subscribe independently.

---

## Phase 5: Testing Strategy

### Manual Tests

- [ ] Open a scene → recompile with a changed method body → Ctrl+Shift+R → verify no crash
- [ ] Open a scene → add a new field to a component → recompile → Ctrl+Shift+R → verify field appears in inspector with default value
- [ ] Open a scene → remove a field from a component → recompile → Ctrl+Shift+R → verify no crash, field gone from inspector
- [ ] Create a new Component subclass → recompile → Ctrl+Shift+R → verify it appears in "Add Component" menu
- [ ] Create a new class with `@InspectorFor` → recompile → Ctrl+Shift+R → verify custom inspector appears
- [ ] Create a new PostEffect subclass → recompile → Ctrl+Shift+R → verify it appears in post-effect dropdown
- [ ] Verify camera position and zoom preserved after reload
- [ ] Verify entity multi-selection preserved (select 2-3 entities, reload, check all still selected)
- [ ] Verify undo history cleared (make undoable changes, reload, Ctrl+Z does nothing)
- [ ] Verify reload blocked during play mode (try shortcut AND menu item)
- [ ] Verify reload of "Untitled" scene (no file path) works
- [ ] Verify dirty flag preserved: make changes → reload → asterisk in title
- [ ] Verify clean flag preserved: save → reload → no asterisk
- [ ] Verify failed reload leaves old scene intact (introduce a deserialization error, verify scene survives)

### Automated Tests

- [ ] Test `ComponentRegistry.reinitialize()` clears and rebuilds (count before == count after)
- [ ] Test `PostEffectRegistry.reinitialize()` clears and rebuilds
- [ ] Test `CustomComponentEditorRegistry.reinitialize()` clears and re-scans
- [ ] Test `EditorSceneSerializer` round-trip: `toSceneData → fromSceneData` produces scene with same entity count, IDs, component types
- [ ] Test `EditorStateSnapshot.capture()` and `restore()` in isolation (mock context and scene)
- [ ] Test `RegistriesRefreshRequestEvent` triggers all subscribers

---

## Phase 6: Code Review

- [ ] Verify `reinitialize()` methods are main-thread-only (document in Javadoc, no synchronization needed for single-threaded editor)
- [ ] Review error handling: failed reload must leave old scene intact (swap-then-destroy)
- [ ] Check that all editor panels react to `context.setCurrentScene()` — `EditorApplication.onSceneChanged()` (line 331) calls `toolController.updateSceneReferences()` and `uiController.updateSceneReferences()`
- [ ] Verify Reflections creates a fresh scan per `new Reflections(...)` call
- [ ] Verify `FieldEditorContext.setCurrentScene()` is updated — happens automatically via `context.setCurrentScene()` (EditorContext line 87)
- [ ] Verify `Serializer` Gson instance is not stale — `ComponentTypeAdapterFactory` reads from `ComponentRegistry` dynamically per deserialization, not cached at construction
- [ ] Verify no double event subscription (see Phase 4 recommendation)
- [ ] Verify `SceneWillChangeEvent` fires before destroy (step 5 in `reloadScene()`)

---

## Complete File Summary

| File | Change |
|------|--------|
| `editor/events/RegistriesRefreshRequestEvent.java` | **NEW** — Event record |
| `editor/EditorStateSnapshot.java` | **NEW** — State capture/restore record |
| `serialization/ComponentRegistry.java` | Add `reinitialize()` method |
| `rendering/postfx/PostEffectRegistry.java` | Add `reinitialize()` method |
| `editor/ui/inspectors/CustomComponentEditorRegistry.java` | Add `reinitialize()` method |
| `editor/EditorSceneController.java` | Add `@Setter PlayModeController`, add `reloadScene()` |
| `editor/shortcut/EditorShortcutHandlers.java` | Add `onReloadScene()` to interface |
| `editor/shortcut/EditorShortcuts.java` | Register `FILE_RELOAD` action, bind handler |
| `editor/shortcut/EditorShortcutHandlersImpl.java` | Implement `onReloadScene()`, add `@Setter EditorSceneController` |
| `editor/EditorApplication.java` | Wire shortcuts, menu, PlayModeController, event subscriptions |
| `editor/ui/EditorMenuBar.java` | Add `onReloadScene` callback and menu item |

---

## Limitations

| Scenario | Behavior |
|---|---|
| Renamed field | Old value lost silently (JSON key mismatch) |
| Changed field type | May cause Gson deserialization error or data loss |
| Prefab changes | PrefabRegistry not re-scanned; can subscribe later if needed |
| TilesetRegistry | Not re-scanned; uses asset scanning, not Reflections |
| Large scenes | Brief freeze during serialize + scan + deserialize |
| AssetLoader changes | Not covered — engine infrastructure |
| Standard JVM (no DCEVM) | Only new classes discovered; modified classes need DCEVM or restart |

---

## Important Note: JVM Class Loading vs Registry Scanning

The registries use Reflections to **scan the filesystem** for `.class` files and then use `Class.forName()` / reflection to load them. On a standard JVM:

- **New classes:** Reflections finds new `.class` files. `Class.forName()` loads them (never loaded before). **Works.**
- **Removed classes:** Reflections won't find them. Old `Class` objects remain in the JVM but won't be in the registries. **Works.**
- **Modified classes (structural):** Reflections finds the `.class` file, but `Class.forName()` returns the **already-loaded** old class. `buildMeta()` uses `clazz.getDeclaredFields()` on the old `Class`, producing identical metadata. **Does not work without DCEVM.**

The reload command is most useful **combined with DCEVM** (Option 1), which applies structural class changes at the JVM level before the registry re-scan picks them up. Without DCEVM, reload still helps with:
- New component classes (never loaded before)
- Refreshing editor UI after IntelliJ debug-mode class redefinition (method body changes)
- Serialize/deserialize round-trip as a development workflow
