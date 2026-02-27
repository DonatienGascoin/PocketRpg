# Asset Editor Tabs — Design Document

## Context

This document explores adding tab-based multi-asset editing to the Asset Editor panel. This is a **separate, larger effort** from the main UX improvements plan. It would allow multiple assets to be open simultaneously with instant switching, similar to IDE tabs.

Currently the Asset Editor can only have one asset open at a time. Switching assets requires the unsaved-changes guard and fully unloads the previous asset. The navigation history (from the main plan) helps navigate back, but it still unloads/reloads assets on each switch.

---

## Concept

```
+-----------------------------------------------------------------------+
| Shell toolbar: [=] [<] [>] | tab_name | [Save] [Undo][Redo] [+New v] |
|-----------------------------------------------------------------------|
| [dialogue_01 *] [animation_walk] [animator_main] [items x]  [+]      |  <-- Tab bar
|-----------------------------------------------------------------------|
| Content toolbar (for active tab's content)                            |
|-----------------------------------------------------------------------|
| breadcrumb: dialogues > dialogue_01.dialogue.json                     |
|-----------------------------------------------------------------------|
|                                                                       |
| Content area for the ACTIVE tab                                       |
| (other tabs are suspended but kept in memory)                         |
|                                                                       |
+-----------------------------------------------------------------------+
```

### What a Tab Represents

Each tab is an independent editing session:

```
EditorTab
    ├── String path               // asset path (unique key)
    ├── Class<?> assetType        // type of asset
    ├── Object loadedAsset        // the loaded asset instance
    ├── AssetEditorContent content // content plugin (initialized, has state)
    ├── Deque<EditorCommand> undoStack  // tab's own undo stack
    ├── Deque<EditorCommand> redoStack  // tab's own redo stack
    ├── boolean dirty             // tab's dirty state
    ├── String displayName        // tab label (filename without extension)
    ├── float scrollPositionY     // preserved scroll position (optional)
    └── Map<String, Object> contentState  // content-specific preserved state
```

### Key Behaviors

| Action | Behavior |
|--------|----------|
| Open asset (sidebar/browser/Ctrl+P) | If tab exists for that path -> switch to it. Otherwise -> create new tab, load asset |
| Close tab | Dirty guard -> save/discard/cancel. Then destroy content, remove tab |
| Close tab (middle-click) | Same as close button |
| Switch tab (click) | Suspend current tab's content, activate new tab's content. No load/save needed |
| Reorder tabs (drag) | Rearrange tab order |
| Close all | Close all tabs with dirty guard for each dirty tab |
| Close others | Close all tabs except the clicked one |
| Max tabs reached | Close the oldest non-dirty tab, or show a warning |

---

## Detailed Tab Bar Layout

```
+-----------------------------------------------------------------------+
|  Tab bar:                                                             |
|                                                                       |
|  +----------------+ +------------------+ +-----------------+ +--+    |
|  | [i] dlg_01 * x | | [i] anim_walk  x | | [i] items    x | | + |   |
|  +-----+----------+ +------------------+ +--------+--------+ +--+    |
|        |                                          |                   |
|     active tab                                 dirty tab              |
|     (highlighted bg,                           (name in              |
|      underline accent)                          warning color)       |
+-----------------------------------------------------------------------+

Each tab shows:
  [icon] displayName [x]
   ^         ^        ^
   |         |        close button (small, appears on hover)
   |         filename without extension
   type icon (from Assets.getIconCodepoint)

Active tab: highlighted background + accent underline/border
Dirty tab: name shows in WARNING color with " *" suffix
Hover: close [x] appears (hidden when not hovered to save space)
```

### Tab Overflow

When there are too many tabs to fit in one row:

```
Option A: Horizontal scroll (recommended)
+-----------------------------------------------------------------------+
| [<] [tab1] [tab2] [tab3] [tab4] [tab5] [tab6] [tab7 ...  [>] [+]   |
|      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^                     |
|      scrollable area                                                  |
+-----------------------------------------------------------------------+

Option B: Dropdown overflow
+-----------------------------------------------------------------------+
| [tab1] [tab2] [tab3] [tab4] [tab5] [v 3 more]                  [+]  |
+-----------------------------------------------------------------------+
                                       |
                                +------------+
                                | tab6       |
                                | tab7       |
                                | tab8       |
                                +------------+
```

**Recommendation:** Option A (horizontal scroll) is simpler to implement with ImGui and more familiar to users. ImGui's `ImGuiTabBar` widget has built-in overflow handling.

---

## Architecture

### Option A: Use ImGui's Built-in TabBar (Recommended)

