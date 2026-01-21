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

#### C.3 Tool System (Unified)

All tools visible in a single toolbar, grouped by category:

```
┌─────────────────────────────────────────────────────────────────┐
│ [Select][Move][Place] │ [Brush][Eraser][Fill][Rect][Pick] │ ... │
│   Entity Tools        │        Painting Tools              │     │
└─────────────────────────────────────────────────────────────────┘
```

**Tool categories:**

| Category | Tools | Viewport behavior | Requires |
|----------|-------|-------------------|----------|
| **Entity** | Select, Move, Place Entity | Operate on entities | Nothing |
| **Tile Painting** | Brush, Eraser, Fill, Rectangle, Picker | Paint on active tilemap layer | Active layer selected |
| **Collision Painting** | Collision Brush, Eraser, Fill, Rectangle, Picker | Paint collision data | Collision layer selected |

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

#### C.5 Panel Visibility (Tool-Driven)

Panels appear based on **active tool category**, not mode:

| Active Tool | Panels visible |
|-------------|----------------|
| Entity tools (Select, Move, Place) | Asset Browser, Prefab Browser |
| Tile painting tools | Tileset Palette |
| Collision painting tools | Collision Panel |

**Always visible:**
- Hierarchy (with all sections expanded)
- Inspector (shows current selection)
- Scene Viewport

**Panel behavior:**
- Panels slide in/out smoothly on tool change
- Or: All panels docked, just highlighted based on relevance
- User can pin panels to always show

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
3. Make Inspector always show current selection
4. Remove selection clearing from mode switch code

**Phase 2: Unify Tool System**
1. Remove tool filtering by mode in `SceneViewToolbar`
2. Show all tools in single toolbar (grouped)
3. Update `ToolManager` to not care about modes
4. Add "Active Target" concept for painting tools

**Phase 3: Update Panel Visibility**
1. Change panel visibility logic from mode-based to tool-based
2. Add smooth transitions or highlighting
3. Allow panel pinning

**Phase 4: Remove Mode System**
1. Deprecate `EditorModeManager`
2. Remove mode dropdown from toolbar
3. Update all mode listeners to use new patterns
4. Remove mode shortcuts (E/M/N) or repurpose

**Phase 5: Polish**
1. Add Alt+Click for quick select
2. Add visual feedback for active target
3. Update keyboard shortcuts
4. Test all workflows

---

#### C.9 Files Affected (Detailed)

| File | Changes |
|------|---------|
| **NEW: `EditorSelectionManager.java`** | New class managing selection state (entity, layer, collision) |
| `EditorModeManager.java` | **DELETE** or deprecate entirely |
| `EditorContext.java` | Remove mode methods, add selection manager reference |
| `HierarchySelectionHandler.java` | Simplify to just handle clicks, delegate to SelectionManager |
| `HierarchyPanel.java` | Remove mode-based highlighting, use selection-based |
| `SceneViewToolbar.java` | Remove mode dropdown, show all tools grouped |
| `EditorToolController.java` | Remove mode filtering, add tool category concept |
| `EditorUIController.java` | Change panel visibility from mode-based to tool-based |
| `ViewportInputHandler.java` | Add Alt+Click handling for quick select |
| `InspectorPanel.java` | Always show selection, remove mode checks |
| `SelectionTool.java` | Works same as before |
| `TileBrushTool.java` (and others) | Add "active target" validation |
| `EditorShortcutHandlersImpl.java` | Update shortcuts for new system |

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
