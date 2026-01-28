# Play Mode Hierarchy & Inspector Support

## Overview

Enable the hierarchy and inspector panels to display and interact with runtime GameObjects during play mode. Changes made during play mode affect only the running game and reset when play stops (Unity-style behavior).

## Prerequisites

**Requires**: `igameobject-interface` plan must be implemented first.

This plan depends on:
- `IGameObject` interface existing
- `GameObject implements IGameObject`
- `EditorGameObject implements IGameObject`
- `Component.getOwner()` returning `IGameObject`

## Current State

- **EditorScene** with **EditorGameObjects** is frozen at play start, restored when play stops
- **RuntimeScene** with **GameObjects** runs during play, destroyed when play stops
- Hierarchy/Inspector panels always render but only show frozen EditorScene state
- No way to see or interact with runtime entities

## Design Approach

Use a **Mode-Aware Adapter Pattern** with interface inheritance:
1. Create `HierarchyItem extends IGameObject` interface for hierarchy-specific needs
2. Create `RuntimeGameObjectAdapter implements HierarchyItem` that wraps runtime `GameObject`s
3. Add a `PlayModeSelectionManager` for runtime selection (separate from editor selection)
4. Modify panels to detect play mode and switch data sources

### Interface Hierarchy

```
IGameObject (from igameobject-interface plan)
    ├── GameObject implements IGameObject
    ├── EditorGameObject implements HierarchyItem
    └── HierarchyItem extends IGameObject
            └── RuntimeGameObjectAdapter implements HierarchyItem (wraps GameObject)
```

This design:
- Eliminates duplicate methods between interfaces
- Keeps hierarchy/UI concerns separate from core game object functionality
- Uses adapter pattern to bridge GameObject (no hierarchy methods) to HierarchyItem

---

## Implementation

### Phase 1: Core Infrastructure

#### 1. `HierarchyItem` Interface (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyItem.java`

```java
package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.core.IGameObject;
import java.util.List;

/**
 * Extended interface for game objects that can be displayed in the hierarchy panel.
 * Extends {@link IGameObject} to add hierarchy navigation and editability.
 * <p>
 * Implemented by {@link EditorGameObject} directly and by {@link RuntimeGameObjectAdapter}
 * which wraps runtime {@link GameObject}s for hierarchy display.
 */
public interface HierarchyItem extends IGameObject {

    /**
     * Returns child items for hierarchy tree display.
     * For EditorGameObject: returns children directly.
     * For RuntimeGameObjectAdapter: returns wrapped children.
     */
    List<? extends HierarchyItem> getHierarchyChildren();

    /**
     * Returns whether this item has children to display.
     */
    default boolean hasHierarchyChildren() {
        return !getHierarchyChildren().isEmpty();
    }

    /**
     * Returns whether this item can be edited (rename, delete, reparent, etc.)
     * Returns true for editor objects, false for runtime objects during play mode.
     */
    default boolean isEditable() {
        return isEditor();
    }
}
```

**Note**: Methods from `IGameObject` are inherited:
- `getName()` - used as display name
- `getId()` - used as unique ID for ImGui
- `getAllComponents()` - used for inspector
- `isEnabled()`, `isRuntime()`, `isEditor()` - context checks

#### 2. `RuntimeGameObjectAdapter` (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/scene/RuntimeGameObjectAdapter.java`

Wraps a runtime `GameObject` to implement `HierarchyItem`. Delegates `IGameObject` methods to the wrapped object:

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

/**
 * Adapts a runtime {@link GameObject} to the {@link HierarchyItem} interface
 * for display in the hierarchy panel during play mode.
 * <p>
 * Uses a static cache to avoid recreating adapters for the same GameObject.
 */
public class RuntimeGameObjectAdapter implements HierarchyItem {

    // Cache adapters by GameObject to maintain stable references
    private static final Map<GameObject, RuntimeGameObjectAdapter> adapterCache = new WeakHashMap<>();

    @Getter
    private final GameObject gameObject;

    private List<HierarchyItem> cachedChildren;

