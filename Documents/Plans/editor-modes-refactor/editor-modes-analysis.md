# Editor Modes Analysis

This document analyzes the current three editor modes (Entity, Tilemap, Collision), their navigation challenges, and potential solutions.

## Current Architecture

### Mode Definition

The editor has three modes defined in `EditorModeManager.java`:

| Mode | Purpose | Default Tool | Shortcut |
|------|---------|--------------|----------|
| **ENTITY** | Select and manipulate GameObjects | Select | E |
| **TILEMAP** | Paint tiles on tilemap layers | Brush | M |
| **COLLISION** | Paint collision data | Collision Brush | N |

### How Mode Switching Works

Mode changes flow through `EditorModeManager` which notifies listeners:

```
User Action → EditorModeManager.switchTo() → Notify Listeners
                                                   ↓
                                          ┌────────┴────────┐
                                          ↓                 ↓
                              HierarchySelectionHandler   EditorContext
                              (sync selection state)      (notify UI listeners)
```

**Current mode switch triggers:**

1. **Keyboard shortcuts** (E/M/N) via `ShortcutRegistry`
2. **Mode dropdown** in `SceneViewToolbar`
3. **Hierarchy clicks**:
   - Click "Tilemap Layers" → switches to TILEMAP mode
   - Click "Collision Map" → switches to COLLISION mode
   - Click an entity → switches to ENTITY mode

### Mode-Dependent Behavior

| Aspect | Entity Mode | Tilemap Mode | Collision Mode |
|--------|-------------|--------------|----------------|
| Tools shown | Select, Place Entity | Brush, Eraser, Fill, Rectangle, Picker | Collision versions of same |
| Panels visible | Asset Browser, Prefab Browser | Tileset Palette | Collision Panel |
| Viewport clicks | Select/move entities | Paint tiles | Paint collision |
| Hierarchy selection | Entity selected | "Tilemap Layers" selected | "Collision Map" selected |

---

## Current Problems

### 1. No Automatic Mode Switching on UI Interaction

**Problem:** When in Tilemap or Collision mode, clicking on UI elements (inspector fields, panels, menus) does NOT switch back to Entity mode. This creates friction:

- User selects an entity in the hierarchy (switches to Entity mode)
- User switches to Tilemap mode to paint some tiles
- User wants to edit entity properties in Inspector
- User clicks on Inspector → still in Tilemap mode
- Viewport clicks still paint tiles instead of selecting entities

**Current behavior:** Mode only changes via explicit triggers (shortcuts, mode dropdown, hierarchy special items).

### 2. Mode Confusion

Users may not realize which mode is active, leading to:
- Accidentally painting tiles when trying to select entities
- Wondering why the Inspector shows nothing (no entity selected in non-Entity modes)
- Losing entity selection when switching to Tilemap/Collision mode

### 3. Inconsistent Navigation Patterns

- Clicking an entity in hierarchy → switches to Entity mode ✓
- Clicking "Tilemap Layers" → switches to Tilemap mode ✓
- Clicking an entity in viewport (while in Entity mode) → works ✓
- Clicking an entity in viewport (while in Tilemap mode) → paints tile instead ✗
- Clicking Inspector to edit entity → does not switch mode ✗

---

## Solution Approaches

### Option A: Context-Sensitive Auto-Switching

**Concept:** Automatically switch modes based on user intent inferred from context.

**Implementation:**
- Click entity in hierarchy → Entity mode (already works)
- Click entity in viewport → Entity mode (if entity under cursor)
- Click Inspector when entity is conceptually selected → Entity mode
- Click Tileset Palette → Tilemap mode
- Click Collision Panel → Collision mode

**Pros:**
- Most intuitive for users
- Reduces friction
- "Does what I mean" behavior

**Cons:**
- Can be surprising/unpredictable
- May conflict with intentional workflows (e.g., user wants to stay in Tilemap mode while checking Inspector)
- Complex to implement correctly
- Hard to define "entity under cursor" click detection in non-Entity mode

**Implementation complexity:** High

---

### Option B: Explicit Mode Toggle with Better Feedback

**Concept:** Keep explicit mode switching but improve mode visibility and add quick-switch options.

