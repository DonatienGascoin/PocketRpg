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

**What's Stubbed (for Phase 2):**
- Save/Load only shows dialogs, no actual serialization
- Hierarchy/Inspector/Tileset/Layers panels are placeholders
- No actual scene rendering (just grid)

**Known Issues Fixed:**
- Borderless fullscreen hides window controls → use maximized windowed instead
- Updated to imgui-java 1.90.0 for full ImGuiKey keyboard support

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

**Required Changes to Existing Code:**
1. Add `transient` to Component.gameObject and other runtime fields
2. Add no-arg constructors to all Component subclasses
3. Add `setGameObject()` setter to Component base class

**What Works:**
- ✅ Any Component subclass serializes automatically
- ✅ Sprite/Texture references resolve via AssetManager  
- ✅ Parent-child hierarchy preserved
- ✅ Scene save/load round-trip
- ✅ Framebuffer rendering infrastructure
- ✅ PrefabRegistry for code-based prefabs

**Deferred to Later Phases:**
- Tilemap chunk serialization (Phase 3)
- CollisionMap layer (Phase 4)
- Inspector panel for editing (Phase 5)
- JSON-based prefabs (Phase 7+)

### 2.1 Scene File Format (Updated)

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
                [0, 1, 2, -1, 3, ...],
                [4, 5, 6, 7, 8, ...]
              ]
            },
            "1,0": { ... }
          }
        }
      }
    },
    {
      "name": "ObjectsLayer",
      "position": [0, 0, 0],
      "components": {
        "TilemapRenderer": {
          "zIndex": 0,
          "tileSize": 1.0,
          "tilesetPath": "gameData/assets/sprites/objects.png",
          "tilesetCols": 8,
          "tilesetRows": 8,
          "chunks": { ... }
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
    },
    {
      "prefabId": "NPC",
      "name": "Villager_01",
      "position": [10, 8, 0],
      "properties": {
        "dialogueId": "villager_01",
        "facing": "DOWN"
      }
    }
  ],
  "collisionLayer": {
    "tileSize": 1.0,
    "chunks": {
      "0,0": {
        "data": [
          [0, 0, 1, 1, 0, ...],
          [0, 0, 1, 1, 0, ...]
        ]
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

**Notes:**
- `tiles` array uses tileset indices; `-1` means empty
- `collisionLayer.data` uses: `0` = passable, `1` = solid, `2` = water, `3` = ledge, etc.
- Each tilemap layer is a separate GameObject with TilemapRenderer component
- `triggers` for scene transitions and events

### 2.2 Data Classes

```java
// Scene structure
public record SceneData(
    String name,
    int version,
    CameraData camera,
    List<GameObjectData> gameObjects,
    List<EntityData> entities,
    CollisionLayerData collisionLayer,
    List<TriggerData> triggers
) {}

public record CameraData(
    float[] position,
    float orthographicSize
) {}

public record GameObjectData(
    String name,
    float[] position,
    Map<String, ComponentData> components
) {}

public record TilemapComponentData(
    int zIndex,
    float tileSize,
    String tilesetPath,
    int tilesetCols,
    int tilesetRows,
    Map<String, ChunkData> chunks
) implements ComponentData {}

public record ChunkData(
    int[][] tiles
) {}

public record EntityData(
    String prefabId,
    String name,
    float[] position,
    Map<String, Object> properties
) {}

public record CollisionLayerData(
    float tileSize,
    Map<String, CollisionChunkData> chunks
) {}

public record CollisionChunkData(
    int[][] data
) {}

public record TriggerData(
    String name,
    String type,
    float[] bounds,
    Map<String, Object> properties
) {}
```

### 2.3 SceneSerializer

```java
public class SceneSerializer {
    
    public static void save(EditorScene scene, Path path) {
        SceneData data = convertToData(scene);
        String json = Serializer.toJson(data, true);
        Files.writeString(path, json);
    }
    
    public static SceneData load(Path path) {
        String json = Files.readString(path);
        return Serializer.fromJson(json, SceneData.class);
    }
    
    private static SceneData convertToData(EditorScene scene) {
        // Convert live scene to serializable data
    }
}
```

### 2.4 SceneLoader (Game-side)

```java
public class SceneLoader {
    private final PrefabRegistry prefabs;
    private final AssetManager assets;
    
    public Scene load(String scenePath, ViewportConfig viewport, RenderingConfig config) {
        SceneData data = SceneSerializer.load(Path.of(scenePath));
        
        // Create scene
        Scene scene = new RuntimeScene(data.name());
        
        // Build tilemap GameObjects
        for (GameObjectData goData : data.gameObjects()) {
            GameObject go = buildGameObject(goData);
            scene.addGameObject(go);
        }
        
        // Instantiate entities from prefabs
        for (EntityData entity : data.entities()) {
            GameObject go = prefabs.instantiate(
                entity.prefabId(),
                entity.name(),
                new Vector3f(entity.position()[0], entity.position()[1], entity.position()[2])
            );
            applyProperties(go, entity.properties());
            scene.addGameObject(go);
        }
        
        // Build collision data (stored separately, used by GridMovement)
        CollisionMap collision = buildCollisionMap(data.collisionLayer());
        scene.setCollisionMap(collision);
        
        // Build triggers
        for (TriggerData trigger : data.triggers()) {
            scene.addTrigger(buildTrigger(trigger));
        }
        
        return scene;
    }
    
    private GameObject buildGameObject(GameObjectData data) {
        GameObject go = new GameObject(data.name());
        go.getTransform().setPosition(data.position()[0], data.position()[1], data.position()[2]);
        
        for (Map.Entry<String, ComponentData> entry : data.components().entrySet()) {
            Component component = buildComponent(entry.getKey(), entry.getValue());
            if (component != null) {
                go.addComponent(component);
            }
        }
        
        return go;
    }
    
    private TilemapRenderer buildTilemapRenderer(TilemapComponentData data) {
        // Load tileset
        Sprite tilesetSprite = assets.load(data.tilesetPath()).get();
        SpriteSheet tileset = new SpriteSheet(
            tilesetSprite.getTexture(),
            (int)(data.tileSize() * 16),  // Assuming 16 PPU
            (int)(data.tileSize() * 16)
        );
        List<Sprite> tileSprites = tileset.generateAllSprites();
        
        // Create tilemap
        TilemapRenderer tilemap = new TilemapRenderer(data.tileSize());
        tilemap.setZIndex(data.zIndex());
        
        // Populate chunks
        for (Map.Entry<String, ChunkData> chunkEntry : data.chunks().entrySet()) {
            String[] coords = chunkEntry.getKey().split(",");
            int cx = Integer.parseInt(coords[0]);
            int cy = Integer.parseInt(coords[1]);
            
            int[][] tiles = chunkEntry.getValue().tiles();
            int chunkSize = TilemapRenderer.TileChunk.CHUNK_SIZE;
            
            for (int ty = 0; ty < tiles.length && ty < chunkSize; ty++) {
                for (int tx = 0; tx < tiles[ty].length && tx < chunkSize; tx++) {
                    int tileIndex = tiles[ty][tx];
                    if (tileIndex >= 0 && tileIndex < tileSprites.size()) {
                        int worldX = cx * chunkSize + tx;
                        int worldY = cy * chunkSize + ty;
                        tilemap.set(worldX, worldY, 
                            new TilemapRenderer.Tile(tileSprites.get(tileIndex)));
                    }
                }
            }
        }
        
        return tilemap;
    }
}
```

### 2.5 PrefabRegistry (Game-side)

```java
public class PrefabRegistry {
    private final Map<String, PrefabFactory> prefabs = new HashMap<>();
    
    @FunctionalInterface
    public interface PrefabFactory {
        GameObject create(String name, Vector3f position);
    }
    
    public void register(String id, PrefabFactory factory) {
        prefabs.put(id, factory);
    }
    
    public GameObject instantiate(String id, String name, Vector3f position) {
        PrefabFactory factory = prefabs.get(id);
        if (factory == null) {
            System.err.println("Unknown prefab: " + id);
            return new GameObject(name, position);  // Empty placeholder
        }
        return factory.create(name, position);
    }
    
    public Set<String> getRegisteredPrefabs() {
        return prefabs.keySet();
    }
}

// Usage in game initialization:
public class GamePrefabs {
    public static void register(PrefabRegistry registry, AssetManager assets) {
        
        // Player prefab
        registry.register("Player", (name, pos) -> {
            GameObject player = new GameObject(name, pos);
            
            SpriteSheet sheet = loadPlayerSheet(assets);
            SpriteRenderer renderer = new SpriteRenderer(sheet.getSprite(0));
            renderer.setZIndex(1);
            renderer.setOriginBottomCenter();
            player.addComponent(renderer);
            
            GridMovement movement = new GridMovement();
            movement.setGridPosition((int)pos.x, (int)pos.y);
            player.addComponent(movement);
            
            player.addComponent(new PlayerMovement(movement));
            player.addComponent(new PlayerCameraFollow());
            
            return player;
        });
        
        // NPC prefab
        registry.register("NPC", (name, pos) -> {
            GameObject npc = new GameObject(name, pos);
            
            SpriteSheet sheet = loadNPCSheet(assets);
            SpriteRenderer renderer = new SpriteRenderer(sheet.getSprite(0));
            renderer.setZIndex(1);
            renderer.setOriginBottomCenter();
            npc.addComponent(renderer);
            
            npc.addComponent(new NPCBehavior());
            npc.addComponent(new Interactable());
            
            return npc;
        });
        
        // Sign prefab
        registry.register("Sign", (name, pos) -> {
            GameObject sign = new GameObject(name, pos);
            // ... components
            return sign;
        });
    }
}
```

### 2.6 Files to Create

```
src/main/java/com/pocket/rpg/
├── editor/
│   └── EditorScene.java            # Editor's scene representation
├── serialization/
│   ├── SceneSerializer.java
│   ├── scene/
│   │   ├── SceneData.java
│   │   ├── CameraData.java
│   │   ├── GameObjectData.java
│   │   ├── ComponentData.java
│   │   ├── TilemapComponentData.java
│   │   ├── ChunkData.java
│   │   ├── EntityData.java
│   │   ├── CollisionLayerData.java
│   │   ├── CollisionChunkData.java
│   │   └── TriggerData.java
│   └── custom/
│       └── SceneDataTypeAdapter.java   # If needed for polymorphic components
├── scenes/
│   ├── SceneLoader.java            # Game-side loader
│   └── RuntimeScene.java           # Scene subclass for loaded scenes
└── prefabs/
    ├── PrefabRegistry.java
    └── GamePrefabs.java            # Game-specific prefab definitions
```

**Deliverables:**
- Save scene to JSON from editor
- Load scene from JSON in game
- Prefab system for code-defined entity templates
- Round-trip test: create in editor → play in game
- Tilemap data preserved as TilemapRenderer components on GameObjects

---

## Phase 3: Tilemap Painting

**Goal:** Visual tile placement with brush tools.

### 3.1 Tileset Palette Panel

```java
public class TilesetPalettePanel {
    private SpriteSheet currentTileset;
    private int selectedTileIndex = -1;
    private int selectionStartX, selectionStartY;
    private int selectionEndX, selectionEndY;
    
    public void render() {
        ImGui.begin("Tileset");
        
        // Tileset selector dropdown
        if (ImGui.beginCombo("Tileset", currentTilesetName)) {
            for (String name : availableTilesets) {
                if (ImGui.selectable(name)) {
                    loadTileset(name);
                }
            }
            ImGui.endCombo();
        }
        
        // Render tileset as clickable grid
        if (currentTileset != null) {
            renderTilesetGrid();
        }
        
        ImGui.end();
    }
    
    private void renderTilesetGrid() {
        // Use ImGui.image() for each tile
        // Handle click for selection
        // Handle drag for multi-tile selection
    }
    
    public int getSelectedTileIndex() {
        return selectedTileIndex;
    }
    
    public int[] getSelectedTileRegion() {
        // Returns [startX, startY, width, height] for multi-tile selection
    }
}
```

### 3.2 Tool System

```java
public interface EditorTool {
    String getName();
    String getIcon();  // For toolbar
    KeyCode getShortcut();
    
    void onActivate();
    void onDeactivate();
    
    void onMouseDown(int tileX, int tileY, int button);
    void onMouseDrag(int tileX, int tileY, int button);
    void onMouseUp(int tileX, int tileY, int button);
    void onMouseMove(int tileX, int tileY);
    
    void renderOverlay(Camera camera);  // Preview, guides
}

public class ToolManager {
    private final List<EditorTool> tools = new ArrayList<>();
    private EditorTool activeTool;
    private final CommandHistory commandHistory;
    
    public void registerTool(EditorTool tool) {
        tools.add(tool);
    }
    
    public void setActiveTool(EditorTool tool) {
        if (activeTool != null) {
            activeTool.onDeactivate();
        }
        activeTool = tool;
        if (activeTool != null) {
            activeTool.onActivate();
        }
    }
    
    public void handleInput(int tileX, int tileY, InputEvent event) {
        if (activeTool == null) return;
        // Route to active tool
    }
}
```

### 3.3 Tile Brush Tool

```java
public class TileBrushTool implements EditorTool {
    private final TilesetPalettePanel palette;
    private final EditorScene scene;
    private final CommandHistory history;
    
    private int brushSize = 1;
    private boolean isPainting = false;
    private PaintTilesCommand currentCommand;
    
    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            isPainting = true;
            currentCommand = new PaintTilesCommand(scene.getActiveLayer());
            paintAt(tileX, tileY);
        }
    }
    
    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (isPainting) {
            paintAt(tileX, tileY);
        }
    }
    
    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (isPainting) {
            isPainting = false;
            if (currentCommand.hasChanges()) {
                history.execute(currentCommand);
            }
            currentCommand = null;
        }
    }
    
    private void paintAt(int tileX, int tileY) {
        int tileIndex = palette.getSelectedTileIndex();
        if (tileIndex < 0) return;
        
        TilemapRenderer tilemap = scene.getActiveLayer();
        
        // Apply brush size
        int halfBrush = brushSize / 2;
        for (int dy = -halfBrush; dy <= halfBrush; dy++) {
            for (int dx = -halfBrush; dx <= halfBrush; dx++) {
                int tx = tileX + dx;
                int ty = tileY + dy;
                
                // Record old tile for undo
                currentCommand.recordChange(tx, ty, tilemap.get(tx, ty));
                
                // Set new tile
                Sprite tileSprite = palette.getTileSprite(tileIndex);
                tilemap.set(tx, ty, new TilemapRenderer.Tile(tileSprite));
            }
        }
    }
    
    @Override
    public void renderOverlay(Camera camera) {
        // Draw brush preview at cursor position
        // Highlight tiles that would be affected
    }
}
```

### 3.4 Other Tile Tools

```java
public class TileEraserTool implements EditorTool {
    // Similar to brush, but sets tiles to null
    // Supports brush size
}

public class TileFillTool implements EditorTool {
    // Flood fill algorithm
    // Fill connected tiles of same type with selected tile
    
    private void floodFill(int startX, int startY, int newTileIndex) {
        TilemapRenderer tilemap = scene.getActiveLayer();
        TilemapRenderer.Tile targetTile = tilemap.get(startX, startY);
        
        // BFS flood fill
        Queue<int[]> queue = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        queue.add(new int[]{startX, startY});
        
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0], y = pos[1];
            
            long key = ((long)x << 32) | (y & 0xFFFFFFFFL);
            if (visited.contains(key)) continue;
            visited.add(key);
            
            TilemapRenderer.Tile current = tilemap.get(x, y);
            if (!tilesMatch(current, targetTile)) continue;
            
            // Paint this tile
            currentCommand.recordChange(x, y, current);
            tilemap.set(x, y, new TilemapRenderer.Tile(palette.getTileSprite(newTileIndex)));
            
            // Add neighbors
            queue.add(new int[]{x + 1, y});
            queue.add(new int[]{x - 1, y});
            queue.add(new int[]{x, y + 1});
            queue.add(new int[]{x, y - 1});
        }
    }
}

public class TileRectangleTool implements EditorTool {
    private int startX, startY;
    private boolean isDragging = false;
    
    // Click to start rectangle, drag to size, release to fill
    
    @Override
    public void renderOverlay(Camera camera) {
        if (isDragging) {
            // Draw rectangle preview from start to current cursor
        }
    }
}

public class TilePickerTool implements EditorTool {
    // Click on tile to select it in palette
    // "Eyedropper" functionality
    
    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        TilemapRenderer.Tile tile = scene.getActiveLayer().get(tileX, tileY);
        if (tile != null) {
            palette.selectTile(tile.sprite());
            toolManager.setActiveTool(brushTool);  // Switch back to brush
        }
    }
}
```

### 3.5 Layer Management Panel

```java
public class LayerPanel {
    private final EditorScene scene;
    
    public void render() {
        ImGui.begin("Layers");
        
        // Add layer button
        if (ImGui.button("+ Add Layer")) {
            scene.addTilemapLayer("NewLayer");
        }
        
        ImGui.separator();
        
        // Layer list (reversed - top layer first visually)
        List<TilemapLayer> layers = scene.getLayers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            TilemapLayer layer = layers.get(i);
            
            ImGui.pushID(i);
            
            // Visibility toggle
            boolean visible = layer.isVisible();
            if (ImGui.checkbox("##visible", visible)) {
                layer.setVisible(!visible);
            }
            
            ImGui.sameLine();
            
            // Selectable layer name
            boolean isActive = scene.getActiveLayerIndex() == i;
            if (ImGui.selectable(layer.getName(), isActive)) {
                scene.setActiveLayer(i);
            }
            
            // Context menu
            if (ImGui.beginPopupContextItem()) {
                if (ImGui.menuItem("Rename")) {
                    // Open rename dialog
                }
                if (ImGui.menuItem("Delete")) {
                    scene.removeLayer(i);
                }
                if (ImGui.menuItem("Move Up") && i < layers.size() - 1) {
                    scene.swapLayers(i, i + 1);
                }
                if (ImGui.menuItem("Move Down") && i > 0) {
                    scene.swapLayers(i, i - 1);
                }
                ImGui.endPopup();
            }
            
            ImGui.popID();
        }
        
        ImGui.end();
    }
}

// Editor's layer wrapper
public class TilemapLayer {
    private final GameObject gameObject;
    private final TilemapRenderer tilemap;
    private boolean visible = true;
    private String name;
    
    public TilemapRenderer getTilemap() {
        return tilemap;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
        gameObject.setEnabled(visible);
    }
}
```

### 3.6 Grid Overlay Rendering

```java
public class EditorOverlayRenderer {
    private final Shader gridShader;
    private int gridVAO, gridVBO;
    
    public void renderGrid(Camera camera, float tileSize) {
        float[] bounds = camera.getWorldBounds();
        float left = bounds[0], bottom = bounds[1], right = bounds[2], top = bounds[3];
        
        // Snap to grid
        int startX = (int) Math.floor(left / tileSize);
        int startY = (int) Math.floor(bottom / tileSize);
        int endX = (int) Math.ceil(right / tileSize);
        int endY = (int) Math.ceil(top / tileSize);
        
        // Draw grid lines
        gridShader.use();
        gridShader.uploadMat4f("projection", camera.getProjectionMatrix());
        gridShader.uploadMat4f("view", camera.getViewMatrix());
        gridShader.uploadVec4f("color", new Vector4f(1, 1, 1, 0.2f));
        
        // Generate line vertices and draw
        // ...
    }
    
    public void renderTileHighlight(Camera camera, int tileX, int tileY, float tileSize, Vector4f color) {
        // Highlight single tile (for cursor position)
    }
    
    public void renderBrushPreview(Camera camera, int tileX, int tileY, int brushSize, float tileSize) {
        // Show affected area for current brush
    }
    
    public void renderSelection(Camera camera, int x1, int y1, int x2, int y2, float tileSize) {
        // Selection rectangle for rectangle tool
    }
}
```

### 3.7 Files to Create

```
src/main/java/com/pocket/rpg/editor/
├── tools/
│   ├── EditorTool.java
│   ├── ToolManager.java
│   ├── TileBrushTool.java
│   ├── TileEraserTool.java
│   ├── TileFillTool.java
│   ├── TileRectangleTool.java
│   └── TilePickerTool.java
├── panels/
│   ├── TilesetPalettePanel.java
│   └── LayerPanel.java
├── scene/
│   ├── TilemapLayer.java
│   └── EditorScene.java (updated)
└── rendering/
    └── EditorOverlayRenderer.java
```

**Deliverables:**
- Paint tiles with brush (variable size)
- Erase tiles
- Fill tool (flood fill)
- Rectangle tool
- Tile picker (eyedropper)
- Multiple tilemap layers as separate GameObjects
- Layer visibility toggle
- Grid visualization
- Cursor/brush preview

---

## Phase 4: Collision Editing

**Goal:** Paint collision data separately from visuals.

### 4.1 Collision Layer Data Structure

```java
public class CollisionMap {
    private final Map<Long, CollisionChunk> chunks = new HashMap<>();
    private final float tileSize;
    
    public static final int PASSABLE = 0;
    public static final int SOLID = 1;
    public static final int WATER = 2;
    public static final int LEDGE_DOWN = 3;
    public static final int LEDGE_UP = 4;
    public static final int LEDGE_LEFT = 5;
    public static final int LEDGE_RIGHT = 6;
    
    public int get(int tileX, int tileY) {
        // Similar chunk logic to TilemapRenderer
    }
    
    public void set(int tileX, int tileY, int collisionType) {
        // ...
    }
    
    public boolean isSolid(int tileX, int tileY) {
        return get(tileX, tileY) == SOLID;
    }
    
    public boolean isPassable(int tileX, int tileY) {
        int type = get(tileX, tileY);
        return type == PASSABLE || type >= LEDGE_DOWN;  // Ledges are passable
    }
}

public class CollisionChunk {
    public static final int CHUNK_SIZE = 32;  // Match TilemapRenderer
    private final int[][] data = new int[CHUNK_SIZE][CHUNK_SIZE];
}
```

### 4.2 Collision Brush Tool

```java
public class CollisionBrushTool implements EditorTool {
    private int collisionType = CollisionMap.SOLID;
    private int brushSize = 1;
    
    // Similar to TileBrushTool but operates on CollisionMap
    
    @Override
    public void renderOverlay(Camera camera) {
        // Render collision overlay on all visible tiles
        CollisionMap collision = scene.getCollisionMap();
        float[] bounds = camera.getWorldBounds();
        
        for each visible tile {
            int type = collision.get(tx, ty);
            if (type != CollisionMap.PASSABLE) {
                Vector4f color = getColorForType(type);
                overlayRenderer.renderTileHighlight(camera, tx, ty, tileSize, color);
            }
        }
    }
    
    private Vector4f getColorForType(int type) {
        return switch (type) {
            case CollisionMap.SOLID -> new Vector4f(1, 0, 0, 0.4f);      // Red
            case CollisionMap.WATER -> new Vector4f(0, 0, 1, 0.4f);      // Blue
            case CollisionMap.LEDGE_DOWN -> new Vector4f(1, 1, 0, 0.4f); // Yellow
            default -> new Vector4f(1, 0, 1, 0.4f);                      // Magenta
        };
    }
}
```

### 4.3 Collision Panel

```java
public class CollisionPanel {
    private int selectedType = CollisionMap.SOLID;
    
    public void render() {
        ImGui.begin("Collision");
        
        // Toggle collision layer visibility
        boolean showCollision = scene.isCollisionVisible();
        if (ImGui.checkbox("Show Collision", showCollision)) {
            scene.setCollisionVisible(!showCollision);
        }
        
        ImGui.separator();
        
        // Collision type selector
        ImGui.text("Collision Type:");
        
        if (ImGui.radioButton("Solid", selectedType == CollisionMap.SOLID)) {
            selectedType = CollisionMap.SOLID;
        }
        if (ImGui.radioButton("Water", selectedType == CollisionMap.WATER)) {
            selectedType = CollisionMap.WATER;
        }
        if (ImGui.radioButton("Ledge Down", selectedType == CollisionMap.LEDGE_DOWN)) {
            selectedType = CollisionMap.LEDGE_DOWN;
        }
        // ... other types
        
        if (ImGui.radioButton("Eraser (Passable)", selectedType == CollisionMap.PASSABLE)) {
            selectedType = CollisionMap.PASSABLE;
        }
        
        ImGui.end();
    }
    
    public int getSelectedType() {
        return selectedType;
    }
}
```

### 4.4 Integration with GridMovement

```java
// Update GridMovement to use CollisionMap from scene
public class GridMovement extends Component {
    
    private CollisionMap collisionMap;  // Set by SceneLoader
    
    public void setCollisionMap(CollisionMap map) {
        this.collisionMap = map;
    }
    
    private MoveResult canMoveTo(int targetX, int targetY) {
        if (collisionMap == null) {
            return MoveResult.ALLOWED;
        }
        
        int collisionType = collisionMap.get(targetX, targetY);
        
        return switch (collisionType) {
            case CollisionMap.SOLID, CollisionMap.WATER -> MoveResult.BLOCKED;
            case CollisionMap.LEDGE_DOWN -> 
                facingDirection == Direction.DOWN ? MoveResult.JUMP : MoveResult.BLOCKED;
            case CollisionMap.LEDGE_UP -> 
                facingDirection == Direction.UP ? MoveResult.JUMP : MoveResult.BLOCKED;
            // ... other ledge directions
            default -> MoveResult.ALLOWED;
        };
    }
}
```

### 4.5 Files to Create

```
src/main/java/com/pocket/rpg/
├── editor/
│   ├── tools/
│   │   └── CollisionBrushTool.java
│   └── panels/
│       └── CollisionPanel.java
└── physics/  (or core/)
    ├── CollisionMap.java
    └── CollisionChunk.java
```

**Deliverables:**
- Paint collision layer (solid, water, ledges)
- Visual collision overlay (toggleable)
- Collision data in scene file
- GridMovement uses CollisionMap instead of TilemapRenderer for collision

---

## Phase 5: Entity Placement

**Goal:** Place prefab instances in scene.

### 5.1 Prefab Browser Panel

```java
public class PrefabBrowserPanel {
    private final PrefabRegistry registry;
    private final Map<String, Texture> prefabPreviews;  // Cached preview sprites
    private String selectedPrefabId = null;
    private String filterText = "";
    
    public void render() {
        ImGui.begin("Prefabs");
        
        // Search filter
        ImGui.inputText("Search", filterText);
        
        ImGui.separator();
        
        // Grid of prefab buttons
        float panelWidth = ImGui.getContentRegionAvailX();
        float buttonSize = 64;
        int columns = Math.max(1, (int)(panelWidth / (buttonSize + 8)));
        
        ImGui.columns(columns, "prefab_grid", false);
        
        for (String prefabId : registry.getRegisteredPrefabs()) {
            if (!filterText.isEmpty() && !prefabId.toLowerCase().contains(filterText.toLowerCase())) {
                continue;
            }
            
            boolean isSelected = prefabId.equals(selectedPrefabId);
            
            // Render prefab button with preview image
            Texture preview = prefabPreviews.get(prefabId);
            if (preview != null) {
                if (ImGui.imageButton(preview.getTextureId(), buttonSize, buttonSize)) {
                    selectedPrefabId = prefabId;
                }
            } else {
                if (ImGui.button(prefabId, buttonSize, buttonSize)) {
                    selectedPrefabId = prefabId;
                }
            }
            
            // Tooltip with prefab name
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(prefabId);
            }
            
            ImGui.nextColumn();
        }
        
        ImGui.columns(1);
        ImGui.end();
    }
    
    public String getSelectedPrefabId() {
        return selectedPrefabId;
    }
}
```

### 5.2 Entity Placer Tool

```java
public class EntityPlacerTool implements EditorTool {
    private final PrefabBrowserPanel prefabPanel;
    private final EditorScene scene;
    private final CommandHistory history;
    
    private boolean snapToGrid = true;
    
    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            String prefabId = prefabPanel.getSelectedPrefabId();
            if (prefabId == null) return;
            
            Vector3f position;
            if (snapToGrid) {
                // Center of tile
                position = new Vector3f(tileX + 0.5f, tileY + 0.5f, 0);
            } else {
                // Exact world position from mouse
                position = getWorldMousePosition();
            }
            
            PlaceEntityCommand cmd = new PlaceEntityCommand(scene, prefabId, position);
            history.execute(cmd);
        }
    }
    
    @Override
    public void renderOverlay(Camera camera) {
        String prefabId = prefabPanel.getSelectedPrefabId();
        if (prefabId == null) return;
        
        // Draw preview sprite at cursor position
        Sprite preview = getPrefabPreviewSprite(prefabId);
        if (preview != null) {
            Vector3f pos = snapToGrid ? 
                new Vector3f(hoveredTileX + 0.5f, hoveredTileY + 0.5f, 0) :
                getWorldMousePosition();
            
            // Render semi-transparent preview
            overlayRenderer.renderSpritePreview(camera, preview, pos, 0.5f);
        }
    }
}
```

### 5.3 Entity List Panel

```java
public class EntityListPanel {
    private final EditorScene scene;
    private EditorEntity selectedEntity = null;
    
    public void render() {
        ImGui.begin("Entities");
        
        // Entity list
        for (EditorEntity entity : scene.getEntities()) {
            boolean isSelected = entity == selectedEntity;
            
            String label = entity.getName() + " (" + entity.getPrefabId() + ")";
            if (ImGui.selectable(label, isSelected)) {
                selectedEntity = entity;
                scene.setSelectedEntity(entity);
            }
            
            // Context menu
            if (ImGui.beginPopupContextItem()) {
                if (ImGui.menuItem("Delete")) {
                    history.execute(new DeleteEntityCommand(scene, entity));
                }
                if (ImGui.menuItem("Duplicate")) {
                    history.execute(new DuplicateEntityCommand(scene, entity));
                }
                if (ImGui.menuItem("Focus Camera")) {
                    editorCamera.setPosition(entity.getPosition());
                }
                ImGui.endPopup();
            }
        }
        
        ImGui.end();
    }
    
    public EditorEntity getSelectedEntity() {
        return selectedEntity;
    }
}
```

### 5.4 Entity Inspector Panel

```java
public class EntityInspectorPanel {
    private final EditorScene scene;
    
    public void render() {
        ImGui.begin("Inspector");
        
        EditorEntity entity = scene.getSelectedEntity();
        if (entity == null) {
            ImGui.text("No entity selected");
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
        
        ImGui.separator();
        
        // Position
        float[] pos = {entity.getPosition().x, entity.getPosition().y};
        if (ImGui.dragFloat2("Position", pos, 0.1f)) {
            entity.setPosition(pos[0], pos[1]);
        }
        
        // Snap to grid button
        ImGui.sameLine();
        if (ImGui.button("Snap")) {
            entity.setPosition(
                Math.round(entity.getPosition().x),
                Math.round(entity.getPosition().y)
            );
        }
        
        ImGui.separator();
        
        // Custom properties
        ImGui.text("Properties:");
        
        Map<String, Object> properties = entity.getProperties();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String strVal) {
                String[] buffer = {strVal};
                if (ImGui.inputText(key, buffer[0])) {
                    properties.put(key, buffer[0]);
                }
            } else if (value instanceof Integer intVal) {
                int[] buffer = {intVal};
                if (ImGui.inputInt(key, buffer)) {
                    properties.put(key, buffer[0]);
                }
            } else if (value instanceof Float floatVal) {
                float[] buffer = {floatVal};
                if (ImGui.inputFloat(key, buffer)) {
                    properties.put(key, buffer[0]);
                }
            } else if (value instanceof Boolean boolVal) {
                boolean[] buffer = {boolVal};
                if (ImGui.checkbox(key, buffer[0])) {
                    properties.put(key, buffer[0]);
                }
            }
        }
        
        // Add property button
        if (ImGui.button("+ Add Property")) {
            ImGui.openPopup("add_property_popup");
        }
        
        if (ImGui.beginPopup("add_property_popup")) {
            // Property type selector and name input
            ImGui.endPopup();
        }
        
        ImGui.end();
    }
}
```

### 5.5 Selection Tool

```java
public class SelectionTool implements EditorTool {
    private final EditorScene scene;
    private EditorEntity draggedEntity = null;
    private Vector2f dragOffset = new Vector2f();
    
    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            Vector3f worldPos = getWorldMousePosition();
            
            // Find entity under cursor
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
    
    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (draggedEntity != null) {
            Vector3f worldPos = getWorldMousePosition();
            draggedEntity.setPosition(
                worldPos.x + dragOffset.x,
                worldPos.y + dragOffset.y
            );
        }
    }
    
    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (draggedEntity != null) {
            // Record move for undo
            // ...
            draggedEntity = null;
        }
    }
    
    @Override
    public void renderOverlay(Camera camera) {
        EditorEntity selected = scene.getSelectedEntity();
        if (selected != null) {
            // Draw selection highlight
            overlayRenderer.renderEntityBounds(camera, selected, new Vector4f(1, 1, 0, 0.8f));
        }
    }
}
```

### 5.6 Editor Entity

```java
public class EditorEntity {
    private String prefabId;
    private String name;
    private Vector3f position;
    private Map<String, Object> properties;
    
    // Preview data (for rendering in editor)
    private Sprite previewSprite;
    private Vector2f previewSize;
    
    public EditorEntity(String prefabId, Vector3f position) {
        this.prefabId = prefabId;
        this.name = prefabId + "_" + UUID.randomUUID().toString().substring(0, 4);
        this.position = new Vector3f(position);
        this.properties = new HashMap<>();
    }
    
    public EntityData toData() {
        return new EntityData(
            prefabId,
            name,
            new float[]{position.x, position.y, position.z},
            new HashMap<>(properties)
        );
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

### 5.7 Files to Create

```
src/main/java/com/pocket/rpg/editor/
├── tools/
│   ├── EntityPlacerTool.java
│   └── SelectionTool.java
├── panels/
│   ├── PrefabBrowserPanel.java
│   ├── EntityListPanel.java
│   └── EntityInspectorPanel.java
└── scene/
    └── EditorEntity.java
```

**Deliverables:**
- Browse available prefabs
- Place entities by clicking
- Select, move, delete entities
- Edit entity properties (name, custom properties)
- Entity data saved to scene file
- Entities instantiated correctly when game loads scene

---

## Phase 6: UX Polish

**Goal:** Make editor pleasant to use.

### 6.1 Undo/Redo System

```java
public interface EditorCommand {
    void execute();
    void undo();
    String getDescription();  // For history panel / menu
}

public class CommandHistory {
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private final int maxHistory = 100;
    
    public void execute(EditorCommand command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();  // Clear redo on new action
        
        // Limit history size
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
    
    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    
    public String getUndoDescription() {
        return undoStack.isEmpty() ? null : undoStack.peek().getDescription();
    }
}

// Example commands
public class PaintTilesCommand implements EditorCommand {
    private final TilemapRenderer tilemap;
    private final Map<Long, TilemapRenderer.Tile> oldTiles = new HashMap<>();
    private final Map<Long, TilemapRenderer.Tile> newTiles = new HashMap<>();
    
    public void recordChange(int x, int y, TilemapRenderer.Tile oldTile, TilemapRenderer.Tile newTile) {
        long key = ((long)x << 32) | (y & 0xFFFFFFFFL);
        if (!oldTiles.containsKey(key)) {
            oldTiles.put(key, oldTile);
        }
        newTiles.put(key, newTile);
    }
    
    @Override
    public void execute() {
        for (var entry : newTiles.entrySet()) {
            int x = (int)(entry.getKey() >> 32);
            int y = (int)(entry.getKey() & 0xFFFFFFFFL);
            tilemap.set(x, y, entry.getValue());
        }
    }
    
    @Override
    public void undo() {
        for (var entry : oldTiles.entrySet()) {
            int x = (int)(entry.getKey() >> 32);
            int y = (int)(entry.getKey() & 0xFFFFFFFFL);
            tilemap.set(x, y, entry.getValue());
        }
    }
    
    @Override
    public String getDescription() {
        return "Paint " + newTiles.size() + " tiles";
    }
    
    public boolean hasChanges() {
        return !newTiles.isEmpty();
    }
}

public class PlaceEntityCommand implements EditorCommand {
    private final EditorScene scene;
    private final EditorEntity entity;
    
    @Override
    public void execute() {
        scene.addEntity(entity);
    }
    
    @Override
    public void undo() {
        scene.removeEntity(entity);
    }
    
    @Override
    public String getDescription() {
        return "Place " + entity.getPrefabId();
    }
}

public class MoveEntityCommand implements EditorCommand {
    private final EditorEntity entity;
    private final Vector3f oldPosition;
    private final Vector3f newPosition;
    
    // ...
}

public class DeleteEntityCommand implements EditorCommand { ... }
public class PaintCollisionCommand implements EditorCommand { ... }
```

### 6.2 Keyboard Shortcuts

```java
public class EditorShortcuts {
    private final EditorApplication editor;
    private final Map<KeyBinding, Runnable> bindings = new HashMap<>();
    
    public void setupDefaults() {
        // File operations
        bind(KeyCode.S, Modifier.CTRL, () -> editor.saveScene());
        bind(KeyCode.O, Modifier.CTRL, () -> editor.openScene());
        bind(KeyCode.N, Modifier.CTRL, () -> editor.newScene());
        
        // Edit operations
        bind(KeyCode.Z, Modifier.CTRL, () -> editor.undo());
        bind(KeyCode.Y, Modifier.CTRL, () -> editor.redo());
        bind(KeyCode.Z, Modifier.CTRL_SHIFT, () -> editor.redo());  // Alternative
        
        // Tools
        bind(KeyCode.B, () -> editor.setTool("brush"));
        bind(KeyCode.E, () -> editor.setTool("eraser"));
        bind(KeyCode.F, () -> editor.setTool("fill"));
        bind(KeyCode.R, () -> editor.setTool("rectangle"));
        bind(KeyCode.I, () -> editor.setTool("picker"));  // Eyedropper
        bind(KeyCode.V, () -> editor.setTool("selection"));
        bind(KeyCode.P, () -> editor.setTool("entity_placer"));
        
        // View
        bind(KeyCode.G, () -> editor.toggleGrid());
        bind(KeyCode.C, () -> editor.toggleCollision());
        
        // Layers
        bind(KeyCode.NUM_1, () -> editor.selectLayer(0));
        bind(KeyCode.NUM_2, () -> editor.selectLayer(1));
        bind(KeyCode.NUM_3, () -> editor.selectLayer(2));
        // ...
        
        // Selection
        bind(KeyCode.DELETE, () -> editor.deleteSelected());
        bind(KeyCode.D, Modifier.CTRL, () -> editor.duplicateSelected());
        
        // Camera
        bind(KeyCode.HOME, () -> editor.resetCamera());
    }
    
    public void handleInput() {
        for (var entry : bindings.entrySet()) {
            KeyBinding binding = entry.getKey();
            if (binding.isPressed()) {
                entry.getValue().run();
            }
        }
    }
}
```

### 6.3 Status Bar

```java
public class StatusBar {
    public void render(EditorState state) {
        // Bottom of screen status bar
        ImGui.setNextWindowPos(0, ImGui.getIO().getDisplaySizeY() - 25);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), 25);
        
        ImGui.begin("StatusBar", ImGuiWindowFlags.NoTitleBar | 
                                  ImGuiWindowFlags.NoResize | 
                                  ImGuiWindowFlags.NoMove);
        
        // Current tool
        ImGui.text("Tool: " + state.getCurrentTool().getName());
        
        ImGui.sameLine(150);
        
        // Cursor position
        ImGui.text("Tile: (" + state.getCursorTileX() + ", " + state.getCursorTileY() + ")");
        
        ImGui.sameLine(300);
        
        // World position
        ImGui.text("World: (%.2f, %.2f)", state.getCursorWorldX(), state.getCursorWorldY());
        
        ImGui.sameLine(500);
        
        // Zoom level
        ImGui.text("Zoom: %.1fx", state.getCamera().getZoom());
        
        ImGui.sameLine(600);
        
        // Active layer
        ImGui.text("Layer: " + state.getActiveLayer().getName());
        
        // Unsaved indicator (right side)
        if (state.hasUnsavedChanges()) {
            ImGui.sameLine(ImGui.getIO().getDisplaySizeX() - 100);
            ImGui.textColored(1, 0.5f, 0, 1, "* Unsaved");
        }
        
        ImGui.end();
    }
}
```

### 6.4 Menu Bar

```java
public class MenuBar {
    public void render(EditorApplication editor) {
        if (ImGui.beginMainMenuBar()) {
            
            // File menu
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New", "Ctrl+N")) {
                    editor.newScene();
                }
                if (ImGui.menuItem("Open...", "Ctrl+O")) {
                    editor.openScene();
                }
                
                // Recent files submenu
                if (ImGui.beginMenu("Recent Files")) {
                    for (String path : editor.getRecentFiles()) {
                        if (ImGui.menuItem(getFileName(path))) {
                            editor.openScene(path);
                        }
                    }
                    ImGui.endMenu();
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("Save", "Ctrl+S")) {
                    editor.saveScene();
                }
                if (ImGui.menuItem("Save As...")) {
                    editor.saveSceneAs();
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("Exit")) {
                    editor.requestClose();
                }
                
                ImGui.endMenu();
            }
            
            // Edit menu
            if (ImGui.beginMenu("Edit")) {
                String undoText = editor.canUndo() ? 
                    "Undo " + editor.getUndoDescription() : "Undo";
                if (ImGui.menuItem(undoText, "Ctrl+Z", false, editor.canUndo())) {
                    editor.undo();
                }
                
                if (ImGui.menuItem("Redo", "Ctrl+Y", false, editor.canRedo())) {
                    editor.redo();
                }
                
                ImGui.endMenu();
            }
            
            // View menu
            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("Show Grid", "G", editor.isGridVisible())) {
                    editor.toggleGrid();
                }
                if (ImGui.menuItem("Show Collision", "C", editor.isCollisionVisible())) {
                    editor.toggleCollision();
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("Reset Camera", "Home")) {
                    editor.resetCamera();
                }
                
                ImGui.endMenu();
            }
            
            // Tools menu
            if (ImGui.beginMenu("Tools")) {
                for (EditorTool tool : editor.getTools()) {
                    boolean isActive = editor.getActiveTool() == tool;
                    String shortcut = tool.getShortcut() != null ? 
                        tool.getShortcut().toString() : "";
                    
                    if (ImGui.menuItem(tool.getName(), shortcut, isActive)) {
                        editor.setActiveTool(tool);
                    }
                }
                ImGui.endMenu();
            }
            
            ImGui.endMainMenuBar();
        }
    }
}
```

### 6.5 Recent Files Management

```java
public class RecentFilesManager {
    private static final int MAX_RECENT = 10;
    private static final Path CONFIG_PATH = Path.of("editor_config.json");
    
