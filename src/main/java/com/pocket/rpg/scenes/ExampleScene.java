package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpritePostEffect;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TranslationComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.postProcessing.BloomEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.ui.UICanvas;
import com.pocket.rpg.ui.UIImage;
import com.pocket.rpg.ui.UIPanel;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Example scene demonstrating sprite animations and the UI system.
 */
public class ExampleScene extends Scene {

    public ExampleScene() {
        super("ExampleScene");
    }

    @Override
    public void onLoad() {
        System.out.println("Loading ExampleScene...");

        try {
            createPlayer();
            createTightlyPackedAnimation();
            createUniformSpacingAnimation();
            createXYSpacingAnimation();
            createMarginAnimation();
            createComplexAnimation();
            createSharedSpriteEntities();

            // === UI DEMO ===
            createUIDemo();

            System.out.println("ExampleScene loaded successfully with " + getGameObjects().size() + " GameObjects");

        } catch (Exception e) {
            System.err.println("Failed to load scene: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===========================================
    // UI DEMO
    // ===========================================

    private void createUIDemo() {
        System.out.println("Creating UI demo...");

        // Create a Canvas (root UI element)
        GameObject canvasGO = new GameObject("MainCanvas");
        UICanvas canvas = new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, 0);
        canvasGO.addComponent(canvas);
        addGameObject(canvasGO);

        // Create a semi-transparent dark panel at top-left (HUD background)
        GameObject hudPanel = new GameObject("HUDPanel", new Vector3f(10, 10, 0));
        Texture hudBg = new Texture("gameData/assets/sprites/hudBg.png");
        UIImage uiImage = new UIImage(hudBg);
//        UIPanel panel = new UIPanel(200, 60);
//        panel.setColor(0.1f, 0.1f, 0.1f, 0.7f);
        hudPanel.addComponent(uiImage);
        hudPanel.setParent(canvasGO);

        // Create health bar background (red)
        GameObject healthBarBg = new GameObject("HealthBarBg", new Vector3f(20, 20, 0));
        Texture healthBg = new Texture("gameData/assets/sprites/lifeBarBg.png");
        UIImage uiHealthBg = new UIImage(healthBg);
//        UIPanel healthBg = new UIPanel(180, 20);
//        healthBg.setColor(0.3f, 0.0f, 0.0f, 1.0f);
        healthBarBg.addComponent(uiHealthBg);
        healthBarBg.setParent(hudPanel);

        // Create health bar fill (green)
        GameObject healthBarFill = new GameObject("HealthBarFill", new Vector3f(20, 20, 0));
        Texture health = new Texture("gameData/assets/sprites/lifeBar.png");
        UIImage uiHealth = new UIImage(health);
//        UIPanel healthFill = new UIPanel(144, 20);  // 80% of 180
//        healthFill.setColor(0.0f, 0.8f, 0.0f, 1.0f);
        healthBarFill.addComponent(uiHealth);
        healthBarFill.setParent(healthBarBg);

        // Create mana bar background (dark blue)
        GameObject manaBarBg = new GameObject("ManaBarBg", new Vector3f(20, 45, 0));
        UIPanel manaBg = new UIPanel(180, 15);
        manaBg.setColor(0.0f, 0.0f, 0.3f, 1.0f);
        manaBarBg.addComponent(manaBg);
        manaBarBg.setParent(hudPanel);

        // Create mana bar fill (bright blue)
        GameObject manaBarFill = new GameObject("ManaBarFill", new Vector3f(20, 45, 0));
        Texture mana = new Texture("gameData/assets/sprites/manaBar.png");
        UIImage uiMana = new UIImage(mana);
//        UIPanel manaFill = new UIPanel(108, 15);  // 60% of 180
//        manaFill.setColor(0.2f, 0.4f, 1.0f, 1.0f);
        manaBarFill.addComponent(uiMana);
        manaBarFill.setParent(manaBarBg);

        // Create icon in bottom-right using UIImage (with texture)
        try {
            Texture iconTexture = new Texture("gameData/assets/player.png");

            GameObject iconGO = new GameObject("PlayerIcon", new Vector3f(700, 500, 0));
            UIImage icon = new UIImage(iconTexture);
            icon.setSize(64, 64);
            iconGO.addComponent(icon);
            iconGO.setParent(canvasGO);

            // Add pulsing animation to icon
            iconGO.addComponent(new UIPulseComponent());

        } catch (Exception e) {
            System.err.println("Could not load icon texture: " + e.getMessage());
        }

        // Create a second canvas with higher sort order (renders on top)
        GameObject overlayCanvas = new GameObject("OverlayCanvas");
        UICanvas overlay = new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, 10);
        overlayCanvas.addComponent(overlay);
        addGameObject(overlayCanvas);

        // Add a small notification badge
        GameObject badge = new GameObject("NotificationBadge", new Vector3f(750, 490, 0));
        UIPanel badgePanel = new UIPanel(20, 20);
        badgePanel.setColor(1.0f, 0.2f, 0.2f, 1.0f);
        badge.addComponent(badgePanel);
        badge.setParent(overlayCanvas);

        System.out.println("✓ UI demo created");
    }

    /**
     * Component that pulses a UI element's alpha.
     */
    private static class UIPulseComponent extends Component {
        private float timer = 0;
        private UIImage image;

        @Override
        protected void onStart() {
            image = gameObject.getComponent(UIImage.class);
        }

        @Override
        public void update(float deltaTime) {
            if (image == null) return;
            timer += deltaTime * 2;
            float alpha = 0.5f + 0.5f * (float) Math.sin(timer);
            image.setAlpha(alpha);
        }
    }

    // ===========================================
    // EXISTING GAME CONTENT
    // ===========================================

    private void createPlayer() throws Exception {
        GameObject player = new GameObject("Player", new Vector3f(400, 50, 0));

        Texture playerTexture = new Texture("gameData/assets/player.png");
        SpriteRenderer playerRenderer = new SpriteRenderer(playerTexture, 64, 64);
        playerRenderer.setOriginCenter();
        player.addComponent(playerRenderer);

        player.addComponent(new RotatingComponent(45f));

        SpritePostEffect effects = player.addComponent(new SpritePostEffect());
        effects.setBufferWidth(256);
        effects.setBufferHeight(256);
        effects.setPadding(64);
        effects.addEffect(new BloomEffect(0.8f, 2.0f));

        addGameObject(player);
        System.out.println("✓ Player created");
    }

    private void createTightlyPackedAnimation() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_tight.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32);

