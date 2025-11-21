package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TranslationComponent;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector3f;

/**
 * Comprehensive example scene demonstrating all component system features:
 * - Camera with custom clear color and movement
 * - Player with rotation
 * - Moving platforms with TranslationComponent
 * - Sprite sheet animation
 */
public class ExampleScene extends Scene {

    public ExampleScene() {
        super("ExampleScene");
    }

    @Override
    public void onLoad() {
        System.out.println("Loading ExampleScene...");

        try {
            // Create camera with movement
            createCamera();

            // Create player
            createPlayer();

            // Create moving platforms
            createMovingPlatforms();

            // Create animated enemies
            createAnimatedEnemies();

            System.out.println("ExampleScene loaded successfully with " + getGameObjects().size() + " GameObjects");

        } catch (Exception e) {
            System.err.println("Failed to load scene: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates camera with dark blue clear color and smooth movement
     */
    private void createCamera() {
        GameObject cameraObj = new GameObject("MainCamera", new Vector3f(0, 0, 0));

        // Create camera with dark blue/purple clear color
        Camera camera = new Camera(.9f, 0.85f, 1f, 1.0f);
        cameraObj.addComponent(camera);

        // Add smooth camera movement (panning left to right)
        cameraObj.addComponent(new TranslationComponent(
                new Vector3f(0, 0, 0),
                new Vector3f(200, 0, 0),
                8.0f,
                TranslationComponent.EasingType.EASE_IN_OUT,
                false,
                true // Ping pong back and forth
        ));

        addGameObject(cameraObj);
        System.out.println("Camera created with custom clear color and movement");
    }

    /**
     * Creates the main player character
     */
    private void createPlayer() throws Exception {
        GameObject player = new GameObject("Player", new Vector3f(400, 300, 0));

        // Add sprite
        Texture playerTexture = new Texture("assets/player.png");
        SpriteRenderer playerRenderer = new SpriteRenderer(playerTexture, 64, 64);
        player.addComponent(playerRenderer);

        // Add rotation
        player.addComponent(new RotatingComponent(30f));

        // Add gentle floating movement
        player.addComponent(new TranslationComponent(
                new Vector3f(400, 300, 0),
                new Vector3f(400, 320, 0),
                2.0f,
                TranslationComponent.EasingType.SMOOTHER_STEP,
                false,
                true
        ));

        addGameObject(player);
        System.out.println("Player created");
    }

    /**
     * Creates moving platforms with different easing types
     */
    private void createMovingPlatforms() throws Exception {
        Texture platformTexture = new Texture("assets/player.png");

        // Platform 1: Linear movement (top)
        GameObject platform1 = new GameObject("Platform_Linear", new Vector3f(100, 150, 0));
        platform1.addComponent(new SpriteRenderer(platformTexture, 48, 16));
        platform1.addComponent(new TranslationComponent(
                new Vector3f(100, 150, 0),
                new Vector3f(700, 150, 0),
                3.0f,
                TranslationComponent.EasingType.LINEAR,
                false,
                true // Ping pong
        ));
        addGameObject(platform1);

        // Platform 2: Smooth step (middle)
        GameObject platform2 = new GameObject("Platform_Smooth", new Vector3f(150, 450, 0));
        platform2.addComponent(new SpriteRenderer(platformTexture, 48, 16));
        platform2.addComponent(new TranslationComponent(
                new Vector3f(150, 450, 0),
                new Vector3f(650, 450, 0),
                4.0f,
                TranslationComponent.EasingType.SMOOTHER_STEP,
                false,
                true // Ping pong
        ));
        addGameObject(platform2);

        // Platform 3: Ease in/out (diagonal)
        GameObject platform3 = new GameObject("Platform_Diagonal", new Vector3f(200, 200, 0));
        platform3.addComponent(new SpriteRenderer(platformTexture, 48, 16));
        platform3.addComponent(new TranslationComponent(
                new Vector3f(200, 200, 0),
                new Vector3f(600, 400, 0),
                5.0f,
                TranslationComponent.EasingType.EASE_IN_OUT,
                true, // Loop
                false
        ));
        addGameObject(platform3);

        System.out.println("3 moving platforms created");
    }

    /**
     * Creates enemies with sprite sheet animation
     */
    private void createAnimatedEnemies() throws Exception {
        Texture spriteSheetTexture = new Texture("assets/spritesheet.png");

        for (int i = 0; i < 3; i++) {
            GameObject enemy = new GameObject("Enemy" + i, new Vector3f(250 + i * 100, 300, 0));

            SpriteRenderer enemyRenderer = new SpriteRenderer(spriteSheetTexture, 48, 48);
            enemyRenderer.getSprite().setUVsFromPixels(0, 0, 32, 32);
            enemy.addComponent(enemyRenderer);

            // Add sprite sheet animation
            enemy.addComponent(new AnimatedSpriteComponent(32, 4, 4, 0.15f));

            // Add slight bobbing movement
            enemy.addComponent(new TranslationComponent(
                    new Vector3f(250 + i * 100, 300, 0),
                    new Vector3f(250 + i * 100, 280, 0),
                    1.5f + i * 0.3f, // Slightly different speeds
                    TranslationComponent.EasingType.SMOOTH_STEP,
                    false,
                    true
            ));

            addGameObject(enemy);
        }

        System.out.println("3 animated enemies created");
    }

    @Override
    public void onUnload() {
        System.out.println("Unloading ExampleScene");
    }

    /**
     * Component that rotates a GameObject continuously
     */
    private static class RotatingComponent extends Component {
        private float rotationSpeed;

        public RotatingComponent(float rotationSpeed) {
            this.rotationSpeed = rotationSpeed;
        }

        @Override
        public void update(float deltaTime) {
            gameObject.getTransform().rotate(0, 0, rotationSpeed * deltaTime);
        }
    }

    /**
     * Component that animates sprite sheets
     */
    private static class AnimatedSpriteComponent extends Component {
        private int spriteSize;
        private int sheetCols;
        private int sheetRows;
        private float animationSpeed;
        private int currentFrame = 0;
        private float animationTimer = 0;

        public AnimatedSpriteComponent(int spriteSize, int sheetCols, int sheetRows, float animationSpeed) {
            this.spriteSize = spriteSize;
            this.sheetCols = sheetCols;
            this.sheetRows = sheetRows;
            this.animationSpeed = animationSpeed;
        }

        @Override
        public void update(float deltaTime) {
            SpriteRenderer spriteRenderer = gameObject.getComponent(SpriteRenderer.class);
            if (spriteRenderer == null) return;

            animationTimer += deltaTime;

            if (animationTimer >= animationSpeed) {
                animationTimer -= animationSpeed;
                currentFrame = (currentFrame + 1) % (sheetCols * sheetRows);

                int row = currentFrame / sheetCols;
                int col = currentFrame % sheetCols;

                spriteRenderer.getSprite().setUVsFromPixels(
                        col * spriteSize,
                        row * spriteSize,
                        spriteSize,
                        spriteSize
                );
            }
        }
    }
}