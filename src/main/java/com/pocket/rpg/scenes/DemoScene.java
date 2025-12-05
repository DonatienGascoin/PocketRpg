package com.pocket.rpg.scenes;

import com.pocket.rpg.components.PlayerCameraFollow;
import com.pocket.rpg.components.PlayerMovement;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.AssetManager;
import org.joml.Random;
import org.joml.Vector3f;

public class DemoScene extends Scene {
    public DemoScene() {
        super("Demo");
    }

    @Override
    public void onLoad() {
        createPlayer();
        createLevel();
    }

    private void createLevel() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Outdoors_misc.png");
        var outdoorTex = resource.get();

        SpriteSheet levelSheet = new SpriteSheet(outdoorTex.getTexture(), 16, 16);
        var sprites = levelSheet.generateAllSprites();

        for (int i = -10; i < 10; i++) {
            for (int j = -10; j < 10; j++) {
                SpriteRenderer tileSprite = new SpriteRenderer(sprites.get(new Random().nextInt(7)));
                GameObject gameObject = new GameObject("Tile_" + i + "_" + j,
                        new Vector3f(i * tileSprite.getSprite().getWidth(), j * tileSprite.getSprite().getHeight(), 0));
                gameObject.addComponent(tileSprite);
                addGameObject(gameObject);
            }
        }
    }

    private void createPlayer() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Char1_32x32.png");
        var playerTex = resource.get();

        SpriteSheet playerSheet = new SpriteSheet(playerTex.getTexture(), 32, 32, 0, 0, 0, 16);
        var sprites = playerSheet.generateAllSprites();

        SpriteRenderer spriteRenderer = new SpriteRenderer(sprites.get(0));

        GameObject gameObject = new GameObject("Player", new Vector3f(0, 0, 0));
        gameObject.getTransform().setPosition(gameObject.getTransform().getPosition().add(new Vector3f(0,0,1)));
        gameObject.addComponent(spriteRenderer);
        gameObject.addComponent(new PlayerMovement());
        gameObject.addComponent(new PlayerCameraFollow());

        addGameObject(gameObject);
    }
}
