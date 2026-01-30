# Animator Editor V2 - Layout Redesign

## Overview

Redesign the Animator Editor panel to match Unity's layout:
- **Left column**: Parameters list (replaces bottom bar)
- **Right column**: Node graph (larger area)
- **External Inspector panel**: Shows state/transition details when selected

## Current vs Proposed Layout

**Current:**
```
┌──────────────────────────────────────────────┐
│  Toolbar                                     │
├───────────────────────────┬──────────────────┤
│                           │  Side Inspector  │
│   Graph Canvas            │  (280px fixed)   │
│                           │                  │
├───────────────────────────┴──────────────────┤
│  Parameters Bar (40px)                       │
└──────────────────────────────────────────────┘
```

**Proposed:**
```
┌──────────────────────────────────────────────┐
│  Toolbar (simplified)                        │
├──────────────┬───────────────────────────────┤
│  Parameters  │                               │
│  List        │   Graph Canvas                │
│  (200px)     │   (larger area)               │
│              │                               │
│  + Add Param │                               │
└──────────────┴───────────────────────────────┘

Inspector Panel (separate window):
┌──────────────────────────────────────────────┐
│  State: idle                                 │
│  ─────────────────────────────────────────   │
│  Name: [idle          ]                      │
│  Type: [Simple ▼]                            │
│  Animation: [idle.anim] [...]                │
│  ☑ Default State                             │
└──────────────────────────────────────────────┘
```

## Design Decisions

1. **Parameters list**: Display-only list with name and type. Add/delete via buttons. Simple inline rename on double-click.
2. **Parameter Inspector**: None - parameters are simple, don't need full Inspector treatment.
3. **Only states/transitions use Inspector panel**.

## Implementation Phases

### Phase 1: Create Animator Inspectors

**AnimatorStateInspector.java:**
- Fields: state, controller, onModified callback
- Methods: setSelection(), clearSelection(), hasSelection(), render()
- Renders: Name, Type, Animation(s), Default checkbox

**AnimatorTransitionInspector.java:**
- Fields: transition, controller, onModified callback
- Methods: setSelection(), clearSelection(), hasSelection(), render()
- Renders: From/To, Type, Conditions list

**InspectorPanel.java changes:**
- Add `AnimatorStateInspector animatorStateInspector`
- Add `AnimatorTransitionInspector animatorTransitionInspector`
- In `renderCurrentInspector()`: check animator inspectors before other types
- Add public methods: `setAnimatorState()`, `setAnimatorTransition()`, `clearAnimatorSelection()`

### Phase 2: New AnimatorEditorPanel Layout

**Left Column (200px):**
```
┌────────────────────┐
│ Parameters         │
├────────────────────┤
│ ● direction (dir)  │  ← List items
│ ● isMoving (bool)  │
│ ● attack (trigger) │
├────────────────────┤
│ [+ Bool] [+ Trig]  │  ← Add buttons
│ [+ Direction]      │
└────────────────────┘
```

**Features:**
- Click parameter to select (highlight)
- Right-click to delete
- Double-click to rename (inline)
- Add buttons create with default name

**Right Column (stretch):**
- Graph canvas only
- No more side inspector

### Phase 3: Selection Sync via Events

Use **EditorEventBus** pattern (same as TriggerSelectedEvent):

**New events:**
- `AnimatorStateSelectedEvent(AnimatorState state, AnimatorController controller, Runnable onModified)`
- `AnimatorTransitionSelectedEvent(AnimatorTransition trans, AnimatorController controller, Runnable onModified)`
- `AnimatorSelectionClearedEvent()`

**AnimatorEditorPanel:**
- On state selected: `EditorEventBus.get().publish(new AnimatorStateSelectedEvent(...))`
- On transition selected: `EditorEventBus.get().publish(new AnimatorTransitionSelectedEvent(...))`
- On deselect: `EditorEventBus.get().publish(new AnimatorSelectionClearedEvent())`

**EditorUIController:**
- Subscribe to events in constructor
- Route to InspectorPanel methods

## Files Summary

### New Files
| File | Purpose |
|------|---------|
| `editor/panels/inspector/AnimatorStateInspector.java` | Renders state details in Inspector |
| `editor/panels/inspector/AnimatorTransitionInspector.java` | Renders transition details in Inspector |
| `editor/events/AnimatorStateSelectedEvent.java` | Event when state selected |
| `editor/events/AnimatorTransitionSelectedEvent.java` | Event when transition selected |
| `editor/events/AnimatorSelectionClearedEvent.java` | Event when animator selection cleared |

