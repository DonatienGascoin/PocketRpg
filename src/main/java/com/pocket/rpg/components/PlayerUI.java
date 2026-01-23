package com.pocket.rpg.components;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MovementModifier;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.editor.EditorApplication;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.ui.UIManager;
import lombok.Getter;

/**
 * Handles player input and translates it to GridMovement commands.
 * <p>
 * Reads keyboard input and calls GridMovement.move() in the appropriate direction.
 * Also handles debug output for collision testing.
 */
@ComponentMeta(category = "Player")
public class PlayerUI extends Component {

    private static final Logger LOG = Log.getLogger(PlayerUI.class);


    @ComponentRef
    private GridMovement movement;

    private UIText elevationText;


    public PlayerUI() {

    }

    @Override
    protected void onStart() {
        elevationText = UIManager.getText("ElevationText");
        if (elevationText == null) {
            LOG.error("ElevationText UI text not found!");
        }
    }

    @Override
    public void update(float deltaTime) {
        elevationText.setText(String.valueOf(movement.getZLevel()));
    }
}