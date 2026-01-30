# Animator Node Graph Editor

## CURRENT STATUS (2026-01-27)

**Implementation is 95% complete. One bug remains:**

### Bug: ImNodes Assertion Error on Tab Open
The `ImNodes.getHoveredNode()` call causes an assertion error:
```
Dear ImGui Assertion Failed: GImNodes->CurrentScope == ImNodesScope_None
```

**Root Cause:** ImNodes hover functions must be called AFTER `endNodeEditor()`, not before.

**Fix Required in `AnimatorGraphEditor.java` (around line 157-162):**

Current (WRONG):
```java
// Capture hover state BEFORE ending the editor (required for context menus)
hoveredNodeId = ImNodes.getHoveredNode();
hoveredLinkId = ImNodes.getHoveredLink();
editorHovered = ImNodes.isEditorHovered();

ImNodes.endNodeEditor();
```

Should be (CORRECT):
```java
ImNodes.endNodeEditor();

// Capture hover state AFTER ending the editor
hoveredNodeId = ImNodes.getHoveredNode();
hoveredLinkId = ImNodes.getHoveredLink();
editorHovered = ImNodes.isEditorHovered();
```

### Files Created/Modified:
- `editor/panels/animator/AnimatorGraphEditor.java` - NEW (needs fix above)
- `editor/panels/animator/AnimatorIdManager.java` - NEW
- `animation/AnimatorLayoutData.java` - NEW
- `resources/loaders/AnimatorLayoutLoader.java` - NEW
- `editor/panels/AnimatorEditorPanel.java` - Modified for graph view

---

## Overview

This document describes a visual node-based editor for AnimatorController assets. States are represented as draggable nodes, and transitions as curved arrows between them.

---

## Part 1: Visual Design

### Basic Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Toolbar: [+ State] [+ Transition] [Auto Layout] [Zoom: 100%] [Fit] [Grid ☑] │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│     ┌─────────────┐                              ┌─────────────┐            │
│     │    idle     │─────────────────────────────▶│    walk     │            │
│     │  ◉ default  │                              │ directional │            │
│     │   ▶ preview │◀─────────────────────────────│   ▶ preview │            │
│     └─────────────┘                              └─────────────┘            │
│            │                                            │                   │
│            │                                            │                   │
│            │         ┌─────────────┐                    │                   │
│            │         │   attack    │                    │                   │
│            └────────▶│   simple    │◀───────────────────┘                   │
│                      │   ▶ preview │                                        │
│                      └─────────────┘                                        │
│                             │                                               │
│                             │ WAIT_FOR_COMPLETION                           │
│                             ▼                                               │
│                      ┌─────────────┐                                        │
│                      │    hurt     │                                        │
│                      │   simple    │                                        │
│                      └─────────────┘                                        │
│                                                                             │
│─────────────────────────────────────────────────────────────────────────────│
│ Parameters: [isMoving: bool] [direction: Direction] [isAttacking: bool]     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Node Design

**State Node - Simple:**
```
┌───────────────────────────┐
│ ● idle                    │  ← Header (colored by state type)
├───────────────────────────┤
│  Type: Simple             │
│  Animation:               │
│  ┌─────┐ idle.anim        │  ← Thumbnail + path
│  │ ▶▶  │                  │
│  └─────┘                  │
├───────────────────────────┤
│  ◉ Default State          │  ← Badge if default
└───────────────────────────┘
    ●                    ●     ← Connection points (left=in, right=out)
```

**State Node - Directional:**
```
┌───────────────────────────┐
│ ● walk                    │
├───────────────────────────┤
│  Type: Directional        │
│  ┌───┬───┬───┬───┐        │
│  │ ↑ │ ↓ │ ← │ → │        │  ← 4 mini thumbnails
│  └───┴───┴───┴───┘        │
│  4/4 animations set       │
└───────────────────────────┘
    ●                    ●
```

**State Node - Compact Mode:**
```
┌─────────────┐
│ ● idle      │
│   simple    │
└─────────────┘
```

### Transition Arrow Design

