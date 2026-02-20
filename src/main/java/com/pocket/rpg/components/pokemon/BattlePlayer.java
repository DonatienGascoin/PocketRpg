package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.save.PlayerData;
import com.pocket.rpg.scenes.transitions.SceneTransition;

/**
 * Minimal battle-scene player component.
 * <p>
 * Receives battle data from {@link PlayerData} on start. When the player presses
 * INTERACT, the battle ends: {@code returningFromBattle} is set to true and a
 * transition back to the last overworld scene is triggered.
 * <p>
 * This is a stub for the future full battle system. It does not implement actual
 * battle mechanics — it only demonstrates the scene-transition round-trip.
 */
@ComponentMeta(category = "Player")
public class BattlePlayer extends Component {

    private static final Logger LOG = Log.getLogger(BattlePlayer.class);

    private boolean battleEnded = false;

    @Override
    protected void onStart() {
        PlayerData data = PlayerData.load();
        LOG.info("Battle started — return scene: %s", data.lastOverworldScene);
    }

    @Override
    public void update(float deltaTime) {
        if (battleEnded) return;

        if (Input.isActionPressed(InputAction.INTERACT)) {
            endBattle();
        }
    }

    private void endBattle() {
        battleEnded = true;

        PlayerData data = PlayerData.load();
        data.returningFromBattle = true;
        data.save();

        String returnScene = data.lastOverworldScene;
        if (returnScene == null || returnScene.isEmpty()) {
            LOG.error("No overworld scene to return to — lastOverworldScene is empty");
            return;
        }

        LOG.info("Battle ended — returning to %s", returnScene);
        SceneTransition.loadScene(returnScene);
    }
}
