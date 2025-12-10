package com.pocket.rpg.prefabs;

import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;

/**
 * Example prefab definitions for the game.
 * 
 * This class shows how to register prefabs with the PrefabRegistry.
 * In a real game, you'd have prefabs for Player, NPCs, items, etc.
 * 
 * Usage:
 * <pre>
 * PrefabRegistry registry = new PrefabRegistry(assetManager);
 * GamePrefabs.registerAll(registry);
 * 
 * // Then instantiate:
 * GameObject player = registry.instantiate("Player", new Vector3f(5, 5, 0));
 * </pre>
 */
public class GamePrefabs {
    
    /**
     * Registers all game prefabs with the registry.
     */
    public static void registerAll(PrefabRegistry registry) {
        registerPlayer(registry);
        registerNPC(registry);
        registerChest(registry);
        registerSign(registry);
    }

    private static void registerPlayer(PrefabRegistry registry) {
        registry.register("Player", (assets, position) -> {
            GameObject go = new GameObject("Player");
            go.getTransform().setPosition(position);
            
            // Load player sprite
            Sprite sprite = loadSprite(assets, "gameData/assets/sprites/player.png", 16, 16);
            sprite.setPivotBottomCenter(); // Feet at position
            
            SpriteRenderer renderer = new SpriteRenderer(sprite);
            renderer.setZIndex(10); // Above ground tiles
            go.addComponent(renderer);
            
            // Add player-specific components here:
            // go.addComponent(new PlayerController());
            // go.addComponent(new GridMovement());
            
            return go;
        }, new PrefabRegistry.PrefabMetadata("Player", "Characters", 
            "gameData/assets/icons/player.png", "The player character"));
    }

    private static void registerNPC(PrefabRegistry registry) {
        registry.register("NPC", (assets, position) -> {
            GameObject go = new GameObject("NPC");
            go.getTransform().setPosition(position);
            
            Sprite sprite = loadSprite(assets, "gameData/assets/sprites/npc.png", 16, 16);
            sprite.setPivotBottomCenter();
            
            SpriteRenderer renderer = new SpriteRenderer(sprite);
            renderer.setZIndex(10);
            go.addComponent(renderer);
            
            // NPC components:
            // go.addComponent(new NPCController());
            // go.addComponent(new DialogueTrigger());
            
            return go;
        }, new PrefabRegistry.PrefabMetadata("NPC", "Characters"));
    }

    private static void registerChest(PrefabRegistry registry) {
        registry.register("Chest", (assets, position) -> {
            GameObject go = new GameObject("Chest");
            go.getTransform().setPosition(position);
            
            Sprite sprite = loadSprite(assets, "gameData/assets/sprites/objects.png", 16, 16);
            // Assuming chest is at specific UV coords in the sheet
            // sprite.setUVsFromPixels(0, 32, 16, 16);
            
            SpriteRenderer renderer = new SpriteRenderer(sprite);
            renderer.setZIndex(5);
            go.addComponent(renderer);
            
            // Chest components:
            // go.addComponent(new Interactable());
            // go.addComponent(new LootContainer());
            
            return go;
        }, new PrefabRegistry.PrefabMetadata("Chest", "Objects"));
    }

    private static void registerSign(PrefabRegistry registry) {
        registry.register("Sign", (assets, position) -> {
            GameObject go = new GameObject("Sign");
            go.getTransform().setPosition(position);
            
            Sprite sprite = loadSprite(assets, "gameData/assets/sprites/objects.png", 16, 16);
            
            SpriteRenderer renderer = new SpriteRenderer(sprite);
            renderer.setZIndex(5);
            go.addComponent(renderer);
            
            return go;
        }, new PrefabRegistry.PrefabMetadata("Sign", "Objects"));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Helper to load a sprite with specified dimensions.
     */
    private static Sprite loadSprite(AssetManager assets, String path, float width, float height) {
        // Try to load via AssetManager, fallback to direct creation
        try {
            Sprite sprite = assets.<Sprite>load(path, "sprite").get();
            sprite.setWidth(width);
            sprite.setHeight(height);
            return sprite;
//            return assets.loadSprite(path, width, height);
        } catch (Exception e) {
            // AssetManager might not have this method, create directly
            return new Sprite(assets.<Texture>load(path, "texture").get(), width, height);
        }
    }
}
