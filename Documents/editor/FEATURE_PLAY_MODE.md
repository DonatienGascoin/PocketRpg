# Feature: Play Mode

## Overview

Add the ability to test scenes directly within the editor. A "Game View" panel renders the running game while editor panels remain visible (but disabled during play). Scene state is snapshotted on play and restored on stop.

## Goals

- Play/Pause/Stop controls in editor
- Game renders in dedicated "Game View" panel
- Editor panels visible but non-editable during play
- Game input only when Game View is focused
- State snapshot on play, restore on stop
- Same game loop as runtime (GameEngine)

## Non-Goals

- Step-by-step debugging
- Runtime variable inspection (future "Debug" panel)
- Hot-reload of code changes
- Multiplayer testing

---

## Current State

### Separate Applications

```
┌─────────────────────┐     ┌─────────────────────┐
│   EditorApplication │     │   GameApplication   │
├─────────────────────┤     ├─────────────────────┤
│ EditorScene         │     │ Scene (runtime)     │
│ EditorCamera        │     │ Camera              │
│ ImGui rendering     │     │ SpriteBatch         │
│ Tool system         │     │ GameEngine loop     │
└─────────────────────┘     └─────────────────────┘
        │                            │
        └────── No connection ───────┘
```

### Key Differences

| Aspect | Editor | Runtime |
|--------|--------|---------|
| Scene | EditorScene | Scene |
| Entity | EditorEntity | GameObject |
| Rendering | EditorSceneRenderer | BatchRenderer |
| Input | ImGui capture | Input system |
| Camera | EditorCamera | Camera component |
| Loop | ImGui frame | GameEngine.update() |

---

## Proposed Architecture

### State Machine

```
         ┌──────────────────────────────────────────┐
         │                                          │
         ▼                                          │
    ┌─────────┐    Play    ┌─────────────┐   Stop   │
    │  EDIT   │ ─────────► │   PLAYING   │ ─────────┤
    └─────────┘            └─────────────┘          │
         ▲                       │                  │
         │                       │ Pause            │
         │                       ▼                  │
         │                 ┌─────────────┐          │
         │                 │   PAUSED    │ ─────────┘
         │                 └─────────────┘    Stop
         │                       │
         │                       │ Resume
         │                       │
         └───────────────────────┘
```

### PlayModeManager

Central controller for play mode state.

```java
public class PlayModeManager {
    
    public enum State {
        EDIT,       // Normal editing
        PLAYING,    // Game running
        PAUSED      // Game paused (can inspect)
    }
    
    private State currentState = State.EDIT;
    private EditorScene editorScene;
    private byte[] sceneSnapshot;           // Serialized EditorScene
    private Scene runtimeScene;             // Active game scene
    private GameViewPanel gameView;
    
    // Embedded game systems
    private EmbeddedGameEngine gameEngine;
    
    public void play() {
        if (currentState != State.EDIT) return;
        
        // 1. Snapshot current scene
        sceneSnapshot = serializeScene(editorScene);
        
        // 2. Convert EditorScene → runtime Scene
        runtimeScene = SceneConverter.convert(editorScene);
        
        // 3. Initialize game systems
        gameEngine.initialize(runtimeScene);
        
        // 4. Switch state
        currentState = State.PLAYING;
        notifyStateChanged();
    }
    
    public void pause() {
        if (currentState != State.PLAYING) return;
        currentState = State.PAUSED;
        notifyStateChanged();
    }
    
    public void resume() {
        if (currentState != State.PAUSED) return;
        currentState = State.PLAYING;
        notifyStateChanged();
    }
    
    public void stop() {
        if (currentState == State.EDIT) return;
        
        // 1. Cleanup game systems
        gameEngine.destroy();
        runtimeScene = null;
        
        // 2. Restore scene from snapshot
        editorScene = deserializeScene(sceneSnapshot);
        sceneSnapshot = null;
        
        // 3. Switch state
        currentState = State.EDIT;
        notifyStateChanged();
    }
    
    public void update(float deltaTime) {
        if (currentState == State.PLAYING) {
            gameEngine.update(deltaTime);
        }
    }
}
```

