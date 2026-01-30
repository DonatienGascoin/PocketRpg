# Particle System Implementation Plan

## Overview

A CPU-based particle system for the Pocket RPG Engine, following existing architectural patterns (component-based, dedicated renderer, object pooling).

**Scope (Phase 1):**
- `ParticleEmitter` component
- Emission shapes: Point, Circle, Cone, Box
- Color/Size over lifetime curves
- World-space vs Local-space particles
- Alpha and Additive blending modes
- Object pooling for particles
- Dedicated `ParticleRenderer`

**Deferred:**
- Frustum culling integration (Phase 2)
- Texture atlas / animated particles
- Physics (gravity, drag, collision)
- Sub-emitters
- GPU compute shaders

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         COMPONENT LAYER                             │
├─────────────────────────────────────────────────────────────────────┤
│  ParticleEmitter (Component)                                        │
│  ├── EmitterConfig (emission rate, max particles, duration, etc.)   │
│  ├── ParticleConfig (lifetime, speed, size curve, color curve)      │
│  ├── EmissionShape (Point, Circle, Cone, Box)                       │
│  └── SimulationSpace (WORLD, LOCAL)                                 │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         SIMULATION LAYER                            │
├─────────────────────────────────────────────────────────────────────┤
│  ParticlePool                                                       │
│  ├── Pre-allocated Particle array                                   │
│  ├── Active/inactive tracking via indices                           │
│  └── Zero-allocation spawn/despawn                                  │
│                                                                     │
│  Particle (struct-like class)                                       │
│  ├── position, velocity (Vector2f)                                  │
│  ├── lifetime, age (float)                                          │
│  ├── size, rotation, alpha (float)                                  │
│  └── color (Vector4f)                                               │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         RENDERING LAYER                             │
├─────────────────────────────────────────────────────────────────────┤
│  ParticleRenderer                                                   │
│  ├── ParticleBatch (similar to SpriteBatch)                         │
│  ├── Dedicated shader (particle.glsl)                               │
│  ├── Blending mode switching (Alpha, Additive)                      │
│  └── Renders all emitters in scene                                  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Phase Breakdown

### Phase 1: Core Data Structures
**Goal:** Define particle data and pooling system.

**Classes:**
- `Particle` - Lightweight particle data holder
- `ParticlePool` - Pre-allocated pool with O(1) spawn/despawn
- `ParticleConfig` - Runtime particle behavior settings
- `EmitterConfig` - Emitter behavior settings

**Key Decisions:**
- `Particle` is a plain class (not a Component) for performance
- Pool uses array + active count pattern (no LinkedList)
- Configs are immutable data objects (can be shared across emitters)

---

### Phase 2: Emission Shapes
**Goal:** Define where/how particles spawn.

**Classes:**
- `EmissionShape` (interface)
- `PointShape` - Single point emission
- `CircleShape` - Random within circle (edge or fill)
- `ConeShape` - Directional cone with angle spread
- `BoxShape` - Random within rectangle

**Key Decisions:**
- Shapes output position offset + initial velocity direction
- Shapes are stateless, reusable across emitters

---

### Phase 3: Lifetime Curves
**Goal:** Animate particle properties over lifetime.

**Classes:**
- `ParticleCurve` (interface) - Maps normalized time (0-1) to value
- `ConstantCurve` - Returns fixed value
- `LinearCurve` - Linear interpolation between two values
- `EaseInOutCurve` - Smooth interpolation
- `ColorGradient` - Interpolates between color stops

**Usage:**
```java
ParticleConfig config = ParticleConfig.builder()
    .sizeCurve(new LinearCurve(0.5f, 0.0f))      // Shrink over time
    .alphaCurve(new LinearCurve(1.0f, 0.0f))    // Fade out
    .colorGradient(ColorGradient.of(Color.YELLOW, Color.RED))
    .build();
```

---

### Phase 4: ParticleEmitter Component
**Goal:** The main component users interact with.

**Class:** `ParticleEmitter extends Component`

