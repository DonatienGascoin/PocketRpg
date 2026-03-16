# ScrollView Implementation Plan

## Overview

Add a ScrollView UI component to the engine, enabling vertical scrolling of content that overflows a viewport area. This is needed for the Pokedex menu and any future list-based UI.

**Goal**: Right-click in hierarchy → Create UI Child → Scroll View, which creates a ready-to-use scroll view with viewport, content container, and scrollbar.

## Architecture

### How Unity Does It (Reference)
Unity's ScrollView creates a hierarchy:
```
ScrollView (ScrollRect component + Image)
├── Viewport (Mask + Image)
│   └── Content (UITransform, usually with VerticalLayoutGroup)
└── Scrollbar Vertical (Scrollbar component + Image)
    └── Sliding Area
        └── Handle (Image)
```

### Our Approach
We follow a similar pattern with a reusable UIMask component:
```
ScrollView (UIScrollView component + UIPanel background)
├── Viewport (UIMask + UITransform - clips children to its bounds)
│   └── Content (UITransform + optional VerticalLayoutGroup)
└── Scrollbar (UIScrollbar component + UIImage track)
    └── Handle (UIImage handle)
```

Key points:
- **UIMask is a standalone reusable component** — any GameObject with UIMask clips its children to its bounds. Useful for scroll views, avatar frames, inventory slots, cropped panels, etc.
- UIScrollView manages scroll offset on its Content child; it doesn't know about clipping — that's UIMask's job
- Scrollbar is a separate component (UIScrollbar) that UIScrollView references
- Content sizing is computed from children automatically

## Phases

### Phase 1: UIMask Component + Scissor Clipping in UIRenderer

Create a reusable `UIMask` component that clips all children to the mask owner's bounds. The renderer detects UIMask during hierarchy traversal and applies scissor test. Also add render culling so children entirely outside the scissor rect are skipped (no draw calls, no recursion into their subtree).

- [x]Create `UIMask` component extending `UIComponent`
  - Serialized field: `enabled` (inherited, toggles clipping on/off)
  - Serialized field: `showMaskGraphic` (boolean, default true) — whether to render the mask owner's own UIPanel/UIImage or just use it as an invisible clipping rect
- [x]Add scissor stack to `UIRenderer` for nested mask support
  - `pushScissor(float x, float y, float width, float height)` — intersects with current scissor (nested masks clip to intersection)
  - `popScissor()` — restores previous scissor rect
  - Convert game coordinates to window pixel coordinates via `ViewportConfig`
- [x]In `UIRenderer.renderCanvasSubtree()`, detect `UIMask`:
  - Before rendering children: push scissor matching the mask owner's screen bounds
  - After rendering children: pop scissor
  - If `showMaskGraphic` is false, skip rendering the owner's UIPanel/UIImage
- [x]**Render culling**: When a scissor rect is active, before rendering any child check if its screen bounds intersect the current scissor rect. If entirely outside, skip `render()` and don't recurse into its children. This means for a 151-item list, only the ~15 visible items incur draw calls — the rest are skipped entirely at the traversal level.
- [x]Add `pushScissor`/`popScissor` to `UIRendererBackend` interface

**Files:**
| File | Change |
|------|--------|
| `components/ui/UIMask.java` | **NEW** - Reusable mask/clipping component |
| `rendering/ui/UIRenderer.java` | Add scissor stack, detect UIMask in traversal, add render culling |
| `rendering/ui/UIRendererBackend.java` | Add `pushScissor`/`popScissor` interface methods |

### Phase 2: UIMask Testing

UIMask is a foundational component that other UI features depend on. Thorough testing before building on top of it is critical.

- [x]**Unit tests** (`UIMaskTest.java`):
  - Scissor rect computation from UITransform screen bounds
  - Nested mask intersection (inner mask clips to intersection of both rects)
  - `showMaskGraphic` flag behavior
  - Mask with no UITransform (should be no-op / not crash)
  - Mask on disabled GameObject (should not clip)
  - Render culling: child fully outside scissor rect → skipped (not rendered, children not traversed)
  - Render culling: child partially inside scissor rect → rendered (not culled)
  - Render culling: child fully inside scissor rect → rendered normally

