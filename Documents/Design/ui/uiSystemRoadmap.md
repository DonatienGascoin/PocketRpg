# UI System Implementation Roadmap

## Architecture Overview

```
UIComponent (abstract) - Base class for all UI components
├── UICanvas    - Marks subtree as UI, defines render mode/sort order
├── UIImage     - Renders textured elements
├── UIPanel     - Renders solid color rectangles
└── (future: UIText, UIButton, etc.)

UITransform (Component) - MANDATORY for UI elements, handles anchoring/positioning

UIRenderer (interface) - Platform abstraction
└── OpenGLUIRenderer - OpenGL implementation

UIRendererBackend (interface) - Draw operations
└── OpenGLUIRenderer implements both interfaces
```

**Key Principles:**
- Type-level separation: UIImage ≠ SpriteRenderer (compiler-enforced)
- Canvas requirement: No canvas ancestor → error, won't render
- UITransform mandatory: Error if UIComponent exists without UITransform
- Pillarbox/letterbox: UI uses game resolution, scales with viewport
- Bottom-left origin: Anchor (0,0) = bottom-left, (1,1) = top-right

---

## Phase 0: GameObject Parenting (Pre-requisite) ✓

**Status:** Implemented in Phase 1

Changes to `GameObject.java`:
- Added `parent` field and `children` list
- `setParent()` with circular reference detection
- Scene inheritance and component registration on reparent
- Recursive `update()`, `lateUpdate()`, `destroy()` for children

---

## Phase 1: Core UI Rendering ✓

**Status:** Implemented

**New Files:**
- `ui/UIComponent.java` - Abstract base class
- `ui/UICanvas.java` - Canvas marker component
- `ui/UIImage.java` - Textured UI element
- `ui/UIPanel.java` - Solid color rectangle
- `ui/UIRenderer.java` - Interface
- `ui/UIRendererBackend.java` - Draw operations interface
- `ui/OpenGLUIRenderer.java` - OpenGL implementation

**Modified Files:**
- `core/GameObject.java` - Parent/child hierarchy
- `core/GameEngine.java` - UIRenderer integration
- `core/GameApplication.java` - renderUI() call after post-processing
- `core/PlatformFactory.java` - createUIRenderer()
- `glfw/GLFWPlatformFactory.java` - Returns OpenGLUIRenderer
- `scenes/Scene.java` - Sorted canvas caching

**Features:**
- Screen-space overlay rendering
- Canvas sort order (pre-sorted, re-sort on change)
- Solid color panels and textured images
- Renders after post-processing (unaffected by effects)

---

## Phase 2: UITransform & Anchoring

**Goal:** Mandatory UITransform with anchor-based positioning, pillarbox/letterbox support

**New Files:**
- `ui/UITransform.java`

**Modified Files:**
- `ui/UIComponent.java` - Validate UITransform exists
- `ui/OpenGLUIRenderer.java` - Use game resolution, bottom-left origin
- `glfw/GLFWPlatformFactory.java` - Pass GameConfig to UIRenderer

**UITransform Features:**
```java
public class UITransform extends Component {
    // Anchor point relative to parent (0-1)
    // (0,0) = bottom-left, (1,1) = top-right
    private Vector2f anchor = new Vector2f(0, 0);
    
    // Offset from anchor point in pixels
    private Vector2f offset = new Vector2f(0, 0);
    
    // Pivot point for rotation/scaling (0-1 relative to own size)
    private Vector2f pivot = new Vector2f(0, 0);
    
    // Size in pixels
    private Vector2f size = new Vector2f(100, 100);
    
    // Calculated screen position (updated before render)
    public Vector2f getScreenPosition(float parentWidth, float parentHeight);
}
```

**Anchor Presets:**
```java
public enum AnchorPreset {
    BOTTOM_LEFT(0, 0),
    BOTTOM_CENTER(0.5f, 0),
    BOTTOM_RIGHT(1, 0),
    CENTER_LEFT(0, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1, 0.5f),
    TOP_LEFT(0, 1),
    TOP_CENTER(0.5f, 1),
    TOP_RIGHT(1, 1);
}
```

