package com.pocket.rpg.scenes;

import com.pocket.rpg.components.PlayerMovement;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.AssetManager;
import org.joml.Vector3f;

public class DemoScene extends Scene {
    public DemoScene() {
        super("Demo");
    }

    @Override
    public void onLoad() {
        createPlayer();
    }

    private void createPlayer() {
        var resource = AssetManager.getInstance().<Sprite>load("gameData/assets/sprites/Char1_32x32.png");
        var playerTex = resource.get();

        System.out.println("Player:" + playerTex);

        SpriteSheet playerSheet = new SpriteSheet(playerTex.getTexture(), 32, 32, 0, 0, 0, 16);
        var sprites = playerSheet.generateAllSprites();

        SpriteRenderer spriteRenderer = new SpriteRenderer(sprites.get(0));

        GameObject gameObject = new GameObject("Player", new Vector3f(400, 50, 0));
        gameObject.addComponent(spriteRenderer);
        gameObject.addComponent(new PlayerMovement());

        addGameObject(gameObject);
    }
}
