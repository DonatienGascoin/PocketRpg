package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;

/**
 * Updates UI elements based on player state.
 * <p>
 * Uses @ComponentReference to resolve UI components from ComponentKeyRegistry keys
 * configured in the editor, eliminating magic strings.
 */
@ComponentMeta(category = "Player")
public class PlayerUI extends Component {

    private static final Logger LOG = Log.getLogger(PlayerUI.class);

    @ComponentReference(source = Source.SELF)
    private GridMovement movement;

    /** Serialized as a JSON string key, resolved at runtime via ComponentKeyRegistry */
    @ComponentReference(source = Source.KEY)
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