**Pillarbox/Letterbox Behavior:**
- UIRenderer receives `GameConfig` with `gameWidth`/`gameHeight`
- Projection uses game resolution: `ortho(0, gameWidth, 0, gameHeight, -1, 1)`
- UI scales uniformly with the game viewport
- Anchor calculations use game resolution, not window resolution

**Validation:**
- `UIComponent.onStart()` throws error if no `UITransform` on same GameObject
- Clear error message: "UIImage requires UITransform component"

**Usage Example:**
```java
// Health bar anchored to top-left
GameObject healthBar = new GameObject("HealthBar");
UITransform transform = new UITransform();
transform.setAnchor(AnchorPreset.TOP_LEFT);
transform.setOffset(20, -20);  // 20px from left, 20px down from top
transform.setSize(200, 30);
healthBar.addComponent(transform);
healthBar.addComponent(new UIPanel(200, 30));  // Size matches UITransform
healthBar.setParent(canvas);

// Icon anchored to bottom-right
GameObject icon = new GameObject("Icon");
UITransform iconTransform = new UITransform();
iconTransform.setAnchor(AnchorPreset.BOTTOM_RIGHT);
iconTransform.setOffset(-74, 10);  // 74px from right edge, 10px up
iconTransform.setSize(64, 64);
icon.addComponent(iconTransform);
icon.addComponent(new UIImage(texture));
icon.setParent(canvas);
```

---

## Phase 3: Input Handling

**Goal:** Clickable buttons with hover feedback and input blocking.

**New Files:**
- `ui/UIButton.java` - Clickable button with built-in visuals
- `ui/UIInputHandler.java` - Processes mouse input for UI elements

**Modified Files:**
- `config/GameConfig.java` - Add `uiButtonHoverTint` property
- `core/GameEngine.java` - Integrate UIInputHandler
- `input/Input.java` - Add `isMouseConsumed()`, `isMouseButtonJustPressed/Released`

**UIButton Features:**
```java
public class UIButton extends UIComponent {
    // Visual modes
    private Sprite image;           // Optional image
    private Vector4f color;         // Solid color (used if no image)
    
    // Hover tint (darkens by this factor, 0.1 = 10% darker)
    private float hoverTint;        // Per-button override, or uses GameConfig default
    
    // Callbacks
    private Runnable onClick;
    private Runnable onHover;
    private Runnable onExit;
    
    // State
    private boolean isHovered;
}
```

**Hover Tint Behavior:**
- If `onHover`/`onExit` callbacks are set → no automatic tint (user handles visuals)
- If no callbacks → apply default darkening tint from GameConfig
- Per-button `hoverTint` overrides GameConfig default

**GameConfig Addition:**
```java
// Default hover tint (10% darker)
private float uiButtonHoverTint = 0.1f;
```

**Input Blocking:**
- `UIInputHandler.update()` called before game input processing
- If mouse is over any `raycastTarget` UI element, `Input.isMouseConsumed()` returns true
- Game code checks `isMouseConsumed()` before processing clicks

**Hit Testing:**
- Uses UITransform bounds (position + size)
- Respects `raycastTarget` property (can disable per-element)
- Tests in reverse render order (top elements first)

**Usage Example:**
```java
// Simple button with color
GameObject btn = new GameObject("PlayButton");
btn.addComponent(new UITransform(200, 50));
UIButton playBtn = new UIButton();
playBtn.setColor(0.2f, 0.6f, 0.2f, 1f);  // Green
playBtn.setOnClick(() -> startGame());
btn.addComponent(playBtn);
btn.setParent(canvas);

// Button with image
UIButton imgBtn = new UIButton();
imgBtn.setImage(buttonSprite);
imgBtn.setOnClick(() -> openMenu());
imgBtn.setOnHover(() -> playHoverSound());  // Custom hover, disables auto-tint

// Check if UI consumed input
if (!Input.isMouseConsumed()) {
    // Safe to process game clicks
}
```