ImGui provides `ImGui.beginTabBar()` / `ImGui.beginTabItem()` / `ImGui.endTabItem()` / `ImGui.endTabBar()` which handles:
- Tab rendering with close buttons
- Tab reordering (with `ImGuiTabBarFlags.Reorderable`)
- Overflow scrolling
- Active tab tracking

```java
// Pseudocode for tab bar rendering
if (ImGui.beginTabBar("##assetTabs", ImGuiTabBarFlags.Reorderable
        | ImGuiTabBarFlags.AutoSelectNewTabs
        | ImGuiTabBarFlags.FittingPolicyScroll
        | ImGuiTabBarFlags.TabListPopupButton)) {

    for (EditorTab tab : tabs) {
        int tabFlags = ImGuiTabItemFlags.None;
        if (tab.dirty) tabFlags |= ImGuiTabItemFlags.UnsavedDocument;
        if (tab.pendingSelect) {
            tabFlags |= ImGuiTabItemFlags.SetSelected;
            tab.pendingSelect = false;
        }

        ImBoolean tabOpen = new ImBoolean(true);
        String label = tab.displayName + "##tab_" + tab.path;

        if (ImGui.beginTabItem(label, tabOpen, tabFlags)) {
            // This tab is active — render its content
            activeTab = tab;
            ImGui.endTabItem();
        }

        if (!tabOpen.get()) {
            // Tab was closed via X button
            closeTab(tab);
        }
    }

    // [+] button to add new tab
    if (ImGui.tabItemButton(MaterialIcons.Add + "##newTab",
            ImGuiTabItemFlags.Trailing)) {
        // Open quick search or new asset dropdown
    }

    ImGui.endTabBar();
}
```

**Pros:** Built-in overflow, reorder, close buttons, styling, keyboard nav.
**Cons:** Less control over custom rendering (icons, hover effects). The `UnsavedDocument` flag provides a dot indicator automatically.

### Option B: Custom Tab Bar

Build the tab bar manually using `ImGui.button()` / `ImGui.selectable()` in a horizontal layout.

**Pros:** Full control over tab appearance (custom icons, colors, hover close button).
**Cons:** Must implement overflow, reorder, and all interactions manually.

**Recommendation:** Start with Option A (ImGui TabBar). It handles the hard parts (overflow, reorder) and can be customized later if needed.

---

## Tab Lifecycle

### Opening an Asset (new tab or switch)

```
selectAssetByPath(path)
    │
    ├── Tab with this path already exists?
    │   ├── YES → Switch to that tab (set pendingSelect = true)
    │   │         No load needed, content is already alive
    │   │
    │   └── NO → Create new EditorTab
    │            │
    │            ├── Check tab limit (MAX_TABS = 10)
    │            │   └── If at limit: close oldest non-dirty tab
    │            │       └── If all dirty: show warning, abort
    │            │
    │            ├── Create new content via contentRegistry.createContent(type)
    │            ├── content.initialize()
    │            ├── Load asset: Assets.load(path, type)
    │            ├── content.onAssetLoaded(path, asset, tabShell)
    │            ├── Add tab to tabs list
    │            └── Set pendingSelect = true
    │
    └── Update navigation history (pushHistory)
```

### Switching Tabs

```
Tab switch (from tabA to tabB)
    │
    ├── tabA.content stays alive (NOT destroyed, NOT unloaded)
    │   └── Just stops rendering — ImGui skips its beginTabItem/endTabItem
    │
    ├── tabB becomes active
    │   ├── Shell toolbar shows tabB's name, dirty state
    │   ├── Content toolbar shows tabB's content extras
    │   ├── Undo/Redo buttons reflect tabB's stacks
    │   └── tabB.content.render() is called
    │
    └── Push to navigation history
```

The key insight: **tabs are NOT unloaded when switching**. The content stays in memory with its full state. Only the active tab's content is rendered. This means instant switching with zero load time.

### Closing a Tab

```
closeTab(tab)
    │
    ├── tab.dirty?
    │   ├── YES → Show unsaved changes popup
    │   │         ├── "Save & Close" → save, then close
    │   │         ├── "Discard & Close" → close without saving
    │   │         └── "Cancel" → abort close
    │   │
    │   └── NO → Close immediately
    │
    ├── tab.content.onAssetUnloaded()
    ├── tab.content.destroy()
    ├── Remove tab from tabs list
    │
    ├── Was this the active tab?
    │   ├── YES → Activate next tab (or previous if last)
    │   │         └── If no tabs left → show empty state
    │   └── NO → No change
    │
    └── Clean up navigation history entries for this path (optional)
```