**Responsibilities:**
- Owns a `ParticlePool`
- Spawns particles based on `EmitterConfig.emissionRate`
- Updates all active particles each frame
- Applies `ParticleConfig` curves during update
- Handles World vs Local simulation space

**Lifecycle:**
```
onStart()     → Allocate pool based on maxParticles
update(dt)    → Spawn new particles, update existing, despawn dead
onDisable()   → Optionally clear particles or let them finish
onDestroy()   → Release pool
```

**API Preview:**
```java
ParticleEmitter emitter = new ParticleEmitter();
emitter.setEmitterConfig(EmitterConfig.builder()
    .maxParticles(500)
    .emissionRate(50)        // particles/second
    .duration(2.0f)          // emitter lifetime (-1 for infinite)
    .loop(true)
    .build());

emitter.setParticleConfig(ParticleConfig.builder()
    .lifetime(1.5f)
    .startSpeed(5.0f)
    .startSize(0.5f)
    .sizeCurve(new LinearCurve(1.0f, 0.0f))
    .startColor(Color.WHITE)
    .colorGradient(ColorGradient.of(Color.YELLOW, Color.ORANGE, Color.RED))
    .build());

emitter.setEmissionShape(new ConeShape(45f));  // 45° spread
emitter.setSimulationSpace(SimulationSpace.WORLD);
emitter.setBlendMode(BlendMode.ADDITIVE);

gameObject.addComponent(emitter);
```

---

### Phase 5: ParticleRenderer
**Goal:** Efficient batched rendering of all particles.

**Classes:**
- `ParticleRenderer` - Integrates with engine render loop
- `ParticleBatch` - Batches particles by texture + blend mode

**Integration Points:**
- Created by `PlatformFactory.createParticleRenderer()`
- Owned by `GameEngine`, rendered after world but before UI
- Receives list of `ParticleEmitter` from scene

**Render Order:**
```
1. World (sprites, tilemaps) via RenderPipeline
2. Particles via ParticleRenderer          ← NEW
3. Post-processing
4. UI via UIRenderer
```

**Shader Requirements:**
- Vertex: position, UV, color (with alpha)
- Fragment: texture sample × vertex color
- Blend mode set per batch (Alpha or Additive)

---

### Phase 6: Scene Integration
**Goal:** Wire emitters into scene lifecycle and rendering.

**Changes to `Scene`:**
- Track `ParticleEmitter` components (similar to `Renderable` list)
- Provide `getParticleEmitters()` for renderer

**Changes to `GameEngine`:**
- Call `particleRenderer.render(scene.getParticleEmitters(), camera)`

**Changes to `PlatformFactory`:**
- Add `createParticleRenderer()` method
- `GLFWPlatformFactory` returns `OpenGLParticleRenderer`

---

### Phase 7: Culling Preparation (Stub)
**Goal:** Design interface for future culling integration.

**Approach:**
- `ParticleEmitter` implements `Cullable` interface (bounds-based)
- Bounds = emitter position + max particle travel distance
- Actual culling logic deferred to Phase 2

```java
public interface Cullable {
    AABB getBounds();
    boolean isVisible();  // Set by CullingSystem
}
```

---

## File Structure

```
src/main/java/com/pocket/rpg/
├── particles/
│   ├── Particle.java
│   ├── ParticlePool.java
│   ├── ParticleConfig.java
│   ├── EmitterConfig.java
│   ├── ParticleEmitter.java          (Component)
│   ├── SimulationSpace.java          (enum)
│   ├── BlendMode.java                (enum)
│   ├── shapes/
│   │   ├── EmissionShape.java        (interface)
│   │   ├── PointShape.java
│   │   ├── CircleShape.java
│   │   ├── ConeShape.java
│   │   └── BoxShape.java
│   └── curves/
│       ├── ParticleCurve.java        (interface)
│       ├── ConstantCurve.java
│       ├── LinearCurve.java
│       ├── EaseInOutCurve.java
│       └── ColorGradient.java
├── rendering/
│   └── particles/
│       ├── ParticleRenderer.java     (interface)
│       ├── ParticleBatch.java
│       └── OpenGLParticleRenderer.java

gameData/assets/shaders/
└── particle.glsl
```