**Standard Transition:**
```
┌───────┐                          ┌───────┐
│ idle  │────────────────────────▶│ walk  │
└───────┘         INSTANT          └───────┘
              isMoving == true
```

**Bidirectional Transitions:**
```
┌───────┐          INSTANT         ┌───────┐
│       │─────────────────────────▶│       │
│ idle  │      isMoving == true    │ walk  │
│       │◀─────────────────────────│       │
└───────┘      isMoving == false   └───────┘
              INSTANT
```

**Curved Transition (avoiding overlap):**
```
                    ╭──────────────────╮
                    │                  │
┌───────┐           │                  ▼
│ idle  │───────────┼─────────────▶┌───────┐
└───────┘           │              │ walk  │
                    │              └───────┘
                    │                  │
                    ╰──────────────────╯
```

**Self-Loop Transition:**
```
        ╭─────╮
        │     │
        ▼     │
    ┌───────┐ │
    │ idle  │─╯
    └───────┘
```

### Transition Label Design

```
         ┌─────────────────────────┐
─────────│ WAIT_FOR_COMPLETION     │─────────▶
         │ isAttacking == true     │
         └─────────────────────────┘
              (click to edit)
```

**Compact Label:**
```
─────────[ INSTANT | isMoving ]─────────▶
```

### Any State Node

The wildcard `*` state is represented as a special node:

```
    ╭─────────────╮
   ╱               ╲
  │    ★ ANY       │
  │                │
   ╲               ╱
    ╰─────────────╯
         │
         │ (triggers from any state)
         ▼
    ┌─────────────┐
    │   attack    │
    └─────────────┘
```

---

## Part 2: Layout Examples

