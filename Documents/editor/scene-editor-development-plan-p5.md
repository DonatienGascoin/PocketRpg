## Phase 5: Entity Placement

**Goal:** Place prefab instances in scene.

### 5.1 Edit Modes

**Problem:** Tools behave differently depending on what's being edited. Need context-sensitive modes.

```java
public enum EditMode {
    TILEMAP,      // Tile painting tools active
    COLLISION,    // Collision painting active
    ENTITY        // Entity selection/placement active
}
```

**Mode behaviors:**
- **TILEMAP:** Tile overlay visible, tile tools active, brush/eraser/fill/rectangle/picker
- **COLLISION:** Collision overlay visible, collision brush active
- **ENTITY:** No tile overlay, standard cursor, selection tool active, entity placer available

**Mode switching:**
- Clicking on Tileset palette → TILEMAP mode
- Clicking on Collision panel → COLLISION mode
- Clicking on Prefabs panel or selecting entity → ENTITY mode
- Keyboard shortcuts: 1 = Tilemap, 2 = Collision, 3 = Entity

### 5.2 Prefab Browser Panel

```java
public class PrefabBrowserPanel {
    private final PrefabRegistry registry;
    private String selectedPrefabId = null;
    private String filterText = "";
    
    public void render() {
        ImGui.begin("Prefabs");
        
        // Search filter
        ImGui.inputText("Search", filterText);
        
        // Grid of prefab buttons with preview images
        for (String prefabId : registry.getRegisteredPrefabs()) {
            if (!filterText.isEmpty() && !prefabId.contains(filterText)) continue;
            
            Texture preview = prefabPreviews.get(prefabId);
            if (preview != null) {
                if (ImGui.imageButton(preview.getTextureId(), 64, 64)) {
                    selectedPrefabId = prefabId;
                    editor.setEditMode(EditMode.ENTITY);
                }
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(prefabId);
            }
        }
        
        ImGui.end();
    }
}
```

### 5.3 Entity Placer Tool

```java
public class EntityPlacerTool implements EditorTool {
    private PrefabBrowserPanel prefabPanel;
    private EditorScene scene;
    private boolean snapToGrid = true;
    
    public String getName() { return "Place Entity"; }
    
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            String prefabId = prefabPanel.getSelectedPrefabId();
            if (prefabId == null) return;
            
            Vector3f position;
            if (snapToGrid) {
                position = new Vector3f(tileX + 0.5f, tileY + 0.5f, 0);
            } else {
                position = getWorldMousePosition();
            }
            
            EditorEntity entity = new EditorEntity(prefabId, position);
            scene.addEntity(entity);
            scene.markDirty();
        }
    }
    
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        String prefabId = prefabPanel.getSelectedPrefabId();
        if (prefabId == null) return;
        
        // Draw semi-transparent preview sprite at cursor
        Sprite preview = getPrefabPreviewSprite(prefabId);
        if (preview != null) {
            Vector3f pos = snapToGrid ? 
                new Vector3f(hoveredTileX + 0.5f, hoveredTileY + 0.5f, 0) :
                getWorldMousePosition();
            overlayRenderer.renderSpritePreview(camera, preview, pos, 0.5f);
        }
    }
}
```

### 5.4 Selection Tool

```java
public class SelectionTool implements EditorTool {
    private EditorScene scene;
    private EditorEntity draggedEntity = null;
    private Vector2f dragOffset = new Vector2f();
    
    public String getName() { return "Select"; }
    public String getShortcutKey() { return "V"; }
    
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            Vector3f worldPos = getWorldMousePosition();
            EditorEntity entity = scene.findEntityAt(worldPos.x, worldPos.y);
            
            if (entity != null) {
                scene.setSelectedEntity(entity);
                draggedEntity = entity;
                dragOffset.set(
                    entity.getPosition().x - worldPos.x,
                    entity.getPosition().y - worldPos.y
                );
            } else {
                scene.setSelectedEntity(null);
            }
        }
    }
    
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (draggedEntity != null) {
            Vector3f worldPos = getWorldMousePosition();
            draggedEntity.setPosition(
                worldPos.x + dragOffset.x,
                worldPos.y + dragOffset.y
            );
        }
    }
    
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        EditorEntity selected = scene.getSelectedEntity();
        if (selected != null) {
            // Draw selection highlight box
            overlayRenderer.renderEntityBounds(camera, selected, new Vector4f(1, 1, 0, 0.8f));
        }
    }
}
```

### 5.5 Entity Inspector Panel