### Modified Files
| File | Changes |
|------|---------|
| `editor/panels/InspectorPanel.java` | Add animator inspectors, priority dispatch |
| `editor/panels/AnimatorEditorPanel.java` | New layout, publish selection events |
| `editor/EditorUIController.java` | Subscribe to animator selection events |

## Phase 4: Animation Preview Panel (PLANNED)

### Overview
Add a collapsible preview panel on the right side for testing animations and state machine behavior.

### UI Design
```
┌────────────────────────────────────────────────────────────────┐
│  Toolbar                                             [☰]      │
├──────────────┬────────────────────────────┬───────────────────┤
│  Parameters  │                            │   Preview Panel   │
│  List        │   Graph Canvas             │   ┌───────────┐   │
│  (200px)     │                            │   │  Sprite   │   │
│              │   [pulsing active state]   │   │  Preview  │   │
│  ─────────── │                            │   └───────────┘   │
│  direction   │   [pulsing transition]     │   [▶] [⏸] [⏹]    │
│  [DOWN  ▼]   │                            │   ─────────────   │
│  isMoving    │                            │   State: idle     │
│  [  ] ☐      │                            │   Frame: 0/4      │
│  ─────────── │                            │                   │
│  + Add Param │                            │                   │
└──────────────┴────────────────────────────┴───────────────────┘
```

### Features

**1. Hamburger Menu (☰)**
- Toggle button in toolbar right side
- Opens/closes preview panel (third column)
- State persisted in layout data

**2. Parameter Value Fields**
- Parameters list shows editable values:
  - Bool: checkbox
  - Trigger: button (fires once on click)
  - Direction: dropdown (UP/DOWN/LEFT/RIGHT)
- Editing values simulates runtime behavior
- Graph responds to parameter changes

**3. Animation Preview**
- Uses existing `RenderPipeline` for sprite rendering
- Shows current animation frame
- Display: current state name, frame number

**4. Playback Controls**
- Play (▶): Start animation playback and state machine evaluation
- Pause (⏸): Freeze at current frame
- Stop (⏹): Reset to default state

**5. Visual Feedback (Pulsing)**
- **Active state node**: Pulsing green outline/glow
- **Pending transition**: Pulsing orange link (when waiting for completion)
- **Active transition**: Brief flash when transition fires
- Use ImGui animation with `ImGui.getTime()` for pulse effect

### Implementation

**New Classes:**
- `AnimatorPreviewPanel.java` - Preview rendering and controls
- `AnimatorPreviewState.java` - Runtime state for preview (separate from game)

**Modified Files:**
- `AnimatorEditorPanel.java` - Add hamburger menu, third column, parameter value fields
- `AnimatorGraphEditor.java` - Add pulsing effect API

**Render Pipeline Integration:**
```java
// Create a small framebuffer for preview
EditorFramebuffer previewBuffer = new EditorFramebuffer(200, 200);

// In render:
previewBuffer.bind();
// Clear and render sprite
previewBuffer.unbind();

// Display in ImGui
ImGui.image(previewBuffer.getTextureId(), 200, 200);
```

**Pulsing Effect:**
```java
// In AnimatorGraphEditor.renderNode():
float time = (float) ImGui.getTime();
float pulse = 0.5f + 0.5f * (float) Math.sin(time * 4.0);

if (stateName.equals(activeStateName)) {
    int glowColor = packColor(100, (int)(200 * pulse), 100, 200);
    // Draw glow outline
}
```

### Files Summary

| File | Changes |
|------|---------|
| `AnimatorEditorPanel.java` | Hamburger menu, preview column, parameter value editing |
| `AnimatorGraphEditor.java` | setActiveState(), setPendingTransition(), pulsing effects |
| `AnimatorPreviewPanel.java` | NEW - Preview rendering with RenderPipeline |
| `AnimatorPreviewState.java` | NEW - Preview runtime state machine |

## Verification

### Phase 1-3 (Complete)
1. Open Animator Editor
2. Select a controller from dropdown
3. Verify parameters show in left column
4. Verify graph takes up more space
5. Click a node - verify state details appear in Inspector panel
6. Click a transition - verify transition details appear in Inspector panel
7. Click empty canvas - verify selection clears
8. Click self-loop transition - verify it's selectable
9. Edit values in Inspector - verify changes reflect in graph
10. Save (Ctrl+S) - verify changes persist

### Phase 4 (Preview Panel)
1. Click hamburger menu - preview panel opens
2. Set parameter values - graph responds
3. Click Play - animation plays in preview
4. Active state shows pulsing glow
5. Transitions show pulsing when pending
6. Click Pause - preview freezes
7. Click Stop - resets to default state