- [x]**Manual visual tests** — verify in editor with these hierarchy patterns:
  - **Simple mask**: UIMask parent with children extending beyond bounds → children clipped
  - **Nested masks**: UIMask inside UIMask → inner clips to intersection
  - **Deep hierarchy**: UIMask → Panel → Panel → Image (mask clips grandchildren/great-grandchildren)
  - **Mixed content**: UIMask parent with UIPanel, UIImage, UIText, UIButton children → all types clip correctly
  - **Mask with layout group**: UIMask + VerticalLayoutGroup → layout children clipped when they overflow
  - **Mask toggle**: Disable/enable UIMask at runtime → clipping toggles
  - **Rotated mask owner**: Verify behavior (scissor is axis-aligned, so rotation = bounding box of rotated rect)
  - **Zero-size or very small mask**: Edge case, should clip everything
  - **Mask with percentage-sized children**: Children using PERCENT sizing inside mask
  - **Many children (performance)**: UIMask + VerticalLayoutGroup with 150+ children → verify only visible items cause draw calls (check with frame debugger or draw call counter if available)

**Files:**
| File | Change |
|------|--------|
| `test/.../components/ui/UIMaskTest.java` | **NEW** - Unit tests for UIMask |

### Phase 3: UIScrollView Component

Core scroll logic — manages scroll offset and content bounds. Does NOT handle clipping (that's UIMask's job on the Viewport child).

- [x]Create `UIScrollView` component extending `UIComponent`
- [x]Track `scrollOffset` (float, vertical only for now)
- [x]Compute `contentHeight` from children of the Content child (max bottom edge)
- [x]Apply scroll offset to Content's UITransform Y position each frame
- [x]Clamp scroll offset to valid range `[0, max(0, contentHeight - viewportHeight)]`
- [x]Expose `getScrollNormalized()` (0..1) and `setScrollNormalized(float)` for scrollbar integration
- [x]Optional: smooth scrolling with lerp

**Serialized Fields:**
- `scrollSensitivity` (float, default 20) — pixels per scroll tick
- `showScrollbar` (enum: ALWAYS, AUTO, NEVER)

**Runtime State (not serialized):**
- `scrollOffset` (float)
- `contentHeight` (float, computed)
- `viewportHeight` (float, computed)

**Files:**
| File | Change |
|------|--------|
| `components/ui/UIScrollView.java` | **NEW** - ScrollView component |

### Phase 4: UIScrollbar Component

Visual scrollbar that reflects and controls scroll position.

