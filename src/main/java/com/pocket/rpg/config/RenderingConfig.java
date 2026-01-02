package com.pocket.rpg.config;

import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.rendering.stats.ConsoleStatisticsReporter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joml.Vector4f;

/**
 * Configuration for the rendering system.
 * <p>
 * This configuration controls how sprites are sized and how the camera views the world.
 * Understanding the relationship between these settings is crucial for proper rendering.
 *
 * <h2>World Unit System</h2>
 * The engine uses an abstract "world unit" coordinate system rather than pixels.
 * This decouples game logic from texture resolution and enables resolution-independent rendering.
 *
 * <h3>Key Concepts</h3>
 * <ul>
 *   <li><b>Pixels Per Unit (PPU)</b>: Defines how many texture pixels equal one world unit.
 *       A 16×16 pixel texture with PPU=16 becomes 1×1 world units.</li>
 *   <li><b>Orthographic Size</b>: Defines how many world units are visible vertically (half-height).
 *       An orthographicSize of 15 means 30 world units are visible vertically.</li>
 *   <li><b>Game Resolution</b>: The framebuffer size in pixels (e.g., 640×480).
 *       This determines pixel density, not world size.</li>
 * </ul>
 *
 * <h3>The PPU Formula</h3>
 * <pre>
 * World Size = Texture Pixels ÷ Pixels Per Unit
 *
 * Examples (with PPU = 16):
 *   - 16×16 texture → 1×1 world units (standard tile)
 *   - 32×32 texture → 2×2 world units (large sprite)
 *   - 8×8 texture  → 0.5×0.5 world units (small detail)
 * </pre>
 *
 * <h3>Pixel-Perfect Rendering</h3>
 * For 1:1 pixel mapping (one texture pixel = one framebuffer pixel):
 * <pre>
 * orthographicSize = gameHeight / (2 × pixelsPerUnit)
 *
 * Example:
 *   gameHeight = 480, PPU = 16
 *   orthographicSize = 480 / (2 × 16) = 15
 *   Visible height = 30 world units
 *   Each world unit = 480 / 30 = 16 framebuffer pixels ✓
 * </pre>
 *
 * <h3>Zooming</h3>
 * <ul>
 *   <li>Smaller orthographicSize = zoom in (fewer world units visible, each appears larger)</li>
 *   <li>Larger orthographicSize = zoom out (more world units visible, each appears smaller)</li>
 * </ul>
 * <pre>
 * orthographicSize = 7.5  → 2× zoom (see 15 units, each = 32 pixels)
 * orthographicSize = 15   → 1× zoom (see 30 units, each = 16 pixels) [default]
 * orthographicSize = 30   → 0.5× zoom (see 60 units, each = 8 pixels)
 * </pre>
 *
 * <h3>Coordinate System</h3>
 * The engine uses a centered Y-up coordinate system:
 * <ul>
 *   <li>Origin (0, 0) is at the center of the camera view</li>
 *   <li>Positive X points right</li>
 *   <li>Positive Y points up</li>
 *   <li>Positive Z points toward the camera (used for depth sorting)</li>
 * </ul>
 *
 * <h3>Typical Setup for 16×16 Tile-Based Game</h3>
 * <pre>
 * RenderingConfig config = RenderingConfig.builder()
 *     .pixelsPerUnit(16f)              // 16px tiles = 1 world unit
 *     .defaultOrthographicSize(null)   // Auto-calculate for pixel-perfect
 *     .build();
 *
 * // With gameHeight=480:
 * // - orthographicSize auto-calculated to 15
 * // - Camera sees 30×40 world units (assuming 4:3 aspect)
 * // - Each tile renders at exactly 16×16 framebuffer pixels
 * </pre>
 *
 * @see com.pocket.rpg.rendering.Sprite#getWorldWidth()
 * @see com.pocket.rpg.rendering.Sprite#getWorldHeight()
 * @see com.pocket.rpg.core.GameCamera#setOrthographicSize(float)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderingConfig {

    /**
     * Maximum number of sprites that can be batched in a single draw call.
     * Higher values reduce draw calls but increase memory usage.
     * <p>
     * Default: 10000
     * Note: Changing this at runtime requires renderer rebuild.
     */
    @Builder.Default
    private int maxBatchSize = 10000;

    /**
     * Strategy for sorting sprites within a batch.
     * Affects rendering order and batching efficiency.
     * <p>
     * Default: BALANCED
     *
     * @see SpriteBatch.SortingStrategy
     */
    @Builder.Default
    private SpriteBatch.SortingStrategy sortingStrategy = SpriteBatch.SortingStrategy.BALANCED;

    /**
     * Whether to collect and report rendering statistics.
     * Useful for debugging but has minor performance overhead.
     * <p>
     * Default: true
     */
    @Builder.Default
    private boolean enableStatistics = true;

    /**
     * Number of frames between statistics reports.
     * Only relevant when {@link #enableStatistics} is true.
     * <p>
     * Default: 300 (every 5 seconds at 60 FPS)
     */
    @Builder.Default
    private int statisticsInterval = 300;

    /**
     * Reporter for rendering statistics output.
     * Set to null to disable reporting even when statistics are enabled.
     * <p>
     * Default: null
     */
    @Builder.Default
    private ConsoleStatisticsReporter reporter = null;

    /**
     * Background clear color for the rendering viewport.
     * Components are (R, G, B, A) in range [0, 1].
     * <p>
     * Default: (1.0, 0.8, 0.8, 1.0) - light pink
     */
    @Builder.Default
    private Vector4f clearColor = new Vector4f(1f, 0.8f, 0.8f, 1.0f);

    // ========================================================================
    // WORLD UNIT SYSTEM
    // ========================================================================

    /**
     * Pixels Per Unit (PPU) - defines sprite sizing in world space.
     * <p>
     * This value determines how many texture pixels correspond to one world unit.
     * It is the fundamental scaling factor between texture space and world space.
     *
     * <h3>Formula</h3>
     * <pre>
     * Sprite World Width  = Texture Width  ÷ pixelsPerUnit
     * Sprite World Height = Texture Height ÷ pixelsPerUnit
     * </pre>
     *
     * <h3>Common Values</h3>
     * <ul>
     *   <li><b>16</b>: For 16×16 pixel art tiles (1 tile = 1 world unit)</li>
     *   <li><b>32</b>: For 32×32 pixel art tiles (1 tile = 1 world unit)</li>
     *   <li><b>100</b>: For HD sprites (100 pixels = 1 meter)</li>
     * </ul>
     *
     * <h3>Per-Sprite Override</h3>
     * Individual sprites can override this value using
     * {@link com.pocket.rpg.rendering.Sprite#setPixelsPerUnitOverride(Float)}.
     * This is useful when mixing sprites of different pixel densities.
     * <p>
     * Default: 16.0
     *
     * @see com.pocket.rpg.rendering.Sprite#getWorldWidth()
     */
    @Builder.Default
    private float pixelsPerUnit = 16f;

    /**
     * Default orthographic size for new cameras (half-height in world units).
     * <p>
     * This value determines how much of the world is visible vertically.
     * The full visible height is {@code orthographicSize × 2}.
     * Visible width is calculated from aspect ratio.
     *
     * <h3>Calculation</h3>
     * <pre>
     * Visible Height = orthographicSize × 2
     * Visible Width  = Visible Height × (gameWidth / gameHeight)
     * </pre>
     *
     * <h3>Pixel-Perfect Auto-Calculation</h3>
     * When set to {@code null}, the orthographic size is automatically calculated
     * to achieve pixel-perfect rendering (1 texture pixel = 1 framebuffer pixel):
     * <pre>
     * autoOrthographicSize = gameHeight / (2 × pixelsPerUnit)
     * </pre>
     *
     * <h3>Manual Override</h3>
     * Set an explicit value to control zoom level independently of PPU:
     * <ul>
     *   <li>Smaller than auto = zoom in (sprites appear larger)</li>
     *   <li>Larger than auto = zoom out (sprites appear smaller)</li>
     * </ul>
     * <p>
     * Default: null (auto-calculate for pixel-perfect rendering)
     *
     * @see #getDefaultOrthographicSize(int)
     * @see com.pocket.rpg.core.GameCamera#setOrthographicSize(float)
     */
    @Builder.Default
    private Float defaultOrthographicSize = null;

    /**
     * Calculates the effective orthographic size for a given game height.
     * <p>
     * If {@link #defaultOrthographicSize} is explicitly set, returns that value.
     * Otherwise, calculates the pixel-perfect value based on game height and PPU.
     *
     * <h3>Pixel-Perfect Formula</h3>
     * <pre>
     * orthographicSize = gameHeight / (2 × pixelsPerUnit)
     * </pre>
     *
     * @param gameHeight The game's internal rendering height in pixels
     * @return The orthographic size (half-height in world units)
     */
    public float getDefaultOrthographicSize(int gameHeight) {
        if (defaultOrthographicSize != null) {
            return defaultOrthographicSize;
        }
        // Auto-calculate for pixel-perfect rendering
        return gameHeight / (2f * pixelsPerUnit);
    }
}
