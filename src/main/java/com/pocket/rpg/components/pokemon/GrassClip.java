package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.collision.CollisionSystem;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.MovementModifier;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.animations.AnimationComponent;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import lombok.Getter;
import lombok.Setter;

@ComponentMeta(category = "Player/Rendering")
public class GrassClip extends Component {

    @Getter @Setter
    private float clipAmount = 0.35f; // ~35% of a 1-unit sprite, tune to taste

    @ComponentReference(source = Source.KEY)
    private SpriteRenderer tallGrassOverPlayerSR;

    @ComponentReference(source = Source.KEY)
    private AnimationComponent tallGrassAnimation;

    @ComponentReference(source = Source.SELF)
    private GridMovement gridMovement;
    @ComponentReference(source = Source.SELF)
    private SpriteRenderer spriteRenderer;

    @Override
    protected void onStart() {
        gridMovement.addFinishedListener(this::handleMovementFinished);
        gridMovement.addStartedListener(this::handleMovementStarted);
        hideTallGrassEffect();
    }

    private void hideTallGrassEffect() {
        spriteRenderer.setClipBottom(0f);
        tallGrassOverPlayerSR.setEnabled(false);
        tallGrassAnimation.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gridMovement.removeFinishedListener(this::handleMovementFinished);
        gridMovement.removeStartedListener(this::handleMovementStarted);
    }

    private void handleMovementStarted(MovementModifier movementModifier) {
        hideTallGrassEffect();
    }

    private void handleMovementFinished(MovementModifier movementModifier) {
        CollisionSystem cs = getCollisionSystem();
        if (cs == null) {
            spriteRenderer.setClipBottom(0f);
            return;
        }

        CollisionType type = cs.getCollisionAt(
            gridMovement.getGridX(),
            gridMovement.getGridY(),
            gridMovement.getZLevel()
        );

        boolean isOverTallGrass = type == CollisionType.TALL_GRASS;
        if (isOverTallGrass) {
            spriteRenderer.setClipBottom(clipAmount);
            tallGrassOverPlayerSR.setEnabled(true);
            tallGrassAnimation.play();
        } else {
            hideTallGrassEffect();
        }
    }

    private CollisionSystem getCollisionSystem() {
        if (gameObject == null || gameObject.getScene() == null) return null;
        return gameObject.getScene().getCollisionSystem();
    }
}
