package com.pocket.rpg.scenes;

import com.pocket.rpg.components.*;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.levels.VillageLevelGenerator;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.postProcessing.PostProcessing;
import com.pocket.rpg.postProcessing.postEffects.VignetteEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.ui.AnchorPreset;
import com.pocket.rpg.ui.UIButton;
import com.pocket.rpg.ui.UICanvas;
import com.pocket.rpg.ui.UITransform;
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
        font = new Font("E:\\Projects\\PocketRpg\\gameData\\assets\\fonts\\zelda.ttf", 18);
//        createLevelOld();
//        createTilemapLevel();
        createVillage();
        createPlayer();
        createHUD();
    }

    @Override
    public void onUnload() {
        font.destroy();
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
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Road_16x16.png");
        var sprite = resource.get();

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 16, 16);
        return sheet.generateAllSprites();
    }

    private List<Sprite> getTrees() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/trees.png");
        var sprite = resource.get();

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 32, 48);
        return sheet.generateAllSprites();
    }

    private List<Sprite> getWater() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/water.png");
        var sprite = resource.get();

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 16, 16);
        return sheet.generateAllSprites();
    }

    private List<Sprite> getHouses() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Building6_64x96.png");
        var sprite = resource.get();

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 64, 96);
        return sheet.generateAllSprites();
    }

    private List<Sprite> getFences() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Fence.png");
        var sprite = resource.get();

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet sheet = new SpriteSheet(sprite.getTexture(), 16, 16);
        return sheet.generateAllSprites();
    }

    private static List<Sprite> getOutdoorSprites() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Outdoors_misc.png");
        var outdoorTex = resource.get();

        // 16×16 pixel tiles with PPU=16 → each tile is 1×1 world units
        SpriteSheet levelSheet = new SpriteSheet(outdoorTex.getTexture(), 16, 16);
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

        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Char1_32x32.png");
        var playerTex = resource.get();

        // 32×32 pixel sprites with PPU=16 → each frame is 2×2 world units
        SpriteSheet playerSheet = new SpriteSheet(playerTex.getTexture(), 32, 32, 0, 0, 0, 16);
        var sprites = playerSheet.generateAllSprites();

        SpriteRenderer spriteRenderer = new SpriteRenderer(sprites.get(0));
        spriteRenderer.setZIndex(1);  // Render above tiles (zIndex=0)
        spriteRenderer.setOriginBottomCenter();
        // Player at world origin (Z not used for sorting anymore)
        GameObject player = new GameObject("Player", new Vector3f(5, 5, 0));

        player.addComponent(spriteRenderer);
        player.addComponent(new PlayerCameraFollow());

        GridMovement movement = player.addComponent(new GridMovement(tilemap));
        movement.setGridPosition(5, 5);
        movement.setMoveSpeed(4f); // 4 tiles/second
        player.addComponent(new PlayerMovement(movement));

        addGameObject(player);
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
        btnText.setColor(.2f, .2f, .2f);
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
