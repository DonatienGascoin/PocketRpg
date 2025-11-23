# Resource Management System - Architecture Overview

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Game Application                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AssetManager (Facade)                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  loadTexture() loadSprite() loadSound() loadShader()       │ │
│  │  unload() retain() release() getLoadingProgress()          │ │
│  └────────────────────────────────────────────────────────────┘ │
└───┬─────────────┬──────────────┬──────────────┬────────────┬───┘
    │             │              │              │            │
    ▼             ▼              ▼              ▼            ▼
┌────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│Resource│  │  Asset   │  │  Hot     │  │ Bundle   │  │Resource  │
│ Cache  │  │ Loaders  │  │ Reload   │  │ Manager  │  │  Pool    │
└────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
    │             │              │              │            │
    ▼             ▼              ▼              ▼            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        File System Layer                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐               │
│  │   Local    │  │   Bundle   │  │ File Watch │               │
│  │   Files    │  │   Archive  │  │   Service  │               │
│  └────────────┘  └────────────┘  └────────────┘               │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Resource Handle System

```
┌─────────────────────────────────────────────────────┐
│                  ResourceHandle<T>                   │
├─────────────────────────────────────────────────────┤
│ - resourceId: string                                 │
│ - refCount: int (atomic)                            │
│ - data: T (weak reference)                          │
│ - state: enum (Loading/Ready/Failed)                │
│ - lastAccessed: timestamp                           │
├─────────────────────────────────────────────────────┤
│ + get(): T                                          │
│ + retain(): void                                    │
│ + release(): void                                   │
│ + isValid(): bool                                   │
└─────────────────────────────────────────────────────┘
```

### 2. Resource Cache Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    ResourceCache                          │
├──────────────────────────────────────────────────────────┤
│ Resources stored with:                                    │
│  - Primary: HashMap<ResourceId, WeakRef<Resource>>       │
│  - LRU tracking: LinkedList<ResourceId>                  │
│  - Reference counting: HashMap<ResourceId, AtomicInt>    │
│                                                           │
│ Cache Strategy:                                           │
│  1. Check cache → Return if exists                       │
│  2. Load from disk/bundle → Store in cache               │
│  3. Track access time for LRU eviction                   │
│  4. Auto-cleanup when refCount == 0 && not retained      │
└──────────────────────────────────────────────────────────┘
```

## Pseudocode Implementation

### Core AssetManager

```pseudocode
class AssetManager {
    private:
        ResourceCache cache
        Map<string, AssetLoader> loaders  // Type -> Loader
        HotReloadManager hotReload
        BundleManager bundles
        ResourcePool pool
        AsyncTaskQueue loadQueue
        
    public:
        static AssetManager& getInstance()
        
        // Main API
        function loadTexture(path: string) -> Handle<Texture> {
            return load<Texture>(path, "texture")
        }
        
        function loadSprite(path: string) -> Handle<Sprite> {
            return load<Sprite>(path, "sprite")
        }
        
        function loadSound(path: string) -> Handle<Sound> {
            return load<Sound>(path, "sound")
        }
        
        // Generic load function
        function load<T>(path: string, type: string) -> Handle<T> {
            resourceId = generateId(path, type)
            
            // Check cache first
            if cached = cache.get(resourceId) {
                cached.retain()
                return cached
            }
            
            // Create placeholder handle
            handle = Handle<T>(resourceId, state=LOADING)
            cache.store(resourceId, handle)
            
            // Queue async load
            loadQueue.enqueue(() => {
                loader = loaders[type]
                data = loader.load(path)
                handle.setData(data)
                handle.setState(READY)
                notifyLoadComplete(handle)
            })
            
            return handle
        }
        
        function loadAsync<T>(path: string, 
                             callback: function(Handle<T>)) {
            handle = load<T>(path)
            
            if handle.isReady() {
                callback(handle)
            } else {
                registerCallback(handle, callback)
            }
        }
        
        function unload(path: string) {
            resourceId = generateId(path)
            cache.remove(resourceId)
        }
        
        function retain(handle: Handle) {
            handle.retain()
            cache.markRetained(handle.id)
        }
        
        function release(handle: Handle) {
            handle.release()
            if handle.refCount == 0 && !handle.isRetained {
                cache.scheduleEviction(handle.id)
            }
        }
        
        function update(deltaTime: float) {
            processLoadQueue()
            cache.cleanupUnused()
            hotReload.checkForChanges()
        }
}
```

### Resource Handle Implementation

```pseudocode
class ResourceHandle<T> {
    private:
        resourceId: string
        refCount: AtomicInt = 0
        data: WeakReference<T>
        state: ResourceState = UNLOADED
        retained: bool = false
        lastAccessed: timestamp
        callbacks: List<Function>
        
    public:
        function get() -> T {
            if state == LOADING {
                // Wait or return placeholder
                return getPlaceholder()
            }
            
            if state == FAILED {
                throw ResourceLoadException(resourceId)
            }
            
            lastAccessed = now()
            return data.lock()  // Convert weak to strong ref
        }
        
        function retain() {
            refCount.increment()
        }
        
        function release() {
            refCount.decrement()
            if refCount == 0 && !retained {
                notifyCanEvict(this)
            }
        }
        
        function isValid() -> bool {
            return state == READY && !data.expired()
        }
        
        function isReady() -> bool {
            return state == READY
        }
        
        function setData(newData: T) {
            data = WeakReference(newData)
            state = READY
            executeCallbacks()
        }
        
        function setState(newState: ResourceState) {
            state = newState
        }
}
```

### Resource Cache with LRU

```pseudocode
class ResourceCache {
    private:
        cache: HashMap<ResourceId, WeakRef<Resource>>
        refCounts: HashMap<ResourceId, AtomicInt>
        retained: Set<ResourceId>
        lruList: LinkedList<ResourceId>
        maxCacheSize: int = 1000
        
    public:
        function get(id: ResourceId) -> Resource? {
            if cache.contains(id) {
                weakRef = cache[id]
                if resource = weakRef.lock() {
                    // Move to front of LRU
                    lruList.moveToFront(id)
                    return resource
                } else {
                    // Resource was deleted, clean up
                    cache.remove(id)
                    return null
                }
            }
            return null
        }
        
        function store(id: ResourceId, resource: Resource) {
            cache[id] = WeakRef(resource)
            lruList.addToFront(id)
            refCounts[id] = 0
            
            // Evict if over capacity
            if lruList.size() > maxCacheSize {
                evictOldest()
            }
        }
        
        function markRetained(id: ResourceId) {
            retained.add(id)
        }
        
