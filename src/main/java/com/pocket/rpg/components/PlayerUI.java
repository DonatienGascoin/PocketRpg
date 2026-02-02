package com.pocket.rpg.components;

import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;

/**
 * Updates UI elements based on player state.
 * <p>
 * Uses @UiKeyReference to resolve UI components from UIManager keys
 * configured in the editor, eliminating magic strings.
 */
@ComponentMeta(category = "Player")
public class PlayerUI extends Component {

    private static final Logger LOG = Log.getLogger(PlayerUI.class);

    @ComponentRef
    private GridMovement movement;

    /** Serialized as a JSON string key, resolved at runtime via UIManager */
    @UiKeyReference
    private UIText elevationText;

    public PlayerUI() {
    }

    @Override
    public void update(float deltaTime) {
        if (elevationText != null) {
            elevationText.setText(String.valueOf(movement.getZLevel()));
        }
    }
}
