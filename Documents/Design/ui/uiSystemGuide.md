# UI System Guide

## Overview

The UI system uses a component-based architecture on top of GameObjects. UI elements are positioned using anchors relative to their parent, with coordinates in game resolution (pillarbox/letterbox scaling).

**Key Principles:**
- All UI components extend `UIComponent` base class
- All UI components require a `UICanvas` ancestor to render
- All UI components require a `UITransform` component (mandatory)
- Coordinate system: (0,0) = bottom-left, (1,1) = top-right
- Uses game resolution, not window resolution

## Components

### UICanvas
Marks a GameObject subtree as UI. Required for any UI element to render.

```java
GameObject canvasGO = new GameObject("MainCanvas");
canvasGO.addComponent(new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, sortOrder));
scene.addGameObject(canvasGO);
```

**sortOrder**: Higher values render on top.

### UITransform (Mandatory)
Handles anchor-based positioning. Required on every GameObject with UI components.

```java
UITransform transform = new UITransform();
transform.setAnchor(AnchorPreset.TOP_LEFT);  // (0, 1)
transform.setOffset(20, -30);                 // 20px right, 30px down
transform.setSize(200, 50);
transform.setPivot(0, 0);                     // Bottom-left pivot (default)
```

**Anchor Presets:**
```
TOP_LEFT (0,1)    TOP_CENTER (0.5,1)    TOP_RIGHT (1,1)
CENTER_LEFT (0,0.5)   CENTER (0.5,0.5)   CENTER_RIGHT (1,0.5)
BOTTOM_LEFT (0,0) BOTTOM_CENTER (0.5,0) BOTTOM_RIGHT (1,0)
```

**Offset**: Pixels from anchor point (positive X = right, positive Y = up)

**Pivot**: Where on the element the anchor attaches (0-1). Default (0,0) = bottom-left corner.

### UIImage
Renders a sprite/texture.

```java
GameObject icon = new GameObject("Icon");
icon.addComponent(new UITransform(64, 64));  // size
icon.addComponent(new UIImage(texture));
icon.setParent(canvasGO);
```

### UIPanel
Renders a solid color rectangle. No texture needed.

```java
GameObject panel = new GameObject("Panel");
UITransform t = new UITransform();
t.set(AnchorPreset.TOP_LEFT, 10, -10, 200, 50);
panel.addComponent(t);
UIPanel p = new UIPanel();
p.setColor(0, 0, 0, 0.5f);
panel.addComponent(p);
panel.setParent(canvasGO);
```

## Complete Example: HUD

```java
private void createHUD() {
    // Canvas
    GameObject canvas = new GameObject("HUDCanvas");
    canvas.addComponent(new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, 0));
    scene.addGameObject(canvas);
    
    // Health bar background - anchored to top-left
    GameObject healthBg = new GameObject("HealthBg");
    UITransform healthBgT = new UITransform();
    healthBgT.setAnchor(AnchorPreset.TOP_LEFT);
    healthBgT.setOffset(20, -20);
    healthBgT.setSize(200, 25);
    healthBg.addComponent(healthBgT);
    UIPanel bgPanel = new UIPanel();
    bgPanel.setColor(0.3f, 0, 0, 1);
    healthBg.addComponent(bgPanel);
    healthBg.setParent(canvas);
    
    // Health bar fill
    GameObject healthFill = new GameObject("HealthFill");
    UITransform healthFillT = new UITransform();
    healthFillT.setAnchor(AnchorPreset.TOP_LEFT);
    healthFillT.setOffset(20, -20);
    healthFillT.setSize(health * 200, 25);  // health is 0-1
    healthFill.addComponent(healthFillT);
    UIPanel fillPanel = new UIPanel();
    fillPanel.setColor(0, 0.8f, 0, 1);
    healthFill.addComponent(fillPanel);
    healthFill.setParent(canvas);
    
    // Icon in bottom-right corner
    GameObject icon = new GameObject("Icon");
    UITransform iconT = new UITransform();
    iconT.setAnchor(AnchorPreset.BOTTOM_RIGHT);
    iconT.setOffset(-74, 10);  // 74px from right edge, 10px up
    iconT.setSize(64, 64);
    icon.addComponent(iconT);
    icon.addComponent(new UIImage(iconTexture));
    icon.setParent(canvas);
}
```

