# Asset Editor UX Improvements

## Context

The Asset Editor panel (`AssetEditorPanel.java`, 1017 lines) is the unified panel for editing all asset types. It hosts pluggable `AssetEditorContent` implementations (Dialogue, Animation, Animator, ItemRegistry, TrainerRegistry, etc.) with a hamburger sidebar, toolbar, and content area.

**Current pain points:**
1. The "New" button is invisible until you open an existing asset (toolbar early-returns at line 243)
2. No navigation history -- switching assets is a one-way trip
3. Empty state is a dead end ("No asset selected" with no actions)
4. Dialogue's "Variables"/"Events" buttons navigate away, losing your place
5. Quick-search requires opening the sidebar
6. No breadcrumb showing current asset location
7. No context menu on sidebar items
8. Ctrl+N only works with active content
9. Toolbar will get crowded with new controls

---

## Improvement Summary

| # | Improvement | Phase |
|---|------------|-------|
| 1 | Two-row toolbar split (shell + content inside body) | Phase 1 |
| 2 | Always-visible "New" dropdown | Phase 1 |
| 3 | Redesigned empty state | Phase 1 |
| 4 | Asset popup viewer (Variables/Events) | Phase 2 |
| 5 | Navigation history (Back/Forward) | Phase 3 |
| 6 | Quick-search popup (Ctrl+P) | Phase 3 |
| 7 | Recently opened list (persisted) | Phase 4 |
| 8 | Favorites (persisted) | Phase 4 |
| 9 | Sidebar context menu | Phase 4 |
| 10 | Breadcrumb path bar | Phase 4 |
| 11 | Keyboard shortcuts | Phase 5 |

---

## Phase 1: Toolbar Split + Always-Visible New + Empty State

### 1A. Two-Row Toolbar

The toolbar is getting crowded. Currently it's one row that early-returns when no asset is loaded. With new controls (back/forward, new dropdown, breadcrumb), we need a split.

**Key design decision:** The content toolbar belongs **inside the content child window**, not in the main panel area. This makes sense because:
- The content toolbar is part of the content's editing context
- It scrolls/clips with the content area, not the shell
- It keeps the shell toolbar clean and always-visible
- Content types that have no toolbar extras don't waste vertical space

**Current layout (single row, gated behind asset loaded):**
```
+-----------------------------------------------------------------------+
| [=] AssetName * | [Save] [Undo] [Redo] | <content extras...>         |
+-----------------------------------------------------------------------+
| sidebar | content                                                     |
+-----------------------------------------------------------------------+
```

**New layout:**
```
+-----------------------------------------------------------------------+
| [=] [<] [>] | AssetName *  | [Save] [Undo] [Redo]  [+ New v] [x]    |  <-- Shell toolbar (ALWAYS visible)
|-----------------------------------------------------------------------|
| breadcrumb: dialogues > dialogue_01.dialogue.json                     |  <-- Breadcrumb (only when asset loaded)
|-----------------------------------------------------------------------|
| separator                                                             |
|-----------------------------------------------------------------------|
| sidebar | CONTENT CHILD WINDOW:                                       |
|         | +----------------------------------------------------------+|
|         | | [+ New] [Delete] ........ [Variables] [Events] [Refresh]||  <-- Content toolbar (INSIDE child)
|         | |----------------------------------------------------------||
|         | | (content body renders here)                              ||
|         | +----------------------------------------------------------+|
+-----------------------------------------------------------------------+
```

**Shell toolbar (always visible, never gated):**
```
+-----------------------------------------------------------------------+
| [=]  [<] [>]  |  dialogue_01 *  |  [Save] [Undo] [Redo]  [+New v] [x]|
|  ^     ^   ^      ^                 ^       ^      ^        ^       ^  |
|  |     |   |      |                 |       |      |        |       |  |
|  |     |   |      Asset name        Save    Undo   Redo     |       |  |
|  |     |   |      (dirty = *)       (warn   (dis-  (dis-    |       Close
|  |     |   |      (or empty msg)     color)  abled) abled)   |       current
|  |     |   |                                                 |       asset
|  |     Back/Forward (Phase 3, placeholder disabled for now)  |
|  |                                                           |
|  Hamburger                                          New dropdown
|  toggle                                             (always visible)
+-----------------------------------------------------------------------+
```

**Content toolbar examples (rendered inside the content child window, by each content type):**
```
DialogueEditorContent:
+-----------------------------------------------------------------------+
| [+ New] [Delete] ................ [Variables] [Events] [Refresh]      |
+-----------------------------------------------------------------------+

AnimationEditorContent:
+-----------------------------------------------------------------------+
| [Play] [Stop] [===speed===] | [+ New] [Delete] [Refresh]             |
+-----------------------------------------------------------------------+

AnimatorEditorContent:
+-----------------------------------------------------------------------+
| [+ New] [Delete] [Refresh] [Preview]                                  |
+-----------------------------------------------------------------------+

ItemRegistry / TrainerRegistry:
(no content toolbar — these types have internal New/Delete in their left column)
```

**Implementation details:**

File: `AssetEditorPanel.java`

Current `renderToolbar()` (lines 231-302) becomes `renderShellToolbar()`:

```java
// ALWAYS rendered, never gated by editingAsset
private void renderShellToolbar() {
    // Hamburger toggle (existing code, lines 233-240)
    String hamburgerIcon = sidebarOpen ? MaterialIcons.MenuOpen : MaterialIcons.Menu;
    if (ImGui.button(hamburgerIcon + "##hamburger")) { ... }

    ImGui.sameLine();

    // Back / Forward (Phase 3 — render disabled placeholders for now)
    ImGui.beginDisabled();
    ImGui.button(MaterialIcons.ArrowBack + "##back");
    ImGui.sameLine();
    ImGui.button(MaterialIcons.ArrowForward + "##forward");
    ImGui.endDisabled();

    ImGui.sameLine();
    ImGui.text("|");
    ImGui.sameLine();

    // Asset name (or "No asset" when nothing loaded)
    if (editingAsset != null) {
        String name = extractFilename(editingPath);
        if (dirty) {
            EditorColors.textColored(EditorColors.WARNING, name + " *");
        } else {
            ImGui.text(name);
        }
    } else {
        ImGui.textDisabled("No asset selected");
    }

    // Right-aligned section: Save, Undo, Redo, New, Close
    float rightWidth = calculateRightToolbarWidth();
    float rightEdge = ImGui.getContentRegionAvailX() + ImGui.getCursorPosX();
    ImGui.sameLine(rightEdge - rightWidth);

    // Save (disabled when no asset or clean)
    boolean canSave = editingAsset != null && dirty;
    if (canSave) EditorColors.pushWarningButton();
    else ImGui.beginDisabled();
    if (ImGui.button(MaterialIcons.Save + "##save")) save();
    if (canSave) EditorColors.popButtonColors();
    else ImGui.endDisabled();
    ImGui.sameLine();

    // Undo / Redo (disabled when stacks empty or no asset)
    boolean canUndo = editingAsset != null && !panelUndoStack.isEmpty();
    if (!canUndo) ImGui.beginDisabled();
    if (ImGui.button(MaterialIcons.Undo + "##undo")) undo();
    if (!canUndo) ImGui.endDisabled();
    ImGui.sameLine();

    boolean canRedo = editingAsset != null && !panelRedoStack.isEmpty();
    if (!canRedo) ImGui.beginDisabled();
    if (ImGui.button(MaterialIcons.Redo + "##redo")) redo();
    if (!canRedo) ImGui.endDisabled();
    ImGui.sameLine();

    // [+ New v] dropdown — ALWAYS visible
    renderNewAssetDropdown();

    // [x] Close current asset (only when asset loaded)
    if (editingAsset != null) {
        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.Close + "##closeAsset")) {
            requestDirtyGuard(() -> clearEditingAsset());
        }
    }
}
```

