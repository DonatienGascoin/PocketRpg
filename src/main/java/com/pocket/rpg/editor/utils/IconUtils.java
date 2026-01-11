package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;

public class IconUtils {

    public static String getIconForEntity(EditorGameObject entity) {
        // Check for UI components first
        if (entity.hasComponent(UICanvas.class)) {
            return getUICanvasIcon();
        } else if (entity.hasComponent(UIButton.class)) {
            return getUIButtonIcon();
        } else if (entity.hasComponent(UIText.class)) {
            return getUITextIcon();
        } else if (entity.hasComponent(UIImage.class)) {
            return getUIImageIcon();
        } else if (entity.hasComponent(UIPanel.class)) {
            return getUIPanelIcon();
        } else if (entity.hasComponent(UITransform.class)) {
            return getUITransformIcon();
        }

        // Default icons
        if (entity.isScratchEntity()) {
            return getScratchEntityIcon();
        } else if (entity.isPrefabValid()) {
            return getPrefabEntityIcon();
        } else {
            return getUnknownEntityIcon();
        }
    }

    public static String getSceneIcon() {
        return FontAwesomeIcons.Map;
    }

    public static String getCameraIcon() {
        return FontAwesomeIcons.Camera;
    }

    public static String getLayersIcon() {
        return FontAwesomeIcons.LayerGroup;
    }

    public static String getCollisionsIcon() {
        return FontAwesomeIcons.BorderAll;
    }

    public static String getUICanvasIcon() {
        return FontAwesomeIcons.Desktop;
    }

    public static String getUIButtonIcon() {
        return FontAwesomeIcons.HandPointer;
    }

    public static String getUITextIcon() {
        return FontAwesomeIcons.Font;
    }

    public static String getUIImageIcon() {
        return FontAwesomeIcons.Image;
    }

    public static String getUIPanelIcon() {
        return FontAwesomeIcons.Square;
    }

    public static String getUITransformIcon() {
        return FontAwesomeIcons.WindowMaximize;
    }

    public static String getMultipleEntitiesIcon() {
        return FontAwesomeIcons.ObjectGroup;
    }

    public static String getScratchEntityIcon() {
        return FontAwesomeIcons.Cube;
    }

    public static String getPrefabEntityIcon() {
        return FontAwesomeIcons.Cubes;
    }

    public static String getUnknownEntityIcon() {
        return FontAwesomeIcons.ExclamationTriangle;
    }
}
