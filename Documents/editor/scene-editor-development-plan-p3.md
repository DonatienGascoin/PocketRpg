## Phase 3: Tilemap Painting ✅ COMPLETED

**Status:** Implemented on 2025-12-10

**Goal:** Visual tile placement with brush tools.

### Implementation Notes

**Files Created:**
```
src/main/java/com/pocket/rpg/editor/
├── tools/
│   ├── EditorTool.java         # Interface
│   ├── ToolManager.java        # Tool switching, input routing
│   ├── TileBrushTool.java      # Paint tiles (single + pattern)
│   ├── TileEraserTool.java     # Erase tiles
│   ├── TileFillTool.java       # Flood fill (2000 tile limit)
│   ├── TileRectangleTool.java  # Rectangle fill
│   └── TilePickerTool.java     # Eyedropper (single + pattern)
├── panels/
│   ├── TilesetPalettePanel.java  # Tile selection grid
│   └── LayerPanel.java           # Layer management
├── tileset/
│   ├── TileSelection.java        # Single or pattern selection
│   ├── TilesetRegistry.java      # Loaded tileset management
│   └── CreateSpritesheetDialog.java  # Create .spritesheet files
└── scene/
    ├── TilemapLayer.java         # Layer wrapper
    └── LayerVisibilityMode.java  # ALL/SELECTED_ONLY/SELECTED_DIMMED
```

**What Works:**
- ✅ Paint tiles with brush (single tile or pattern stamp)
- ✅ Variable brush size (1-10)
- ✅ Erase tiles
- ✅ Fill tool (flood fill with 2000 tile safety limit)
- ✅ Rectangle tool (drag to define, fill on release)
- ✅ Picker tool (click for single, shift+drag for pattern)
- ✅ Multiple tilemap layers as separate GameObjects
- ✅ Layer visibility toggle
- ✅ Layer lock
- ✅ Layer reordering (move up/down)
- ✅ Grid visualization
- ✅ Cursor/brush preview overlay
- ✅ Spritesheet creation dialog
- ✅ Visual tile selection in palette (single + rectangle)

**Tool Shortcuts:**
- B - Brush
- E - Eraser
- F - Fill
- R - Rectangle
- I - Picker (eyedropper)
- -/+ - Adjust brush size

**Known Issues (to fix in Phase 3.5):**
- Tool shortcuts only work when viewport is focused
- Fill tool can fill unbounded areas (needs boundary check)
- Picker tool needs visual feedback when Shift is held
- Layer creation requires naming popup (should be instant)
- SELECTED_DIMMED mode doesn't actually dim (needs vertex colors)

---

## Phase 3.5: Consolidation

**Goal:** Fix outstanding issues from Phases 1-3, complete the save/load workflow, and improve editor usability before adding new features.

### 3.5.1 EditorScene Serialization

**Problem:** `SceneLoader` works with `Scene` (game runtime), but the editor uses `EditorScene`. Need bidirectional conversion.

**Solution:** Create `EditorSceneSerializer` that converts between `EditorScene` and `SceneData`.

```java
/**
 * Handles conversion between EditorScene (editor runtime) and SceneData (serialization).
 */
public class EditorSceneSerializer {
    
    /**
     * Converts EditorScene to SceneData for saving.
     */
    public static SceneData toSceneData(EditorScene editorScene) {
        SceneData data = new SceneData(editorScene.getName());
        
        // Convert camera
        data.setCamera(new SceneData.CameraData(0, 0, 0, 15f));
        
        // Convert tilemap layers to GameObjectData
        for (TilemapLayer layer : editorScene.getLayers()) {
            GameObjectData goData = convertTilemapLayer(layer);
            data.addGameObject(goData);
        }
        
        // Convert collision map
        if (editorScene.getCollisionMap() != null) {
            data.setCollision(convertCollisionMap(editorScene.getCollisionMap()));
        }
        
        // Convert entities (Phase 5)
        // Convert triggers (Phase 7)
        
        return data;
    }
    
    /**
     * Converts SceneData to EditorScene for editing.
     */
    public static EditorScene fromSceneData(SceneData data, String filePath) {
        EditorScene scene = new EditorScene(data.getName());
        scene.setFilePath(filePath);
        
        // Convert GameObjects with TilemapRenderer to TilemapLayers
        for (GameObjectData goData : data.getGameObjects()) {
            if (hasTilemapRenderer(goData)) {
                TilemapLayer layer = convertToTilemapLayer(goData);
                scene.addExistingLayer(layer);
            }
        }
        
        // Load collision map
        if (data.getCollision() != null) {
            scene.setCollisionMap(convertToCollisionMap(data.getCollision()));
        }
        
        scene.clearDirty();
        return scene;
    }
    
    private static GameObjectData convertTilemapLayer(TilemapLayer layer) {
        GameObjectData goData = new GameObjectData();
        goData.setName(layer.getName());
        goData.setPosition(new float[]{0, 0, 0});
        
        TilemapRenderer tilemap = layer.getTilemap();
        TilemapComponentData tilemapData = new TilemapComponentData();
        tilemapData.setZIndex(tilemap.getZIndex());
        tilemapData.setTileSize(tilemap.getTileSize());
        
        // Serialize chunks with tileset references
        Map<String, ChunkData> chunks = new HashMap<>();
        for (var entry : tilemap.allChunks().entrySet()) {
            long key = entry.getKey();
            int cx = (int)(key >> 32);
            int cy = (int)(key & 0xFFFFFFFFL);
            
            TilemapRenderer.TileChunk chunk = entry.getValue();
            ChunkData chunkData = convertChunk(chunk);
            chunks.put(cx + "," + cy, chunkData);
        }
        tilemapData.setChunks(chunks);
        
        goData.getComponents().put("TilemapRenderer", tilemapData);
        return goData;
    }
}
```

