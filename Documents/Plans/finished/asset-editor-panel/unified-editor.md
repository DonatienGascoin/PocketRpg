# Unified Asset Editor Panel

## Context

Six separate editor panels (AssetEditorPanel, DialogueEditorPanel, AnimatorEditorPanel, AnimationEditorPanel, PokedexEditorPanel, SpriteEditorPanel) all duplicate the same boilerplate: toolbar (save/undo/redo), dirty tracking, undo stack management via `UndoManager.pushTarget()`, asset list sidebar, shortcut registration, and `selectByPath()` wiring. The only thing that differs is the main content area.

This plan merges them into one unified `AssetEditorPanel` with a pluggable `AssetEditorContent` interface. Each asset type provides its own content implementation; the shell owns all shared infrastructure.

---

## Class Diagram

```
EditorPanel (abstract base)
    └── AssetEditorPanel (unified shell)
            │
            ├── owns: toolbar, hamburger sidebar, undo stacks, dirty tracking, shortcuts
            │
            ├── has-a ──► AssetEditorContent (interface)
            │                 │
            │                 ├── render()
            │                 ├── onAssetLoaded(path, asset, shell)
            │                 ├── onAssetUnloaded()
            │                 ├── hasCustomSave(): boolean
            │                 ├── customSave(path): void
            │                 ├── renderToolbarExtras(): void
            │                 ├── renderPopups(): void
            │                 ├── provideExtraShortcuts(layout): List<ShortcutAction>
            │                 ├── getAssetAnnotation(path): String  (e.g. warning icons)
            │                 ├── initialize(): void
            │                 ├── destroy(): void
            │                 └── getAssetClass(): Class<?>
            │
            └── has-a ──► AssetEditorContentRegistry
                              maps Class<?> → Supplier<AssetEditorContent>
                              ├── Dialogue.class        → DialogueEditorContent
                              ├── AnimatorController     → AnimatorEditorContent
                              ├── Animation.class        → AnimationEditorContent
                              ├── Pokedex.class          → PokedexEditorContent
                              ├── Sprite.class           → SpriteEditorContent
                              └── (default)              → ReflectionEditorContent


AssetEditorShell (interface — shell API exposed to content)
    ├── markDirty()
    ├── isDirty()
    ├── getEditingPath(): String
    ├── getUndoStack(): Deque<EditorCommand>
    ├── getRedoStack(): Deque<EditorCommand>
    ├── showStatus(message)
    ├── requestSidebarRefresh()
    └── panelShortcut(): ShortcutAction.Builder
```

---

## Panel Layout

The hamburger sidebar is **only** an asset list browser for quick navigation/switching.
Content implementations **keep their own internal layout** (left panels, tabs, sub-columns).
The hamburger does NOT replace content-specific navigation.

### Hamburger sidebar open (example: Dialogue content with its own left column):

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [≡] dialogue_01.dialogue.json *  [Save] [Undo] [Redo] | [Vars] [Events]   │
├─────────┬────────────────────────────────────────────────────────────────────┤
│ Type    │                                                                    │
│[Dial. ▼]│  Content Area (AssetEditorContent.render())                        │
│         │  ┌──────────────┬──────────────────────────────────┐               │
│ Search  │  │ Dialogue's   │                                  │               │
│ [_____] │  │ own left col │    Lines editor                  │               │
│         │  │              │    + choices editor              │               │
│ ◉ dlg_1 │  │ [Search]     │                                  │               │
│   dlg_2 │  │ [+Add][-Del] │                                  │               │
│   dlg_3 │  │              │                                  │               │
│   dlg_4 │  │ dlg_line_a   │                                  │               │
│         │  │ dlg_line_b ⚠ │                                  │               │
│         │  │ dlg_line_c   │                                  │               │
│         │  └──────────────┴──────────────────────────────────┘               │
└─────────┴────────────────────────────────────────────────────────────────────┘
  hamburger          content owns its full internal layout
  (asset list)