**Implementation:**
- Add prominent mode indicator in viewport (not just dropdown)
- Add visual overlay when in Tilemap/Collision mode ("Tilemap Mode" watermark)
- ESC key returns to Entity mode from any mode
- Double-click entity in hierarchy always switches to Entity mode and selects
- Add mode indicator in Inspector header when no entity selected

**Pros:**
- Predictable behavior
- Users learn explicit control
- No accidental mode switches
- Lower implementation complexity

**Cons:**
- Requires more user actions
- Steeper learning curve
- Still possible to get "stuck" in wrong mode

**Implementation complexity:** Medium

---

### Option C: Eliminate Modes (Unified Tools) — EXPANDED

**Concept:** Remove the mode system entirely. Decouple "what is selected" from "what tool is active". All tools coexist, and the active tool determines viewport click behavior while selection persists independently.

---

#### C.1 Core Architecture Change

**Current model (mode-based):**
```
Mode → determines → Tool set + Panel visibility + Selection behavior + Inspector content
```

**New model (tool-based):**
```
Selection (persistent) → determines → Inspector content
Active Tool (independent) → determines → Viewport click behavior
Tool Category → determines → Which auxiliary panel is visible
```

**Key principle:** Selection and tool are orthogonal concepts.

---

#### C.2 Selection System (Decoupled)

Selection becomes a first-class concept independent of tools:

| Selection Type | What it means | Inspector shows |
|----------------|---------------|-----------------|
| Entity selected | One or more GameObjects | Entity components |
| Tilemap layer selected | A specific layer | Layer properties (name, z-order, tileset) |
| Collision layer selected | Collision at z-level | Collision layer properties |
| Camera selected | Scene camera | Camera settings |
| Nothing selected | Empty | "No selection" message |

**Selection rules:**
- Clicking entity in Hierarchy → selects entity (always)
- Clicking entity in viewport with Select tool → selects entity
- Clicking "Tilemap Layers" in Hierarchy → selects tilemap (shows layer properties)
- Clicking "Collision Map" in Hierarchy → selects collision layer
- Selection persists when switching tools
- ESC clears selection

---

#### C.3 Tool System (Panel-Driven Visibility)

**Key insight from Unity:** Tile Palette tools only appear when the Tile Palette window is open. This reduces clutter while maintaining a modeless design.

**Tool visibility rules:**

| Panel State | Tools Visible in Toolbar |
|-------------|--------------------------|
| Only base panels open | Entity tools only: `[Select][Move][Place]` |
| Tileset Palette open | Entity + Tile tools: `[Select][Move][Place] │ [Brush][Eraser][Fill][Rect][Pick]` |
| Collision Panel open | Entity + Collision tools: `[Select][Move][Place] │ [C.Brush][C.Eraser][C.Fill][C.Rect][C.Pick]` |
| Both panels open | All tools visible (rare case) |

**Toolbar appearance:**

```
Base state (no painting panels):
┌─────────────────────────┐
│ [Select][Move][Place]   │
└─────────────────────────┘

With Tileset Palette open:
┌───────────────────────────────────────────────────────────┐
│ [Select][Move][Place] │ [Brush][Eraser][Fill][Rect][Pick] │
│                       │  ← appears when panel opens        │
└───────────────────────────────────────────────────────────┘

With Collision Panel open:
┌─────────────────────────────────────────────────────────────────┐
│ [Select][Move][Place] │ [C.Brush][C.Eraser][C.Fill][C.Rect][C.Pick] │
└─────────────────────────────────────────────────────────────────┘
```

**Panel open/close behavior:**

| Action | Result |
|--------|--------|
| Open Tileset Palette | Tile tools appear in toolbar |
| Close Tileset Palette | Tile tools disappear, active tool switches to Select if was tile tool |
| Open Collision Panel | Collision tools appear in toolbar |
| Close Collision Panel | Collision tools disappear, active tool switches to Select if was collision tool |

**Tool categories:**

| Category | Tools | Viewport behavior | Visible when |
|----------|-------|-------------------|--------------|
| **Entity** | Select, Move, Place Entity | Operate on entities | Always |
| **Tile Painting** | Brush, Eraser, Fill, Rectangle, Picker | Paint on active tilemap layer | Tileset Palette open |
| **Collision Painting** | Collision Brush, Eraser, Fill, Rectangle, Picker | Paint collision data | Collision Panel open |

**Tool determines click behavior:**

