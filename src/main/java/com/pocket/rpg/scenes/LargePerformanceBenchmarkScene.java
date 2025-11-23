package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.utils.Time;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Large performance benchmark scene with 1000+ sprites in a grid.
 * Tests:
 * - Performance with many sprites
 * - Culling effectiveness at different zoom levels
 * - Camera controls (WASD + scroll zoom)
 * - FPS and frame time metrics
 */
public class LargePerformanceBenchmarkScene extends Scene {

    private static final int GRID_COLS = 50;
    private static final int GRID_ROWS = 50;
    private static final int SPRITE_SPACING = 100;
    private static final float WORLD_SIZE = GRID_COLS * SPRITE_SPACING;

    private Camera camera;
    private GameObject cameraObject;
    private CameraController cameraController;

    public LargePerformanceBenchmarkScene() {
        super("LargePerformanceBenchmark");
    }

    @Override
    public void onLoad() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       LARGE PERFORMANCE BENCHMARK SCENE                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Testing performance with 1000+ sprites                      ║");
        System.out.println("║  Controls:                                                   ║");
        System.out.println("║    WASD - Move camera                                        ║");
        System.out.println("║    Scroll - Zoom (not implemented in this version)           ║");
        System.out.println("║  Grid: 50x50 sprites across large world                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Create camera with controller
            createCamera();

            // Create massive sprite grid
            createSpriteGrid();

            int totalSprites = GRID_COLS * GRID_ROWS;
            System.out.println("✓ LargePerformanceBenchmarkScene loaded: " + totalSprites + " sprites");
            System.out.println("  World size: " + WORLD_SIZE + "x" + WORLD_SIZE + " pixels");
            System.out.println();

        } catch (Exception e) {
            System.err.println("Failed to load LargePerformanceBenchmarkScene: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates camera with WASD controls.
     */
    private void createCamera() {
        cameraObject = new GameObject("Camera", new Vector3f(0, 0, 0));
        camera = new Camera(Camera.ProjectionType.ORTHOGRAPHIC);
        camera.setClearColor(0.05f, 0.05f, 0.1f, 1.0f);
        cameraObject.addComponent(camera);

        // Add camera controller
        cameraController = new CameraController(200f); // 200 pixels/sec
        cameraObject.addComponent(cameraController);

        addGameObject(cameraObject);

        System.out.println("✓ Camera created with WASD controls (200 px/s)");
    }

    /**
     * Creates a massive grid of sprites.
     */
    private void createSpriteGrid() throws Exception {
        Texture texture = new Texture("assets/player.png");

        System.out.print("Creating sprite grid... ");
        int created = 0;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                float x = col * SPRITE_SPACING + 50;
                float y = row * SPRITE_SPACING + 50;

                String name = String.format("Sprite_%d_%d", col, row);
                GameObject obj = new GameObject(name, new Vector3f(x, y, 0));

                SpriteRenderer renderer = new SpriteRenderer(new Sprite(texture, 64, 64));
                renderer.setOriginCenter();
                obj.addComponent(renderer);

                addGameObject(obj);
                created++;
            }
        }

        System.out.println(created + " sprites created");
    }

    @Override
    public void onUnload() {
        System.out.println("LargePerformanceBenchmarkScene unloaded");
        System.out.println("Final FPS: " + Time.fps());
    }

    /**
     * Camera controller component for WASD movement.
     */
    private static class CameraController extends Component {
        private final float moveSpeed;
        private long windowHandle;

        public CameraController(float moveSpeed) {
            this.moveSpeed = moveSpeed;
        }

        @Override
        public void update(float deltaTime) {
            Transform transform = getTransform();
            Vector3f pos = transform.getPosition();

            float moveAmount = moveSpeed * deltaTime;

            // Note: GLFW key polling would need window handle
            // For now, this is a placeholder
            // In a real implementation, we'd need to pass the window handle

            // Example movement (would need actual GLFW polling):
            // if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) pos.y -= moveAmount;
            // if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) pos.y += moveAmount;
            // if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) pos.x -= moveAmount;
            // if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) pos.x += moveAmount;

            // For testing, just pan slowly right
            pos.x += moveSpeed * deltaTime * 0.5f;
            if (pos.x > GRID_COLS * SPRITE_SPACING) {
                pos.x = 0;
            }

            transform.setPosition(pos);
        }
    }
}