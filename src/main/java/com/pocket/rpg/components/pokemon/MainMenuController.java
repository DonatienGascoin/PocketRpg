package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.animation.tween.Ease;
import com.pocket.rpg.animation.tween.Tweens;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.player.InputMode;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UITransform;

@RequiredComponent(PlayerInput.class)
public class MainMenuController extends Component {

    @ComponentReference(source = Source.KEY)
    private UITransform menuTransform;

    @ComponentReference(source = Source.SELF)
    private PlayerInput playerInput;

    private float menuTweenDuration = .5f;
    private float waitDurationBeforeMenu = 1f;

    private transient float currentWait;
    private transient boolean isMenuActive = false;
    private transient float visibleOffsetX;

    @Override
    protected void onStart() {
        playerInput.setMode(InputMode.MENU);
        visibleOffsetX = menuTransform.getWidth();
        menuTransform.setOffset(visibleOffsetX, menuTransform.getOffset().y);
    }

    @Override
    public void update(float deltaTime) {
        if (isMenuActive) {
            handleMenuInput();
        } else {
            waitAndActivateMenu(deltaTime);
        }
    }

    private void waitAndActivateMenu(float deltaTime) {
        currentWait += deltaTime;
        if (currentWait >= waitDurationBeforeMenu) {
            Tweens.offsetX(menuTransform, 0, menuTweenDuration)
                .setEase(Ease.IN_QUAD);
            isMenuActive = true;
        }

        if (playerInput.isMenuPressed()) {
            // Skip wait and show menu immediately
            Tweens.offsetX(menuTransform, 0, menuTweenDuration)
                .setEase(Ease.IN_QUAD);
            isMenuActive = true;
        }
    }

    private void handleMenuInput() {

    }
}