---

## Class Details

### Particle.java
```java
/**
 * Lightweight particle data. NOT a Component.
 * Stored in arrays for cache-friendly iteration.
 */
public class Particle {
    // Position in world or local space
    public float x, y;
    
    // Velocity (world units/second)
    public float vx, vy;
    
    // Lifetime
    public float lifetime;      // Total lifetime
    public float age;           // Current age (0 to lifetime)
    
    // Visual properties (current values, modified by curves)
    public float size;
    public float rotation;
    public float alpha;
    public float r, g, b;       // Color components
    
    // Initial values (for curve calculations)
    public float startSize;
    public float startAlpha;
    public float startR, startG, startB;
    
    public boolean isAlive() {
        return age < lifetime;
    }
    
    public float getNormalizedAge() {
        return age / lifetime;  // 0.0 to 1.0
    }
    
    public void reset() {
        x = y = vx = vy = 0;
        lifetime = age = 0;
        size = rotation = alpha = 0;
        r = g = b = 1;
        startSize = startAlpha = 0;
        startR = startG = startB = 1;
    }
}
```

### ParticlePool.java
```java
/**
 * Pre-allocated particle pool with O(1) operations.
 * Uses swap-with-last pattern for despawn.
 */
public class ParticlePool {
    private final Particle[] particles;
    private int activeCount;
    
    public ParticlePool(int capacity);
    
    /** Returns null if pool is full */
    public Particle spawn();
    
    /** Despawns particle at index (swaps with last active) */
    public void despawn(int index);
    
    /** Iterates only active particles */
    public void forEach(Consumer<Particle> action);
    
    /** Iterates with index (needed for despawn during iteration) */
    public void forEachIndexed(BiConsumer<Integer, Particle> action);
    
    public int getActiveCount();
    public int getCapacity();
    public void clear();
}
```

### EmitterConfig.java
```java
@Builder
@Getter
public class EmitterConfig {
    /** Maximum particles alive at once */
    @Builder.Default
    private int maxParticles = 100;
    
    /** Particles spawned per second */
    @Builder.Default
    private float emissionRate = 10;
    
    /** Emitter duration in seconds (-1 = infinite) */
    @Builder.Default
    private float duration = -1;
    
    /** Restart after duration ends */
    @Builder.Default
    private boolean loop = false;
    
    /** Spawn all particles instantly on start */
    @Builder.Default
    private boolean burst = false;
    
    /** Burst count (if burst = true) */
    @Builder.Default
    private int burstCount = 10;
}
```

### ParticleConfig.java
```java
@Builder
@Getter
public class ParticleConfig {
    // Lifetime
    @Builder.Default
    private float lifetime = 1.0f;
    
    @Builder.Default
    private float lifetimeVariance = 0.0f;  // ± random
    
    // Speed
    @Builder.Default
    private float startSpeed = 1.0f;
    
    @Builder.Default
    private float startSpeedVariance = 0.0f;
    
    // Size
    @Builder.Default
    private float startSize = 1.0f;
    
    @Builder.Default
    private float startSizeVariance = 0.0f;
    
    @Builder.Default
    private ParticleCurve sizeCurve = new ConstantCurve(1.0f);
    
    // Rotation
    @Builder.Default
    private float startRotation = 0.0f;
    
    @Builder.Default
    private float rotationSpeed = 0.0f;  // degrees/second
    
    // Color & Alpha
    @Builder.Default
    private Vector4f startColor = new Vector4f(1, 1, 1, 1);
    
    @Builder.Default
    private ColorGradient colorGradient = null;  // null = use startColor
    
    @Builder.Default
    private ParticleCurve alphaCurve = new ConstantCurve(1.0f);
}
```

