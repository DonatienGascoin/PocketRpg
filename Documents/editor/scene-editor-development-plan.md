# Scene Editor - Development Phases

## Overview

Standalone editor application sharing engine code with the game. Outputs `.scene` JSON files that the game loads at runtime.

**Core Features:**
- Tilemap painting (multi-layer)
- Collision layer editing
- Entity/prefab placement
- Scene transitions setup

**Architecture:**
```
EditorApplication (separate main)
    ├── EditorWindow (GLFW + ImGui context)
    ├── EditorScene (renders world, no game logic)
    ├── ToolSystem (brush, eraser, selection, entity placer)
    └── Panels (scene hierarchy, inspector, tileset palette, asset browser)

Output: .scene JSON files
Game: SceneLoader reads .scene files → builds Scene with GameObjects
```

**Important Note:** Tilemaps are currently implemented as `TilemapRenderer` components attached to GameObjects, not standalone objects. The editor and serialization must respect this architecture - each tilemap layer is a GameObject with a TilemapRenderer component.

---

## Terminology

To ensure clarity throughout the codebase and documentation:

| Term | Definition |
|------|------------|
| **Sprite** | A single image region from a texture, with UV coordinates and optional pivot |
| **SpriteSheet** | A texture divided into a grid of sprites (rows, cols, spacing, offset) |
| **Tileset** | A SpriteSheet used specifically for tilemap painting (same class, different context) |
| **Tile** | A single cell in a tilemap, references a Sprite |
| **Tilemap** | A grid data structure holding Tiles, managed by TilemapRenderer |
| **TilemapLayer** | Editor wrapper around a GameObject with TilemapRenderer component |
| **Layer** | In editor context, synonymous with TilemapLayer |
| **CollisionMap** | Separate grid storing collision types (not visual, not part of TilemapRenderer) |

---

## Phase 1: Foundation ✅ COMPLETED

**Status:** Implemented on 2025-12-09

### Implementation Notes

**Configuration Decisions Made:**
- Window mode: Maximized windowed (with decorations/title bar), not true fullscreen
- Uses `imgui-java-binding` + `imgui-java-lwjgl3` (1.90.0) for full ImGuiKey support
- Manual ImGui integration with existing GLFW (for full control)
- ImGuiKey for shortcuts (1.90.0 has full keyboard enum)

**Files Created:**
```
src/main/java/com/pocket/rpg/editor/
├── EditorApplication.java      # Main entry point with docking layout
├── core/
│   ├── EditorConfig.java       # @Builder configuration
│   ├── EditorWindow.java       # GLFW window + input state
│   ├── ImGuiLayer.java         # ImGui initialization and rendering
│   └── FileDialogs.java        # NFD native file dialogs
├── camera/
│   └── EditorCamera.java       # Free camera (pan/zoom)
├── scene/
│   └── EditorScene.java        # Scene data model (placeholder)
└── ui/
    ├── EditorMenuBar.java      # File/Edit/View/Help menus + shortcuts
    ├── SceneViewport.java      # Grid overlay, coordinate display
    └── StatusBar.java          # Tool/message/zoom/FPS display
```

**Controls:**
- Pan: WASD/Arrow keys (speed adjusted by zoom)
- Pan (drag): Middle mouse button
- Zoom: Scroll wheel (toward mouse position)
- Reset: Home key
- Shortcuts: Ctrl+N (New), Ctrl+O (Open), Ctrl+S (Save), Ctrl+Shift+S (Save As)

**What Works:**
- ✅ Maximized windowed mode with decorations (like IntelliJ)
- ✅ ImGui docking layout with central viewport
- ✅ Free camera with smooth pan/zoom
- ✅ Native file dialogs (NFD)
- ✅ Grid overlay in viewport
- ✅ Status bar with FPS
- ✅ Unsaved changes dialog

---

## Phase 2: Scene Serialization ✅ COMPLETED

**Status:** Implemented on 2025-12-09

**Goal:** Round-trip scene data: Editor saves → Game loads.

### Implementation Notes

**Architecture Decisions:**
- Generic component serialization via Gson TypeAdapters (not per-component)
- `ComponentSerializer`/`ComponentDeserializer` wraps components with type info
- Asset references (Sprite, Texture) serialize to file paths, resolve via AssetManager
- Hierarchy preserved by nesting children in `GameObjectData.children`
- Code-based prefabs via `PrefabRegistry` (JSON prefabs deferred to Phase 7+)

**Files Created:**
```
src/main/java/com/pocket/rpg/
├── serialization/
│   ├── ComponentSerializer.java      # Gson serializer for Component polymorphism
│   ├── ComponentDeserializer.java    # Gson deserializer
│   ├── SpriteTypeAdapter.java        # Sprite ↔ JSON with asset paths
│   ├── TextureTypeAdapter.java       # Texture ↔ path string  
│   ├── Vector2fTypeAdapter.java      # JOML Vector2f ↔ [x, y]
│   ├── Vector3fTypeAdapter.java      # JOML Vector3f ↔ [x, y, z]
│   ├── Vector4fTypeAdapter.java      # JOML Vector4f ↔ [x, y, z, w]
│   ├── SceneData.java                # Root scene structure
│   ├── GameObjectData.java           # GameObject with components + children
│   ├── SceneSerializer.java          # Save/load entry point
│   └── SceneLoader.java              # Game-side instantiation
├── prefabs/
│   ├── PrefabRegistry.java           # Code-based prefab factories
│   └── GamePrefabs.java              # Example prefab definitions
└── editor/rendering/
    ├── EditorFramebuffer.java        # OpenGL FBO for scene rendering
    └── EditorSceneRenderer.java      # Renders SceneData to framebuffer
```