**Files to Create:**
```
src/main/java/com/pocket/rpg/editor/serialization/
├── EditorSceneSerializer.java    # EditorScene ↔ SceneData conversion
├── TilemapComponentData.java     # Tilemap serialization format
└── ChunkData.java                # Chunk serialization format
```

### 3.5.2 Complete Save/Load Workflow

**Update EditorApplication methods:**

```java
private void saveScene() {
    if (currentScene == null) return;
    
    if (currentScene.getFilePath() == null) {
        saveSceneAs();
        return;
    }
    
    try {
        SceneData data = EditorSceneSerializer.toSceneData(currentScene);
        String json = Serializer.toJson(data, true);
        Files.writeString(Path.of(currentScene.getFilePath()), json);
        
        currentScene.clearDirty();
        statusBar.showMessage("Saved: " + currentScene.getName());
    } catch (IOException e) {
        statusBar.showMessage("Error saving: " + e.getMessage());
        e.printStackTrace();
    }
}

private void saveSceneAs() {
    String path = FileDialogs.saveFile("scene", "Scene Files", "*.scene");
    if (path == null) return;
    
    if (!path.endsWith(".scene")) {
        path += ".scene";
    }
    
    currentScene.setFilePath(path);
    String fileName = Path.of(path).getFileName().toString();
    currentScene.setName(fileName.replace(".scene", ""));
    
    saveScene();
}

private void openScene() {
    if (currentScene != null && currentScene.hasUnsavedChanges()) {
        // Show unsaved changes dialog
    }
    
    String path = FileDialogs.openFile("scene", "Scene Files", "*.scene");
    if (path == null) return;
    
    try {
        String json = Files.readString(Path.of(path));
        SceneData data = Serializer.fromJson(json, SceneData.class);
        
        if (currentScene != null) {
            currentScene.destroy();
        }
        
        currentScene = EditorSceneSerializer.fromSceneData(data, path);
        updateSceneReferences();
        
        camera.reset();
        statusBar.showMessage("Opened: " + currentScene.getName());
    } catch (Exception e) {
        statusBar.showMessage("Error loading: " + e.getMessage());
        e.printStackTrace();
    }
}
```

### 3.5.3 Layout Persistence

**Problem:** Editor layout resets every startup because `io.setIniFilename(null)` disables ImGui's layout saving.

**Solution:** Use ImGui's built-in INI persistence with a custom path, plus a default layout builder for first run.

```java
// In ImGuiLayer.init()
public void init(long windowHandle, boolean installCallbacks) {
    ImGui.createContext();
    ImGuiIO io = ImGui.getIO();
    
    io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
    io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
    
    // Enable layout persistence
    io.setIniFilename("editor_layout.ini");
    
    // ... rest of init
}

// In EditorApplication, build default layout on first run
private boolean firstFrame = true;

private void setupDocking() {
    // ... existing docking setup ...
    
    if (firstFrame && !Files.exists(Path.of("editor_layout.ini"))) {
        buildDefaultLayout();
    }
    firstFrame = false;
}

private void buildDefaultLayout() {
    int dockspaceId = ImGui.getID("EditorDockSpace");
    
    ImGui.dockBuilderRemoveNode(dockspaceId);
    ImGui.dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.DockSpace);
    ImGui.dockBuilderSetNodeSize(dockspaceId, window.getWidth(), window.getHeight());
    
    // Split: left panel (20%), center (60%), right panel (20%)
    int leftId = ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.20f, null, dockspaceId);
    int rightId = ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Right, 0.25f, null, dockspaceId);
    int rightTopId = ImGui.dockBuilderSplitNode(rightId, ImGuiDir.Up, 0.5f, null, rightId);
    
    // Dock windows
    ImGui.dockBuilderDockWindow("Hierarchy", leftId);
    ImGui.dockBuilderDockWindow("Tileset", leftId);
    ImGui.dockBuilderDockWindow("Scene", dockspaceId);
    ImGui.dockBuilderDockWindow("Inspector", rightTopId);
    ImGui.dockBuilderDockWindow("Layers", rightId);
    ImGui.dockBuilderDockWindow("Tools", rightId);
    
    ImGui.dockBuilderFinish(dockspaceId);
}
```