        GameObject entity = new GameObject("TightPacked", new Vector3f(100, 150, 0));
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);
        entity.addComponent(new FrameAnimationComponent(frames, 0.1f));

        addGameObject(entity);
        System.out.println("✓ Tightly packed animation");
    }

    private void createUniformSpacingAnimation() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_spacing.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 2);

        GameObject entity = new GameObject("UniformSpacing", new Vector3f(200, 150, 0));
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);
        entity.addComponent(new FrameAnimationComponent(frames, 0.12f));

        addGameObject(entity);
        System.out.println("✓ Uniform spacing animation");
    }

    private void createXYSpacingAnimation() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_xy_spacing.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 4, 2, 0, 0);

        GameObject entity = new GameObject("XYSpacing", new Vector3f(300, 150, 0));
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);
        entity.addComponent(new FrameAnimationComponent(frames, 0.08f));

        addGameObject(entity);
        System.out.println("✓ X/Y spacing animation");
    }

    private void createMarginAnimation() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_margin.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 0, 0, 8, 8);

        GameObject entity = new GameObject("Margin", new Vector3f(400, 150, 0));
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);
        entity.addComponent(new FrameAnimationComponent(frames, 0.15f));

        addGameObject(entity);
        System.out.println("✓ Margin animation");
    }

    private void createComplexAnimation() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_complex.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32, 2, 4, 8, 8);

        GameObject entity = new GameObject("Complex", new Vector3f(500, 150, 0));
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);
        entity.addComponent(new FrameAnimationComponent(frames, 0.1f));

        addGameObject(entity);
        System.out.println("✓ Complex animation");
    }

    private void createSharedSpriteEntities() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_tight.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32);
        List<Sprite> sharedFrames = sheet.generateAllSprites(32, 32);

        for (int i = 0; i < 10; i++) {
            GameObject entity = new GameObject("Shared_" + i,
                    new Vector3f(100 + i * 50, 300, 0));

            SpriteRenderer renderer = new SpriteRenderer(sharedFrames.get(0));
            renderer.setOriginCenter();
            entity.addComponent(renderer);

            entity.addComponent(new FrameAnimationComponent(sharedFrames, 0.05f + i * 0.02f));

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

        System.out.println("✓ Created 10 entities sharing sprites");
    }

    @Override
    public void onUnload() {
        System.out.println("Unloading ExampleScene");
    }

    // ===========================================
    // HELPER COMPONENTS
    // ===========================================

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
}