**What Works:**
- ✅ Any Component subclass serializes automatically
- ✅ Sprite/Texture references resolve via AssetManager
- ✅ Parent-child hierarchy preserved
- ✅ Scene save/load round-trip
- ✅ Framebuffer rendering infrastructure
- ✅ PrefabRegistry for code-based prefabs

**Deferred to Later Phases:**
- EditorScene ↔ SceneData conversion (Phase 3.5)
- Complete save/load workflow in editor (Phase 3.5)
- CollisionMap layer (Phase 4)
- Inspector panel for editing (Phase 5)
- JSON-based prefabs (Phase 7+)

### 2.1 Scene File Format

```json
{
  "name": "Village",
  "version": 1,
  "camera": {
    "position": [0, 0],
    "orthographicSize": 15
  },
  "gameObjects": [
    {
      "name": "GroundLayer",
      "position": [0, 0, 0],
      "components": {
        "TilemapRenderer": {
          "zIndex": -1,
          "tileSize": 1.0,
          "tilesetPath": "gameData/assets/sprites/tileset.png",
          "tilesetCols": 16,
          "tilesetRows": 16,
          "chunks": {
            "0,0": {
              "tiles": [
                [0, 1, 2, -1, 3],
                [4, 5, 6, 7, 8]
              ]
            }
          }
        }
      }
    }
  ],
  "entities": [
    {
      "prefabId": "Player",
      "name": "Player",
      "position": [5, 5, 0],
      "properties": {}
    }
  ],
  "collision": {
    "tileSize": 1.0,
    "layers": {
      "0": {
        "chunks": {
          "0,0": {
            "data": [[0, 0, 1, 1], [0, 0, 1, 1]]
          }
        }
      }
    }
  },
  "triggers": [
    {
      "name": "ToForest",
      "type": "SCENE_TRANSITION",
      "bounds": [15, 10, 2, 1],
      "properties": {
        "targetScene": "Forest",
        "spawnPoint": "FromVillage"
      }
    }
  ]
}
```

---

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

## Phase 4: Collision Editing

**Goal:** Paint collision data with a flexible, extensible system that supports multiple collision types, Z-levels, and custom behaviors.

### 4.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         SERIALIZED                              │
│  (stored in .scene file)                                        │
├─────────────────────────────────────────────────────────────────┤
│  CollisionMap                                                   │
│  ├── tileSize: 1.0                                              │
│  └── zLayers                                                    │
│      ├── 0 (ground): CollisionLayer → chunks of int IDs        │
│      └── 1 (elevated): CollisionLayer → chunks of int IDs      │
│                                                                 │
│  (Just integers - CollisionType IDs. No behavior code stored)   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        NOT SERIALIZED                           │
│  (created at game startup, lives in code)                       │
├─────────────────────────────────────────────────────────────────┤
│  CollisionBehaviorRegistry (singleton)                          │
│  ├── PASSABLE → PassableBehavior                                │
│  ├── SOLID    → SolidBehavior                                   │
│  ├── WATER    → WaterBehavior                                   │
│  └── ...                                                        │
│                                                                 │
│  EntityOccupancyMap (rebuilt at scene load)                     │
│  ├── (5, 3, 0) → NPC_01                                         │
│  └── (2, 7, 0) → Player                                         │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 CollisionType Enum

```java
public enum CollisionType {
    PASSABLE(0, "Passable", new Color(0, 0, 0, 0)),
    SOLID(1, "Solid", new Color(1, 0, 0, 0.5f)),
    WATER(2, "Water", new Color(0, 0, 1, 0.5f)),
    ICE(3, "Ice", new Color(0, 1, 1, 0.5f)),
    LEDGE_DOWN(4, "Ledge ↓", new Color(1, 1, 0, 0.5f)),
    LEDGE_UP(5, "Ledge ↑", new Color(1, 1, 0, 0.5f)),
    LEDGE_LEFT(6, "Ledge ←", new Color(1, 1, 0, 0.5f)),
    LEDGE_RIGHT(7, "Ledge →", new Color(1, 1, 0, 0.5f)),
    STAIRS_UP(8, "Stairs ↑Z", new Color(0, 1, 0, 0.5f)),
    STAIRS_DOWN(9, "Stairs ↓Z", new Color(0, 0.5f, 0, 0.5f)),
    TRIGGER(10, "Trigger", new Color(1, 0, 1, 0.3f));
    
    private final int id;
    private final String displayName;
    private final Color editorColor;
    
    public record Color(float r, float g, float b, float a) {}
    
    public static CollisionType fromId(int id) {
        for (CollisionType type : values()) {
            if (type.id == id) return type;
        }
        return PASSABLE;
    }
}
```