```

### Hamburger sidebar closed (default):

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [≡] player_idle.anim.json *  [Save] [Undo] [Redo] | [Play] [Stop] [1.0x]  │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Content Area — full width (e.g., Animation editor)                         │
│  ┌──────────┬───────────────┬──────────────────────┐                        │
│  │Properties│ Current Frame │      Preview         │                        │
│  │          │               │                      │                        │
│  └──────────┴───────────────┴──────────────────────┘                        │
│  ┌──────────────────────────────────────────────────┐                        │
│  │                  Timeline                        │                        │
│  └──────────────────────────────────────────────────┘                        │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Toolbar breakdown:**
- `[≡]` — hamburger toggle (opens/closes left sidebar)
- Asset name + dirty indicator (`*`)
- `[Save]` — warning color when dirty, disabled when clean
- `[Undo]` `[Redo]` — disabled when stacks empty
- `|` separator
- Content-specific extras via `renderToolbarExtras()` (e.g., playback controls for animation, Variables/Events for dialogue)

**Hamburger sidebar contents:**
- Type filter dropdown (filter by asset type: All, Dialogue, Animation, etc.)
- Search box (filters asset list)
- Scrollable asset list (click to open in content area)
- Selecting an asset loads the appropriate content implementation

---

## Migration Path per Panel

### 1. AssetEditorPanel (current generic) → ReflectionEditorContent

**Moves into content:**
- `renderMainContent()` → `render()`
- `ReflectionAssetEditor.drawObject()` call
- `AssetFieldCollector.getFields()` usage
- JSON snapshot for undo

**Gets deleted from shell:**
- `editingFields`, `editingType` fields (moved to content)
- `openCustomEditorCallback` (no longer needed — there's only one panel)

### 2. DialogueEditorPanel → DialogueEditorContent

**Moves into content (the entire two-column layout):**
- Left column: dialogue list with search, add/remove, validation warnings
- Right column: toolbar area (Variables/Events/Refresh buttons) + lines editor + choices editor
- Sub-editors (`DialogueLinesEditor`, `DialogueChoicesEditor`)
- Dialogue-specific snapshot/undo (`DialogueSnapshot`)
- Popups (new dialogue, delete confirm, unsaved changes)

**Handled by shell:**
- Save/undo/redo toolbar buttons and Ctrl+S/Z/Y shortcuts
- `selectDialogueByPath()` → shell's `selectAssetByPath()`
- Dirty indicator in toolbar
- Undo stack management (pushTarget/popTarget)

**Toolbar extras (via renderToolbarExtras):**
- Variables / Events "open in editor" buttons
- Refresh button

**Extra shortcuts kept:**
- `Ctrl+Enter` — Add dialogue line

### 3. AnimatorEditorPanel → AnimatorEditorContent

**Moves into content:**
- Parameters panel + node graph editor
- Preview panel (hamburger-style toggle in toolbar extras)
- Graph editor callbacks
- Inspector selection events (publish state/transition selected)
- Controller-specific snapshot/undo
- Create/delete controller (toolbar extras)
- Layout data save

**Handled by shell:**
- Controller dropdown → hamburger sidebar asset list
- Save/undo/redo toolbar + shortcuts

**Extra shortcuts kept:**
- `Ctrl+N` — New animator
- `F5` — Refresh list

**External wiring preserved:**
- `setSelectionManager()` — wired via content factory or init
- Inspector events via `EditorEventBus`

### 4. AnimationEditorPanel → AnimationEditorContent

**Moves into content:**
- Three-section layout (properties | current frame | preview)
- Timeline (track/strip modes)
- Playback controls (play/stop/speed)
- Frame editing (duration, sprite picker)
- Preview renderer + timeline renderers
- Create/delete animation (toolbar extras)
- Sprite picker popup

**Handled by shell:**
- Animation dropdown → hamburger sidebar asset list
- Save/undo/redo toolbar + shortcuts

**Extra shortcuts kept:**
- `Space` — Play/pause
- Arrow keys — Previous/next frame
- `Home/End` — First/last frame
- `Delete` — Delete frame
- `Ctrl+N` — New animation
- `F5` — Refresh

### 5. PokedexEditorPanel → PokedexEditorContent

**Moves into content:**
- Species/Moves tab bar
- Species list + editor (left sub-column within content)
- Move list + editor
- Learnset editor
- Create/delete species/moves (toolbar extras)
- Pokedex-specific snapshot/undo

**Handled by shell:**
- Pokedex file selector → hamburger sidebar asset list
- Save/undo/redo toolbar + shortcuts

### 6. SpriteEditorPanel (popup) → SpriteEditorContent (dockable)

**Moves into content:**
- Mode toggle (Single/Multiple)
- Tab bar (Slicing/Pivot/NineSlice)
- Preview canvas with zoom/pan
- Sidebar (grid settings, pivot editor, 9-slice editor)
- Local undo history → migrated to shell's UndoManager stacks
- Cancel → becomes "Revert" or just undo

**Handled by shell:**
- Asset selection via sidebar
- Save/undo/redo
- Dockable window (no longer a popup)

**Breaking change:**
- No more "Cancel" button — save or undo instead
- No longer blocks interaction as a modal

---

## EditorUIController Simplification

### Before (current):
```java
// 6 panel declarations
AnimationEditorPanel animationEditorPanel;
SpriteEditorPanel spriteEditorPanel;
AnimatorEditorPanel animatorEditorPanel;
DialogueEditorPanel dialogueEditorPanel;
PokedexEditorPanel pokedexEditorPanel;
AssetEditorPanel assetEditorPanel;

