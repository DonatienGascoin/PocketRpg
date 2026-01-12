package com.pocket.rpg.scenes;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.components.*;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.levels.VillageLevelGenerator;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.postfx.PostProcessing;
import com.pocket.rpg.rendering.postfx.effects.VignetteEffect;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.ui.AnchorPreset;
import com.pocket.rpg.components.ui.UIButton;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.ui.text.*;
import org.joml.Random;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Demo scene showcasing the world-unit coordinate system.
 * <p>
 * Coordinate system (Y-up, centered):
 * <ul>
 *   <li>Origin (0, 0) is at world center</li>
 *   <li>Positive X = right</li>
 *   <li>Positive Y = up</li>
 *   <li>Tiles placed at integer world coordinates</li>
 * </ul>
 * <p>
 * With PPU=16 and 16×16 pixel tiles:
 * <ul>
 *   <li>Each tile = 1×1 world unit</li>
 *   <li>Tile at (5, 3) is 5 units right, 3 units up from origin</li>
 * </ul>
 */
public class DemoScene extends Scene {
    private Font font;
    private TilemapRenderer tilemap;

    public DemoScene() {
        super("Demo");
    }

    @Override
    public void onLoad() {
        font = new Font("gameData\\assets\\fonts\\zelda.ttf", 18); // TODO: Replace with Assets.load(), will need an AssetLoader for Font
//        createLevelOld();
//        createTilemapLevel();
        createVillage();
        setupCollision();  // NEW: Setup collision data
        createPlayer();
        createHUD();
    }

    @Override
    public void onUnload() {
        font.destroy();
    }

    /**
     * Sets up collision data for testing the collision system.
     * Creates various collision types around the player spawn area.
     */
    private void setupCollision() {
        CollisionMap collisionMap = getCollisionMap();

        // =====================================================================
        // TEST AREA 1: Solid walls (around spawn at 5,5)
        // Creates a small enclosure the player must navigate around
        // =====================================================================

        // Vertical wall to the right of spawn (x=8, y=3 to y=7)
        for (int y = 3; y <= 7; y++) {
            collisionMap.set(8, y, CollisionType.SOLID);
        }

        // Horizontal wall above spawn (x=3 to x=7, y=8)
        for (int x = 3; x <= 7; x++) {
            collisionMap.set(x, 8, CollisionType.SOLID);
        }

        // =====================================================================
        // TEST AREA 2: Ledges (south of spawn)
        // Player can jump DOWN but not climb UP
        // =====================================================================

        // Row of down-ledges at y=2 (player can jump from y=3 to y=2)
        for (int x = 3; x <= 7; x++) {
            collisionMap.set(x, 2, CollisionType.LEDGE_DOWN);
        }

        // =====================================================================
        // TEST AREA 3: Ice patch (west of spawn)
        // Player slides until hitting non-ice or wall
        // =====================================================================

        // Ice corridor from x=-2 to x=2, y=5
        for (int x = -2; x <= 2; x++) {
            collisionMap.set(x, 5, CollisionType.ICE);
        }
        // Wall at end of ice corridor to stop sliding
        collisionMap.set(-3, 5, CollisionType.SOLID);

        // =====================================================================
        // TEST AREA 4: Water (blocks without swim ability)
        // =====================================================================

        // Small pond at x=10-12, y=5-7
        for (int x = 10; x <= 12; x++) {
            for (int y = 5; y <= 7; y++) {
                collisionMap.set(x, y, CollisionType.WATER);
            }
        }

        // =====================================================================
        // TEST AREA 5: Tall grass (encounter zone)
        // =====================================================================

        // Grass patch at x=5-7, y=10-12
        for (int x = 5; x <= 7; x++) {
            for (int y = 10; y <= 12; y++) {
                collisionMap.set(x, y, CollisionType.TALL_GRASS);
            }
        }

        // =====================================================================
        // TEST AREA 6: Sand (slow movement)
        // =====================================================================

        // Sand strip at y=0, x=0 to x=5
        for (int x = 0; x <= 5; x++) {
            collisionMap.set(x, 0, CollisionType.SAND);
        }

        // =====================================================================
        // TEST AREA 7: Directional ledges
        // =====================================================================

        // Right ledge - can only jump right
        collisionMap.set(12, 10, CollisionType.LEDGE_RIGHT);

        // Left ledge - can only jump left
        collisionMap.set(14, 10, CollisionType.LEDGE_LEFT);

        // Up ledge - can only jump up
        collisionMap.set(13, 8, CollisionType.LEDGE_UP);

        // =====================================================================
        // TEST AREA 8: Interaction triggers (for future use)
        // =====================================================================

        // Warp tile
        collisionMap.set(0, 10, CollisionType.WARP);

        // Door tile
        collisionMap.set(15, 5, CollisionType.DOOR);

        // Script trigger
        collisionMap.set(15, 6, CollisionType.SCRIPT_TRIGGER);

        System.out.println("[DemoScene] Collision setup complete: " + collisionMap.getTileCount() + " collision tiles");
    }

