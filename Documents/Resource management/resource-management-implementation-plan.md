# Resource Management System - Implementation Plan for Pocket RPG Engine

## Executive Summary

This document provides a phased implementation plan for integrating a comprehensive resource management system into the Pocket RPG engine. The system will handle asset loading, caching, hot reloading, and lifecycle management for textures, sprites, shaders, and custom JSON-based asset types.

## Current State Analysis

### Existing Components
Your engine currently has:
- **Texture.java**: Basic texture loading using STB Image
- **Sprite.java**: Visual definition with texture and UV coordinates
- **SpriteSheet.java**: Manual sprite sheet parsing with caching
- **Shader.java**: Shader compilation and uniform management
- **Component-based architecture**: GameObject + Component system
- **Scene system**: Scene management with lifecycle

### Current Limitations
1. **No centralized asset management**: Each class loads resources independently
2. **Manual memory management**: No automatic unloading or reference counting
3. **No hot reloading**: Requires restart to update assets
4. **Limited asset types**: Only textures, sprites, and shaders
5. **No async loading**: Blocks main thread during load
6. **Duplicate loading**: Same texture can be loaded multiple times

## Integration Strategy

The resource management system will integrate as a **new package** (`com.pocket.rpg.resources`) that works alongside your existing rendering system. It will wrap and enhance the current Texture/Sprite/Shader classes rather than replace them.

### Package Structure
```
src/main/java/com/pocket/rpg/
├── resources/           [NEW PACKAGE]
│   ├── AssetManager.java
│   ├── ResourceHandle.java
│   ├── ResourceCache.java
│   ├── AssetLoader.java
│   ├── loaders/
│   │   ├── TextureLoader.java
│   │   ├── SpriteLoader.java
│   │   ├── ShaderLoader.java
│   │   ├── SpriteSheetLoader.java
│   │   └── GenericJSONLoader.java
│   ├── HotReloadManager.java
│   ├── BundleManager.java
│   └── types/
│       └── (Custom asset type definitions)
├── rendering/           [EXISTING - MINIMAL CHANGES]
│   ├── Texture.java     [Keep as-is, used by TextureLoader]
│   ├── Sprite.java      [Keep as-is, used by SpriteLoader]
│   ├── Shader.java      [Minor changes for reloading support]
│   └── ...
```

---

## Phase 1: Core Foundation (Week 1)

### Goal
Establish the basic resource management infrastructure without breaking existing code.

### 1.1 ResourceHandle<T> - Smart Reference System

**Purpose**: Type-safe wrapper for resources with reference counting and lifecycle tracking.

**File**: `src/main/java/com/pocket/rpg/resources/ResourceHandle.java`

**Key Features**:
```java
public class ResourceHandle<T> {
    private String resourceId;
    private AtomicInteger refCount;
    private WeakReference<T> data;
    private ResourceState state; // UNLOADED, LOADING, READY, FAILED
    private long lastAccessed;
    
    public T get();  // Returns resource or placeholder while loading
    public void retain();  // Increment refCount
    public void release();  // Decrement refCount
    public boolean isReady();
    public boolean isValid();
}
```

**Implementation Details**:
- Use `WeakReference<T>` to allow GC when no strong references exist
- `AtomicInteger` for thread-safe reference counting
- Track last access time for LRU eviction
- Support async callbacks when resource becomes ready

**Integration Point**: This is a standalone class with no dependencies.

---

### 1.2 AssetLoader Interface - Pluggable Loading System

**Purpose**: Define contract for loading different asset types.

**File**: `src/main/java/com/pocket/rpg/resources/AssetLoader.java`

**Key Features**:
```java
public interface AssetLoader<T> {
    T load(String path) throws IOException;
    void unload(T resource);
    String[] getSupportedExtensions();
    T getPlaceholder();  // Returns placeholder while loading
}
```

**Implementation Details**:
- Generic interface allows any asset type
- Placeholder support for seamless async loading
- Extension mapping for auto-detection

**Integration Point**: Interface only, no implementation yet.

---

### 1.3 ResourceCache - LRU Caching Layer

**Purpose**: Centralized cache with automatic cleanup and LRU eviction.

**File**: `src/main/java/com/pocket/rpg/resources/ResourceCache.java`