### EmissionShape.java
```java
public interface EmissionShape {
    /**
     * Generates spawn position offset and velocity direction.
     * 
     * @param outPosition  Output: position offset from emitter (mutated)
     * @param outDirection Output: normalized velocity direction (mutated)
     */
    void generate(Vector2f outPosition, Vector2f outDirection);
}
```

### ParticleCurve.java
```java
public interface ParticleCurve {
    /**
     * Evaluates the curve at normalized time.
     * @param t Normalized time (0.0 = birth, 1.0 = death)
     * @return Multiplier to apply to base value
     */
    float evaluate(float t);
}
```

### ParticleEmitter.java (simplified)
```java
public class ParticleEmitter extends Component {
    
    private EmitterConfig emitterConfig;
    private ParticleConfig particleConfig;
    private EmissionShape emissionShape;
    private SimulationSpace simulationSpace;
    private BlendMode blendMode;
    private Texture texture;
    
    private ParticlePool pool;
    private float emissionAccumulator;
    private float emitterAge;
    private boolean isEmitting;
    
    // Reusable vectors to avoid allocations
    private final Vector2f tempPosition = new Vector2f();
    private final Vector2f tempDirection = new Vector2f();
    
    @Override
    protected void onStart() {
        pool = new ParticlePool(emitterConfig.getMaxParticles());
        isEmitting = true;
    }
    
    @Override
    public void update(float deltaTime) {
        updateEmission(deltaTime);
        updateParticles(deltaTime);
    }
    
    private void updateEmission(float deltaTime) {
        if (!isEmitting) return;
        
        // Check duration
        if (emitterConfig.getDuration() > 0) {
            emitterAge += deltaTime;
            if (emitterAge >= emitterConfig.getDuration()) {
                if (emitterConfig.isLoop()) {
                    emitterAge = 0;
                } else {
                    isEmitting = false;
                    return;
                }
            }
        }
        
        // Emit particles
        emissionAccumulator += emitterConfig.getEmissionRate() * deltaTime;
        while (emissionAccumulator >= 1.0f) {
            spawnParticle();
            emissionAccumulator -= 1.0f;
        }
    }
    
    private void spawnParticle() {
        Particle p = pool.spawn();
        if (p == null) return;  // Pool full
        
        // Get emission position and direction
        emissionShape.generate(tempPosition, tempDirection);
        
        // Set position (world or local)
        Vector3f emitterPos = getTransform().getPosition();
        if (simulationSpace == SimulationSpace.WORLD) {
            p.x = emitterPos.x + tempPosition.x;
            p.y = emitterPos.y + tempPosition.y;
        } else {
            p.x = tempPosition.x;
            p.y = tempPosition.y;
        }
        
        // Set velocity
        float speed = particleConfig.getStartSpeed() + 
            randomVariance(particleConfig.getStartSpeedVariance());
        p.vx = tempDirection.x * speed;
        p.vy = tempDirection.y * speed;
        
        // Set lifetime
        p.lifetime = particleConfig.getLifetime() + 
            randomVariance(particleConfig.getLifetimeVariance());
        p.age = 0;
        
        // Set initial visual properties
        p.startSize = particleConfig.getStartSize() + 
            randomVariance(particleConfig.getStartSizeVariance());
        p.size = p.startSize;
        
        p.rotation = particleConfig.getStartRotation();
        
        Vector4f color = particleConfig.getStartColor();
        p.startR = p.r = color.x;
        p.startG = p.g = color.y;
        p.startB = p.b = color.z;
        p.startAlpha = p.alpha = color.w;
    }
    
    private void updateParticles(float deltaTime) {
        // Iterate backwards to allow safe despawn
        for (int i = pool.getActiveCount() - 1; i >= 0; i--) {
            Particle p = pool.get(i);
            
            // Update age
            p.age += deltaTime;
            if (!p.isAlive()) {
                pool.despawn(i);
                continue;
            }
            
            // Update position
            p.x += p.vx * deltaTime;
            p.y += p.vy * deltaTime;
            
            // Apply curves
            float t = p.getNormalizedAge();
            
            p.size = p.startSize * particleConfig.getSizeCurve().evaluate(t);
            p.alpha = p.startAlpha * particleConfig.getAlphaCurve().evaluate(t);
            p.rotation += particleConfig.getRotationSpeed() * deltaTime;
            
            // Apply color gradient
            if (particleConfig.getColorGradient() != null) {
                particleConfig.getColorGradient().evaluate(t, p);
            }
        }
    }
    
    // === Public API ===
    
    public void play() { isEmitting = true; emitterAge = 0; }
    public void stop() { isEmitting = false; }
    public void clear() { pool.clear(); }
    
    public int getActiveParticleCount() { return pool.getActiveCount(); }
    public boolean isPlaying() { return isEmitting || pool.getActiveCount() > 0; }
    
    // Getters for renderer
    public ParticlePool getPool() { return pool; }
    public Texture getTexture() { return texture; }
    public BlendMode getBlendMode() { return blendMode; }
    public SimulationSpace getSimulationSpace() { return simulationSpace; }
}
```