### Example 1: Simple Player Controller

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                         INSTANT                                             │
│                    isMoving == true                                         │
│     ┌─────────┐ ─────────────────────▶ ┌─────────┐                         │
│     │  idle   │                        │  walk   │                         │
│     │ ◉ default│ ◀───────────────────── │ directional                      │
│     └─────────┘      INSTANT           └─────────┘                         │
│                  isMoving == false                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Example 2: Combat Character

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│     ┌─────────┐         INSTANT         ┌─────────┐                        │
│     │  idle   │ ──────────────────────▶ │  walk   │                        │
│     │ ◉ default│ ◀────────────────────── │         │                        │
│     └─────────┘                         └─────────┘                        │
│          │                                   │                              │
│          │ INSTANT                           │ INSTANT                      │
│          │ trigger("attack")                 │ trigger("attack")            │
│          │                                   │                              │
│          │         ┌─────────┐               │                              │
│          └────────▶│ attack  │◀──────────────┘                              │
│                    │ simple  │                                              │
│                    └─────────┘                                              │
│                         │                                                   │
│                         │ WAIT_FOR_COMPLETION                               │
│                         │ (auto return)                                     │
│                         ▼                                                   │
│                    ┌ ─ ─ ─ ─ ┐                                              │
│                      PREVIOUS   (returns to idle or walk)                   │
│                    └ ─ ─ ─ ─ ┘                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Example 3: NPC with Multiple States

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                              ┌─────────┐                                    │
│                         ┌───▶│  sleep  │                                    │
│                         │    └─────────┘                                    │
│                         │         │                                         │
│                         │         │ INSTANT                                 │
│                         │         │ isAwake == true                         │
│                         │         ▼                                         │
│     ┌─────────┐    INSTANT   ┌─────────┐    INSTANT    ┌─────────┐         │
│     │  walk   │◀────────────│  idle   │──────────────▶│  talk   │         │
│     │         │  !isTalking  │ ◉ default│  isTalking    │         │         │
│     └─────────┘              └─────────┘               └─────────┘         │
│          │                        │                         │               │
│          │                        │                         │               │
│          │    INSTANT             │ INSTANT                 │               │
│          │    isAlert             │ isAlert                 │               │
│          │                        │                         │               │
│          │                   ┌─────────┐                    │               │
│          └──────────────────▶│  alert  │◀───────────────────┘               │
│                              │         │                                    │
│                              └─────────┘                                    │
│                                   │                                         │
│                                   │ INSTANT                                 │
│                                   │ trigger("attack")                       │
│                                   ▼                                         │
│                              ┌─────────┐                                    │
│                              │ attack  │                                    │
│                              └─────────┘                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Example 4: Directional Character with Jump

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                    ┌───────────────────────────────────┐                    │
│                    │           GROUND LAYER            │                    │
│                    │                                   │                    │
│     ┌─────────┐    │    INSTANT     ┌─────────┐       │                    │
│     │  idle   │────┼───────────────▶│  walk   │       │                    │
│     │ ↑↓←→    │    │   isMoving     │ ↑↓←→    │       │                    │
│     │ ◉ default│◀───┼────────────────│         │       │                    │
│     └─────────┘    │   !isMoving    └─────────┘       │                    │
│          │         │                      │           │                    │
│          └─────────┼──────────────────────┘           │                    │
│                    │         │                        │                    │
│                    └─────────┼────────────────────────┘                    │
│                              │                                              │
│                              │ INSTANT                                      │
│                              │ isJumping                                    │
│                              ▼                                              │
│                    ┌───────────────────────────────────┐                    │
│                    │            AIR LAYER              │                    │
│                    │                                   │                    │
│                    │         ┌─────────┐              │                    │
│                    │         │  jump   │              │                    │
│                    │         │ simple  │              │                    │
│                    │         └─────────┘              │                    │
│                    │              │                   │                    │
│                    │              │ WAIT_COMPLETION   │                    │
│                    │              ▼                   │                    │
│                    │         ┌─────────┐              │                    │
│                    │         │  fall   │              │                    │
│                    │         │ simple  │──────────────┼──▶ (back to idle)  │
│                    │         └─────────┘   !isJumping │                    │
│                    │                                  │                    │
│                    └──────────────────────────────────┘                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Example 5: Boss with Phases

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  ┌────────────────────────── PHASE 1 (health > 50%) ──────────────────────┐ │
│  │                                                                        │ │
│  │    ┌─────────┐      INSTANT       ┌─────────┐                         │ │
│  │    │  idle   │ ─────────────────▶ │  slam   │                         │ │
│  │    │ ◉ phase1│ ◀───────────────── │         │                         │ │
│  │    └─────────┘   WAIT_COMPLETION  └─────────┘                         │ │
│  │         │                              │                               │ │
│  │         │                              │                               │ │
│  │         └──────────────┬───────────────┘                               │ │
│  │                        │                                               │ │
│  │                        │ INSTANT                                       │ │
│  │                        │ trigger("summon")                             │ │
│  │                        ▼                                               │ │
│  │                   ┌─────────┐                                          │ │
│  │                   │ summon  │                                          │ │
│  │                   └─────────┘                                          │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                              │                                              │
│                              │ INSTANT                                      │
│                              │ health <= 50%                                │
│                              ▼                                              │
│  ┌────────────────────────── PHASE 2 (health <= 50%) ─────────────────────┐ │
│  │                                                                        │ │
│  │    ┌─────────┐                        ┌─────────┐                     │ │
│  │    │  rage   │ ◀────────────────────▶ │  charge │                     │ │
│  │    │ ◉ phase2│         INSTANT        │         │                     │ │
│  │    └─────────┘                        └─────────┘                     │ │
│  │         │                                   │                          │ │
│  │         │         ┌─────────┐               │                          │ │
│  │         └────────▶│ roar    │◀──────────────┘                          │ │
│  │                   │         │                                          │ │
│  │                   └─────────┘                                          │ │
│  │                                                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Part 3: Interaction Patterns

### Node Selection

```
Before click:                        After click:
┌───────────────┐                    ╔═══════════════╗
│     idle      │         ────▶      ║     idle      ║  ← Blue highlight
│    simple     │                    ║    simple     ║
└───────────────┘                    ╚═══════════════╝
```

### Multi-Selection (Shift+Click or Box Select)

```
Box select:
    ┌─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┐
    ╔═══════════╗              │
    ║   idle    ║
    ╚═══════════╝   ╔═══════════╗
    │               ║   walk    ║
                    ╚═══════════╝
    └─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┘

Result: Both nodes selected
```