**Key Features**:
```java
public class ResourceCache {
    private Map<String, WeakReference<Object>> cache;
    private Map<String, Integer> refCounts;
    private Set<String> retained;
    private LinkedList<String> lruList;
    private int maxCacheSize = 1000;
    
    public <T> T get(String id);
    public <T> void store(String id, T resource);
    public void markRetained(String id);
    public void cleanupUnused();
    public void evictOldest();
}
```

**Implementation Details**:
- Use `WeakReference` to allow GC when appropriate
- LRU tracking with `LinkedList` for O(1) reordering
- Retained resources never evicted (UI, core assets)
- Periodic cleanup based on refCount and last access time

**Integration Point**: Used by AssetManager internally.

---

### 1.4 AssetManager - Main Facade

**Purpose**: Unified API for loading all asset types.

**File**: `src/main/java/com/pocket/rpg/resources/AssetManager.java`

**Key Features**:
```java
public class AssetManager {
    private static AssetManager instance;
    
    private ResourceCache cache;
    private Map<String, AssetLoader<?>> loaders;
    private ExecutorService loadQueue;
    
    public static void initialize();
    public static AssetManager getInstance();
    
    public <T> ResourceHandle<T> load(String path);
    public <T> ResourceHandle<T> loadAsync(String path, Consumer<ResourceHandle<T>> callback);
    public void unload(String path);
    public void retain(ResourceHandle<?> handle);
    public void release(ResourceHandle<?> handle);
    
    public void update(float deltaTime);  // Call from game loop
    public void registerLoader(String type, AssetLoader<?> loader);
}
```

**Implementation Details**:
- Singleton pattern for global access
- Thread pool for async loading
- Type-based loader registry
- Integration with game loop via `update()`

**Integration Point**: 
- Initialize in `GameWindow.initGame()` **before** any asset loading
- Call `update()` in `GameWindow.renderGame()` **before** scene update

---

### 1.5 Phase 1 Testing Strategy

**Test without breaking existing code**:
1. Initialize AssetManager but don't use it yet
2. Existing Texture/Sprite loading continues to work
3. Add simple test scene that loads one texture via AssetManager
4. Verify reference counting and caching work

**Success Criteria**:
- AssetManager can load a texture
- Same texture loaded twice returns cached instance
- Reference counting works correctly
- Existing code still functions normally

---

## Phase 2: Asset Loaders (Week 2)

### Goal
Implement concrete loaders for existing asset types.

### 2.1 TextureLoader - Texture Asset Loading

**Purpose**: Load textures through AssetManager while reusing existing Texture.java.

**File**: `src/main/java/com/pocket/rpg/resources/loaders/TextureLoader.java`

**Key Features**:
```java
public class TextureLoader implements AssetLoader<Texture> {
    @Override
    public Texture load(String path) {
        // Use existing Texture constructor
        return new Texture(path);
    }
    
    @Override
    public void unload(Texture texture) {
        texture.destroy();
    }
    
    @Override
    public Texture getPlaceholder() {
        // Return 1x1 magenta texture
        return createPlaceholderTexture();
    }
}
```

**Integration Details**:
- Register in AssetManager: `registerLoader("texture", new TextureLoader())`
- Map extensions: `.png`, `.jpg`, `.bmp`
- No changes to Texture.java required

**Migration Path for Existing Code**:
```java
// OLD CODE (still works):
Texture texture = new Texture("player.png");

// NEW CODE (preferred):
ResourceHandle<Texture> textureHandle = AssetManager.load("player.png");
Texture texture = textureHandle.get();
```

---

### 2.2 ShaderLoader - Shader Asset Loading

**Purpose**: Load shaders through AssetManager with hot reload support.

**File**: `src/main/java/com/pocket/rpg/resources/loaders/ShaderLoader.java`

**Key Features**:
```java
public class ShaderLoader implements AssetLoader<Shader> {
    @Override
    public Shader load(String path) {
        Shader shader = new Shader(path);
        shader.compileAndLink();
        return shader;
    }
    
    @Override
    public void unload(Shader shader) {
        shader.delete();
    }
}
```

**Required Changes to Shader.java**:
```java
// Add to Shader.java:
public void reload() {
    // Preserve current program ID for hot swapping
    int oldProgram = shaderProgramId;
    
    // Recompile
    compileAndLink();
    
    // Delete old program
    glDeleteProgram(oldProgram);
    
    // Clear uniform cache
    uniformLocationCache.clear();
}
```

---

### 2.3 SpriteLoader - Sprite from Texture

**Purpose**: Create sprites with automatic texture loading.

