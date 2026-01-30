# Unified Rendering Architecture

## Overview

This document explicitly defines which renderer is used where and why.

## Renderer Responsibilities

### UnifiedRenderer / RenderPipeline
**Used for: Runtime game rendering**

| Context | Class | Why UnifiedRenderer |
|---------|-------|---------------------|
| Standalone Game | `GameApplication` | Full pipeline: scene → post-fx → UI → overlay |
| Play Mode | `PlayModeController` | Same as standalone game |
| Game Preview (stopped) | `GameViewPanel` | Preview needs game camera, no editor features |

**Features:**
- Post-processing effects
- UI canvas rendering
- Transition overlays
- Frustum culling
- Z-sorted renderables

### EditorSceneRenderer
**Used for: Editor viewport only**

| Context | Class | Why NOT UnifiedRenderer |
|---------|-------|-------------------------|
| Scene Viewport | `EditorApplication` | Requires editor-specific features |

**Editor-specific features NOT in UnifiedRenderer:**
- Per-layer tint/dimming (visibility modes)
- LayerUtils integration for editor rendering rules
- EntityRenderer with selection highlighting
- No post-processing (editor shows raw scene)
- No UI canvas rendering (editor has own UI)

### Renderers NOT using UnifiedRenderer (by design)

| Renderer | Reason |
|----------|--------|
| `EditorSceneRenderer` | Editor-specific layer dimming, selection visuals |
| `CollisionOverlayRenderer` | ImGui DrawList overlay, not OpenGL |
| `CameraOverlayRenderer` | ImGui DrawList overlay, not OpenGL |
| `GridOverlayRenderer` | ImGui DrawList overlay, not OpenGL |

## File Mapping

```
┌─────────────────────────────────────────────────────────────────┐
│                     STANDALONE GAME                              │
│  GameApplication                                                 │
│    └── UnifiedRenderer (RenderPipeline)                         │
│          ├── SceneRenderer (tilemaps, sprites)                  │
│          ├── PostProcessor (blur, vignette, etc.)               │
│          ├── UIRenderer (canvases)                              │
│          └── OverlayRenderer (transitions)                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     EDITOR - PLAY MODE                           │
│  PlayModeController                                              │
│    └── RenderPipeline                                           │
│          ├── SceneRenderer                                      │
│          ├── PostProcessor                                      │
│          ├── UIRenderer                                         │
│          └── OverlayRenderer                                    │
│    └── EditorFramebuffer → ImGui.image()                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     EDITOR - GAME VIEW PREVIEW                   │
│  GameViewPanel (when stopped)                                    │
│    └── RenderPipeline (scene only, no post-fx)                  │
│          └── SceneRenderer                                      │
│    └── EditorFramebuffer → ImGui.image()                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     EDITOR - SCENE VIEWPORT                      │
│  EditorApplication                                               │
│    └── EditorSceneRenderer  ← NOT UnifiedRenderer               │
│          ├── SceneRenderingBackend (tilemaps with tints)        │
│          └── EntityRenderer (sprites with selection)            │
│    └── EditorFramebuffer → ViewportRenderer → ImGui.image()     │
│                                                                  │
│  ImGui Overlays (drawn on top):                                 │
│    ├── GridOverlayRenderer                                      │
│    ├── CollisionOverlayRenderer                                 │
│    ├── CameraOverlayRenderer                                    │
│    └── Tool overlays                                            │
└─────────────────────────────────────────────────────────────────┘
```

## Phase 6 Deletions

After integration, these become obsolete:

| File | Replaced By |
|------|-------------|
| `PlayModeRenderer.java` | `RenderPipeline` in `PlayModeController` |
| `GamePreviewRenderer.java` | `RenderPipeline` in `GameViewPanel` |
| `OpenGLRenderer.java` | `RenderPipeline` (when GameApplication migrates) |

**Kept (not replaced):**
| File | Reason |
|------|--------|
| `EditorSceneRenderer.java` | Editor-specific features |
| `SceneRenderingBackend.java` | Used by EditorSceneRenderer |
| `EntityRenderer.java` | Used by EditorSceneRenderer |

## Integration Checklist

- [ ] `PlayModeController` → uses `RenderPipeline`
- [ ] `GameViewPanel` → uses `RenderPipeline` for preview
- [ ] `GameApplication` → uses `UnifiedRenderer` (Phase 5b)
- [ ] `EditorSceneRenderer` → **unchanged** (intentionally)
- [ ] Delete `PlayModeRenderer.java`
- [ ] Delete `GamePreviewRenderer.java`