### Creating Transitions (Drag from Output to Input)

```
Step 1: Hover output port           Step 2: Drag to target
┌───────────┐                       ┌───────────┐
│   idle    │●  ← Port highlights   │   idle    │●─ ─ ─ ─ ─ ─ ┐
└───────────┘                       └───────────┘              │
                                                               │
                                    ┌───────────┐              │
                                    │   walk    │● ◀─ ─ ─ ─ ─ ┘
                                    └───────────┘
                                      ↑ Target highlights

Step 3: Release creates transition
┌───────────┐         ┌───────────┐
│   idle    │────────▶│   walk    │
└───────────┘ INSTANT └───────────┘
           (opens transition editor)
```

### Moving Nodes

```
Drag node:
┌───────────┐                           ┌───────────┐
│   idle    │  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ▶ │   idle    │
└───────────┘      (ghost outline)      └───────────┘
     │                                        │
     │ (transitions follow)                   │
     ▼                                        ▼
┌───────────┐                           ┌───────────┐
│   walk    │                           │   walk    │
└───────────┘                           └───────────┘
```

### Context Menu (Right-Click)

```
Right-click on empty space:          Right-click on node:
┌─────────────────────┐              ┌─────────────────────┐
│ + Add State         │              │ Edit State...       │
│ + Add Any State     │              │ Set as Default      │
│ ─────────────────── │              │ ─────────────────── │
│ Paste               │              │ Duplicate           │
│ Select All          │              │ Delete              │
│ ─────────────────── │              │ ─────────────────── │
│ Auto Layout         │              │ Add Transition From │
│ Reset View          │              │ Add Transition To   │
└─────────────────────┘              └─────────────────────┘

Right-click on transition:
┌─────────────────────┐
│ Edit Transition...  │
│ ─────────────────── │
│ Delete              │
│ Reverse Direction   │
└─────────────────────┘
```

### Keyboard Shortcuts in Graph View

| Key | Action |
|-----|--------|
| `Delete` | Delete selected nodes/transitions |
| `Ctrl+D` | Duplicate selected nodes |
| `Ctrl+A` | Select all nodes |
| `F` | Fit graph in view |
| `Home` | Center on default state |
| `Space` | Pan mode (hold and drag) |
| `Scroll` | Zoom in/out |
| `1` | Reset zoom to 100% |

---

## Part 4: Panel Layout Variations

### Layout A: Graph + Inspector Side Panel

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ [+ State] [Auto Layout] [Zoom] [Fit]                    [Animator: player ▼]│
├─────────────────────────────────────────────────────┬───────────────────────┤
│                                                     │ INSPECTOR             │
│                                                     ├───────────────────────┤
│                                                     │ State: idle           │
│     ┌─────────┐              ┌─────────┐           │                       │
│     │  idle   │─────────────▶│  walk   │           │ Name: [idle      ]    │
│     └─────────┘              └─────────┘           │ Type: [Simple ▼]      │
│                                                     │                       │
│              GRAPH CANVAS                           │ Animation:            │
│                                                     │ [idle.anim   ][Browse]│
│                                                     │                       │
│                                                     │ ☑ Default State       │
│                                                     ├───────────────────────┤
│                                                     │ TRANSITIONS OUT       │
│                                                     │ → walk (INSTANT)      │
│                                                     │ → attack (INSTANT)    │
├─────────────────────────────────────────────────────┴───────────────────────┤
│ Parameters: [+ Add]  isMoving: false   direction: DOWN                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout B: Graph + Bottom Inspector

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ [+ State] [Auto Layout] [Zoom] [Fit]                    [Animator: player ▼]│
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│     ┌─────────┐              ┌─────────┐              ┌─────────┐          │
│     │  idle   │─────────────▶│  walk   │─────────────▶│ attack  │          │
│     └─────────┘              └─────────┘              └─────────┘          │
│                                                                             │
│                            GRAPH CANVAS                                     │
│                                                                             │
├────────────────────┬────────────────────────────────────────────────────────┤
│ SELECTED: idle     │ Type: Simple    Animation: [idle.anim    ] [Browse]   │
│ ☑ Default          │ Transitions: → walk (isMoving)  → attack (trigger)    │
├────────────────────┴────────────────────────────────────────────────────────┤
│ Parameters: [+ Add]  isMoving: false   direction: DOWN   isAttacking: false │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout C: Full Graph with Floating Inspector

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ [+ State] [Auto Layout] [Zoom] [Fit]                    [Animator: player ▼]│
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│     ┌─────────┐              ┌─────────┐                                   │
│     │  idle   │─────────────▶│  walk   │                                   │
│     └─────────┘              └─────────┘                                   │
│          ║                                    ┌─────────────────────┐       │
│          ║ (selected)                         │ State: idle         │       │
│          ║                                    ├─────────────────────┤       │
│          ▼                                    │ Name: [idle    ]    │       │
│     ┌─────────┐                               │ Type: [Simple ▼]    │       │
│     │ attack  │                               │ Anim: [idle.anim]   │       │
│     └─────────┘                               │ ☑ Default           │       │
│                                               └─────────────────────┘       │
│                                                (floating, draggable)        │
├─────────────────────────────────────────────────────────────────────────────┤
│ Parameters: isMoving: false   direction: DOWN                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layout D: Compact Mode (Small Screens)