**File**: `src/main/java/com/pocket/rpg/resources/loaders/SpriteLoader.java`

**Key Features**:
```java
public class SpriteLoader implements AssetLoader<Sprite> {
    @Override
    public Sprite load(String path) {
        // Auto-load texture
        ResourceHandle<Texture> textureHandle = AssetManager.load(path);
        return new Sprite(textureHandle.get());
    }
}
```

**Usage**:
```java
// Loads texture automatically:
ResourceHandle<Sprite> spriteHandle = AssetManager.load("player.png");
```

---

### 2.4 SpriteSheetLoader - JSON-based Sprite Sheets

**Purpose**: Load sprite sheet definitions from JSON with automatic texture loading.

**File**: `src/main/java/com/pocket/rpg/resources/loaders/SpriteSheetLoader.java`

**Example JSON** (`player.spritesheet.json`):
```json
{
  "texture": "player.png",
  "spriteWidth": 32,
  "spriteHeight": 32,
  "spacingX": 2,
  "spacingY": 2,
  "offsetX": 0,
  "offsetY": 0,
  "frames": {
    "idle": 0,
    "walk1": 1,
    "walk2": 2,
    "attack": 3
  }
}
```

**Implementation**:
```java
public class SpriteSheetLoader implements AssetLoader<SpriteSheetData> {
    @Override
    public SpriteSheetData load(String path) {
        JsonObject json = parseJSON(path);
        
        // Load texture through AssetManager
        String texturePath = json.get("texture").getAsString();
        ResourceHandle<Texture> texture = AssetManager.load(texturePath);
        
        // Create SpriteSheet
        SpriteSheet sheet = new SpriteSheet(
            texture.get(),
            json.get("spriteWidth").getAsInt(),
            json.get("spriteHeight").getAsInt(),
            json.get("spacingX").getAsInt(),
            json.get("spacingY").getAsInt(),
            json.get("offsetX").getAsInt(),
            json.get("offsetY").getAsInt()
        );
        
        // Wrap in data class with named frames
        return new SpriteSheetData(sheet, json);
    }
}
```

**New Class Needed**: `SpriteSheetData.java`
```java
public class SpriteSheetData {
    private SpriteSheet sheet;
    private Map<String, Integer> namedFrames;
    
    public Sprite getSprite(String name) {
        Integer frameIndex = namedFrames.get(name);
        return sheet.getSprite(frameIndex);
    }
}
```

---

### 2.5 GenericJSONLoader - Extensible JSON Loading

**Purpose**: Universal loader for custom JSON asset types.

**File**: `src/main/java/com/pocket/rpg/resources/loaders/GenericJSONLoader.java`

**Key Features**:
```java
public class GenericJSONLoader<T> implements AssetLoader<T> {
    private Function<JsonObject, T> constructor;
    private String[] extensions;
    
    public GenericJSONLoader(Function<JsonObject, T> constructor, String[] extensions) {
        this.constructor = constructor;
        this.extensions = extensions;
    }
    
    @Override
    public T load(String path) {
        JsonObject json = parseJSON(path);
        return constructor.apply(json);
    }
}
```

**Usage Example** (Animation system):
```java
// Register animation loader
AssetManager.registerLoader("animation", 
    new GenericJSONLoader<>(
        json -> Animation.fromJSON(json),
        new String[]{".anim", ".animation.json"}
    )
);

// Load animation
ResourceHandle<Animation> anim = AssetManager.load("player_walk.anim");
```

---

### 2.6 Phase 2 Testing Strategy

**Gradual Migration**:
1. Add loaders one at a time
2. Test each loader independently
3. Create test scene that uses new loading methods
4. Verify existing code still works

**Success Criteria**:
- All asset types can load through AssetManager
- Texture sharing works (same texture = same instance)
- Reference counting prevents premature unloading
- Memory usage is stable

---

## Phase 3: Hot Reload System (Week 3)

### Goal
Enable live asset editing without engine restart.

### 3.1 FileWatcher - File System Monitoring

**Purpose**: Detect file changes in real-time.

**File**: `src/main/java/com/pocket/rpg/resources/FileWatcher.java`

**Key Features**:
```java
public class FileWatcher {
    private WatchService watchService;
    private Map<Path, WatchKey> watchedPaths;
    private Queue<String> modifiedFiles;
    
    public void start();
    public void addPath(String path);
    public void poll();  // Call from AssetManager.update()
    public Queue<String> getModifiedFiles();
}
```