| Active Tool | Left-click in viewport |
|-------------|------------------------|
| Select | Select entity under cursor, or clear selection |
| Move | Start dragging selected entity |
| Place Entity | Place prefab at cursor |
| Tile Brush | Paint tile at cursor |
| Tile Eraser | Erase tile at cursor |
| Collision Brush | Paint collision at cursor |
| etc. | ... |

**Keyboard shortcut behavior:**

| Shortcut | Panel closed | Panel open |
|----------|--------------|------------|
| B (Tile Brush) | Opens Tileset Palette, activates Brush | Activates Brush |
| C (Collision Brush) | Opens Collision Panel, activates Collision Brush | Activates Collision Brush |
| V (Select) | Activates Select (panels stay as-is) | Activates Select (panels stay as-is) |

This gives a clean workflow:
- Press B → Tileset Palette opens, you're ready to paint
- Press V → Back to Select tool, palette stays open for reference
- Close palette → Automatically back to Select tool

---

#### C.4 "Active Target" Concept (from Unity)

Instead of modes, introduce **Active Target** for painting tools:

- **Active Tilemap Layer** - which layer tile tools paint on
- **Active Collision Z-Level** - which z-level collision tools paint on

These are selected via:
1. Clicking layer in Hierarchy (also selects it for Inspector)
2. Dropdown in Tileset Palette panel
3. Keyboard shortcuts ([ and ] to cycle layers)

**Visual feedback:**
- Active layer highlighted in Hierarchy
- Layer name shown in toolbar when painting tool active
- Subtle overlay on viewport showing active layer bounds

---

#### C.5 Panel Visibility (User-Controlled, Tools Follow)

**Inverted from original design:** Panels are user-controlled. Tools follow panel state, not the other way around.

**Always visible (core panels):**
- Hierarchy (with all sections expanded)
- Inspector (shows current selection)
- Scene Viewport
- Asset Browser (for entity placement)

**Toggleable painting panels:**

| Panel | How to open | How to close | Effect on tools |
|-------|-------------|--------------|-----------------|
| Tileset Palette | Menu, shortcut (B), or click "Tilemap Layers" in Hierarchy | X button, or ESC when tile tool active | Tile tools appear/disappear |
| Collision Panel | Menu, shortcut (C), or click "Collision Map" in Hierarchy | X button, or ESC when collision tool active | Collision tools appear/disappear |

**Panel states:**

```
┌─────────────────────────────────────────────────────────────┐
│  Hierarchy  │  Scene Viewport  │  Inspector                 │
│             │                  │  (shows selection)         │
│  - Camera   │  [toolbar here]  │                            │
│  - Entities │                  │                            │
│  - Tilemap  │                  ├────────────────────────────┤
│  - Collision│                  │  Tileset Palette           │
│             │                  │  (when open)               │
│             │                  ├────────────────────────────┤
│             │                  │  Collision Panel           │
│             │                  │  (when open)               │
└─────────────────────────────────────────────────────────────┘
```

**Hierarchy click shortcuts:**

| Click target | Action |
|--------------|--------|
| "Tilemap Layers" | Opens Tileset Palette (if closed), selects tilemap in Inspector |
| "Collision Map" | Opens Collision Panel (if closed), selects collision in Inspector |
| Any entity | Selects entity in Inspector (panels unchanged) |

This matches Unity behavior where:
- Opening Tile Palette enables tile tools
- Closing Tile Palette disables tile tools (reverts to Select)
- You control when painting is available by opening/closing the palette

---

#### C.6 Keyboard Shortcuts (Revised)

**Tool shortcuts (direct selection):**

| Key | Tool | Notes |
|-----|------|-------|
| V | Select | Entity selection tool |
| G | Move | Move selected entity (like Unity) |
| P | Place Entity | Place prefab |
| B | Tile Brush | Auto-activates tilemap target if none |
| E | Tile Eraser | |
| F | Tile Fill | |
| R | Tile Rectangle | |
| I | Tile Picker | |
| C | Collision Brush | Auto-activates collision target if none |
| X | Collision Eraser | |

**Modifier shortcuts:**
| Modifier | Effect |
|----------|--------|
| Ctrl + Click | With painting tool: pick tile/collision under cursor |
| Shift + Click | With painting tool: erase instead of paint |
| Alt + Click | With any tool: temporarily switch to Select, click to select entity |

