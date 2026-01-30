# Animation Editor Phase 4.5: Enhancements

## Issues Identified

### Visual/UX Issues
1. ✅ **Play/Stop buttons** - Not colored, hard to spot play state
2. ✅ **Unsaved indicator** - Should be yellow like scene name in main menu
3. ✅ **Timeline** - Hard to spot and understand how it works
4. ✅ **Drag-and-drop** - Can't insert between frames, no visual feedback
5. ✅ **Duration field** - Label should be on left (use PrimitiveEditors)
6. ✅ **Layout** - Properties should be on left, preview on right

### Redundancy Issues
1. ✅ **Double menu bar** - File/Edit menu not needed, toolbar is sufficient

### Missing Features
1. ✅ **Right-click frame** - No option to change sprite
2. ✅ **Asset browser drag** - Can't drag sprite from asset browser to timeline

---

## Implemented Layout

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Animation Editor                                                             [x] │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [+ New] [🗑 Delete] [💾 Save] [↻ Refresh]  |  [▶ PLAY] [⏹ STOP] 1.0x │ ▾ player_walk* │
├────────────────────┬─────────────────────┬───────────────────────────────────────┤
│ PROPERTIES         │ CURRENT FRAME       │  PREVIEW                              │
│ ┌────────────────┐ │ ┌─────────────────┐ │ ┌───────────────────────────────────┐ │
│ │Name: walk      │ │ │ Frame 2         │ │ │                                   │ │
│ │Looping: [x]    │ │ │                 │ │ │           ┌─────────┐             │ │
│ │                │ │ │ Duration: 0.10s │ │ │           │         │             │ │
│ │Frames: 4       │ │ │                 │ │ │           │  Sprite │             │ │
│ │Duration: 0.4s  │ │ │ Sprite:         │ │ │           │         │             │ │
│ │                │ │ │ [player#2] [📁] │ │ │           └─────────┘             │ │
│ │                │ │ │                 │ │ │                                   │ │
│ └────────────────┘ │ └─────────────────┘ │ │            [0.5x] [Fit]           │ │
│                    │                     │ └───────────────────────────────────┘ │
├────────────────────┴─────────────────────┴───────────────────────────────────────┤
│ TIMELINE                                        [Track][Strip]  Frame 2/4  0.15s │
│ ┌────────────────────────────────────────────────────────────────────────────┐   │
│ │ 0.0    0.1    0.2    0.3    0.4                                            │   │
│ │ ▼ (playhead)                                                               │   │
│ │ ┌──────────┬──────────┬───────────────┬──────────┐                         │   │
│ │ │    🖼    │    🖼    │      🖼       │    🖼    │ [+]                      │   │
│ │ └──────────┴──────────┴───────────────┴──────────┘                         │   │
│ └────────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Key changes from original design:**
- **Animation list as dropdown** in toolbar (right side) - saves vertical space
- **Dropdown turns yellow** when animation has unsaved changes (with asterisk)
- **Three columns:** Properties | Current Frame | Preview
- **Current Frame panel** contains frame inspector (duration, sprite picker)
- **Preview** shows current frame sprite with zoom controls
- **Play/Stop buttons** with green/red color coding and pulsing effect
- **Timeline** with Track/Strip mode toggle

**Behavior notes:**
- Changing animation with unsaved changes triggers confirmation dialog
- Creating new animation with unsaved changes triggers confirmation dialog
- Clicking frame during playback selects it but doesn't stop playback
- New frames have empty sprite by default (no sprite selected)
- Empty frames are allowed (can save animations with empty sprite paths)

---

## Timeline Layout Proposals

### Option A: Horizontal Strip (Current, Enhanced)

```
┌──────────────────────────────────────────────────────────────────────┐
│  ▼                                                                   │
│ ┌────┐  ┌────┐  ┌────┐  ┌────┐                                      │
│ │ 🖼 │  │ 🖼 │  │ 🖼 │  │ 🖼 │  [+]                                  │
│ │0.1s│  │0.1s│  │0.15│  │0.1s│                                       │
│ └────┘  └────┘  └────┘  └────┘                                       │
│   1       2       3       4                                          │
│                  ▲                                                   │
│            (selected)                                                │
└──────────────────────────────────────────────────────────────────────┘
```

**Enhancements:**
- Frame numbers below thumbnails
- Selected frame has colored border + arrow indicator
- Current playback frame has playhead marker above
- Drop zones between frames (visible on drag)

**Pros:** Familiar, compact
**Cons:** Limited vertical space for thumbnails

---

### Option B: Filmstrip with Drop Zones

```
┌──────────────────────────────────────────────────────────────────────┐
│ ║ ┌────┐ ║ ┌────┐ ║ ┌────┐ ║ ┌────┐ ║                               │
│ ║ │ 🖼 │ ║ │ 🖼 │ ║ │ 🖼 │ ║ │ 🖼 │ ║  [+]                           │
│ ║ └────┘ ║ └────┘ ║ └────┘ ║ └────┘ ║                               │
│ ║  0.1s  ║  0.1s  ║  0.15s ║  0.1s  ║                               │
│ ║   1    ║   2    ║   3    ║   4    ║                               │
│     ↑         ↑         ↑         ↑                                 │
│  (drop)   (drop)    (drop)    (drop)                                │
└──────────────────────────────────────────────────────────────────────┘
```

**Features:**
- Vertical separators (║) are drop zones
- Dragging a frame or sprite highlights drop zones
- Insert position clearly visible
- Sprocket holes aesthetic (filmstrip look)

**Pros:** Clear drop targets, professional look
**Cons:** Takes more horizontal space

---

### Option C: Track-Based Timeline (Like Video Editors)

```
┌──────────────────────────────────────────────────────────────────────┐
│ Time:  0.0   0.1   0.2   0.3   0.4   0.5                            │
│        |     |     |     |     |     |                              │
│        ▼                                                             │
│ ┌──────┬──────┬────────┬──────┐                                     │
│ │  🖼  │  🖼  │   🖼   │  🖼  │                                      │
│ │  1   │  2   │   3    │  4   │                                      │
│ └──────┴──────┴────────┴──────┘                                     │
│        |_____|_____|________|_____|                                  │
│         0.1s  0.1s   0.15s   0.1s                                   │
└──────────────────────────────────────────────────────────────────────┘
```

**Features:**
- Time ruler at top
- Frame width proportional to duration
- Playhead (▼) shows current time
- Can resize frames to change duration
- Click on ruler to seek

**Pros:** Duration is visual, professional
**Cons:** Complex to implement, may be overkill

---

### Option D: Grid/Card Layout (For Many Frames)

```
┌──────────────────────────────────────────────────────────────────────┐
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐              │
│ │   🖼   │ │   🖼   │ │   🖼   │ │   🖼   │ │   🖼   │              │
│ │  0.1s  │ │  0.1s  │ │  0.1s  │ │  0.1s  │ │  0.1s  │   [+]        │
│ │   #1   │ │   #2   │ │▶ #3   │ │   #4   │ │   #5   │              │
│ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘              │
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                         │
│ │   🖼   │ │   🖼   │ │   🖼   │ │   🖼   │                         │
│ │  0.1s  │ │  0.15s │ │  0.1s  │ │  0.2s  │                         │
│ │   #6   │ │   #7   │ │   #8   │ │   #9   │                         │
│ └────────┘ └────────┘ └────────┘ └────────┘                         │
└──────────────────────────────────────────────────────────────────────┘
```

**Features:**
- Wraps to multiple rows for many frames
- Larger thumbnails
- Frame number and duration visible
- Current frame marked with ▶

**Pros:** Good for animations with many frames, larger previews
**Cons:** Order less obvious when wrapped

---

### Option E: Track-Based with Resizable Frames (Unity-style)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Time: 0.0    0.1    0.2    0.3    0.4    0.5    0.6    0.7    0.8      │
│       |      |      |      |      |      |      |      |      |        │
│       ▼ (playhead)                                                      │
│ ┌──────────┬──────────┬───────────────┬──────────┬                      │
│ │    🖼    │    🖼    │      🖼       │    🖼    │ [+]                   │
│ │    1     │    2     │      3        │    4     │                      │
│ └──────────┴──────────┴───────────────┴──────────┘                      │
│  |  0.1s   |   0.1s   |    0.15s      |   0.1s   |                      │
│            ↔          ↔               ↔          ↔                      │
│         (drag edges to resize)                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Resize Behavior:**
- Each frame is a box, **width = duration × scale**
- **Drag RIGHT edge** of a frame to change its duration
- Adjacent frames **slide** (push/pull) - they don't change their own duration
- Total animation duration changes as you resize
- Minimum duration enforced (e.g., 0.01s)

**Example - Extending Frame 2:**
```
BEFORE: [  1  ][  2  ][    3    ][  4  ]
              drag →
AFTER:  [  1  ][    2    ][    3    ][  4  ]
                          ↑
                    (frame 3 & 4 pushed right)
```

**Example - Shrinking Frame 3:**
```
BEFORE: [  1  ][  2  ][    3    ][  4  ]
                          ← drag
AFTER:  [  1  ][  2  ][ 3 ][  4  ]
                          ↑
                    (frame 4 pulled left)
```

**Interaction Zones:**
```
┌─────────────────────────────────────┐
│ [drag zone]  [select]  [drag zone] │
│     ↔          click        ↔      │
│  (resize)    (select)   (resize)   │
└─────────────────────────────────────┘
     5px        center        5px
```

**Features:**
- Time ruler with tick marks
- Playhead (▼) animates during playback
- Click on ruler to seek to time
- Click frame to select
- Drag frame center to reorder (shows insertion line)
- Drag frame edge to resize
- Cursor changes: ↔ for resize zones, ✋ for drag zones
- Drop sprite from asset browser → creates frame at drop position, pushes others

**Pros:**
- Very intuitive duration editing
- Visual representation of timing
- Professional (Unity/video editor feel)
- Duration changes are immediate and visual

**Cons:**
- More complex to implement
- Needs horizontal scroll for long animations

**Zoom & Pan (fixes "small durations hard to see"):**
- **Mouse wheel** on timeline → Zoom in/out (centered on cursor)
- **Middle mouse drag** → Pan horizontally
- Zoom range: 0.25x to 4x (or more)
- Show current zoom level indicator
- Double-click ruler → Reset to fit all frames

```
┌────────────────────────────────────────────────────────────┐
│ Timeline                    [- 1.0x +] [Fit]  [Track][Strip]│
└────────────────────────────────────────────────────────────┘
```

**Drag & Drop in Track Mode:**
- **Drag frame center** → Reorder (shows insertion line)
- **Drag frame right edge** → Resize duration
- Both work in Track mode, cursor changes to indicate action

```
Interaction zones per frame:
┌─────────────────────────────────────┐
│ [resize]  [drag to reorder] [resize]│
│    5px         center          5px  │
│    ↔            ✋              ↔    │
└─────────────────────────────────────┘
```

---

### Option E + B Hybrid: Toggle Between Views

Provide **two timeline modes** with a toggle button:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Timeline                                    [📊 Track] [🎞️ Strip]       │
├─────────────────────────────────────────────────────────────────────────┤
```

**Track Mode (E):** For precise timing work
```
│ 0.0    0.1    0.2    0.3    0.4    0.5
│ ▼
│ ┌──────────┬──────────┬───────────────┬──────────┐
│ │    🖼    │    🖼    │      🖼       │    🖼    │
│ └──────────┴──────────┴───────────────┴──────────┘
```

**Strip Mode (B):** For quick overview and reordering
```
│ ║ ┌────┐ ║ ┌────┐ ║ ┌────┐ ║ ┌────┐ ║
│ ║ │ 🖼 │ ║ │ 🖼 │ ║ │ 🖼 │ ║ │ 🖼 │ ║  [+]
│ ║ │0.1s│ ║ │0.1s│ ║ │0.15│ ║ │0.1s│ ║
│ ║ └────┘ ║ └────┘ ║ └────┘ ║ └────┘ ║
```

**When to use each:**
- **Track:** Fine-tuning timing, seeing relative durations
- **Strip:** Quick frame management, reordering, adding/removing

---

### Recommendation: Option E with Strip Mode Toggle

Best of both worlds:
- **Track mode (default):** Unity-like, duration editing by drag
- **Strip mode (toggle):** Quick operations, clear drop zones

---

## Updated Resize Behavior Clarification

| Action | Frame Being Edited | Adjacent Frames |
|--------|-------------------|-----------------|
| Drag right edge → | Duration increases | Pushed right (slide) |
| Drag right edge ← | Duration decreases | Pulled left (slide) |
| Drag left edge → | Duration decreases | N/A (first frame) or previous shrinks? |
| Drag left edge ← | Duration increases | N/A (first frame) or previous shrinks? |

**Simplification:** Only allow dragging the **RIGHT edge** of each frame. This avoids complexity of left-edge behavior and is intuitive (you're "extending" the frame).

---

## Asset Browser Drop Behavior

When dragging a sprite from Asset Browser to timeline:

**Drop at end:**
```
BEFORE: [  1  ][  2  ][  3  ]     ← drop here
AFTER:  [  1  ][  2  ][  3  ][ NEW ]
                              (0.1s default)
```

**Drop between frames:**
```
BEFORE: [  1  ][  2  ][  3  ]
              ↑ drop here
AFTER:  [  1  ][ NEW ][  2  ][  3  ]
               (0.1s)  ← pushed right
```

In Track Mode, the insertion is visual - you see exactly where the new frame will go and how it affects timing.

---

## Play State Indication

**Pulsing + Background Color:**

```
STOPPED:                         PLAYING:
┌─────────────────┐              ┌─────────────────┐
│  ▶ Play         │              │  ⏹ Stop         │
│  (gray bg)      │              │  (green bg,     │
│                 │              │   pulsing glow) │
└─────────────────┘              └─────────────────┘
```

**Implementation:**
- Stop button: Red background when playing
- Play button: Green background when stopped (indicating "ready to play")
- When playing: Pulsing green border/glow on Stop button (draw attention)
- Timeline background: Subtle green tint during playback
- Playhead: Animated movement during playback

---

## Improvement List

### Priority 1: Critical UX Fixes

| # | Issue | Solution |
|---|-------|----------|
| 1 | Play/Stop not visible | Color buttons: Play=Green when stopped, Stop=Red when playing. Add pulsing border when playing |
| 2 | Unsaved indicator | Yellow text + asterisk for unsaved animations (match scene name style) |
| 3 | Can't insert between frames | Add visible drop zones between frames during drag |
| 4 | No visual drag feedback | Show insertion line/highlight at drop position |
| 5 | Timeline hard to understand | Add frame numbers, clearer selection indicator, playhead marker |

### Priority 2: Layout Improvements

| # | Issue | Solution |
|---|-------|----------|
| 6 | Properties on wrong side | Move to left column (under animation list) |
| 7 | Double menu bar | Remove File/Edit menu, keep toolbar only |
| 8 | Duration label position | Use PrimitiveEditors.floatField() with label on left |

### Priority 3: Missing Features

| # | Feature | Description |
|---|---------|-------------|
| 9 | Right-click change sprite | Add "Change Sprite..." option to frame context menu |
| 10 | Drag from asset browser | Accept sprite drops on timeline to add frames |

### Priority 4: Polish

| # | Enhancement | Description |
|---|-------------|-------------|
| 11 | Playhead visualization | Moving indicator showing current playback position |
| 12 | Frame number labels | Show "1", "2", "3" below each frame |
| 13 | Keyboard hint tooltips | Show shortcuts in button tooltips |

---

## Implementation Order

### Step 1: Layout & Basic Fixes
1. **Layout restructure** (Properties left, Preview right, remove menu bar)
2. **Play/Stop button colors** (green/red + pulsing when playing)
3. **Unsaved indicator** (yellow + asterisk)

### Step 2: Timeline Track Mode (E)
4. **Track mode timeline** (time ruler, proportional width, playhead)
5. **Frame edge dragging** (right edge only, to resize duration)
6. **Frames push/slide** when resizing
7. **Drag to reorder** (drag frame center, shows insertion line)
8. **Zoom & Pan** (mouse wheel zoom, middle-click pan, zoom indicator)

### Step 3: Timeline Strip Mode (B)
7. **Strip mode toggle** (add toggle button)
8. **Drop zone visualization** (filmstrip separators)
9. **Insert between frames** (in both modes)

### Step 4: Additional Features
10. **Duration field** (use PrimitiveEditors in frame inspector)
11. **Right-click change sprite**
12. **Asset browser drag-drop** (default 0.1s, pushes adjacent frames)

---

## Decisions Made

| Question | Decision |
|----------|----------|
| Timeline layout | **Option E (Track) + B (Strip) with toggle** |
| Play state indicator | **Pulsing + background color** |
| Asset browser drops | **Default 0.1s duration**, push adjacent frames |
| Resize behavior | **Right edge only**, adjacent frames slide |
| Left edge drag | **Not implemented** (keep it simple) |
| Small durations visibility | **Zoom (mouse wheel) + Pan (middle click)** |
| Drag in Track mode | **Yes** - drag center to reorder, drag edge to resize |
| Animation list location | **Dropdown in toolbar** (right side) - saves vertical space |
| Layout columns | **Three columns:** Properties \| Current Frame \| Preview |
| Unsaved indicator | **Yellow dropdown text with asterisk** |
| Unsaved changes dialog | **Triggers on animation switch AND new animation creation** |
| Click frame during playback | **Selects frame but doesn't stop playback** - allows inspection |
| New frame default sprite | **Empty (no sprite selected)** - user must explicitly choose |
| Empty frames in animation | **Allowed** - can save animations with empty sprite paths |
| Drag feedback | **Cyan insertion line with triangle** at drop position |
| Asset browser drag format | **Uses AssetDragPayload.DRAG_TYPE ("ASSET_DRAG")** |

---

## Implementation Summary

### Phase 4.5 Status: ✅ Complete

All features from the Phase 4.5 enhancement list have been implemented in `AnimationEditorPanel.java`.

### Key Technical Details

**Layout Structure:**
- Three-column layout: Properties (200px) | Current Frame (200px) | Preview (remaining)
- Animation dropdown in toolbar with yellow highlight for unsaved changes
- Timeline at bottom with Track/Strip mode toggle

**Drag and Drop:**
- Frame reordering uses `FRAME_DRAG_TYPE` ("ANIM_FRAME")
- Asset browser sprites use `AssetDragPayload.DRAG_TYPE` ("ASSET_DRAG")
- Payload format: `path|typeClassName` (deserialized via `AssetDragPayload.deserialize()`)
- `AcceptPeekOnly` flag used for hover detection without accepting drop
- `dropTargetIndex` state tracks current insertion point for visual feedback

**Visual Feedback:**
- Drop insertion indicator: Cyan line (3px) with triangle at top
- Play button: Green when stopped, pulsing when playing
- Stop button: Red when playing
- Unsaved indicator: Yellow text with asterisk in dropdown

**Empty Frame Handling:**
- New frames created with empty sprite path (`""`)
- Empty frames display "No sprite" text in preview
- Empty frames are allowed when saving (valid use case)

**Playback Behavior:**
- Clicking frame during playback selects it for inspection but doesn't stop playback
- Space bar toggles play/pause
- Playhead animates during playback (both track and strip modes)

---

## Code Architecture (Post-Refactor)

The `AnimationEditorPanel.java` was refactored from ~2200 lines to ~1200 lines by extracting timeline and preview rendering into separate classes.

### File Structure

```
src/main/java/com/pocket/rpg/editor/panels/
├── AnimationEditorPanel.java          # Main panel (~1200 lines)
└── animation/
    ├── AnimationTimelineContext.java  # Shared context/state (~180 lines)
    ├── AnimationPreviewRenderer.java  # Preview with playback (~280 lines)
    ├── TrackTimelineRenderer.java     # Track-based timeline (~400 lines)
    └── StripTimelineRenderer.java     # Strip-based timeline (~370 lines)
```

### Class Responsibilities

**AnimationEditorPanel.java**
- Animation list management and dropdown
- Toolbar (new, delete, save, refresh, undo/redo, play/stop)
- Properties panel
- Current frame panel (duration, sprite picker)
- Keyboard shortcuts
- Dialogs (new, delete, unsaved changes)
- Coordinates between renderers

**AnimationTimelineContext.java**
- Shared state container (animation, selection, zoom, pan)
- Constants (frame size, spacing, colors)
- Callbacks for state sync (captureUndoState, markModified, etc.)
- Utility methods (intToBytes, bytesToInt)
- Helper methods (getFrameSpriteSafe, getPixelsPerSecond)

**AnimationPreviewRenderer.java**
- Preview area with checker background
- Zoom mode selection (0.5x, Fit)
- Playback timing (play, stop, speed control)
- Sprite rendering with stable sizing

**TrackTimelineRenderer.java**
- Time ruler rendering
- Proportional frame width based on duration
- Frame resize by dragging right edge
- Playhead indicator
- Insertion zones for drag-drop (cyan/green lines)
- Mouse wheel zoom, middle-click pan

**StripTimelineRenderer.java**
- Fixed-size thumbnails with frame labels
- Horizontal scroll for many frames
- Insertion zones between frames
- No resize functionality (duration edited in Current Frame panel)

### State Flow

```
AnimationEditorPanel
    ├── creates AnimationTimelineContext each frame
    │   ├── passes current state (animation, selection, zoom)
    │   └── passes callbacks (captureUndoState, markModified, etc.)
    │
    ├── TrackTimelineRenderer/StripTimelineRenderer
    │   ├── reads context state
    │   ├── handles user interaction
    │   ├── calls context callbacks for modifications
    │   └── updates context state (selection, zoom)
    │
    └── AnimationPreviewRenderer
        ├── owns its own state (isPlaying, previewFrame, timer)
        ├── updates on setAnimation()
        └── syncs previewFrame back to panel
```

### Design Decisions

- **Context per frame**: Context is recreated each frame to ensure fresh state sync.
- **Callbacks over direct coupling**: Renderers use callbacks instead of direct panel access.
- **Preview owns playback**: Preview renderer manages playback state independently.
- **Deferred operations**: Frame moves/deletes are stored in context and processed after rendering pass.