        function cleanupUnused() {
            toRemove = []
            
            for (id, weakRef) in cache {
                // Skip retained resources
                if retained.contains(id) {
                    continue
                }
                
                // Check if weak ref is dead
                if !weakRef.isValid() {
                    toRemove.add(id)
                    continue
                }
                
                // Check ref count and last access time
                if refCounts[id] == 0 {
                    timeSinceAccess = now() - getLastAccess(id)
                    if timeSinceAccess > EVICTION_THRESHOLD {
                        toRemove.add(id)
                    }
                }
            }
            
            for id in toRemove {
                remove(id)
            }
        }
        
        function evictOldest() {
            while lruList.size() > maxCacheSize {
                oldest = lruList.back()
                
                // Don't evict retained or actively used
                if retained.contains(oldest) || refCounts[oldest] > 0 {
                    break
                }
                
                remove(oldest)
            }
        }
        
        function remove(id: ResourceId) {
            cache.remove(id)
            refCounts.remove(id)
            lruList.remove(id)
            retained.remove(id)
        }
}
```

### Asset Loaders

```pseudocode
interface AssetLoader {
    function load(path: string) -> Resource
    function unload(resource: Resource)
    function getSupportedExtensions() -> List<string>
}

class TextureLoader implements AssetLoader {
    function load(path: string) -> Texture {
        // Find file (check bundles first, then disk)
        fileData = findAndReadFile(path)
        
        // Decode image format
        imageData = decodeImage(fileData)
        
        // Upload to GPU
        textureId = createGPUTexture(imageData)
        
        // Create texture object
        texture = new Texture(textureId, imageData.width, imageData.height)
        
        return texture
    }
    
    function unload(texture: Texture) {
        deleteGPUTexture(texture.id)
    }
}

class SpriteLoader implements AssetLoader {
    function load(path: string) -> Sprite {
        data = parseJSON(readFile(path))
        
        // Load texture if not already loaded
        texture = AssetManager.loadTexture(data.texturePath)
        
        sprite = new Sprite(
            texture,
            data.sourceRect,
            data.pivot,
            data.pixelsPerUnit
        )
        
        return sprite
    }
}

class ShaderLoader implements AssetLoader {
    function load(path: string) -> Shader {
        vertexSource = readFile(path + ".vert")
        fragmentSource = readFile(path + ".frag")
        
        // Compile shaders
        program = compileShaderProgram(vertexSource, fragmentSource)
        
        // Extract uniforms
        uniforms = extractUniforms(program)
        
        return new Shader(program, uniforms)
    }
}
```

### Hot Reload System

```pseudocode
class HotReloadManager {
    private:
        fileWatcher: FileSystemWatcher
        watchedPaths: Set<string>
        modifiedResources: Queue<ResourceId>
        
    public:
        function initialize() {
            fileWatcher.start()
            fileWatcher.onFileChanged = handleFileChanged
        }
        
        function watch(path: string) {
            watchedPaths.add(path)
            fileWatcher.addPath(path)
        }
        
        function handleFileChanged(path: string) {
            resourceId = pathToResourceId(path)
            modifiedResources.enqueue(resourceId)
        }
        
        function checkForChanges() {
            while !modifiedResources.isEmpty() {
                resourceId = modifiedResources.dequeue()
                reloadResource(resourceId)
            }
        }
        
        function reloadResource(resourceId: ResourceId) {
            // Get current handle
            handle = cache.get(resourceId)
            if !handle {
                return  // No longer in use
            }
            
            // Reload data
            path = resourceIdToPath(resourceId)
            type = resourceIdToType(resourceId)
            loader = getLoader(type)
            
            try {
                newData = loader.load(path)
                
                // Swap data atomically
                oldData = handle.getData()
                handle.setData(newData)
                
                // Clean up old data
                loader.unload(oldData)
                
                // Notify listeners
                notifyReloadComplete(resourceId)
                
                log("Hot reloaded: " + path)
            } catch error {
                log("Hot reload failed: " + path + " - " + error)
                // Keep old data
            }
        }
}
```

### Bundle Manager

```pseudocode
class BundleManager {
    private:
        loadedBundles: Map<string, Bundle>
        bundleIndex: Map<ResourceId, BundleLocation>
        
    public:
        function loadBundle(bundlePath: string) {
            if loadedBundles.contains(bundlePath) {
                return loadedBundles[bundlePath]
            }
            
            // Read bundle file
            bundleData = readFile(bundlePath)
            
            // Parse bundle header
            bundle = parseBundle(bundleData)
            
            // Build index of resources in bundle
            for resource in bundle.resources {
                bundleIndex[resource.id] = BundleLocation(
                    bundle: bundlePath,
                    offset: resource.offset,
                    size: resource.size
                )
            }
            
            loadedBundles[bundlePath] = bundle
            return bundle
        }
        
        function getResourceData(resourceId: ResourceId) -> bytes? {
            if !bundleIndex.contains(resourceId) {
                return null
            }
            
            location = bundleIndex[resourceId]
            bundle = loadedBundles[location.bundle]
            
            // Extract and decompress
            compressedData = bundle.data.slice(
                location.offset, 
                location.offset + location.size
            )
            
            return decompress(compressedData)
        }
        
        function unloadBundle(bundlePath: string) {
            if bundle = loadedBundles[bundlePath] {
                // Remove from index
                for resource in bundle.resources {
                    bundleIndex.remove(resource.id)
                }
                
                loadedBundles.remove(bundlePath)
            }
        }
}

// Bundle file format:
struct Bundle {
    header: {
        magic: "BUNDLE"
        version: int
        compression: enum
        encrypted: bool
        resourceCount: int
    }
    
    index: Array<ResourceEntry> {
        id: string (hash)
        type: string
        offset: int
        compressedSize: int
        uncompressedSize: int
    }
    
    data: bytes  // All compressed resources
}
```

### Resource Pool

```pseudocode
class ResourcePool<T> {
    private:
        pool: Stack<T>
        factory: Function() -> T
        maxSize: int
        activeCount: int = 0
        
    public:
        function acquire() -> T {
            if pool.isEmpty() {
                if activeCount < maxSize {
                    activeCount++
                    return factory()
                } else {
                    // Wait or expand pool
                    waitForReturn()
                }
            }
            
            return pool.pop()
        }
        
        function release(object: T) {
            // Reset object state
            object.reset()
            
            // Return to pool
            pool.push(object)
        }
        
        function prewarm(count: int) {
            for i in 0..count {
                pool.push(factory())
            }
            activeCount += count
        }
}

// Usage example:
particlePool = new ResourcePool<ParticleEmitter>(
    factory: () => new ParticleEmitter(),
    maxSize: 100
)