    private void createTilemapLevel() {
        var sprites = getOutdoorSprites();
        Random random = new Random();

        GameObject tilemapObj = new GameObject("Tilemap", new Vector3f(0, 0, 0));
        tilemap = tilemapObj.addComponent(new TilemapRenderer());
        tilemap.setZIndex(-1);
        for (int i = -10; i < 10; i++) {
            for (int j = -10; j < 10; j++) {
                tilemap.set(i, j, new TilemapRenderer.Tile("Tile_" + i + "_" + j, sprites.get(random.nextInt(7))));
            }
        }
        var tilemap2 = tilemapObj.addComponent(new TilemapRenderer());
        tilemap2.setZIndex(0);
        for (int i = -10; i < 10; i++) {
            for (int j = -10; j < 10; j++) {
                if (random.nextInt(10) >= 8) {
                    tilemap2.set(i, j, new TilemapRenderer.Tile("Tile_" + i + "_" + j, sprites.get(getRandomNumber(16, 40))));
                }
            }
        }

        addGameObject(tilemapObj);
    }

    private void createVillage() {
        VillageLevelGenerator generator = new VillageLevelGenerator();
        generator.setSprites(
                getOutdoorSprites(),
                getRoad(),
                getTrees(),
                getWater(),
                getHouses(),
                getFences()
        );
        addGameObject(generator.generate());
    }

