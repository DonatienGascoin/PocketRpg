package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.input.InputManager;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector3f;

/**
 * Comprehensive Phase 2 test scene demonstrating:
 * - Sprite batching with multiple textures
 * - Static sprite optimization
 * - Depth sorting strategies
 * - Performance comparison
 */
public class Phase2TestScene extends Scene {

    private static final int STATIC_SPRITES = 200;  // 90% of total
    private static final int DYNAMIC_SPRITES = 20;  // 10% of total
    private static final int TEXTURES = 5;          // Different textures

    private int sortingStrategyIndex = 2; // Start with BALANCED

    public Phase2TestScene() {
        super("Phase2Test");
    }

    @Override
    public void onLoad() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          PHASE 2: SPRITE BATCHING TEST SCENE                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  This scene demonstrates sprite batching optimizations      ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Features:                                                   ║");
        System.out.println("║    - " + STATIC_SPRITES + " static sprites (environment)                      ║");
        System.out.println("║    - " + DYNAMIC_SPRITES + " dynamic sprites (moving)                         ║");
        System.out.println("║    - " + TEXTURES + " different textures                                  ║");
        System.out.println("║    - Depth sorting                                           ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Controls:                                                   ║");
        System.out.println("║    1 - TEXTURE_PRIORITY sorting (max batching)              ║");
        System.out.println("║    2 - DEPTH_PRIORITY sorting (correct depth)               ║");
        System.out.println("║    3 - BALANCED sorting (recommended)                       ║");
        System.out.println("║    P - Print batch statistics                                ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Expected Performance:                                       ║");
        System.out.println("║    Draw calls: ~" + TEXTURES + " (vs ~" + (STATIC_SPRITES + DYNAMIC_SPRITES) + " without batching)            ║");
        System.out.println("║    Performance gain: 20-40x                                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        try {
            // Create camera
            createCamera();

            // Create textures
            Texture[] textures = loadTextures();

            // Create static sprites (environment)
            createStaticSprites(textures);

            // Create dynamic sprites (moving objects)
            createDynamicSprites(textures);

            System.out.println("\n✓ Phase2TestScene loaded:");
            System.out.println("  Static sprites: " + STATIC_SPRITES);
            System.out.println("  Dynamic sprites: " + DYNAMIC_SPRITES);
            System.out.println("  Total sprites: " + (STATIC_SPRITES + DYNAMIC_SPRITES));
            System.out.println("  Textures: " + TEXTURES);
            System.out.println("  Current sorting: BALANCED");

        } catch (Exception e) {
            System.err.println("Failed to load Phase2TestScene: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates camera.
     */
    private void createCamera() {
        GameObject cameraObject = new GameObject("Camera", new Vector3f(0, 0, 0));
        Camera camera = new Camera(Camera.ProjectionType.ORTHOGRAPHIC);
        camera.setClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        cameraObject.addComponent(camera);

        // Add input controller
        cameraObject.addComponent(new SortingStrategyController());

        addGameObject(cameraObject);
    }

    /**
     * Loads test textures.
     */
    private Texture[] loadTextures() {
        Texture[] textures = new Texture[TEXTURES];

        // Load different textures (reuse player texture for demo)
        for (int i = 0; i < TEXTURES; i++) {
            textures[i] = new Texture("assets/player.png");
        }

        System.out.println("✓ Loaded " + TEXTURES + " textures");
        return textures;
    }

    /**
     * Creates static sprites (environment).
     */
    private void createStaticSprites(Texture[] textures) {
        System.out.print("Creating " + STATIC_SPRITES + " static sprites... ");

        int cols = 20;
        int rows = STATIC_SPRITES / cols;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {

                float x = col * 32 + 16;
                float y = row * 32 + 16;
                float z = 0; // Background layer

                GameObject obj = new GameObject("Static_" + (row * cols + col),
                        new Vector3f(x, y, z));

                // Distribute across different textures
                Texture texture = textures[(row * cols + col) % textures.length];

                // Create static sprite renderer
                SpriteRenderer renderer = new SpriteRenderer(
                        new Sprite(texture, 32, 32),
                        true  // isStatic = true
                );
                renderer.setOriginCenter();
                obj.addComponent(renderer);

                addGameObject(obj);
            }
        }

        System.out.println("Done!");
    }

    /**
     * Creates dynamic sprites (moving objects).
     */
    private void createDynamicSprites(Texture[] textures) {
        System.out.print("Creating " + DYNAMIC_SPRITES + " dynamic sprites... ");

        for (int i = 0; i < DYNAMIC_SPRITES; i++) {
            float x = 100 + (i % 10) * 60;
            float y = 100 + ((float) i / 10) * 60;
            float z = 1; // Foreground layer

            GameObject obj = new GameObject("Dynamic_" + i,
                    new Vector3f(x, y, z));

            // Distribute across different textures
            Texture texture = textures[i % textures.length];

            // Create dynamic sprite renderer (isStatic = false by default)
            SpriteRenderer renderer = new SpriteRenderer(
                    new Sprite(texture, 48, 48),
                    false  // isStatic = false
            );
            renderer.setOriginCenter();
            obj.addComponent(renderer);

            // Add movement
            obj.addComponent(new FloatingMovement(
                    new Vector3f(x, y, z),
                    30f,           // amplitude
                    1.0f + i * 0.1f // speed variation
            ));

            addGameObject(obj);
        }

        System.out.println("Done!");
    }

    @Override
    public void onUnload() {
        System.out.println("Phase2TestScene unloaded");
    }

    // ==================== Components ====================

    /**
     * Controller for switching sorting strategies.
     */
    private class SortingStrategyController extends Component {

        @Override
        public void update(float deltaTime) {
            // Get the renderer from scene
            if (!(gameObject.getScene() instanceof Phase2TestScene)) {
                return;
            }

            // Switch sorting strategies
            if (InputManager.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1)) {
                sortingStrategyIndex = 0;
                updateSortingStrategy();
            } else if (InputManager.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_2)) {
                sortingStrategyIndex = 1;
                updateSortingStrategy();
            } else if (InputManager.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_3)) {
                sortingStrategyIndex = 2;
                updateSortingStrategy();
            }