**Implementation Details**:
- Use Java NIO `WatchService` for efficient file monitoring
- Watch directories, not individual files
- Queue changes for batch processing
- Filter by registered extensions

---

### 3.2 HotReloadManager - Resource Reloading

**Purpose**: Coordinate asset reloading when files change.

**File**: `src/main/java/com/pocket/rpg/resources/HotReloadManager.java`

**Key Features**:
```java
public class HotReloadManager {
    private FileWatcher fileWatcher;
    private Map<String, Set<ResourceHandle<?>>> pathToHandles;
    
    public void watch(String path, ResourceHandle<?> handle);
    public void checkForChanges();
    public void reloadResource(String path);
}
```

**Reload Process**:
1. FileWatcher detects change
2. Get all handles pointing to that file
3. Reload asset using appropriate loader
4. Atomically swap data in handles
5. Notify listeners (optional)

**Integration with AssetManager**:
```java
// In AssetManager.update():
public void update(float deltaTime) {
    processLoadQueue();
    cache.cleanupUnused();
    hotReloadManager.checkForChanges();  // NEW
}
```

---

### 3.3 Hot Reload Support in Loaders

**TextureLoader Hot Reload**:
```java
// Texture needs no changes - reload creates new GL texture
public void reload(Texture oldTexture, String path) {
    Texture newTexture = load(path);
    
    // Copy new data into old texture's ID (advanced)
    // OR: Just create new texture, old one gets GC'd
}
```

**ShaderLoader Hot Reload**:
```java
// Use Shader.reload() method added in Phase 2
public void reload(Shader shader, String path) {
    shader.reload();  // Already implemented
}
```

**SpriteSheetLoader Hot Reload**:
```java
// Reload texture, rebuild sprite sheet
public void reload(SpriteSheetData oldData, String path) {
    SpriteSheetData newData = load(path);
    
    // Swap internal data
    oldData.updateFrom(newData);
}
```

---

### 3.4 Phase 3 Testing Strategy

**Hot Reload Testing**:
1. Load a texture, display it in game
2. Edit texture file in external tool (e.g., GIMP)
3. Save file
4. Verify game updates within 1 second
5. Test with shader (edit shader code, see visual change)

**Success Criteria**:
- File changes detected within 500ms
- Assets reload without flickering
- No memory leaks from old resources
- Game continues running smoothly

---

## Phase 4: Advanced Features (Week 4)

### Goal
Add async loading, resource bundles, and editor support.

### 4.1 Async Loading with Progress

**Update AssetManager**:
```java
public class AssetManager {
    private ExecutorService asyncLoadPool;
    
    public <T> CompletableFuture<ResourceHandle<T>> loadAsync(String path) {
        return CompletableFuture.supplyAsync(() -> load(path), asyncLoadPool);
    }
    
    public LoadingProgress loadBatch(List<String> paths) {
        LoadingProgress progress = new LoadingProgress(paths.size());
        
        for (String path : paths) {
            loadAsync(path).thenAccept(handle -> {
                progress.increment();
            });
        }
        
        return progress;
    }
}

public class LoadingProgress {
    private AtomicInteger loaded;
    private int total;
    
    public float getProgress() {
        return (float) loaded.get() / total;
    }
}
```

**Usage in Loading Screen**:
```java
public class LoadingScene extends Scene {
    private LoadingProgress progress;
    
    @Override
    public void start() {
        List<String> assets = Arrays.asList(
            "player.png",
            "enemies.spritesheet.json",
            "level1.tmx",
            "music/theme.ogg"
        );
        
        progress = AssetManager.getInstance().loadBatch(assets);
    }
    
    @Override
    public void update(float dt) {
        if (progress.isComplete()) {
            SceneManager.loadScene("GameScene");
        }
        
        // Render progress bar
        renderProgressBar(progress.getProgress());
    }
}
```

---

### 4.2 Resource Bundles - Archive Loading

**Purpose**: Package multiple assets into single file for distribution.

**File**: `src/main/java/com/pocket/rpg/resources/BundleManager.java`

**Bundle Format**:
```
[HEADER]
- Magic: "POCKETRPG"
- Version: 1
- Asset count: N

[INDEX]
- Asset 1: path, offset, compressed size, uncompressed size
- Asset 2: ...
- Asset N: ...

[DATA]
- Asset 1 compressed data
- Asset 2 compressed data
- Asset N compressed data
```