    private RuntimeGameObjectAdapter(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    /**
     * Gets or creates an adapter for the given GameObject.
     */
    public static RuntimeGameObjectAdapter of(GameObject gameObject) {
        return adapterCache.computeIfAbsent(gameObject, RuntimeGameObjectAdapter::new);
    }

    /**
     * Clears the adapter cache. Call when play mode ends.
     */
    public static void clearCache() {
        adapterCache.clear();
    }

    // ========================================================================
    // IGameObject methods - delegate to wrapped GameObject
    // ========================================================================

    @Override
    public String getName() {
        return gameObject.getName();
    }

    @Override
    public String getId() {
        // Prefix to distinguish from editor IDs
        return "runtime_" + gameObject.getId();
    }

    @Override
    public Transform getTransform() {
        return gameObject.getTransform();
    }

    @Override
    public <T extends Component> T getComponent(Class<T> type) {
        return gameObject.getComponent(type);
    }

    @Override
    public <T extends Component> List<T> getComponents(Class<T> type) {
        return gameObject.getComponents(type);
    }

    @Override
    public List<Component> getAllComponents() {
        return gameObject.getAllComponents();
    }

    @Override
    public boolean isEnabled() {
        return gameObject.isEnabled();
    }

    @Override
    public boolean isRuntime() {
        return true;  // Always runtime
    }

    // ========================================================================
    // HierarchyItem methods
    // ========================================================================

    @Override
    public List<? extends HierarchyItem> getHierarchyChildren() {
        List<GameObject> children = gameObject.getChildren();
        if (children.isEmpty()) {
            return List.of();
        }

        // Cache and wrap children
        if (cachedChildren == null || cachedChildren.size() != children.size()) {
            cachedChildren = new ArrayList<>(children.size());
            for (GameObject child : children) {
                cachedChildren.add(RuntimeGameObjectAdapter.of(child));
            }
        }
        return cachedChildren;
    }

    @Override
    public boolean isEditable() {
        return false;  // Runtime objects are not editable
    }
}
```

#### 3. `PlayModeSelectionManager` (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/PlayModeSelectionManager.java`

```java
package com.pocket.rpg.editor;

import com.pocket.rpg.core.GameObject;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages selection of runtime GameObjects during play mode.
 * Separate from the editor's SelectionManager to keep runtime and editor state isolated.
 */
public class PlayModeSelectionManager {

    @Getter
    private final Set<GameObject> selectedObjects = new LinkedHashSet<>();

    public void select(GameObject obj) {
        selectedObjects.clear();
        if (obj != null) {
            selectedObjects.add(obj);
        }
    }

    public void addToSelection(GameObject obj) {
        if (obj != null) {
            selectedObjects.add(obj);
        }
    }

    public void toggleSelection(GameObject obj) {
        if (obj == null) return;
        if (selectedObjects.contains(obj)) {
            selectedObjects.remove(obj);
        } else {
            selectedObjects.add(obj);
        }
    }

    public void clearSelection() {
        selectedObjects.clear();
    }

    public GameObject getSingleSelected() {
        if (selectedObjects.size() == 1) {
            return selectedObjects.iterator().next();
        }
        return null;
    }

    public boolean isSelected(GameObject obj) {
        return selectedObjects.contains(obj);
    }
}
```

#### 4. Update `EditorGameObject` to implement `HierarchyItem`
**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

Change from `implements IGameObject` to `implements HierarchyItem` (which extends IGameObject):

```java
// Was: implements Renderable, IGameObject (from igameobject-interface plan)
// Now: implements Renderable, HierarchyItem
public class EditorGameObject implements Renderable, HierarchyItem {
```

Add only the hierarchy-specific method (IGameObject methods already implemented):

```java
/**
 * Returns children for hierarchy display.
 * EditorGameObject children are already HierarchyItems.
 */
@Override
public List<? extends HierarchyItem> getHierarchyChildren() {
    return getChildren();  // List<EditorGameObject> is List<? extends HierarchyItem>
}

// isEditable() uses default implementation: returns isEditor() which is true
// hasHierarchyChildren() uses default implementation: checks getHierarchyChildren().isEmpty()
```

**Note**: Since `EditorGameObject` already implements `IGameObject` (from the igameobject-interface plan), and `HierarchyItem extends IGameObject`, we only need to:
1. Change the implements clause
2. Add `getHierarchyChildren()` method
3. Import `HierarchyItem`

---

### Phase 2: PlayModeController Integration

#### 5. Update `PlayModeController`
**File:** `src/main/java/com/pocket/rpg/editor/PlayModeController.java`

Add field and getter:
```java
@Getter
private PlayModeSelectionManager selectionManager;
```

Add method to expose runtime scene:
```java
public Scene getRuntimeScene() {
    return gameLoop != null ? gameLoop.getSceneManager().getCurrentScene() : null;
}
```

In `play()` method, after creating runtime systems:
```java
selectionManager = new PlayModeSelectionManager();
```

In `cleanup()` method:
```java
if (selectionManager != null) {
    selectionManager.clearSelection();
    selectionManager = null;
}

// Clear adapter cache to release references to destroyed GameObjects
RuntimeGameObjectAdapter.clearCache();
```

#### 6. Wire up in `EditorUIController`
**File:** `src/main/java/com/pocket/rpg/editor/ui/EditorUIController.java`

Pass `PlayModeController` reference to `HierarchyPanel` and `InspectorPanel`:
```java
hierarchyPanel.setPlayModeController(playModeController);
inspectorPanel.setPlayModeController(playModeController);
```

---

### Phase 3: Hierarchy Panel Updates

#### 7. Update `HierarchyPanel`
**File:** `src/main/java/com/pocket/rpg/editor/panels/HierarchyPanel.java`

Add field and helper:
```java
@Setter
private PlayModeController playModeController;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}
```

Modify `render()` method:
```java
public void render() {
    if (ImGui.begin("Hierarchy")) {
        if (isPlayMode()) {
            renderPlayModeHeader();
            renderRuntimeHierarchy();
        } else {
            // Existing editor hierarchy code
            renderEditorHierarchy();
        }
    }
    ImGui.end();
}
```

Add play mode header:
```java
private void renderPlayModeHeader() {
    ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.6f, 0.2f, 1f);
    ImGui.text(MaterialIcons.PlayArrow + " PLAY MODE");
    ImGui.popStyleColor();
    ImGui.separator();
}
```

Add runtime hierarchy rendering:
```java
private void renderRuntimeHierarchy() {
    Scene runtimeScene = playModeController.getRuntimeScene();
    if (runtimeScene == null) {
        ImGui.textDisabled("No runtime scene");
        return;
    }

    List<GameObject> rootObjects = runtimeScene.getRootGameObjects();
    if (rootObjects.isEmpty()) {
        ImGui.textDisabled("No entities");
    } else {
        for (GameObject obj : rootObjects) {
            HierarchyItem adapter = RuntimeGameObjectAdapter.of(obj);
            treeRenderer.renderHierarchyItemTree(adapter);
        }
    }
}
```

Move existing hierarchy code to `renderEditorHierarchy()`.

#### 8. Update `HierarchyTreeRenderer`
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyTreeRenderer.java`

Add generic method for rendering any `HierarchyItem`:
```java
/**
 * Renders a HierarchyItem and its children as a tree.
 * Works for both EditorGameObject (editor mode) and RuntimeGameObjectAdapter (play mode).
 */
public void renderHierarchyItemTree(HierarchyItem item) {
    int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;

    if (!item.hasHierarchyChildren()) {
        flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
    }

    // Check selection based on context
    if (item.isRuntime()) {
        if (isRuntimeItemSelected(item)) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
    } else {
        // Editor selection check (existing code path)
        if (isEditorItemSelected((EditorGameObject) item)) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
    }

    String icon = IconUtils.getGenericEntityIcon();
    // Use IGameObject methods: getName() for display, getId() for ImGui ID
    boolean open = ImGui.treeNodeEx(item.getId(), flags, icon + " " + item.getName());

    // Handle click
    if (ImGui.isItemClicked()) {
        if (item.isRuntime()) {
            handlePlayModeSelection(item);
        } else {
            handleEditorSelection((EditorGameObject) item);
        }
    }

    // Context menu and drag-drop only for editable items
    if (item.isEditable()) {
        renderContextMenu((EditorGameObject) item);
        handleDragDrop((EditorGameObject) item);
    }

    if (open && item.hasHierarchyChildren()) {
        for (HierarchyItem child : item.getHierarchyChildren()) {
            renderHierarchyItemTree(child);
        }
        ImGui.treePop();
    }
}

private boolean isRuntimeItemSelected(HierarchyItem item) {
    if (!(item instanceof RuntimeGameObjectAdapter adapter)) return false;
    return playModeSelectionManager != null &&
           playModeSelectionManager.isSelected(adapter.getGameObject());
}

private void handlePlayModeSelection(HierarchyItem item) {
    if (!(item instanceof RuntimeGameObjectAdapter adapter)) return;
    if (playModeSelectionManager == null) return;

    boolean addToSelection = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
    if (addToSelection) {
        playModeSelectionManager.toggleSelection(adapter.getGameObject());
    } else {
        playModeSelectionManager.select(adapter.getGameObject());
    }
}
```

---

### Phase 4: Inspector Panel Updates

#### 9. Update `InspectorPanel`
**File:** `src/main/java/com/pocket/rpg/editor/panels/InspectorPanel.java`

Add field:
```java
@Setter
private PlayModeController playModeController;

private boolean isPlayMode() {
    return playModeController != null && playModeController.isActive();
}
```

Modify render logic to check play mode first:
```java
@Override
protected void renderContent() {
    if (isPlayMode()) {
        renderPlayModeInspector();
        return;
    }

    // Existing editor inspector logic...
}

private void renderPlayModeInspector() {
    PlayModeSelectionManager selMgr = playModeController.getSelectionManager();
    if (selMgr == null) return;

    Set<GameObject> selected = selMgr.getSelectedObjects();
    if (selected.isEmpty()) {
        ImGui.textDisabled("Select an entity in the hierarchy");
        return;
    }

    if (selected.size() > 1) {
        ImGui.text(selected.size() + " objects selected");
        ImGui.textDisabled("Multi-selection editing not supported in play mode");
        return;
    }

    // Pass as IGameObject - renderRuntime accepts the interface
    IGameObject obj = selected.iterator().next();
    entityInspector.renderRuntime(obj);
}
```

#### 10. Update `EntityInspector`
**File:** `src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java`

Add runtime rendering method that works with `IGameObject`:
```java
/**
 * Renders inspector for a runtime game object (during play mode).
 * Uses IGameObject interface methods for compatibility.
 */
public void renderRuntime(IGameObject gameObject) {
    // Header with name (read-only display)
    String icon = MaterialIcons.Cube;
    ImGui.text(icon);
    ImGui.sameLine();
    ImGui.text(gameObject.getName());

    // Enabled state indicator
    ImGui.sameLine();
    if (gameObject.isEnabled()) {
        ImGui.textColored(0.4f, 0.8f, 0.4f, 1f, "(enabled)");
    } else {
        ImGui.textColored(0.8f, 0.4f, 0.4f, 1f, "(disabled)");
    }

    ImGui.separator();

    // Render components using IGameObject.getAllComponents()
    List<Component> components = gameObject.getAllComponents();
    for (int i = 0; i < components.size(); i++) {
        Component comp = components.get(i);
        ImGui.pushID(i);

        String label = comp.getClass().getSimpleName();
        boolean open = ImGui.collapsingHeader(label, ImGuiTreeNodeFlags.DefaultOpen);

        if (open) {
            if (fieldEditor.renderRuntimeComponentFields(comp)) {
                // Value changed - no scene.markDirty() needed, changes are temporary
            }
        }

        ImGui.popID();
    }

    // Note about temporary changes
    ImGui.separator();
    ImGui.textDisabled("Changes reset when play mode stops");
}
```

#### 11. Update `ComponentFieldEditor`
**File:** `src/main/java/com/pocket/rpg/editor/panels/inspector/ComponentFieldEditor.java`

Add runtime-specific method:
```java
/**
 * Renders component fields for runtime editing (no undo, no prefab overrides).
 */
public boolean renderRuntimeComponentFields(Component component) {
    ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
    if (meta == null) {
        ImGui.textDisabled("Unknown component type");
        return false;
    }

    boolean changed = false;

    for (FieldMeta fieldMeta : meta.fields()) {
        // Create a runtime-specific context (no prefab, no undo)
        FieldEditorContext ctx = FieldEditorContext.forRuntime(component, fieldMeta);

        if (ReflectionFieldEditor.renderField(ctx)) {
            changed = true;
        }
    }

    return changed;
}
```

Also update `FieldEditorContext` to support runtime mode:
```java
public static FieldEditorContext forRuntime(Component component, FieldMeta fieldMeta) {
    return new FieldEditorContext(
        component,
        fieldMeta,
        null,   // no entity
        false,  // not prefab
        true    // isPlayMode - skips undo registration
    );
}
```

---

### Phase 5: Polish

#### 12. Visual Indicators

**Hierarchy Panel:**
- Orange "PLAY MODE" header when active
- Different selection color for runtime objects

**Inspector Panel:**
- Show "Changes reset when play mode stops" notice
- Possibly use orange accent color for field labels

#### 13. Edge Cases to Handle

1. **Scene transitions during play** - If the game loads a different scene, hierarchy should update
   - Solution: Check `getRuntimeScene()` each frame, clear selection if scene changed

2. **Paused state** - Still show hierarchy and allow inspection
   - Already handled: `isActive()` returns true for both PLAYING and PAUSED

3. **GameObjects destroyed during play** - Selection may become invalid
   - Solution: Check if selected object still exists before rendering inspector

---

## Files Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `editor/panels/hierarchy/HierarchyItem.java` | NEW | Interface extending IGameObject for hierarchy display |
| `editor/scene/RuntimeGameObjectAdapter.java` | NEW | Wraps GameObject, implements HierarchyItem |
| `editor/PlayModeSelectionManager.java` | NEW | Tracks runtime selection |
| `editor/scene/EditorGameObject.java` | MODIFY | Change to `implements HierarchyItem`, add `getHierarchyChildren()` |
| `editor/PlayModeController.java` | MODIFY | Add selection manager, getRuntimeScene(), clear adapter cache on stop |
| `editor/ui/EditorUIController.java` | MODIFY | Wire PlayModeController to panels |
| `editor/panels/HierarchyPanel.java` | MODIFY | Add play mode detection and runtime rendering |
| `editor/panels/hierarchy/HierarchyTreeRenderer.java` | MODIFY | Unified `renderHierarchyItemTree(HierarchyItem)` method |
| `editor/panels/InspectorPanel.java` | MODIFY | Add play mode detection and routing |
| `editor/panels/inspector/EntityInspector.java` | MODIFY | Add renderRuntime() method |
| `editor/panels/inspector/ComponentFieldEditor.java` | MODIFY | Add runtime mode (skip undo) |
| `editor/ui/fields/FieldEditorContext.java` | MODIFY | Add forRuntime() factory method |

**Note:** `HierarchySelectionHandler` removed from list - selection logic moved to `HierarchyTreeRenderer` for simplicity.

---

## Verification Checklist

- [ ] Start play mode → Hierarchy shows runtime GameObjects with orange "PLAY MODE" header
- [ ] Click entity in hierarchy → Entity is selected (visual feedback)
- [ ] Inspector shows selected entity's components
- [ ] Edit a component value (e.g., position) → Change applies immediately to running game
- [ ] Stop play mode → Hierarchy reverts to editor scene
- [ ] Verify edited values reset to original after stopping play mode
- [ ] Cannot drag-drop entities during play mode
- [ ] Cannot rename entities during play mode
- [ ] Cannot delete entities during play mode
- [ ] Cannot add/remove components during play mode
- [ ] Pausing shows hierarchy normally (just game updates paused)
- [ ] Editor scene is completely unmodified after play/stop cycle

---

## Code Review

After implementation, create a review document at `Documents/Plans/play-mode-inspection/review.md` covering:
- Code quality and adherence to existing patterns
- Proper cleanup of runtime state
- Edge case handling
- Performance considerations (adapter caching)
