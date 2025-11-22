package com.pocket.rpg.scenes;

import com.pocket.rpg.components.*;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.postProcessing.BloomEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector3f;

import java.util.List;

/**
 * Comprehensive example scene demonstrating:
 * - All SpriteSheet constructor variations
 * - Sprite caching and sharing
 * - Different animation techniques
 * - Camera system
 * - Component-based architecture
 */
public class ExampleScene extends Scene {

    public ExampleScene() {
        super("ExampleScene");
    }

    @Override
    public void onLoad() {
        System.out.println("Loading ExampleScene...");

        try {
            // Create camera
            createCamera();

            // Create player with rotation
            createPlayer();

            // Demonstrate all SpriteSheet constructors
            createTightlyPackedAnimation();
            createUniformSpacingAnimation();
            createXYSpacingAnimation();
            createMarginAnimation();
            createComplexAnimation();

            // Demonstrate sprite sharing
            createSharedSpriteEntities();

            System.out.println("ExampleScene loaded successfully with " + getGameObjects().size() + " GameObjects");

        } catch (Exception e) {
            System.err.println("Failed to load scene: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates camera with custom clear color
     */
    private void createCamera() {
        GameObject cameraObj = new GameObject("MainCamera", new Vector3f(0, 0, 0));
        Camera camera = new Camera(0.15f, 0.15f, 0.2f, 1.0f);
        cameraObj.addComponent(camera);
        addGameObject(cameraObj);
        System.out.println("✓ Camera created");
    }

    /**
     * Creates rotating player
     */
    private void createPlayer() throws Exception {
        GameObject player = new GameObject("Player", new Vector3f(400, 50, 0));

        Texture playerTexture = new Texture("assets/player.png");
        SpriteRenderer playerRenderer = new SpriteRenderer(playerTexture, 64, 64);
        playerRenderer.setOriginCenter();
        player.addComponent(playerRenderer);

        player.addComponent(new RotatingComponent(45f));

        SpritePostEffect effects = player.addComponent(new SpritePostEffect());
        effects.setBufferWidth(256);
        effects.setBufferHeight(256);
        effects.setPadding(64); // Extra space for bloom to bleed
        effects.addEffect(new BloomEffect(0.8f, 2.0f));


        addGameObject(player);
        System.out.println("✓ Player created");
    }

    /**
     * Example 1: Tightly packed sprite sheet (no spacing, no offset)
     * Constructor: new SpriteSheet(texture, 32, 32)
     */
    private void createTightlyPackedAnimation() throws Exception {
        Texture texture = new Texture("assets/sheet_tight.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32);

        GameObject entity = new GameObject("TightPacked", new Vector3f(100, 150, 0));

        // Pre-generate all frames for animation
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);

        // Frame-based animation component
        entity.addComponent(new FrameAnimationComponent(frames, 0.1f));

        addGameObject(entity);
        System.out.println("✓ Tightly packed animation (no spacing/offset)");
    }

    /**
     * Example 2: Uniform spacing sprite sheet
     * Constructor: new SpriteSheet(texture, 32, 32, 2)
     */
    private void createUniformSpacingAnimation() throws Exception {
        Texture texture = new Texture("assets/sheet_spacing.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 2);

        GameObject entity = new GameObject("UniformSpacing", new Vector3f(200, 150, 0));

        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);

        entity.addComponent(new FrameAnimationComponent(frames, 0.12f));

        addGameObject(entity);
        System.out.println("✓ Uniform spacing animation (2px spacing)");
    }

    /**
     * Example 3: Different X/Y spacing
     * Constructor: new SpriteSheet(texture, 32, 32, spacingX, spacingY, 0, 0)
     */
    private void createXYSpacingAnimation() throws Exception {
        Texture texture = new Texture("assets/sheet_xy_spacing.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 4, 2, 0, 0);

        GameObject entity = new GameObject("XYSpacing", new Vector3f(300, 150, 0));

        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);

        entity.addComponent(new FrameAnimationComponent(frames, 0.08f));

        addGameObject(entity);
        System.out.println("✓ X/Y spacing animation (4px/2px spacing)");
    }

    /**
     * Example 4: Sprite sheet with margin/offset
     * Constructor: new SpriteSheet(texture, 32, 32, 0, 0, offsetX, offsetY)
     */
    private void createMarginAnimation() throws Exception {
        Texture texture = new Texture("assets/sheet_margin.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 0, 0, 8, 8);

        GameObject entity = new GameObject("Margin", new Vector3f(400, 150, 0));

        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);

        entity.addComponent(new FrameAnimationComponent(frames, 0.15f));

        addGameObject(entity);
        System.out.println("✓ Margin animation (8px offset)");
    }

    /**
     * Example 5: Complex sprite sheet (spacing + offset)
     * Constructor: new SpriteSheet(texture, 32, 32, spacingX, spacingY, offsetX, offsetY)
     */
    private void createComplexAnimation() throws Exception {
        Texture texture = new Texture("assets/sheet_complex.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 2, 4, 8, 8);

        GameObject entity = new GameObject("Complex", new Vector3f(500, 150, 0));

        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);

        entity.addComponent(new FrameAnimationComponent(frames, 0.1f));

        addGameObject(entity);
        System.out.println("✓ Complex animation (2/4px spacing + 8/8px offset)");
    }

    /**
     * Example 6: Demonstrates sprite sharing
     * Multiple GameObjects share the same Sprite instances for efficiency
     */
    private void createSharedSpriteEntities() throws Exception {
        Texture texture = new Texture("assets/sheet_tight.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32);

        // Generate sprites once
        List<Sprite> sharedFrames = sheet.generateAllSprites(32, 32);

        // Create 10 entities that all share the same sprites
        for (int i = 0; i < 10; i++) {
            GameObject entity = new GameObject("Shared_" + i,
                    new Vector3f(100 + i * 50, 300, 0));

            SpriteRenderer renderer = new SpriteRenderer(sharedFrames.get(0));
            renderer.setOriginCenter();
            entity.addComponent(renderer);

            // Each has different animation speed
            entity.addComponent(new FrameAnimationComponent(sharedFrames, 0.05f + i * 0.02f));

            // Add floating movement
            entity.addComponent(new TranslationComponent(
                    new Vector3f(100 + i * 50, 300, 0),
                    new Vector3f(100 + i * 50, 280, 0),
                    1.0f + i * 0.2f,
                    TranslationComponent.EasingType.SMOOTH_STEP,
                    false,
                    true
            ));

            addGameObject(entity);
        }

        System.out.println("✓ Created 10 entities sharing " + sharedFrames.size() + " sprites");
        System.out.println("  Memory saved: ~" + (10 * sharedFrames.size() - sharedFrames.size()) + " sprite instances");
    }

    @Override
    public void onUnload() {
        System.out.println("Unloading ExampleScene");
    }

    // ==================== Custom Components ====================

    /**
     * Component that rotates a GameObject continuously
     */
    private static class RotatingComponent extends Component {
        private final float rotationSpeed;

        public RotatingComponent(float rotationSpeed) {
            this.rotationSpeed = rotationSpeed;
        }

        @Override
        public void update(float deltaTime) {
            gameObject.getTransform().rotate(0, 0, rotationSpeed * deltaTime);
        }
    }

    /**
     * Frame-based animation component.
     * Cycles through a list of pre-generated sprite frames.
     * More efficient than UV-based animation when sprites are cached.
     */
    private static class FrameAnimationComponent extends Component {
        private final List<Sprite> frames;
        private final float frameTime;
        private int currentFrame = 0;
        private float timer = 0;

        public FrameAnimationComponent(List<Sprite> frames, float frameTime) {
            this.frames = frames;
            this.frameTime = frameTime;
        }

        @Override
        public void update(float deltaTime) {
            SpriteRenderer renderer = gameObject.getComponent(SpriteRenderer.class);
            if (renderer == null || frames.isEmpty()) return;

            timer += deltaTime;

            if (timer >= frameTime) {
                timer -= frameTime;
                currentFrame = (currentFrame + 1) % frames.size();
                renderer.setSprite(frames.get(currentFrame));
            }
        }
    }

    /**
     * UV-based animation component.
     * Updates sprite UVs instead of changing sprite reference.
     * More memory efficient but requires SpriteSheet reference.
     */
    private static class UVAnimationComponent extends Component {
        private final SpriteSheet spriteSheet;
        private final float frameTime;
        private int currentFrame = 0;
        private float timer = 0;

        public UVAnimationComponent(SpriteSheet spriteSheet, float frameTime) {
            this.spriteSheet = spriteSheet;
            this.frameTime = frameTime;
        }

        @Override
        public void update(float deltaTime) {
            SpriteRenderer renderer = gameObject.getComponent(SpriteRenderer.class);
            if (renderer == null || renderer.getSprite() == null) return;

            timer += deltaTime;

            if (timer >= frameTime) {
                timer -= frameTime;
                currentFrame = (currentFrame + 1) % spriteSheet.getTotalFrames();

                // Update sprite UVs to show different frame
                spriteSheet.updateSpriteFrame(renderer.getSprite(), currentFrame);
            }
        }
    }
}