```
┌─────────────────────────────────────┐
│ [+] [Layout] [Zoom]    [player ▼]   │
├─────────────────────────────────────┤
│   ┌─────┐       ┌─────┐            │
│   │idle │──────▶│walk │            │
│   └─────┘       └─────┘            │
│      │                              │
│      ▼                              │
│   ┌─────┐                          │
│   │atk  │                          │
│   └─────┘                          │
├─────────────────────────────────────┤
│ idle | Simple | idle.anim  [Edit]  │
├─────────────────────────────────────┤
│ Params: isMoving direction         │
└─────────────────────────────────────┘
```

---

## Part 5: Auto-Layout Algorithms

### Hierarchical Layout (Default)

Organizes nodes in layers based on transition flow:

```
Layer 0 (entry):     Layer 1:           Layer 2:           Layer 3:
┌─────────┐         ┌─────────┐        ┌─────────┐        ┌─────────┐
│  idle   │────────▶│  walk   │───────▶│  run    │───────▶│  jump   │
└─────────┘         └─────────┘        └─────────┘        └─────────┘
     │                   │
     │                   │
     ▼                   ▼
┌─────────┐         ┌─────────┐
│ attack  │         │  slide  │
└─────────┘         └─────────┘
```

**Algorithm:**
1. Find default state → Layer 0
2. States reachable in 1 transition → Layer 1
3. Continue until all states placed
4. Handle cycles by placing back-edges

### Force-Directed Layout

Treats nodes as repelling particles and transitions as springs:

```
Before:                              After:
┌───┐ ┌───┐ ┌───┐                   ┌───┐
│ A │ │ B │ │ C │                   │ A │─────────┐
└───┘ └───┘ └───┘                   └───┘         │
┌───┐ ┌───┐                              ╲        │
│ D │ │ E │                               ╲       ▼
└───┘ └───┘                          ┌───┐ ╲  ┌───┐
                                     │ D │──▶│ B │
                                     └───┘   └───┘
                                               │
                                               ▼
                                             ┌───┐
                                             │ C │
                                             └───┘
```

### Grid Snap Layout

Aligns nodes to a grid for clean appearance:

```
Grid: 100px spacing

     0      100     200     300
  0  ┌───────┐       ┌───────┐
     │ idle  │──────▶│ walk  │
     └───────┘       └───────┘
100       │               │
          ▼               ▼
200  ┌───────┐       ┌───────┐
     │attack │       │ run   │
     └───────┘       └───────┘
```

### Circular Layout (for interconnected states)

```
              ┌───────┐
              │ state1│
              └───────┘
             ╱         ╲
    ┌───────┐           ┌───────┐
    │state5 │           │state2 │
    └───────┘           └───────┘
             ╲         ╱
    ┌───────┐           ┌───────┐
    │state4 │───────────│state3 │
    └───────┘           └───────┘
```