## Coordinate System

- **Origin**: Bottom-left (0, 0)
- **Positive X**: Right
- **Positive Y**: Up
- **Anchors**: (0,0) = bottom-left of parent, (1,1) = top-right of parent
- **Resolution**: Uses game resolution (e.g., 800x600), scales with pillarbox/letterbox

## Render Order

1. Game world renders (affected by post-processing)
2. Post-processing applies
3. **UI renders** (NOT affected by post-processing)

Within UI:
1. Lower sortOrder canvases render first
2. Children render after parents
3. Later siblings render on top of earlier siblings

## Error Messages

| Error | Cause |
|-------|-------|
| "has no UICanvas ancestor" | UI component not parented to a canvas |
| "requires UITransform component" | Missing UITransform on same GameObject |

## File Structure

```
ui/
├── UIComponent.java      - Abstract base class
├── UICanvas.java         - Canvas marker
├── UIImage.java          - Textured element
├── UIPanel.java          - Solid color rectangle
├── UIButton.java         - Clickable button (Phase 3)
├── UITransform.java      - Anchor-based positioning
├── AnchorPreset.java     - Anchor position presets
├── UIRenderer.java       - Interface
├── UIRendererBackend.java - Draw operations interface
├── UIInputHandler.java   - Mouse input processing (Phase 3)
└── OpenGLUIRenderer.java - OpenGL implementation
```

---

## UIButton (Phase 3)

Clickable button with built-in visuals (image or solid color).

### Basic Usage

```java
// Simple button with color
GameObject btn = new GameObject("PlayButton");
UITransform t = new UITransform();
t.setAnchor(AnchorPreset.CENTER);
t.setSize(200, 50);
btn.addComponent(t);

UIButton button = new UIButton();
button.setColor(0.2f, 0.6f, 0.2f, 1f);  // Green
button.setOnClick(() -> startGame());
btn.addComponent(button);
btn.setParent(canvas);
```

### Button with Image

```java
UIButton button = new UIButton(buttonTexture);
button.setOnClick(() -> openMenu());
```

### Hover Tint

By default, buttons darken on hover. The tint is configurable:

```java
// Per-button override
button.setHoverTint(0.2f);  // 20% darker

// Or set default in GameConfig
config.setUiButtonHoverTint(0.15f);  // 15% darker default
```

### Custom Hover Callbacks

When you set `onHover`/`onExit` callbacks, automatic hover tint is disabled:

```java
button.setOnHover(() -> {
    button.setColor(1.0f, 0.5f, 0.5f, 1f);  // Bright on hover
    playHoverSound();
});
button.setOnExit(() -> {
    button.setColor(0.6f, 0.2f, 0.2f, 1f);  // Normal color
});
```

### Input Blocking

UI buttons block game input when hovered/clicked:

```java
// In your game update:
if (input.isMouseButtonJustPressed(0) && !input.isMouseConsumed()) {
    // Safe to process game click - UI didn't consume it
    handleGameClick();
}
```

### UIInputHandler Setup

```java
// In GameApplication or similar:
UIInputHandler uiInputHandler = new UIInputHandler(gameConfig);
gameEngine.setUIInputHandler(uiInputHandler);

// In game loop, before game input processing:
// Convert screen mouse coordinates to game coordinates first
float gameMouseX = convertToGameX(Input.getMousePosition().x);
float gameMouseY = convertToGameY(Input.getMousePosition().y);
gameEngine.updateUIInput(gameMouseX, gameMouseY);
```

### Input System Integration

The UI system integrates with the existing Input system via:

```java
// Check if UI consumed input before processing game clicks
if (Input.getMouseButtonDown(KeyCode.MOUSE_BUTTON_LEFT) && !Input.isMouseConsumed()) {
    // Safe to process game click - UI didn't consume it
    handleGameClick();
}
```

**New methods added to InputContext/Input:**
- `isMouseConsumed()` - Returns true if UI consumed mouse input this frame
- `setMouseConsumed(boolean)` - Called by UIInputHandler (not for game code)