### EmbeddedGameEngine

Stripped-down game engine that runs within the editor process.

```java
public class EmbeddedGameEngine {
    
    private Scene scene;
    private BatchRenderer renderer;
    private EditorFramebuffer gameFramebuffer;
    
    // Systems (subset of full GameEngine)
    private boolean inputEnabled = false;
    
    public void initialize(Scene scene) {
        this.scene = scene;
        
        // Initialize renderer to render into framebuffer
        gameFramebuffer = new EditorFramebuffer(width, height);
        gameFramebuffer.init();
        
        renderer = new BatchRenderer(renderingConfig);
        renderer.init(width, height);
        
        // Start all GameObjects
        for (GameObject go : scene.getGameObjects()) {
            go.start();
        }
    }
    
    public void update(float deltaTime) {
        // Update game logic
        for (GameObject go : scene.getGameObjects()) {
            if (go.isEnabled()) {
                go.update(deltaTime);
            }
        }
        
        // Late update
        for (GameObject go : scene.getGameObjects()) {
            if (go.isEnabled()) {
                go.lateUpdate(deltaTime);
            }
        }
    }
    
    public void render() {
        gameFramebuffer.bind();
        gameFramebuffer.clear(0, 0, 0, 1);
        
        // Render scene
        renderer.render(scene);
        
        gameFramebuffer.unbind();
    }
    
    public int getFramebufferTexture() {
        return gameFramebuffer.getTextureId();
    }
    
    public void setInputEnabled(boolean enabled) {
        this.inputEnabled = enabled;
    }
    
    public void destroy() {
        renderer.destroy();
        gameFramebuffer.destroy();
        scene.destroy();
    }
}
```

### GameViewPanel

Panel that displays the running game.

```java
public class GameViewPanel {
    
    private PlayModeManager playModeManager;
    private EmbeddedGameEngine gameEngine;
    
    private float viewportX, viewportY;
    private float viewportWidth, viewportHeight;
    private boolean isFocused = false;
    private boolean isHovered = false;
    
    public void render() {
        int windowFlags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        
        if (ImGui.begin("Game View", windowFlags)) {
            renderToolbar();
            
            ImGui.separator();
            
            // Track focus for input routing
            isFocused = ImGui.isWindowFocused();
            isHovered = ImGui.isWindowHovered();
            
            // Get content region
            updateViewportBounds();
            
            // Render game framebuffer or placeholder
            if (playModeManager.isPlaying() || playModeManager.isPaused()) {
                renderGameView();
            } else {
                renderPlaceholder();
            }
        }
        ImGui.end();
    }
    
    private void renderToolbar() {
        PlayModeManager.State state = playModeManager.getState();
        
        // Play button
        boolean isPlaying = state != PlayModeManager.State.EDIT;
        if (isPlaying) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
        }
        if (ImGui.button(FontAwesomeIcons.Play + " Play")) {
            if (!isPlaying) {
                playModeManager.play();
            }
        }
        if (isPlaying) {
            ImGui.popStyleColor();
        }
        
        ImGui.sameLine();
        
        // Pause button
        boolean canPause = state == PlayModeManager.State.PLAYING;
        boolean isPaused = state == PlayModeManager.State.PAUSED;
        
        if (isPaused) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.6f, 0.2f, 1f);
        }
        
        if (ImGui.button(FontAwesomeIcons.Pause + " Pause") && canPause) {
            playModeManager.pause();
        } else if (isPaused && ImGui.isItemClicked()) {
            playModeManager.resume();
        }
        
        if (isPaused) {
            ImGui.popStyleColor();
        }
        
        ImGui.sameLine();
        
        // Stop button
        boolean canStop = state != PlayModeManager.State.EDIT;
        if (ImGui.button(FontAwesomeIcons.Stop + " Stop") && canStop) {
            playModeManager.stop();
        }
        
        ImGui.sameLine();
        ImGui.separator();
        ImGui.sameLine();
        
        // Stats
        if (isPlaying) {
            ImGui.text(String.format("FPS: %.0f", 1f / ImGui.getIO().getDeltaTime()));
        }
    }
    
    private void renderGameView() {
        int textureId = gameEngine.getFramebufferTexture();
        
        // Flip UVs for OpenGL
        ImGui.image(textureId, viewportWidth, viewportHeight, 0, 1, 1, 0);
        
        // Handle input when focused
        if (isFocused && playModeManager.isPlaying()) {
            gameEngine.setInputEnabled(true);
            routeInputToGame();
        } else {
            gameEngine.setInputEnabled(false);
        }
    }
    
    private void renderPlaceholder() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.1f, 0.1f, 0.1f, 1f);
        ImGui.beginChild("placeholder", viewportWidth, viewportHeight);
        
        String message = "Press Play to start";
        float textWidth = ImGui.calcTextSize(message).x;
        ImGui.setCursorPos((viewportWidth - textWidth) / 2, viewportHeight / 2);
        ImGui.textDisabled(message);
        
        ImGui.endChild();
        ImGui.popStyleColor();
    }
    
    private void routeInputToGame() {
        // Convert ImGui mouse position to game coordinates
        // Forward to game Input system
    }
}
```