---

## Part 6: Technical Implementation

### Data Structures

```java
/**
 * Visual position data for nodes (stored separately from AnimatorController).
 * Saved in a companion file: <animator>.animator.layout.json
 */
public class AnimatorLayoutData {
    private Map<String, NodeLayout> nodeLayouts = new HashMap<>();
    private float viewPanX = 0;
    private float viewPanY = 0;
    private float viewZoom = 1.0f;

    public record NodeLayout(
        float x,
        float y,
        boolean collapsed,
        String comment  // Optional note attached to node
    ) {}
}
```

### Node Rendering

```java
private void renderStateNode(AnimatorState state, NodeLayout layout, boolean isSelected) {
    ImVec2 nodePos = toScreenPos(layout.x(), layout.y());
    ImVec2 nodeSize = calculateNodeSize(state, layout.collapsed());

    // Background
    int bgColor = isSelected ? COLOR_NODE_SELECTED : COLOR_NODE_BG;
    if (state.getName().equals(controller.getDefaultState())) {
        bgColor = COLOR_NODE_DEFAULT;
    }

    ImDrawList drawList = ImGui.getWindowDrawList();

    // Node rectangle with rounded corners
    drawList.addRectFilled(
        nodePos.x, nodePos.y,
        nodePos.x + nodeSize.x, nodePos.y + nodeSize.y,
        bgColor, 8.0f  // corner radius
    );

    // Border
    int borderColor = isSelected ? COLOR_BORDER_SELECTED : COLOR_BORDER;
    drawList.addRect(
        nodePos.x, nodePos.y,
        nodePos.x + nodeSize.x, nodePos.y + nodeSize.y,
        borderColor, 8.0f, 0, 2.0f  // thickness
    );

    // Header
    drawList.addRectFilled(
        nodePos.x, nodePos.y,
        nodePos.x + nodeSize.x, nodePos.y + HEADER_HEIGHT,
        getStateTypeColor(state.getType()), 8.0f
    );

    // Title
    drawList.addText(
        nodePos.x + PADDING, nodePos.y + PADDING,
        COLOR_TEXT, state.getName()
    );

    // Connection ports
    renderInputPort(nodePos, nodeSize);
    renderOutputPort(nodePos, nodeSize);

    // Content (animation preview, type label)
    if (!layout.collapsed()) {
        renderNodeContent(state, nodePos, nodeSize);
    }
}
```

### Transition Rendering

```java
private void renderTransition(AnimatorTransition trans, boolean isSelected) {
    NodeLayout fromLayout = getNodeLayout(trans.getFrom());
    NodeLayout toLayout = getNodeLayout(trans.getTo());

    if (fromLayout == null || toLayout == null) return;

    ImVec2 startPos = getOutputPortPos(fromLayout);
    ImVec2 endPos = getInputPortPos(toLayout);

    // Calculate control points for bezier curve
    float dist = distance(startPos, endPos);
    float curvature = Math.min(dist * 0.5f, 100f);

    ImVec2 cp1 = new ImVec2(startPos.x + curvature, startPos.y);
    ImVec2 cp2 = new ImVec2(endPos.x - curvature, endPos.y);

    ImDrawList drawList = ImGui.getWindowDrawList();

    // Draw bezier curve
    int lineColor = isSelected ? COLOR_TRANSITION_SELECTED : COLOR_TRANSITION;
    float thickness = isSelected ? 3.0f : 2.0f;

    drawList.addBezierCubic(
        startPos.x, startPos.y,
        cp1.x, cp1.y,
        cp2.x, cp2.y,
        endPos.x, endPos.y,
        lineColor, thickness
    );

    // Arrow head
    ImVec2 arrowDir = normalize(subtract(endPos, cp2));
    renderArrowHead(endPos, arrowDir, lineColor);

    // Label at midpoint
    ImVec2 midpoint = bezierPoint(startPos, cp1, cp2, endPos, 0.5f);
    renderTransitionLabel(trans, midpoint, isSelected);
}
```