// In game:
emitter = particlePool.acquire()
emitter.play()
// ... later ...
particlePool.release(emitter)
```

## Usage Examples

### Basic Loading

```pseudocode
// Simple synchronous-style usage
function loadGameAssets() {
    // These return immediately with handles
    playerTexture = AssetManager.loadTexture("player.png")
    enemySprite = AssetManager.loadSprite("enemy.sprite")
    explosionSound = AssetManager.loadSound("explosion.wav")
    
    // Handles track loading state internally
    // Use them directly - will wait if still loading
    sprite.setTexture(playerTexture.get())
}
```

### Async Loading with Progress

```pseudocode
function loadLevelAsync(levelName: string, onProgress: Function) {
    levelData = readLevelFile(levelName)
    totalAssets = levelData.assets.length
    loadedAssets = 0
    
    for asset in levelData.assets {
        AssetManager.loadAsync(asset.path, (handle) => {
            loadedAssets++
            progress = loadedAssets / totalAssets
            onProgress(progress)
            
            if loadedAssets == totalAssets {
                onLevelReady()
            }
        })
    }
}
```

### Retained Resources

```pseudocode
// Keep critical assets always loaded
function initializeCoreAssets() {
    uiAtlas = AssetManager.loadTexture("ui/atlas.png")
    AssetManager.retain(uiAtlas)  // Never unload
    
    mainFont = AssetManager.loadFont("fonts/main.ttf")
    AssetManager.retain(mainFont)
}

function shutdown() {
    // Release retained assets
    AssetManager.release(uiAtlas)
    AssetManager.release(mainFont)
}
```

### Streaming Large Assets

```pseudocode
class StreamingTexture {
    private:
        lowResHandle: Handle<Texture>
        highResHandle: Handle<Texture>?
        currentLOD: int = 0
        
    public:
        function load(basePath: string) {
            // Load low-res immediately
            lowResHandle = AssetManager.loadTexture(basePath + "_low.png")
            
            // Stream high-res in background
            AssetManager.loadAsync(basePath + "_high.png", (handle) => {
                highResHandle = handle
                currentLOD = 1
                notifyLODChanged()
            })
        }
        
        function getCurrentTexture() -> Texture {
            if currentLOD == 1 && highResHandle.isReady() {
                return highResHandle.get()
            }
            return lowResHandle.get()
        }
}
```

## State Diagram

```
Resource Lifecycle States:

    ┌─────────┐
    │UNLOADED │
    └────┬────┘
         │ load()
         ▼
    ┌─────────┐     timeout/error
    │LOADING  ├──────────────────┐
    └────┬────┘                  │
         │ data ready            │
         ▼                       ▼
    ┌─────────┐              ┌────────┐
    │  READY  │              │ FAILED │
    └────┬────┘              └────────┘
         │                       │
         │ refCount=0            │
         │ && !retained          │ retry()
         ▼                       │
    ┌─────────┐                 │
    │ EVICTED │◄────────────────┘
    └─────────┘
```

## Complete Workflow Example

### Adding a State Machine Type - Step by Step

```pseudocode
// ═══════════════════════════════════════════════════════════
// STEP 1: Define your data structure (5 minutes)
// ═══════════════════════════════════════════════════════════

class StateMachine {
    name: string
    states: Map<string, State>
    transitions: Array<Transition>
    currentState: string
    
    struct State {
        name: string
        onEnter: string?  // Optional script/function name
        onExit: string?
        onUpdate: string?
    }
    
    struct Transition {
        from: string
        to: string
        condition: string  // Expression to evaluate
        priority: int
    }
    
    // Required: Deserialization
    static function fromJSON(json: JSON) -> StateMachine {
        sm = new StateMachine()
        sm.name = json.name
        sm.currentState = json.initialState
        
        // Parse states
        for stateName, stateData in json.states {
            sm.states[stateName] = State.fromJSON(stateData)
        }
        
        // Parse transitions
        for transData in json.transitions {
            sm.transitions.add(Transition.fromJSON(transData))
        }
        
        return sm
    }
    
    // Required: Serialization
    function toJSON() -> JSON {
        return {
            name: this.name,
            initialState: this.currentState,
            states: this.states.map((name, state) => 
                [name, state.toJSON()]
            ),
            transitions: this.transitions.map(t => t.toJSON())
        }
    }
    
    // Your actual state machine logic
    function update(deltaTime: float, context: any) {
        currentState = states[this.currentState]
        
        // Check for transitions
        for transition in transitions {
            if transition.from == this.currentState {
                if evaluateCondition(transition.condition, context) {
                    transitionTo(transition.to)
                    break
                }
            }
        }
        
        // Update current state
        if currentState.onUpdate {
            executeScript(currentState.onUpdate, context)
        }
    }
    
