# Feature: UI Support

## Overview

Add UI creation and editing capabilities to the editor. UI elements are stored as part of the scene (scene-embedded) but edited in a dedicated "UI Editor" panel with canvas preview and visual manipulation.

## Goals

- Create and edit UI hierarchies visually
- Support existing UI components: UICanvas, UIImage, UIPanel, UIButton, UIText
- Visual anchor/position manipulation in canvas preview
- Property editing via same reflection system as entities
- Serialize UI alongside scene data

## Non-Goals

- Layout containers (HBox, VBox, Grid) - future feature
- Animation/tweening editor
- Data binding / MVVM patterns
- Runtime UI hot-reload

---

## Current State

### Existing UI System

Your runtime UI system is functional:

```java
// UICanvas - root marker
// UITransform - anchor-based positioning
// UIComponent - base class (UIImage, UIPanel, UIButton, UIText)
// UIRenderer - renders to screen
```

UI is currently created programmatically:

```java
GameObject canvasGO = new GameObject("MainCanvas");
canvasGO.addComponent(new UICanvas());

GameObject buttonObj = new GameObject("Button");
buttonObj.addComponent(new UITransform(150, 40));
buttonObj.addComponent(new UIButton());
canvasGO.addChild(buttonObj);
```

### What's Missing

- No editor representation of UI
- No visual editing
- No serialization
- No preview in editor

---

## Proposed Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                      EditorScene                             │
├─────────────────────────────────────────────────────────────┤
│  Layers: [...]                                               │
│  Entities: [...]                                             │
│  UI Roots: [EditorUICanvas, EditorUICanvas, ...]            │  ← NEW
│  Collision: [...]                                            │
│  Camera: [...]                                               │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                     UI Editor Panel                          │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌────────────────────────────────────┐   │
│  │  UI Hierarchy │  │         Canvas Preview             │   │
│  │               │  │  ┌─────────────────────────────┐   │   │
│  │  ▼ MainCanvas │  │  │                             │   │   │
│  │    ▼ Panel    │  │  │    [Button]     [Image]     │   │   │
│  │      Button   │  │  │                             │   │   │
│  │      Text     │  │  │         [Panel]             │   │   │
│  │    Image      │  │  │                             │   │   │
│  │               │  │  └─────────────────────────────┘   │   │
│  └──────────────┘  └────────────────────────────────────┘   │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                    UI Inspector                         │ │
│  │  Name: [Button        ]                                 │ │
│  │  ─────────────────────────────────────                  │ │
│  │  UITransform                                            │ │
│  │    Anchor: [BOTTOM_RIGHT ▼]                             │ │
│  │    Offset: [-85, -30]                                   │ │
│  │    Size:   [150, 40]                                    │ │
│  │  UIButton                                               │ │
│  │    Color:  [■■■■■■]                                     │ │
│  │    HoverTint: [0.2]                                     │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### EditorUIElement

Wrapper for UI elements in the editor (similar to EditorEntity).

```java
public class EditorUIElement {
    private String id;
    private String name;
    private String parentId;                    // Hierarchy
    private List<String> childIds;
    
    // Component data (same as EditorEntity)
    private List<ComponentData> components;     // UITransform, UIButton, etc.
    
    // Editor state
    private boolean expanded = true;            // In hierarchy
    private boolean visible = true;             // In preview
    
    // Convenience
    public UITransformData getTransformData();
    public boolean hasComponent(String type);
}
```

### EditorUICanvas

Root container for a UI hierarchy.

```java
public class EditorUICanvas {
    private String id;
    private String name;
    private UICanvas.RenderMode renderMode;
    private int sortOrder;
    
    private List<EditorUIElement> elements;     // Flat list, hierarchy via parentId
    
    // Preview settings
    private int previewWidth = 320;             // Configurable preview resolution
    private int previewHeight = 180;
}
```

---

## Key Components

### 1. UIEditorPanel

Main panel containing hierarchy, preview, and inspector.

```java
public class UIEditorPanel {
    private EditorScene scene;
    private EditorUICanvas selectedCanvas;
    private EditorUIElement selectedElement;
    
    // Sub-panels
    private UIHierarchySection hierarchy;
    private UICanvasPreview preview;
    private UIInspectorSection inspector;
    
    public void render() {
        if (ImGui.begin("UI Editor")) {
            renderToolbar();                    // Canvas selector, Add Canvas, etc.
            
            // Split layout
            ImGui.columns(3);
            hierarchy.render();
            ImGui.nextColumn();
            preview.render();
            ImGui.nextColumn();
            inspector.render();
            ImGui.columns(1);
        }
        ImGui.end();
    }
}
```

### 2. UIHierarchySection

Tree view of UI elements within selected canvas.

