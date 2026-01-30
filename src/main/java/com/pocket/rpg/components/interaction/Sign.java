package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Simple interactable sign/panel that logs a message to the console.
 * <p>
 * Attach this to any GameObject to make it interactable. When the player
 * presses the interact key nearby, the configured message is printed
 * to the console.
 */
@ComponentMeta(category = "Interaction")
public class Sign extends Component implements Interactable {

    /**
     * The message displayed when the player interacts with this sign.
     */
    @Getter
    @Setter
    private String message = "Hello, world!";

    /**
     * The prompt verb shown to the player (e.g. "Read", "Examine").
     */
    @Getter
    @Setter
    private String prompt = "Read";

    /**
     * Elevation level for tile registration (default 0 = ground level).
     */
    @Getter
    @Setter
    private int elevation = 0;

    // Runtime state - not serialized
    private transient TileEntityMap tileEntityMap;
    private transient TileCoord registeredTile;

    @Override
    protected void onStart() {
        tileEntityMap = getTileEntityMap();
        registerWithMap();
    }

    @Override
    protected void onDestroy() {
        unregisterFromMap();
    }

    // ========================================================================
    // INTERACTABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public void interact(GameObject player) {
        System.out.println("[Sign] " + gameObject.getName() + ": " + message);
    }

    @Override
    public String getInteractionPrompt() {
        return prompt;
    }

    @Override
    public int getInteractionPriority() {
        return 0;
    }

    // ========================================================================
    // TILE ENTITY MAP REGISTRATION
    // ========================================================================

    private TileCoord getTileCoord() {
        Vector3f pos = getTransform().getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        return new TileCoord(x, y, elevation);
    }

    private void registerWithMap() {
        if (tileEntityMap == null) return;

        TileCoord tile = getTileCoord();
        tileEntityMap.register(this, tile);
        registeredTile = tile;
    }

    private void unregisterFromMap() {
        if (tileEntityMap == null || registeredTile == null) return;

        tileEntityMap.unregister(this, registeredTile);
        registeredTile = null;
    }

    private TileEntityMap getTileEntityMap() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getCollisionSystem().getTileEntityMap();
    }
}