    private List<Sprite> getRoad() {
        var sprite = Assets.<Sprite>load("sprites/Road_16x16.png");

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 16, 16);
        Assets.persist(sheet, "spritesheets/roads.spritesheet");
        return sheet.generateAllSprites();
    }

    private List<Sprite> getTrees() {
        var sprite = Assets.<Sprite>load("sprites/trees.png");

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 32, 48);
        Assets.persist(sheet, "spritesheets/trees.spritesheet");
        return sheet.generateAllSprites();
    }

    private List<Sprite> getWater() {
        var sprite = Assets.<Sprite>load("sprites/water.png");

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 16, 16);
        Assets.persist(sheet, "spritesheets/water.spritesheet");
        return sheet.generateAllSprites();
    }

    private List<Sprite> getHouses() {
        var sprite = Assets.<Sprite>load("sprites/Building6_64x96.png");

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 64, 96);
        Assets.persist(sheet, "spritesheets/buildings6.spritesheet");
        return sheet.generateAllSprites();
    }

    private List<Sprite> getFences() {
        var sprite = Assets.<Sprite>load("sprites/Fence.png");

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 16, 16);
        Assets.persist(sheet, "spritesheets/fences.spritesheet");
        return sheet.generateAllSprites();
    }

    private static List<Sprite> getOutdoorSprites() {
        Sprite sprite = Assets.load("sprites/Outdoors_misc.png", Sprite.class);
        System.out.println("Success: " + sprite);
        var outdoorTex = Assets.<Sprite>load("sprites/Outdoors_misc.png");

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet levelSheet = new SpriteSheet(outdoorTex.getTexture(), 16, 16);
        Assets.persist(levelSheet, "spritesheets/outdoor.spritesheet");
        var sprites = levelSheet.generateAllSprites();
        return sprites;
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }


    /**
     * Creates the player character at world origin.
     * <p>
     * With 32×32 pixel sprite and PPU=16:
     * - Player sprite = 2×2 world units
     * - zIndex=1 ensures player renders above tiles (zIndex=0)
     */
    private void createPlayer() {

        var playerTex = Assets.<Sprite>load("sprites/characters/Char1_32x32.png");

        // 32×32 pixel sprites with PPU=16 → each frame is 2×2 world units
        SpriteSheet playerSheet = new SpriteSheet(playerTex.getTexture(), 32, 32, 0, 0, 0, 16);
        var sprites = playerSheet.generateAllSprites();
        Assets.persist(playerSheet, "spritesheets/player.spritesheet");

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(sprites.get(0));
        spriteRenderer.setZIndex(1);  // Render above tiles (zIndex=0)
        spriteRenderer.setOriginBottomCenter();
        // Player at world origin (Z not used for sorting anymore)
        GameObject player = new GameObject("Player", new Vector3f(5, 5, 0));

        player.addComponent(spriteRenderer);
        player.addComponent(new PlayerCameraFollow());

        GridMovement movement = player.addComponent(new GridMovement(1));
        movement.setGridPosition(5, 5);
        movement.setBaseSpeed(4f); // 4 tiles/second
        player.addComponent(new PlayerMovement());

        addGameObject(player);

        System.out.println("[DemoScene] Player created at grid position (5, 5)");
        System.out.println("[DemoScene] Test areas:");
        System.out.println("  - SOLID walls: x=8 (y=3-7), y=8 (x=3-7)");
        System.out.println("  - LEDGE_DOWN: y=2 (x=3-7) - jump down only");
        System.out.println("  - ICE: y=5 (x=-2 to 2) - slide left until wall");
        System.out.println("  - WATER: x=10-12, y=5-7 - blocks movement");
        System.out.println("  - TALL_GRASS: x=5-7, y=10-12 - encounter zone");
        System.out.println("  - SAND: y=0 (x=0-5) - slow movement");
    }

    private void createHUD() {
        // Create a Canvas (root UI element)
        GameObject canvasGO = new GameObject("MainCanvas");
        UICanvas canvas = new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, 0);
        canvasGO.addComponent(canvas);
        canvasGO.getTransform().setScale(2, 2, 2); // Does not work, is it ok ?
        addGameObject(canvasGO);

        createButton(canvasGO);
    }

    private void createButton(GameObject canvasGO) {
        GameObject buttonObj = new GameObject("Start Button");

        // Button Transform - anchored to bottom-right corner
        UITransform btnTransform = new UITransform(150, 40);
        btnTransform.setAnchor(AnchorPreset.BOTTOM_RIGHT);
        btnTransform.setOffset(-85, -30);
        btnTransform.setPivotCenter();
        buttonObj.addComponent(btnTransform);

        // Button component
        UIButton button = new UIButton();
        button.setColor(new Vector4f(0.6f, 0.6f, 0.6f, 1f));
        button.setHoverTint(.2f);
        button.setOnClick(() -> {
            List<PostEffect> effects = PostProcessing.getEffects();
            if (effects.isEmpty()) {
//                PostProcessing.addEffect(new BloomEffect());
                PostProcessing.addEffect(new VignetteEffect(.5f, .75f));
            } else {
                PostProcessing.clearEffects();
            }
            System.out.println("Button clicked!");
        });
        buttonObj.addComponent(button);

        // Button text (child) - autoFit automatically uses parent bounds
        GameObject textObj = new GameObject("Button Text");
        textObj.addComponent(new UITransform(150, 40));  // Size doesn't matter!

        UIText btnText = new UIText(font, "START");
        btnText.setAutoFit(true);  // Automatically fills parent button
        btnText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        btnText.setVerticalAlignment(VerticalAlignment.MIDDLE);
        btnText.setColor(.2f, .2f, .2f, 1f);
        textObj.addComponent(btnText);
        buttonObj.addChild(textObj);

        System.out.println("Font metrics - ascent: " + font.getAscent() +
                ", descent: " + font.getDescent() +
                ", lineHeight: " + font.getLineHeight() +
                ", pixelSize: " + font.getSize());

        Glyph glyph = font.getGlyph('A');
        System.out.println("Glyph A - bearingX: " + glyph.bearingX +
                ", bearingY: " + glyph.bearingY +
                ", width: " + glyph.width +
                ", height: " + glyph.height);

        canvasGO.addChild(buttonObj);
    }

}