```java
public class UIHierarchySection {
    
    public void render(EditorUICanvas canvas) {
        // Tree nodes for each root element
        // Drag-drop for reordering
        // Right-click context menu (Delete, Duplicate, Add Child)
        // Double-click to rename
    }
    
    private void renderElementNode(EditorUIElement element) {
        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        
        if (element == selectedElement) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        
        if (element.getChildIds().isEmpty()) {
            flags |= ImGuiTreeNodeFlags.Leaf;
        }
        
        String icon = getIconForElement(element);  // Based on component type
        boolean open = ImGui.treeNodeEx(element.getId(), flags, icon + " " + element.getName());
        
        handleInteraction(element);
        
        if (open) {
            for (String childId : element.getChildIds()) {
                renderElementNode(getElement(childId));
            }
            ImGui.treePop();
        }
    }
}
```

### 3. UICanvasPreview

Renders a preview of the selected canvas.

```java
public class UICanvasPreview {
    private EditorFramebuffer previewFramebuffer;
    private UIRendererBackend previewRenderer;
    
    // Preview settings
    private int previewWidth = 320;
    private int previewHeight = 180;
    private float zoom = 1.0f;
    
    public void render(EditorUICanvas canvas) {
        // Render to framebuffer
        renderCanvasToFramebuffer(canvas);
        
        // Display framebuffer as ImGui image
        ImGui.image(previewFramebuffer.getTextureId(), displayWidth, displayHeight);
        
        // Overlay: selection handles, guides
        if (selectedElement != null) {
            renderSelectionOverlay(selectedElement);
        }
        
        // Handle click-to-select
        if (ImGui.isItemClicked()) {
            selectElementAt(mouseX, mouseY);
        }
        
        // Handle drag-to-move (optional visual editing)
        if (dragging) {
            updateElementPosition(selectedElement, dragDelta);
        }
    }
    
    private void renderCanvasToFramebuffer(EditorUICanvas canvas) {
        // Convert EditorUIElements → temporary UIComponents
        // Use existing UIRendererBackend to render
    }
}
```

### 4. UIInspectorSection

Properties for selected element (uses ReflectionFieldEditor).

```java
public class UIInspectorSection {
    
    public void render(EditorUIElement element) {
        if (element == null) {
            ImGui.textDisabled("Select a UI element");
            return;
        }
        
        // Name
        String name = element.getName();
        if (ImGui.inputText("Name", name)) {
            element.setName(name);
            scene.markDirty();
        }
        
        ImGui.separator();
        
        // Components
        for (ComponentData comp : element.getComponents()) {
            if (ImGui.collapsingHeader(comp.getDisplayName(), ImGuiTreeNodeFlags.DefaultOpen)) {
                if (ReflectionFieldEditor.drawComponentData(comp)) {
                    scene.markDirty();
                }
            }
        }
        
        // Add Component
        if (ImGui.button("Add Component")) {
            openUIComponentBrowser();  // Filtered to UI components only
        }
    }
}
```

### 5. UIElementFactory

Creates new UI elements with sensible defaults.

```java
public class UIElementFactory {
    
    public static EditorUIElement createPanel(String name) {
        EditorUIElement element = new EditorUIElement(name);
        element.addComponent(createUITransform(100, 100));
        element.addComponent(createUIPanel());
        return element;
    }
    
    public static EditorUIElement createButton(String name) {
        EditorUIElement element = new EditorUIElement(name);
        element.addComponent(createUITransform(120, 40));
        element.addComponent(createUIButton());
        return element;
    }
    
    public static EditorUIElement createImage(String name) { ... }
    public static EditorUIElement createText(String name, Font font) { ... }
}
```

---

## Data Structures

### Scene Serialization

```java
public class SceneData {
    // Existing
    private String name;
    private List<LayerData> layers;
    private List<EntityData> entities;
    private CollisionMapData collision;
    private CameraSettingsData camera;
    
    // New
    private List<UICanvasData> uiCanvases;
}

public class UICanvasData {
    private String id;
    private String name;
    private String renderMode;          // SCREEN_SPACE_OVERLAY, etc.
    private int sortOrder;
    private List<UIElementData> elements;
}

public class UIElementData {
    private String id;
    private String name;
    private String parentId;            // null for root elements
    private List<ComponentData> components;
}
```

### JSON Example

```json
{
    "name": "MainMenu",
    "uiCanvases": [
        {
            "id": "hud_canvas",
            "name": "HUD",
            "renderMode": "SCREEN_SPACE_OVERLAY",
            "sortOrder": 0,
            "elements": [
                {
                    "id": "health_panel",
                    "name": "Health Panel",
                    "parentId": null,
                    "components": [
                        {
                            "type": "UITransform",
                            "fields": {
                                "anchor": [0, 0],
                                "offset": [10, 10],
                                "width": 200,
                                "height": 30
                            }
                        },
                        {
                            "type": "UIPanel",
                            "fields": {
                                "color": [0.2, 0.2, 0.2, 0.8]
                            }
                        }
                    ]
                }
            ]
        }
    ]
}
```