---

## Tab Shell (Per-Tab Shell Interface)

Each tab needs its own shell that routes to tab-specific state:

```java
public class TabShell implements AssetEditorShell {
    private final EditorTab tab;
    private final AssetEditorPanel panel;

    @Override
    public void markDirty() {
        tab.dirty = true;
    }

    @Override
    public boolean isDirty() {
        return tab.dirty;
    }

    @Override
    public String getEditingPath() {
        return tab.path;
    }

    @Override
    public Deque<EditorCommand> getUndoStack() {
        return tab.undoStack;
    }

    @Override
    public Deque<EditorCommand> getRedoStack() {
        return tab.redoStack;
    }

    @Override
    public void showStatus(String msg) {
        panel.showStatus(msg);
    }

    @Override
    public void requestSidebarRefresh() {
        panel.requestSidebarRefresh();
    }

    @Override
    public void selectAssetByPath(String path) {
        // Opens in a tab (new or existing)
        panel.selectAssetByPath(path);
    }

    @Override
    public void clearEditingAsset() {
        panel.closeTab(tab);
    }

    @Override
    public String createAsset(String name, Object defaultAsset) {
        // Create file, then open in a new tab
        return panel.createAsset(name, defaultAsset);
    }

    @Override
    public void requestDirtyGuard(Runnable afterResolved) {
        if (!tab.dirty) {
            afterResolved.run();
            return;
        }
        panel.showDirtyGuard(tab, afterResolved);
    }

    @Override
    public EditorSelectionManager getSelectionManager() {
        return panel.getSelectionManager();
    }

    @Override
    public void openPopupViewer(String path, Class<?> type) {
        panel.openPopupViewer(path, type);
    }
}
```

---

## Undo/Redo With Tabs

Each tab has its own undo/redo stacks. The shell toolbar's Undo/Redo buttons always reflect the **active tab's** stacks.

```
Tab 1 (Dialogue):     undoStack: [cmd1, cmd2, cmd3]    redoStack: []
Tab 2 (Animation):    undoStack: [cmd4]                 redoStack: [cmd5]
Tab 3 (Animator):     undoStack: []                     redoStack: []

Active tab = Tab 1
→ Undo button: enabled (3 commands)
→ Redo button: disabled (empty)

Switch to Tab 2:
→ Undo button: enabled (1 command)
→ Redo button: enabled (1 command)
```

**In `renderContentArea()`:**

```java
private void renderContentArea() {
    if (activeTab == null || activeTab.content == null) return;

    UndoManager um = UndoManager.getInstance();
    um.pushTarget(activeTab.undoStack, activeTab.redoStack);
    try {
        activeTab.content.render();
    } finally {
        um.popTarget();
    }
}
```

---

## Memory Management

With multiple tabs open, each holding an asset and content in memory:

| Content Type | Approximate Memory | Notes |
|---|---|---|
| Dialogue | ~10-50 KB | Lines + choices, text data |
| Animation | ~5-20 KB + texture refs | Frame data, sprite references |
| Animator | ~10-30 KB | States, transitions, parameters |
| Item Registry | ~20-100 KB | All items in the registry |
| Trainer Registry | ~30-150 KB | All trainers with party data |
| Sprite Editor | ~1-5 MB | Texture preview in memory |

**10 tabs worst case:** ~5-6 MB. This is fine for a desktop editor.

**Tab limit:** `MAX_TABS = 10` is reasonable. When exceeded:
1. Find the oldest non-dirty tab
2. Auto-close it
3. If all tabs are dirty, show a warning: "Too many tabs open. Please close some tabs."

---

## Tab Context Menu

Right-click on a tab opens a context menu:

```
+-----------------------+
| Close                 |
| Close Others          |
| Close All             |
| Close to the Right    |
|---------------------- |
| Copy Path             |
| Reveal in Sidebar     |
+-----------------------+
```

```java
if (ImGui.beginPopupContextItem("##tabCtx_" + tab.path)) {
    if (ImGui.selectable("Close")) {
        closeTab(tab);
    }
    if (ImGui.selectable("Close Others")) {
        closeAllTabsExcept(tab);
    }
    if (ImGui.selectable("Close All")) {
        closeAllTabs();
    }
    if (ImGui.selectable("Close to the Right")) {
        closeTabsToRight(tab);
    }
    ImGui.separator();
    if (ImGui.selectable("Copy Path")) {
        ImGui.setClipboardText(tab.path);
    }
    if (ImGui.selectable("Reveal in Sidebar")) {
        sidebarOpen = true;
        sidebarScrollToSelected = true;
    }
    ImGui.endPopup();
}
```

