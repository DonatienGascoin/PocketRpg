package com.pocket.rpg.engine;

import com.pocket.rpg.rendering.Renderer;
import com.pocket.rpg.scenes.ExampleScene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.utils.DefaultCallback;
import com.pocket.rpg.utils.WindowConfig;

public class GameWindow extends Window {

    private Renderer renderer;
    private SceneManager sceneManager;

    /*// Game content
    private Texture playerTexture;
    private Sprite playerSprite;

    // Sprite sheet sprites
    private SpriteSheet spriteSheet;
    private List<Sprite> sheetSprites;

    // Animation
    private float rotation = 0;
    private int currentFrame = 0;
    private float animationTimer = 0;
    private static final float ANIMATION_SPEED = 0.2f; // Seconds per frame*/

    public GameWindow() {
        super(WindowConfig.builder().callback(new DefaultCallback()).build());
    }

    @Override
    protected void initGame() {
        // Initialize renderer
        renderer = new Renderer();
        renderer.init(getScreenWidth(), getScreenHeight());

        // Initialize scene manager
        sceneManager = new SceneManager(renderer);

        // Register your scenes
        sceneManager.registerScene(new ExampleScene());

        // Load the first scene
        sceneManager.loadScene("ExampleScene");
    }

    /**
     * Renders all game content.
     *
     * @param deltaTime Time since last frame in seconds
     */
    @Override
    protected void renderGame(float deltaTime) {
        // Update the current scene (which updates all GameObjects and Components)
        sceneManager.update(deltaTime);

        // Render the current scene (which renders all SpriteRenderers)
        sceneManager.render();
    }



    @Override
    protected void destroyGame() {
        // Clean up scene manager
        if (sceneManager != null) {
            sceneManager.destroy();
        }

        // Clean up renderer
        if (renderer != null) {
            renderer.destroy();
        }

        super.destroy();
    }
}