The content toolbar moves **into** the content child window. Update `renderContentArea()`:

```java
private void renderContentArea() {
    if (activeContent == null) return;

    UndoManager um = UndoManager.getInstance();
    um.pushTarget(panelUndoStack, panelRedoStack);
    try {
        // Content toolbar is now INSIDE the content child window
        activeContent.renderToolbarExtras();
        // Only add separator if content actually rendered toolbar extras
        // (content types with no extras will just skip this)

        activeContent.render();
    } finally {
        um.popTarget();
    }
}
```

Updated `render()` flow:
```java
if (visible) {
    renderShellToolbar();
    renderBreadcrumb();    // Phase 4 — no-op until implemented
    ImGui.separator();
    renderBody();          // body contains sidebar + content child (with content toolbar inside)
}
```

The content-specific `[+ New]` button in `renderToolbarExtras()` stays as a convenience shortcut (same type, no type picker needed). The shell's `[+ New v]` dropdown is for creating any asset type from anywhere. Both coexist — analogous to "File > New" (global) vs a type-specific "+" button.

---

### 1B. Always-Visible "New Asset" Dropdown

A dropdown button in the shell toolbar that lists all creatable asset types.

```
+-------------------+
| [+ New v]         |  <-- button with down arrow
+-------------------+
| Dialogue          |  <-- from DialogueEditorContent.getCreationInfo()
| Animation         |  <-- from AnimationEditorContent.getCreationInfo()
| Animator          |  <-- from AnimatorEditorContent.getCreationInfo()
| Item Registry     |  <-- from ItemRegistryEditorContent.getCreationInfo()
| Trainer Registry  |  <-- from TrainerRegistryEditorContent.getCreationInfo()
+-------------------+
```

**Implementation:**

```java
// New fields in AssetEditorPanel
private List<CreatableAssetType> creatableTypes; // populated once at init
private record CreatableAssetType(
    String displayName,        // e.g. "Dialogue"
    Class<?> assetClass,
    AssetCreationInfo info,
    Supplier<AssetEditorContent> factory
) {}

private void initCreatableTypes() {
    creatableTypes = new ArrayList<>();
    for (Class<?> type : contentRegistry.getRegisteredTypes()) {
        AssetEditorContent temp = contentRegistry.createContent(type);
        AssetCreationInfo info = temp.getCreationInfo();
        if (info != null) {
            String name = type.getSimpleName();
            creatableTypes.add(new CreatableAssetType(name, type, info,
                () -> contentRegistry.createContent(type)));
        }
        temp.destroy();
    }
    creatableTypes.sort(Comparator.comparing(CreatableAssetType::displayName));
}

private void renderNewAssetDropdown() {
    if (ImGui.button(MaterialIcons.Add + " New " + MaterialIcons.ArrowDropDown)) {
        ImGui.openPopup("##newAssetDropdown");
    }
    if (ImGui.isItemHovered()) {
        ImGui.setTooltip("Create new asset (Ctrl+N)");
    }

    if (ImGui.beginPopup("##newAssetDropdown")) {
        for (CreatableAssetType ct : creatableTypes) {
            if (ImGui.selectable(ct.displayName())) {
                openNewAssetDialog(ct);
            }
        }
        ImGui.endPopup();
    }
}
```

**New asset creation flow:**

When a type is selected from the dropdown, we switch to that content type (if not already active) and delegate to its `onNewRequested()` method. This reuses all existing creation dialogs without duplication.

```java
private void openNewAssetDialog(CreatableAssetType type) {
    Runnable createAction = () -> {
        // Ensure the right content type is active
        if (activeContent == null || activeContent.getAssetClass() != type.assetClass()) {
            switchContentForNewAsset(type.assetClass());
        }
        if (activeContent != null) {
            activeContent.onNewRequested();
        }
    };

    if (dirty && editingAsset != null) {
        requestDirtyGuard(createAction);
    } else {
        createAction.run();
    }
}

// Switch content type without loading an asset (for "New" flow)
private void switchContentForNewAsset(Class<?> type) {
    if (activeContent != null) {
        activeContent.onAssetUnloaded();
        activeContent.destroy();
    }
    activeContent = contentRegistry.createContent(type);
    activeContent.initialize();
    editingType = type;
    // editingAsset/editingPath stay null — content handles onNewRequested() without a loaded asset
}
```

**Content side — ensure `onNewRequested()` works without loaded asset:**

`DialogueEditorContent`, `AnimationEditorContent`, and `AnimatorEditorContent` already override `onNewRequested()` with popup dialogs that don't depend on a loaded asset.

`ItemRegistryEditorContent`, `TrainerRegistryEditorContent`, and `ShopRegistryEditorContent` need to add `onNewRequested()` overrides:
```java
@Override
public void onNewRequested() {
    // Show name popup, then call shell.createAsset(name, new ItemRegistry())
    newRegistryName.set("");
    showNewRegistryPopup = true;
}
```

**Ctrl+N shortcut update:**