---

## Tab Bar + Toolbar Interaction

### Full Panel Layout with Tabs

```
+-----------------------------------------------------------------------+
| SHELL TOOLBAR                                                         |
| [=] [<] [>] | activeTabName * | [Save] [Undo] [Redo]  [+ New v]     |
|-----------------------------------------------------------------------|
| TAB BAR                                                               |
| [dlg_01 *] [anim_walk] [animator x] [items] ................... [+]  |
|-----------------------------------------------------------------------|
| CONTENT TOOLBAR (for active tab's content type)                       |
| [+ New] [Delete] ............... [Variables] [Events] [Refresh]       |
|-----------------------------------------------------------------------|
| BREADCRUMB                                                            |
| dialogues > events > dialogue_01.dialogue.json                        |
|-----------------------------------------------------------------------|
|                                                                       |
| BODY (sidebar + content)                                              |
|                                                                       |
| Sidebar  |  Content area                                              |
| (200px)  |  (active tab's content.render())                           |
|          |                                                            |
+-----------------------------------------------------------------------+
```

**Vertical ordering:**
1. Shell toolbar (always visible)
2. Tab bar (always visible, even with 0 tabs — shows just [+])
3. Content toolbar (only if active tab exists and content has extras)
4. Breadcrumb (only if active tab exists)
5. Separator
6. Body (sidebar + content, or empty state if no tabs)

### Shell Toolbar Adaptation

With tabs, the shell toolbar's asset name shows the **active tab's** info:

```java
// In renderShellToolbar():
EditorTab active = activeTab;
if (active != null) {
    String name = active.displayName;
    if (active.dirty) {
        EditorColors.textColored(EditorColors.WARNING, name + " *");
    } else {
        ImGui.text(name);
    }
} else {
    ImGui.textDisabled("No asset selected");
}
```

Save/Undo/Redo also route to the active tab:
```java
boolean canSave = activeTab != null && activeTab.dirty;
boolean canUndo = activeTab != null && !activeTab.undoStack.isEmpty();
boolean canRedo = activeTab != null && !activeTab.redoStack.isEmpty();
```

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Tab` | Switch to next tab |
| `Ctrl+Shift+Tab` | Switch to previous tab |
| `Ctrl+W` | Close active tab |
| `Ctrl+Shift+W` | Close all tabs |
| `Ctrl+1` through `Ctrl+9` | Switch to tab by index |

```java
// Ctrl+Tab — next tab
panelShortcut()
    .id("editor.asset.nextTab")
    .displayName("Next Tab")
    .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Tab))
    .handler(() -> {
        if (tabs.size() > 1) {
            int idx = tabs.indexOf(activeTab);
            int next = (idx + 1) % tabs.size();
            switchToTab(tabs.get(next));
        }
    })
    .build(),

// Ctrl+Shift+Tab — previous tab
panelShortcut()
    .id("editor.asset.prevTab")
    .displayName("Previous Tab")
    .defaultBinding(ShortcutBinding.ctrlShift(ImGuiKey.Tab))
    .handler(() -> {
        if (tabs.size() > 1) {
            int idx = tabs.indexOf(activeTab);
            int prev = (idx - 1 + tabs.size()) % tabs.size();
            switchToTab(tabs.get(prev));
        }
    })
    .build(),
```

---

## Migration Path

### From Current Single-Asset to Tabs

The current `AssetEditorPanel` has these single-asset fields:
```java
private Object editingAsset;
private String editingPath;
private Class<?> editingType;
private AssetEditorContent activeContent;
private boolean dirty;
private final Deque<EditorCommand> panelUndoStack;
private final Deque<EditorCommand> panelRedoStack;
```

With tabs, these become:
```java
private final List<EditorTab> tabs = new ArrayList<>();
private EditorTab activeTab;  // pointer to current tab in the list
// editingAsset, editingPath, etc. are accessed via activeTab.*
```

**Compatibility getters** (to minimize changes in content code):
```java
// These delegate to activeTab
private Object getEditingAsset() { return activeTab != null ? activeTab.loadedAsset : null; }
private String getEditingPath() { return activeTab != null ? activeTab.path : null; }
private boolean isDirty() { return activeTab != null && activeTab.dirty; }
```

### Backward-Compatible `selectAssetByPath()`

The existing `selectAssetByPath()` is called from many places (sidebar, browser, events, history). With tabs, it should:
1. Check if a tab for this path exists → switch to it
2. Otherwise → create new tab

This is a drop-in replacement — no callers need to change.

### Phased Implementation

**Step 1:** Extract `EditorTab` data class with all per-asset state.

**Step 2:** Convert single-asset fields to a single-element tab list. Everything works as before, but the data lives in `EditorTab` instead of panel fields. This is a pure refactor with no behavior change.

**Step 3:** Add tab bar rendering. Still single-tab — clicking an asset replaces the tab (same as today).

**Step 4:** Allow multiple tabs. `selectAssetByPath()` creates new tabs instead of replacing. The unsaved-changes guard only triggers on tab close, not on tab switch.

**Step 5:** Add tab close, context menu, keyboard shortcuts.

---

## Interaction with Other Features

### Navigation History

History should work across tabs. Going "back" switches to the tab containing that asset (or reopens it if the tab was closed).

```
Tab 1: dialogue_01  (open)
Tab 2: anim_walk    (open)