**Navigation:**
| Key | Action |
|-----|--------|
| [ | Previous tilemap layer / collision z-level |
| ] | Next tilemap layer / collision z-level |
| ESC | Clear selection, switch to Select tool |

---

#### C.7 Workflow Examples

**Example 1: Edit entity while painting tiles**
1. User has Tile Brush active, painting tiles
2. User sees an entity they want to adjust
3. User clicks entity in Hierarchy → entity selected, Inspector shows entity
4. User edits properties in Inspector
5. User continues painting (Tile Brush still active)
6. No mode switch needed!

**Example 2: Quick entity selection from painting**
1. User has Tile Brush active
2. User holds Alt + clicks entity in viewport
3. Entity selected, Inspector updates
4. User releases Alt, continues painting

**Example 3: Switch from entity work to tile painting**
1. User has Select tool, entity selected
2. User presses B for Tile Brush
3. Tileset Palette appears
4. User paints tiles
5. Entity still selected in Inspector (can see/edit properties)

---

#### C.8 Implementation Plan

**Phase 1: Decouple Selection from Mode**
1. Create `EditorSelectionManager` to handle selection independently
2. Move selection state out of `HierarchySelectionHandler`
3. Make Inspector always show current selection regardless of mode
4. Remove selection clearing from mode switch code
5. Test: Switch modes, verify entity stays selected in Inspector

**Phase 2: Panel-Driven Tool Visibility**
1. Add `isPanelOpen()` checks for Tileset Palette and Collision Panel
2. Update `SceneViewToolbar` to show tools based on panel state:
   - Entity tools: always visible
   - Tile tools: visible when Tileset Palette open
   - Collision tools: visible when Collision Panel open
3. Add panel open/close listeners to toolbar
4. When panel closes, if active tool was from that category, switch to Select
5. Test: Open/close panels, verify tools appear/disappear correctly

**Phase 3: Shortcut-Opens-Panel Behavior**
1. Update tile tool shortcuts (B, E, F, R, I) to open Tileset Palette if closed
2. Update collision tool shortcuts (C, X, etc.) to open Collision Panel if closed
3. Add hierarchy click behavior: clicking "Tilemap Layers" opens palette
4. Test: Press B with palette closed, verify it opens and Brush activates

**Phase 4: Remove Mode System**
1. Identify all `EditorModeManager` usages
2. Replace mode checks with panel-open checks or tool-category checks
3. Remove mode dropdown from toolbar
4. Deprecate/delete `EditorModeManager`
5. Update `EditorContext` to remove mode methods
6. Test: Full workflow without modes

**Phase 5: Polish**
1. Add Alt+Click for quick entity select from any tool
2. Add ESC behavior: close active painting panel OR clear selection
3. Add visual feedback for active tilemap layer in viewport
4. Update keyboard shortcut documentation
5. Test: All workflow examples from C.7

---

#### C.9 Files Affected (Detailed)

| File | Phase | Changes |
|------|-------|---------|
| **NEW: `EditorSelectionManager.java`** | 1 | New class managing selection state (entity, layer, collision) independently |
| `EditorContext.java` | 1, 4 | Add selection manager; later remove mode methods |
| `HierarchySelectionHandler.java` | 1 | Delegate selection to SelectionManager, don't clear on mode switch |
| `InspectorPanel.java` | 1 | Always show current selection, remove mode checks |
| `SceneViewToolbar.java` | 2, 4 | Show tools based on panel state; remove mode dropdown |
| `TilesetPalettePanel.java` | 2 | Add `isOpen()` method, notify on open/close |
| `CollisionPanel.java` | 2 | Add `isOpen()` method, notify on open/close |
| `EditorUIController.java` | 2, 3 | Track panel open state, handle panel-opens-on-shortcut |
| `EditorToolController.java` | 2 | Remove mode filtering, add tool category checks |
| `EditorShortcutHandlersImpl.java` | 3 | Update shortcuts to open panels when needed |
| `HierarchyPanel.java` | 3 | Click "Tilemap Layers" opens palette, click "Collision" opens panel |
| `EditorModeManager.java` | 4 | **DELETE** entirely |
| `ViewportInputHandler.java` | 5 | Add Alt+Click handling for quick select |
| `SelectionTool.java` | - | Unchanged (works same as before) |
| `TileBrushTool.java` (and others) | - | Minor: validate active target exists |

---

