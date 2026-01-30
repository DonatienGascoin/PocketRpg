# IGameObject Interface & Play Mode Inspection

## Overview

This plan merges the `igameobject-interface` and `play-mode-inspection` plans into a single implementation. Both modify `EditorGameObject` and `Component`, and play-mode-inspection depends on `IGameObject` — so implementing them together avoids redundant changes.

**Goals:**
1. Extract `IGameObject` interface so Components work in both runtime and editor contexts
2. Enable hierarchy and inspector panels to display runtime GameObjects during play mode
3. Use the existing `EditorEventBus` for play mode state changes (already implemented: `PlayModeStartedEvent`, `PlayModeStoppedEvent`, `PlayModePausedEvent`)

## Current State

- `Component` has `gameObject` field typed as `GameObject` — NPE in editor mode
- Hierarchy/Inspector panels only show frozen `EditorScene` state during play mode
- `PlayModeController` already publishes play mode events via `EditorEventBus`

---

## Implementation

### Phase 1: IGameObject Interface

#### 1. `IGameObject` Interface (NEW)
**File:** `src/main/java/com/pocket/rpg/core/IGameObject.java`

```java
package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import java.util.List;

/**
 * Common interface for game object types.
 * Implemented by both runtime {@link GameObject} and editor EditorGameObject.
 * Allows {@link Component} to access transform and sibling components
 * without knowing whether it's in runtime or editor context.
 */
public interface IGameObject {

    String getName();
    String getId();
    Transform getTransform();

    <T extends Component> T getComponent(Class<T> type);
    <T extends Component> List<T> getComponents(Class<T> type);
    List<Component> getAllComponents();

    default boolean hasComponent(Class<? extends Component> type) {
        return getComponent(type) != null;
    }

    boolean isEnabled();

    default boolean isRuntime() {
        return this instanceof GameObject;
    }

    default boolean isEditor() {
        return !isRuntime();
    }
}
```

#### 2. Update `GameObject` to implement `IGameObject`
**File:** `src/main/java/com/pocket/rpg/core/GameObject.java`

- Add `implements IGameObject`
- Add `getId()` method:
```java
@Override
public String getId() {
    return name != null ? name : ("obj_" + System.identityHashCode(this));
}
```
- Existing methods already satisfy: `getName()`, `getTransform()`, `getComponent()`, `getComponents()`, `getAllComponents()`, `isEnabled()`

#### 3. Update `EditorGameObject` to implement `IGameObject`
**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

- Change to `implements Renderable, IGameObject`
- Add missing methods:
```java
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

@Override
public List<Component> getAllComponents() {
    return getComponents();
}

@Override
public boolean isEnabled() {
    return true;
}
```

- Existing methods already satisfy: `getId()`, `getName()`, `getTransform()`, `getComponent()`

---

### Phase 2: Component Owner Refactor

#### 4. Update `Component` to use `IGameObject` owner
**File:** `src/main/java/com/pocket/rpg/components/Component.java`

Add `owner` field and update helper methods:

```java
@Getter
protected IGameObject owner;

// Backward compatibility
@Deprecated
protected GameObject gameObject;

public IGameObject getOwner() {
    return owner;
}

public GameObject getGameObject() {
    return (owner instanceof GameObject go) ? go : null;
}

public void setOwner(IGameObject owner) {
    this.owner = owner;
    this.gameObject = (owner instanceof GameObject go) ? go : null;
}

@Deprecated
public void setGameObject(GameObject gameObject) {
    setOwner(gameObject);
}

protected Transform getTransform() {
    return owner != null ? owner.getTransform() : null;
}

protected <T extends Component> T getComponent(Class<T> type) {
    return owner != null ? owner.getComponent(type) : null;
}

protected <T extends Component> List<T> getComponents(Class<T> type) {
    return owner != null ? owner.getComponents(type) : Collections.emptyList();
}

public boolean isEnabled() {
    return enabled && owner != null && owner.isEnabled();
}
```