```java
public class EntityInspectorPanel {
    private EditorScene scene;
    
    public void render() {
        ImGui.begin("Inspector");
        
        EditorEntity entity = scene.getSelectedEntity();
        if (entity == null) {
            ImGui.textDisabled("No entity selected");
            ImGui.end();
            return;
        }
        
        // Name
        String name = entity.getName();
        if (ImGui.inputText("Name", name)) {
            entity.setName(name);
        }
        
        // Prefab ID (read-only)
        ImGui.text("Prefab: " + entity.getPrefabId());
        
        // Position
        float[] pos = {entity.getPosition().x, entity.getPosition().y};
        if (ImGui.dragFloat2("Position", pos, 0.1f)) {
            entity.setPosition(pos[0], pos[1]);
        }
        
        ImGui.sameLine();
        if (ImGui.button("Snap")) {
            entity.setPosition(Math.round(pos[0]), Math.round(pos[1]));
        }
        
        // Custom properties
        ImGui.separator();
        ImGui.text("Properties:");
        
        for (var entry : entity.getProperties().entrySet()) {
            renderPropertyEditor(entry.getKey(), entry.getValue());
        }
        
        ImGui.end();
    }
}
```

### 5.6 EditorEntity

```java
public class EditorEntity {
    private String prefabId;
    private String name;
    private Vector3f position;
    private Map<String, Object> properties = new HashMap<>();
    
    // Preview data for rendering in editor
    private Sprite previewSprite;
    private Vector2f previewSize;
    
    public EditorEntity(String prefabId, Vector3f position) {
        this.prefabId = prefabId;
        this.name = prefabId + "_" + UUID.randomUUID().toString().substring(0, 4);
        this.position = new Vector3f(position);
    }
    
    public EntityData toData() {
        return new EntityData(prefabId, name,
            new float[]{position.x, position.y, position.z},
            new HashMap<>(properties));
    }
    
    public static EditorEntity fromData(EntityData data) {
        EditorEntity entity = new EditorEntity(
            data.prefabId(),
            new Vector3f(data.position()[0], data.position()[1], data.position()[2])
        );
        entity.name = data.name();
        entity.properties = new HashMap<>(data.properties());
        return entity;
    }
}
```

### 5.6 Scene Camera Settings

The scene has a default camera configuration that defines the initial game view when the scene loads.

**SceneCamera stored in EditorScene:**
```java
public class SceneCameraSettings {
    private Vector2f position = new Vector2f(0, 0);
    private float orthographicSize = 10f;  // Half-height in world units
    private boolean followPlayer = true;
    private String followTargetName = "Player";  // Entity name to follow
    
    // Bounds for camera clamping (optional)
    private boolean useBounds = false;
    private Vector4f bounds = new Vector4f();  // minX, minY, maxX, maxY
}
```

**Camera Inspector Panel (part of main Inspector or separate):**
```java
public class CameraInspectorPanel {
    private EditorScene scene;
    
    public void render() {
        SceneCameraSettings cam = scene.getCameraSettings();
        
        ImGui.text("Scene Camera");
        ImGui.separator();
        
        // Initial position
        float[] pos = {cam.getPosition().x, cam.getPosition().y};
        if (ImGui.dragFloat2("Start Position", pos, 0.5f)) {
            cam.setPosition(pos[0], pos[1]);
            scene.markDirty();
        }
        
        // Orthographic size
        float[] size = {cam.getOrthographicSize()};
        if (ImGui.dragFloat("Ortho Size", size, 0.5f, 1f, 50f)) {
            cam.setOrthographicSize(size[0]);
            scene.markDirty();
        }
        
        // Follow target
        if (ImGui.checkbox("Follow Player", cam.isFollowPlayer())) {
            cam.setFollowPlayer(!cam.isFollowPlayer());
            scene.markDirty();
        }
        
        if (cam.isFollowPlayer()) {
            String[] target = {cam.getFollowTargetName()};
            if (ImGui.inputText("Target Entity", target)) {
                cam.setFollowTargetName(target[0]);
                scene.markDirty();
            }
        }
        
        // Camera bounds
        if (ImGui.checkbox("Use Bounds", cam.isUseBounds())) {
            cam.setUseBounds(!cam.isUseBounds());
            scene.markDirty();
        }
        
        if (cam.isUseBounds()) {
            float[] bounds = {cam.getBounds().x, cam.getBounds().y, 
                              cam.getBounds().z, cam.getBounds().w};
            if (ImGui.dragFloat4("Bounds (minX,minY,maxX,maxY)", bounds)) {
                cam.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
                scene.markDirty();
            }
            
            if (ImGui.button("Set from Current View")) {
                // Use editor camera bounds as scene bounds
            }
        }
        
        // Preview button
        if (ImGui.button("Preview Game View")) {
            editorCamera.setPosition(cam.getPosition());
            editorCamera.setZoom(calculateZoomFromOrthoSize(cam.getOrthographicSize()));
        }
    }
}
```

**Serialization** - already covered in SceneData.CameraData, just ensure EditorSceneSerializer maps it:
```java
// In EditorSceneSerializer.toSceneData()
SceneCameraSettings cam = editorScene.getCameraSettings();
data.setCamera(new SceneData.CameraData(
    cam.getPosition().x, cam.getPosition().y, 0,
    cam.getOrthographicSize()
));
// Add followPlayer, followTargetName, bounds to CameraData if not present
```

**Or** BETTER ! integrate into existing `EntityInspectorPanel` as a special entity at the top of the scene (shows scene-level settings including camera).

**Camera Bounds Visualization in Viewport:**