#### C.10 Migration Considerations

**Breaking changes:**
- Mode shortcuts (E/M/N) will change meaning or be removed
- Users accustomed to modes need to learn new model
- Some workflows may feel different initially

**Backward compatibility:**
- Could keep E/M/N as "quick switch" shortcuts that:
  - E → Switch to Select tool
  - M → Switch to Tile Brush + select first tilemap layer
  - N → Switch to Collision Brush + select collision layer
- This preserves muscle memory while using new architecture

**Settings migration:**
- No persistent settings affected (modes aren't saved)
- Keyboard shortcut config may need updates

---

#### C.11 Pros and Cons (Revised)

**Pros:**
- Eliminates mode confusion entirely
- Matches Unity mental model (familiar to many users)
- Selection and tools are orthogonal (more flexible)
- Inspector always useful (shows selection)
- Faster workflows (no mode switching overhead)
- More professional feel

**Cons:**
- Significant refactoring effort
- More tools visible (mitigated by grouping)
- Learning curve for existing users
- Need to handle "no active target" edge cases
- More keyboard shortcuts to remember (mitigated by logical grouping)

**Implementation complexity:** High (but well-defined steps)

---

### Option D: Smart Fallback Mode

**Concept:** Non-Entity modes "fall through" to Entity mode when the action doesn't make sense for the current mode.

**Implementation:**
- In Tilemap/Collision mode, if user clicks on an entity in viewport → select entity AND stay in current mode
- Inspector always shows selected entity regardless of mode
- Entity selection persists across mode switches
- Clicking empty Inspector area doesn't switch modes

**Pros:**
- Best of both worlds
- Entity manipulation works in any mode
- Tile/collision work continues without interruption
- Maintains clear mode concept

**Cons:**
- Mixed behavior might confuse users
- Selection highlighting may conflict with tile overlay
- Need to handle entity selection state separately from mode

**Implementation complexity:** Medium-High

---

### Option E: Modal with Quick-Return

**Concept:** Keep modes but add a "return to Entity mode" button/shortcut that's always visible.

**Implementation:**
- Always show "← Entity Mode" button in non-Entity modes
- ESC returns to Entity mode (partially exists)
- Add floating return button in viewport when in Tilemap/Collision mode
- Remember last selected entity, restore on return

**Pros:**
- Clear mental model
- Easy to return
- Minimal changes to existing architecture
- Low risk

**Cons:**
- Still requires explicit action
- Takes up UI space
- Doesn't solve the fundamental navigation issue

**Implementation complexity:** Low

---

## Recommendation

**If choosing Option C (Eliminate Modes):**

This is the most thorough solution and aligns with Unity's proven UX pattern. The implementation is broken into 5 phases (see C.8) that can be done incrementally:

| Phase | Effort | Risk | Deliverable |
|-------|--------|------|-------------|
| 1: Decouple Selection | Medium | Low | Selection persists, Inspector always works |
| 2: Unify Tools | Medium | Medium | All tools visible, no mode filtering |
| 3: Panel Visibility | Low | Low | Panels react to tool category |
| 4: Remove Modes | Low | Medium | Delete mode system entirely |
| 5: Polish | Medium | Low | Alt+Click, visual feedback, shortcuts |

**Suggested approach:** Implement phases 1-2 first. This gives the biggest UX improvement. Phases 3-5 can follow iteratively.

**Alternative: Hybrid B+D+E (lower effort)**

If Option C feels too large, the hybrid approach preserves modes but fixes the main pain points:
- Selection persists across modes
- Inspector always shows selected entity
- ESC returns to Entity mode
- Ctrl+Click to select entities from any mode

This is ~40% of the effort for ~70% of the benefit.

---

## Next Steps

1. **Decide:** Option C (full refactor) or Hybrid B+D+E (incremental)?
2. **If Option C:** Start with Phase 1 (EditorSelectionManager)
3. **Prototype:** Test the new selection model before removing modes
4. **Iterate:** Each phase is independently testable

---

## Questions

1. **Scope:** Do you want to proceed with full Option C, or start with the hybrid approach?

2. **Timeline:** Is this a priority feature, or something to implement gradually?

3. **Toolbar design:** For unified tools, prefer:
   - Grouped in one row with separators?
   - Tabbed groups (Entity | Tiles | Collision)?
   - Dropdown per category?
