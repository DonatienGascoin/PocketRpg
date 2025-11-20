package com.pocket.rpg.engine;

import com.pocket.rpg.rendering.Renderer;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.utils.WindowConfig;

import java.util.ArrayList;
import java.util.List;

public class GameWindow extends Window {

    private Renderer renderer;

    // Game content
    private Texture playerTexture;
    private Sprite playerSprite;

    // Sprite sheet sprites
    private SpriteSheet spriteSheet;
    private List<Sprite> sheetSprites;

    // Animation
    private float rotation = 0;
    private int currentFrame = 0;
    private float animationTimer = 0;
    private static final float ANIMATION_SPEED = 0.2f; // Seconds per frame

    public GameWindow() {
        super(WindowConfig.builder().build());
    }

    @Override
    protected void initGame() {
        // Initialize renderer
        renderer = new Renderer();
        renderer.init(getScreenWidth(), getScreenHeight());

        // Load game content
        loadGameContent();
    }

    /**
     * Renders all game content.
     *
     * @param deltaTime Time since last frame in seconds
     */
    @Override
    protected void renderGame(float deltaTime) {
        // Update animations
        updateAnimations(deltaTime);

        // Render all sprites
        renderer.begin();

        // Render full texture sprite (if loaded)
        if (playerSprite != null) {
            renderer.drawSprite(playerSprite);
        }

        // Render sprite sheet sprites (if loaded)
        if (sheetSprites != null) {
            for (Sprite sprite : sheetSprites) {
                renderer.drawSprite(sprite);
            }
        }

        renderer.end();
    }

    /**
     * Updates sprite animations.
     *
     * @param deltaTime Time since last frame in seconds
     */
    private void updateAnimations(float deltaTime) {
        // Rotate the full texture sprite
        if (playerSprite != null) {
            rotation += 45 * deltaTime; // 45 degrees per second
            if (rotation > 360) rotation -= 360;
            playerSprite.setRotation(rotation);
        }

        // Animate sprite sheet sprites using SpriteSheet class
        if (spriteSheet != null && sheetSprites != null && !sheetSprites.isEmpty()) {
            animationTimer += deltaTime;

            if (animationTimer >= ANIMATION_SPEED) {
                animationTimer -= ANIMATION_SPEED;
                currentFrame = (currentFrame + 1) % spriteSheet.getTotalFrames();

                // Update all sprites to show the new frame
                for (Sprite sprite : sheetSprites) {
                    spriteSheet.updateSpriteFrame(sprite, currentFrame);
                }
            }
        }
    }

    @Override
    protected void destroyGame() {
        // Clean up game resources
        if (playerTexture != null) {
            playerTexture.destroy();
        }
        if (spriteSheet != null) {
            spriteSheet.getTexture().destroy();
        }

        // Clean up systems
        renderer.destroy();

        super.destroy();
    }

    /**
     * Loads all game assets (textures, sprites, etc.)
     */
    private void loadGameContent() {
        try {
            // Load full texture sprite
            playerTexture = new Texture("assets/player.png");
            playerSprite = new Sprite(playerTexture,
                    getScreenWidth() / 2f - 32,
                    getScreenHeight() / 2f - 32,
                    64, 64);
            playerSprite.setOrigin(0.5f, 0.5f); // Rotate around center

        } catch (Exception e) {
            System.err.println("Failed to load player texture: " + e.getMessage());
            System.err.println("Run TestTextureGenerator.main() to create test textures");
        }

        try {
            // Load sprite sheet using SpriteSheet class
            Texture sheetTexture = new Texture("assets/spritesheet.png");
            spriteSheet = new SpriteSheet(sheetTexture, 32, 32); // 32x32 sprites

            sheetSprites = new ArrayList<>();

            // Create sprites from sprite sheet at different positions
            for (int i = 0; i < 3; i++) {
                float x = 100 + i * 150;
                float y = 400;

                // Create sprite from frame 0 with screen size 64x64
                Sprite sprite = spriteSheet.getSprite(0, x, y, 64, 64);
                sprite.setOrigin(0.5f, 0.5f);
                sheetSprites.add(sprite);
            }

        } catch (Exception e) {
            System.err.println("Failed to load sprite sheet: " + e.getMessage());
            System.err.println("Run SpriteSheetGenerator.main() to create a test sprite sheet");
        }
    }
}