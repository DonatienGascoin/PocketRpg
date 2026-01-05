```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#e1f5fe', 'primaryTextColor': '#01579b', 'primaryBorderColor': '#0288d1', 'lineColor': '#0288d1', 'secondaryColor': '#fff3e0', 'tertiaryColor': '#f3e5f5'}}}%%

flowchart TB
    subgraph Views["<b>Application Views</b>"]
        direction TB
        GA["<b>Game Application</b><br/>(Standalone Game)"]
        GP["<b>Game Panel</b><br/>(Editor Preview/Play)"]
        SP["<b>Scene Panel</b><br/>(Editor Scene View)"]
        UD["<b>UI Designer</b><br/>(Editor UI View)"]
    end

    subgraph Renderers["<b>View Renderers</b>"]
        direction TB
        OGR["OpenGLRenderer"]
        PMR["PlayModeRenderer"]
        GPR["GamePreviewRenderer"]
        ESR["EditorSceneRenderer"]
        UIR["UIRenderingBackend"]
    end

    subgraph Core["<b>Shared Core</b>"]
        direction TB
        SRB["SceneRenderingBackend"]
        RP["RenderPipeline"]
        BR["BatchRenderer"]
        SB["SpriteBatch"]
        CS["CullingSystem"]
    end

    subgraph Cameras["<b>Cameras (RenderCamera)</b>"]
        direction LR
        GC["GameCamera"]
        EC["EditorCamera"]
        PC["PreviewCamera"]
    end

    subgraph Support["<b>Support Classes</b>"]
        direction TB
        ER["EntityRenderer"]
        EFB["EditorFramebuffer"]
        SH["Shader"]
    end

    %% View → Renderer connections
    GA --> OGR
    GP -->|"Play Mode"| PMR
    GP -->|"Preview Mode"| GPR
    SP --> ESR
    UD --> UIR

    %% Renderer → Core connections
    OGR --> RP
    PMR --> OGR
    GPR --> SRB
    ESR --> SRB
    UIR --> SB

    RP --> BR
    SRB --> BR
    SRB --> CS
    BR --> SB

    %% Renderer → Camera connections
    OGR -.->|"uses"| GC
    PMR -.->|"uses"| GC
    GPR -.->|"uses"| PC
    ESR -.->|"uses"| EC
    PC -.->|"wraps"| GC

    %% Support connections
    GPR --> ER
    ESR --> ER
    GPR --> EFB
    ESR --> EFB
    UIR --> EFB
    BR --> SH
    UIR --> SH

    %% Styling
    classDef viewStyle fill:#e3f2fd,stroke:#1976d2,stroke-width:2px,color:#0d47a1
    classDef rendererStyle fill:#fff3e0,stroke:#f57c00,stroke-width:2px,color:#e65100
    classDef coreStyle fill:#e8f5e9,stroke:#388e3c,stroke-width:2px,color:#1b5e20
    classDef cameraStyle fill:#fce4ec,stroke:#c2185b,stroke-width:2px,color:#880e4f
    classDef supportStyle fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#4a148c

    class GA,GP,SP,UD viewStyle
    class OGR,PMR,GPR,ESR,UIR rendererStyle
    class SRB,RP,BR,SB,CS coreStyle
    class GC,EC,PC cameraStyle
    class ER,EFB,SH supportStyle
```

---

## Class Relationships Detailed

### RenderCamera Interface
```
┌─────────────────────────────────────────────────────────────┐
│                    <<interface>>                             │
│                     RenderCamera                             │
├─────────────────────────────────────────────────────────────┤
│ + getProjectionMatrix(): Matrix4f                           │
│ + getViewMatrix(): Matrix4f                                  │
│ + getWorldBounds(): float[]                                  │
│ + worldToScreen(x, y): Vector2f                              │
│ + screenToWorld(x, y): Vector3f                              │
└─────────────────────────────────────────────────────────────┘
                              △
                              │ implements
          ┌───────────────────┼───────────────────┐
          │                   │                   │
   ┌──────┴──────┐    ┌──────┴──────┐    ┌──────┴──────┐
   │  GameCamera  │    │ EditorCamera │    │PreviewCamera│
   │              │    │              │    │   wraps     │
   │ orthoSize    │    │ pixelsPerUnit│    │  GameCamera │
   │ ViewportCfg  │    │ zoom/pan     │    │             │
   └──────────────┘    └──────────────┘    └─────────────┘
```

