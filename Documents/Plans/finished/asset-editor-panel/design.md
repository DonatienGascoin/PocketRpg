# Generic Asset Editor Panel + Preview-Only Inspector

## Context

The inspector panel doubles as both a preview surface and an editing surface for certain asset types (dialogue variables, dialogue events, sprite metadata). This creates friction:

- **Dual undo stacks**: Scene edits use `UndoManager` (global), asset edits use per-renderer snapshot stacks. Ctrl+Z routes to the wrong one depending on panel focus.
- **No Ctrl+S for assets**: `FILE_SAVE` always saves the scene. Asset save requires clicking a button.
- **Undo lost on deselect**: Asset renderer undo stacks clear in `onDeselect()`.
- **Doesn't scale**: Pokemon species, trainer data, shop info, and more asset types are coming.

Additionally, panels like `PokedexEditorPanel` that use `PrimitiveEditors`/`EnumEditor` getter/setter overloads suffer a **double-undo bug**: field editors unconditionally push `SetterUndoCommand` to the global `UndoManager`, while the panel also maintains its own snapshot-based undo stack.

## Approach

1. **Add target stack redirection to `UndoManager`** — panels provide their own undo/redo deques, and UndoManager operates on those during rendering and undo/redo operations. Field editors work unchanged — they push to whatever stack is currently active.
2. **Create a generic Asset Editor Panel** (dockable, animator-style) that can edit any saveable asset via reflection-based field discovery.
3. **Make the inspector preview-only** for all assets — preview + "Open in Editor" button.
4. **Custom panels** (Animator, Animation, Sprite, Dialogue) remain for complex workflows. The generic editor offers an "Open in [Custom Editor]" button.

---

## Phase 1: UndoManager Target Stack Redirection

### 1.1 Add `pushTarget` / `popTarget` to `UndoManager`

**Modify:** `src/main/java/com/pocket/rpg/editor/undo/UndoManager.java`

Add a target override stack (supports nesting for safety):

```java
private final Deque<TargetOverride> targetStack = new ArrayDeque<>();

record TargetOverride(
    Deque<EditorCommand> undoStack,
    Deque<EditorCommand> redoStack,
    EditorCommand savedLastCommand,
    long savedLastCommandTime
) {}

public void pushTarget(Deque<EditorCommand> undo, Deque<EditorCommand> redo) {
    targetStack.push(new TargetOverride(undo, redo, lastCommand, lastCommandTime));
    lastCommand = null;  // reset merge chain for the new target
    lastCommandTime = 0;
}

public void popTarget() {
    TargetOverride override = targetStack.pop();
    lastCommand = override.savedLastCommand();
    lastCommandTime = override.savedLastCommandTime();
}
```

Add active stack resolution:

```java
private Deque<EditorCommand> activeUndoStack() {
    return targetStack.isEmpty() ? undoStack : targetStack.peek().undoStack();
}

private Deque<EditorCommand> activeRedoStack() {
    return targetStack.isEmpty() ? redoStack : targetStack.peek().redoStack();
}
```

Change all methods (`execute`, `push`, `undo`, `redo`, `canUndo`, `canRedo`, `getUndoDescription`, `getRedoDescription`, `getUndoCount`, `getRedoCount`, `clear`) to use `activeUndoStack()` / `activeRedoStack()` instead of direct field access.

The `pushScope()` / `popScope()` methods continue operating on the internal `undoStack`/`redoStack` fields directly (scene-level isolation for prefab-edit mode, unaffected by target redirection).

### 1.2 Create `SnapshotCommand<T>`

**New file:** `src/main/java/com/pocket/rpg/editor/undo/commands/SnapshotCommand.java`

A generic `EditorCommand` that captures/restores full state via a deep-copy function:

```java
public class SnapshotCommand<T> implements EditorCommand {
    private final T target;
    private final Object beforeSnapshot;
    private final Object afterSnapshot;
    private final BiConsumer<T, Object> restorer;
    // execute() restores afterSnapshot, undo() restores beforeSnapshot
}
```

