package com.pocket.rpg.scenes;

import com.pocket.rpg.components.PlayerCameraFollow;
import com.pocket.rpg.components.PlayerMovement;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.ui.AnchorPreset;
import com.pocket.rpg.ui.UIButton;
import com.pocket.rpg.ui.UICanvas;
import com.pocket.rpg.ui.UITransform;
import com.pocket.rpg.ui.text.Font;
import com.pocket.rpg.ui.text.HorizontalAlignment;
import com.pocket.rpg.ui.text.UIText;
import com.pocket.rpg.ui.text.VerticalAlignment;
import org.joml.Random;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class DemoScene extends Scene {
    private Font font;

    public DemoScene() {
        super("Demo");
    }

    @Override
    public void onLoad() {
        font = new Font("E:\\Projects\\PocketRpg\\gameData\\assets\\fonts\\zelda.ttf", 18);
        createHUD();
        createPlayer();
        createLevel();
    }

    @Override
    public void onUnload() {
        font.destroy();
    }

    private void createLevel() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Outdoors_misc.png");
        var outdoorTex = resource.get();

        SpriteSheet levelSheet = new SpriteSheet(outdoorTex.getTexture(), 16, 16);
        var sprites = levelSheet.generateAllSprites();
        GameObject level = new GameObject("Level");
        level.getTransform().setScale(2, 2, 2);
        for (int i = -10; i < 10; i++) {
            for (int j = -10; j < 10; j++) {
                SpriteRenderer tileSprite = new SpriteRenderer(sprites.get(new Random().nextInt(7)));
                GameObject gameObject = new GameObject("Tile_" + i + "_" + j,
                        new Vector3f(i * tileSprite.getSprite().getWidth(), j * tileSprite.getSprite().getHeight(), 0));
                gameObject.addComponent(tileSprite);
                gameObject.setParent(level);
            }
        }
        addGameObject(level);
    }

    private void createPlayer() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Char1_32x32.png");
        var playerTex = resource.get();

        SpriteSheet playerSheet = new SpriteSheet(playerTex.getTexture(), 32, 32, 0, 0, 0, 16);
        var sprites = playerSheet.generateAllSprites();

        SpriteRenderer spriteRenderer = new SpriteRenderer(sprites.get(0));

        GameObject gameObject = new GameObject("Player", new Vector3f(0, 0, 0));
        gameObject.getTransform().setPosition(gameObject.getTransform().getPosition().add(new Vector3f(0, 0, 1)));
        gameObject.getTransform().setScale(2, 2, 2);
        gameObject.addComponent(spriteRenderer);
        gameObject.addComponent(new PlayerMovement());
        gameObject.addComponent(new PlayerCameraFollow());

        addGameObject(gameObject);
    }

    private void createHUD() {
        // Create a Canvas (root UI element)
        GameObject canvasGO = new GameObject("MainCanvas");
        UICanvas canvas = new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, 0);
        canvasGO.addComponent(canvas);
        addGameObject(canvasGO);

        createButton(canvasGO);
    }

    private void createButton(GameObject canvasGO) {
        GameObject buttonObj = new GameObject("Start Button");

// Transform
        UITransform btnTransform = new UITransform(150, 40);
        btnTransform.setAnchor(AnchorPreset.BOTTOM_RIGHT);
        btnTransform.setOffset(-85, -30);
        btnTransform.setPivotCenter();
        buttonObj.addComponent(btnTransform);

// Button component
        UIButton button = new UIButton();
        button.setColor(new Vector4f(0.6f, 0.6f, 0.6f, 1f));
        button.setHoverTint(.2f);
        button.setOnClick(() -> System.out.println("Button clicked!"));
        buttonObj.addComponent(button);

// Button text (child)
        GameObject textObj = new GameObject("Button Text");
        UITransform textTransform = new UITransform(0, 0);
        textTransform.setAnchor(AnchorPreset.CENTER);
        textTransform.setPivotCenter();
        textObj.addComponent(textTransform);

        UIText btnText = new UIText(font, "START");
        btnText.setHorizontalAlignment(HorizontalAlignment.CENTER);
        btnText.setVerticalAlignment(VerticalAlignment.MIDDLE);
        btnText.setColor(.2f,.2f,.2f);
        textObj.addComponent(btnText);
        buttonObj.addChild(textObj);

        canvasGO.addChild(buttonObj);
    }
}
