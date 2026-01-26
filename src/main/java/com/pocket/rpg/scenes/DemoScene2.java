package com.pocket.rpg.scenes;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.resources.Assets;
import org.joml.Vector3f;

public class DemoScene2 extends Scene {
    public DemoScene2() {
        super("Demo2");
    }

    @Override
    public void onLoad() {
        createPlayer();
    }

    private void createPlayer() {
        var playerSprite = Assets.<Sprite>load("gameData/assets/sprites/Char1_32x32.png");

        System.out.println("Player:" + playerSprite);

        SpriteGrid playerGrid = SpriteGrid.create(playerSprite.getTexture(), 32, 32);
        var sprites = playerGrid.getAllSprites();

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(sprites.get(5));

        GameObject gameObject = new GameObject("Player", new Vector3f(200, 200, 0));
        gameObject.addComponent(spriteRenderer);
//        gameObject.addComponent(new PlayerMovement());

        addGameObject(gameObject);
    }
}
