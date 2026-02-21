# Asset Editor Panel Guide

> **Summary:** The unified Asset Editor Panel hosts all asset-type editors (dialogues, animations, sprites, etc.) through pluggable content implementations. Create a custom editor UI by implementing `AssetEditorContent` with `@EditorContentFor` for automatic discovery, or let the default reflection-based editor handle it.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Workflows](#workflows)
6. [Creating Custom Editor Content](#creating-custom-editor-content)
7. [AssetEditorShell API](#asseteditorshell-api)
8. [Asset Preview Renderers](#asset-preview-renderers)
9. [Keyboard Shortcuts](#keyboard-shortcuts)
10. [Tips & Best Practices](#tips--best-practices)
11. [Troubleshooting](#troubleshooting)
12. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Open an asset for editing | Double-click in Asset Browser, or click in sidebar |
| Create custom editor UI | Implement `AssetEditorContent`, add `@EditorContentFor(MyAsset.class)` |
| Use reflection-based editor | Do nothing — it's the automatic fallback for unregistered types |
| Add a preview renderer | Implement `AssetPreviewRenderer<T>`, register in `AssetPreviewRegistry` |
| Create new asset from editor | Press Ctrl+N (if content supports it) |
| Save changes | Ctrl+S or click Save button in toolbar |
| Undo/Redo | Ctrl+Z / Ctrl+Shift+Z (panel-scoped stacks) |

---

## Overview

The Asset Editor Panel is a unified, shell-based architecture for editing any asset type. Instead of separate panels for each asset type, a single `AssetEditorPanel` provides shared infrastructure:

- **Toolbar**: asset name, dirty indicator, Save/Undo/Redo buttons, content-specific extras
- **Hamburger sidebar**: browse and switch between assets, filtered by type
- **Content area**: delegated to a pluggable `AssetEditorContent` implementation
- **Undo/Redo stacks**: panel-scoped via `UndoManager` target redirection
- **Unsaved changes guard**: prompts before switching assets with unsaved edits
- **Asset creation**: centralized `shell.createAsset()` handles naming, collision resolution, and disk I/O

Content implementations own their full internal layout (columns, tabs, sub-panels). The shell only manages the toolbar and sidebar.

**Built-in content implementations:**

| Content | Asset Type | Auto-discovered |
|---------|-----------|-----------------|
| `DialogueEditorContent` | `Dialogue` | Yes (`@EditorContentFor`) |
| `AnimationEditorContent` | `Animation` | Yes |
| `AnimatorEditorContent` | `AnimatorController` | Yes |
| `SpriteEditorContent` | `Sprite` | Yes |
| `PokedexEditorContent` | `Pokedex` | Yes |
| `AudioClipEditorContent` | `AudioClip` | Yes |
| `ReflectionEditorContent` | (any type) | Default fallback |

---

## Opening the Panel

- **Double-click** an asset in the [Asset Browser](assetBrowserGuide.md) whose loader returns `EditorPanelType.ASSET_EDITOR`
- **Click** an asset in the hamburger sidebar (toggle with the hamburger icon in the toolbar)
- The panel opens and focuses automatically

---

## Interface Overview

```
+------------------------------------------------------------------+
| [=] MyDialogue *  |  [Save] [Undo] [Redo] [+ New] [Validate]    |
+----------+-------------------------------------------------------+
| SIDEBAR  | CONTENT AREA                                          |
|          |                                                        |
| All    v |  (Delegated to AssetEditorContent implementation)      |
| [Search] |                                                        |
| ---------|  e.g., DialogueEditorContent shows:                    |
| v dlgs/  |  - Entry list column                                   |
|   intro  |  - Entry editor column                                 |
|  >quest  |  - Choice editor                                       |
|   shop   |  - Validation warnings                                 |
|          |                                                        |
+----------+-------------------------------------------------------+
```

| Element | Description |
|---------|-------------|
| **Hamburger toggle** | Opens/closes the asset sidebar |
| **Asset name + dirty** | Shows current asset name, `*` if unsaved |
| **Save/Undo/Redo** | Standard toolbar controls (panel-scoped undo stacks) |
| **Content extras** | Content-specific toolbar buttons (e.g., New, Validate) |
| **Sidebar type filter** | Dropdown to filter by asset type |
| **Sidebar search** | Filter assets by name |
| **Sidebar tree** | Folder tree of matching assets |
| **Content area** | Full editor UI from the active `AssetEditorContent` |

---

## Workflows

### Editing an Asset

1. Double-click an asset in the Asset Browser
2. The Asset Editor opens with the appropriate content implementation
3. Edit fields — changes are tracked with panel-scoped undo/redo
4. Save with Ctrl+S or the Save button

### Switching Between Assets

1. Open the sidebar (click the hamburger icon)
2. Filter by type or search by name
3. Click an asset to switch to it
4. If the current asset has unsaved changes, a confirmation dialog appears

### Creating a New Asset

1. Open the Asset Editor for the target asset type
2. Press Ctrl+N or click the **New** button in the toolbar
3. Enter a name in the dialog
4. The asset is created on disk and opened for editing

### Using the Reflection-Based Fallback

If no custom `AssetEditorContent` is registered for an asset type, the `ReflectionEditorContent` automatically generates an editor UI by scanning the asset's fields — similar to how the Inspector auto-generates component editors.

---

## Creating Custom Editor Content

### Step 1: Create the Content Class

Implement `AssetEditorContent` and annotate with `@EditorContentFor`:

```java
package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;

@EditorContentFor(QuestLog.class)
public class QuestLogEditorContent implements AssetEditorContent {

    private QuestLog questLog;
    private AssetEditorShell shell;

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.questLog = (QuestLog) asset;
        this.shell = shell;
    }

    @Override
    public void onAssetUnloaded() {
        questLog = null;
    }

    @Override
    public Class<?> getAssetClass() {
        return QuestLog.class;
    }

    @Override
    public void render() {
        if (questLog == null) return;

        // Your ImGui editor UI here
        ImGui.text("Quest Log Editor");
        // ... fields, lists, etc.
    }
}
```

### Step 2: Requirements

Your content class must:

1. **Implement `AssetEditorContent`**
2. **Add `@EditorContentFor(YourAsset.class)`** for auto-discovery
3. **Have a no-arg constructor** (required for reflection-based instantiation)

That's it. The content is automatically discovered and registered at editor startup.

### Step 3: Optional Features

Override these defaults for richer functionality:

```java
// Add toolbar buttons after Save/Undo/Redo
@Override
public void renderToolbarExtras() {
    ImGui.sameLine();
    if (ImGui.button(MaterialIcons.Add + " New Entry")) {
        // Add entry with undo support
    }
}

// Support creating new assets via Ctrl+N
@Override
public AssetCreationInfo getCreationInfo() {
    return new AssetCreationInfo(
        ".questlog.json",     // file extension
        "questlogs/"          // subdirectory under assets/
    );
}

@Override
public void onNewRequested() {
    // Show name input dialog, then:
    QuestLog defaultLog = new QuestLog();
    defaultLog.setName(name);
    shell.createAsset(name, defaultLog);
}

// Custom save logic (instead of default Assets.persist)
@Override
public boolean hasCustomSave() { return true; }

@Override
public void customSave(String path) {
    // Custom serialization
}

// Content-specific keyboard shortcuts
@Override
public List<ShortcutAction> provideExtraShortcuts(KeyboardLayout layout) {
    return List.of(
        ShortcutAction.builder()
            .id("editor.questlog.addEntry")
            .displayName("Add Quest Entry")
            .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.E))
            .handler(this::addEntry)
            .build()
    );
}

// Sidebar annotations (e.g., validation warnings)
@Override
public String getAssetAnnotation(String path) {
    QuestLog log = Assets.load(path, QuestLog.class);
    return log.hasErrors() ? MaterialIcons.Warning : null;
}

// Called after undo/redo to re-resolve stale references
@Override
public void onAfterUndoRedo() {
    refreshSelectedEntry();
}

// Render popups (must be outside child windows)
@Override
public void renderPopups() {
    renderDeleteConfirmPopup();
}
```

---

## AssetEditorShell API

The `shell` object passed to `onAssetLoaded()` provides access to the panel's shared infrastructure:

| Method | Description |
|--------|-------------|
| `shell.markDirty()` | Mark asset as having unsaved changes |
| `shell.isDirty()` | Check if there are unsaved changes |
| `shell.getEditingPath()` | Get the current asset's path |
| `shell.getUndoStack()` / `getRedoStack()` | Direct access to undo/redo stacks |
| `shell.showStatus(msg)` | Show a status message in the editor status bar |
| `shell.requestSidebarRefresh()` | Refresh the sidebar asset list |
| `shell.selectAssetByPath(path)` | Navigate to a different asset |
| `shell.clearEditingAsset()` | Clear the editor (e.g., after deletion) |
| `shell.createAsset(name, default)` | Create a new asset file and navigate to it |
| `shell.requestDirtyGuard(action)` | Run action after resolving unsaved changes |
| `shell.getSelectionManager()` | Get the editor selection manager |

### Marking Dirty

Call `shell.markDirty()` whenever the user modifies the asset. This enables the Save button and triggers the unsaved changes guard when switching assets.

```java
// After any user edit:
shell.markDirty();
```

### Creating Assets

`shell.createAsset()` handles all the boilerplate:
- Name sanitization (whitespace, special characters)
- Collision resolution (appends `_1`, `_2`, etc.)
- Parent directory creation
- Disk write via `Assets.persist()`
- Event publishing (`AssetChangedEvent.CREATED`)
- Navigation to the new asset

```java
QuestLog defaultLog = new QuestLog();
defaultLog.setName(name);
String path = shell.createAsset(name, defaultLog);
// path is null on failure
```

---

## Asset Preview Renderers

Preview renderers control how assets appear in the Asset Browser thumbnails and the Inspector panel.

### Interface

```java
public interface AssetPreviewRenderer<T> {
    /** Compact thumbnail (picker popup, browser grid). */
    void renderPreview(T asset, float maxSize);

    /** Detailed inspector view. Defaults to renderPreview(). */
    default void renderInspector(T asset, String assetPath, float maxSize) {
        renderPreview(asset, maxSize);
    }

    Class<T> getAssetType();
}
```

### Built-in Renderers

| Renderer | Asset Type | Preview | Inspector |
|----------|-----------|---------|-----------|
| `SpritePreviewRenderer` | `Sprite` | Thumbnail | Grid overlay + mode + size info |
| `AnimationPreviewRenderer` | `Animation` | First frame | Animated playback + controls |
| `FontPreviewRenderer` | `Font` | Sample text | Same as preview |
| `AudioClipPreviewRenderer` | `AudioClip` | Audio icon | Same as preview |
| `SpriteBasedPreviewRenderer` | (default) | Loader's `getPreviewSprite()` | Same as preview |

### Creating a Custom Renderer

```java
public class QuestLogPreviewRenderer implements AssetPreviewRenderer<QuestLog> {

    @Override
    public void renderPreview(QuestLog log, float maxSize) {
        ImGui.text(MaterialIcons.MenuBook);
        ImGui.text(log.getEntryCount() + " quests");
    }

    @Override
    public Class<QuestLog> getAssetType() {
        return QuestLog.class;
    }
}
```

Register in `AssetPreviewRegistry`:

```java
// In AssetPreviewRegistry static block:
register(QuestLog.class, new QuestLogPreviewRenderer());
```

---

## Keyboard Shortcuts

| Shortcut | Action | Scope |
|----------|--------|-------|
| Ctrl+S | Save asset | Panel focused |
| Ctrl+Z | Undo | Panel focused |
| Ctrl+Shift+Z | Redo | Panel focused |
| Ctrl+Y | Redo (alt) | Panel focused |
| Ctrl+N | New asset | Panel focused (if content supports it) |
| F5 | Refresh sidebar | Panel focused |

Content implementations can add extra shortcuts via `provideExtraShortcuts()`.

> See the [Shortcuts Guide](shortcutsGuide.md) for all editor shortcuts.

---

## Tips & Best Practices

- **Let the shell handle saving** - Unless you need custom serialization, use the default `Assets.persist()` flow
- **Call `shell.markDirty()` on every edit** - This keeps the Save button, dirty indicator, and unsaved changes guard in sync
- **Use `onAfterUndoRedo()`** - Re-resolve any stale object references (e.g., selected items) after undo/redo
- **Render popups in `renderPopups()`** - Not in `render()`, because popups must be outside child windows
- **Use `shell.createAsset()` for creation** - Handles naming, collisions, disk I/O, and navigation
- **Skip custom content for simple assets** - The `ReflectionEditorContent` fallback works well for POJOs
- **Add `@EditorContentFor` and forget** - No manual registration, no wiring in EditorUIController

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Content not auto-discovered | Ensure `@EditorContentFor` annotation is present and class has a no-arg constructor |
| Asset not opening in editor | Check that the loader returns `EditorPanelType.ASSET_EDITOR` from `getEditorPanelType()` |
| Undo not working | Ensure the content renders inside `render()` (the shell redirects UndoManager before calling it) |
| Save button always disabled | Call `shell.markDirty()` when the user makes changes |
| Unsaved changes dialog not appearing | Ensure `markDirty()` is called; the guard only triggers on dirty assets |
| Sidebar not updating after creation | Call `shell.requestSidebarRefresh()` or use `shell.createAsset()` which does it automatically |
| Shortcuts not firing | Content shortcuts must be returned from `provideExtraShortcuts()` and the panel must be focused |

---

## Related

- [Asset Loader Guide](assetLoaderGuide.md) - Creating asset loaders (the data pipeline side)
- [Custom Inspector Guide](customInspectorGuide.md) - Custom component inspectors (different from asset editors)
- [Asset Browser Guide](assetBrowserGuide.md) - Browsing and selecting assets
- [Inspector Panel Guide](inspectorPanelGuide.md) - The property inspector panel
- [Shortcuts Guide](shortcutsGuide.md) - All keyboard shortcuts
