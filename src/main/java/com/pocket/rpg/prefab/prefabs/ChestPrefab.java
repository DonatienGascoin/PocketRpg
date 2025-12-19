package com.pocket.rpg.prefab.prefabs;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PropertyDefinition;
import com.pocket.rpg.prefab.PropertyType;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.resources.Assets;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

public class ChestPrefab implements Prefab {

    private SpriteSheet spriteSheet;

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
    public List<PropertyDefinition> getEditableProperties() {
        return List.of(
                new PropertyDefinition("lootTable", PropertyType.STRING, "common_loot",
                        "ID of loot table to use"),
                new PropertyDefinition("locked", PropertyType.BOOLEAN, false,
                        "Whether chest requires a key")
        );
    }

    @Override
    public Sprite getPreviewSprite() {
        ensureSpriteSheet();
        return spriteSheet != null ? spriteSheet.getSprite(0) : null;
    }

    @Override
    public GameObject instantiate(Vector3f position, Map<String, Object> overrides) {
        ensureSpriteSheet();

        GameObject chest = new GameObject("Chest", position);

        if (spriteSheet != null) {
            SpriteRenderer renderer = new SpriteRenderer(spriteSheet.getSprite(0));
            renderer.setOriginBottomCenter();
            renderer.setZIndex(10);
            chest.addComponent(renderer);
        }

        // Apply overrides to your custom components here
        // String lootTable = (String) overrides.getOrDefault("lootTable", "common_loot");
        // boolean locked = (Boolean) overrides.getOrDefault("locked", false);

        return chest;
    }

    private void ensureSpriteSheet() {
        if (spriteSheet == null) {
            try {
                Sprite sprite = Assets.load("sprites/objects/chest.png");
                spriteSheet = new SpriteSheet(sprite.getTexture(), 16, 16);
            } catch (Exception e) {
                System.err.println("Failed to load chest sprite: " + e.getMessage());
            }
        }
    }
}