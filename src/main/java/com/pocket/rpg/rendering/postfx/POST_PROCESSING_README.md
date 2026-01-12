# Post-Processing Effects Collection

Complete set of 12 post-processing effects for your RPG game. Each effect is modular and can be combined in your rendering pipeline.

## Effects Overview

### 1. **Vignette Effect**
- **Files**: `vignette.glsl`, `VignetteEffect.java`
- **Purpose**: Darkens screen edges to focus attention on center
- **Parameters**:
  - `vignetteIntensity` (0.5-2.0): How far the vignette reaches
  - `vignetteStrength` (0.3-1.0): How dark it becomes
- **Use Cases**: Cinematic moments, focus effects, dramatic scenes
- **Example**: `new VignetteEffect(1.0f, 0.75f)` // Small vignette, 75% black

### 2. **Desaturation Effect**
- **Files**: `desaturation.glsl`, `DesaturationEffect.java`
- **Purpose**: Removes color for vintage or noir aesthetic
- **Parameters**:
  - `desaturationAmount` (0.0-1.0): 0=full color, 1=grayscale
- **Use Cases**: Death screens, flashbacks, retro styling
- **Example**: `new DesaturationEffect(0.5f)` // 50% desaturation

### 3. **Bloom/Glow Effect**
- **Files**: `bloom.glsl`, `BloomEffect.java`
- **Purpose**: Makes bright areas glow and bleed light
- **Parameters**:
  - `bloomThreshold` (0.5-1.0): Brightness needed to glow
  - `bloomIntensity` (0.5-3.0): Strength of glow
- **Use Cases**: Magic effects, explosions, glowing objects, divine/ethereal moments
- **Example**: `new BloomEffect(0.8f, 1.5f)`
- **Note**: Requires 2 render passes (automatic)

### 4. **Chromatic Aberration**
- **Files**: `chromaticAberration.glsl`, `ChromaticAberrationEffect.java`
- **Purpose**: Splits RGB channels for lens distortion effect
- **Parameters**:
  - `aberrationStrength` (0.002-0.02): Channel separation amount
- **Use Cases**: Impact moments, corrupted visuals, cinematic style, damage feedback
- **Example**: `new ChromaticAberrationEffect(0.005f)`

### 5. **Scanlines Effect**
- **Files**: `scanlines.glsl`, `ScanlinesEffect.java`
- **Purpose**: Adds horizontal lines for CRT monitor look
- **Parameters**:
  - `scanlineIntensity` (0.0-0.7): Darkness of lines
  - `scanlineCount` (150-600): Number of lines
- **Use Cases**: Retro aesthetic, computer terminal UI, security camera view
- **Example**: `new ScanlinesEffect(0.3f, 300.0f)`

### 6. **Film Grain Effect**
- **Files**: `filmGrain.glsl`, `FilmGrainEffect.java`
- **Purpose**: Adds animated noise texture
- **Parameters**:
  - `grainIntensity` (0.02-0.2): Strength of grain
- **Use Cases**: Vintage film look, security footage, found footage style
- **Example**: `new FilmGrainEffect(0.05f)`
- **Note**: Grain animates automatically over time

### 7. **Color Grading/Tint**
- **Files**: `colorGrading.glsl`, `ColorGradingEffect.java`
- **Purpose**: Shifts entire screen toward a color
- **Parameters**:
  - `tintColor` (RGB): Target color
  - `tintStrength` (0.0-1.0): How much to apply
- **Use Cases**: 
  - Red tint for low health/danger
  - Blue tint for cold/ice areas
  - Green tint for poison/toxic
  - Warm tint for cozy/safe areas
- **Examples**:
  - `new ColorGradingEffect(1.0f, 0.3f, 0.3f, 0.5f)` // Red danger tint
  - `new ColorGradingEffect(0.3f, 0.5f, 1.0f, 0.3f)` // Blue cold tint

### 8. **Pixelation Effect**
- **Files**: `pixelation.glsl`, `PixelationEffect.java`
- **Purpose**: Reduces resolution for retro pixel art look
- **Parameters**:
  - `pixelSize` (0.001-0.05): Size of pixels
- **Use Cases**: Retro style, low-res surveillance, transition effects, damaged screens
- **Example**: `new PixelationEffect(0.005f)`

### 9. **Motion Blur**
- **Files**: `motionBlur.glsl`, `MotionBlurEffect.java`
- **Purpose**: Directional blur for movement sense
- **Parameters**:
  - `blurDirection` (Vector2f): Direction of blur
  - `blurStrength` (0.01-0.1): Length of streak
  - `samples` (4-16): Quality of blur
- **Use Cases**: Dash abilities, speed boosts, disorientation, fast movement
- **Examples**:
  - `new MotionBlurEffect(1.0f, 0.0f, 0.02f, 8)` // Horizontal
  - `new MotionBlurEffect(0.0f, 1.0f, 0.03f, 10)` // Vertical

### 10. **Edge Detection/Outline**
- **Files**: `edgeDetection.glsl`, `EdgeDetectionEffect.java`
- **Purpose**: Draws outlines around objects
- **Parameters**:
  - `edgeThreshold` (0.05-0.3): Edge sensitivity
  - `edgeColor` (RGB): Color of outlines
- **Use Cases**: Cel-shaded style, comic book look, improved clarity, artistic style
- **Examples**:
  - `new EdgeDetectionEffect(0.1f, 0.0f, 0.0f, 0.0f)` // Black outlines
  - `new EdgeDetectionEffect(0.1f, 1.0f, 0.5f, 0.0f)` // Orange outlines