**Implementation**:
```java
public class BundleManager {
    private Map<String, Bundle> loadedBundles;
    private Map<String, BundleLocation> assetLocations;
    
    public void loadBundle(String bundlePath);
    public byte[] getAssetData(String assetPath);
    public void unloadBundle(String bundlePath);
}

public class Bundle {
    private RandomAccessFile file;
    private Map<String, AssetEntry> index;
    
    public byte[] readAsset(String path);
}
```

**Integration with AssetManager**:
```java
// In AssetLoader.load():
public T load(String path) {
    // Check bundles first
    byte[] data = bundleManager.getAssetData(path);
    if (data != null) {
        return loadFromBytes(data);
    }
    
    // Fall back to file system
    return loadFromFile(path);
}
```

---

### 4.3 Resource Pools - Object Reuse

**Purpose**: Reuse expensive objects (particles, projectiles).

**File**: `src/main/java/com/pocket/rpg/resources/ResourcePool.java`

**Implementation**:
```java
public class ResourcePool<T> {
    private Stack<T> pool;
    private Supplier<T> factory;
    private Consumer<T> reset;
    private int maxSize;
    private int activeCount;
    
    public T acquire() {
        if (pool.isEmpty()) {
            return factory.get();
        }
        return pool.pop();
    }
    
    public void release(T object) {
        reset.accept(object);
        
        if (pool.size() < maxSize) {
            pool.push(object);
        }
    }
    
    public void prewarm(int count) {
        for (int i = 0; i < count; i++) {
            pool.push(factory.get());
        }
    }
}
```

**Usage Example**:
```java
// Particle system
public class ParticleSystem {
    private ResourcePool<Particle> particlePool;
    
    public ParticleSystem() {
        particlePool = new ResourcePool<>(
            () -> new Particle(),  // Factory
            p -> p.reset(),        // Reset
            1000                   // Max size
        );
        
        particlePool.prewarm(100);  // Pre-allocate
    }
    
    public void emit() {
        Particle p = particlePool.acquire();
        p.setPosition(emitterPos);
        activeParticles.add(p);
    }
    
    public void update(float dt) {
        for (Particle p : activeParticles) {
            p.update(dt);
            
            if (p.isDead()) {
                particlePool.release(p);
                activeParticles.remove(p);
            }
        }
    }
}
```

---

### 4.4 Phase 4 Testing Strategy

**Performance Testing**:
1. Load 100+ assets asynchronously
2. Measure loading time vs synchronous
3. Test bundle loading vs individual files
4. Profile memory usage with pools

**Success Criteria**:
- Async loading doesn't block main thread
- Loading screen shows accurate progress
- Bundles load 2-3x faster than individual files
- Pools reduce GC pressure significantly

---

## Phase 5: Integration & Migration (Week 5)

### Goal
Update existing code to use AssetManager, provide migration tools.

### 5.1 Update Component Classes

**SpriteRenderer Migration**:
```java
public class SpriteRenderer extends Component {
    private ResourceHandle<Sprite> spriteHandle;  // NEW
    private Sprite sprite;  // Keep for compatibility
    
    // NEW CONSTRUCTOR (preferred)
    public SpriteRenderer(ResourceHandle<Sprite> handle) {
        this.spriteHandle = handle;
        this.sprite = handle.get();
    }
    
    // OLD CONSTRUCTOR (deprecated but works)
    public SpriteRenderer(Sprite sprite) {
        this.sprite = sprite;
        this.spriteHandle = null;
    }
    
    @Override
    public void onDestroy() {
        if (spriteHandle != null) {
            spriteHandle.release();  // Decrement refCount
        }
    }
}
```

---

### 5.2 Update GameWindow Initialization

**GameWindow.initGame() Changes**:
```java
@Override
protected void initGame() {
    System.out.println("Initializing game systems...");
    
    // 1. Initialize AssetManager FIRST
    AssetManager.initialize();
    AssetManager manager = AssetManager.getInstance();
    
    // 2. Register loaders
    manager.registerLoader("texture", new TextureLoader());
    manager.registerLoader("sprite", new SpriteLoader());
    manager.registerLoader("shader", new ShaderLoader());
    manager.registerLoader("spritesheet", new SpriteSheetLoader());
    
    // 3. Enable hot reload (dev only)
    if (isDevelopmentMode()) {
        manager.enableHotReload();
    }
    
    // 4. Load core assets (retained)
    ResourceHandle<Texture> uiAtlas = manager.load("ui/atlas.png");
    manager.retain(uiAtlas);  // Never unload
    
    // 5. Initialize other systems
    CameraSystem.initialize(config.getGameWidth(), config.getGameHeight());
    renderer = new Renderer();
    // ... rest of initialization
}
```