#### 5. Update `GameObject` to use `setOwner()`
**File:** `src/main/java/com/pocket/rpg/core/GameObject.java`

In `addComponentInternal`:
```java
component.setOwner(this);  // was: setGameObject(this)
```

In `removeComponent`:
```java
component.setOwner(null);  // was: setGameObject(null)
```

#### 6. Update `EditorGameObject` to set owner on components
**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

Lazy initialization approach — ensure owner is set when components are accessed:

```java
public List<Component> getComponents() {
    // ... existing logic ...
    // After building component list, ensure owner is set:
    for (Component comp : result) {
        if (comp.getOwner() != this) {
            comp.setOwner(this);
        }
    }
    return result;
}
```

Also set owner in `addComponent()`:
```java
public void addComponent(Component component) {
    component.setOwner(this);
    // ... existing logic ...
}
```

---

### Phase 3: HierarchyItem Interface & Adapter

#### 7. `HierarchyItem` Interface (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyItem.java`

```java
package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.core.IGameObject;
import java.util.List;

/**
 * Extended interface for game objects that can be displayed in the hierarchy panel.
 * Extends {@link IGameObject} to add hierarchy navigation and editability.
 */
public interface HierarchyItem extends IGameObject {

    List<? extends HierarchyItem> getHierarchyChildren();

    default boolean hasHierarchyChildren() {
        return !getHierarchyChildren().isEmpty();
    }

    /**
     * Whether this item can be edited (rename, delete, reparent, etc.)
     * Returns true for editor objects, false for runtime objects.
     */
    default boolean isEditable() {
        return isEditor();
    }
}
```

#### 8. Update `EditorGameObject` to implement `HierarchyItem`
**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

Change from `implements Renderable, IGameObject` to `implements Renderable, HierarchyItem`:

```java
public class EditorGameObject implements Renderable, HierarchyItem {

    @Override
    public List<? extends HierarchyItem> getHierarchyChildren() {
        return getChildren();  // List<EditorGameObject> is List<? extends HierarchyItem>
    }
    // isEditable() default returns true (isEditor() == true)
}
```

#### 9. `RuntimeGameObjectAdapter` (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/scene/RuntimeGameObjectAdapter.java`

Wraps a runtime `GameObject` to implement `HierarchyItem`:

```java
package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class RuntimeGameObjectAdapter implements HierarchyItem {

    private static final Map<GameObject, RuntimeGameObjectAdapter> adapterCache = new WeakHashMap<>();

    @Getter
    private final GameObject gameObject;

    private RuntimeGameObjectAdapter(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    public static RuntimeGameObjectAdapter of(GameObject gameObject) {
        return adapterCache.computeIfAbsent(gameObject, RuntimeGameObjectAdapter::new);
    }

    public static void clearCache() {
        adapterCache.clear();
    }

    // IGameObject delegation
    @Override public String getName() { return gameObject.getName(); }
    @Override public String getId() { return "runtime_" + gameObject.getId(); }
    @Override public Transform getTransform() { return gameObject.getTransform(); }
    @Override public <T extends Component> T getComponent(Class<T> type) { return gameObject.getComponent(type); }
    @Override public <T extends Component> List<T> getComponents(Class<T> type) { return gameObject.getComponents(type); }
    @Override public List<Component> getAllComponents() { return gameObject.getAllComponents(); }
    @Override public boolean isEnabled() { return gameObject.isEnabled(); }
    @Override public boolean isRuntime() { return true; }

    // HierarchyItem
    @Override
    public List<? extends HierarchyItem> getHierarchyChildren() {
        List<GameObject> children = gameObject.getChildren();
        if (children.isEmpty()) return List.of();
        List<HierarchyItem> wrapped = new ArrayList<>(children.size());
        for (GameObject child : children) {
            wrapped.add(RuntimeGameObjectAdapter.of(child));
        }
        return wrapped;
    }

    @Override
    public boolean isEditable() {
        return false;  // Runtime objects are read-only in hierarchy
    }
}
```

---

### Phase 4: Play Mode Selection