---

## Scene Conversion

Converting EditorScene to runtime Scene.

```java
public class SceneConverter {
    
    public static Scene convert(EditorScene editorScene) {
        Scene scene = new Scene(editorScene.getName());
        
        // 1. Convert tilemap layers
        for (TilemapLayer layer : editorScene.getLayers()) {
            GameObject layerGO = convertLayer(layer);
            scene.addGameObject(layerGO);
        }
        
        // 2. Convert entities
        for (EditorEntity entity : editorScene.getEntities()) {
            GameObject entityGO = convertEntity(entity);
            scene.addGameObject(entityGO);
        }
        
        // 3. Convert UI canvases (from UI Support feature)
        for (EditorUICanvas canvas : editorScene.getUICanvases()) {
            GameObject canvasGO = UISceneBuilder.build(canvas);
            scene.addGameObject(canvasGO);
        }
        
        // 4. Apply camera settings
        applyCameraSettings(scene, editorScene.getCameraSettings());
        
        // 5. Apply collision map
        scene.setCollisionMap(editorScene.getCollisionMap());
        
        return scene;
    }
    
    private static GameObject convertEntity(EditorEntity entity) {
        GameObject go = new GameObject(entity.getName());
        
        // Set position
        go.getTransform().setPosition(entity.getPosition());
        
        // Instantiate components
        for (ComponentData compData : entity.getComponents()) {
            Component comp = ComponentFactory.instantiate(compData);
            go.addComponent(comp);
        }
        
        return go;
    }
    
    private static void applyCameraSettings(Scene scene, SceneCameraSettings settings) {
        Camera camera = scene.getCamera();
        camera.setPosition(settings.getPosition());
        camera.setOrthographicSize(settings.getOrthographicSize());
        // ... other settings
    }
}
```

### ComponentFactory

Instantiates components from ComponentData.

```java
public class ComponentFactory {
    
    public static Component instantiate(ComponentData data) {
        Class<? extends Component> clazz = ComponentRegistry.getByName(data.getType());
        
        if (clazz == null) {
            throw new RuntimeException("Unknown component: " + data.getType());
        }
        
        try {
            // Create instance (requires no-arg constructor)
            Component component = clazz.getDeclaredConstructor().newInstance();
            
            // Apply field values
            for (var entry : data.getFields().entrySet()) {
                Field field = findField(clazz, entry.getKey());
                if (field != null) {
                    field.setAccessible(true);
                    Object value = convertValue(field.getType(), entry.getValue());
                    field.set(component, value);
                }
            }
            
            return component;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + data.getType(), e);
        }
    }
    
    private static Object convertValue(Class<?> targetType, Object value) {
        // Handle type conversions from JSON
        // List<Double> → Vector3f
        // String → Enum
        // String → Asset reference
        // etc.
    }
}
```