**Menu option to reset layout:**
```java
if (ImGui.menuItem("Reset Layout")) {
    Files.deleteIfExists(Path.of("editor_layout.ini"));
}
```

### 3.5.4 Dimmed Layer Opacity (Vertex Colors)

**Problem:** SELECTED_DIMMED visibility mode doesn't render dimmed because BatchRenderer doesn't support per-sprite opacity.

**Solution:** Add RGBA vertex colors to the batch rendering system.

#### SpriteBatch Changes

```java
// Updated vertex format: [posX, posY, u, v, r, g, b, a] = 8 floats per vertex
private static final int FLOATS_PER_VERTEX = 8;  // Was 4
private static final int FLOATS_PER_SPRITE = 48; // Was 24

public void submit(SpriteRenderer spriteRenderer, float opacity) {
    buildVertexData(sprite, transform, renderer, opacity);
}

public void submitChunk(TilemapRenderer tilemap, int cx, int cy, float opacity) {
    // Pass opacity to vertex building
}

private void buildVertexData(..., float opacity) {
    // ... existing position/UV calculations ...
    
    // Add color data for each vertex
    float r = 1.0f, g = 1.0f, b = 1.0f, a = opacity;
    
    vertexData[offset++] = r;
    vertexData[offset++] = g;
    vertexData[offset++] = b;
    vertexData[offset++] = a;
}

// Update VAO setup
private void setupVAO() {
    // Position (location 0)
    glVertexAttribPointer(0, 2, GL_FLOAT, false, 8 * Float.BYTES, 0);
    glEnableVertexAttribArray(0);
    
    // UV (location 1)
    glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 2 * Float.BYTES);
    glEnableVertexAttribArray(1);
    
    // Color (location 2) - NEW
    glVertexAttribPointer(2, 4, GL_FLOAT, false, 8 * Float.BYTES, 4 * Float.BYTES);
    glEnableVertexAttribArray(2);
}
```

#### Shader Changes (batch_sprite.glsl)

```glsl
// Vertex shader
layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec4 aColor;  // NEW

out vec2 TexCoord;
out vec4 Color;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 0.0, 1.0);
    TexCoord = aTexCoord;
    Color = aColor;
}

// Fragment shader
in vec2 TexCoord;
in vec4 Color;

out vec4 FragColor;

uniform sampler2D textureSampler;

void main() {
    vec4 texColor = texture(textureSampler, TexCoord);
    FragColor = texColor * Color;  // Multiply by vertex color
}
```

#### Future: Tint Color Support

With RGBA vertex colors, adding tint support is trivial:

```java
// In SpriteRenderer
@Getter @Setter
private Vector4f tintColor = new Vector4f(1, 1, 1, 1);

// In SpriteBatch.buildVertexData()
float r = renderer.getTintColor().x;
float g = renderer.getTintColor().y;
float b = renderer.getTintColor().z;
float a = renderer.getTintColor().w * opacity;
```

### 3.5.5 Unlimited Batch Size

**Problem:** SpriteBatch has fixed buffer size (MAX_SPRITES = 10000). Large scenes can overflow.

**Solution:** Deferred submission with auto-flush when buffer is full, maintaining correct z-order.