### 4.3 Collision Data Structures

```java
public class CollisionMap {
    private final float tileSize;
    private final Map<Integer, CollisionLayer> zLayers = new HashMap<>();
    
    public CollisionMap(float tileSize) {
        this.tileSize = tileSize;
        zLayers.put(0, new CollisionLayer());  // Default ground layer
    }
    
    public CollisionType get(int x, int y, int zLevel) {
        CollisionLayer layer = zLayers.get(zLevel);
        return layer != null ? layer.get(x, y) : CollisionType.PASSABLE;
    }
    
    public void set(int x, int y, int zLevel, CollisionType type) {
        zLayers.computeIfAbsent(zLevel, k -> new CollisionLayer()).set(x, y, type);
    }
}

public class CollisionLayer {
    public static final int CHUNK_SIZE = 32;
    private final Map<Long, CollisionChunk> chunks = new HashMap<>();
    
    public CollisionType get(int x, int y) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        CollisionChunk chunk = chunks.get(packCoord(cx, cy));
        if (chunk == null) return CollisionType.PASSABLE;
        return chunk.get(Math.floorMod(x, CHUNK_SIZE), Math.floorMod(y, CHUNK_SIZE));
    }
    
    public void set(int x, int y, CollisionType type) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        chunks.computeIfAbsent(packCoord(cx, cy), k -> new CollisionChunk())
              .set(Math.floorMod(x, CHUNK_SIZE), Math.floorMod(y, CHUNK_SIZE), type);
    }
}

public class CollisionChunk {
    private final byte[][] data = new byte[CHUNK_SIZE][CHUNK_SIZE];
    
    public CollisionType get(int lx, int ly) {
        return CollisionType.fromId(data[ly][lx] & 0xFF);
    }
    
    public void set(int lx, int ly, CollisionType type) {
        data[ly][lx] = (byte) type.getId();
    }
}
```

### 4.4 TileBehavior System

```java
public interface TileBehavior {
    MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction moveDir);
    default void onEnter(GameObject entity, int x, int y) {}
    default void onExit(GameObject entity, int x, int y) {}
    default void onStay(GameObject entity, int x, int y, float deltaTime) {}
}

public record MoveResult(boolean allowed, MovementModifier modifier, String blockedReason) {
    public static final MoveResult ALLOWED = new MoveResult(true, MovementModifier.NONE, null);
    public static final MoveResult BLOCKED = new MoveResult(false, MovementModifier.NONE, "Blocked");
    
    public static MoveResult blocked(String reason) {
        return new MoveResult(false, MovementModifier.NONE, reason);
    }
    
    public static MoveResult allowedWith(MovementModifier modifier) {
        return new MoveResult(true, modifier, null);
    }
}

public enum MovementModifier {
    NONE, SLIDE, JUMP, SLOW, FAST
}

public enum Direction {
    UP(0, 1), DOWN(0, -1), LEFT(-1, 0), RIGHT(1, 0);
    
    public final int dx, dy;
    Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
    
    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN; case DOWN -> UP;
            case LEFT -> RIGHT; case RIGHT -> LEFT;
        };
    }
}
```

### 4.5 Behavior Implementations

```java
public class PassableBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.ALLOWED;
    }
}

public class SolidBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.blocked("Solid obstacle");
    }
}

public class WaterBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        SwimmingAbility swimming = entity.getComponent(SwimmingAbility.class);
        if (swimming != null && swimming.canSwim()) {
            return MoveResult.ALLOWED;
        }
        return MoveResult.blocked("Cannot swim");
    }
    
    public void onEnter(GameObject entity, int x, int y) {
        Animator animator = entity.getComponent(Animator.class);
        if (animator != null) animator.play("swim_start");
    }
    
    public void onExit(GameObject entity, int x, int y) {
        Animator animator = entity.getComponent(Animator.class);
        if (animator != null) animator.play("swim_end");
    }
}

public class IceBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.allowedWith(MovementModifier.SLIDE);
    }
}

public class LedgeBehavior implements TileBehavior {
    private final Direction allowedDirection;
    
    public LedgeBehavior(Direction allowedDirection) {
        this.allowedDirection = allowedDirection;
    }
    
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction moveDir) {
        if (moveDir == allowedDirection) {
            return MoveResult.allowedWith(MovementModifier.JUMP);
        }
        return MoveResult.blocked("Wrong direction for ledge");
    }
}

public class ZLevelChangeBehavior implements TileBehavior {
    private final int targetZLevel;
    
    public ZLevelChangeBehavior(int targetZLevel) {
        this.targetZLevel = targetZLevel;
    }
    
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.ALLOWED;
    }
    
    public void onEnter(GameObject entity, int x, int y) {
        ZLevelComponent zLevel = entity.getComponent(ZLevelComponent.class);
        if (zLevel != null) zLevel.setZLevel(targetZLevel);
    }
}
```

### 4.6 CollisionBehaviorRegistry