History: [dialogue_01, anim_walk]
                          ^

User clicks Back:
→ Switch to Tab 1 (dialogue_01)
→ No load/unload needed, just tab switch

History: [dialogue_01, anim_walk]
              ^
```

If the asset's tab was closed:
```
History: [dialogue_01, anim_walk]
              ^
              But Tab 1 was closed!

→ Create new tab for dialogue_01
→ Load asset, initialize content
→ Switch to new tab
```

### Quick Search (Ctrl+P)

Works the same — selecting a result calls `selectAssetByPath()` which now creates/switches tabs.

### Asset Popup Viewer

Popup viewers remain independent of tabs. They float over whatever tab is active. A popup viewer for Variables/Events is still useful even with tabs, because:
- Opening Variables in a tab would switch away from the dialogue
- The popup overlays the dialogue so you can see both

### Sidebar

The sidebar shows all assets regardless of which tab is active. Clicking a sidebar item calls `selectAssetByPath()` which creates/switches tabs. The "selected" highlight in the sidebar reflects the active tab's path.

---

## Considerations and Risks

### Memory
- 10 open tabs with content instances uses ~5-6 MB — acceptable
- Sprite editor tabs may hold texture previews (~1-5 MB each) — consider lazy unloading for background tabs

### Asset Hot-Reload
- When an asset file changes on disk, ALL tabs for that asset need to be notified
- Listen for `AssetChangedEvent` and reload the affected tab's asset
- If tab is dirty, show a conflict dialog: "File changed on disk. Reload (lose changes) or keep current?"

### Undo/Redo Confusion
- Users might expect Ctrl+Z to undo across tabs (like some IDEs)
- But per-tab undo is more predictable (like VS Code)
- **Decision:** Per-tab undo. Each tab has independent history.

### Tab Persistence Across Sessions (Future)
- Could save open tab paths to editor preferences
- On next session, restore tabs (but not undo history)
- Not in initial implementation — session-scoped only

### Content State Preservation
- Some content types have internal state (scroll position, selection, expanded tree nodes)
- Since content stays alive across tab switches, this is automatically preserved
- No special handling needed (unlike a close/reopen flow)

---

## ImGui TabBar API Reference

Key functions from imgui-java:

```java
// Create a tab bar
boolean open = ImGui.beginTabBar("id", flags);
// flags: Reorderable, AutoSelectNewTabs, FittingPolicyScroll, TabListPopupButton

// Create a tab item (call within beginTabBar/endTabBar)
ImBoolean pOpen = new ImBoolean(true);
boolean selected = ImGui.beginTabItem("Label", pOpen, itemFlags);
// pOpen: set to false when user clicks X
// itemFlags: UnsavedDocument (shows dot), SetSelected (force select), Leading/Trailing
if (selected) {
    // Render tab content
    ImGui.endTabItem();
}

// Special button tab (e.g., [+] button)
boolean clicked = ImGui.tabItemButton("Label", flags);
// flags: Trailing (appears at end), Leading (appears at start)

ImGui.endTabBar();
```

**Note:** `ImGuiTabItemFlags.UnsavedDocument` adds a dot/bullet before the tab name automatically — this replaces our manual " *" dirty indicator on tabs.

---

## Summary

| Aspect | Decision |
|--------|----------|
| Tab widget | ImGui built-in TabBar |
| Max tabs | 10 |
| Overflow | Scroll (FittingPolicyScroll) |
| Tab state | Full content kept alive in memory |
| Undo/Redo | Per-tab independent stacks |
| Dirty indicator | ImGuiTabItemFlags.UnsavedDocument |
| Tab close | X button + dirty guard |
| Navigation | selectAssetByPath creates/switches tabs |
| History | Works across tabs, reopens closed tabs |
| Session persistence | Not in v1 (future consideration) |
