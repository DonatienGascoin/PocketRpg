package com.pocket.rpg.scenes;

import com.pocket.rpg.components.*;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.input.InputManager;
import com.pocket.rpg.postProcessing.BloomEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Example scene demonstrating the new InputManager system.
 *
 * Controls:
 * - WASD: Move the player
 * - Q/E: Rotate the player
 * - +/-: Scale the player
 * - Left Mouse: Click anywhere to move player there
 * - Right Mouse: Spawn animated entity at mouse position
 * - Space: Jump (prints to console)
 * - ESC: Close window
 */
public class ExampleInputSystemScene extends Scene {

    public ExampleInputSystemScene() {
        super("ExampleScene");
    }

    @Override
    public void onLoad() {
        System.out.println("Loading ExampleScene...");
        System.out.println("===========================================");
        System.out.println("CONTROLS:");
        System.out.println("  WASD - Move player");
        System.out.println("  Q/E - Rotate player");
        System.out.println("  +/- - Scale player");
        System.out.println("  Left Click - Click to move");
        System.out.println("  Right Click - Spawn entity at mouse");
        System.out.println("  Space - Jump (console message)");
        System.out.println("  ESC - Close window");
        System.out.println("===========================================");

        try {
            // Create camera
            createCamera();

            // Create input-controlled player
            createInputControlledPlayer();

            // Create some background entities
            createBackgroundEntities();

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
        GameObject cameraObj = new GameObject("MainCamera", new Vector3f(320, 240, 0));
        Camera camera = new Camera(0.15f, 0.15f, 0.2f, 1.0f);
        cameraObj.addComponent(camera);
        addGameObject(cameraObj);
        System.out.println("✓ Camera created");
    }

    /**
     * Creates player with full input control
     */
    private void createInputControlledPlayer() throws Exception {
        GameObject player = new GameObject("Player", new Vector3f(320, 240, 0));

        Texture playerTexture = new Texture("assets/player.png");
        SpriteRenderer playerRenderer = new SpriteRenderer(playerTexture, 64, 64);
        playerRenderer.setOriginCenter();
        player.addComponent(playerRenderer);

        // Add bloom effect
        SpritePostEffect effects = player.addComponent(new SpritePostEffect());
        effects.setBufferWidth(256);
        effects.setBufferHeight(256);
        effects.setPadding(64);
        effects.addEffect(new BloomEffect(0.8f, 2.0f));

        // Add input controller component
        player.addComponent(new InputControllerComponent());

        addGameObject(player);
        System.out.println("✓ Player created with input controls");
    }

    /**
     * Creates some animated background entities
     */
    private void createBackgroundEntities() throws Exception {
        Texture texture = new Texture("assets/sheet_tight.png");
        SpriteSheet sheet = new SpriteSheet(texture, 32, 32);
        List<Sprite> frames = sheet.generateAllSprites(48, 48);

        // Create floating entities
        for (int i = 0; i < 5; i++) {
            GameObject entity = new GameObject("Background_" + i,
                    new Vector3f(100 + i * 120, 100, 0));

            SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
            renderer.setOriginCenter();
            entity.addComponent(renderer);

            entity.addComponent(new FrameAnimationComponent(frames, 0.1f + i * 0.02f));

            entity.addComponent(new TranslationComponent(
                    new Vector3f(100 + i * 120, 100, 0),
                    new Vector3f(100 + i * 120, 150, 0),
                    2.0f + i * 0.3f,
                    TranslationComponent.EasingType.SMOOTH_STEP,
                    false,
                    true
            ));

            addGameObject(entity);
        }

        System.out.println("✓ Created 5 background entities");
    }

    @Override
    public void onUnload() {
        System.out.println("Unloading ExampleScene");
    }

    // ==================== Custom Components ====================

    /**
     * Input-controlled player component demonstrating all input features:
     * - WASD movement (axis-based)
     * - Q/E rotation (axis-based)
     * - +/- scaling (axis-based)
     * - Space jump (single press)
     * - Left click to move (mouse position)
     * - Right click to spawn entity (mouse action + position)
     */
    private class InputControllerComponent extends Component {
        private float moveSpeed = 200f;
        private float rotationSpeed = 90f;
        private float scaleSpeed = 1.0f;

        private Vector3f targetPosition = null;
        private boolean movingToTarget = false;

        @Override
        public void update(float deltaTime) {
            handleMovement(deltaTime);
            handleRotation(deltaTime);
            handleScaling(deltaTime);
            handleJump();
            handleMouseActions();
        }

        private void handleMovement(float deltaTime) {
            Transform transform = getTransform();

            // Keyboard movement using axes
            Vector2f movement = InputManager.getMovementAxis();

            if (movement.lengthSquared() > 0) {
                // Cancel click-to-move if player uses keyboard
                movingToTarget = false;

                // Apply movement
                transform.translate(
                        movement.x * moveSpeed * deltaTime,
                        movement.y * moveSpeed * deltaTime,
                        0
                );
            }

            // Click-to-move
            if (movingToTarget && targetPosition != null) {
                Vector3f current = transform.getPosition();
                Vector3f direction = new Vector3f(targetPosition).sub(current);
                float distance = direction.length();

                if (distance < 5f) {
                    movingToTarget = false;
                } else {
                    direction.normalize().mul(moveSpeed * deltaTime);
                    transform.translate(direction.x, direction.y, 0);
                }
            }
        }

        private void handleRotation(float deltaTime) {
            // Rotation using Q/E keys
            float rotation = InputManager.getAxis(com.pocket.rpg.input.InputAxis.ROTATION);

            if (rotation != 0) {
                getTransform().rotate(0, 0, rotation * rotationSpeed * deltaTime);
            }
        }

        private void handleScaling(float deltaTime) {
            // Scaling using +/- keys
            float scale = InputManager.getAxis(com.pocket.rpg.input.InputAxis.SCALE);

            if (scale != 0) {
                Vector3f currentScale = getTransform().getScale();
                float newScale = currentScale.x + (scale * scaleSpeed * deltaTime);
                newScale = Math.max(0.5f, Math.min(3.0f, newScale)); // Clamp
                getTransform().setScale(newScale, newScale, 1.0f);
            }
        }

        private void handleJump() {
            // Single press detection - only triggers once per press
            if (InputManager.isActionPressedThisFrame(InputAction.JUMP)) {
                System.out.println("JUMP! Player jumped at position: " + getTransform().getPosition());
            }
        }

        private void handleMouseActions() {
            // Left click - move to mouse position
            if (InputManager.isActionPressedThisFrame(InputAction.MOUSE_PRIMARY)) {
                Vector2f mousePos = InputManager.getMousePosition();
                // Convert from screen space to game space
                // For now, direct mapping (would need camera transform for proper conversion)
                targetPosition = new Vector3f(mousePos.x, mousePos.y, 0);
                movingToTarget = true;
                System.out.println("Moving to: " + targetPosition);
            }

            // Right click - spawn entity at mouse position
            if (InputManager.isActionPressedThisFrame(InputAction.MOUSE_SECONDARY)) {
                try {
                    spawnEntityAtMouse();
                } catch (Exception e) {
                    System.err.println("Failed to spawn entity: " + e.getMessage());
                }
            }
        }

        private void spawnEntityAtMouse() throws Exception {
            Vector2f mousePos = InputManager.getMousePosition();

            Texture texture = new Texture("assets/sheet_tight.png");
            SpriteSheet sheet = new SpriteSheet(texture, 32, 32);
            List<Sprite> frames = sheet.generateAllSprites(32, 32);

            GameObject entity = new GameObject("Spawned_" + System.currentTimeMillis(),
                    new Vector3f(mousePos.x, mousePos.y, 0));

            SpriteRenderer renderer = new SpriteRenderer(frames.get(0));
            renderer.setOriginCenter();
            entity.addComponent(renderer);

            entity.addComponent(new FrameAnimationComponent(frames, 0.08f));

            // Add spinning
            entity.addComponent(new RotatingComponent(180f));

            gameObject.getScene().addGameObject(entity);

            System.out.println("Spawned entity at: " + mousePos);
        }
    }

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
}