```java
public class CollisionBehaviorRegistry {
    private static CollisionBehaviorRegistry instance;
    private final Map<CollisionType, TileBehavior> behaviors = new EnumMap<>(CollisionType.class);
    
    private CollisionBehaviorRegistry() {
        registerDefaults();
    }
    
    public static CollisionBehaviorRegistry getInstance() {
        if (instance == null) instance = new CollisionBehaviorRegistry();
        return instance;
    }
    
    private void registerDefaults() {
        register(CollisionType.PASSABLE, new PassableBehavior());
        register(CollisionType.SOLID, new SolidBehavior());
        register(CollisionType.WATER, new WaterBehavior());
        register(CollisionType.ICE, new IceBehavior());
        register(CollisionType.LEDGE_DOWN, new LedgeBehavior(Direction.DOWN));
        register(CollisionType.LEDGE_UP, new LedgeBehavior(Direction.UP));
        register(CollisionType.LEDGE_LEFT, new LedgeBehavior(Direction.LEFT));
        register(CollisionType.LEDGE_RIGHT, new LedgeBehavior(Direction.RIGHT));
        register(CollisionType.STAIRS_UP, new ZLevelChangeBehavior(1));
        register(CollisionType.STAIRS_DOWN, new ZLevelChangeBehavior(0));
        register(CollisionType.TRIGGER, new TriggerBehavior());
    }
    
    public void register(CollisionType type, TileBehavior behavior) {
        behaviors.put(type, behavior);
    }
    
    public TileBehavior getBehavior(CollisionType type) {
        return behaviors.getOrDefault(type, new PassableBehavior());
    }
}
```

### 4.7 GridCollisionSystem

```java
public interface CollisionSystem {
    MoveResult canMove(GameObject entity, int fromX, int fromY, int toX, int toY, Direction dir);
    void onEntityMoved(GameObject entity, int oldX, int oldY, int newX, int newY);
}

public class GridCollisionSystem implements CollisionSystem {
    private final CollisionMap collisionMap;
    private final CollisionBehaviorRegistry behaviorRegistry;
    private final EntityOccupancyMap entityOccupancy;
    
    public GridCollisionSystem(CollisionMap collisionMap) {
        this.collisionMap = collisionMap;
        this.behaviorRegistry = CollisionBehaviorRegistry.getInstance();
        this.entityOccupancy = new EntityOccupancyMap();
    }
    
    public MoveResult canMove(GameObject entity, int fromX, int fromY, int toX, int toY, Direction dir) {
        int zLevel = getEntityZLevel(entity);
        
        // Check tile collision
        CollisionType tileType = collisionMap.get(toX, toY, zLevel);
        TileBehavior behavior = behaviorRegistry.getBehavior(tileType);
        MoveResult tileResult = behavior.evaluateEntry(entity, toX, toY, dir);
        
        if (!tileResult.allowed()) return tileResult;
        
        // Check entity occupancy
        GameObject occupant = entityOccupancy.getEntityAt(toX, toY, zLevel);
        if (occupant != null && occupant != entity && !canPassThrough(entity, occupant)) {
            return MoveResult.blocked("Blocked by " + occupant.getName());
        }
        
        return tileResult;
    }
    
    public void onEntityMoved(GameObject entity, int oldX, int oldY, int newX, int newY) {
        int zLevel = getEntityZLevel(entity);
        
        entityOccupancy.remove(entity, oldX, oldY, zLevel);
        entityOccupancy.set(entity, newX, newY, zLevel);
        
        CollisionType oldType = collisionMap.get(oldX, oldY, zLevel);
        behaviorRegistry.getBehavior(oldType).onExit(entity, oldX, oldY);
        
        CollisionType newType = collisionMap.get(newX, newY, zLevel);
        behaviorRegistry.getBehavior(newType).onEnter(entity, newX, newY);
    }
    
    private int getEntityZLevel(GameObject entity) {
        ZLevelComponent zComp = entity.getComponent(ZLevelComponent.class);
        return zComp != null ? zComp.getZLevel() : 0;
    }
    
    private boolean canPassThrough(GameObject mover, GameObject occupant) {
        EntityCollider collider = occupant.getComponent(EntityCollider.class);
        return collider == null || !collider.blocksEntities();
    }
}
```

### 4.8 EntityOccupancyMap

```java
public class EntityOccupancyMap {
    private final Map<Long, GameObject> occupancy = new HashMap<>();
    private final Map<GameObject, long[]> entityPositions = new HashMap<>();
    
    public void set(GameObject entity, int x, int y, int z) {
        occupancy.put(packCoord(x, y, z), entity);
        entityPositions.put(entity, new long[]{x, y, z});
    }
    
    public void remove(GameObject entity, int x, int y, int z) {
        long key = packCoord(x, y, z);
        if (occupancy.get(key) == entity) occupancy.remove(key);
        entityPositions.remove(entity);
    }
    
    public GameObject getEntityAt(int x, int y, int z) {
        return occupancy.get(packCoord(x, y, z));
    }
    
    private long packCoord(int x, int y, int z) {
        return ((long)(z & 0xFFFF) << 48) | ((long)(x & 0xFFFFFF) << 24) | (y & 0xFFFFFF);
    }
}
```

### 4.9 ZLevelComponent

```java
public class ZLevelComponent extends Component {
    @Getter
    private int zLevel = 0;
    
    public void setZLevel(int zLevel) {
        this.zLevel = zLevel;
    }
}
```

