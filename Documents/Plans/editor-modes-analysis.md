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

### Option C: Eliminate Modes (Unified Tools)

**Concept:** Instead of modes, make all tools available simultaneously with different keybindings or tool-specific behavior.

**Implementation:**
- Remove the mode concept entirely
- Selection tool always selects entities
- Brush tool always paints tiles (when valid)
- Collision brush always paints collision
- Tools determine behavior based on what's under cursor
- Hold modifier (e.g., Shift) to access secondary tool behavior

**Pros:**
- No mode confusion
- Direct tool selection
- Similar to professional tools (Photoshop, Aseprite)

**Cons:**
- Major architectural change
- More tools visible at once (UI clutter)
- May need more keyboard shortcuts
- Some operations conflict (click on entity while brush selected)

**Implementation complexity:** Very High

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

**Recommended approach: Hybrid of Option B + D + E**

1. **Better feedback (from B)**
   - Add mode indicator overlay in viewport
   - Show mode in Inspector header when empty

2. **Entity selection persists (from D)**
   - Keep entity selected when switching to Tilemap/Collision
   - Inspector still shows entity properties in any mode
   - Clicking entity in hierarchy always selects it (regardless of mode)

3. **Quick return (from E)**
   - ESC always returns to Entity mode
   - Add subtle "Press E for Entity mode" hint in non-Entity modes

4. **Viewport click enhancement (from D)**
   - In Tilemap/Collision mode, right-click on entity → shows context menu with "Select Entity" option
   - Or: Ctrl+Click selects entity without changing mode

This hybrid approach:
- Preserves explicit mode control (predictable)
- Reduces friction with persistent selection
- Provides clear feedback
- Offers escape hatches without automatic switching

---

## Files Affected (for implementation)

| File | Changes |
|------|---------|
| `EditorModeManager.java` | Minor - add ESC return handling |
| `EditorContext.java` | Persist entity selection across modes |
| `HierarchySelectionHandler.java` | Don't clear entity selection on mode switch |
| `SceneViewport.java` | Add mode overlay indicator |
| `InspectorPanel.java` | Show entity in any mode, add mode hint |
| `ViewportInputHandler.java` | Add Ctrl+Click for entity selection in non-Entity modes |
| `SelectionTool.java` | Support activation from any mode |

---

## Questions for User

1. **Priority:** Which pain point is most urgent?
   - Accidentally painting when trying to select?
   - Not knowing current mode?
   - Having to manually switch back to Entity mode?

2. **Workflow preference:** Do you prefer:
   - Automatic mode switching (more magic, less control)?
   - Explicit mode switching with better escape options?

3. **Entity selection in non-Entity modes:** Should clicking an entity in viewport while in Tilemap mode:
   - Do nothing (current behavior)?
   - Select the entity but stay in Tilemap mode?
   - Select the entity and switch to Entity mode?