---

## Input Routing

### Problem

ImGui captures all keyboard/mouse input. Game needs input only when Game View is focused.

### Solution

```java
public class EditorInputRouter {
    
    private boolean gameViewFocused = false;
    private EmbeddedGameEngine gameEngine;
    
    // Called each frame
    public void update() {
        if (gameViewFocused && playModeManager.isPlaying()) {
            // Route raw input to game Input system
            forwardMouseToGame();
            forwardKeyboardToGame();
        }
    }
    
    private void forwardMouseToGame() {
        // Get ImGui mouse position
        ImVec2 mousePos = ImGui.getMousePos();
        
        // Convert to game viewport coordinates
        float gameX = (mousePos.x - gameViewportX) / gameViewportWidth * gameWidth;
        float gameY = (mousePos.y - gameViewportY) / gameViewportHeight * gameHeight;
        
        // Update game Input system
        GameInput.setMousePosition(gameX, gameY);
        
        // Forward button states
        GameInput.setMouseButton(0, ImGui.isMouseDown(0));
        GameInput.setMouseButton(1, ImGui.isMouseDown(1));
    }
    
    private void forwardKeyboardToGame() {
        // Check each key the game cares about
        // Forward to game Input system
        
        // Note: ImGui.getIO().getWantCaptureKeyboard() should be false
        // when Game View is focused
    }
}
```

### Game Input Adapter

Wrapper that allows game code to use normal Input API during play mode.

```java
public class PlayModeInputAdapter {
    
    private static boolean[] keyStates = new boolean[256];
    private static boolean[] mouseButtons = new boolean[8];
    private static float mouseX, mouseY;
    
    // Called by EditorInputRouter
    public static void setKeyState(int key, boolean pressed) {
        keyStates[key] = pressed;
    }
    
    public static void setMouseButton(int button, boolean pressed) {
        mouseButtons[button] = pressed;
    }
    
    public static void setMousePosition(float x, float y) {
        mouseX = x;
        mouseY = y;
    }
    
    // Called by game Input system when in play mode
    public static boolean getKey(int keyCode) {
        return keyStates[keyCode];
    }
    
    public static boolean getMouseButton(int button) {
        return mouseButtons[button];
    }
    
    public static Vector2f getMousePosition() {
        return new Vector2f(mouseX, mouseY);
    }
}
```

---

## Editor Panel Locking

Disable editor panels during play mode.

```java
public class EditorUIController {
    
    public void renderUI() {
        boolean playing = playModeManager.isPlaying();
        
        // Lock panels during play
        if (playing) {
            ImGui.pushItemFlag(ImGuiItemFlags.Disabled, true);
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.5f);
        }
        
        // Render panels (they appear but can't be interacted with)
        layerPanel.render();
        hierarchyPanel.render();
        inspectorPanel.render();
        // etc.
        
        if (playing) {
            ImGui.popStyleVar();
            ImGui.popItemFlag();
        }
        
        // Game View is always interactive
        gameViewPanel.render();
    }
}
```

---

## State Snapshot

### Serialization Approach

```java
public class SceneSnapshot {
    
    public static byte[] capture(EditorScene scene) {
        // Use existing scene serialization
        SceneData data = EditorSceneSerializer.toSceneData(scene);
        
        // Serialize to JSON bytes
        String json = Serializer.toJson(data);
        return json.getBytes(StandardCharsets.UTF_8);
    }
    
    public static EditorScene restore(byte[] snapshot) {
        String json = new String(snapshot, StandardCharsets.UTF_8);
        SceneData data = Serializer.fromJson(json, SceneData.class);
        
        return EditorSceneSerializer.fromSceneData(data, null);
    }
}
```

