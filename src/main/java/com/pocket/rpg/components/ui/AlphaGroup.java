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

    private transient float currentAlpha = -1.0f;

    @Override
    protected void onStart() {
        applyAlphaInternal();
    }

    @Override
    public void update(float deltaTime) {
        if (currentAlpha != alpha) {
            applyAlphaInternal();
            currentAlpha = alpha;
        }
    }

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        if (currentAlpha != alpha) {
            applyAlphaInternal();
            currentAlpha = alpha;
        }
    }

    /**
     * Called by the inspector to apply alpha changes immediately.
     */
    public void applyAlphaInEditor() {
        applyAlphaInternal();
        currentAlpha = alpha;
    }

    private void applyAlphaInternal() {
        // Clamp alpha to valid range
        alpha = Math.max(0f, Math.min(1f, alpha));

        if (gameObject == null) {
            return;
        }

        applyAlphaRecursive(gameObject);
    }

    private void applyAlphaRecursive(GameObject go) {
        if (!go.hasChildren()) {
            return;
        }
        for (GameObject child : go.getChildren()) {
            applyAlphaToComponents(child);
            applyAlphaRecursive(child);
        }
    }

    private void applyAlphaToComponents(GameObject target) {
        for (var comp : target.getAllComponents()) {
            if (comp instanceof UIVisual visual) {
                visual.setAlpha(alpha);
            }
        }
    }
}