    private List<String> recentFiles = new ArrayList<>();
    
    public void addRecentFile(String path) {
        // Remove if already exists (will re-add at top)
        recentFiles.remove(path);
        
        // Add at beginning
        recentFiles.add(0, path);
        
        // Limit size
        while (recentFiles.size() > MAX_RECENT) {
            recentFiles.remove(recentFiles.size() - 1);
        }
        
        save();
    }
    
    public List<String> getRecentFiles() {
        return Collections.unmodifiableList(recentFiles);
    }
    
    public void load() {
        if (Files.exists(CONFIG_PATH)) {
            // Load from JSON
        }
    }
    
    public void save() {
        // Save to JSON
    }
}
```

### 6.6 Unsaved Changes Dialog

```java
public class UnsavedChangesDialog {
    
    public enum Result {
        SAVE,
        DISCARD,
        CANCEL
    }
    
    private boolean isOpen = false;
    private Result result = null;
    private Runnable onComplete;
    
    public void show(Runnable onComplete) {
        this.isOpen = true;
        this.result = null;
        this.onComplete = onComplete;
    }
    
    public void render() {
        if (!isOpen) return;
        
        ImGui.openPopup("Unsaved Changes");
        
        if (ImGui.beginPopupModal("Unsaved Changes")) {
            ImGui.text("You have unsaved changes. What would you like to do?");
            
            ImGui.separator();
            
            if (ImGui.button("Save")) {
                result = Result.SAVE;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Discard")) {
                result = Result.DISCARD;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
                result = Result.CANCEL;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
        }
        
        if (result != null) {
            isOpen = false;
            onComplete.run();
        }
    }
    
    public Result getResult() {
        return result;
    }
}
```

### 6.7 Files to Create

```
src/main/java/com/pocket/rpg/editor/
├── commands/
│   ├── EditorCommand.java
│   ├── CommandHistory.java
│   ├── PaintTilesCommand.java
│   ├── PaintCollisionCommand.java
│   ├── PlaceEntityCommand.java
│   ├── MoveEntityCommand.java
│   ├── DeleteEntityCommand.java
│   └── DuplicateEntityCommand.java
├── ui/
│   ├── MenuBar.java
│   ├── StatusBar.java
│   ├── EditorShortcuts.java
│   └── UnsavedChangesDialog.java
└── config/
    └── RecentFilesManager.java
```

**Deliverables:**
- Full undo/redo for all operations
- Keyboard shortcuts for common actions
- Menu bar with all operations
- Status bar showing context info
- Recent files menu
- Unsaved changes warning on close

---

## Phase 7: Advanced Features (Future)

Lower priority, implement as needed.

### 7.1 Scene Transitions / Triggers

```java
public class TriggerEditorTool implements EditorTool {
    // Draw rectangle to define trigger bounds
    // Configure trigger type and properties
}

public class TriggerInspectorPanel {
    // Edit trigger properties:
    // - Type (SCENE_TRANSITION, EVENT, DIALOGUE, etc.)
    // - Target scene (for transitions)
    // - Spawn point name
    // - Custom properties
}
```

Scene file format already includes triggers section.

### 7.2 Copy/Paste

```java
public class ClipboardManager {
    private Object clipboardContent;  // TileSelection or List<EditorEntity>
    