### Memory Considerations

- Snapshot is held in memory during play
- Large scenes with many chunks could be significant
- Alternative: write to temp file

---

## Implementation Phases

### Phase 1: Core Infrastructure
1. Create `PlayModeManager` with state machine
2. Create `GameViewPanel` shell with toolbar
3. Wire Play/Pause/Stop buttons
4. Add panel to EditorUIController

### Phase 2: Scene Conversion
1. Implement `SceneConverter.convert()`
2. Create `ComponentFactory` for instantiation
3. Test with simple scene (tilemap only)
4. Handle entities

### Phase 3: Embedded Game Engine
1. Create `EmbeddedGameEngine` with framebuffer
2. Implement update loop
3. Implement rendering
4. Display in Game View panel

### Phase 4: Input Routing
1. Create `EditorInputRouter`
2. Implement `PlayModeInputAdapter`
3. Configure game Input to use adapter during play
4. Test with PlayerMovement component

### Phase 5: State Management
1. Implement `SceneSnapshot.capture()`
2. Implement `SceneSnapshot.restore()`
3. Snapshot on Play, restore on Stop
4. Handle edge cases (dirty scene, etc.)

### Phase 6: Polish
1. Panel locking during play
2. Play mode visual indicator (tinted toolbar?)
3. Error handling (component instantiation failures)
4. Performance stats in Game View

---

## Open Questions

1. **Shared framebuffer**: Game View and Scene View use separate framebuffers. OK or share?

2. **Time management**: Who controls deltaTime during play? ImGui.getIO().getDeltaTime() or separate timer?

3. **Audio**: Your engine has audio? Need to initialize audio system in play mode?

4. **Scripting/Events**: If components register event listeners during start(), need to clean up on stop.

5. **Scene references**: Runtime Scene stores references to GameObjects. EditorScene restores from snapshot. Different object instances - is that OK?

6. **Play from here**: Allow playing from a specific position (not scene start)? Future feature.

7. **Multiple cameras**: If scene has multiple cameras, which one renders in Game View?

---

## Integration Points

```
┌─────────────────────────────────────────────────────────────────┐
│                     EditorApplication                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  loop() {                                                       │
│      ...                                                        │
│      update() {                                                 │
│          // Existing editor update                              │
│          toolController.processShortcuts();                     │
│                                                                  │
│  +       // Play mode update                                    │
│  +       if (playModeManager.isPlaying()) {                     │
│  +           playModeManager.update(deltaTime);                 │
│  +       }                                                      │
│      }                                                          │
│                                                                  │
│      render() {                                                 │
│          // Existing editor render                              │
│          sceneRenderer.render(scene, camera);                   │
│                                                                  │
│  +       // Game view render (if playing)                       │
│  +       if (playModeManager.isPlaying()) {                     │
│  +           embeddedGameEngine.render();                       │
│  +       }                                                      │
│                                                                  │
│          // ImGui                                               │
│          imGuiLayer.newFrame();                                 │
│          uiController.renderUI();                               │
│  +       gameViewPanel.render();    // NEW                      │
│          imGuiLayer.render();                                   │
│      }                                                          │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Toolbar Indicator

Visual feedback that editor is in play mode:

```java
// In EditorMenuBar or main toolbar
if (playModeManager.isPlaying()) {
    ImGui.pushStyleColor(ImGuiCol.MenuBarBg, 0.4f, 0.2f, 0.2f, 1f);  // Red tint
}

// ... render menu bar ...

if (playModeManager.isPlaying()) {
    ImGui.popStyleColor();
}
```

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Component requires constructor args | High | Document no-arg constructor requirement |
| Memory leak on stop | High | Explicit destroy() on all game objects |
| Input conflicts | Medium | Clear input state on state transitions |
| Snapshot too large | Low | Consider temp file for large scenes |
| Game crashes during play | Medium | Try-catch around game update, auto-stop |
