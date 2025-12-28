package com.pocket.rpg.scenes;

import com.pocket.rpg.components.*;
import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.postProcessing.BloomEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.ui.*;
import org.joml.Vector3f;

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
        // Anchored to TOP_LEFT (0, 1) with offset down and right
        GameObject hudPanel = new GameObject("HUDPanel");
        UITransform hudTransform = new UITransform();
        hudTransform.setAnchor(AnchorPreset.TOP_LEFT);
        hudTransform.setOffset(10, -70);  // 10px right, 70px down from top
        hudTransform.setSize(200, 60);
        hudPanel.addComponent(hudTransform);
        UIPanel panel = new UIPanel();
        panel.setColor(0.1f, 0.1f, 0.1f, 0.7f);
        hudPanel.addComponent(panel);
        hudPanel.setParent(canvasGO);

        // Create health bar background (red)
        GameObject healthBarBg = new GameObject("HealthBarBg");
        UITransform healthBgTransform = new UITransform();
        healthBgTransform.setAnchor(AnchorPreset.TOP_LEFT);
        healthBgTransform.setOffset(20, -30);
        healthBgTransform.setSize(180, 20);
        healthBarBg.addComponent(healthBgTransform);
        UIPanel healthBg = new UIPanel();
        healthBg.setColor(0.3f, 0.0f, 0.0f, 1.0f);
        healthBarBg.addComponent(healthBg);
        healthBarBg.setParent(canvasGO);

        // Create health bar fill (green) - 80% filled
        GameObject healthBarFill = new GameObject("HealthBarFill");
        UITransform healthFillTransform = new UITransform();
        healthFillTransform.setAnchor(AnchorPreset.TOP_LEFT);
        healthFillTransform.setOffset(20, -30);
        healthFillTransform.setSize(144, 20);  // 80% of 180
        healthBarFill.addComponent(healthFillTransform);
        UIPanel healthFill = new UIPanel();
        healthFill.setColor(0.0f, 0.8f, 0.0f, 1.0f);
        healthBarFill.addComponent(healthFill);
        healthBarFill.setParent(canvasGO);

        // Create mana bar background (dark blue)
        GameObject manaBarBg = new GameObject("ManaBarBg");
        UITransform manaBgTransform = new UITransform();
        manaBgTransform.setAnchor(AnchorPreset.TOP_LEFT);
        manaBgTransform.setOffset(20, -55);
        manaBgTransform.setSize(180, 15);
        manaBarBg.addComponent(manaBgTransform);
        UIPanel manaBg = new UIPanel();
        manaBg.setColor(0.0f, 0.0f, 0.3f, 1.0f);
        manaBarBg.addComponent(manaBg);
        manaBarBg.setParent(canvasGO);

        // Create mana bar fill (bright blue) - 60% filled
        GameObject manaBarFill = new GameObject("ManaBarFill");
        UITransform manaFillTransform = new UITransform();
        manaFillTransform.setAnchor(AnchorPreset.TOP_LEFT);
        manaFillTransform.setOffset(20, -55);
        manaFillTransform.setSize(108, 15);  // 60% of 180
        manaBarFill.addComponent(manaFillTransform);
        UIPanel manaFill = new UIPanel();
        manaFill.setColor(0.2f, 0.4f, 1.0f, 1.0f);
        manaBarFill.addComponent(manaFill);
        manaBarFill.setParent(canvasGO);

        // Create icon in bottom-right using UIImage (with texture)
        try {
            Texture iconTexture = new Texture("gameData/assets/player.png");

            GameObject iconGO = new GameObject("PlayerIcon");
            UITransform iconTransform = new UITransform();
            iconTransform.setAnchor(AnchorPreset.BOTTOM_RIGHT);
            iconTransform.setOffset(-74, 10);  // 74px from right, 10px up
            iconTransform.setSize(64, 64);
            iconGO.addComponent(iconTransform);
            UIImage icon = new UIImage(iconTexture);
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

        // Add a small notification badge at bottom-right
        GameObject badge = new GameObject("NotificationBadge");
        UITransform badgeTransform = new UITransform();
        badgeTransform.setAnchor(AnchorPreset.BOTTOM_RIGHT);
        badgeTransform.setOffset(-20, 54);  // Near the icon
        badgeTransform.setSize(20, 20);
        badge.addComponent(badgeTransform);
        UIPanel badgePanel = new UIPanel();
        badgePanel.setColor(1.0f, 0.2f, 0.2f, 1.0f);
        badge.addComponent(badgePanel);
        badge.setParent(overlayCanvas);

        // ===========================================
        // BUTTON DEMO (Phase 3)
        // ===========================================

        // Simple color button with default hover tint
        GameObject playBtn = new GameObject("PlayButton");
        UITransform playTransform = new UITransform();
        playTransform.setAnchor(AnchorPreset.CENTER);
        playTransform.setOffset(0, -100);
        playTransform.setSize(150, 50);
        playBtn.addComponent(playTransform);
        UIButton playButton = new UIButton();
        playButton.setColor(0.2f, 0.6f, 0.2f, 1f);  // Green
        playButton.setOnClick(() -> System.out.println("Play button clicked!"));
        playBtn.addComponent(playButton);
        playBtn.setParent(canvasGO);

        // Button with custom hover tint
        GameObject optionsBtn = new GameObject("OptionsButton");
        UITransform optionsTransform = new UITransform();
        optionsTransform.setAnchor(AnchorPreset.CENTER);
        optionsTransform.setOffset(0, -160);
        optionsTransform.setSize(150, 50);
        optionsBtn.addComponent(optionsTransform);
        UIButton optionsButton = new UIButton();
        optionsButton.setColor(0.3f, 0.3f, 0.6f, 1f);  // Blue-ish
        optionsButton.setHoverTint(0.2f);  // 20% darker on hover
        optionsButton.setOnClick(() -> System.out.println("Options button clicked!"));
        optionsBtn.addComponent(optionsButton);
        optionsBtn.setParent(canvasGO);

        // Button with custom hover callback (no auto-tint)
        GameObject quitBtn = new GameObject("QuitButton");
        UITransform quitTransform = new UITransform();
        quitTransform.setAnchor(AnchorPreset.CENTER);
        quitTransform.setOffset(0, -220);
        quitTransform.setSize(150, 50);
        quitBtn.addComponent(quitTransform);
        UIButton quitButton = new UIButton();
        quitButton.setColor(0.6f, 0.2f, 0.2f, 1f);  // Red
        // Custom hover callbacks disable auto-tint
        quitButton.setOnHover(() -> {
            System.out.println("Hovering over Quit...");
            quitButton.setColor(1.0f, 0.3f, 0.3f, 1f);  // Brighter red
        });
        quitButton.setOnExit(() -> {
            quitButton.setColor(0.6f, 0.2f, 0.2f, 1f);  // Back to normal
        });
        quitButton.setOnClick(() -> System.out.println("Quit button clicked!"));
        quitBtn.addComponent(quitButton);
        quitBtn.setParent(canvasGO);

        System.out.println("✓ UI demo created");
    }

    /*private void createUIDemoOld() {
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
    }*/

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

        SpriteRenderer renderer = new SpriteRenderer(); renderer.setSprite(frames.get(0));
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

        SpriteRenderer renderer = new SpriteRenderer(); renderer.setSprite(frames.get(0));
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

        SpriteRenderer renderer = new SpriteRenderer(); renderer.setSprite(frames.get(0));
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

        SpriteRenderer renderer = new SpriteRenderer(); renderer.setSprite(frames.get(0));
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

        SpriteRenderer renderer = new SpriteRenderer(); renderer.setSprite(frames.get(0));
        renderer.setOriginCenter();
        entity.addComponent(renderer);
        entity.addComponent(new FrameAnimationComponent(frames, 0.1f));

        addGameObject(entity);
        System.out.println("✓ Complex animation");
    }


    private void visualizeSpriteSheet(
            List<Sprite> frames,
            float screenWidth,
            float screenHeight,
            float padding) {

        if (frames == null || frames.isEmpty()) return;
        if (screenWidth <= 0) screenWidth = 640f; // fallback if caller passed 0

        // use sprite render sizes (float)
        float spriteW = frames.get(0).getWidth();
        float spriteH = frames.get(0).getHeight();
        float stepX = spriteW + padding;
        float stepY = spriteH + padding;

        // compute spritesPerRow. If screenHeight > 0 we will consider vertical fit later;
        // we always force at least one column.
        int spritesPerRow = Math.max(1, (int) Math.floor(screenWidth / stepX));

        // if screenHeight provided and tiny, try to compute a better spritesPerRow so it fits vertically too
        if (screenHeight > 0) {
            int maxRows = Math.max(1, (int) Math.floor(screenHeight / stepY));
            // If rows would exceed number of sprites, we can try to make a squarer grid:
            int idealPerRow = (int) Math.ceil((double) frames.size() / (double) maxRows);
            spritesPerRow = Math.max(1, Math.min(spritesPerRow, idealPerRow));
        }

        // compute grid size for centering
        int cols = spritesPerRow;
        int rows = (int) Math.ceil((double) frames.size() / cols);
        float gridWidth = cols * stepX - padding;
        float gridHeight = rows * stepY - padding;

        // starting origin: center the grid horizontally and place near top (or centered vertically if screenHeight set)
        float startX = Math.max(0, (screenWidth - gridWidth) / 2f);
        float startY;
        if (screenHeight > 0) {
            startY = Math.max(0, (screenHeight - gridHeight) / 2f);
        } else {
            startY = 20f; // small top margin if no height provided
        }

        for (int i = 0; i < frames.size(); i++) {
            Sprite sprite = frames.get(i);
            int col = i % cols;
            int row = i / cols;

            float x = startX + col * stepX;
            float y = startY + row * stepY;

            GameObject entity = new GameObject("Sprite_" + i, new Vector3f(x + spriteW / 2f, y + spriteH / 2f, 0f));
            SpriteRenderer renderer = new SpriteRenderer();
            renderer.setSprite(sprite);
            renderer.setOriginCenter(); // keep consistent origin
            entity.addComponent(renderer);

            addGameObject(entity);
        }
    }


    private void createSharedSpriteEntities() throws Exception {
        Texture texture = new Texture("gameData/assets/sheet_tight.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32);
        List<Sprite> sharedFrames = sheet.generateAllSprites(32, 32);

        for (int i = 0; i < 10; i++) {
            GameObject entity = new GameObject("Shared_" + i,
                    new Vector3f(100 + i * 50, 300, 0));

            SpriteRenderer renderer = new SpriteRenderer();
            renderer.setSprite(sharedFrames.get(0));
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

}