### 4.10 GridMovement Integration

```java
public class GridMovement extends Component {
    @Getter @Setter
    private CollisionSystem collisionSystem;  // Replace TilemapRenderer
    
    public boolean move(Direction direction) {
        if (isMoving) return false;
        
        updateFacingDirection(direction.dx, direction.dy);
        
        int targetX = gridX + direction.dx;
        int targetY = gridY + direction.dy;
        
        MoveResult result;
        if (collisionSystem != null) {
            result = collisionSystem.canMove(gameObject, gridX, gridY, targetX, targetY, direction);
        } else {
            result = MoveResult.ALLOWED;
        }
        
        if (!result.allowed()) return false;
        
        startMovement(targetX, targetY, result.modifier());
        return true;
    }
    
    private void startMovement(int targetX, int targetY, MovementModifier modifier) {
        int oldX = gridX, oldY = gridY;
        gridX = targetX;
        gridY = targetY;
        
        // ... existing position interpolation setup ...
        
        isJumping = (modifier == MovementModifier.JUMP);
        isSliding = (modifier == MovementModifier.SLIDE);
        
        if (collisionSystem != null) {
            collisionSystem.onEntityMoved(gameObject, oldX, oldY, targetX, targetY);
        }
    }
    
    private void finishMovement() {
        // ... existing finish logic ...
        
        // Handle sliding (ice)
        if (isSliding && collisionSystem != null) {
            isSliding = false;
            MoveResult result = collisionSystem.canMove(
                gameObject, gridX, gridY,
                gridX + facingDirection.dx, gridY + facingDirection.dy,
                facingDirection
            );
            if (result.allowed() && result.modifier() == MovementModifier.SLIDE) {
                move(facingDirection);  // Continue sliding
            }
        }
    }
}
```

### 4.11 Editor: Collision Brush Tool

```java
public class CollisionBrushTool implements EditorTool {
    private EditorScene scene;
    private CollisionType selectedType = CollisionType.SOLID;
    private int selectedZLevel = 0;
    private int brushSize = 1;
    
    public String getName() { return "Collision"; }
    public String getShortcutKey() { return "C"; }
    
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) paintAt(tileX, tileY);
        else if (button == 1) eraseAt(tileX, tileY);
    }
    
    private void paintAt(int tileX, int tileY) {
        CollisionMap map = scene.getCollisionMap();
        int half = brushSize / 2;
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                map.set(tileX + dx, tileY + dy, selectedZLevel, selectedType);
            }
        }
        scene.markDirty();
    }
    
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (!scene.isCollisionVisible()) return;
        
        // Draw colored overlay for each collision tile
        CollisionMap map = scene.getCollisionMap();
        float[] bounds = camera.getWorldBounds();
        
        for (int y = (int)bounds[1]; y <= (int)bounds[3]; y++) {
            for (int x = (int)bounds[0]; x <= (int)bounds[2]; x++) {
                CollisionType type = map.get(x, y, selectedZLevel);
                if (type == CollisionType.PASSABLE) continue;
                
                CollisionType.Color c = type.getEditorColor();
                int color = ImGui.colorConvertFloat4ToU32(c.r(), c.g(), c.b(), c.a());
                drawTileHighlight(camera, x, y, color);
            }
        }
    }
}
```

### 4.12 Editor: Collision Panel

```java
public class CollisionPanel {
    private EditorScene scene;
    private CollisionBrushTool collisionTool;
    
    public void render() {
        if (ImGui.begin("Collision")) {
            // Visibility toggle
            boolean visible = scene.isCollisionVisible();
            if (ImGui.checkbox("Show Collision", visible)) {
                scene.setCollisionVisible(!visible);
            }
            
            // Z-Level selector
            int[] zLevel = {collisionTool.getSelectedZLevel()};
            ImGui.combo("Z-Level", zLevel, new String[]{"Ground (0)", "Elevated (1)"});
            collisionTool.setSelectedZLevel(zLevel[0]);
            
            // Collision type selector with color indicators
            for (CollisionType type : CollisionType.values()) {
                CollisionType.Color c = type.getEditorColor();
                ImGui.colorButton("##" + type.name(), new float[]{c.r(), c.g(), c.b(), c.a()});
                ImGui.sameLine();
                if (ImGui.radioButton(type.getDisplayName(), 
                    collisionTool.getSelectedType() == type)) {
                    collisionTool.setSelectedType(type);
                }
            }
            
            // Brush size
            int[] size = {collisionTool.getBrushSize()};
            ImGui.sliderInt("Brush Size", size, 1, 10);
            collisionTool.setBrushSize(size[0]);
        }
        ImGui.end();
    }
}
```

### 4.13 Files to Create

```
src/main/java/com/pocket/rpg/
├── collision/
│   ├── CollisionType.java
│   ├── CollisionMap.java
│   ├── CollisionLayer.java
│   ├── CollisionChunk.java
│   ├── CollisionSystem.java
│   ├── GridCollisionSystem.java
│   ├── EntityOccupancyMap.java
│   ├── MoveResult.java
│   ├── MovementModifier.java
│   ├── Direction.java
│   └── behaviors/
│       ├── TileBehavior.java
│       ├── CollisionBehaviorRegistry.java
│       ├── PassableBehavior.java
│       ├── SolidBehavior.java
│       ├── WaterBehavior.java
│       ├── IceBehavior.java
│       ├── LedgeBehavior.java
│       ├── ZLevelChangeBehavior.java
│       └── TriggerBehavior.java
├── components/
│   └── ZLevelComponent.java
└── editor/
    ├── tools/CollisionBrushTool.java
    └── panels/CollisionPanel.java
```

