# Plan 6: Web Platform Implementation

**Goal:** Implement all web backends and deliver a playable game in Chrome and Firefox.

**Prerequisites:** Plan 5.

**Status:** Not started

---

## Dependency Graph

```
Plan 5 (multi-module + stubs)
   |
   v
Plan 6 (this plan)
   |
   v
(Future: Android plan)
```

---

## Deliverables

### 1. `WebGLRenderDevice` (5 sub-backends via TeaVM JSO / WebGL 2.0)

Full implementations of all 5 backend interfaces from Plan 1:

- **`WebGLBufferBackend`** — VAO/VBO via `WebGL2RenderingContext`
- **`WebGLTextureBackend`** — Texture operations via WebGL
- **`WebGLShaderBackend`** — Shader compilation with ES 3.0 headers (from Plan 4)
- **`WebGLFramebufferBackend`** — FBO operations via WebGL
- **`WebGLStateBackend`** — Blend, viewport, clear via WebGL

All using TeaVM JSO bindings to JavaScript WebGL API.

### 2. `CanvasWindow`

- Implements `AbstractWindow` (or equivalent windowing interface)
- Manages HTML5 canvas element sizing
- Handles `devicePixelRatio` for high-DPI displays
- Resize handling via `ResizeObserver`

### 3. `WebInputBackend`

- Keyboard input via DOM `keydown` / `keyup` events
- Mouse input via DOM `mousedown` / `mouseup` / `mousemove` events
- Maps browser key codes to game input actions
- **Release all keys on `blur`** event (prevent stuck keys when tabbing away)
- Prevent default browser shortcuts that conflict with game controls (arrow key scrolling, etc.)

### 4. `WebAudioBackend`

- Web Audio API implementation
- **User gesture gate:** Audio context created/resumed on first click (browser autoplay policy)
- Sound effects via `AudioBufferSourceNode`
- Music via streaming or pre-decoded buffers
- **Safari MP3 fallback:** Detect OGG support, fall back to MP3 (converted in Plan 5 build)
- Volume control, mute support

### 5. `WebAssetProvider`

- Fetch API for loading assets over HTTP
- Per-scene preloading from asset manifest (generated in Plan 5)
- Loading flow:
  1. Fetch `asset-manifest.json`
  2. Before scene transition: preload all assets for target scene
  3. Game code calls `readBytes()` / `readString()` synchronously from cache
- Cache management: assets kept in memory after loading

### 6. `WebSaveProvider`

- `localStorage` implementation
- Keys prefixed to avoid collision (e.g., `pocketrpg_save_`)
- Handles `localStorage` quota errors gracefully

### 7. `WebImageDecoder`

- Decode images using `HTMLImageElement`
- Load via `Image()` constructor + `onload` callback
- Extract pixel data via offscreen `Canvas` + `getImageData()`
- Returns decoded pixel data for texture upload

### 8. Pre-rasterized font atlas

- Fonts pre-rasterized at build time into texture atlas
- Web platform loads atlas as a regular texture
- No runtime font rasterization needed (STB TrueType not available on web)

### 9. `WebLoop`

- `requestAnimationFrame`-based game loop
- Calls `GameRunner.processFrame()` (from Plan 4) each frame
- Handles frame timing, delta time calculation
- Integrates with `TimeContext` pause/resume

### 10. `WebMain` entry point

Startup sequence:
1. Fetch game config
2. Show loading overlay with progress bar
3. Preload global assets + first scene assets
4. Show "Click to Start" button (required for audio context)
5. On click: create audio context, start game loop
6. Hide overlay, game begins

### 11. Visibility change handling

- `visibilitychange` event listener
- On hidden: pause `WebLoop`, freeze `TimeContext`, suspend audio
- On visible: resume `WebLoop`, resume `TimeContext` (with delta clamp from Plan 4), resume audio
- Prevents: time explosion after long background, wasted CPU in background tab

### 12. WebGL context loss handling

