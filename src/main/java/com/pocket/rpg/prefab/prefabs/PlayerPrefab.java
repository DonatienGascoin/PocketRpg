package com.pocket.rpg.prefab.prefabs;

import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentData;

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

    private static final String SPRITE_RENDERER_TYPE = "com.pocket.rpg.components.SpriteRenderer";
    private static final String PLAYER_CAMERA_FOLLOW_TYPE = "com.pocket.rpg.components.PlayerCameraFollow";
    private static final String GRID_MOVEMENT_TYPE = "com.pocket.rpg.components.GridMovement";
    private static final String PLAYER_MOVEMENT_TYPE = "com.pocket.rpg.components.PlayerMovement";

    private SpriteSheet playerSheet;
    private Sprite previewSprite;
    private List<ComponentData> components;

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
    public List<ComponentData> getComponents() {
        if (components == null) {
            components = buildComponents();
        }
        return components;
    }

    @Override
    public Sprite getPreviewSprite() {
        return previewSprite;
    }

    private List<ComponentData> buildComponents() {
        List<ComponentData> result = new ArrayList<>();

        // SpriteRenderer component
        ComponentData spriteRenderer = new ComponentData(SPRITE_RENDERER_TYPE);
        spriteRenderer.getFields().put("sprite", playerSheet.getSprite(0));
        spriteRenderer.getFields().put("originBottomCenter", true);
        result.add(spriteRenderer);

        // PlayerCameraFollow component
        ComponentData cameraFollow = new ComponentData(PLAYER_CAMERA_FOLLOW_TYPE);
        result.add(cameraFollow);

        // GridMovement component
        ComponentData gridMovement = new ComponentData(GRID_MOVEMENT_TYPE);
        gridMovement.getFields().put("gridSize", 1);
        gridMovement.getFields().put("baseSpeed", 4.0f);
        result.add(gridMovement);

        // PlayerMovement component
        ComponentData playerMovement = new ComponentData(PLAYER_MOVEMENT_TYPE);
        result.add(playerMovement);

        return result;
    }
}
