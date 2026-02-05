package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import lombok.Getter;

@ComponentMeta(category = "UI")
public class AlphaGroup extends Component {
    @Getter
    private float alpha = 1.0f;

    private transient float currentAlpha = 0.0f;

    @Override
    protected void onStart() {
        applyAlpha();
    }

    @Override
    public void update(float deltaTime) {
        if (currentAlpha != alpha) {
            applyAlpha();
            currentAlpha = alpha;
        }
    }

    private void applyAlpha() {
        applyAlphaToGameObject(getGameObject());
    }

    private void applyAlphaToGameObject(GameObject gameObject) {
        if (!gameObject.hasChildren()) {
            return;
        }
        for (GameObject child : gameObject.getChildren()) {
            for(var comp: child.getComponents()) {
                if (comp instanceof UIImage) {
                    ((UIImage) comp).setAlpha(alpha);
                } else if (comp instanceof UIPanel) {
                    ((UIPanel) comp).setAlpha(alpha);
                } else if (comp instanceof UIButton) {
                    ((UIButton) comp).setAlpha(alpha);
                }
            }
            // Recursively apply to children of this game object
            applyAlphaToGameObject(child);
        }
    }

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        // Apply alpha in editor when value changes
        if (currentAlpha != alpha) {
            applyAlpha();
            currentAlpha = alpha;
        }
    }
}
