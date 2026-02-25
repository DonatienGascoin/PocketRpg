package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.components.interaction.InteractableComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.shop.ShopInventory;
import com.pocket.rpg.shop.ShopRegistry;
import com.pocket.rpg.shop.ShopService;
import lombok.Getter;
import lombok.Setter;

/**
 * NPC shopkeeper component. Opens the shop when interacted with.
 * <p>
 * Extends {@link InteractableComponent} to get TriggerZone registration,
 * directional interaction, and gizmo drawing. The {@link #shopId} field
 * references a shop definition in the {@link ShopRegistry}.
 */
@ComponentMeta(category = "Interaction")
public class ShopComponent extends InteractableComponent {

    private static final Logger LOG = Log.getLogger(ShopComponent.class);
    private static final String SHOP_REGISTRY_PATH = "data/shops/shops.shops.json";

    @Getter @Setter
    @Tooltip("Shop ID from the ShopRegistry (e.g., \"viridian_pokemart\")")
    private String shopId;

    public ShopComponent() {
        gizmoShape = GizmoShape.SQUARE;
        gizmoColor = GizmoColors.fromRGBA(0.2f, 0.6f, 1.0f, 0.9f); // Blue
    }

    @Override
    public void interact(GameObject player) {
        if (shopId == null || shopId.isEmpty()) {
            LOG.warn("ShopComponent on {} has no shopId set", gameObject.getName());
            return;
        }

        ShopRegistry registry = Assets.load(SHOP_REGISTRY_PATH, ShopRegistry.class);
        if (registry == null) {
            LOG.warn("Failed to load ShopRegistry from {}", SHOP_REGISTRY_PATH);
            return;
        }

        ShopInventory shop = registry.getShop(shopId);
        if (shop == null) {
            LOG.warn("Unknown shopId: {}", shopId);
            return;
        }

        PlayerInventoryComponent inventory = player.getComponent(PlayerInventoryComponent.class);
        if (inventory == null) {
            LOG.warn("Player has no PlayerInventoryComponent");
            return;
        }

        // Log shop info — UI system is out of scope for this plan
        LOG.info("=== SHOP: {} ===", shop.getShopName());
        LOG.info("Player money: {}", inventory.getMoney());
        ShopService.logShopContents(shop, shopId);
    }

    @Override
    public String getInteractionPrompt() {
        return "Shop";
    }
}