---

## Rendering Integration

### GameApplication.processFrame() changes
```java
private void processFrame() {
    // ... existing code ...
    
    // 5. RENDER GAME WORLD
    engine.render();
    
    // 5.5 RENDER PARTICLES (NEW)
    engine.renderParticles();
    
    // 6. APPLY POST-PROCESSING
    postProcessor.endCaptureAndApplyEffects();
    
    // 7. RENDER UI
    engine.renderUI();
    
    // ... rest of method ...
}
```

### GameEngine changes
```java
@NonNull
@Getter
private final ParticleRenderer particleRenderer;

public void renderParticles() {
    Scene scene = sceneManager.getCurrentScene();
    if (scene != null) {
        Camera camera = scene.getCamera();
        particleRenderer.render(scene.getParticleEmitters(), camera);
    }
}
```

---

## Implementation Order

| Phase | Deliverables | Dependencies | Est. Effort |
|-------|--------------|--------------|-------------|
| 1 | `Particle`, `ParticlePool`, configs | None | 2-3 hours |
| 2 | Emission shapes (4 classes) | Phase 1 | 2 hours |
| 3 | Curves + ColorGradient | None | 2 hours |
| 4 | `ParticleEmitter` component | Phases 1-3 | 3-4 hours |
| 5 | `ParticleRenderer`, `ParticleBatch`, shader | Phase 4 | 4-5 hours |
| 6 | Scene + Engine integration | Phase 5 | 2 hours |
| 7 | Culling interface stub | Phase 6 | 1 hour |

**Total estimated: 16-19 hours**

---

## Testing Strategy

### Unit Tests
- `ParticlePoolTest` - spawn/despawn/capacity
- `CurveTest` - all curve types evaluate correctly
- `EmissionShapeTest` - shapes produce valid ranges

### Integration Tests
- `ParticleEmitterTest` - lifecycle, emission rate accuracy
- `ParticleRendererTest` - renders without errors (headless)

### Visual Test Scene
- `ParticleTestScene` - interactive demo with:
  - Multiple emitters (fire, smoke, sparkles)
  - Toggle blend modes
  - Toggle simulation space
  - Show particle count overlay

---

## Open Questions

1. **Should particles render before or after post-processing?**
   - Before: Particles affected by bloom/blur (fire looks great with bloom)
   - After: Particles always crisp (UI-like)
   - **Recommendation:** Before (current plan), but could add flag per emitter

2. **Z-ordering between particles and sprites?**
   - Current plan: All particles render after all sprites
   - Alternative: Particles have zIndex, interleave with sprites
   - **Recommendation:** Keep simple for Phase 1, revisit if needed

3. **Texture per emitter or texture atlas?**
   - Current plan: Single texture per emitter
   - **Deferred:** Atlas support for animated particles

---

## Ready to proceed?

Confirm this plan looks good, or let me know any changes before I start Phase 1 implementation.