When `useBounds` is enabled, render a visual indicator in the scene viewport.
```java
// In SceneViewport or EditorSceneRenderer
public void renderCameraOverlays(EditorCamera camera, SceneCameraSettings sceneCam) {
    if (!sceneCam.isUseBounds()) return;
    
    ImDrawList drawList = ImGui.getBackgroundDrawList();
    Vector4f bounds = sceneCam.getBounds();
    
    // Convert world bounds to screen coordinates
    Vector2f minScreen = camera.worldToScreen(bounds.x, bounds.y);
    Vector2f maxScreen = camera.worldToScreen(bounds.z, bounds.w);
    
    // Dashed rectangle for camera bounds
    int boundsColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, 0.8f);  // Cyan
    drawDashedRect(drawList, minScreen.x, minScreen.y, maxScreen.x, maxScreen.y, boundsColor, 2f, 8f);
    
    // Corner handles for resizing (optional)
    float handleSize = 8f;
    int handleColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.9f);
    drawList.addRectFilled(minScreen.x - handleSize/2, minScreen.y - handleSize/2,
                           minScreen.x + handleSize/2, minScreen.y + handleSize/2, handleColor);
    drawList.addRectFilled(maxScreen.x - handleSize/2, maxScreen.y - handleSize/2,
                           maxScreen.x + handleSize/2, maxScreen.y + handleSize/2, handleColor);
    
    // Label
    drawList.addText(minScreen.x + 5, minScreen.y - 20, boundsColor, "Camera Bounds");
    
    // Game view rectangle (what the player sees at start position)
    if (showGameViewPreview) {
        float aspect = (float) viewportWidth / viewportHeight;
        float halfHeight = sceneCam.getOrthographicSize();
        float halfWidth = halfHeight * aspect;
        
        Vector2f camPos = sceneCam.getPosition();
        Vector2f viewMin = camera.worldToScreen(camPos.x - halfWidth, camPos.y - halfHeight);
        Vector2f viewMax = camera.worldToScreen(camPos.x + halfWidth, camPos.y + halfHeight);
        
        int viewColor = ImGui.colorConvertFloat4ToU32(1f, 0.5f, 0.2f, 0.6f);  // Orange
        drawList.addRect(viewMin.x, viewMin.y, viewMax.x, viewMax.y, viewColor, 0, 0, 2f);
        drawList.addText(viewMin.x + 5, viewMin.y - 20, viewColor, "Game View");
    }
}

private void drawDashedRect(ImDrawList drawList, float x1, float y1, float x2, float y2, 
                            int color, float thickness, float dashLength) {
    drawDashedLine(drawList, x1, y1, x2, y1, color, thickness, dashLength);  // Top
    drawDashedLine(drawList, x2, y1, x2, y2, color, thickness, dashLength);  // Right
    drawDashedLine(drawList, x2, y2, x1, y2, color, thickness, dashLength);  // Bottom
    drawDashedLine(drawList, x1, y2, x1, y1, color, thickness, dashLength);  // Left
}

private void drawDashedLine(ImDrawList drawList, float x1, float y1, float x2, float y2,
                            int color, float thickness, float dashLength) {
    float dx = x2 - x1, dy = y2 - y1;
    float length = (float) Math.sqrt(dx * dx + dy * dy);
    float nx = dx / length, ny = dy / length;
    
    float pos = 0;
    boolean draw = true;
    while (pos < length) {
        float segmentLength = Math.min(dashLength, length - pos);
        if (draw) {
            drawList.addLine(
                x1 + nx * pos, y1 + ny * pos,
                x1 + nx * (pos + segmentLength), y1 + ny * (pos + segmentLength),
                color, thickness
            );
        }
        pos += dashLength;
        draw = !draw;
    }
}
```

**Toggle in View menu:**
```java
// In EditorMenuBar
if (ImGui.beginMenu("View")) {
    if (ImGui.menuItem("Show Camera Bounds", "Alt+B", showCameraBounds)) {
        showCameraBounds = !showCameraBounds;
    }
    if (ImGui.menuItem("Show Game View Preview", "Alt+G", showGameViewPreview)) {
        showGameViewPreview = !showGameViewPreview;
    }
    // ...
}
```

Or, if using the hierarchy, by clicking on the camera

### 5.7 Files to Create

```
src/main/java/com/pocket/rpg/editor/
├── EditMode.java
├── tools/
│   ├── EntityPlacerTool.java
│   └── SelectionTool.java
├── panels/
│   ├── PrefabBrowserPanel.java
│   └── EntityInspectorPanel.java
└── scene/
    └── EditorEntity.java
│   └── SceneCameraSettings.java
```

### 5.8 Testing Checklist

- [ ] Browse available prefabs in panel
- [ ] Select prefab shows preview at cursor
- [ ] Click to place entity
- [ ] Snap to grid works
- [ ] Select entity by clicking
- [ ] Drag entity to move
- [ ] Delete key removes selected entity
- [ ] Inspector shows entity properties
- [ ] Edit entity name and position
- [ ] Entity data saves/loads from .scene file
- [ ] Switching edit modes changes available tools

---