#### 10. `PlayModeSelectionManager` (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/PlayModeSelectionManager.java`

```java
package com.pocket.rpg.editor;

import com.pocket.rpg.core.GameObject;
import lombok.Getter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages selection of runtime GameObjects during play mode.
 * Separate from EditorSelectionManager to keep runtime and editor state isolated.
 */
public class PlayModeSelectionManager {

    @Getter
    private final Set<GameObject> selectedObjects = new LinkedHashSet<>();

    public void select(GameObject obj) {
        selectedObjects.clear();
        if (obj != null) selectedObjects.add(obj);
    }

    public void toggleSelection(GameObject obj) {
        if (obj == null) return;
        if (!selectedObjects.remove(obj)) selectedObjects.add(obj);
    }

    public void clearSelection() {
        selectedObjects.clear();
    }

    public GameObject getSingleSelected() {
        return selectedObjects.size() == 1 ? selectedObjects.iterator().next() : null;
    }

    public boolean isSelected(GameObject obj) {
        return selectedObjects.contains(obj);
    }

    /**
     * Removes any selected objects that are no longer alive in the scene.
     * Call each frame to handle objects destroyed at runtime.
     */
    public void pruneDestroyedObjects() {
        selectedObjects.removeIf(GameObject::isDestroyed);
    }
}
```

**Note:** This requires `GameObject.isDestroyed()` to exist. If it doesn't, we add a simple `destroyed` flag set during `GameObject.destroy()`.

#### 11. Update `PlayModeController`
**File:** `src/main/java/com/pocket/rpg/editor/PlayModeController.java`

Add field and getter:
```java
@Getter
private PlayModeSelectionManager playModeSelectionManager;
```

Add method to expose runtime scene:
```java
public Scene getRuntimeScene() {
    return gameLoop != null ? gameLoop.getSceneManager().getCurrentScene() : null;
}
```

In `play()`, after creating runtime systems:
```java
playModeSelectionManager = new PlayModeSelectionManager();
```

In `cleanup()`:
```java
if (playModeSelectionManager != null) {
    playModeSelectionManager.clearSelection();
    playModeSelectionManager = null;
}
RuntimeGameObjectAdapter.clearCache();
```

---

### Phase 5: Hierarchy Panel — Play Mode Support

#### 12. Update `HierarchyPanel`
**File:** `src/main/java/com/pocket/rpg/editor/panels/HierarchyPanel.java`

Subscribe to play mode events via `EditorEventBus` instead of setter injection:

```java
private PlayModeController playModeController;

public void setPlayModeController(PlayModeController controller) {
    this.playModeController = controller;
}

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}
```

Modify `render()` to branch on play mode:

```java
@Override
public void render() {
    if (!isOpen()) return;

    if (ImGui.begin("Hierarchy")) {
        if (isPlayMode()) {
            renderPlayModeHeader();
            renderRuntimeHierarchy();
        } else {
            // All existing editor rendering (scene null check, camera, tilemap, entities, etc.)
            renderEditorHierarchy();
        }
    }
    ImGui.end();

    if (!isPlayMode()) {
        dragDropHandler.resetDropTarget();
    }
}
```

Extract existing render body into `renderEditorHierarchy()`.

Add play mode rendering:

```java
private void renderPlayModeHeader() {
    ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.6f, 0.2f, 1f);
    ImGui.text(MaterialIcons.PlayArrow + " PLAY MODE");
    ImGui.popStyleColor();
    ImGui.separator();
}

private void renderRuntimeHierarchy() {
    Scene runtimeScene = playModeController.getRuntimeScene();
    if (runtimeScene == null) {
        ImGui.textDisabled("No runtime scene");
        return;
    }

    // Prune destroyed objects from selection
    PlayModeSelectionManager selMgr = playModeController.getPlayModeSelectionManager();
    if (selMgr != null) {
        selMgr.pruneDestroyedObjects();
    }

    List<GameObject> rootObjects = runtimeScene.getRootGameObjects();
    if (rootObjects.isEmpty()) {
        ImGui.textDisabled("No entities");
    } else {
        for (GameObject obj : rootObjects) {
            HierarchyItem adapter = RuntimeGameObjectAdapter.of(obj);
            treeRenderer.renderHierarchyItemTree(adapter, selMgr);
        }
    }
}
```

