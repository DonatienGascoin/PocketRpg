package com.pocket.rpg.editor.scene;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.serialization.ComponentData;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Factory for creating UI entity templates in the editor.
 * Creates EditorEntity instances with pre-configured UI components.
 */
public class UIEntityFactory {

    private static final String UI_CANVAS = "com.pocket.rpg.components.ui.UICanvas";
    private static final String UI_TRANSFORM = "com.pocket.rpg.components.ui.UITransform";
    private static final String UI_PANEL = "com.pocket.rpg.components.ui.UIPanel";
    private static final String UI_IMAGE = "com.pocket.rpg.components.ui.UIImage";
    private static final String UI_BUTTON = "com.pocket.rpg.components.ui.UIButton";
    private static final String UI_TEXT = "com.pocket.rpg.components.ui.UIText";

    private final GameConfig gameConfig;

    public UIEntityFactory(GameConfig gameConfig) {
        this.gameConfig = gameConfig;
    }

    /**
     * Creates a UI entity by type name.
     *
     * @param type Type: "Canvas", "Panel", "Image", "Button", "Text"
     * @param name Optional custom name (null for default)
     * @return New EditorEntity with UI components
     */
    public EditorEntity create(String type, String name) {
        return switch (type) {
            case "Canvas" -> createCanvas(name);
            case "Panel" -> createPanel(name);
            case "Image" -> createImage(name);
            case "Button" -> createButton(name);
            case "Text" -> createText(name);
            default -> null;
        };
    }

    /**
     * Creates a UICanvas entity.
     * Canvas is the root container for UI elements.
     */
    public EditorEntity createCanvas(String name) {
        EditorEntity entity = new EditorEntity(
                name != null ? name : "Canvas",
                new Vector3f(0, 0, 0),
                false
        );

        ComponentData canvas = new ComponentData(UI_CANVAS);
        // Default: SCREEN_SPACE_OVERLAY
        canvas.getFields().put("renderMode", "SCREEN_SPACE_OVERLAY");
        canvas.getFields().put("sortOrder", 0);
        entity.addComponent(canvas);

        return entity;
    }

    /**
     * Creates a UIPanel entity.
     * Panel is a container with optional background.
     */
    public EditorEntity createPanel(String name) {
        EditorEntity entity = new EditorEntity(
                name != null ? name : "Panel",
                new Vector3f(0, 0, 0),
                false
        );

        ComponentData transform = new ComponentData(UI_TRANSFORM);
        transform.getFields().put("width", 200f);
        transform.getFields().put("height", 100f);
        transform.getFields().put("anchor", new Vector2f(0, 0));
        transform.getFields().put("offset", new Vector2f(10, 10));
        transform.getFields().put("pivot", new Vector2f(0, 0));
        entity.addComponent(transform);

        ComponentData panel = new ComponentData(UI_PANEL);
        panel.getFields().put("backgroundColor", new Vector4f(1f, 1f, 1f, 1f));
        entity.addComponent(panel);

        return entity;
    }

    /**
     * Creates a UIImage entity.
     * Image displays a sprite.
     */
    public EditorEntity createImage(String name) {
        EditorEntity entity = new EditorEntity(
                name != null ? name : "Image",
                new Vector3f(0, 0, 0),
                false
        );

        ComponentData transform = new ComponentData(UI_TRANSFORM);
        transform.getFields().put("width", 64f);
        transform.getFields().put("height", 64f);
        transform.getFields().put("anchor", new Vector2f(0, 0));
        transform.getFields().put("offset", new Vector2f(10, 10));
        transform.getFields().put("pivot", new Vector2f(0, 0));
        entity.addComponent(transform);

        ComponentData image = new ComponentData(UI_IMAGE);
        image.getFields().put("color", new Vector4f(1f, 1f, 1f, 1f));
        entity.addComponent(image);

        return entity;
    }

    /**
     * Creates a UIButton entity.
     * Button with text and click handling.
     */
    public EditorEntity createButton(String name) {
        EditorEntity entity = new EditorEntity(
                name != null ? name : "Button",
                new Vector3f(0, 0, 0),
                false
        );

        ComponentData transform = new ComponentData(UI_TRANSFORM);
        transform.getFields().put("width", 120f);
        transform.getFields().put("height", 40f);
        transform.getFields().put("anchor", new Vector2f(0.5f, 0.5f));
        transform.getFields().put("offset", new Vector2f(0, 0));
        transform.getFields().put("pivot", new Vector2f(0.5f, 0.5f));
        entity.addComponent(transform);

        ComponentData button = new ComponentData(UI_BUTTON);
        button.getFields().put("text", "Button");
        button.getFields().put("color", new Vector4f(1f, 1f, 1f, 1f));
        button.getFields().put("normalColor", new Vector4f(1f, 1f, 1f, 1f));
        button.getFields().put("hoverColor", new Vector4f(0.9f, 0.9f, 0.9f, 1f));
        button.getFields().put("pressedColor", new Vector4f(0.8f, 0.8f, 0.8f, 1f));
        entity.addComponent(button);

        return entity;
    }

    /**
     * Creates a UIText entity.
     * Text displays a string with font settings.
     */
    public EditorEntity createText(String name) {
        EditorEntity entity = new EditorEntity(
                name != null ? name : "Text",
                new Vector3f(0, 0, 0),
                false
        );

        ComponentData transform = new ComponentData(UI_TRANSFORM);
        transform.getFields().put("width", 200f);
        transform.getFields().put("height", 30f);
        transform.getFields().put("anchor", new Vector2f(0, 0));
        transform.getFields().put("offset", new Vector2f(10, 10));
        transform.getFields().put("pivot", new Vector2f(0, 0));
        entity.addComponent(transform);

        ComponentData text = new ComponentData(UI_TEXT);
        text.getFields().put("text", "New Text");
        text.getFields().put("fontSize", 16f);
        text.getFields().put("color", new Vector4f(1f, 1f, 1f, 1f));
        entity.addComponent(text);

        return entity;
    }
}