**GameWindow.renderGame() Changes**:
```java
@Override
protected void renderGame(float deltaTime) {
    // Update AssetManager (hot reload, async loads, cleanup)
    AssetManager.getInstance().update(deltaTime);
    
    // Update scene
    sceneManager.update(deltaTime);
    
    // Render
    if (sceneManager.getCurrentScene() != null) {
        renderPipeline.render(sceneManager.getCurrentScene());
    }
}
```

**GameWindow.destroyGame() Changes**:
```java
@Override
protected void destroyGame() {
    System.out.println("Destroying game systems...");
    
    if (sceneManager != null) {
        sceneManager.destroy();
    }
    
    // Destroy AssetManager LAST
    AssetManager.destroy();
    
    // ... rest of cleanup
}
```

---

### 5.3 Scene Asset Management

**Scene-based Resource Loading**:
```java
public abstract class Scene {
    private List<ResourceHandle<?>> sceneAssets = new ArrayList<>();
    
    protected <T> ResourceHandle<T> loadAsset(String path) {
        ResourceHandle<T> handle = AssetManager.load(path);
        sceneAssets.add(handle);
        return handle;
    }
    
    public void unload() {
        // Release all assets when scene unloads
        for (ResourceHandle<?> handle : sceneAssets) {
            handle.release();
        }
        sceneAssets.clear();
    }
}
```

**Example Usage**:
```java
public class GameScene extends Scene {
    private ResourceHandle<SpriteSheetData> playerSheet;
    private ResourceHandle<Texture> background;
    
    @Override
    public void load() {
        // These will auto-unload when scene changes
        playerSheet = loadAsset("player.spritesheet.json");
        background = loadAsset("backgrounds/forest.png");
        
        // Create player with sprites from sheet
        Sprite idleSprite = playerSheet.get().getSprite("idle");
        GameObject player = new GameObject("Player");
        player.addComponent(new SpriteRenderer(idleSprite));
        addGameObject(player);
    }
}
```

---

### 5.4 Migration Helper Tools

**Asset Audit Tool**:
```java
public class AssetAudit {
    public static void printAssetReport() {
        AssetManager manager = AssetManager.getInstance();
        
        System.out.println("=== ASSET MANAGER REPORT ===");
        System.out.println("Total cached assets: " + manager.getCacheSize());
        System.out.println("Memory usage: " + manager.getMemoryUsage() + " MB");
        System.out.println("\nTop 10 assets by memory:");
        
        List<AssetInfo> assets = manager.getAllAssets();
        assets.sort((a, b) -> Long.compare(b.memorySize, a.memorySize));
        
        for (int i = 0; i < Math.min(10, assets.size()); i++) {
            AssetInfo asset = assets.get(i);
            System.out.printf("  %s (%d KB, refCount=%d)%n",
                asset.path, asset.memorySize / 1024, asset.refCount);
        }
    }
}
```

**Automated Migration Script** (conceptual):
```java
// Tool to help migrate old code
public class CodeMigrationHelper {
    public static void suggestMigrations(File sourceFile) {
        String code = readFile(sourceFile);
        
        // Find old-style texture loading
        if (code.contains("new Texture(")) {
            System.out.println("Found old texture loading in " + sourceFile);
            System.out.println("Consider changing to:");
            System.out.println("  ResourceHandle<Texture> handle = AssetManager.load(path);");
        }
        
        // Find SpriteRenderer without handles
        if (code.contains("new SpriteRenderer(new Sprite(")) {
            System.out.println("Found old sprite renderer pattern in " + sourceFile);
            System.out.println("Consider loading sprite via AssetManager");
        }
    }
}
```

---

### 5.5 Phase 5 Testing Strategy

**Regression Testing**:
1. Run all existing test scenes
2. Verify no visual differences
3. Check for memory leaks (load/unload scenes repeatedly)
4. Profile performance (should be same or better)

**Success Criteria**:
- All existing scenes work with new system
- No memory leaks after 1000 scene transitions
- Hot reload works for all asset types
- Performance is equal or better than before