- `webglcontextlost` event: pause game, show "Context Lost" message
- `webglcontextrestored` event: re-upload all GPU resources via `GPUResourceTracker` (from Plan 1), resume
- Prevent default on `webglcontextlost` (enables restoration)

### 13. Fetch error handling

- 10-second timeout per fetch request
- 2 retries with exponential backoff
- On final failure: show error screen with retry button
- Retry button reloads the failed assets and resumes

### 14. Global error handling

- `window.onerror` handler for uncaught exceptions
- Display error overlay with message and stack trace
- Include game state info for debugging (current scene, frame count)

### 15. `index.html`

- Loading overlay with progress bar
- Canvas element (full viewport)
- Start button (for audio gesture gate)
- Error overlay (hidden by default)
- Minimal CSS for layout
- `<script>` tag loading TeaVM-compiled JS

---

## Success Criteria

- [ ] Game loads in Chrome and Firefox
- [ ] First scene renders correctly
- [ ] Keyboard navigation works
- [ ] Scene transitions work with loading indicator
- [ ] Audio plays after click-to-start
- [ ] 30fps+ on mid-range laptop
- [ ] Tab background/restore works without artifacts or time explosion
- [ ] Save/load works via localStorage
- [ ] Fetch failures show error with retry option
- [ ] Safari: renders and plays audio (MP3 fallback)
- [ ] High-DPI displays render at correct resolution
- [ ] No stuck keys after tab switch

---

## QA Integration

### Cross-browser testing
- Chrome (primary target)
- Firefox
- Safari (WebGL 2.0 + MP3 audio)
- Mobile Chrome (touch not supported, but should render)

### Performance profiling
- Browser dev tools: FPS counter, frame timing
- Memory profiling: check for leaks on scene transitions
- Target: 30fps+ on mid-range laptop, 60fps on modern hardware

### Context loss testing
- Use `WEBGL_lose_context` extension to simulate context loss
- Verify recovery: game resumes, textures re-uploaded, no visual artifacts

### Network failure testing
- Chrome DevTools: throttle to slow 3G, offline
- Verify: timeout, retry, error screen, retry button works
- Test: partial asset load failure mid-scene-transition

### Save/load testing
- Verify localStorage save/load cycle
- Test quota exceeded scenario
- Test private browsing mode (localStorage may be restricted)

---

## Implementation Order

1. Set up TeaVM JSO type bindings for WebGL 2.0
2. Implement `WebGLRenderDevice` (all 5 sub-backends)
3. Implement `WebImageDecoder`
4. Implement `CanvasWindow`
5. Test: static triangle renders in browser (validate pipeline)
6. Implement `WebAssetProvider` with fetch + preloading
7. Implement `WebInputBackend`
8. Implement `WebLoop` with `requestAnimationFrame`
9. Implement `WebMain` entry point (config → preload → start)
10. Test: first scene renders with input
11. Implement `WebAudioBackend` with gesture gate
12. Implement `WebSaveProvider`
13. Implement visibility change handling
14. Implement WebGL context loss handling
15. Implement fetch error handling + retry
16. Implement `window.onerror` error reporting
17. Create `index.html` with overlays
18. Build pre-rasterized font atlas pipeline
19. Cross-browser testing (Chrome, Firefox, Safari)
20. Performance profiling and optimization
21. Final QA pass

---

## Android (Future)

Deferred to a standalone plan after this one. The architecture from Plans 1-5 makes it straightforward:

- `RenderDevice` -> GLES 3.0 implementation
- `AssetProvider` -> Android `AssetManager` wrapper
- `SaveProvider` -> internal storage
- `AudioBackend` -> SoundPool + MediaPlayer
- `GPUResourceTracker` -> context loss recovery (designed in Plan 1, activated here)
- `GameRunner` -> `GLSurfaceView.Renderer.onDrawFrame()`
- Plus Android-specific: lifecycle, threading, touch input, orientation lock, Gradle module
