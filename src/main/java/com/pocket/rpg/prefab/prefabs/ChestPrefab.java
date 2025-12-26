package com.pocket.rpg.prefab.prefabs;

import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentData;

import java.util.ArrayList;
import java.util.List;

/**
 * Prefab for treasure chests.
 * <p>
 * Components:
 * - SpriteRenderer: Displays the chest sprite
 * <p>
 * To add loot/locked behavior, create a ChestBehavior component
 * and add it to this prefab's component list.
 */
public class ChestPrefab implements Prefab {

    private static final String SPRITE_RENDERER_TYPE = "com.pocket.rpg.components.SpriteRenderer";

    private SpriteSheet spriteSheet;
    private List<ComponentData> components;

    @Override
    public String getId() {
        return "chest";
    }

    @Override
    public String getDisplayName() {
        return "Treasure Chest";
    }

    @Override
    public String getCategory() {
        return "Interactables";
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
        ensureSpriteSheet();
        return spriteSheet != null ? spriteSheet.getSprite(0) : null;
    }

    private List<ComponentData> buildComponents() {
        List<ComponentData> result = new ArrayList<>();

        // SpriteRenderer component
        ComponentData spriteRenderer = new ComponentData(SPRITE_RENDERER_TYPE);
        spriteRenderer.getFields().put("spritePath", "spritesheets/Chest.spritesheet");
        spriteRenderer.getFields().put("spriteIndex", 0);
        spriteRenderer.getFields().put("zIndex", 10);
        spriteRenderer.getFields().put("originBottomCenter", true);
        result.add(spriteRenderer);

        return result;
    }

    private void ensureSpriteSheet() {
        if (spriteSheet == null) {
            try {
                spriteSheet = Assets.load("spritesheets/Chest.spritesheet");
            } catch (Exception e) {
                System.err.println("Failed to load chest sprite: " + e.getMessage());
            }
        }
    }
}
