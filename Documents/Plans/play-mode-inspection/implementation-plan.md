# Play Mode Hierarchy & Inspector Support

## Overview

Enable the hierarchy and inspector panels to display and interact with runtime GameObjects during play mode. Changes made during play mode affect only the running game and reset when play stops (Unity-style behavior).

## Current State

- **EditorScene** with **EditorGameObjects** is frozen at play start, restored when play stops
- **RuntimeScene** with **GameObjects** runs during play, destroyed when play stops
- Hierarchy/Inspector panels always render but only show frozen EditorScene state
- No way to see or interact with runtime entities

## Design Approach

Use a **Mode-Aware Adapter Pattern**:
1. Create a `HierarchyItem` interface that abstracts the common display needs
2. Create a `RuntimeGameObjectAdapter` that wraps runtime `GameObject`s
3. Add a `PlayModeSelectionManager` for runtime selection (separate from editor selection)
4. Modify panels to detect play mode and switch data sources

---

## Implementation

### Phase 1: Core Infrastructure

#### 1. `HierarchyItem` Interface (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyItem.java`

```java
public interface HierarchyItem {
    String getDisplayName();
    String getUniqueId();
    List<? extends HierarchyItem> getHierarchyChildren();
    boolean hasHierarchyChildren();
    List<Component> getInspectableComponents();
    boolean isEditable();  // false during play mode
}
```

#### 2. `RuntimeGameObjectAdapter` (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/scene/RuntimeGameObjectAdapter.java`

Wraps a runtime `GameObject` to implement `HierarchyItem`:
- `getDisplayName()` → `gameObject.getName()`
- `getUniqueId()` → generate stable ID from object identity (e.g., `"runtime_" + System.identityHashCode(gameObject)`)
- `getHierarchyChildren()` → wrap children in adapters (cache adapters to avoid recreation)
- `getInspectableComponents()` → `gameObject.getAllComponents()`
- `isEditable()` → `false`

Also store reference to the wrapped `GameObject` for selection purposes.

#### 3. `PlayModeSelectionManager` (NEW)
**File:** `src/main/java/com/pocket/rpg/editor/PlayModeSelectionManager.java`

```java
public class PlayModeSelectionManager {
    private final Set<GameObject> selectedObjects = new LinkedHashSet<>();

    public void select(GameObject obj);
    public void addToSelection(GameObject obj);
    public void toggleSelection(GameObject obj);
    public void clearSelection();
    public Set<GameObject> getSelectedObjects();
    public GameObject getSingleSelected();
    public boolean isSelected(GameObject obj);
}
```

#### 4. Make `EditorGameObject` implement `HierarchyItem`
**File:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

Add `implements HierarchyItem` to class declaration and implement methods:
- `getDisplayName()` → `getName()`
- `getUniqueId()` → `getId()`
- `getHierarchyChildren()` → `getChildren()` (already returns `List<EditorGameObject>`)
- `hasHierarchyChildren()` → `hasChildren()` (already exists)
- `getInspectableComponents()` → `getComponents()` (already exists)
- `isEditable()` → `return true;`

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
            RuntimeGameObjectAdapter adapter = new RuntimeGameObjectAdapter(obj);
            treeRenderer.renderHierarchyItemTree(adapter, true);
        }
    }
}
```

Move existing hierarchy code to `renderEditorHierarchy()`.

#### 8. Update `HierarchyTreeRenderer`
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyTreeRenderer.java`

Add generic method for rendering any `HierarchyItem`:
```java
public void renderHierarchyItemTree(HierarchyItem item, boolean isPlayMode) {
    int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;

    if (!item.hasHierarchyChildren()) {
        flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
    }

    // Check selection (different for play mode vs editor)
    if (isPlayMode) {
        if (isRuntimeItemSelected(item)) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
    }

    String icon = IconUtils.getGenericEntityIcon();
    boolean open = ImGui.treeNodeEx(item.getUniqueId(), flags, icon + " " + item.getDisplayName());

    // Handle click - select in appropriate selection manager
    if (ImGui.isItemClicked()) {
        handlePlayModeSelection(item);
    }

    // NO context menu during play mode
    // NO drag-drop during play mode

    if (open && item.hasHierarchyChildren()) {
        for (HierarchyItem child : item.getHierarchyChildren()) {
            renderHierarchyItemTree(child, isPlayMode);
        }
        ImGui.treePop();
    }
}
```

#### 9. Update `HierarchySelectionHandler`
**File:** `src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchySelectionHandler.java`

Add runtime selection support:
```java
@Setter
private PlayModeSelectionManager playModeSelectionManager;

public void selectRuntimeObject(GameObject obj, boolean addToSelection) {
    if (playModeSelectionManager == null) return;

    if (addToSelection) {
        playModeSelectionManager.addToSelection(obj);
    } else {
        playModeSelectionManager.select(obj);
    }
}

public boolean isRuntimeObjectSelected(GameObject obj) {
    return playModeSelectionManager != null && playModeSelectionManager.isSelected(obj);
}
```

---

### Phase 4: Inspector Panel Updates

#### 10. Update `InspectorPanel`
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

    GameObject obj = selected.iterator().next();
    entityInspector.renderRuntime(obj);
}
```

#### 11. Update `EntityInspector`
**File:** `src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java`

Add runtime rendering method:
```java
public void renderRuntime(GameObject gameObject) {
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

    // Render components
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

#### 12. Update `ComponentFieldEditor`
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

#### 13. Visual Indicators

**Hierarchy Panel:**
- Orange "PLAY MODE" header when active
- Different selection color for runtime objects

**Inspector Panel:**
- Show "Changes reset when play mode stops" notice
- Possibly use orange accent color for field labels

#### 14. Edge Cases to Handle

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
| `editor/panels/hierarchy/HierarchyItem.java` | NEW | Interface for hierarchy display abstraction |
| `editor/scene/RuntimeGameObjectAdapter.java` | NEW | Wraps GameObject for hierarchy display |
| `editor/PlayModeSelectionManager.java` | NEW | Tracks runtime selection |
| `editor/scene/EditorGameObject.java` | MODIFY | Implement HierarchyItem interface |
| `editor/PlayModeController.java` | MODIFY | Add selection manager and getRuntimeScene() |
| `editor/ui/EditorUIController.java` | MODIFY | Wire PlayModeController to panels |
| `editor/panels/HierarchyPanel.java` | MODIFY | Add play mode detection and runtime rendering |
| `editor/panels/hierarchy/HierarchyTreeRenderer.java` | MODIFY | Support HierarchyItem generic rendering |
| `editor/panels/hierarchy/HierarchySelectionHandler.java` | MODIFY | Support runtime selection |
| `editor/panels/InspectorPanel.java` | MODIFY | Add play mode detection and routing |
| `editor/panels/inspector/EntityInspector.java` | MODIFY | Add renderRuntime() method |
| `editor/panels/inspector/ComponentFieldEditor.java` | MODIFY | Add runtime mode (skip undo) |
| `editor/ui/fields/FieldEditorContext.java` | MODIFY | Add forRuntime() factory method |

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