### Hit Testing

```java
private HitResult hitTest(float mouseX, float mouseY) {
    ImVec2 worldPos = toWorldPos(mouseX, mouseY);

    // Check nodes (reverse order for top-most first)
    List<AnimatorState> states = controller.getStates();
    for (int i = states.size() - 1; i >= 0; i--) {
        AnimatorState state = states.get(i);
        NodeLayout layout = getNodeLayout(state.getName());

        if (isPointInNode(worldPos, layout)) {
            // Check if on output port
            if (isPointInOutputPort(worldPos, layout)) {
                return new HitResult(HitType.OUTPUT_PORT, state.getName(), -1);
            }
            // Check if on input port
            if (isPointInInputPort(worldPos, layout)) {
                return new HitResult(HitType.INPUT_PORT, state.getName(), -1);
            }
            return new HitResult(HitType.NODE, state.getName(), -1);
        }
    }

    // Check transitions
    List<AnimatorTransition> transitions = controller.getTransitions();
    for (int i = 0; i < transitions.size(); i++) {
        AnimatorTransition trans = transitions.get(i);
        if (isPointNearTransition(worldPos, trans, 5.0f)) {
            return new HitResult(HitType.TRANSITION, null, i);
        }
    }

    return new HitResult(HitType.NONE, null, -1);
}

private record HitResult(HitType type, String nodeName, int transitionIndex) {}

private enum HitType {
    NONE, NODE, TRANSITION, INPUT_PORT, OUTPUT_PORT
}
```

### Pan and Zoom

```java
private void handlePanZoom() {
    ImGuiIO io = ImGui.getIO();

    // Zoom with scroll wheel
    if (ImGui.isWindowHovered() && io.getMouseWheel() != 0) {
        float zoomDelta = io.getMouseWheel() * 0.1f;
        float newZoom = Math.max(0.25f, Math.min(4.0f, viewZoom + zoomDelta));

        // Zoom toward mouse position
        ImVec2 mousePos = ImGui.getMousePos();
        ImVec2 windowPos = ImGui.getWindowPos();
        float relX = mousePos.x - windowPos.x;
        float relY = mousePos.y - windowPos.y;

        // Adjust pan to keep mouse position stable
        viewPanX += relX * (1.0f / viewZoom - 1.0f / newZoom);
        viewPanY += relY * (1.0f / viewZoom - 1.0f / newZoom);

        viewZoom = newZoom;
    }

    // Pan with middle mouse or Space+drag
    boolean isPanning = ImGui.isMouseDragging(ImGuiMouseButton.Middle) ||
                       (ImGui.isKeyDown(ImGuiKey.Space) && ImGui.isMouseDragging(ImGuiMouseButton.Left));

    if (isPanning) {
        ImVec2 delta = ImGui.getMouseDragDelta(isPanning ? ImGuiMouseButton.Middle : ImGuiMouseButton.Left);
        viewPanX += delta.x / viewZoom;
        viewPanY += delta.y / viewZoom;
        ImGui.resetMouseDragDelta(isPanning ? ImGuiMouseButton.Middle : ImGuiMouseButton.Left);
    }
}
```

---

## Part 7: Play Mode Integration

### Current State Highlight

During play mode, the currently active state is highlighted:

```
Edit Mode:                           Play Mode:
┌───────────┐                        ┌───────────┐
│   idle    │                        │   idle    │
└───────────┘                        └───────────┘
     │                                    │
     ▼                                    ▼
┌───────────┐                        ╔═══════════╗
│   walk    │                        ║   walk    ║ ← Green pulsing border
└───────────┘                        ║ ● ACTIVE  ║
                                     ╚═══════════╝
```

### Transition Animation

When a transition fires, briefly animate the arrow:

```
Transition firing:

┌───────┐                    ┌───────┐
│ idle  │════════════════▶▶▶│ walk  │
└───────┘    (animated)      └───────┘
         ●────────────▶
            particle
```

### Parameter Visualization

