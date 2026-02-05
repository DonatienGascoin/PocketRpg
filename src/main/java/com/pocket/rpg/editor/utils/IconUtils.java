package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.editor.core.MaterialIcons;
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
        } else if (entity.hasComponent(UIHorizontalLayoutGroup.class)) {
            return getUIHorizontalLayoutIcon();
        } else if (entity.hasComponent(UIVerticalLayoutGroup.class)) {
            return getUIVerticalLayoutIcon();
        } else if (entity.hasComponent(UIGridLayoutGroup.class)) {
            return getUIGridLayoutIcon();
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
        return MaterialIcons.Map;
    }

    public static String getCameraIcon() {
        return MaterialIcons.Videocam;
    }

    public static String getLayersIcon() {
        return MaterialIcons.Layers;
    }

    public static String getCollisionsIcon() {
        return MaterialIcons.GridOn;
    }

    public static String getUICanvasIcon() {
        return MaterialIcons.DesktopWindows;
    }

    public static String getUIButtonIcon() {
        return MaterialIcons.TouchApp;
    }

    public static String getUITextIcon() {
        return MaterialIcons.TextFields;
    }

    public static String getUIImageIcon() {
        return MaterialIcons.Image;
    }

    public static String getUIPanelIcon() {
        return MaterialIcons.CheckBoxOutlineBlank;
    }

    public static String getUITransformIcon() {
        return MaterialIcons.OpenInFull;
    }

    public static String getUIHorizontalLayoutIcon() {
        return MaterialIcons.ViewWeek;
    }

    public static String getUIVerticalLayoutIcon() {
        return MaterialIcons.ViewStream;
    }

    public static String getUIGridLayoutIcon() {
        return MaterialIcons.GridView;
    }

    public static String getMultipleEntitiesIcon() {
        return MaterialIcons.SelectAll;
    }

    public static String getScratchEntityIcon() {
        return MaterialIcons.ViewInAr;
    }

    public static String getPrefabEntityIcon() {
        return MaterialIcons.Inventory2;
    }

    public static String getUnknownEntityIcon() {
        return MaterialIcons.Warning;
    }
}