---

## Implementation Timeline

### Week 1: Core Foundation
- **Day 1-2**: ResourceHandle + ResourceCache
- **Day 3-4**: AssetManager + AssetLoader interface
- **Day 5**: Integration and testing

### Week 2: Asset Loaders
- **Day 1**: TextureLoader
- **Day 2**: ShaderLoader + Shader.reload()
- **Day 3**: SpriteLoader + SpriteSheetLoader
- **Day 4**: GenericJSONLoader
- **Day 5**: Testing and refinement

### Week 3: Hot Reload
- **Day 1-2**: FileWatcher
- **Day 3-4**: HotReloadManager
- **Day 5**: Integration testing

### Week 4: Advanced Features
- **Day 1-2**: Async loading
- **Day 3**: Resource bundles
- **Day 4**: Resource pools
- **Day 5**: Performance testing

### Week 5: Integration & Migration
- **Day 1-2**: Update component classes
- **Day 3**: Update GameWindow and Scene
- **Day 4**: Migration tools
- **Day 5**: Final testing and documentation

---

## Critical Integration Points

### 1. Initialization Order (CRITICAL)
```
GameWindow.initGame():
  1. AssetManager.initialize()       ← MUST BE FIRST
  2. Register all loaders
  3. CameraSystem.initialize()
  4. Renderer.init()
  5. Scene initialization
```

### 2. Update Loop Integration
```
GameWindow.renderGame():
  1. AssetManager.update()           ← BEFORE scene update
  2. SceneManager.update()
  3. RenderPipeline.render()
```

### 3. Cleanup Order
```
GameWindow.destroyGame():
  1. SceneManager.destroy()
  2. Renderer.destroy()
  3. AssetManager.destroy()          ← MUST BE LAST
```

---

## Migration Strategy for Existing Code

### Phase A: Parallel Systems (Weeks 1-3)
- New AssetManager exists alongside old code
- Both systems work simultaneously
- No breaking changes

### Phase B: Gradual Adoption (Week 4-5)
- New features use AssetManager only
- Old code gradually migrated
- Deprecated warnings for old patterns

### Phase C: Full Migration (Week 6+)
- All code uses AssetManager
- Old direct loading removed
- Full benefits realized

---

## Risk Mitigation

### Risk 1: Breaking Changes
**Mitigation**: Keep old constructors, add new ones. Support both patterns during migration.

### Risk 2: Performance Regression
**Mitigation**: Profile at each phase. Cache should improve performance, not hurt it.

### Risk 3: Memory Leaks
**Mitigation**: Extensive testing with scene transitions. Use WeakReferences appropriately.

### Risk 4: Complex Asset Types
**Mitigation**: Start with simple types (Texture, Sprite), add complex types after system is proven.

---

## Success Metrics

### Performance
- Asset loading: 0-20% slower (acceptable due to caching benefits)
- Memory usage: 10-30% reduction (less duplication)
- Cache hit rate: >80% in typical gameplay

### Developer Experience
- Hot reload: <500ms from file save to visual update
- Asset loading: 1 line of code vs 3-5 previously
- Scene load time: 2-5x faster (with bundles)

### Stability
- Zero crashes from asset system after 1 week of testing
- No memory leaks after 10,000 asset load/unload cycles
- 100% of existing scenes work without modification

---

## Future Enhancements (Post-Launch)

### 1. Asset Browser/Editor
- Visual tool to browse all loaded assets
- Live editing of asset properties
- Memory usage visualization

### 2. Advanced Caching
- Disk cache for large assets
- Predictive loading based on gameplay
- Streaming for very large worlds

### 3. Network Loading
- Download assets from server
- Progressive download during gameplay
- Asset versioning and updates

### 4. Asset Compilation
- Pre-process assets at build time
- Optimize texture formats
- Generate mipmaps offline

---

## Conclusion

This implementation plan provides a structured approach to integrating a production-quality resource management system into your Pocket RPG engine. The phased approach ensures:

1. **No breaking changes** during development
2. **Gradual testing** at each phase
3. **Clear integration points** with existing code
4. **Measurable success criteria** at each step

The system will transform asset management from manual, error-prone loading to an automated, efficient, hot-reload-enabled system that scales to production needs.

**Start with Phase 1** and build confidence before moving forward. Each phase is designed to be independently valuable, so you can stop at any point and still have a working system.
