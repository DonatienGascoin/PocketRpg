package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.IGameObject;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.scene.EditorGameObject;
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

        IGameObject owner = getOwner();
        if (owner == null) {
            return;
        }

        if (owner.isRuntime() && gameObject != null) {
            applyAlphaToGameObject(gameObject);
        } else if (owner instanceof EditorGameObject editorGO) {
            applyAlphaToEditorGameObject(editorGO);
        }
    }

    private void applyAlphaToGameObject(GameObject gameObject) {
        if (!gameObject.hasChildren()) {
            return;
        }
        for (GameObject child : gameObject.getChildren()) {
            applyAlphaToComponents(child);
            applyAlphaToGameObject(child);
        }
    }

    private void applyAlphaToEditorGameObject(EditorGameObject editorGO) {
        if (!editorGO.hasChildren()) {
            return;
        }
        for (EditorGameObject child : editorGO.getChildren()) {
            applyAlphaToComponents(child);
            applyAlphaToEditorGameObject(child);
        }
    }

    private void applyAlphaToComponents(IGameObject target) {
        for (var comp : target.getAllComponents()) {
            if (comp instanceof UIImage img) {
                img.setAlpha(alpha);
            } else if (comp instanceof UIPanel panel) {
                panel.setAlpha(alpha);
            } else if (comp instanceof UIButton btn) {
                btn.setAlpha(alpha);
            } else if(comp instanceof UIText text) {
                text.setAlpha(alpha);
            }
        }
    }
}
