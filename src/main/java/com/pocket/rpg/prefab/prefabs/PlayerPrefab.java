package com.pocket.rpg.prefab.prefabs;

import com.pocket.rpg.components.*;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteSheet;
import com.pocket.rpg.resources.Assets;

import java.util.ArrayList;
import java.util.List;

/**
 * Prefab for the player character.
 * <p>
 * Components:
 * - SpriteRenderer: Player sprite display
 * - PlayerCameraFollow: Camera follows this entity
 * - GridMovement: Grid-based movement system
 * - PlayerMovement: Input handling for player control
 */
public class PlayerPrefab implements Prefab {

    private SpriteSheet playerSheet;
    private Sprite previewSprite;
    private List<Component> components;

    public PlayerPrefab() {
        try {
            playerSheet = Assets.load("spritesheets/player.spritesheet");
            previewSprite = Sprite.copy(playerSheet.getSprite(0));
            previewSprite.setPivotCenter();
        } catch (Exception e) {
            System.err.println("Failed to load player spritesheet: " + e.getMessage());
        }
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
    public List<Component> getComponents() {
        if (components == null) {
            components = buildComponents();
        }
        return components;
    }

    @Override
    public Sprite getPreviewSprite() {
        return previewSprite;
    }

    private List<Component> buildComponents() {
        List<Component> result = new ArrayList<>();

        Transform transform = new Transform();
        result.add(transform);

        // SpriteRenderer component
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        if (playerSheet != null) {
            spriteRenderer.setSprite(playerSheet.getSprite(0));
        }
        result.add(spriteRenderer);

        // PlayerCameraFollow component
        PlayerCameraFollow cameraFollow = new PlayerCameraFollow();
        result.add(cameraFollow);

        // GridMovement component
        GridMovement gridMovement = new GridMovement();
        gridMovement.setTileSize(1);
        gridMovement.setBaseSpeed(4.0f);
        result.add(gridMovement);

        // PlayerMovement component
        PlayerMovement playerMovement = new PlayerMovement();
        result.add(playerMovement);

        return result;
    }
}
