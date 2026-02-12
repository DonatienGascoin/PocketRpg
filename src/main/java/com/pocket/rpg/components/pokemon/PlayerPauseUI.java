package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.animation.tween.Ease;
import com.pocket.rpg.animation.tween.TweenManager;
import com.pocket.rpg.animation.tween.Tweens;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.player.InputMode;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;

/**
 * Updates UI elements based on player state.
 * <p>
 * Uses @ComponentReference to resolve UI components from ComponentKeyRegistry keys
 * configured in the editor, eliminating magic strings.
 * Reads input from {@link PlayerInput} — only responds in OVERWORLD mode.
 */
@ComponentMeta(category = "Player")
public class PlayerPauseUI extends Component {

    private static final Logger LOG = Log.getLogger(PlayerPauseUI.class);

    private float toggleDuration = 0.5f;

    @ComponentReference(source = Source.KEY)
    private UIImage pauseMenuBgImage;

    @ComponentReference(source = Source.SELF)
    private PlayerInput playerInput;

    private transient UITransform pauseMenuTransform;
    private transient float hiddenOffsetX;
    private transient float visibleOffsetX;

    private transient boolean isPaused = false;

    public PlayerPauseUI() {
    }

    @Override
    protected void onStart() {
        pauseMenuTransform = pauseMenuBgImage.getUITransform();
        visibleOffsetX = pauseMenuTransform.getOffset().x;
        hiddenOffsetX = visibleOffsetX + pauseMenuTransform.getWidth();

        // Start hidden off-screen to the right
        pauseMenuTransform.setOffset(hiddenOffsetX, pauseMenuTransform.getOffset().y);

        if (playerInput != null) {
            playerInput.onMenu(InputMode.OVERWORLD, this::togglePauseMenu);
        }
    }

    private void togglePauseMenu() {
        isPaused = !isPaused;
        LOG.info("Toggling pause menu UI to " + (isPaused ? "visible" : "hidden"));

        TweenManager.kill(pauseMenuTransform, false);

        if (isPaused) {
            Tweens.offsetX(pauseMenuTransform, visibleOffsetX, toggleDuration)
                    .setEase(Ease.OUT_SINE);
        } else {
            Tweens.offsetX(pauseMenuTransform, hiddenOffsetX, toggleDuration)
                    .setEase(Ease.IN_QUAD);
        }
    }
}