**Dynamic updates**: Because `renderRuntimeHierarchy()` calls `runtimeScene.getRootGameObjects()` each frame, entities created, destroyed, or reparented at runtime are automatically reflected. No event subscription is needed for hierarchy mutations.

#### 13. Update `HierarchyTreeRenderer`
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyTreeRenderer.java`

Add a new method for rendering any `HierarchyItem` (used only in play mode). The existing `renderEntityTree(EditorGameObject)` remains unchanged for editor mode:

```java
/**
 * Renders a HierarchyItem tree for play mode display.
 * Read-only — no drag-drop, no context menu, no rename.
 */
public void renderHierarchyItemTree(HierarchyItem item, PlayModeSelectionManager selMgr) {
    int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;

    if (!item.hasHierarchyChildren()) {
        flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
    }

    // Selection highlight
    if (item instanceof RuntimeGameObjectAdapter adapter && selMgr != null) {
        if (selMgr.isSelected(adapter.getGameObject())) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
    }

    String icon = item.isEnabled()
        ? IconUtils.getGenericEntityIcon()
        : IconUtils.getDisabledEntityIcon();
    boolean open = ImGui.treeNodeEx(item.getId(), flags, icon + " " + item.getName());

    // Handle click — select in PlayModeSelectionManager
    if (ImGui.isItemClicked() && item instanceof RuntimeGameObjectAdapter adapter && selMgr != null) {
        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        if (ctrl) {
            selMgr.toggleSelection(adapter.getGameObject());
        } else {
            selMgr.select(adapter.getGameObject());
        }
    }

    // No context menu, no drag-drop (isEditable() == false)

    if (open && item.hasHierarchyChildren()) {
        for (HierarchyItem child : item.getHierarchyChildren()) {
            renderHierarchyItemTree(child, selMgr);
        }
        ImGui.treePop();
    }
}
```

---

### Phase 6: Inspector Panel — Play Mode Support

#### 14. Update `InspectorPanel`
**File:** `src/main/java/com/pocket/rpg/editor/panels/InspectorPanel.java`

Add play mode controller reference:
```java
@Setter
private PlayModeController playModeController;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}
```

Modify `render()` to check play mode first:

```java
@Override
public void render() {
    if (!isOpen()) return;
    if (ImGui.begin("Inspector")) {
        if (isPlayMode()) {
            renderPlayModeInspector();
        } else {
            // Existing editor inspector logic (scene null check, renderCurrentInspector, etc.)
            renderEditorInspector();
        }
    }
    ReflectionFieldEditor.renderAssetPicker();
    entityInspector.renderDeleteConfirmationPopup();
    ImGui.end();
}
```

Extract existing render body into `renderEditorInspector()`.

Add play mode inspector:

```java
private void renderPlayModeInspector() {
    PlayModeSelectionManager selMgr = playModeController.getPlayModeSelectionManager();
    if (selMgr == null) return;

    // Prune destroyed objects
    selMgr.pruneDestroyedObjects();

    Set<GameObject> selected = selMgr.getSelectedObjects();
    if (selected.isEmpty()) {
        ImGui.textDisabled("Select an entity in the hierarchy");
        return;
    }

    if (selected.size() > 1) {
        ImGui.text(selected.size() + " objects selected");
        ImGui.textDisabled("Multi-selection not supported in play mode");
        return;
    }

    GameObject obj = selected.iterator().next();
    entityInspector.renderRuntime(obj);
}
```

#### 15. Update `EntityInspector`
**File:** `src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java`

Add runtime rendering method using `IGameObject`:

```java
/**
 * Renders inspector for a runtime game object during play mode.
 * Read-only header, editable component fields (changes are temporary).
 */