// 6 panel handlers
assetBrowserPanel.registerPanelHandler(ANIMATION_EDITOR, ...);
assetBrowserPanel.registerPanelHandler(SPRITE_EDITOR, ...);
assetBrowserPanel.registerPanelHandler(ANIMATOR_EDITOR, ...);
assetBrowserPanel.registerPanelHandler(DIALOGUE_EDITOR, ...);
assetBrowserPanel.registerPanelHandler(POKEDEX_EDITOR, ...);
assetBrowserPanel.registerPanelHandler(ASSET_EDITOR, ...);

// 6 render calls
animationEditorPanel.render();
animatorEditorPanel.render();
spriteEditorPanel.render();
dialogueEditorPanel.render();
pokedexEditorPanel.render();
assetEditorPanel.render();

// 6 entries in Window menu
```

### After:
```java
// 1 panel declaration
AssetEditorPanel assetEditorPanel;

// 1 panel handler
assetBrowserPanel.registerPanelHandler(ASSET_EDITOR, assetEditorPanel::selectAssetByPath);

// 1 render call
assetEditorPanel.render();

// 1 entry in Window menu
```

### Event handler changes:
- `OpenAnimationEditorEvent` → calls `assetEditorPanel.selectAssetByPath(path)`
- `OpenDialogueEditorEvent` → calls `assetEditorPanel.selectAssetByPath(path)`
- `OpenSpriteEditorEvent` → calls `assetEditorPanel.selectAssetByPath(path)`
- Animator events (state/transition selected) → content registers its own listeners

---

## EditorPanelType Cleanup

### Before:
```java
ANIMATION_EDITOR, SPRITE_EDITOR, ANIMATOR_EDITOR,
PREFAB_EDITOR, DIALOGUE_EDITOR, POKEDEX_EDITOR, ASSET_EDITOR
```

### After:
```java
PREFAB_EDITOR,    // stays — uses PrefabEditController, not asset editor
ASSET_EDITOR      // the unified panel for all asset types
```

All `AssetLoader` subclasses that return a specific panel type → return `ASSET_EDITOR`.
The shell + content registry handle routing to the correct content implementation.

---

## Files to Change

| File | Change |
|------|--------|
| `editor/panels/AssetEditorPanel.java` | **REWRITE** — unified shell with hamburger sidebar |
| `editor/panels/AssetEditorContent.java` | **NEW** — pluggable content interface |
| `editor/panels/AssetEditorShell.java` | **NEW** — shell API interface for content |
| `editor/panels/AssetEditorContentRegistry.java` | **NEW** — maps asset Class → content supplier |
| `editor/panels/content/ReflectionEditorContent.java` | **NEW** — default reflection-based editor |
| `editor/panels/content/DialogueEditorContent.java` | **NEW** — migrated from DialogueEditorPanel |
| `editor/panels/content/AnimatorEditorContent.java` | **NEW** — migrated from AnimatorEditorPanel |
| `editor/panels/content/AnimationEditorContent.java` | **NEW** — migrated from AnimationEditorPanel |
| `editor/panels/content/PokedexEditorContent.java` | **NEW** — migrated from PokedexEditorPanel |
| `editor/panels/content/SpriteEditorContent.java` | **NEW** — migrated from SpriteEditorPanel |
| `editor/panels/DialogueEditorPanel.java` | **DELETE** |
| `editor/panels/AnimatorEditorPanel.java` | **DELETE** |
| `editor/panels/AnimationEditorPanel.java` | **DELETE** |
| `editor/panels/PokedexEditorPanel.java` | **DELETE** |
| `editor/panels/SpriteEditorPanel.java` | **DELETE** |
| `editor/EditorPanelType.java` | Remove 5 enum values |
| `editor/EditorUIController.java` | Remove 5 panel fields, simplify wiring |
| `editor/panels/AssetBrowserPanel.java` | Simplify panelHandlers to single ASSET_EDITOR |
| `editor/shortcut/EditorShortcuts.java` | Remove 4 PanelId constants (keep ASSET_EDITOR) |
| `resources/loaders/*.java` | Return ASSET_EDITOR instead of specific panel types |

---

## Phased Approach

### Phase 1: Core Infrastructure ✅
**Goal:** Create the unified shell and content interface. All existing panels still work.

- [x] Create `AssetEditorContent` interface
- [x] Create `AssetEditorShell` interface
- [x] Create `AssetEditorContentRegistry` with registration API
- [x] Create `ReflectionEditorContent` (extracted from current AssetEditorPanel logic)
- [x] Rewrite `AssetEditorPanel` as the unified shell:
  - Hamburger sidebar (type filter, search, asset list)
  - Toolbar (asset name, dirty indicator, save/undo/redo, content extras)
  - Content area delegation
  - Undo stack management (pushTarget/popTarget around content.render())
  - Shortcut registration (shell + content extras)
- [x] Register `ReflectionEditorContent` as default in registry
- [x] Verify: existing panels still work, new shell handles reflection-based assets

### Phase 2: Migrate Dialogue and Pokedex ✅
**Goal:** Replace the two two-column editors.

- [x] Create `DialogueEditorContent`:
  - Move right-column rendering, sub-editors, snapshots, popups
  - Add toolbar extras (Variables, Events, Refresh, New/Delete buttons)
  - Implement `getAssetAnnotation()` for validation warnings in sidebar
  - Wire `hasCustomSave()` → DialogueLoader save
- [x] Create `PokedexEditorContent`:
  - Move species/moves tabs, editors, learnset
  - Add toolbar extras (New Species/Move, Delete) — kept in left column
  - Wire `hasCustomSave()` → PokedexLoader save
- [x] Register both in `AssetEditorContentRegistry`
- [x] Remove `DialogueEditorPanel` and `PokedexEditorPanel`
- [x] Update `EditorUIController`: remove dialogue/pokedex panel fields and wiring
- [x] Update `EditorPanelType`: remove `DIALOGUE_EDITOR`, `POKEDEX_EDITOR`
- [x] Update loaders to return `ASSET_EDITOR`
- [ ] Verify: dialogues and pokedex editable via unified panel

### Phase 3: Migrate Animation and Animator ✅
**Goal:** Replace the two complex editors.

- [x] Create `AnimatorEditorContent`:
  - Move graph editor, parameters panel, preview panel
  - Move inspector selection event publishing
  - Add toolbar extras (New, Delete, preview toggle, controller info)
  - Wire `hasCustomSave()` → AnimatorControllerLoader save + layout save
  - Wire `initialize()`/`destroy()` for graph editor lifecycle
  - SelectionGuard passed via constructor (captured in registry lambda)
  - Content subscribes to SelectionChangedEvent for clearGraphSelection
- [x] Create `AnimationEditorContent`:
  - Move three-section layout, timeline, playback controls
  - Move frame editing, sprite picker
  - Add toolbar extras (New, Delete, play/stop/speed controls)
  - Wire `hasCustomSave()` → AnimationLoader save
  - Wire `initialize()`/`destroy()` for preview renderer lifecycle
- [x] Register both in `AssetEditorContentRegistry`
- [x] Remove `AnimatorEditorPanel` and `AnimationEditorPanel`
- [x] Update `EditorUIController`: remove animator/animation panel fields and wiring
- [x] Update `EditorPanelType`: remove `ANIMATOR_EDITOR`, `ANIMATION_EDITOR`
- [x] Update loaders to return `ASSET_EDITOR`
- [x] Move animator event subscriptions to content (SelectionChangedEvent) and keep inspector wiring in EditorUIController
- [x] Add `destroy()` to `AssetEditorPanel` for content cleanup on shutdown
- [ ] Verify: animators and animations editable, inspector integration works

### Phase 4: Migrate Sprite Editor
**Goal:** Convert the popup into a dockable content implementation.

- [ ] Create `SpriteEditorContent`:
  - Move mode toggle, tabs, preview canvas, sidebar
  - Adapt local undo to UndoManager target stacks
  - Remove Cancel button (use undo instead)
  - Wire `hasCustomSave()` → metadata save
- [ ] Register in `AssetEditorContentRegistry`
- [ ] Remove `SpriteEditorPanel`
- [ ] Update `AssetBrowserPanel`: remove sprite editor panel reference
- [ ] Update `EditorUIController`: remove sprite editor panel field
- [ ] Update `EditorPanelType`: remove `SPRITE_EDITOR`
- [ ] Update `OpenSpriteEditorEvent` handler → selectAssetByPath
- [ ] Verify: sprites editable, pivot/9-slice work, no popup

### Phase 5: Final Cleanup
**Goal:** Remove all vestiges of the old system.

- [ ] Simplify `AssetBrowserPanel.panelHandlers` to single ASSET_EDITOR entry
- [ ] Remove dead code in `EditorUIController` (old event subscriptions, panel references)
- [ ] Clean up `EditorShortcuts.PanelIds` (remove ANIMATION_EDITOR, ANIMATOR_EDITOR, DIALOGUE_EDITOR, POKEDEX_EDITOR)
- [ ] Remove `InspectorPanel` references to old panels if any
- [ ] Update `EditorMenuBar` Window menu to single "Asset Editor" entry
- [ ] Code review: check for unused imports, dead references
- [ ] Verify: full editor workflow, all asset types open correctly from asset browser

---

## Verification

After each phase:
1. `mvn compile` — must succeed
2. Run editor: `mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"`
3. Test matrix:
   - Double-click each asset type in asset browser → opens in unified panel
   - Ctrl+S saves, Ctrl+Z/Y undo/redo works per-asset
   - Hamburger sidebar: type filter, search, asset list navigation
   - Switch between asset types preserves undo history per asset
   - Dirty indicator shows/clears correctly
   - All content-specific features work (dialogue lines, animator graph, animation timeline, etc.)
   - Inspector integration for animator states/transitions
   - Sprite pivot/9-slice editing (Phase 4+)