Set a `pendingOpenNewDropdown` flag (since `ImGui.openPopup()` can't be called from a shortcut handler outside the render loop):
```java
.handler(() -> {
    if (activeContent != null) {
        activeContent.onNewRequested();
    } else {
        pendingOpenNewDropdown = true;
    }
})
```
Then in `renderNewAssetDropdown()`:
```java
if (pendingOpenNewDropdown) {
    ImGui.openPopup("##newAssetDropdown");
    pendingOpenNewDropdown = false;
}
```

**Files to modify:**
- `AssetEditorPanel.java` — split toolbar, add dropdown, add `switchContentForNewAsset()`
- `ItemRegistryEditorContent.java` — add `onNewRequested()` override
- `TrainerRegistryEditorContent.java` — add `onNewRequested()` override
- `ShopRegistryEditorContent.java` — add `onNewRequested()` override (if it has creation info)

---

### 1C. Redesigned Empty State

Replace the "No asset selected" text with an actionable landing page.

**Current empty state:**
```
+-----------------------------------------------------------------------+
| No asset selected. Double-click an asset in the Asset Browser,        |
| or select one from the sidebar.                                       |
+-----------------------------------------------------------------------+
```

**New empty state:**
```
+-----------------------------------------------------------------------+
|                                                                       |
|                    Asset Editor                                       |
|                                                                       |
|   Create New                                                          |
|   +-------------+ +-------------+ +-------------+ +-------------+    |
|   | [icon]      | | [icon]      | | [icon]      | | [icon]      |    |
|   | Dialogue    | | Animation   | | Animator    | | Item Reg.   |    |
|   +-------------+ +-------------+ +-------------+ +-------------+    |
|                                                                       |
|   Favorites                                          (Phase 4)        |
|   +---------------------------------------------------------------+  |
|   | [star] dialogue_01.dialogue.json                              |  |
|   | [star] main_controller.animator.json                          |  |
|   +---------------------------------------------------------------+  |
|                                                                       |
|   Recently Opened                                    (Phase 4)        |
|   +---------------------------------------------------------------+  |
|   | [icon] dialogue_02.dialogue.json               2 min ago      |  |
|   | [icon] player_walk.anim.json                   15 min ago     |  |
|   | [icon] items.items.json                        1 hour ago     |  |
|   +---------------------------------------------------------------+  |
|                                                                       |
|   Tip: Open the sidebar [=] to browse all assets, or press Ctrl+P    |
|                                                                       |
+-----------------------------------------------------------------------+
```

**Implementation:**

```java
private void renderEmptyState() {
    float availW = ImGui.getContentRegionAvailX();
    float contentWidth = Math.min(600, availW - 40);
    float startX = (availW - contentWidth) / 2 + ImGui.getCursorPosX();

    ImGui.setCursorPosY(ImGui.getCursorPosY() + 20);

    // Title
    ImGui.setCursorPosX(startX);
    ImGui.pushFont(EditorFonts.getLargeFont()); // if available
    ImGui.text("Asset Editor");
    ImGui.popFont();
    ImGui.spacing();

    // "Create New" section
    ImGui.setCursorPosX(startX);
    ImGui.text("Create New");
    ImGui.spacing();

    // Asset type cards — grid of clickable buttons
    float cardWidth = 120;
    float cardHeight = 60;
    float cardSpacing = 8;
    float cardX = startX;

    for (CreatableAssetType ct : creatableTypes) {
        ImGui.setCursorPosX(cardX);
        if (ImGui.button(getTypeIcon(ct) + "\n" + ct.displayName(), cardWidth, cardHeight)) {
            openNewAssetDialog(ct);
        }

        cardX += cardWidth + cardSpacing;
        if (cardX + cardWidth > startX + contentWidth) {
            cardX = startX;
        } else {
            ImGui.sameLine();
        }
    }

    ImGui.spacing();
    ImGui.spacing();

    // Favorites (Phase 4 — placeholder until persistence is added)
    renderFavoritesList(startX, contentWidth);

    // Recently opened (Phase 4 — placeholder until persistence is added)
    renderRecentlyOpenedList(startX, contentWidth);

    ImGui.spacing();

    // Tip
    ImGui.setCursorPosX(startX);
    ImGui.textDisabled("Tip: Open the sidebar " + MaterialIcons.Menu
        + " to browse all assets, or press Ctrl+P to search");
}
```

Replace both empty state locations in `renderBody()` (lines 323-325 and 333-335):
```java
// Before:
ImGui.textDisabled("No asset selected...");
// After:
renderEmptyState();
```

**Files to modify:**
- `AssetEditorPanel.java` — add `renderEmptyState()`, replace both empty state blocks in `renderBody()`

---

## Phase 2: Asset Popup Viewer

### Problem

When editing a Dialogue, clicking "Variables" or "Events" fires `AssetSelectionRequestEvent` which navigates the entire Asset Editor to a different asset. The dialogue is unloaded, and getting back requires finding it in the sidebar.

### Solution: Floating Asset Viewer Window

A standalone `ImGui.begin()`/`ImGui.end()` window (NOT a popup/modal) that:
- Hosts its own `AssetEditorContent` instance
- Has its own undo/redo stacks
- Has its own save button and dirty tracking
- Floats over the main editor
- Can be closed (with save guard)
- Leaves the main Asset Editor completely untouched

```
+-- Asset Editor (main) ------------------------------------------+
| [=] [<] [>] | dialogue_01 * | [Save] [Undo] [Redo] [+New v] [x]|
|------------------------------------------------------------------|
| dialogues > dialogue_01.dialogue.json                            |
|------------------------------------------------------------------|
| sidebar | [+ New] [Delete] ........ [Variables] [Events] [Refr.] |
|         |---------------------------------------------------------|
|         |                                                         |
|         |  Dialogue content                                       |
|         |  (lines editor + choices)                               |
|         |                                                         |
|         |    +-- Variables Viewer (floating) ---------+            |
|         |    | variables * | ................ [Save]  |            |
|         |    |----------------------------------------|            |
|         |    |                                        |            |
|         |    |  ReflectionEditorContent for            |            |
|         |    |  DialogueVariables asset                |            |
|         |    |                                        |            |
|         |    |  [variable_1]  [String v]  [____]      |            |
|         |    |  [variable_2]  [Int v]     [____]      |            |
|         |    |  [+ Add Variable]                      |            |
|         |    |                                        |            |
|         |    +----------------------------------------+            |
|         |                                                         |
+------------------------------------------------------------------+
```

### Architecture

New class: `AssetPopupViewer`

```
AssetPopupViewer
    ├── String windowTitle          // ImGui window title (must be unique)
    ├── String assetPath            // path of the loaded asset
    ├── Object loadedAsset          // the asset instance
    ├── Class<?> assetType          // asset type class
    ├── AssetEditorContent content  // content plugin instance
    ├── Deque<EditorCommand> undoStack  // own undo stack
    ├── Deque<EditorCommand> redoStack  // own redo stack
    ├── boolean dirty               // own dirty tracking
    ├── boolean open                // window open state
    ├── AssetEditorShell shell      // lightweight shell impl
    │
    ├── open(path, type)            // load asset and show window
    ├── render()                    // called from AssetEditorPanel.render()
    ├── close()                     // with dirty guard
    └── save()                      // persist changes
```

**File:** `src/main/java/com/pocket/rpg/editor/panels/AssetPopupViewer.java`

```java
public class AssetPopupViewer {

    private final AssetEditorContentRegistry contentRegistry;
    private final String windowId;          // unique ID for ImGui
    private String windowTitle;
    private String assetPath;
    private Object loadedAsset;
    private Class<?> assetType;
    private AssetEditorContent content;
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private boolean dirty;
    private boolean open;
    private boolean pendingClose;
    private final ImBoolean windowOpen = new ImBoolean(true);
    private float initialWidth = 500;
    private float initialHeight = 400;

    private final AssetEditorShell shell = new AssetEditorShell() {
        @Override public void markDirty() { dirty = true; }
        @Override public boolean isDirty() { return dirty; }
        @Override public String getEditingPath() { return assetPath; }
        @Override public Deque<EditorCommand> getUndoStack() { return undoStack; }
        @Override public Deque<EditorCommand> getRedoStack() { return redoStack; }
        @Override public void showStatus(String msg) { /* delegate to main panel */ }
        @Override public void requestSidebarRefresh() { /* no sidebar */ }
        @Override public void selectAssetByPath(String path) { /* no-op */ }
        @Override public void clearEditingAsset() { close(); }
        @Override public String createAsset(String name, Object def) { return null; }
        @Override public void requestDirtyGuard(Runnable after) {
            if (!dirty) { after.run(); return; }
            pendingCloseAction = after;
            showUnsavedPopup = true;
        }
        @Override public EditorSelectionManager getSelectionManager() { return null; }
    };

    public void open(String path, Class<?> type) {
        loadedAsset = Assets.load(path, type);
        if (loadedAsset == null) return;

        assetPath = path;
        assetType = type;
        windowTitle = extractFilename(path);

        if (content != null) { content.onAssetUnloaded(); content.destroy(); }
        content = contentRegistry.createContent(type);
        content.initialize();
        content.onAssetLoaded(path, loadedAsset, shell);

        dirty = false;
        undoStack.clear();
        redoStack.clear();
        open = true;
        windowOpen.set(true);
    }

    public void render() {
        if (!open) return;

        String title = windowTitle + (dirty ? " *" : "")
                     + "###popupViewer_" + windowId;

        ImGui.setNextWindowSize(initialWidth, initialHeight, ImGuiCond.FirstUseEver);

        if (ImGui.begin(title, windowOpen,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse)) {

            renderMiniToolbar();
            ImGui.separator();

            // Render content with own undo stacks
            UndoManager um = UndoManager.getInstance();
            um.pushTarget(undoStack, redoStack);
            try {
                content.render();
            } finally {
                um.popTarget();
            }
        }

        if (content != null) content.renderPopups();
        renderUnsavedChangesPopup();

        ImGui.end();

        // Handle window close via X button
        if (!windowOpen.get()) {
            if (dirty) {
                windowOpen.set(true);
                pendingClose = true;
                showUnsavedPopup = true;
            } else {
                close();
            }
        }
    }

    private void renderMiniToolbar() {
        if (dirty) {
            EditorColors.textColored(EditorColors.WARNING, windowTitle + " *");
        } else {
            ImGui.text(windowTitle);
        }

        // Right-aligned Save button
        float saveW = ImGui.calcTextSize(MaterialIcons.Save + " Save").x
                     + ImGui.getStyle().getFramePaddingX() * 2;
        float rightEdge = ImGui.getContentRegionAvailX() + ImGui.getCursorPosX();
        ImGui.sameLine(rightEdge - saveW);

        boolean canSave = dirty;
        if (canSave) EditorColors.pushWarningButton();
        else ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Save + " Save##popupSave_" + windowId)) {
            save();
        }
        if (canSave) EditorColors.popButtonColors();
        else ImGui.endDisabled();
    }

    public void close() {
        if (content != null) {
            content.onAssetUnloaded();
            content.destroy();
            content = null;
        }
        open = false;
        loadedAsset = null;
        assetPath = null;
    }
}
```

**Popup viewer mini toolbar:**
```
+-----------------------------------------------+
| variables * | ........................ [Save]   |
+-----------------------------------------------+
| (content renders here)                         |
+-----------------------------------------------+
```

### Integration with AssetEditorPanel

```java
// New fields in AssetEditorPanel
private final List<AssetPopupViewer> popupViewers = new ArrayList<>();

// In render(), after renderBody() and before ImGui.end():
for (AssetPopupViewer viewer : popupViewers) {
    viewer.render();
}
popupViewers.removeIf(v -> !v.isOpen());

// New shell method (add to AssetEditorShell interface):
void openPopupViewer(String assetPath, Class<?> assetType);

// Implementation in AssetEditorPanel:
@Override
public void openPopupViewer(String assetPath, Class<?> assetType) {
    // Check if already open — focus existing
    for (AssetPopupViewer v : popupViewers) {
        if (assetPath.equals(v.getAssetPath())) {
            v.requestFocus();
            return;
        }
    }
    AssetPopupViewer viewer = new AssetPopupViewer(contentRegistry, generateViewerId());
    viewer.open(assetPath, assetType);
    popupViewers.add(viewer);
}
```

### Changes to DialogueEditorContent

Replace `openVariablesAsset()` and `openEventsAsset()` to use popup viewer:

```java
// Before (navigates away):
private void openVariablesAsset() {
    ensureAssetExists(VARIABLES_ASSET_PATH, () -> { ... });
    EditorEventBus.get().publish(new AssetSelectionRequestEvent(
        VARIABLES_ASSET_PATH, DialogueVariables.class));
}

// After (opens popup viewer):
private void openVariablesAsset() {
    ensureAssetExists(VARIABLES_ASSET_PATH, () -> { ... });
    shell.openPopupViewer(VARIABLES_ASSET_PATH, DialogueVariables.class);
}

private void openEventsAsset() {
    ensureAssetExists(EVENTS_ASSET_PATH, () -> { ... });
    shell.openPopupViewer(EVENTS_ASSET_PATH, DialogueEvents.class);
}
```

Update button icons (no longer "open in new" since they open in a popup):
```java
// Before:
if (ImGui.button("Variables " + MaterialIcons.OpenInNew)) {
// After:
if (ImGui.button("Variables " + MaterialIcons.Visibility)) {
```

**Files to create:**
- `src/main/java/com/pocket/rpg/editor/panels/AssetPopupViewer.java`

**Files to modify:**
- `AssetEditorShell.java` — add `openPopupViewer(String path, Class<?> type)` method
- `AssetEditorPanel.java` — manage popup viewers list, implement `openPopupViewer()`
- `DialogueEditorContent.java` — change Variables/Events to use `shell.openPopupViewer()`

---

## Phase 3: Navigation History + Quick Search

### 3A. Navigation History

**Data structure:**

```java
// New fields in AssetEditorPanel
private final List<String> navigationHistory = new ArrayList<>();
private int navigationIndex = -1;  // current position in history
private static final int MAX_HISTORY = 50;
private boolean navigatingHistory = false;  // flag to prevent pushing during back/forward
```

**How it works:**

```
History: [dialogue_01, animation_walk, dialogue_02, animator_main]
                                                        ^
                                                   navigationIndex = 3

User clicks Back:
History: [dialogue_01, animation_walk, dialogue_02, animator_main]
                                            ^
                                       navigationIndex = 2
(loads dialogue_02)

User opens sidebar asset "items.items.json":
History: [dialogue_01, animation_walk, dialogue_02, items]
                                                      ^
                                                 navigationIndex = 3
(forward history after index 2 is truncated)
```

**Implementation:**

```java
private void pushHistory(String path) {
    if (navigatingHistory) return;
    if (path == null) return;

    // Don't push duplicate of current position
    if (navigationIndex >= 0 && navigationIndex < navigationHistory.size()
            && path.equals(navigationHistory.get(navigationIndex))) {
        return;
    }

    // Truncate forward history
    if (navigationIndex < navigationHistory.size() - 1) {
        navigationHistory.subList(navigationIndex + 1, navigationHistory.size()).clear();
    }

    navigationHistory.add(path);
    navigationIndex = navigationHistory.size() - 1;

    // Cap size
    if (navigationHistory.size() > MAX_HISTORY) {
        navigationHistory.remove(0);
        navigationIndex--;
    }
}

private boolean canGoBack() { return navigationIndex > 0; }
private boolean canGoForward() { return navigationIndex < navigationHistory.size() - 1; }

private void goBack() {
    if (!canGoBack()) return;
    navigatingHistory = true;
    navigationIndex--;
    selectAssetByPath(navigationHistory.get(navigationIndex));
    // navigatingHistory is reset at the end of forceSelectAssetByPath()
}

private void goForward() {
    if (!canGoForward()) return;
    navigatingHistory = true;
    navigationIndex++;
    selectAssetByPath(navigationHistory.get(navigationIndex));
}
```

**Call `pushHistory()` from `forceSelectAssetByPath()`**, then reset the flag:
```java
// In forceSelectAssetByPath(), after successful load:
pushHistory(path);
navigatingHistory = false;  // reset here so dirty guard + deferred loads work
```

Using `selectAssetByPath()` (not `forceSelectAssetByPath()`) from goBack/goForward ensures the dirty guard is respected.

**Toolbar buttons (replace disabled placeholders from Phase 1):**
```java
boolean canBack = canGoBack();
if (!canBack) ImGui.beginDisabled();
if (ImGui.button(MaterialIcons.ArrowBack + "##back")) {
    requestDirtyGuard(this::goBack);
}
if (!canBack) ImGui.endDisabled();
if (ImGui.isItemHovered()) ImGui.setTooltip("Back (Alt+Left)");
ImGui.sameLine();

boolean canFwd = canGoForward();
if (!canFwd) ImGui.beginDisabled();
if (ImGui.button(MaterialIcons.ArrowForward + "##forward")) {
    requestDirtyGuard(this::goForward);
}
if (!canFwd) ImGui.endDisabled();
if (ImGui.isItemHovered()) ImGui.setTooltip("Forward (Alt+Right)");
```

**Shortcuts:**
```java
panelShortcut()
    .id("editor.asset.back")
    .displayName("Navigate Back")
    .defaultBinding(ShortcutBinding.alt(ImGuiKey.LeftArrow))
    .handler(() -> { if (canGoBack()) requestDirtyGuard(this::goBack); })
    .build(),

panelShortcut()
    .id("editor.asset.forward")
    .displayName("Navigate Forward")
    .defaultBinding(ShortcutBinding.alt(ImGuiKey.RightArrow))
    .handler(() -> { if (canGoForward()) requestDirtyGuard(this::goForward); })
    .build(),
```

**Files to modify:**
- `AssetEditorPanel.java` — add history fields, pushHistory, goBack/goForward, update toolbar, add shortcuts

---

### 3B. Quick-Search Popup (Ctrl+P)

A floating search popup that fuzzy-matches asset names across all types.

```
+-- Quick Open --------------------------------+
|  [____________________________] (auto-focus) |
|----------------------------------------------|
|  [icon] dialogue_01.dialogue.json            |
|  [icon] dialogue_02.dialogue.json            |  <-- highlighted
|  [icon] dialog_events.dialogue-events.json   |
|  [icon] player_walk.anim.json                |
|  [icon] idle_controller.animator.json        |
|  ...                                         |
|  (max 15 results)                            |
+----------------------------------------------+
```

**New class: `AssetQuickSearchPopup`**

File: `src/main/java/com/pocket/rpg/editor/panels/AssetQuickSearchPopup.java`

Follows the existing `ComponentBrowserPopup` pattern:

```java
public class AssetQuickSearchPopup {
    private static final String POPUP_ID = "##assetQuickSearch";
    private final ImString searchText = new ImString(256);
    private boolean shouldOpen;
    private boolean focusSearchNextFrame;
    private List<String> allAssetPaths;       // cached flat list
    private List<SearchResult> filteredResults;
    private int selectedIndex;
    private Consumer<String> onSelect;        // callback when asset is chosen

    private record SearchResult(String path, Class<?> type, int score) {}

    public void open(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        shouldOpen = true;
        searchText.set("");
        selectedIndex = 0;
        refreshAssetList();
        filterResults();
    }

    public void render() {
        if (shouldOpen) {
            float centerX = ImGui.getIO().getDisplaySizeX() / 2;
            float centerY = ImGui.getIO().getDisplaySizeY() / 3;
            ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Appearing, 0.5f, 0.5f);
            ImGui.setNextWindowSize(450, 350);
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
            focusSearchNextFrame = true;
        }

        if (ImGui.beginPopup(POPUP_ID)) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }

            if (focusSearchNextFrame) {
                ImGui.setKeyboardFocusHere();
                focusSearchNextFrame = false;
            }
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputTextWithHint("##quickSearch",
                    MaterialIcons.Search + " Type to search assets...", searchText)) {
                filterResults();
                selectedIndex = 0;
            }

            // Keyboard navigation
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow))
                selectedIndex = Math.min(selectedIndex + 1, filteredResults.size() - 1);
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow))
                selectedIndex = Math.max(selectedIndex - 1, 0);
            if (ImGui.isKeyPressed(ImGuiKey.Enter) && !filteredResults.isEmpty()) {
                confirmSelection(filteredResults.get(selectedIndex));
                ImGui.closeCurrentPopup();
            }

            ImGui.separator();

            if (ImGui.beginChild("##quickSearchResults")) {
                for (int i = 0; i < filteredResults.size() && i < 15; i++) {
                    SearchResult r = filteredResults.get(i);
                    boolean isSelected = (i == selectedIndex);
                    String icon = Assets.getIconCodepoint(r.type());
                    String display = icon + " " + r.path();

                    if (ImGui.selectable(display, isSelected)) {
                        confirmSelection(r);
                        ImGui.closeCurrentPopup();
                    }
                    if (isSelected) ImGui.setScrollHereY();
                }
            }
            ImGui.endChild();
            ImGui.endPopup();
        }
    }

    private void filterResults() {
        String query = searchText.get().trim().toLowerCase();
        if (query.isEmpty()) {
            // Show recent assets first when no query (Phase 4 integration)
            filteredResults = allAssetPaths.stream()
                .map(p -> new SearchResult(p, Assets.getTypeForPath(p), 0))
                .limit(15)
                .toList();
            return;
        }

        filteredResults = allAssetPaths.stream()
            .map(path -> {
                int score = FuzzyMatcher.score(query, path.toLowerCase());
                return score > 0 ? new SearchResult(path, Assets.getTypeForPath(path), score) : null;
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(SearchResult::score).reversed())
            .limit(15)
            .toList();
    }
}
```

**Note:** Check if `FuzzyMatcher` has a `score()` method or only `matches()`. If only `matches()`, use a simple scoring: exact match > starts-with > contains.

**Integration with AssetEditorPanel:**

```java
private final AssetQuickSearchPopup quickSearchPopup = new AssetQuickSearchPopup();

// In render(), after body, before popups:
quickSearchPopup.render();

// Shortcut
panelShortcut()
    .id("editor.asset.quickSearch")
    .displayName("Quick Open Asset")
    .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.P))
    .handler(() -> quickSearchPopup.open(path -> selectAssetByPath(path)))
    .build(),
```

**Files to create:**
- `src/main/java/com/pocket/rpg/editor/panels/AssetQuickSearchPopup.java`

**Files to modify:**
- `AssetEditorPanel.java` — add quickSearchPopup field, render call, shortcut

---

## Phase 4: Recently Opened + Favorites + Context Menu + Breadcrumb

### 4A. Recently Opened List (Persisted)

Both recently opened assets and favorites are **persisted across sessions** in `EditorConfig`, following the same pattern as `recentScenes`.

**Add to `EditorConfig.java`:**

```java
// ===== ASSET EDITOR =====

/** Recently opened asset paths (most recent first). */
@Builder.Default
private List<String> recentAssets = new ArrayList<>();

private static final int MAX_RECENT_ASSETS = 15;

/** Favorite (pinned) asset paths. */
@Builder.Default
private List<String> favoriteAssets = new ArrayList<>();

public void addRecentAsset(String path) {
    if (path == null || path.isEmpty()) return;
    String normalized = path.replace('\\', '/');
    recentAssets.remove(normalized);
    recentAssets.add(0, normalized);
    while (recentAssets.size() > MAX_RECENT_ASSETS) {
        recentAssets.remove(recentAssets.size() - 1);
    }
    save();
}

public void toggleFavoriteAsset(String path) {
    String normalized = path.replace('\\', '/');
    if (favoriteAssets.contains(normalized)) {
        favoriteAssets.remove(normalized);
    } else {
        favoriteAssets.add(normalized);
    }
    save();
}

public boolean isFavoriteAsset(String path) {
    return favoriteAssets.contains(path.replace('\\', '/'));
}
```

**Push to recent in AssetEditorPanel:**
```java
// In forceSelectAssetByPath(), after successful load:
editorConfig.addRecentAsset(path);
```

**Display in empty state (from Phase 1):**
```java
private void renderRecentlyOpenedList(float startX, float contentWidth) {
    List<String> recents = editorConfig.getRecentAssets();
    if (recents.isEmpty()) return;

    ImGui.setCursorPosX(startX);
    ImGui.text("Recently Opened");
    ImGui.spacing();

    for (String path : recents) {
        ImGui.setCursorPosX(startX);
        Class<?> type = Assets.getTypeForPath(path);
        String icon = type != null ? Assets.getIconCodepoint(type) : MaterialIcons.InsertDriveFile;
        String filename = extractFilename(path);

        if (ImGui.selectable(icon + " " + filename + "##recent_" + path)) {
            selectAssetByPath(path);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(path);
        }
    }
}
```

**Also integrate with quick search:** When the search query is empty, show recent assets first instead of alphabetical.

---

### 4B. Favorites (Persisted)

Users can pin frequently-used assets. Favorites are shown in:
1. The empty state landing page (above recents)
2. A "Pinned" section at the top of the sidebar

**Sidebar rendering — pinned section:**
```
+-------------------+
| Type [All     v]  |
| Search [________] |
|-------------------|
| Pinned            |   <-- collapsible section, only if favorites exist
|   [star] dlg_01   |
|   [star] ctrl_main|
|-------------------|
| dialogues/        |   <-- normal folder tree
|   dlg_01          |
|   dlg_02          |
| animations/       |
|   walk            |
+-------------------+
```

**How to add/remove favorites:**
- Star icon next to items in the sidebar (toggle on click)
- Context menu "Add to Favorites" / "Remove from Favorites"
- Star icon in the shell toolbar next to the asset name (when asset is loaded)

**Shell toolbar star icon:**
```
+-----------------------------------------------------------------------+
| [=] [<] [>] | dialogue_01 * [star] | [Save] [Undo] [Redo] [+New v] [x]|
+-----------------------------------------------------------------------+
                                ^
                        Click to toggle favorite
                        Filled star = favorited
                        Empty star = not favorited
```

**Implementation in sidebar:**

```java
// In renderSidebarFiles(), for each file node:
// Add a star toggle before the filename
boolean isFav = editorConfig.isFavoriteAsset(path);
if (isFav) {
    ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING); // gold star
}
String starIcon = isFav ? MaterialIcons.Star : MaterialIcons.StarBorder;
// Render star as part of the tree node label or as a small button
if (isFav) {
    ImGui.popStyleColor();
}
```

**Pinned section at top of sidebar tree:**
```java
private void renderSidebarPinnedSection() {
    List<String> favorites = editorConfig.getFavoriteAssets();
    if (favorites.isEmpty()) return;

    if (ImGui.treeNodeEx("Pinned", ImGuiTreeNodeFlags.DefaultOpen)) {
        for (String path : favorites) {
            Class<?> type = Assets.getTypeForPath(path);
            String icon = type != null ? Assets.getIconCodepoint(type) : MaterialIcons.InsertDriveFile;
            String display = icon + " " + MaterialIcons.Star + " " + stripExtension(extractFilename(path));

            int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen
                      | ImGuiTreeNodeFlags.SpanAvailWidth;
            if (path.equals(editingPath)) flags |= ImGuiTreeNodeFlags.Selected;

            ImGui.treeNodeEx(display + "##fav_" + path, flags);
            if (ImGui.isItemClicked()) {
                selectAssetByPath(path);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(path);
            }
        }
        ImGui.treePop();
    }
    ImGui.separator();
}
```

**Files to modify:**
- `EditorConfig.java` — add `recentAssets`, `favoriteAssets`, helper methods
- `AssetEditorPanel.java` — read/write from config, render in empty state and sidebar

---

### 4C. Sidebar Context Menu

Right-click on sidebar items opens a context menu.

**For files:**
```
+-------------------------+
| Open                    |
| Rename...               |
| Delete                  |
| Duplicate               |
|-------------------------|
| Add to Favorites        |  (or "Remove from Favorites" if already pinned)
|-------------------------|
| Copy Path               |
| Reveal in Browser       |
+-------------------------+
```

**For folders:**
```
+------------------------+
| New [type] here...     |
| Expand All             |
| Collapse All           |
+------------------------+
```

**Implementation — in `renderSidebarFiles()` (around line 456):**

```java
// After rendering the selectable tree node for a file:
if (ImGui.beginPopupContextItem("##ctx_" + path)) {
    if (ImGui.selectable("Open")) {
        selectAssetByPath(path);
    }
    if (ImGui.selectable("Rename...")) {
        pendingRenamePath = path;
        renameBuffer.set(stripExtension(extractFilename(path)));
        showRenamePopup = true;
    }
    if (ImGui.selectable("Delete")) {
        pendingDeletePath = path;
        showDeleteConfirmPopup = true;
    }
    if (ImGui.selectable("Duplicate")) {
        duplicateAsset(path);
    }
    ImGui.separator();
    boolean isFav = editorConfig.isFavoriteAsset(path);
    if (ImGui.selectable(isFav ? "Remove from Favorites" : "Add to Favorites")) {
        editorConfig.toggleFavoriteAsset(path);
    }
    ImGui.separator();
    if (ImGui.selectable("Copy Path")) {
        ImGui.setClipboardText(path);
    }
    if (ImGui.selectable("Reveal in Browser")) {
        EditorEventBus.get().publish(new AssetFocusRequestEvent(path));
    }
    ImGui.endPopup();
}
```

**Rename popup:**
```
+----------------------------------------------+
|    Rename Asset                               |
|                                               |
|  Name: [________________]                     |
|                                               |
|  [!] Renaming an asset that is referenced     |
|  by other assets will break those references. |
|                                               |
|  [Rename]     [Cancel]                        |
+----------------------------------------------+
```

The warning tooltip is always shown (not conditional) since we can't easily detect all references at rename time.

```java
private void renderRenamePopup() {
    if (showRenamePopup) {
        ImGui.openPopup("Rename Asset##assetRename");
        showRenamePopup = false;
    }
    if (ImGui.beginPopupModal("Rename Asset##assetRename", ImGuiWindowFlags.AlwaysAutoResize)) {
        ImGui.text("Name:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(250);
        ImGui.inputText("##renameName", renameBuffer);

        ImGui.spacing();

        // Warning about broken references
        ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING);
        ImGui.text(MaterialIcons.Warning + " Renaming an asset that is referenced by other");
        ImGui.text("  assets in the project will break those references.");
        ImGui.popStyleColor();

        ImGui.spacing();

        if (ImGui.button("Rename", 120, 0)) {
            renameAsset(pendingRenamePath, renameBuffer.get());
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }
}
```

Rename implementation:
```java
private void renameAsset(String oldPath, String newName) {
    String dir = oldPath.substring(0, oldPath.lastIndexOf('/') + 1);
    String ext = oldPath.substring(oldPath.indexOf('.'));
    String newPath = dir + newName + ext;

    Path oldFile = Paths.get(Assets.getAssetRoot(), oldPath);
    Path newFile = Paths.get(Assets.getAssetRoot(), newPath);
    Files.move(oldFile, newFile);

    EditorEventBus.get().publish(new AssetChangedEvent(oldPath, AssetChangedEvent.Type.DELETED));
    EditorEventBus.get().publish(new AssetChangedEvent(newPath, AssetChangedEvent.Type.CREATED));

    if (oldPath.equals(editingPath)) {
        editingPath = newPath;
    }
    requestSidebarRefresh();
}
```

**Delete confirmation:**
```
+---------------------------+
|    Delete Asset?           |
|                            |
|  Are you sure you want     |
|  to delete "dialogue_01"?  |
|                            |
|  This cannot be undone.    |
|                            |
|  [Delete]     [Cancel]     |
+---------------------------+
```

**Duplicate:**
```java
private void duplicateAsset(String originalPath) {
    Class<?> type = Assets.getTypeForPath(originalPath);
    Object asset = Assets.load(originalPath, type);
    if (asset == null) return;

    String dir = originalPath.substring(0, originalPath.lastIndexOf('/') + 1);
    String ext = originalPath.substring(originalPath.indexOf('.'));
    String baseName = stripExtension(extractFilename(originalPath));
    String newName = baseName + "_copy";
    String newPath = dir + newName + ext;
    int counter = 2;
    while (Files.exists(Paths.get(Assets.getAssetRoot(), newPath))) {
        newName = baseName + "_copy_" + counter++;
        newPath = dir + newName + ext;
    }

    Assets.persist(newPath, asset);
    EditorEventBus.get().publish(new AssetChangedEvent(newPath, AssetChangedEvent.Type.CREATED));
    requestSidebarRefresh();
    selectAssetByPath(newPath);
}
```

**Files to modify:**
- `AssetEditorPanel.java` — context menu rendering, rename/delete/duplicate methods, popups

---

### 4D. Breadcrumb Path Bar

Shows the folder path of the current asset between the shell toolbar and the body.

**Layout:**
```
+-----------------------------------------------------------------------+
| Shell toolbar                                                         |
|-----------------------------------------------------------------------|
| dialogues > events > dialogue_01.dialogue.json                        |  <-- breadcrumb
|-----------------------------------------------------------------------|
| separator                                                             |
|-----------------------------------------------------------------------|
| sidebar | content (with content toolbar inside)                       |
+-----------------------------------------------------------------------+
```

**Implementation:**

```java
private void renderBreadcrumb() {
    if (editingPath == null) return;

    String[] segments = editingPath.split("/");

    for (int i = 0; i < segments.length; i++) {
        boolean isLast = (i == segments.length - 1);
        String segment = segments[i];

        if (isLast) {
            ImGui.text(segment);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.LINK);
            if (ImGui.smallButton(segment + "##bc_" + i)) {
                String folderPath = String.join("/",
                    Arrays.copyOfRange(segments, 0, i + 1));
                filterSidebarToFolder(folderPath);
                if (!sidebarOpen) sidebarOpen = true;
            }
            ImGui.popStyleColor();
            ImGui.sameLine(0, 2);
            ImGui.textDisabled(">");
            ImGui.sameLine(0, 2);
        }
    }
}
```

**In the render flow:**
```java
if (visible) {
    renderShellToolbar();
    renderBreadcrumb();
    ImGui.separator();
    renderBody();  // body contains sidebar + content child (with content toolbar inside)
}
```

**Files to modify:**
- `AssetEditorPanel.java` — add `renderBreadcrumb()`, insert in render flow

---

## Phase 5: Keyboard Shortcuts Polish

### New shortcuts to register:

```java
// Quick search (Phase 3)
panelShortcut()
    .id("editor.asset.quickSearch")
    .displayName("Quick Open Asset")
    .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.P))
    .handler(() -> quickSearchPopup.open(path -> selectAssetByPath(path)))
    .build(),

// Back (Phase 3)
panelShortcut()
    .id("editor.asset.back")
    .displayName("Navigate Back")
    .defaultBinding(ShortcutBinding.alt(ImGuiKey.LeftArrow))
    .handler(() -> { if (canGoBack()) requestDirtyGuard(this::goBack); })
    .build(),

// Forward (Phase 3)
panelShortcut()
    .id("editor.asset.forward")
    .displayName("Navigate Forward")
    .defaultBinding(ShortcutBinding.alt(ImGuiKey.RightArrow))
    .handler(() -> { if (canGoForward()) requestDirtyGuard(this::goForward); })
    .build(),

// Close current asset
panelShortcut()
    .id("editor.asset.close")
    .displayName("Close Asset")
    .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.W))
    .handler(() -> {
        if (editingAsset != null) requestDirtyGuard(this::clearEditingAsset);
    })
    .build(),
```

**Updated Ctrl+N handler:**
```java
panelShortcut()
    .id("editor.asset.new")
    .displayName("New Asset")
    .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.N))
    .handler(() -> {
        if (activeContent != null) {
            activeContent.onNewRequested();
        } else {
            pendingOpenNewDropdown = true;
        }
    })
    .build(),
```

### Full shortcut table:

| Shortcut | Action | Scope | Phase |
|----------|--------|-------|-------|
| `Ctrl+N` | New asset (content-specific or dropdown) | Panel focused | Phase 1 (update) |
| `Ctrl+S` | Save | Panel focused | Existing |
| `Ctrl+Z` | Undo | Panel focused | Existing |
| `Ctrl+Shift+Z` / `Ctrl+Y` | Redo | Panel focused | Existing |
| `F5` | Refresh sidebar | Panel focused | Existing |
| `Ctrl+P` | Quick-search popup | Panel focused | Phase 3 |
| `Alt+Left` | Navigate back | Panel focused | Phase 3 |
| `Alt+Right` | Navigate forward | Panel focused | Phase 3 |
| `Ctrl+W` | Close current asset | Panel focused | Phase 5 |

**Files to modify:**
- `AssetEditorPanel.java` — update `provideShortcuts()`

---

## Files Summary

### New files:
| File | Phase | Purpose |
|------|-------|---------|
| `editor/panels/AssetPopupViewer.java` | 2 | Floating asset viewer window |
| `editor/panels/AssetQuickSearchPopup.java` | 3 | Ctrl+P quick search |

### Modified files:
| File | Phases | Changes |
|------|--------|---------|
| `editor/panels/AssetEditorPanel.java` | 1-5 | Toolbar split, empty state, history, breadcrumb, shortcuts, popup viewers, context menu, recent list, favorites |
| `editor/panels/AssetEditorShell.java` | 2 | Add `openPopupViewer()` method |
| `editor/panels/content/DialogueEditorContent.java` | 2 | Variables/Events use popup viewer |
| `editor/panels/content/ItemRegistryEditorContent.java` | 1 | Add `onNewRequested()` |
| `editor/panels/content/TrainerRegistryEditorContent.java` | 1 | Add `onNewRequested()` |
| `editor/panels/content/ShopRegistryEditorContent.java` | 1 | Add `onNewRequested()` (if has creation info) |
| `editor/core/EditorConfig.java` | 4 | Add `recentAssets`, `favoriteAssets`, persistence methods |

---

## Verification

### Phase 1 Testing:
- [ ] Open Asset Editor with no asset loaded — see empty state with create cards
- [ ] Click a "Create" card — triggers creation dialog for that type
- [ ] Click `[+ New v]` dropdown — shows all creatable types
- [ ] Select a type from dropdown — opens creation dialog
- [ ] Ctrl+N with no asset loaded — opens dropdown
- [ ] Shell toolbar always visible (hamburger, back/forward disabled, new dropdown)
- [ ] Content toolbar renders inside the content child window (not in shell area)
- [ ] Content types with no toolbar extras don't waste vertical space

### Phase 2 Testing:
- [ ] Open a Dialogue asset
- [ ] Click "Variables" — floating window opens with variables editor
- [ ] Edit a variable in the popup — popup shows dirty state
- [ ] Save popup — variable changes persist
- [ ] Close popup — dialogue is still loaded, unchanged
- [ ] Click "Events" — second floating window opens
- [ ] Both popups can be open simultaneously
- [ ] Click "Variables" again while popup already open — focuses existing popup

### Phase 3 Testing:
- [ ] Open asset A, then asset B — back button becomes enabled
- [ ] Click back — returns to asset A, forward button enabled
- [ ] Click forward — returns to asset B
- [ ] Alt+Left / Alt+Right — same as back/forward buttons
- [ ] Open asset C while at A (after going back) — forward history truncated
- [ ] Ctrl+P — quick search popup appears, centered
- [ ] Type search query — results filter with fuzzy matching
- [ ] Arrow keys + Enter — navigate and select results
- [ ] Escape — closes popup
- [ ] Select result — opens that asset in the editor

### Phase 4 Testing:
- [ ] Open several assets, close editor, reopen — "Recently Opened" persists
- [ ] Click a recent entry — opens that asset
- [ ] Right-click sidebar file → "Add to Favorites" — star appears, asset shows in Pinned section
- [ ] Favorites persist across editor sessions
- [ ] Star icon in shell toolbar toggles favorite state
- [ ] Right-click sidebar file — context menu with Open/Rename/Delete/Duplicate/Favorites/Copy Path
- [ ] Rename shows warning about breaking references
- [ ] Delete works with confirmation popup
- [ ] Duplicate creates "_copy" suffix and auto-opens
- [ ] Breadcrumb shows between shell toolbar and body
- [ ] Click breadcrumb folder segment — sidebar opens and filters to that folder

### Phase 5 Testing:
- [ ] All keyboard shortcuts work as documented
- [ ] Ctrl+W closes current asset (with dirty guard)
- [ ] No shortcut conflicts with existing bindings
