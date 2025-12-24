package com.pocket.rpg.prefab.prefabs;

import com.pocket.rpg.components.GridMovement;
import com.pocket.rpg.components.PlayerCameraFollow;
import com.pocket.rpg.components.PlayerMovement;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PropertyDefinition;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

public class PlayerPrefab implements Prefab {

    private final SpriteSheet playerSheet;
    private final Sprite previewSprite;

    public PlayerPrefab() {
        playerSheet = Assets.load("spritesheets/player.spritesheet");
        previewSprite = Sprite.copy(playerSheet.getSprite(0));
        previewSprite.setPivotCenter();
    }

    @Override
    public String getId() {
        return "player";
    }

    @Override
    public String getDisplayName() {
        return "Player";
    }

    @Override
    public String getCategory() {
        return "Characters";
    }

    @Override
    public List<PropertyDefinition> getEditableProperties() {
        return List.of();
    }

    @Override
    public Sprite getPreviewSprite() {
        return previewSprite;
    }

    @Override
    public GameObject instantiate(Vector3f position, Map<String, Object> overrides) {

        // 32×32 pixel sprites with PPU=16 → each frame is 2×2 world units
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(playerSheet.getSprite(0));
        spriteRenderer.setOriginBottomCenter();
        // Player at world origin (Z not used for sorting anymore)
        GameObject player = new GameObject("Player", new Vector3f(5, 5, 0));

        player.addComponent(spriteRenderer);
        player.addComponent(new PlayerCameraFollow());

        GridMovement movement = player.addComponent(new GridMovement(1));
        movement.setGridPosition(5, 5);
        movement.setBaseSpeed(4f); // 4 tiles/second
        player.addComponent(new PlayerMovement());

        // Apply overrides to your custom components here, ex:
        // String lootTable = (String) overrides.getOrDefault("lootTable", "common_loot");
        // boolean locked = (Boolean) overrides.getOrDefault("locked", false);

        return player;
    }
}