---

## Toolbar Actions

```
┌─────────────────────────────────────────────────────────────────┐
│ Canvas: [MainCanvas ▼] | [+ Canvas] | [+ Panel] [+ Button] ... │
│                        | Resolution: [320x180 ▼] | [Zoom: 100%] │
└─────────────────────────────────────────────────────────────────┘
```

- **Canvas selector**: Dropdown to switch between canvases
- **+ Canvas**: Create new UICanvas
- **+ Element buttons**: Quick-add Panel, Button, Image, Text
- **Resolution**: Preview resolution presets (phone, tablet, desktop)
- **Zoom**: Preview zoom level

---

## Runtime Instantiation

Converting EditorUICanvas to runtime GameObjects:

```java
public class UISceneBuilder {
    
    public static GameObject build(EditorUICanvas editorCanvas) {
        GameObject canvasGO = new GameObject(editorCanvas.getName());
        
        UICanvas canvas = new UICanvas(editorCanvas.getRenderMode());
        canvas.setSortOrder(editorCanvas.getSortOrder());
        canvasGO.addComponent(canvas);
        
        // Build element hierarchy
        Map<String, GameObject> elementMap = new HashMap<>();
        
        for (EditorUIElement element : editorCanvas.getElements()) {
            GameObject elementGO = buildElement(element);
            elementMap.put(element.getId(), elementGO);
        }
        
        // Link parent-child relationships
        for (EditorUIElement element : editorCanvas.getElements()) {
            GameObject elementGO = elementMap.get(element.getId());
            
            if (element.getParentId() == null) {
                canvasGO.addChild(elementGO);
            } else {
                GameObject parent = elementMap.get(element.getParentId());
                parent.addChild(elementGO);
            }
        }
        
        return canvasGO;
    }
    
    private static GameObject buildElement(EditorUIElement element) {
        GameObject go = new GameObject(element.getName());
        
        for (ComponentData compData : element.getComponents()) {
            Component comp = ComponentFactory.instantiate(compData);
            go.addComponent(comp);
        }
        
        return go;
    }
}
```

---

## Implementation Phases

### Phase 1: Data Model
1. Create `EditorUIElement` and `EditorUICanvas` classes
2. Add `List<EditorUICanvas>` to `EditorScene`
3. Implement serialization (UICanvasData, UIElementData)
4. Test save/load

### Phase 2: Hierarchy Panel
1. Create `UIEditorPanel` shell
2. Implement `UIHierarchySection` with tree view
3. Add create/delete/rename functionality
4. Wire to EditorScene

### Phase 3: Inspector
1. Create `UIInspectorSection`
2. Integrate `ReflectionFieldEditor` (from Entities feature)
3. Handle UITransform special case (anchor presets dropdown)
4. Add Component button (filtered to UI components)

### Phase 4: Canvas Preview
1. Create `UICanvasPreview` with framebuffer
2. Implement preview rendering (temporary component instantiation)
3. Add click-to-select functionality
4. Display selection highlight

### Phase 5: Visual Editing (Optional)
1. Drag-to-move elements in preview
2. Resize handles
3. Snap-to-grid
4. Alignment guides

### Phase 6: Integration
1. Wire UIEditorPanel to EditorUIController
2. Add to editor mode system (or always visible)
3. Hierarchy panel shows "UI" section
4. Runtime builder for Play Mode

---

## Open Questions

1. **Preview rendering**: Instantiate real components temporarily, or custom preview-only rendering? Temp instantiation reuses existing code but may have side effects.

2. **Font handling**: UIText needs a Font. How to select fonts in editor? Font registry/browser?

3. **Asset references**: UIImage.sprite, UIButton.image need asset picker. Share with Entity inspector.

4. **Anchor visualization**: Show anchor points and connections in preview? Adds complexity.

5. **Multi-resolution preview**: Support testing different resolutions? Important for responsive UI.

6. **World-space UI**: Your UICanvas supports WORLD_SPACE mode. How to preview/position world-space UI?

---

## Dependencies

- **Entities Support feature**: ReflectionFieldEditor, ComponentData
- **EditorFramebuffer** (existing): For canvas preview
- **UIRendererBackend** (existing): For preview rendering

---

## UI Component Filter

When adding components to UI elements, filter to UI-specific components:

```java
public static boolean isUIComponent(Class<? extends Component> clazz) {
    return UIComponent.class.isAssignableFrom(clazz) 
        || clazz == UITransform.class;
}
```