public void renderRuntime(IGameObject gameObject) {
    // Header (read-only)
    String icon = MaterialIcons.Cube;
    ImGui.text(icon);
    ImGui.sameLine();
    ImGui.text(gameObject.getName());
    ImGui.sameLine();
    if (gameObject.isEnabled()) {
        ImGui.textColored(0.4f, 0.8f, 0.4f, 1f, "(enabled)");
    } else {
        ImGui.textColored(0.8f, 0.4f, 0.4f, 1f, "(disabled)");
    }

    ImGui.separator();

    // Render components
    List<Component> components = gameObject.getAllComponents();
    for (int i = 0; i < components.size(); i++) {
        Component comp = components.get(i);
        ImGui.pushID(i);
        String label = comp.getClass().getSimpleName();
        boolean open = ImGui.collapsingHeader(label, ImGuiTreeNodeFlags.DefaultOpen);
        if (open) {
            fieldEditor.renderRuntimeComponentFields(comp);
        }
        ImGui.popID();
    }

    ImGui.separator();
    ImGui.textDisabled("Changes reset when play mode stops");
}
```

#### 16. Update `ComponentFieldEditor`
**File:** `src/main/java/com/pocket/rpg/editor/panels/inspector/ComponentFieldEditor.java`

Add runtime-specific method (no undo, no prefab overrides):

```java
public boolean renderRuntimeComponentFields(Component component) {
    ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
    if (meta == null) {
        ImGui.textDisabled("Unknown component type");
        return false;
    }

    boolean changed = false;
    for (FieldMeta fieldMeta : meta.fields()) {
        FieldEditorContext ctx = FieldEditorContext.forRuntime(component, fieldMeta);
        if (ReflectionFieldEditor.renderField(ctx)) {
            changed = true;
        }
    }
    return changed;
}
```

#### 17. Update `FieldEditorContext`
**File:** `src/main/java/com/pocket/rpg/editor/ui/fields/FieldEditorContext.java`

Add factory method for runtime mode:

```java
public static FieldEditorContext forRuntime(Component component, FieldMeta fieldMeta) {
    return new FieldEditorContext(
        component,
        fieldMeta,
        null,   // no entity
        false,  // not prefab
        true    // isPlayMode — skips undo registration
    );
}
```

---

### Phase 7: Wiring & Cleanup

#### 18. Wire `PlayModeController` to panels
**File:** `src/main/java/com/pocket/rpg/editor/EditorUIController.java`

```java
hierarchyPanel.setPlayModeController(playModeController);
inspectorPanel.setPlayModeController(playModeController);
```

#### 19. Clear adapter cache on play stop
Already handled in step 11 (`cleanup()` calls `RuntimeGameObjectAdapter.clearCache()`).

---

### Phase 8: Polish & Edge Cases

#### 20. Visual Indicators

**Hierarchy:**
- Orange "PLAY MODE" header when active
- No context menus or drag-drop for runtime items

**Inspector:**
- "Changes reset when play mode stops" footer notice
- Optionally orange accent for play mode field labels

#### 21. Edge Cases

| Case | Handling |
|------|----------|
| **Entity created at runtime** | `getRootGameObjects()` is called each frame — new entities appear automatically |
| **Entity destroyed at runtime** | `WeakHashMap` in adapter cache + `pruneDestroyedObjects()` on selection manager |
| **Entity reparented at runtime** | Same as creation — hierarchy re-reads tree each frame |
| **Scene transition during play** | `getRuntimeScene()` returns new scene; old adapters are GC'd via WeakHashMap |
| **Paused state** | `isActive()` returns true for PLAYING and PAUSED — hierarchy still visible |
| **Selected object destroyed** | `pruneDestroyedObjects()` called before rendering inspector, removes stale selections |

#### 22. `GameObject.isDestroyed()` guard

If `GameObject` doesn't already have a `destroyed` flag, add one:

```java
// In GameObject:
@Getter
private boolean destroyed = false;