This wraps the existing snapshot-based undo pattern (used by all panels) as an `EditorCommand` so it can live in UndoManager's stack.

---

## Phase 2: Migrate Existing Panels to `pushTarget`

All existing editor panels currently manage their own `Deque<SnapshotType>` stacks independently from UndoManager. Migrate them to use `pushTarget` so all undo flows through UndoManager's infrastructure.

### Migration pattern

For each panel:

1. Replace custom `Deque<SnapshotType>` undo/redo stacks with `Deque<EditorCommand>` stacks
2. Wrap field rendering in `render()` with `UndoManager.pushTarget(panelUndoStack, panelRedoStack)` / `popTarget()`
3. Replace `captureUndoState()` calls with `UndoManager.push(new SnapshotCommand(...))` — since the target is redirected, this goes to the panel's stack
4. For panels using `PrimitiveEditors`/`EnumEditor` getter/setter overloads: remove the `captureUndo` lambda from the setter callback (the field editors now push `SetterUndoCommand` to the panel's stack automatically via the redirect). For complex mutations (add/remove/reorder), continue using `SnapshotCommand`.
5. Simplify Ctrl+Z/S handlers: `pushTarget` → `UndoManager.undo()` / `redo()` → `popTarget`
6. Remove panel-specific snapshot classes where they become unused

### 2.1 Migrate `PokedexEditorPanel`

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/PokedexEditorPanel.java`

This panel uses `PrimitiveEditors`/`EnumEditor` for most fields (currently double-undo bug). Migration:
- Remove `PokedexSnapshot`, custom deques, `captureUndoState()` from setter lambdas
- Field editors' `SetterUndoCommand` handles field-level undo automatically
- Use `SnapshotCommand` only for structural mutations (add/remove species/moves, learnset changes)

### 2.2 Migrate `DialogueEditorPanel`

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/DialogueEditorPanel.java`

- Replace `DialogueSnapshot` deques with `Deque<EditorCommand>`
- Use `SnapshotCommand` for dialogue mutations (add/remove lines, choices, etc.)
- Remove custom `undo()` / `redo()` methods — delegate to `UndoManager.undo()` with pushTarget

### 2.3 Migrate `AnimatorEditorPanel`

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/AnimatorEditorPanel.java`

- Replace `ControllerState` deques with `Deque<EditorCommand>`
- Use `SnapshotCommand` for graph mutations (add/remove states, transitions, etc.)

### 2.4 Migrate `AnimationEditorPanel`

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/AnimationEditorPanel.java`

- Replace `AnimationState` deques with `Deque<EditorCommand>`
- Use `SnapshotCommand` for frame mutations (add/remove/reorder frames)

---

## Phase 3: Reflection-Based Asset Field Discovery

### 3.1 Create `AssetFieldCollector`

**New file:** `src/main/java/com/pocket/rpg/editor/ui/fields/AssetFieldCollector.java`

Discovers editable fields on any POJO via reflection. Same conventions as `ComponentRegistry.collectFields()` but without Component-specific exclusions:

- Walk the class hierarchy (stop at `Object`)
- Include non-static, non-transient fields
- Exclude fields annotated `@HideInInspector`
- Return `List<FieldMeta>` (reuse existing class from `com.pocket.rpg.serialization`)
- Cache results per class (fields don't change at runtime)

### 3.2 Create `ReflectionAssetEditor`

**New file:** `src/main/java/com/pocket/rpg/editor/ui/fields/ReflectionAssetEditor.java`

Renders all discovered fields for an arbitrary object using the existing getter/setter field editor overloads. Core method:

```java
public static boolean drawObject(Object target, List<FieldMeta> fields, String idPrefix)
```

Type dispatch (mirrors `ReflectionFieldEditor.drawFieldInternal` but uses getter/setter overloads with reflection-based get/set):

| Field Type | Editor |
|---|---|
| `int`, `Integer` | `PrimitiveEditors.drawInt(label, key, getter, setter)` |
| `float`, `Float` | `PrimitiveEditors.drawFloat(label, key, getter, setter, 0.1f)` |
| `boolean`, `Boolean` | `PrimitiveEditors.drawBoolean(label, key, getter, setter)` |
| `String` | `PrimitiveEditors.drawString(label, key, getter, setter)` |
| Enums | `EnumEditor.drawEnum(label, key, getter, setter, enumClass)` |
| `List<T>` (primitives/strings/enums) | List editor with add/remove/reorder |
| `List<T>` (objects) | Collapsible list — each element rendered recursively via `drawObject()` |
| Unknown types | Read-only display (`toString()`) |

Returns `true` if any field was modified. Getters/setters use `Field.get()` / `Field.set()` via reflection.

Since `UndoManager` is redirected to the panel's stacks during rendering, the field editors' built-in undo tracking pushes to the right place automatically.

### 3.3 Add `canSave()` to `AssetLoader`

**Modify:** `src/main/java/com/pocket/rpg/resources/AssetLoader.java`

```java
default boolean canSave() { return false; }
```

**Modify loaders** that implement `save()` — override `canSave()` to return `true`:
- `DialogueLoader`, `DialogueVariablesLoader`, `DialogueEventsLoader`
- `AnimationLoader`, `AnimatorControllerLoader`
- `PokedexLoader`

**Modify:** `src/main/java/com/pocket/rpg/resources/Assets.java` — Add `public static boolean canSave(Class<?> type)`.

---

## Phase 4: Asset Editor Panel

### 4.1 Add `ASSET_EDITOR` to `EditorPanelType`

**Modify:** `src/main/java/com/pocket/rpg/editor/EditorPanelType.java` — Add `ASSET_EDITOR("Asset Editor")`.

### 4.2 Add panel ID

**Modify:** `src/main/java/com/pocket/rpg/editor/shortcut/EditorShortcuts.java` — Add `ASSET_EDITOR = "assetEditor"` to `PanelIds`.

### 4.3 Create `AssetEditorPanel`

**New file:** `src/main/java/com/pocket/rpg/editor/panels/AssetEditorPanel.java`

Extends `EditorPanel`. Follows the established dockable panel pattern (like `DialogueEditorPanel`):

**Layout:**
- **Toolbar:** Hamburger menu toggle (hidden by default) | asset name | Save button | Undo/Redo buttons | "Open in [Custom Editor]" button if applicable
- **Left column (hidden by default):** Type filter dropdown (lists all asset types where `canSave()` is true) + scrollable list of assets of the selected type, with search filter. Toggled via the hamburger icon in the toolbar. Useful for browsing without switching to the Asset Browser panel.
- **Main area:** Scrollable field editor using `ReflectionAssetEditor.drawObject()`. Takes full width when the left column is hidden.

**Undo/redo via UndoManager target redirection:**
- Panel owns `Deque<EditorCommand> panelUndoStack` and `panelRedoStack`
- In `render()`: `UndoManager.pushTarget(panelUndoStack, panelRedoStack)` before rendering fields, `popTarget()` after
- Ctrl+Z/Ctrl+Shift+Z handlers: wrap `UndoManager.undo()`/`redo()` with `pushTarget`/`popTarget`
- Dirty tracking: check `panelUndoStack` size or track via flag

**Save:**
- Calls the asset's loader `save()` method via `Assets.save(path, asset)` (may need a new static helper), then `Assets.reload()` to update the cache
- Clears the panel's undo/redo stacks after save

**Shortcuts (panel-scoped via `panelShortcut()`):**
- `Ctrl+S` → save current asset
- `Ctrl+Z` → undo
- `Ctrl+Shift+Z` / `Ctrl+Y` → redo

**"Open in Custom Editor" button:**
- Check `Assets.getEditorPanelType(assetType)` — if non-null and not `ASSET_EDITOR`, show button
- Button calls a callback wired by `EditorUIController` to open the correct custom panel

**Public API:**
- `selectAssetByPath(String path)` — opens panel, finds asset, selects it

### 4.4 Wire into `EditorUIController`

**Modify:** `src/main/java/com/pocket/rpg/editor/EditorUIController.java`

- Declare, construct, `initPanel()` the `AssetEditorPanel`
- Call `render()` in `renderPanels()`
- Add to `renderWindowMenu()`
- Register `assetBrowserPanel.registerPanelHandler(EditorPanelType.ASSET_EDITOR, assetEditorPanel::selectAssetByPath)`
- Wire the "open custom editor" callback using the existing `panelHandlers` map

---

## Phase 5: Asset Browser + Inspector Integration

### 5.1 Asset browser double-click routing

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/AssetBrowserPanel.java`

Current logic: check `EditorPanelType` → call handler → else do nothing.

New logic:
1. `Assets.getEditorPanelType(type)` returns custom panel → call that handler (existing behavior)
2. Else if `Assets.canSave(type)` → call `assetEditorPanel.selectAssetByPath(path)` (open in generic editor)
3. Else → do nothing (non-editable asset)

### 5.2 Loaders: set `getEditorPanelType()` for assets without custom panels

Asset types that should open in the generic editor (no custom panel):

**Modify:** `DialogueVariablesLoader` — return `ASSET_EDITOR` from `getEditorPanelType()`, `canSave() = true`
**Modify:** `DialogueEventsLoader` — same

Assets that already have custom panels keep their existing panel type (Dialogue → `DIALOGUE_EDITOR`, Animation → `ANIMATION_EDITOR`, etc.). Double-click opens the custom panel.

### 5.3 Make inspector preview-only

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/inspector/AssetInspectorRenderer.java`

Remove editing methods:
- ~~`hasEditableProperties()`~~, ~~`save()`~~, ~~`hasUnsavedChanges()`~~, ~~`undo()`~~, ~~`redo()`~~, ~~`onDeselect()`~~

Change `render()` return type to `void`.

Keep: `render(T asset, String assetPath, float maxPreviewSize)` + `getAssetType()`.

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/inspector/AssetInspector.java`

Remove: save button, unsaved changes popup, all pending-switch state, `checkUnsavedChangesBeforeLeaving()`, `hasPendingPopup()`.

Add: generic "Open in Editor" button after the preview. Uses `Assets.getEditorPanelType(assetType)` to determine target panel. Publishes event / calls callback to open the correct panel (generic or custom).

`setAsset()` becomes an immediate switch (no guards).

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/inspector/AssetInspectorRegistry.java`

Remove: `currentInspector`/`currentAssetType` tracking, `undo()`/`redo()`/`save()`/`hasUnsavedChanges()`/`notifyDeselect()` delegation.

Remove registrations for `DialogueVariablesInspectorRenderer` and `DialogueEventsInspectorRenderer`.

### 5.4 Simplify concrete inspector renderers

**Modify:** `SpriteInspectorRenderer` — Remove PPU input, tileset checkbox, save logic. Keep preview with grid overlay. Remove "Open Sprite Editor" button (handled generically by AssetInspector).

**Modify:** `AnimationInspectorRenderer` — Remove loop toggle mutation. Keep animated preview + playback controls. Remove "Open Animation Editor" button.

**Delete:** `DialogueVariablesInspectorRenderer`, `DialogueEventsInspectorRenderer`.

### 5.5 Simplify `InspectorPanel` undo routing

**Modify:** `src/main/java/com/pocket/rpg/editor/panels/InspectorPanel.java`

Remove: `isShowingAssetInspector()`, `wasShowingAssetInspector`, asset-specific branches in `handleUndo()`/`handleRedo()`, unsaved-changes transition guard in `renderEditorInspector()`.

Inspector shortcuts now always route to `UndoManager` (scene undo).

---

## Phase 6: Cleanup and Review

- [ ] Remove dead imports and unused methods across all modified files
- [ ] Verify no other code references removed `AssetInspectorRenderer` methods
- [ ] Run `mvn test`
- [ ] Code review

---

## Files Summary

| File | Change |
|------|--------|
| **Phase 1** | |
| `editor/undo/UndoManager.java` | Add `pushTarget`/`popTarget` with active stack resolution |
| `editor/undo/commands/SnapshotCommand.java` | **NEW** — Generic snapshot-based EditorCommand |
| **Phase 2** | |
| `editor/panels/PokedexEditorPanel.java` | Replace snapshot undo with UndoManager target redirection |
| `editor/panels/DialogueEditorPanel.java` | Migrate to pushTarget + SnapshotCommand |
| `editor/panels/AnimatorEditorPanel.java` | Migrate to pushTarget + SnapshotCommand |
| `editor/panels/AnimationEditorPanel.java` | Migrate to pushTarget + SnapshotCommand |
| **Phase 3** | |
| `editor/ui/fields/AssetFieldCollector.java` | **NEW** — Reflection-based field discovery for any POJO |
| `editor/ui/fields/ReflectionAssetEditor.java` | **NEW** — Renders fields for arbitrary objects using existing field editors |
| `resources/AssetLoader.java` | Add `default boolean canSave()` |
| `resources/Assets.java` | Add `canSave(Class<?>)` static method |
| Various loaders | Override `canSave()` to return `true` |
| **Phase 4** | |
| `editor/EditorPanelType.java` | Add `ASSET_EDITOR` |
| `editor/shortcut/EditorShortcuts.java` | Add `ASSET_EDITOR` panel ID |
| `editor/panels/AssetEditorPanel.java` | **NEW** — Generic dockable asset editor panel |
| `editor/EditorUIController.java` | Wire AssetEditorPanel |
| **Phase 5** | |
| `editor/panels/AssetBrowserPanel.java` | Double-click routing: custom panel → generic editor → nothing |
| `resources/loaders/DialogueVariablesLoader.java` | `getEditorPanelType() = ASSET_EDITOR`, `canSave() = true` |
| `resources/loaders/DialogueEventsLoader.java` | Same |
| `editor/panels/inspector/AssetInspectorRenderer.java` | Remove editing methods, `render()` returns void |
| `editor/panels/inspector/AssetInspector.java` | Remove save/popup, add generic "Open in Editor" button |
| `editor/panels/inspector/AssetInspectorRegistry.java` | Remove undo/save delegation, remove vars/events registrations |
| `editor/panels/inspector/SpriteInspectorRenderer.java` | Remove editing fields, keep preview |
| `editor/panels/inspector/AnimationInspectorRenderer.java` | Remove loop toggle, keep preview |
| `editor/panels/inspector/DialogueVariablesInspectorRenderer.java` | **DELETE** |
| `editor/panels/inspector/DialogueEventsInspectorRenderer.java` | **DELETE** |
| `editor/panels/InspectorPanel.java` | Remove asset undo routing, unsaved-changes guard |

## Verification

1. **UndoManager redirection**: Edit a species field in PokedexEditorPanel → Ctrl+Z undoes it → switch to scene viewport → Ctrl+Z does NOT undo the species edit (no orphaned commands in scene stack)
2. **Panel migration**: For each migrated panel (Pokedex, Dialogue, Animator, Animation) — edit → undo → redo → save → verify all work correctly. Verify no double-undo or orphaned commands.
3. **Generic asset editor — simple asset**: Double-click `variables.dialogue-vars.json` → Asset Editor opens → fields editable via reflection → Ctrl+Z/Ctrl+S work → persistence verified on reopen
4. **Generic asset editor — complex asset**: Open a Pokedex in the generic editor → simple fields editable, nested structures visible → "Open in Pokedex Editor" button works
5. **Custom panels unchanged**: Double-click `.dialogue.json` → Dialogue Editor. Double-click `.animator.json` → Animator Editor. Existing workflows preserved.
6. **Inspector preview-only**: Select any asset → preview + "Open in Editor" → no editing, no save button, no unsaved popup
7. **Undo routing clean**: Ctrl+Z in inspector always = scene undo. Ctrl+Z in any editor panel = that panel's undo. No cross-contamination.
8. **`mvn test` passes**