    function transitionTo(stateName: string) {
        // Exit current state
        if states[currentState].onExit {
            executeScript(states[currentState].onExit)
        }
        
        // Enter new state
        currentState = stateName
        if states[currentState].onEnter {
            executeScript(states[currentState].onEnter)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// STEP 2: Register the type (ONE LINE in engine init)
// ═══════════════════════════════════════════════════════════

function initializeEngine() {
    // ... other initialization ...
    
    // That's it! One line to register:
    registerJSONType<StateMachine>("statemachine", [".sm.json", ".statemachine"])
    
    // Or with default template:
    registerJSONType<StateMachine>("statemachine", [".sm.json"], {
        name: "New State Machine",
        initialState: "idle",
        states: {
            idle: { name: "idle" }
        },
        transitions: []
    })
}

// ═══════════════════════════════════════════════════════════
// STEP 3: Use it in your game
// ═══════════════════════════════════════════════════════════

class Enemy {
    ai: Handle<StateMachine>
    
    function initialize() {
        // Load the state machine
        ai = AssetManager.load<StateMachine>("enemies/goblin_ai.sm.json")
    }
    
    function update(deltaTime: float) {
        // Use it
        if ai.isReady() {
            ai.get().update(deltaTime, this)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// STEP 4: Create assets in editor (runtime)
// ═══════════════════════════════════════════════════════════

class EditorUI {
    function onCreateNewStateMachine() {
        // User clicks "Create > State Machine"
        path = "enemies/new_ai.sm.json"
        
        // Create new asset
        sm = AssetManager.createNew<StateMachine>(
            "statemachine",
            path,
            {
                name: "New AI",
                initialState: "patrol",
                states: {
                    patrol: { name: "patrol", onUpdate: "patrolBehavior" },
                    chase: { name: "chase", onUpdate: "chaseBehavior" },
                    attack: { name: "attack", onUpdate: "attackBehavior" }
                },
                transitions: [
                    { from: "patrol", to: "chase", condition: "playerInRange" },
                    { from: "chase", to: "attack", condition: "playerInAttackRange" },
                    { from: "attack", to: "chase", condition: "!playerInAttackRange" },
                    { from: "chase", to: "patrol", condition: "!playerInRange" }
                ]
            }
        )
        
        // Immediately available to use - no restart needed!
        // Game can now load this asset
    }
    
    function onEditStateMachine(path: string) {
        // Open existing
        sm = AssetManager.load<StateMachine>(path)
        
        // Show in editor UI
        displayStateMachineEditor(sm)
    }
    
    function onAddState(sm: Handle<StateMachine>, stateName: string) {
        // User adds a new state
        machine = sm.get()
        machine.states[stateName] = new State(name: stateName)
        
        // IMMEDIATE EFFECT: Any enemy using this AI now has the new state!
        // No reload, no restart needed.
        
        // Mark as modified - user must save manually
        markModified(sm)
    }
}

// ═══════════════════════════════════════════════════════════
// Example JSON file created: enemies/goblin_ai.sm.json
// ═══════════════════════════════════════════════════════════

{
  "name": "Goblin AI",
  "initialState": "patrol",
  "states": {
    "patrol": {
      "name": "patrol",
      "onEnter": "startPatrolling",
      "onUpdate": "updatePatrol",
      "onExit": "stopPatrolling"
    },
    "chase": {
      "name": "chase",
      "onUpdate": "chasePlayer"
    },
    "attack": {
      "name": "attack",
      "onEnter": "startAttack",
      "onUpdate": "updateAttack"
    }
  },
  "transitions": [
    {
      "from": "patrol",
      "to": "chase",
      "condition": "distance(player) < 10",
      "priority": 1
    },
    {
      "from": "chase",
      "to": "attack",
      "condition": "distance(player) < 2",
      "priority": 2
    },
    {
      "from": "attack",
      "to": "chase",
      "condition": "distance(player) > 2",
      "priority": 1
    },
    {
      "from": "chase",
      "to": "patrol",
      "condition": "distance(player) > 15",
      "priority": 0
    }
  ]
}
```

### Real-World Editor Integration

```pseudocode
// Complete editor for StateMachine with live preview
class StateMachineEditor extends AssetEditor {
    previewEntity: GameObject?
    isPlaying: bool = false
    
    function open(path: string) {
        super.open<StateMachine>(path, "statemachine")
        
        // Create preview entity to test the state machine
        previewEntity = createPreviewEntity()
        previewEntity.stateMachine = currentAsset
    }
    
    function renderUI() {
        machine = currentAsset.get()
        
        // ════════════════════════════════════════════
        // State List
        // ════════════════════════════════════════════
        UI.beginPanel("States")
        
        for stateName, state in machine.states {
            isCurrentState = (machine.currentState == stateName)
            
            if UI.button(stateName, highlighted: isCurrentState) {
                // Preview this state
                machine.transitionTo(stateName)
            }
            
            UI.sameLine()
            if UI.button("Edit") {
                showStateEditor(state)
            }
            
            UI.sameLine()
            if UI.button("Delete") {
                machine.states.remove(stateName)
                onPropertyChanged("states", machine.states)
            }
        }
        
        if UI.button("+ Add State") {
            newName = UI.showInputDialog("State name:")
            if newName {
                machine.states[newName] = new State(name: newName)
                onPropertyChanged("states", machine.states)
            }
        }
        
        UI.endPanel()
        
        // ════════════════════════════════════════════
        // Transitions (Visual Graph)
        // ════════════════════════════════════════════
        UI.beginPanel("Transitions")
        
        // Draw state graph
        for stateName, state in machine.states {
            node = UI.drawNode(stateName, getStatePosition(stateName))
            
            // Draw transitions from this state
            for transition in machine.transitions {
                if transition.from == stateName {
                    toNode = getStatePosition(transition.to)
                    UI.drawArrow(node.position, toNode, 
                               label: transition.condition)
                }
            }
        }
        
        // Add transition mode
        if UI.button("+ Add Transition") {
            enterTransitionCreationMode()
        }
        
        UI.endPanel()
        
        // ════════════════════════════════════════════
        // Live Preview
        // ════════════════════════════════════════════
        UI.beginPanel("Preview")
        
        if UI.button(isPlaying ? "⏸ Pause" : "▶ Play") {
            isPlaying = !isPlaying
        }
        
        if isPlaying && previewEntity {
            previewEntity.update(getDeltaTime())
        }
        
        UI.label("Current State: " + machine.currentState)
        UI.label("Time in State: " + machine.getTimeInState())
        
        // Visual preview of the entity
        renderPreviewViewport(previewEntity)
        
        UI.endPanel()
        
        // ════════════════════════════════════════════
        // Properties Inspector
        // ════════════════════════════════════════════
        UI.beginPanel("Properties")
        
        // Edit initial state
        newInitial = UI.dropdown("Initial State", 
                                machine.currentState,
                                machine.states.keys())
        if newInitial != machine.currentState {
            machine.currentState = newInitial
            onPropertyChanged("currentState", newInitial)
        }
        
        UI.endPanel()
    }
    
    function showStateEditor(state: State) {
        // Popup window to edit state properties
        UI.beginWindow("Edit State: " + state.name)
        
        state.onEnter = UI.textField("On Enter Script:", state.onEnter)
        state.onUpdate = UI.textField("On Update Script:", state.onUpdate)
        state.onExit = UI.textField("On Exit Script:", state.onExit)
        
        if UI.button("Save") {
            onPropertyChanged("states", machine.states)
            UI.closeWindow()
        }
        
        UI.endWindow()
    }
}

// Usage in game editor:
editor = new StateMachineEditor()
editor.open("enemies/goblin_ai.sm.json")

// User edits states, adds transitions, tests in preview
// All changes are LIVE - any goblin in the game gets updates immediately!
// Save happens automatically every 2 seconds
```

### Type Registry System

```pseudocode
class TypeRegistry {
    private:
        types: Map<string, TypeInfo>
        extensionMap: Map<string, string>  // .json -> "spritesheet"
        templates: Map<string, JSON>
        
    struct TypeInfo {
        name: string
        loader: AssetLoader
        extensions: Array<string>
        createDefault: Function() -> any
        editorFactory: Function() -> AssetEditor?
    }
    
    public:
        function register<T>(
            typeName: string,
            loader: AssetLoader,
            extensions: Array<string>,
            defaultTemplate: JSON = {},
            editorType: Type<AssetEditor>? = null
        ) {
            typeInfo = TypeInfo(
                name: typeName,
                loader: loader,
                extensions: extensions,
                createDefault: () => T.fromJSON(defaultTemplate),
                editorFactory: editorType ? 
                    () => new editorType() : null
            )
            
            types[typeName] = typeInfo
            templates[typeName] = defaultTemplate
            
            // Map extensions
            for ext in extensions {
                extensionMap[ext] = typeName
            }
        }
        
        function getTypeFromPath(path: string) -> string? {
            ext = getExtension(path)
            return extensionMap[ext]
        }
        
        function createDefaultAsset<T>(typeName: string) -> T {
            if !types.contains(typeName) {
                throw "Unknown asset type: " + typeName
            }
            
            return types[typeName].createDefault()
        }
        
        function createEditor(typeName: string) -> AssetEditor? {
            if !types.contains(typeName) {
                return null
            }
            
            factory = types[typeName].editorFactory
            return factory ? factory() : new GenericJSONEditor()
        }
        
        function getAllTypes() -> Array<string> {
            return types.keys()
        }
        
        function getTemplate(typeName: string) -> JSON {
            return templates[typeName] ?? {}
        }
}

// Enhanced registration helper
function registerJSONType<T>(
    typeName: string,
    extensions: Array<string>,
    defaultTemplate: JSON = {},
    editorType: Type<AssetEditor>? = null
) {
    loader = new GenericJSONLoader<T>(
        T.fromJSON,
        extensions
    )
    
    AssetManager.typeRegistry.register<T>(
        typeName,
        loader,
        extensions,
        defaultTemplate,
        editorType
    )
    
    log("✓ Registered type: " + typeName + " (" + extensions.join(", ") + ")")
}
```

### Initialize All Game Types

```pseudocode
// In your engine initialization, register all types
function initializeAssetSystem() {
    log("Initializing Asset System...")
    
    // ════════════════════════════════════════════
    // Core Engine Types
    // ════════════════════════════════════════════
    
    registerJSONType<SpriteSheet>(
        "spritesheet",
        [".spritesheet", ".ss.json"],
        {
            texture: "",
            meta: { version: "1.0" },
            sprites: {}
        },
        SpriteSheetEditor
    )
    
    registerJSONType<AnimationClip>(
        "animation",
        [".anim", ".animation.json"],
        {
            name: "New Animation",
            duration: 1.0,
            loop: true,
            keyframes: []
        },
        AnimationEditor
    )
    
    registerJSONType<Animator>(
        "animator",
        [".animator", ".animator.json"],
        {
            spriteSheet: "",
            animations: {},
            transitions: [],
            defaultAnimation: "idle"
        },
        AnimatorEditor
    )
    
    // ════════════════════════════════════════════
    // AI & Gameplay Types
    // ════════════════════════════════════════════
    
    registerJSONType<StateMachine>(
        "statemachine",
        [".sm.json", ".statemachine"],
        {
            name: "New State Machine",
            initialState: "default",
            states: {
                default: { name: "default" }
            },
            transitions: []
        },
        StateMachineEditor
    )
    
    registerJSONType<DialogueTree>(
        "dialogue",
        [".dialogue.json"],
        {
            nodes: [],
            startNode: "start"
        },
        DialogueEditor
    )
    
    registerJSONType<QuestData>(
        "quest",
        [".quest.json"],
        {
            id: "",
            title: "New Quest",
            description: "",
            objectives: [],
            rewards: []
        }
    )
    
    // ════════════════════════════════════════════
    // Level & World Types
    // ════════════════════════════════════════════
    
    registerJSONType<Tilemap>(
        "tilemap",
        [".tilemap.json", ".tmx"],
        {
            width: 32,
            height: 32,
            tileSize: 16,
            layers: []
        },
        TilemapEditor
    )
    
    registerJSONType<Prefab>(
        "prefab",
        [".prefab.json"],
        {
            components: [],
            children: []
        },
        PrefabEditor
    )
    
    // ════════════════════════════════════════════
    // Visual Effects
    // ════════════════════════════════════════════
    
    registerJSONType<ParticleEffect>(
        "particles",
        [".particles.json"],
        {
            emissionRate: 10,
            lifetime: 1.0,
            startColor: [255, 255, 255, 255],
            endColor: [255, 255, 255, 0]
        },
        ParticleEditor
    )
    
    registerJSONType<Shader>(
        "shader",
        [".shader.json"],
        {
            vertexShader: "",
            fragmentShader: "",
            uniforms: {}
        }
    )
    
    // ════════════════════════════════════════════
    // Audio Types
    // ════════════════════════════════════════════
    
    registerJSONType<AudioMixer>(
        "audiomixer",
        [".mixer.json"],
        {
            groups: {
                master: { volume: 1.0 },
                music: { volume: 0.8 },
                sfx: { volume: 1.0 }
            }
        }
    )
    
    log("✓ Asset system initialized with " + 
        AssetManager.typeRegistry.getAllTypes().length + " types")
}
```

### Editor "Create" Menu Integration

```pseudocode
class EditorMenuBar {
    function renderCreateMenu() {
        if UI.beginMenu("Create") {
            
            // Get all registered types
            types = AssetManager.typeRegistry.getAllTypes()
            
            // Organize by category
            categories = {
                "Animation": ["spritesheet", "animation", "animator"],
                "AI": ["statemachine", "dialogue"],
                "Gameplay": ["quest", "prefab"],
                "Level": ["tilemap"],
                "Effects": ["particles", "shader"],
                "Audio": ["audiomixer"]
            }
            
            for categoryName, typeList in categories {
                if UI.beginMenu(categoryName) {
                    
                    for typeName in typeList {
                        if UI.menuItem(typeName) {
                            createNewAsset(typeName)
                        }
                    }
                    
                    UI.endMenu()
                }
            }
            
            UI.endMenu()
        }
    }
    
    function createNewAsset(typeName: string) {
        // Show save dialog
        path = UI.showSaveDialog(
            title: "Create " + typeName,
            defaultName: "new_" + typeName,
            filter: getExtensionsForType(typeName)
        )
        
        if !path {
            return
        }
        
        // Create asset with default template
        handle = AssetManager.createNew(
            typeName,
            path,
            AssetManager.typeRegistry.getTemplate(typeName)
        )
        
        // Open in appropriate editor
        editor = AssetManager.typeRegistry.createEditor(typeName)
        if editor {
            editor.open(path, typeName)
            openEditorWindow(editor)
        }
        
        log("✓ Created new " + typeName + " at " + path)
    }
}
```

## Performance Considerations

### Memory Management Strategy

```
Priority Levels:
┌──────────────┬────────────────┬───────────────────┐
│   CRITICAL   │    ACTIVE      │     CACHED        │
│  (Retained)  │ (RefCount > 0) │  (RefCount = 0)   │
├──────────────┼────────────────┼───────────────────┤
│ Never evict  │ Keep in memory │ Evict when needed │
│ UI assets    │ Current scene  │ Previous scenes   │
│ Core systems │ Active objects │ Unused assets     │
└──────────────┴────────────────┴───────────────────┘
```

### Loading Strategy

```pseudocode
// Prioritized loading queue
class LoadingQueue {
    highPriority: Queue<LoadTask>    // Immediate (UI, player)
    normalPriority: Queue<LoadTask>  // Standard (enemies, items)
    lowPriority: Queue<LoadTask>     // Background (music, ambient)
    streaming: Queue<LoadTask>       // Large assets
    
    function process() {
        // Process in order of priority
        if task = highPriority.dequeue() {
            task.execute()
        } else if task = normalPriority.dequeue() {
            task.execute()
        } else if hasSpareTime() {
            if task = lowPriority.dequeue() {
                task.execute()
            } else if task = streaming.dequeue() {
                task.executeChunk()  // Incremental loading
            }
        }
    }
}
```

## Error Handling

```pseudocode
class ResourceLoadException extends Exception {
    resourceId: string
    reason: ErrorReason
    fallbackPath: string?
    
    function getFallback() -> Resource? {
        if fallbackPath {
            return AssetManager.load(fallbackPath)
        }
        return getDefaultPlaceholder()
    }
}

// Fallback chain
function loadWithFallback(path: string, fallbacks: List<string>) -> Handle {
    try {
        return AssetManager.load(path)
    } catch error {
        log("Failed to load: " + path)
        
        for fallback in fallbacks {
            try {
                return AssetManager.load(fallback)
            } catch {
                continue
            }
        }
        
        // Use engine default
        return AssetManager.loadDefault(getResourceType(path))
    }
}
```

## Extensible Type System

### Easy Type Registration

The system is designed to make adding new asset types trivial through a registration mechanism:

```pseudocode
// Adding a new type is just 3 steps:

// 1. Define your data structure
class StateMachine {
    name: string
    states: Map<string, State>
    transitions: Array<Transition>
    initialState: string
    
    function fromJSON(json: JSON) -> StateMachine {
        // Parse JSON into StateMachine
        return new StateMachine(json)
    }
    
    function toJSON() -> JSON {
        // Serialize to JSON
        return {
            name: this.name,
            states: this.states,
            transitions: this.transitions,
            initialState: this.initialState
        }
    }
}

// 2. Create a loader (optional if using GenericJSONLoader)
class StateMachineLoader implements AssetLoader {
    function load(path: string) -> StateMachine {
        json = parseJSON(readFile(path))
        return StateMachine.fromJSON(json)
    }
    
    function unload(resource: StateMachine) {
        // Cleanup if needed
    }
    
    function getSupportedExtensions() -> List<string> {
        return [".statemachine", ".sm.json"]
    }
}

// 3. Register with AssetManager (usually in engine initialization)
AssetManager.registerType<StateMachine>("statemachine", new StateMachineLoader())

// Done! Now you can load it:
sm = AssetManager.load<StateMachine>("enemy_ai.statemachine")
```

### Generic JSON Loader

For simple JSON-based types, use the generic loader to avoid boilerplate:

```pseudocode
class GenericJSONLoader<T> implements AssetLoader {
    private:
        constructor: Function(JSON) -> T
        extensions: Array<string>
        
    public:
        function GenericJSONLoader(
            constructor: Function(JSON) -> T,
            extensions: Array<string>
        ) {
            this.constructor = constructor
            this.extensions = extensions
        }
        
        function load(path: string) -> T {
            json = parseJSON(readFile(path))
            return constructor(json)
        }
        
        function unload(resource: T) {
            // Generic cleanup
            if resource.hasMethod("dispose") {
                resource.dispose()
            }
        }
        
        function getSupportedExtensions() -> Array<string> {
            return extensions
        }
}

// Usage - one-liner registration:
AssetManager.registerType<StateMachine>(
    "statemachine",
    new GenericJSONLoader<StateMachine>(
        StateMachine.fromJSON,
        [".statemachine", ".sm.json"]
    )
)

AssetManager.registerType<SpriteSheet>(
    "spritesheet",
    new GenericJSONLoader<SpriteSheet>(
        SpriteSheet.fromJSON,
        [".spritesheet", ".ss.json"]
    )
)

AssetManager.registerType<AnimationClip>(
    "animation",
    new GenericJSONLoader<AnimationClip>(
        AnimationClip.fromJSON,
        [".anim", ".animation.json"]
    )
)

AssetManager.registerType<Animator>(
    "animator",
    new GenericJSONLoader<Animator>(
        Animator.fromJSON,
        [".animator", ".animator.json"]
    )
)
```

### Enhanced AssetManager with Type Registration

```pseudocode
class AssetManager {
    private:
        cache: ResourceCache
        loaders: Map<string, AssetLoader>
        typeRegistry: Map<string, TypeInfo>
        hotReload: HotReloadManager
        saveQueue: AsyncSaveQueue
        
    public:
        // Register new type
        function registerType<T>(
            typeName: string,
            loader: AssetLoader,
            extensions: Array<string> = []
        ) {
            loaders[typeName] = loader
            
            typeInfo = new TypeInfo(
                name: typeName,
                loader: loader,
                extensions: extensions.isEmpty() ? 
                    loader.getSupportedExtensions() : extensions
            )
            
            typeRegistry[typeName] = typeInfo
            
            // Register extensions for auto-detection
            for ext in typeInfo.extensions {
                extensionToType[ext] = typeName
            }
            
            log("Registered asset type: " + typeName)
        }
        
        // Load with auto-type detection
        function load<T>(path: string) -> Handle<T> {
            extension = getExtension(path)
            typeName = extensionToType[extension]
            
            if !typeName {
                throw UnknownAssetTypeException(path)
            }
            
            return loadTyped<T>(path, typeName)
        }
        
        // Load with explicit type
        function loadTyped<T>(path: string, typeName: string) -> Handle<T> {
            resourceId = generateId(path, typeName)
            
            // Check cache
            if cached = cache.get(resourceId) {
                cached.retain()
                return cached
            }
            
            // Create handle and load
            handle = new Handle<T>(resourceId, state=LOADING)
            cache.store(resourceId, handle)
            
            loader = loaders[typeName]
            
            loadQueue.enqueue(() => {
                try {
                    data = loader.load(path)
                    handle.setData(data)
                    handle.setState(READY)
                    
                    // Register for hot reload
                    hotReload.watch(path, resourceId)
                    
                    notifyLoadComplete(handle)
                } catch error {
                    handle.setState(FAILED)
                    handle.setError(error)
                    log("Failed to load " + path + ": " + error)
                }
            })
            
            return handle
        }
        
        // EDITOR SUPPORT: Save asset at runtime
        function save<T>(asset: T, path: string, typeName: string) -> bool {
            if !asset.hasMethod("toJSON") {
                throw "Asset type must implement toJSON() for saving"
            }
            
            json = asset.toJSON()
            jsonString = JSON.stringify(json, indent=2)
            
            // Queue async save to not block game
            saveQueue.enqueue(() => {
                try {
                    writeFile(path, jsonString)
                    
                    // Update cache if already loaded
                    resourceId = generateId(path, typeName)
                    if handle = cache.get(resourceId) {
                        handle.setData(asset)
                        handle.markDirty(false)
                    }
                    
                    log("Saved: " + path)
                    return true
                } catch error {
                    log("Save failed: " + path + " - " + error)
                    return false
                }
            })
        }
        
        // EDITOR SUPPORT: Create new asset
        function createNew<T>(
            typeName: string,
            savePath: string,
            initialData: JSON = {}
        ) -> Handle<T> {
            // Create instance from type
            typeInfo = typeRegistry[typeName]
            loader = typeInfo.loader
            
            // Create empty/default instance
            asset = createDefaultAsset<T>(typeName, initialData)
            
            // Save to disk
            save(asset, savePath, typeName)
            
            // Create handle
            resourceId = generateId(savePath, typeName)
            handle = new Handle<T>(resourceId, state=READY)
            handle.setData(asset)
            cache.store(resourceId, handle)
            
            // Enable hot reload
            hotReload.watch(savePath, resourceId)
            
            return handle
        }
        
        // EDITOR SUPPORT: Save without reload
        function saveInPlace<T>(handle: Handle<T>, path: string, typeName: string) {
            asset = handle.get()
            
            // Save to disk
            json = asset.toJSON()
            writeFile(path, JSON.stringify(json, indent=2))
            
            // No reload needed - handle already points to modified object
            // Just mark as clean
            handle.markDirty(false)
            
            // Notify other systems if needed
            notifyAssetModified(handle.resourceId, asset)
        }
}
```

### Example: Complete New Type Implementation

```pseudocode
// 1. Define the SpriteSheet class
class SpriteSheet {
    texturePath: string
    texture: Handle<Texture>
    sprites: Map<string, SpriteData>
    metadata: JSON
    
    struct SpriteData {
        name: string
        x: int, y: int, width: int, height: int
        pivotX: float, pivotY: float
        rotated: bool
    }
    
    // Required for GenericJSONLoader
    static function fromJSON(json: JSON) -> SpriteSheet {
        sheet = new SpriteSheet()
        sheet.texturePath = json.texture
        sheet.metadata = json.meta
        
        // Load the texture
        sheet.texture = AssetManager.loadTexture(sheet.texturePath)
        
        // Parse sprites
        for spriteName, spriteData in json.sprites {
            sheet.sprites[spriteName] = SpriteData.fromJSON(spriteData)
        }
        
        return sheet
    }
    
    // Required for saving
    function toJSON() -> JSON {
        return {
            texture: this.texturePath,
            meta: this.metadata,
            sprites: this.sprites.map((name, data) => {
                return [name, data.toJSON()]
            })
        }
    }
    
    // Helper to get a specific sprite
    function getSprite(name: string) -> Sprite {
        if !sprites.contains(name) {
            throw "Sprite not found: " + name
        }
        
        data = sprites[name]
        return new Sprite(
            texture.get(),
            Rect(data.x, data.y, data.width, data.height),
            Vector2(data.pivotX, data.pivotY)
        )
    }
}

// 2. Register it (in engine initialization)
function initializeAssetTypes() {
    AssetManager.registerType<SpriteSheet>(
        "spritesheet",
        new GenericJSONLoader<SpriteSheet>(
            SpriteSheet.fromJSON,
            [".spritesheet", ".ss.json"]
        )
    )
}

// 3. Use it in your game
function loadPlayerAssets() {
    // Load spritesheet
    playerSheet = AssetManager.load<SpriteSheet>("characters/player.spritesheet")
    
    // Get individual sprites
    idleSprite = playerSheet.get().getSprite("idle_01")
    runSprite = playerSheet.get().getSprite("run_01")
}
```

### Example: Animation System

```pseudocode
// Animation clip - single animation
class AnimationClip {
    name: string
    duration: float
    loop: bool
    keyframes: Array<Keyframe>
    
    struct Keyframe {
        time: float
        spriteName: string
        properties: Map<string, any>  // position, rotation, scale, etc.
    }
    
    static function fromJSON(json: JSON) -> AnimationClip {
        clip = new AnimationClip()
        clip.name = json.name
        clip.duration = json.duration
        clip.loop = json.loop ?? true
        
        for kfData in json.keyframes {
            clip.keyframes.add(Keyframe.fromJSON(kfData))
        }
        
        return clip
    }
    
    function toJSON() -> JSON {
        return {
            name: this.name,
            duration: this.duration,
            loop: this.loop,
            keyframes: this.keyframes.map(kf => kf.toJSON())
        }
    }
    
    function sample(time: float) -> Keyframe {
        // Interpolate between keyframes at given time
        // ...
    }
}

// Animator - controls multiple animations
class Animator {
    spriteSheet: Handle<SpriteSheet>
    animations: Map<string, Handle<AnimationClip>>
    currentAnimation: string
    currentTime: float
    transitions: Map<string, AnimationTransition>
    parameters: Map<string, any>
    
    struct AnimationTransition {
        from: string
        to: string
        duration: float
        condition: Function() -> bool
    }
    
    static function fromJSON(json: JSON) -> Animator {
        animator = new Animator()
        
        // Load spritesheet
        animator.spriteSheet = AssetManager.load<SpriteSheet>(json.spriteSheet)
        
        // Load animation clips
        for animName, animPath in json.animations {
            animator.animations[animName] = 
                AssetManager.load<AnimationClip>(animPath)
        }
        
        // Setup transitions
        for transData in json.transitions {
            animator.transitions[transData.id] = 
                AnimationTransition.fromJSON(transData)
        }
        
        animator.currentAnimation = json.defaultAnimation
        
        return animator
    }
    
    function toJSON() -> JSON {
        return {
            spriteSheet: this.spriteSheet.get().texturePath,
            animations: this.animations.map((name, handle) => {
                return [name, handle.resourceId]
            }),
            transitions: this.transitions.map(t => t.toJSON()),
            defaultAnimation: this.currentAnimation,
            parameters: this.parameters
        }
    }
    
    function play(animationName: string) {
        if animations.contains(animationName) {
            currentAnimation = animationName
            currentTime = 0
        }
    }
    
    function update(deltaTime: float) {
        currentTime += deltaTime
        
        // Check transitions
        for transition in transitions.values() {
            if transition.from == currentAnimation && transition.condition() {
                play(transition.to)
                break
            }
        }
        
        // Sample current animation
        anim = animations[currentAnimation].get()
        keyframe = anim.sample(currentTime)
        
        // Update sprite
        sheet = spriteSheet.get()
        currentSprite = sheet.getSprite(keyframe.spriteName)
    }
}

// Register both types
AssetManager.registerType<AnimationClip>(
    "animation",
    new GenericJSONLoader<AnimationClip>(
        AnimationClip.fromJSON,
        [".anim", ".animation.json"]
    )
)

AssetManager.registerType<Animator>(
    "animator",
    new GenericJSONLoader<Animator>(
        Animator.fromJSON,
        [".animator", ".animator.json"]
    )
)
```

### Editor Integration - Live Editing Without Reload

```pseudocode
// Editor UI for creating/editing assets
class AssetEditor {
    currentAsset: Handle<?>
    assetType: string
    modified: bool = false
    
    function open<T>(path: string, typeName: string) {
        // Load asset
        currentAsset = AssetManager.load<T>(path)
        assetType = typeName
        modified = false
        
        // Display in editor UI
        displayAssetProperties(currentAsset.get())
    }
    
    function createNew<T>(typeName: string) {
        // Show save dialog first
        path = showSaveDialog(typeName)
        if !path {
            return
        }
        
        // Create with defaults
        currentAsset = AssetManager.createNew<T>(
            typeName,
            path,
            getDefaultData(typeName)
        )
        
        assetType = typeName
        modified = false
        
        displayAssetProperties(currentAsset.get())
    }
    
    function onPropertyChanged(propertyName: string, newValue: any) {
        // Update the asset directly in memory
        asset = currentAsset.get()
        asset[propertyName] = newValue
        
        modified = true
        
        // The game is already using this asset, so changes are
        // immediately visible! No reload needed.
        
        // User must call save() manually
    }
    
    function save() {
        if !modified {
            return
        }
        
        // Save without reloading
        AssetManager.saveInPlace(
            currentAsset,
            currentAsset.resourceId.path,
            assetType
        )
        
        modified = false
        log("Asset saved")
    }
    
    function update(deltaTime: float) {
        // Update editor logic
        // No auto-save
    }
}

// Example: Animator Editor
class AnimatorEditor extends AssetEditor {
    function open(path: string) {
        super.open<Animator>(path, "animator")
    }
    
    function displayUI() {
        animator = currentAsset.get()
        
        // Display animations list
        UI.label("Animations:")
        for name, clipHandle in animator.animations {
            if UI.button(name) {
                playPreview(name)
            }
        }
        
        // Add animation button
        if UI.button("Add Animation") {
            path = showFileDialog("Select animation clip")
            if path {
                clipHandle = AssetManager.load<AnimationClip>(path)
                animator.animations[getFileName(path)] = clipHandle
                onPropertyChanged("animations", animator.animations)
            }
        }
        
        // Edit transitions
        UI.label("Transitions:")
        for transition in animator.transitions.values() {
            displayTransitionEditor(transition)
        }
        
        if UI.button("Add Transition") {
            newTransition = createNewTransition()
            animator.transitions[newTransition.id] = newTransition
            onPropertyChanged("transitions", animator.transitions)
        }
    }
    
    function playPreview(animName: string) {
        // Preview runs in editor, game still running with same asset
        animator = currentAsset.get()
        animator.play(animName)
        
        // Any game objects using this animator see the change immediately!
    }
}
```

### Live Editing Example Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    LIVE EDITING FLOW                         │
└─────────────────────────────────────────────────────────────┘

1. Game Running
   ├─> Enemy using animator "goblin.animator"
   └─> Animator has "idle" and "attack" animations

2. Open in Editor
   ├─> AssetEditor.open("goblin.animator")
   ├─> Loads animator (already in cache, returns existing handle)
   └─> Editor UI displays properties

3. User Changes Animation Speed
   ├─> Editor: onPropertyChanged("duration", 0.5)
   ├─> Directly modifies animator.animations["attack"].duration
   ├─> Game enemy IMMEDIATELY uses new speed (same object in memory)
   └─> No reload, no restart, instant feedback!

4. User Adds New Animation
   ├─> Editor: adds "hurt" animation
   ├─> Modifies animator.animations map
   ├─> Game can now play "hurt" animation
   └─> Still no reload!

5. User Saves (Ctrl+S or Save Button)
   ├─> Editor: AssetManager.saveInPlace()
   ├─> Writes JSON to disk
   ├─> Handle still points to same object
   └─> Game continues uninterrupted

6. Hot Reload (if user modifies file externally)
   ├─> File watcher detects change
   ├─> Reloads animator from disk
   ├─> Updates handle data
   └─> Game objects get new version automatically
```

### Type Registration Helper

```pseudocode
// Macro/helper for extremely easy registration
function registerJSONType<T>(
    typeName: string,
    extensions: Array<string>,
    defaultData: JSON = {}
) {
    AssetManager.registerType<T>(
        typeName,
        new GenericJSONLoader<T>(
            T.fromJSON,
            extensions
        )
    )
    
    // Register default template
    AssetManager.registerDefaultTemplate(typeName, defaultData)
}

// Now registering a type is ONE line:
registerJSONType<StateMachine>("statemachine", [".sm.json"], {
    states: {},
    transitions: [],
    initialState: "idle"
})

registerJSONType<SpriteSheet>("spritesheet", [".spritesheet"], {
    texture: "",
    sprites: {}
})

registerJSONType<AnimationClip>("animation", [".anim"], {
    name: "New Animation",
    duration: 1.0,
    loop: true,
    keyframes: []
})

registerJSONType<Animator>("animator", [".animator"], {
    spriteSheet: "",
    animations: {},
    transitions: [],
    defaultAnimation: ""
})
```

### Benefits of This System

```
✅ Adding new type: 1-3 lines of code
✅ Editor creates/edits assets: Built-in
✅ Save without reload: Automatic
✅ Live editing: Changes visible immediately
✅ Hot reload: Works for external changes
✅ Type-safe: Full type checking
✅ JSON format: Human-readable, VCS-friendly
✅ Extensible: Easy to add custom loaders
✅ No boilerplate: Generic loader handles common cases
```

This architecture provides a robust, efficient resource management system with caching, hot reloading, flexible asset handling, and seamless editor integration for live asset creation and editing!
