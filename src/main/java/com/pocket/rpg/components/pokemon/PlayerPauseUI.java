package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.animation.tween.Ease;
import com.pocket.rpg.animation.tween.TweenManager;
import com.pocket.rpg.animation.tween.Tweens;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.UiKeyReference;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;

/**
 * Updates UI elements based on player state.
 * <p>
 * Uses @UiKeyReference to resolve UI components from UIManager keys
 * configured in the editor, eliminating magic strings.
 */
@ComponentMeta(category = "Player")
public class PlayerPauseUI extends Component {

    private static final Logger LOG = Log.getLogger(PlayerPauseUI.class);

    private float toggleDuration = 0.5f;

    @UiKeyReference
    private UIImage pauseMenuBgImage;

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
    }

    @Override
    public void update(float deltaTime) {
        if (Input.isActionPressed(InputAction.MENU)) {
            isPaused = !isPaused;
            LOG.info("Toggling pause menu UI to " + (isPaused ? "visible" : "hidden"));

            // Kill any in-progress animation

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
}