### 4.14 Testing Checklist

- [ ] Paint collision types on ground layer (Z=0)
- [ ] Paint collision types on elevated layer (Z=1)
- [ ] Toggle collision visibility
- [ ] Collision overlay shows correct colors
- [ ] Collision data saves/loads from .scene file
- [ ] Player blocked by SOLID tiles
- [ ] Player slides on ICE tiles
- [ ] Player jumps over LEDGE in correct direction
- [ ] Player changes Z-level on STAIRS tiles
- [ ] NPCs block player (entity occupancy)

---

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

## Phase 6: UX Polish

**Goal:** Make editor pleasant to use.

### 6.1 Undo/Redo System

```java
public interface EditorCommand {
    void execute();
    void undo();
    String getDescription();
}

public class CommandHistory {
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private final int maxHistory = 100;
    
    public void execute(EditorCommand command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        
        while (undoStack.size() > maxHistory) {
            undoStack.removeLast();
        }
    }
    
    public void undo() {
        if (undoStack.isEmpty()) return;
        EditorCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
    }
    
    public void redo() {
        if (redoStack.isEmpty()) return;
        EditorCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
    }
}

// Example commands
public class PaintTilesCommand implements EditorCommand {
    private final TilemapRenderer tilemap;
    private final Map<Long, TilemapRenderer.Tile> oldTiles = new HashMap<>();
    private final Map<Long, TilemapRenderer.Tile> newTiles = new HashMap<>();
    
    public void recordChange(int x, int y, Tile oldTile, Tile newTile) {
        long key = packCoord(x, y);
        if (!oldTiles.containsKey(key)) oldTiles.put(key, oldTile);
        newTiles.put(key, newTile);
    }
    
    public void execute() {
        for (var entry : newTiles.entrySet()) {
            tilemap.set(unpackX(entry.getKey()), unpackY(entry.getKey()), entry.getValue());
        }
    }
    
    public void undo() {
        for (var entry : oldTiles.entrySet()) {
            tilemap.set(unpackX(entry.getKey()), unpackY(entry.getKey()), entry.getValue());
        }
    }
    
    public String getDescription() {
        return "Paint " + newTiles.size() + " tiles";
    }
}
```

### 6.2 Logging System

```java
public class EditorLogger {
    private static final int MAX_LOG_ENTRIES = 1000;
    private static final Deque<LogEntry> logBuffer = new ArrayDeque<>();
    
    public static void info(String message) { log(LogLevel.INFO, message); }
    public static void warn(String message) { log(LogLevel.WARN, message); }
    public static void error(String message) { log(LogLevel.ERROR, message); }
    
    private static void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(level, message, System.currentTimeMillis());
        
        synchronized (logBuffer) {
            logBuffer.addLast(entry);
            while (logBuffer.size() > MAX_LOG_ENTRIES) {
                logBuffer.removeFirst();
            }
        }
        
        System.out.println("[" + level + "] " + message);
    }
    
    public static List<LogEntry> getRecentLogs(int count) {
        synchronized (logBuffer) {
            int start = Math.max(0, logBuffer.size() - count);
            return new ArrayList<>(logBuffer).subList(start, logBuffer.size());
        }
    }
}

public record LogEntry(LogLevel level, String message, long timestamp) {}
public enum LogLevel { DEBUG, INFO, WARN, ERROR }
```

### 6.3 Log Panel

```java
public class LogPanel {
    private LogLevel filterLevel = LogLevel.INFO;
    private boolean autoScroll = true;
    
    public void render() {
        ImGui.begin("Log");
        
        // Filter buttons
        if (ImGui.button("Clear")) EditorLogger.clear();
        ImGui.sameLine();
        ImGui.checkbox("Auto-scroll", autoScroll);
        ImGui.sameLine();
        ImGui.combo("Filter", filterLevel.ordinal(), LogLevel.values());
        
        ImGui.separator();
        
        // Log entries
        ImGui.beginChild("log_scroll");
        for (LogEntry entry : EditorLogger.getRecentLogs(500)) {
            if (entry.level().ordinal() < filterLevel.ordinal()) continue;
            
            int color = switch (entry.level()) {
                case DEBUG -> 0xFF888888;
                case INFO -> 0xFFFFFFFF;
                case WARN -> 0xFF00FFFF;
                case ERROR -> 0xFF0000FF;
            };
            
            ImGui.textColored(color, formatEntry(entry));
        }
        
        if (autoScroll) ImGui.setScrollHereY(1.0f);
        ImGui.endChild();
        
        ImGui.end();
    }
}
```

### 6.4 Keyboard Shortcuts