```java
private static class SpriteSubmission {
    final Sprite sprite;
    final Transform transform;
    final SpriteRenderer renderer;
    final float opacity;
    final int zIndex;
}

private static class ChunkSubmission {
    final TilemapRenderer tilemap;
    final int cx, cy;
    final float opacity;
    final int zIndex;
}

private List<SpriteSubmission> spriteSubmissions = new ArrayList<>();
private List<ChunkSubmission> chunkSubmissions = new ArrayList<>();

public void submit(SpriteRenderer spriteRenderer, float opacity) {
    spriteSubmissions.add(new SpriteSubmission(...));
}

public void end() {
    processBatches();
    spriteSubmissions.clear();
    chunkSubmissions.clear();
}

private void processBatches() {
    // Combine all submissions
    List<Object> allSubmissions = new ArrayList<>();
    allSubmissions.addAll(spriteSubmissions);
    allSubmissions.addAll(chunkSubmissions);
    
    // Sort globally by zIndex
    allSubmissions.sort((a, b) -> Integer.compare(getZIndex(a), getZIndex(b)));
    
    // Process in sorted order, auto-flush when buffer full
    for (Object sub : allSubmissions) {
        if (sub instanceof SpriteSubmission s) {
            if (spriteCount >= maxSprites) flush();
            buildVertexData(s);
            spriteCount++;
        } else if (sub instanceof ChunkSubmission c) {
            addChunkToBatch(c);  // Handles per-tile flush
        }
    }
    
    flush();
}
```

### 3.5.6 Tool Improvements

#### Global Tool Shortcuts

```java
private void processToolShortcuts() {
    // Don't process when typing in text fields
    if (ImGui.getIO().getWantTextInput()) {
        return;
    }
    
    // Shortcuts work globally now
    if (ImGui.isKeyPressed(ImGuiKey.B)) {
        toolManager.setActiveTool("Brush");
    }
    // ...
}
```

#### Fill Tool Boundary Check

```java
public class TileFillTool implements EditorTool {
    
    private static final int MAX_FILL_TILES = 2000;
    
    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) {
            BoundaryCheckResult result = checkBoundaries(tileX, tileY);
            
            if (result == BoundaryCheckResult.UNBOUNDED) {
                statusBar.showMessage("Cannot fill: area has no boundaries");
                return;
            }
            
            if (result == BoundaryCheckResult.TOO_LARGE) {
                statusBar.showMessage("Cannot fill: area too large");
                return;
            }
            
            floodFill(tileX, tileY);
        }
    }
    
    private enum BoundaryCheckResult {
        BOUNDED, UNBOUNDED, TOO_LARGE
    }
    
    private BoundaryCheckResult checkBoundaries(int startX, int startY) {
        // Quick BFS to check if area is bounded
        // Returns early if limit exceeded
    }
}
```

#### Picker Tool Shift Visual Feedback

```java
@Override
public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
    boolean shiftHeld = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);
    
    int color;
    if (shiftHeld) {
        color = ImGui.colorConvertFloat4ToU32(0.3f, 1.0f, 0.3f, 0.6f);  // Green
        drawList.addText(viewportX + 10, viewportY + viewportHeight - 30,
            ImGui.colorConvertFloat4ToU32(0.3f, 1.0f, 0.3f, 1.0f),
            "Shift+Drag to pick pattern");
    } else {
        color = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.3f, 0.6f);  // Orange
    }
    
    drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY, color, true);
}
```

#### Layer Quick Create

```java
// In LayerPanel
if (ImGui.button("+ Add Layer")) {
    String autoName = "Layer " + (scene.getLayerCount() + 1);
    scene.addLayer(autoName);
    statusBar.showMessage("Created: " + autoName);
    // No popup
}
```

### 3.5.7 Files to Create/Modify

**New Files:**
```
src/main/java/com/pocket/rpg/editor/serialization/
├── EditorSceneSerializer.java
├── TilemapComponentData.java
└── ChunkData.java
```

**Modified Files:**
```
src/main/java/com/pocket/rpg/
├── editor/
│   ├── EditorApplication.java       # Save/load workflow, layout
│   ├── core/ImGuiLayer.java         # Layout persistence
│   ├── tools/TileFillTool.java      # Boundary check
│   ├── tools/TilePickerTool.java    # Shift visual
│   └── panels/LayerPanel.java       # Quick create
└── rendering/
    ├── SpriteBatch.java             # Vertex colors, unlimited size
    └── renderers/BatchRenderer.java # Opacity parameter
```

### 3.5.8 Testing Checklist

- [ ] Save scene to .scene file
- [ ] Load scene from .scene file
- [ ] Round-trip: create → save → close → load → verify identical
- [ ] Layout persists across editor restarts
- [ ] "Reset Layout" menu option works
- [ ] SELECTED_DIMMED mode actually dims non-active layers
- [ ] Large tilemap (>10000 tiles) renders without errors
- [ ] Z-index ordering correct across multiple batches
- [ ] Tool shortcuts work globally (except in text fields)
- [ ] Fill tool rejects unbounded areas
- [ ] Picker shows green overlay when Shift held
- [ ] Layer creation is instant (no popup)

---