@Override
public void destroy() {
    destroyed = true;
    // ... existing cleanup ...
}
```

This allows `PlayModeSelectionManager.pruneDestroyedObjects()` to work.

---

## Files Summary

| File | Change | Phase |
|------|--------|-------|
| `core/IGameObject.java` | **NEW** | 1 |
| `core/GameObject.java` | MODIFY — implement IGameObject, add getId(), use setOwner() | 1, 2 |
| `editor/scene/EditorGameObject.java` | MODIFY — implement HierarchyItem, add missing methods, set owner | 1, 2, 3 |
| `components/Component.java` | MODIFY — IGameObject owner, helper methods | 2 |
| `editor/panels/hierarchy/HierarchyItem.java` | **NEW** | 3 |
| `editor/scene/RuntimeGameObjectAdapter.java` | **NEW** | 3 |
| `editor/PlayModeSelectionManager.java` | **NEW** | 4 |
| `editor/PlayModeController.java` | MODIFY — add selection manager, getRuntimeScene(), cleanup | 4 |
| `editor/panels/HierarchyPanel.java` | MODIFY — play mode branch, runtime hierarchy rendering | 5 |
| `editor/panels/hierarchy/HierarchyTreeRenderer.java` | MODIFY — add renderHierarchyItemTree() | 5 |
| `editor/panels/InspectorPanel.java` | MODIFY — play mode branch, runtime inspector | 6 |
| `editor/panels/inspector/EntityInspector.java` | MODIFY — add renderRuntime() | 6 |
| `editor/panels/inspector/ComponentFieldEditor.java` | MODIFY — add renderRuntimeComponentFields() | 6 |
| `editor/ui/fields/FieldEditorContext.java` | MODIFY — add forRuntime() factory | 6 |
| `editor/EditorUIController.java` | MODIFY — wire PlayModeController to panels | 7 |

---

## Verification Checklist

### IGameObject (Phases 1-2)
- [ ] Components can call `getTransform()` in gizmo methods without NPE
- [ ] Components can call `getComponent()` in gizmo methods without NPE
- [ ] Runtime behavior unchanged (all tests pass)
- [ ] Editor scene loads without errors
- [ ] Gizmos render correctly for all component types
- [ ] `owner.isRuntime()` / `owner.isEditor()` return correct values

### Play Mode Inspection (Phases 3-8)
- [ ] Start play mode → Hierarchy shows runtime GameObjects with orange "PLAY MODE" header
- [ ] Click entity in hierarchy → Entity is selected (visual feedback)
- [ ] Inspector shows selected entity's components
- [ ] Edit a component value (e.g., position) → Change applies to running game
- [ ] Stop play mode → Hierarchy reverts to editor scene
- [ ] Edited values reset to original after stopping play mode
- [ ] Cannot drag-drop, rename, or delete entities during play mode
- [ ] Pausing shows hierarchy normally
- [ ] Editor scene is unmodified after play/stop cycle
- [ ] Entity created at runtime appears in hierarchy
- [ ] Entity destroyed at runtime disappears from hierarchy and selection
- [ ] Scene transition during play updates hierarchy to new scene

---

## Code Review

After implementation, create a review document at `Documents/Plans/igameobject-interface/review.md` covering:
- Code quality and adherence to existing patterns
- Proper cleanup of runtime state
- Edge case handling
- Performance considerations (adapter caching, pruning)

---

## Follow-Up: Remove Deprecated `Component.gameObject` Field

After the main implementation is complete and stable, perform an analysis to fully remove the deprecated `gameObject` field from `Component`:

1. **Audit all Component subclasses** that access `gameObject` directly (not via `getGameObject()`)
2. **Identify runtime-only usages** — code that needs `GameObject`-specific methods like `getScene()`, `getParent()`, `setParent()`, etc. These must use `getGameObject()` or cast `getOwner()`.
3. **Identify context-agnostic usages** — code that only needs `getTransform()`, `getComponent()`, etc. These should switch to `getOwner()`.
4. **Estimate scope** of the migration (number of files, risk level)
5. **Propose a plan** to remove the deprecated field, or justify keeping the compatibility shim if the migration cost is too high