Show current parameter values in a bar:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ PLAY MODE                                                      [Stop] [Pause]│
├─────────────────────────────────────────────────────────────────────────────┤
│ Parameters (live):                                                          │
│   isMoving: ● true    direction: → RIGHT    isAttacking: ○ false           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Part 8: Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `editor/panels/animator/AnimatorGraphRenderer.java` | **NEW** | Core graph rendering logic |
| `editor/panels/animator/AnimatorNodeRenderer.java` | **NEW** | Individual node rendering |
| `editor/panels/animator/AnimatorTransitionRenderer.java` | **NEW** | Transition/arrow rendering |
| `editor/panels/animator/AnimatorGraphInteraction.java` | **NEW** | Mouse/keyboard handling |
| `editor/panels/animator/AnimatorLayoutEngine.java` | **NEW** | Auto-layout algorithms |
| `animation/AnimatorLayoutData.java` | **NEW** | Layout persistence model |
| `resources/loaders/AnimatorLayoutLoader.java` | **NEW** | Load/save layout files |
| `editor/panels/AnimatorEditorPanel.java` | Modify | Integrate graph view |

---

## Part 9: Implementation Phases

### Phase 1: Basic Graph Rendering
- [ ] Canvas with pan and zoom
- [ ] Render nodes as simple rectangles
- [ ] Render transitions as straight lines
- [ ] Basic node selection

### Phase 2: Node Interaction
- [ ] Node dragging
- [ ] Multi-selection (box select, shift+click)
- [ ] Delete nodes with keyboard
- [ ] Context menus

### Phase 3: Transition Creation
- [ ] Drag from output port to input port
- [ ] Transition creation dialog on release
- [ ] Bezier curve rendering
- [ ] Arrow heads

### Phase 4: Visual Polish
- [ ] Rounded rectangles for nodes
- [ ] State type colors
- [ ] Default state indicator
- [ ] Transition labels
- [ ] Animation thumbnails in nodes

### Phase 5: Auto-Layout
- [ ] Hierarchical layout algorithm
- [ ] Force-directed layout
- [ ] Grid snapping
- [ ] Layout persistence (.layout.json)

### Phase 6: Play Mode Integration
- [ ] Current state highlight
- [ ] Transition animation
- [ ] Live parameter display
- [ ] Read-only mode during play

---

## Estimated Effort

| Phase | Time |
|-------|------|
| Phase 1: Basic Graph | 1 week |
| Phase 2: Node Interaction | 1 week |
| Phase 3: Transition Creation | 1 week |
| Phase 4: Visual Polish | 0.5 week |
| Phase 5: Auto-Layout | 1 week |
| Phase 6: Play Mode | 0.5 week |
| **Total** | **5 weeks** |

---

## Decision: Use imgui-node-editor Library

**Final Choice:** Use `imgui-node-editor` (already included in imgui-java dependency)

| Factor | Decision |
|--------|----------|
| **Library** | imgui-node-editor (NOT custom, NOT imnodes) |
| **Layout** | Graph + Side Inspector |
| **View Toggle** | No - replace list view entirely |
| **Play Mode** | Deferred to separate play-mode-inspection plan |
| **Time Estimate** | ~2 weeks (vs 5 weeks custom) |

### Self-Loop Handling

imgui-node-editor doesn't render self-loops well (bezier collapses). Solution:
1. Allow self-transitions in data model
2. Skip `NodeEditor.link()` for self-loops
3. Render manually using `ImDrawList.addBezierCubic()` with loop control points

```java
// Self-loop rendering (above the node)
if (fromState.equals(toState)) {
    ImVec2 nodePos = getNodePosition(fromState);
    float loopHeight = 50f;
    drawList.addBezierCubic(
        nodePos.x + nodeWidth, nodePos.y + nodeHeight/2,  // Start (right side)
        nodePos.x + nodeWidth + loopHeight, nodePos.y - loopHeight,  // Control 1
        nodePos.x + loopHeight, nodePos.y - loopHeight,  // Control 2
        nodePos.x, nodePos.y + nodeHeight/2,  // End (left side, same node)
        color, thickness
    );
}
```