**Required Input.java additions:**
```java
boolean isMouseButtonJustPressed(int button);
boolean isMouseButtonJustReleased(int button);
boolean isMouseConsumed();
void setMouseConsumed(boolean consumed);  // Called by UIInputHandler
```

---

## Phase 4: Text Rendering

**New Files:**
- `ui/UIText.java` - Text component
- `ui/BitmapFont.java` - Font data structure
- `ui/BitmapFontLoader.java` - Loads BMFont format

**Features:**
- BMFont format support (.fnt + atlas .png)
- Text alignment (left, center, right)
- Color and alpha
- Word wrapping (optional)

**External Tools:**
- BMFont (Windows): https://www.angelcode.com/products/bmfont/
- Hiero (cross-platform): LibGDX tool

---

## Phase 5: Advanced Features

### 9-Slice Sprites
Resizable images that preserve corner/edge appearance.

```java
public class UIImage {
    // 9-slice borders (left, right, top, bottom in pixels)
    private Vector4f sliceBorders = null;  // null = no slicing
    
    public void setSliceBorders(float left, float right, float top, float bottom);
}
```

### Fill Modes
Progress bars, cooldown indicators.

```java
public enum FillMode {
    NONE,           // Normal rendering
    HORIZONTAL,     // Fill left-to-right
    VERTICAL,       // Fill bottom-to-top
    RADIAL_90,      // Radial fill 90°
    RADIAL_180,     // Radial fill 180°
    RADIAL_360      // Radial fill 360°
}

public class UIImage {
    private FillMode fillMode = FillMode.NONE;
    private float fillAmount = 1.0f;  // 0-1
}
```

---

## Phase 6: Stretch Mode (Optional)

**Goal:** Elements that stretch to fill parent bounds.

Instead of a single anchor point, use anchor min/max:

```java
public class UITransform {
    // Point anchor mode (current)
    private Vector2f anchor;
    
    // Stretch mode - when anchorMin != anchorMax
    private Vector2f anchorMin = new Vector2f(0, 0);
    private Vector2f anchorMax = new Vector2f(0, 0);  // Same as min = point anchor
    
    // For stretch mode, offset becomes edge padding
    private Vector4f padding;  // left, right, bottom, top
}
```

**Example - Bottom toolbar stretching horizontally:**
```java
UITransform toolbar = new UITransform();
toolbar.setAnchorMin(0, 0);      // Left edge at parent's left
toolbar.setAnchorMax(1, 0);      // Right edge at parent's right
toolbar.setPadding(10, 10, 10, 0);  // 10px padding on sides and bottom
toolbar.setHeight(50);           // Fixed height
```

**When to use:**
- Toolbars that span full width
- Sidebars that span full height  
- Backgrounds that fill entire canvas
- Responsive layouts

---

## File Structure (Complete)

```
src/main/java/com/pocket/rpg/
├── core/
│   ├── GameObject.java         [Phase 1]
│   ├── GameEngine.java         [Phase 1]
│   ├── GameApplication.java    [Phase 1]
│   └── PlatformFactory.java    [Phase 1]
├── glfw/
│   └── GLFWPlatformFactory.java [Phase 1, modified Phase 2]
├── scenes/
│   └── Scene.java              [Phase 1]
└── ui/
    ├── UIComponent.java        [Phase 1]
    ├── UICanvas.java           [Phase 1]
    ├── UIImage.java            [Phase 1, modified Phase 5]
    ├── UIPanel.java            [Phase 1]
    ├── UITransform.java        [Phase 2]
    ├── AnchorPreset.java       [Phase 2]
    ├── UIRenderer.java         [Phase 1]
    ├── UIRendererBackend.java  [Phase 1]
    ├── OpenGLUIRenderer.java   [Phase 1, modified Phase 2]
    ├── UIInputHandler.java     [Phase 3]
    ├── UIButton.java           [Phase 3]
    ├── UIText.java             [Phase 4]
    ├── BitmapFont.java         [Phase 4]
    └── BitmapFontLoader.java   [Phase 4]
```