### Rendering Flow by View

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            GAME APPLICATION                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   GameApplication                                                            │
│        │                                                                     │
│        ▼                                                                     │
│   OpenGLRenderer ──────► RenderPipeline ──────► BatchRenderer               │
│        │                      │                      │                       │
│        │                      ▼                      ▼                       │
│        │              CullingSystem            SpriteBatch                   │
│        │                      │                      │                       │
│        │                      └──────────┬───────────┘                       │
│        │                                 │                                   │
│        ▼                                 ▼                                   │
│   GameCamera ◄─────────────────── Scene.getRenderers()                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                         GAME PANEL (PLAY MODE)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   GameViewPanel                                                              │
│        │                                                                     │
│        ▼                                                                     │
│   PlayModeRenderer ──────► OpenGLRenderer ──────► RenderPipeline            │
│        │                                               │                     │
│        ▼                                               ▼                     │
│   RuntimeScene ◄──────────────────────────────── GameCamera                 │
│        │                                                                     │
│        ▼                                                                     │
│   EditorFramebuffer ──────► ImGui.image(textureId)                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        GAME PANEL (PREVIEW MODE)                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   GameViewPanel                                                              │
│        │                                                                     │
│        ▼                                                                     │
│   GamePreviewRenderer ──────► SceneRenderingBackend                         │
│        │                            │                                        │
│        │                            ├──► BatchRenderer ──► SpriteBatch      │
│        │                            └──► CullingSystem                       │
│        │                                                                     │
│        ├──► EntityRenderer                                                   │
│        │                                                                     │
│        ▼                                                                     │
│   PreviewCamera ─wraps─► GameCamera                                         │
│        │                                                                     │
│        ▼                                                                     │
│   EditorFramebuffer ──────► ImGui.image(textureId)                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              SCENE PANEL                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ScenePanel                                                                 │
│        │                                                                     │
│        ▼                                                                     │
│   EditorSceneRenderer ──────► SceneRenderingBackend                         │
│        │                            │                                        │
│        │                            ├──► BatchRenderer ──► SpriteBatch      │
│        │                            └──► CullingSystem                       │
│        │                                                                     │
│        ├──► EntityRenderer                                                   │
│        │                                                                     │
│        ▼                                                                     │
│   EditorCamera (free pan/zoom)                                              │
│        │                                                                     │
│        ▼                                                                     │
│   EditorFramebuffer ──────► ImGui.image(textureId)                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              UI DESIGNER                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   UIDesignerPanel                                                            │
│        │                                                                     │
│        ▼                                                                     │
│   UIRenderingBackend ──────► SpriteBatch                                    │
│        │                                                                     │
│        │  (Screen-space coords: origin top-left, Y-down)                    │
│        │                                                                     │
│        ▼                                                                     │
│   EditorFramebuffer ──────► ImGui.image(textureId)                          │
│        │                                                                     │
│        │  Interaction: ImGui mouse events → Java hit testing                │
│        │                                                                     │
│        ▼                                                                     │
│   [Handles, Selection, Drag all via ImGui input + Java logic]               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Summary

| View | Camera | Renderer | Backend | Output |
|------|--------|----------|---------|--------|
| **Game App** | GameCamera | OpenGLRenderer | RenderPipeline → BatchRenderer | Screen (default FBO) |
| **Game Panel (Play)** | GameCamera | PlayModeRenderer → OpenGLRenderer | RenderPipeline → BatchRenderer | EditorFramebuffer → ImGui |
| **Game Panel (Preview)** | PreviewCamera | GamePreviewRenderer | SceneRenderingBackend | EditorFramebuffer → ImGui |
| **Scene Panel** | EditorCamera | EditorSceneRenderer | SceneRenderingBackend | EditorFramebuffer → ImGui |
| **UI Designer** | None (screen-space) | UIRenderingBackend | SpriteBatch direct | EditorFramebuffer → ImGui |

---

## Key Design Decisions

### 1. SceneRenderingBackend as Shared Core
Both `EditorSceneRenderer` and `GamePreviewRenderer` use `SceneRenderingBackend` which owns:
- `BatchRenderer` → `SpriteBatch` for drawing
- `CullingSystem` for frustum culling

This eliminates duplicate tilemap rendering logic.

### 2. RenderCamera Interface
All cameras implement `RenderCamera`:
- `GameCamera` - runtime, uses `orthographicSize` + `ViewportConfig`
- `EditorCamera` - editor scene view, uses `pixelsPerUnit` + free pan/zoom
- `PreviewCamera` - wraps `GameCamera` to ensure preview matches runtime

### 3. PreviewCamera Wraps GameCamera
Ensures preview mode uses **identical math** to runtime:
```java
public class PreviewCamera implements RenderCamera {
    private final GameCamera camera;  // Same math as runtime
    
    public void applySceneSettings(Vector2f pos, float orthoSize) {
        camera.setPosition(pos.x, pos.y);
        camera.setOrthographicSize(orthoSize);
    }
}
```

### 4. UIRenderingBackend Uses Screen-Space
UI Designer uses screen-space coordinates (Y-down) matching the UI system:
```java
projectionMatrix.ortho(0, width, height, 0, -1000, 1000);  // Y-down
```

### 5. EntityRenderer is Stateless
`EntityRenderer` receives `SpriteBatch` from caller:
```java
entityRenderer.render(backend.getSpriteBatch(), scene);
```
No state, easily shared across renderers.