### 11. **Radial Blur**
- **Files**: `radialBlur.glsl`, `RadialBlurEffect.java`
- **Purpose**: Blur radiating from a center point
- **Parameters**:
  - `blurCenter` (Vector2f): Center point (UV space)
  - `blurStrength` (0.01-0.1): Intensity
  - `samples` (6-16): Quality
- **Use Cases**: Explosions, speed lines, dramatic focus, impact frames
- **Examples**:
  - `new RadialBlurEffect(0.5f, 0.5f, 0.03f, 10)` // Screen center
  - `new RadialBlurEffect(0.3f, 0.7f, 0.05f, 12)` // Off-center explosion

### 12. **Displacement/Screen Shake**
- **Files**: `displacement.glsl`, `DisplacementEffect.java`
- **Purpose**: Distorts screen with waves and shake
- **Parameters**:
  - `displacementStrength` (0.001-0.02): Wave intensity
  - `shakeDirection` (Vector2f): Directional shake
- **Use Cases**: Explosions, earthquakes, impacts, environmental disturbances
- **Examples**:
  - `new DisplacementEffect(0.005f)` // Waves only
  - `new DisplacementEffect(0.01f, 0.002f, 0.002f)` // Waves + diagonal shake
- **Note**: Waves animate automatically over time

## Usage Examples

### Basic Setup
```java
// Add individual effects
postProcessor.addEffect(new VignetteEffect());
postProcessor.addEffect(new FilmGrainEffect());
```

### Retro CRT Style
```java
postProcessor.addEffect(new ScanlinesEffect(0.3f, 300.0f));
postProcessor.addEffect(new DesaturationEffect(0.7f));
postProcessor.addEffect(new ChromaticAberrationEffect(0.003f));
postProcessor.addEffect(new VignetteEffect(1.0f, 0.5f));
```

### Dramatic Combat
```java
postProcessor.addEffect(new MotionBlurEffect(1.0f, 0.0f, 0.03f, 10));
postProcessor.addEffect(new ChromaticAberrationEffect(0.008f));
postProcessor.addEffect(new DisplacementEffect(0.01f, 0.002f, 0.0f));
```

### Magical/Ethereal Scene
```java
postProcessor.addEffect(new BloomEffect(0.7f, 2.0f));
postProcessor.addEffect(new ColorGradingEffect(0.8f, 0.9f, 1.0f, 0.3f)); // Slight blue tint
postProcessor.addEffect(new VignetteEffect(1.2f, 0.4f));
```

### Low Health Warning
```java
postProcessor.addEffect(new ColorGradingEffect(1.0f, 0.3f, 0.3f, 0.5f)); // Red tint
postProcessor.addEffect(new DesaturationEffect(0.3f));
postProcessor.addEffect(new VignetteEffect(1.5f, 0.8f));
```

### Cel-Shaded/Comic Style
```java
postProcessor.addEffect(new EdgeDetectionEffect(0.15f, 0.0f, 0.0f, 0.0f));
postProcessor.addEffect(new DesaturationEffect(0.2f));
```

### Speed/Dash Effect
```java
postProcessor.addEffect(new MotionBlurEffect(playerVelocity.x, playerVelocity.y, 0.04f, 12));
postProcessor.addEffect(new RadialBlurEffect(0.5f, 0.5f, 0.02f, 10));
postProcessor.addEffect(new ChromaticAberrationEffect(0.006f));
```

## Performance Notes

**Light Effects** (minimal performance impact):
- Vignette
- Desaturation
- Color Grading
- Scanlines
- Pixelation

**Medium Effects** (moderate impact):
- Film Grain
- Chromatic Aberration
- Displacement

**Heavy Effects** (significant impact):
- Bloom (2 passes required)
- Motion Blur (multiple samples)
- Radial Blur (multiple samples)
- Edge Detection (9 texture samples)

**Optimization Tips**:
1. Use fewer samples in blur effects on lower-end hardware
2. Combine effects that serve similar purposes
3. Disable effects dynamically based on player settings
4. Use effects situationally (combat, special moments) rather than always-on

## Shader Placement

All shaders should be placed in: `assets/shaders/`

## Dynamic Effect Control

You can create dynamic systems to control effects:

```java
// Health-based red tint
float healthPercent = player.health / player.maxHealth;
float tintStrength = 1.0f - healthPercent; // More red at low health
ColorGradingEffect healthTint = new ColorGradingEffect(1.0f, 0.3f, 0.3f, tintStrength);

// Speed-based motion blur
float speed = player.velocity.length();
float blurAmount = Math.min(speed / maxSpeed * 0.05f, 0.05f);
MotionBlurEffect speedBlur = new MotionBlurEffect(
    player.velocity.x, 
    player.velocity.y, 
    blurAmount, 
    8
);

// Impact shake with decay
float shakeAmount = impactForce * 0.01f;
DisplacementEffect impact = new DisplacementEffect(0.005f, shakeAmount, 0.0f);
// Decrease shakeAmount over time
```

## Troubleshooting

**Effect not visible:**
- Check shader file paths are correct
- Verify effect is added to post-processor
- Ensure parameters aren't set to 0 or minimal values

**Performance issues:**
- Reduce sample counts in blur effects
- Disable heavy effects like Bloom
- Combine multiple passes where possible

**Artifacts or glitches:**
- Check texture coordinates aren't going out of bounds
- Verify framebuffer setup is correct
- Ensure proper cleanup between passes

## Credits

All effects use standard post-processing techniques:
- Bloom: Gaussian blur-based extraction and combination
- Edge Detection: Sobel operator
- Blur effects: Multi-sample convolution
- Color operations: Standard color space transformations

Enjoy experimenting with these effects! Mix and match to create your unique visual style.
