package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector3f;

/**
 * Example scene that demonstrates the component system.
 * This scene creates GameObjects with Transform and SpriteRenderer components.
 */
public class ExampleScene extends Scene {

    public ExampleScene() {
        super("ExampleScene");
    }

    @Override
    public void onLoad() {
        System.out.println("Loading ExampleScene...");

        try {
            // Create a player GameObject
            GameObject player = new GameObject("Player", new Vector3f(400, 300, 0));

            // Add a SpriteRenderer component
            Texture playerTexture = new Texture("assets/player.png");
            SpriteRenderer playerRenderer = new SpriteRenderer(playerTexture, 64, 64);
            player.addComponent(playerRenderer);

            // Add custom behavior component
            player.addComponent(new RotatingComponent(120f));

            // Add to scene
            addGameObject(player);

            // Create some sprite sheet enemies
            Texture spriteSheetTexture = new Texture("assets/spritesheet.png");

            for (int i = 0; i < 3; i++) {
                GameObject enemy = new GameObject("Enemy" + i, new Vector3f(100 + i * 150, 400, 0));

                SpriteRenderer enemyRenderer = new SpriteRenderer(spriteSheetTexture, 64, 64);
                // Set UV coordinates for a specific frame in the sprite sheet
                enemyRenderer.getSprite().setUVsFromPixels(0, 0, 32, 32);
                enemy.addComponent(enemyRenderer);

                // Add animation component
                enemy.addComponent(new AnimatedSpriteComponent(32, 4, 4, 0.2f));

                addGameObject(enemy);
            }

            System.out.println("ExampleScene loaded with " + getGameObjects().size() + " GameObjects");

        } catch (Exception e) {
            System.err.println("Failed to load scene content: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onUnload() {
        System.out.println("Unloading ExampleScene...");
    }

    /**
     * Example component that rotates a GameObject.
     */
    private static class RotatingComponent extends Component {
        private float rotationSpeed;

        public RotatingComponent(float rotationSpeed) {
            this.rotationSpeed = rotationSpeed;
        }

        @Override
        public void update(float deltaTime) {
            Transform transform = gameObject.getTransform();
            transform.rotate(0, 0, rotationSpeed * deltaTime);
        }
    }

    /**
     * Example component that animates a sprite sheet.
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