```java
public class EditorShortcuts {
    public void setupDefaults() {
        // File
        bind(KeyCode.S, Modifier.CTRL, () -> editor.saveScene());
        bind(KeyCode.O, Modifier.CTRL, () -> editor.openScene());
        bind(KeyCode.N, Modifier.CTRL, () -> editor.newScene());
        
        // Edit
        bind(KeyCode.Z, Modifier.CTRL, () -> editor.undo());
        bind(KeyCode.Y, Modifier.CTRL, () -> editor.redo());
        
        // Tools (already implemented)
        bind(KeyCode.B, () -> editor.setTool("brush"));
        bind(KeyCode.E, () -> editor.setTool("eraser"));
        bind(KeyCode.F, () -> editor.setTool("fill"));
        bind(KeyCode.R, () -> editor.setTool("rectangle"));
        bind(KeyCode.I, () -> editor.setTool("picker"));
        bind(KeyCode.V, () -> editor.setTool("selection"));
        bind(KeyCode.C, () -> editor.setTool("collision"));
        
        // Edit modes
        bind(KeyCode.NUM_1, () -> editor.setEditMode(EditMode.TILEMAP));
        bind(KeyCode.NUM_2, () -> editor.setEditMode(EditMode.COLLISION));
        bind(KeyCode.NUM_3, () -> editor.setEditMode(EditMode.ENTITY));
        
        // View
        bind(KeyCode.G, () -> editor.toggleGrid());
        bind(KeyCode.HOME, () -> editor.resetCamera());
        
        // Selection
        bind(KeyCode.DELETE, () -> editor.deleteSelected());
        bind(KeyCode.D, Modifier.CTRL, () -> editor.duplicateSelected());
    }
}
```

### 6.5 Files to Create

```
src/main/java/com/pocket/rpg/editor/
├── commands/
│   ├── EditorCommand.java
│   ├── CommandHistory.java
│   ├── PaintTilesCommand.java
│   ├── PaintCollisionCommand.java
│   ├── PlaceEntityCommand.java
│   ├── MoveEntityCommand.java
│   └── DeleteEntityCommand.java
├── logging/
│   ├── EditorLogger.java
│   ├── LogEntry.java
│   └── LogLevel.java
└── panels/
    └── LogPanel.java
```

---

## Phase 7: Advanced Features (Future)

Lower priority, implement as needed.

### 7.1 Sprite Pivot Editor

**Goal:** Edit pivot points for sprites and spritesheets.

**Access methods:**
1. Right-click on sprite/spritesheet in asset browser → "Edit Pivot"
2. Menu: Assets → Pivot Editor
3. Selection dropdown in the pivot editor panel

**Features:**
- Visual pivot point indicator on sprite preview
- Click to set pivot position
- Preset buttons: Center, Bottom-Center, Bottom-Left, Top-Left
- Per-sprite pivot override for spritesheets
- Default pivot for entire spritesheet
- Save pivot data to .spritesheet file

```java
public class PivotEditorPanel {
    private Sprite selectedSprite;
    private SpriteSheet selectedSheet;
    private int selectedSpriteIndex = -1;
    
    public void render() {
        ImGui.begin("Pivot Editor");
        
        // Sprite/sheet selector
        renderAssetSelector();
        
        if (selectedSprite == null && selectedSheet == null) {
            ImGui.textDisabled("Select a sprite or spritesheet");
            ImGui.end();
            return;
        }
        
        // Large preview with grid
        renderPreview();
        
        // Preset buttons
        if (ImGui.button("Center")) setPivot(0.5f, 0.5f);
        ImGui.sameLine();
        if (ImGui.button("Bottom-Center")) setPivot(0.5f, 0f);
        ImGui.sameLine();
        if (ImGui.button("Bottom-Left")) setPivot(0f, 0f);
        
        // Manual input
        float[] pivot = {currentPivotX, currentPivotY};
        if (ImGui.sliderFloat2("Pivot", pivot, 0f, 1f)) {
            setPivot(pivot[0], pivot[1]);
        }
        
        // Apply/Reset
        if (ImGui.button("Apply")) applyChanges();
        ImGui.sameLine();
        if (ImGui.button("Reset")) resetChanges();
        
        ImGui.end();
    }
    
    private void renderPreview() {
        // Draw sprite with checkerboard background
        // Draw pivot marker (crosshair)
        // Handle click to set pivot position
    }
}
```

### 7.2 Scene Transitions / Triggers

```java
public class TriggerEditorTool implements EditorTool {
    // Draw rectangle to define trigger bounds
    // Configure trigger type and properties
}

public class TriggerInspectorPanel {
    // Type selector: SCENE_TRANSITION, EVENT, DIALOGUE, etc.
    // Target scene (for transitions)
    // Spawn point name
    // Custom properties
}
```

### 7.3 JSON-based Prefabs

Load prefab definitions from JSON files instead of code.

```json
{
  "id": "NPC_Villager",
  "components": [
    {
      "type": "SpriteRenderer",
      "sprite": "sprites/npc_villager.png",
      "zIndex": 1
    },
    {
      "type": "GridMovement",
      "speed": 2.0
    },
    {
      "type": "NPCBehavior",
      "dialogueId": "villager_01"
    }
  ],
  "properties": {
    "facing": { "type": "enum", "values": ["UP", "DOWN", "LEFT", "RIGHT"] },
    "dialogueId": { "type": "string" }
  }
}
```

