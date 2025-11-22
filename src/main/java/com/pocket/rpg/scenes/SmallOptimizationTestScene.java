package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector3f;

/**
 * Small test scene with 8-10 sprites to verify culling accuracy.
 * Tests:
 * - Some sprites visible, some off-screen
 * - Camera movement
 * - Statistics output to console
 * - Projection changes
 */
public class SmallOptimizationTestScene extends Scene {

    private Camera camera;
    private GameObject cameraObject;
    private float cameraSpeed = 100f; // pixels per second

    public SmallOptimizationTestScene() {
        super("SmallOptimizationTest");
    }

    @Override
    public void onLoad() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          SMALL OPTIMIZATION TEST SCENE                       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  This scene tests culling with a small number of sprites    ║");
        System.out.println("║  - 10 sprites: 5 visible, 5 off-screen                       ║");
        System.out.println("║  - Camera moves automatically                                ║");
        System.out.println("║  - Statistics printed to console                             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Create camera
            createCamera();

            // Create test sprites
            createTestSprites();

            System.out.println("SmallOptimizationTestScene loaded: " + getGameObjects().size() + " objects");
            System.out.println();

        } catch (Exception e) {
            System.err.println("Failed to load SmallOptimizationTestScene: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates the camera.
     */
    private void createCamera() {
        cameraObject = new GameObject("Camera", new Vector3f(0, 0, 0));
        camera = new Camera(Camera.ProjectionType.ORTHOGRAPHIC);
        camera.setClearColor(0.1f, 0.15f, 0.2f, 1.0f);
        cameraObject.addComponent(camera);
        addGameObject(cameraObject);

        System.out.println("✓ Camera created at (0, 0)");
    }

    /**
     * Creates test sprites: 5 on-screen, 5 off-screen.
     */
    private void createTestSprites() throws Exception {
        Texture texture = new Texture("assets/player.png");

        // On-screen sprites (should always be visible)
        createSprite("OnScreen_1", texture, 100, 100);
        createSprite("OnScreen_2", texture, 300, 100);
        createSprite("OnScreen_3", texture, 500, 100);
        createSprite("OnScreen_4", texture, 200, 300);
        createSprite("OnScreen_5", texture, 400, 300);

        // Off-screen sprites (should be culled initially)
        createSprite("OffScreen_1", texture, -200, 100);   // Left of screen
        createSprite("OffScreen_2", texture, 900, 100);    // Right of screen
        createSprite("OffScreen_3", texture, 300, -200);   // Above screen
        createSprite("OffScreen_4", texture, 300, 800);    // Below screen
        createSprite("OffScreen_5", texture, -100, -100);  // Top-left corner

        System.out.println("✓ Created 10 sprites (5 on-screen, 5 off-screen)");
    }

    /**
     * Creates a single sprite at the given position.
     */
    private void createSprite(String name, Texture texture, float x, float y) {
        GameObject obj = new GameObject(name, new Vector3f(x, y, 0));
        SpriteRenderer renderer = new SpriteRenderer(new Sprite(texture, 64, 64));
        renderer.setOriginCenter();
        obj.addComponent(renderer);
        addGameObject(obj);
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);

        // Move camera right continuously
        if (cameraObject != null) {
            Transform transform = cameraObject.getTransform();
            Vector3f pos = transform.getPosition();

            // Move right, then wrap around
            pos.x += cameraSpeed * deltaTime;
            if (pos.x > 1000) {
                pos.x = 0;
            }

            transform.setPosition(pos);
        }
    }

    @Override
    public void onUnload() {
        System.out.println("SmallOptimizationTestScene unloaded");
    }
}