    public void copyTiles(TilemapRenderer tilemap, int x1, int y1, int x2, int y2);
    public void pasteTiles(TilemapRenderer tilemap, int targetX, int targetY);
    
    public void copyEntities(List<EditorEntity> entities);
    public void pasteEntities(Vector3f position);
}
```

### 7.3 Tileset Auto-Tiling

Define rules for automatic tile selection based on neighbors (corners, edges, etc.). Significant complexity - consider using a library or defer until needed.

### 7.4 Multiple Tilesets Per Layer

Allow each layer to reference multiple tilesets. Palette panel would have tileset tabs.

### 7.5 Animated Tiles

Support for animated tiles in tilemap (cycle through tile indices). Would require TilemapRenderer changes.

### 7.6 Custom Prefab Properties Schema

Define expected properties per prefab type:
```json
{
  "NPC": {
    "properties": {
      "dialogueId": { "type": "string", "required": true },
      "facing": { "type": "enum", "values": ["UP", "DOWN", "LEFT", "RIGHT"] },
      "wanderRadius": { "type": "int", "default": 0 }
    }
  }
}
```

Inspector would auto-generate UI based on schema.

---

## Implementation Order Summary

| Phase | Status | Effort | Dependency | Description |
|-------|--------|--------|------------|-------------|
| 1. Foundation | ✅ Done | Medium | None | Editor shell, ImGui, camera |
| 2. Serialization | ✅ Done | Medium | Phase 1 | Scene file format, save/load, framebuffer |
| 3. Tilemap Painting | Pending | Large | Phase 2 | Brush tools, layers, palette |
| 4. Collision Editing | Pending | Small | Phase 3 | Collision layer painting |
| 5. Entity Placement | Pending | Medium | Phase 2 | Prefab browser, placement, inspector |
| 6. UX Polish | Pending | Medium | Phase 3, 5 | Undo/redo, shortcuts, menus |
| 7. Advanced | Pending | Large | All above | Triggers, copy/paste, auto-tile |

**Recommended implementation order:**
1. ~~Phase 1 → Phase 2~~ ✅ Complete → Phase 3 (gives you a usable tilemap editor)
2. Phase 5 (entity placement + inspector)
3. Phase 4 (collision - small effort, high value)
4. Phase 6 (polish)
5. Phase 7 (as needed)

---

## Deferred Features (Future Phases)

Features discussed but deferred for future implementation:

### Phase 7+ Additions

**JSON-based Prefabs**
- Load prefab definitions from JSON files instead of code
- Allow editing prefabs in the editor
- Hot-reload prefab changes

**Inspector Enhancements**
- `@EditorRange(min, max)` annotation for slider controls
- `@EditorHidden` to exclude from inspector (but still serialize)
- `@EditorReadOnly` for display-only fields
- `@EditorLabel("Display Name")` for custom labels
- Auto-generate UI based on property schemas per prefab type

**Component Registry with Reflection**
- Scan classpath at startup to find all Component subclasses
- Cache results for "Add Component" dropdown
- One-time cost, acceptable slowness at startup

---

## Complete File Structure

```
src/main/java/com/pocket/rpg/
├── editor/
│   ├── EditorApplication.java          # Main entry point
│   ├── EditorWindow.java               # GLFW + ImGui window
│   ├── EditorCamera.java               # Free camera controls
│   ├── EditorInputHandler.java         # Input routing
│   ├── tools/
│   │   ├── EditorTool.java             # Interface
│   │   ├── ToolManager.java
│   │   ├── TileBrushTool.java
│   │   ├── TileEraserTool.java
│   │   ├── TileFillTool.java
│   │   ├── TileRectangleTool.java
│   │   ├── TilePickerTool.java
│   │   ├── CollisionBrushTool.java
│   │   ├── EntityPlacerTool.java
│   │   ├── SelectionTool.java
│   │   └── TriggerTool.java            # Phase 7
│   ├── panels/
│   │   ├── TilesetPalettePanel.java
│   │   ├── LayerPanel.java
│   │   ├── CollisionPanel.java
│   │   ├── PrefabBrowserPanel.java
│   │   ├── EntityListPanel.java
│   │   └── EntityInspectorPanel.java
│   ├── commands/
│   │   ├── EditorCommand.java
│   │   ├── CommandHistory.java
│   │   ├── PaintTilesCommand.java
│   │   ├── PaintCollisionCommand.java
│   │   ├── PlaceEntityCommand.java
│   │   ├── MoveEntityCommand.java
│   │   ├── DeleteEntityCommand.java
│   │   └── DuplicateEntityCommand.java
│   ├── scene/
│   │   ├── EditorScene.java
│   │   ├── TilemapLayer.java
│   │   └── EditorEntity.java
│   ├── rendering/
│   │   └── EditorOverlayRenderer.java
│   ├── ui/
│   │   ├── MenuBar.java
│   │   ├── StatusBar.java
│   │   ├── EditorShortcuts.java
│   │   └── UnsavedChangesDialog.java
│   └── config/
│       └── RecentFilesManager.java
├── serialization/
│   ├── Serializer.java                 # Existing
│   ├── SceneSerializer.java            # New
│   └── scene/
│       ├── SceneData.java
│       ├── CameraData.java
│       ├── GameObjectData.java
│       ├── ComponentData.java
│       ├── TilemapComponentData.java
│       ├── ChunkData.java
│       ├── EntityData.java
│       ├── CollisionLayerData.java
│       ├── CollisionChunkData.java
│       └── TriggerData.java
├── scenes/
│   ├── Scene.java                      # Existing
│   ├── SceneLoader.java                # New - game-side loading
│   └── RuntimeScene.java               # New - loaded scene subclass
├── physics/
│   ├── CollisionMap.java               # New
│   └── CollisionChunk.java             # New
└── prefabs/
    ├── PrefabRegistry.java             # New
    └── GamePrefabs.java                # New - game-specific definitions
```

---

## Key Integration Points with Existing Code

### TilemapRenderer (Existing)
- Editor uses TilemapRenderer directly for painting
- Each layer is a GameObject with TilemapRenderer component
- Chunk structure (`TileChunk`) used for efficient storage and serialization
- `Tile` record used for individual tiles

### Scene (Existing)
- Editor creates Scene instances for preview
- RuntimeScene extends Scene for loaded scenes
- Uses existing `addGameObject`, `getRenderers()` etc.

### AssetManager (Existing)
- Editor uses AssetManager for loading tilesets, sprites
- Prefab previews loaded via AssetManager

### Camera (Existing)
- EditorCamera wraps Camera for free movement
- Reuses projection/view matrix system

### Serializer (Existing)
- SceneSerializer uses existing Gson setup
- May need custom TypeAdapters for new data types

### Input System (Existing)
- Editor has separate input handling (ImGui first)
- Can reuse KeyCode enum for shortcuts