### 7.4 Copy/Paste for Tiles and Entities

### 7.5 Tileset Auto-Tiling

Define rules for automatic tile selection based on neighbors.

---

## Implementation Order Summary

| Phase | Status | Effort | Description |
|-------|--------|--------|-------------|
| 1. Foundation | ✅ Done | Medium | Editor shell, ImGui, camera |
| 2. Serialization | ✅ Done | Medium | Scene file format, save/load infrastructure |
| 3. Tilemap Painting | ✅ Done | Large | Brush tools, layers, palette |
| 3.5. Consolidation | Pending | Medium | Complete save/load, opacity, batch fixes, tool polish |
| 4. Collision Editing | Pending | Large | Full collision system with behaviors |
| 5. Entity Placement | Pending | Medium | Prefab browser, placement, inspector, edit modes |
| 6. UX Polish | Pending | Medium | Undo/redo, logging, shortcuts |
| 7. Advanced | Pending | Large | Pivot editor, triggers, auto-tile |

---

## Complete File Structure

```
src/main/java/com/pocket/rpg/
├── collision/
│   ├── CollisionType.java
│   ├── CollisionMap.java
│   ├── CollisionLayer.java
│   ├── CollisionChunk.java
│   ├── CollisionSystem.java
│   ├── GridCollisionSystem.java
│   ├── EntityOccupancyMap.java
│   ├── MoveResult.java
│   ├── MovementModifier.java
│   ├── Direction.java
│   └── behaviors/
│       ├── TileBehavior.java
│       ├── CollisionBehaviorRegistry.java
│       ├── PassableBehavior.java
│       ├── SolidBehavior.java
│       ├── WaterBehavior.java
│       ├── IceBehavior.java
│       ├── LedgeBehavior.java
│       ├── ZLevelChangeBehavior.java
│       └── TriggerBehavior.java
├── components/
│   ├── ZLevelComponent.java
│   └── ... (existing)
├── editor/
│   ├── EditorApplication.java
│   ├── EditMode.java
│   ├── core/
│   │   ├── EditorConfig.java
│   │   ├── EditorWindow.java
│   │   ├── ImGuiLayer.java
│   │   └── FileDialogs.java
│   ├── camera/
│   │   └── EditorCamera.java
│   ├── tools/
│   │   ├── EditorTool.java
│   │   ├── ToolManager.java
│   │   ├── TileBrushTool.java
│   │   ├── TileEraserTool.java
│   │   ├── TileFillTool.java
│   │   ├── TileRectangleTool.java
│   │   ├── TilePickerTool.java
│   │   ├── CollisionBrushTool.java
│   │   ├── EntityPlacerTool.java
│   │   └── SelectionTool.java
│   ├── panels/
│   │   ├── TilesetPalettePanel.java
│   │   ├── LayerPanel.java
│   │   ├── CollisionPanel.java
│   │   ├── PrefabBrowserPanel.java
│   │   ├── EntityInspectorPanel.java
│   │   └── LogPanel.java
│   ├── commands/
│   │   ├── EditorCommand.java
│   │   ├── CommandHistory.java
│   │   └── ... (command implementations)
│   ├── scene/
│   │   ├── EditorScene.java
│   │   ├── TilemapLayer.java
│   │   ├── EditorEntity.java
│   │   └── LayerVisibilityMode.java
│   ├── serialization/
│   │   ├── EditorSceneSerializer.java
│   │   ├── TilemapComponentData.java
│   │   └── ChunkData.java
│   ├── rendering/
│   │   ├── EditorFramebuffer.java
│   │   └── EditorSceneRenderer.java
│   ├── logging/
│   │   ├── EditorLogger.java
│   │   ├── LogEntry.java
│   │   └── LogLevel.java
│   ├── ui/
│   │   ├── EditorMenuBar.java
│   │   ├── SceneViewport.java
│   │   └── StatusBar.java
│   └── tileset/
│       ├── TileSelection.java
│       ├── TilesetRegistry.java
│       └── CreateSpritesheetDialog.java
├── rendering/
│   ├── SpriteBatch.java             # Updated with vertex colors
│   └── renderers/BatchRenderer.java # Updated with opacity
└── serialization/
    ├── SceneData.java
    └── ... (existing)
```

---

## Key Integration Points

### TilemapRenderer (Existing)
- Editor uses TilemapRenderer directly for painting
- Each layer is a GameObject with TilemapRenderer component
- Chunk structure used for efficient storage and serialization

### GridMovement (Updated)
- Uses CollisionSystem instead of TilemapRenderer for collision
- Handles MovementModifier (SLIDE, JUMP, etc.)
- Integrates with ZLevelComponent for multi-level support

### Scene (Existing)
- RuntimeScene extends Scene for loaded scenes
- Uses existing addGameObject, getRenderers(), etc.

### AssetManager (Existing)
- Editor uses AssetManager for loading tilesets, sprites
- Prefab previews loaded via AssetManager

### Serializer (Existing)
- SceneSerializer uses existing Gson setup
- EditorSceneSerializer handles EditorScene ↔ SceneData conversion