            // Print batch stats
            if (InputManager.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_P)) {
                printBatchStats();
            }
        }

        private void updateSortingStrategy() {
            // This requires access to the renderer, which would typically be
            // done through a game manager or similar. For now, we'll just print.
            String[] strategies = {"TEXTURE_PRIORITY", "DEPTH_PRIORITY", "BALANCED"};
            System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Sorting Strategy Changed: " + String.format("%-33s", strategies[sortingStrategyIndex]) + "║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println("Note: To actually change strategy, modify GameWindow to expose");
            System.out.println("      the renderer and call:");
            System.out.println("      ((BatchRenderer)renderer).getBatch().setSortingStrategy(...)");
        }

        private void printBatchStats() {
            System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║                   BATCH STATISTICS                           ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println("Note: Statistics are printed automatically every 5 seconds.");
            System.out.println("      Check the console output for detailed batch information.");
        }
    }

    /**
     * Simple floating movement component.
     */
    private static class FloatingMovement extends Component {
        private final Vector3f origin;
        private final float amplitude;
        private final float speed;
        private float time = 0;

        public FloatingMovement(Vector3f origin, float amplitude, float speed) {
            this.origin = new Vector3f(origin);
            this.amplitude = amplitude;
            this.speed = speed;
        }

        @Override
        public void update(float deltaTime) {
            time += deltaTime * speed;

            float offsetX = (float) Math.sin(time) * amplitude;
            float offsetY = (float) Math.cos(time * 0.7f) * amplitude * 0.5f;

            gameObject.getTransform().setPosition(
                    origin.x + offsetX,
                    origin.y + offsetY,
                    origin.z
            );
        }
    }
}