- [x]Create `UIScrollbar` component extending `UIComponent`
- [x]Render track (uses parent's UIImage/UIPanel as background)
- [x]Compute handle size proportional to `viewportHeight / contentHeight`
- [x]Compute handle position from scroll offset
- [x]Handle click-on-track to jump scroll position
- [x]Handle drag on handle to scroll
- [x]Reference to UIScrollView (via ComponentRef or parent traversal)

**Serialized Fields:**
- `handleSprite` (SpriteRef) — sprite for the handle
- `handleColor` (Vector4f) — color/tint for the handle
- `minHandleSize` (float, default 20) — minimum handle height in pixels

**Files:**
| File | Change |
|------|--------|
| `components/ui/UIScrollbar.java` | **NEW** - Scrollbar component |

### Phase 5: Rendering Integration

Wire UIScrollView into the render traversal. The scroll view applies the content offset; UIMask on the Viewport handles clipping automatically.

- [x]In `UIRenderer.renderCanvasSubtree()`, detect UIScrollView component
- [x]Before rendering Content children: apply scroll offset to Content's Y position
- [x]UIMask on the Viewport already handles scissor — no extra clipping logic needed here
- [x]Scrollbar renders outside the viewport (sibling), so it's never clipped

**Files:**
| File | Change |
|------|--------|
| `rendering/ui/UIRenderer.java` | Add scroll view content offset in traversal |

### Phase 6: Input Integration

Handle scroll wheel and scrollbar drag input.

- [x]In `UIInputHandler`, detect UIScrollView components (similar to button collection)
- [x]On mouse wheel over a scroll view's viewport bounds, adjust scroll offset
- [x]On mouse press on scrollbar handle, begin drag
- [x]On mouse drag, update scroll position proportionally
- [x]On click on scrollbar track (not handle), jump to position
- [x]Consume mouse input when interacting with scroll view

**Files:**
| File | Change |
|------|--------|
| `ui/UIInputHandler.java` | Add scroll view and scrollbar input handling |
| `input/Input.java` | Add `getMouseScrollRaw()` if not already available |

### Phase 7: Editor Integration (Hierarchy Menu + Factory)

Add "Scroll View" to the right-click Create UI Child menu.

- [x]Add `createScrollView()` to `UIEntityFactory` — creates the full hierarchy:
  - ScrollView root: UITransform (200x300) + UIPanel (background) + UIScrollView
  - Viewport child: UITransform (fills parent minus scrollbar width, PERCENT mode) + **UIMask**
  - Content child: UITransform (fills viewport width, FIXED height that grows)
  - Scrollbar child: UITransform (anchored right, fixed width ~12px, fills height) + UIScrollbar
  - Handle child: UITransform + UIImage (handle graphic)
- [x]Add case to `UIEntityFactory.create()` switch
- [x]Add menu item in `HierarchyTreeRenderer.renderCreateMenuItems()`
- [x]Register UIMask, UIScrollView, and UIScrollbar in `ComponentRegistry` if needed

**Files:**
| File | Change |
|------|--------|
| `editor/scene/UIEntityFactory.java` | Add `createScrollView()`, update switch |
| `editor/panels/hierarchy/HierarchyTreeRenderer.java` | Add "Scroll View" menu item |
| `serialization/ComponentRegistry.java` | Register new components (if needed) |

### Phase 8: Inspector Support

Add inspector fields for the new components.

- [x]UIMask inspector: `showMaskGraphic` toggle
- [x]UIScrollView inspector: `scrollSensitivity`, `showScrollbar` enum
- [x]UIScrollbar inspector: `handleColor`, `minHandleSize`, `handleSprite`
- [x]These should work automatically via the reflection-based inspector if fields are properly typed

**Files:**
| File | Change |
|------|--------|
| Likely automatic via existing `ComponentTypeAdapterFactory` and inspector reflection |

### Phase 9: ScrollView Testing & Code Review

- [x]**Unit tests** (`UIScrollViewTest.java`):
  - Scroll offset clamping (can't scroll past content, can't go negative)
  - Content height computation from children
  - Normalized scroll get/set
  - Scroll with content smaller than viewport (no scrolling possible)
- [x]**Unit tests** (`UIScrollbarTest.java`):
  - Handle size proportional to viewport/content ratio
  - Handle position from scroll offset
  - Min handle size enforcement
- [x]**Manual tests**:
  - Create ScrollView in editor, add children, verify scrolling
  - Verify content clipped outside viewport (UIMask working)
  - Scrollbar handle size adjusts as content grows/shrinks
  - Scrollbar drag and track click
  - ScrollView inside another ScrollView (nested — should both work independently)
  - ScrollView with different child component types (Image, Text, Button, Panel)
  - Buttons inside ScrollView are clickable and clipped correctly
- [x]Code review

**Files:**
| File | Change |
|------|--------|
| `test/.../components/ui/UIScrollViewTest.java` | **NEW** |
| `test/.../components/ui/UIScrollbarTest.java` | **NEW** |

## Key Design Decisions

1. **UIMask as reusable component**: Clipping is decoupled from scrolling. UIMask can be used anywhere — scroll views, cropped images, overflow containers. UIScrollView only manages scroll offset; clipping is UIMask's responsibility.

2. **Scissor test over stencil buffer**: Scissor is simpler, hardware-accelerated, and sufficient for rectangular clipping. Stencil would be needed for non-rectangular masks (future feature). Nested masks use scissor intersection.

3. **Content height from children**: UIScrollView computes content height by finding the maximum bottom edge of all content children. If Content has a VerticalLayoutGroup, the layout determines positioning and we just need the total.

4. **Scroll offset on Content transform**: Rather than modifying each child, we offset the Content container's Y position. Combined with UIMask on the Viewport, this gives correct scrolling.

5. **Scrollbar as sibling**: The scrollbar is a sibling of the viewport (not inside it), so it's outside UIMask's clipping region and always visible.

6. **Render culling at the traversal level**: When a scissor rect is active, children whose screen bounds fall entirely outside it are skipped — no `render()` call, no recursion into their subtree. This keeps performance flat regardless of total item count (only visible items cost draw calls). Layout still runs on all children (cheap float math), but rendering is O(visible) not O(total).

7. **No virtualization needed at this scale**: 151 items with render culling is plenty fast. Virtualization (object pooling / recycler pattern) would only be needed for 1000+ items and adds significant complexity (data binding, pool management, layout bypass). Not worth it for the Pokedex use case.

8. **Vertical only for now**: Horizontal scrolling can be added later by extending the same pattern. The component has a clear extension point for this.

## Testing Strategy

Testing is split into two focused phases:

- **Phase 2 (UIMask)**: Tested in isolation before anything depends on it. Covers unit tests for scissor math + manual visual tests across many hierarchy patterns (nested masks, deep hierarchies, mixed content types, layout groups, edge cases).

- **Phase 9 (ScrollView)**: Tests scroll-specific logic (offset clamping, content height, scrollbar math) + integration tests with UIMask (content clipping, nested scroll views, interactive children inside scroll).