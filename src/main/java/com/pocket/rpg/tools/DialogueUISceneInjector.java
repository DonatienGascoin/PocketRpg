package com.pocket.rpg.tools;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Utility that injects the DialogueUI hierarchy and a test NPC into a scene file.
 * <p>
 * Since the prefab system doesn't support hierarchy yet, this tool
 * generates the DialogueUI GameObjects as JSON and merges them into
 * an existing .scene file. It also adds a test NPC with a
 * {@code DialogueInteractable} component for testing the dialogue system.
 * <p>
 * Idempotent: running it multiple times replaces the previous objects.
 * <p>
 * Usage:
 * <pre>
 * mvn exec:java -Dexec.mainClass="com.pocket.rpg.tools.DialogueUISceneInjector" \
 *               -Dexec.args="gameData/scenes/DemoScene.scene"
 * </pre>
 */
public class DialogueUISceneInjector {

    private static final String ROOT_NAME = "DialogueUI";
    private static final String NPC_NAME = "TestNPC";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Layout constants (mirror DialogueUIBuilder)
    private static final float DIALOGUE_BOX_HEIGHT = 150f;
    private static final float CHOICE_HEIGHT = 40f;
    private static final float CHOICE_SPACING = 5f;
    private static final int MAX_CHOICES = 4;
    private static final float CONTAINER_HEIGHT_FRACTION = 0.8f;
    private static final String FONT = "fonts/Pokemon-Red.ttf";
    private static final int FONT_SIZE = 20;
    private static final int ARROW_FONT_SIZE = 16;

    private static float choicePanelHeight(int count) {
        float contentHeight = count * CHOICE_HEIGHT + Math.max(0, count - 1) * CHOICE_SPACING;
        return contentHeight / CONTAINER_HEIGHT_FRACTION;
    }

    public static void main(String[] args) throws IOException {
        String scenePath = args.length > 0 ? args[0] : "gameData/scenes/DemoScene.scene";
        Path path = Path.of(scenePath);

        if (!Files.exists(path)) {
            System.err.println("Scene file not found: " + path.toAbsolutePath());
            System.exit(1);
        }

        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject scene = JsonParser.parseString(json).getAsJsonObject();
        JsonArray gameObjects = scene.getAsJsonArray("gameObjects");

        // Remove existing injected objects (idempotent)
        removeByName(gameObjects, ROOT_NAME);
        removeByName(gameObjects, NPC_NAME);

        // Generate and add new DialogueUI hierarchy
        List<JsonObject> dialogueObjects = buildDialogueUIObjects();
        for (JsonObject obj : dialogueObjects) {
            gameObjects.add(obj);
        }

        // Generate and add test NPC
        JsonObject npc = buildTestNPC();
        gameObjects.add(npc);

        // Write back
        String output = GSON.toJson(scene);
        Files.writeString(path, output, StandardCharsets.UTF_8);

        System.out.println("Injected " + dialogueObjects.size() + " DialogueUI objects + 1 TestNPC into " + path);
    }

    // ========================================================================
    // REMOVAL (idempotent)
    // ========================================================================

    /**
     * Removes a root-level object by name and all its descendants from the gameObjects array.
     */
    private static void removeByName(JsonArray gameObjects, String name) {
        // Find the root object's id
        Set<String> idsToRemove = new LinkedHashSet<>();
        for (JsonElement el : gameObjects) {
            JsonObject go = el.getAsJsonObject();
            if (name.equals(go.get("name").getAsString()) && !go.has("parentId")) {
                idsToRemove.add(go.get("id").getAsString());
            }
        }

        if (idsToRemove.isEmpty()) {
            return;
        }

        // Collect all descendants (BFS)
        boolean found = true;
        while (found) {
            found = false;
            for (JsonElement el : gameObjects) {
                JsonObject go = el.getAsJsonObject();
                if (go.has("parentId") && idsToRemove.contains(go.get("parentId").getAsString())) {
                    if (idsToRemove.add(go.get("id").getAsString())) {
                        found = true;
                    }
                }
            }
        }

        // Remove all collected objects (iterate backwards to avoid index issues)
        for (int i = gameObjects.size() - 1; i >= 0; i--) {
            String id = gameObjects.get(i).getAsJsonObject().get("id").getAsString();
            if (idsToRemove.contains(id)) {
                gameObjects.remove(i);
            }
        }

        System.out.println("Removed " + idsToRemove.size() + " existing '" + name + "' objects");
    }

    // ========================================================================
    // BUILD HIERARCHY
    // ========================================================================

    private static List<JsonObject> buildDialogueUIObjects() {
        List<JsonObject> objects = new ArrayList<>();

        String rootId = newId();
        String boxId = newId();
        String textId = newId();
        String continueId = newId();
        String panelId = newId();

        // Root: DialogueUI (UICanvas)
        objects.add(gameObject(rootId, "DialogueUI", true, 0, null,
                uiTransform(0, 0, 0, 0, 0, 0, 100, 100, "PERCENT", 100, "PERCENT", 100, null),
                uiCanvas(10)
        ));

        // DialogueBox (UIImage — battleDialogue.png, SLICED)
        objects.add(gameObject(boxId, "DialogueBox", true, 0, rootId,
                uiTransform(0, DIALOGUE_BOX_HEIGHT, 0, 1, 0, 1, 100, DIALOGUE_BOX_HEIGHT, "PERCENT", 100, "FIXED", 100, null),
                uiImage("sprites/UIs/Battle/battleDialogue.png", "SLICED", "dialogue_box")
        ));

        // DialogueText (UIText)
        objects.add(gameObject(textId, "DialogueText", true, 0, boxId,
                uiTransform(20, 20, 0, 0, 0, 0, 100, 100, "PERCENT", 85, "PERCENT", 75, null),
                uiText("", FONT, FONT_SIZE, 0, 0, 0, 1, "LEFT", "TOP", true, "dialogue_text")
        ));

        // ContinueIndicator (UIText)
        objects.add(gameObject(continueId, "ContinueIndicator", true, 1, boxId,
                uiTransform(-8, -8, 1, 1, 1, 1, 24, 24, "FIXED", 100, "FIXED", 100, null),
                uiText("v", FONT, ARROW_FONT_SIZE, 0, 0, 0, 0, "CENTER", "MIDDLE", false, "dialogue_continue")
        ));

        // ChoicePanel (UIImage — dialogueChoiceBg.png) — anchored top-right, ~35% width
        // Height sized for MAX_CHOICES; resized at runtime by PlayerDialogueManager
        // Starts active so registerCachedComponents registers keys;
        // PlayerDialogueManager.resolveUI() hides it after resolving references
        objects.add(gameObject(panelId, "ChoicePanel", true, 2, boxId,
                uiTransform(-10, 0, 1, 0, 1, 1, 100, choicePanelHeight(MAX_CHOICES), "PERCENT", 35, "FIXED", 100, null),
                uiImage("sprites/UIs/Battle/dialogueChoiceBg.png", "SIMPLE", "dialogue_choice_panel")
        ));

        // ChoiceContainer — inset from ChoicePanel edges; VerticalLayoutGroup stacks children
        String containerId = newId();
        objects.add(gameObject(containerId, "ChoiceContainer", true, 0, panelId,
                uiTransform(16, 0, 0, 0.10f, 0, 0, 100, 100, "PERCENT", 75, "PERCENT", 80, null),
                uiVerticalLayoutGroup(CHOICE_SPACING, true)
        ));

        // Choice slots (0-3) — children of ChoiceContainer, fixed 40px height
        for (int i = 0; i < MAX_CHOICES; i++) {
            String slotId = newId();
            String arrowId = newId();
            String choiceTextId = newId();

            // Slot — fixed height, layout group handles positioning
            objects.add(gameObject(slotId, "Choice" + i, true, i, containerId,
                    uiTransform(0, 0, 0, 0, 0, 0, 100, CHOICE_HEIGHT, "PERCENT", 100, "FIXED", 100, null)
            ));

            // Arrow
            objects.add(gameObject(arrowId, "Arrow" + i, true, 0, slotId,
                    uiTransform(0, 0, 0, 0, 0, 0, 100, 100, "PERCENT", 20, "PERCENT", 100, null),
                    uiText("<", FONT, 25, 1, 1, 1, 1, "CENTER", "MIDDLE", false, "dialogue_choice_arrow_" + i)
            ));

            // Choice text
            objects.add(gameObject(choiceTextId, "Text" + i, true, 1, slotId,
                    uiTransform(0, 0, 0.2f, 0, 0, 0, 100, 100, "PERCENT", 80, "PERCENT", 100, null),
                    uiText("", FONT, FONT_SIZE, 1, 1, 1, 1, "LEFT", "MIDDLE", true, "dialogue_choice_text_" + i)
            ));
        }

        return objects;
    }

    // ========================================================================
    // TEST NPC
    // ========================================================================

    /**
     * Builds a test NPC with DialogueInteractable at (3, -3).
     * Has conditional dialogue: after hearing the cave rumor, the NPC says different things.
     */
    private static JsonObject buildTestNPC() {
        return gameObject(newId(), NPC_NAME, true, 20, null,
                transform(3.5f, -3.0f),
                spriteRenderer("sprites/characters/NPC1.png#0"),
                triggerZone(),
                staticOccupant(),
                dialogueInteractable()
        );
    }

    // ========================================================================
    // WORLD-SPACE COMPONENT BUILDERS
    // ========================================================================

    private static JsonObject transform(float x, float y) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.core.Transform");

        JsonObject props = new JsonObject();
        props.add("localPosition", vec3(x, y, 0));
        props.add("localRotation", vec3(0, 0, 0));
        props.add("localScale", vec3(1, 1, 1));

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject spriteRenderer(String spriteRef) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.rendering.SpriteRenderer");

        JsonObject props = new JsonObject();
        props.addProperty("sprite", "com.pocket.rpg.rendering.resources.Sprite:" + spriteRef);
        props.add("tintColor", vec4(1, 1, 1, 1));
        props.addProperty("flipX", false);
        props.addProperty("flipY", false);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject triggerZone() {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.interaction.TriggerZone");

        JsonObject props = new JsonObject();
        props.addProperty("offsetX", 0);
        props.addProperty("offsetY", 0);
        props.addProperty("width", 1);
        props.addProperty("height", 1);
        props.addProperty("oneShot", false);
        props.addProperty("playerOnly", true);
        props.addProperty("elevation", 0);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject staticOccupant() {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.interaction.StaticOccupant");

        JsonObject props = new JsonObject();
        props.addProperty("offsetX", 0);
        props.addProperty("offsetY", 0);
        props.addProperty("width", 1);
        props.addProperty("height", 1);
        props.addProperty("elevation", 0);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject dialogueInteractable() {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.dialogue.DialogueInteractable");

        JsonObject props = new JsonObject();
        props.addProperty("directionalInteraction", true);
        JsonArray interactFrom = new JsonArray();
        interactFrom.add("DOWN");
        props.add("interactFrom", interactFrom);

        // Default dialogue
        props.addProperty("dialogue",
                "com.pocket.rpg.dialogue.Dialogue:dialogues/npc_greeting.dialogue.json");

        // Conditional dialogue: after HEARD_CAVE_RUMOR, use post_lore dialogue
        JsonArray conditionals = new JsonArray();
        JsonObject conditional = new JsonObject();
        JsonArray conditions = new JsonArray();
        JsonObject condition = new JsonObject();
        condition.addProperty("eventName", "HEARD_CAVE_RUMOR");
        condition.addProperty("expectedState", "FIRED");
        conditions.add(condition);
        conditional.add("conditions", conditions);
        conditional.addProperty("dialogue",
                "dialogues/npc_post_lore.dialogue.json");
        conditionals.add(conditional);
        props.add("conditionalDialogues", conditionals);

        comp.add("properties", props);
        return comp;
    }

    // ========================================================================
    // JSON BUILDERS
    // ========================================================================

    private static JsonObject gameObject(String id, String name, boolean active, int order,
                                         String parentId, JsonObject... components) {
        JsonObject go = new JsonObject();
        go.addProperty("id", id);
        go.addProperty("name", name);
        go.addProperty("active", active);
        go.addProperty("order", order);
        if (parentId != null) {
            go.addProperty("parentId", parentId);
        }
        JsonArray comps = new JsonArray();
        for (JsonObject c : components) {
            comps.add(c);
        }
        go.add("components", comps);
        return go;
    }

    private static JsonObject uiTransform(float offsetX, float offsetY,
                                           float anchorX, float anchorY,
                                           float pivotX, float pivotY,
                                           float width, float height,
                                           String widthMode, float widthPercent,
                                           String heightMode, float heightPercent,
                                           String componentKey) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.ui.UITransform");

        JsonObject props = new JsonObject();
        if (componentKey != null) {
            props.addProperty("componentKey", componentKey);
        }
        props.add("localPosition", vec3(offsetX, offsetY, 0));
        props.add("localRotation", vec3(0, 0, 0));
        props.add("localScale", vec3(1, 1, 1));
        props.add("anchor", vec2(anchorX, anchorY));
        props.add("pivot", vec2(pivotX, pivotY));
        props.addProperty("width", width);
        props.addProperty("height", height);
        props.addProperty("widthMode", widthMode);
        props.addProperty("heightMode", heightMode);
        props.addProperty("widthPercent", widthPercent);
        props.addProperty("heightPercent", heightPercent);
        props.addProperty("matchParentRotation", false);
        props.addProperty("matchParentScale", false);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject uiCanvas(int sortOrder) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.ui.UICanvas");

        JsonObject props = new JsonObject();
        props.addProperty("raycastTarget", true);
        props.addProperty("renderMode", "SCREEN_SPACE_OVERLAY");
        props.addProperty("sortOrder", sortOrder);
        props.addProperty("planeDistance", 100.0);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject uiPanel(float r, float g, float b, float a, String componentKey) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.ui.UIPanel");

        JsonObject props = new JsonObject();
        if (componentKey != null) {
            props.addProperty("componentKey", componentKey);
        }
        props.addProperty("raycastTarget", true);
        props.add("color", vec4(r, g, b, a));

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject uiImage(String spritePath, String imageType, String componentKey) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.ui.UIImage");

        JsonObject props = new JsonObject();
        if (componentKey != null) {
            props.addProperty("componentKey", componentKey);
        }
        props.addProperty("raycastTarget", true);
        props.addProperty("sprite", "com.pocket.rpg.rendering.resources.Sprite:" + spritePath);
        props.add("color", vec4(1, 1, 1, 1));
        props.addProperty("imageType", imageType);
        props.addProperty("fillCenter", true);
        props.addProperty("fillMethod", "HORIZONTAL");
        props.addProperty("fillOrigin", "LEFT");
        props.addProperty("fillAmount", 1.0);
        props.addProperty("fillClockwise", true);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject uiText(String text, String fontPath, int fontSize,
                                      float r, float g, float b, float a,
                                      String hAlign, String vAlign,
                                      boolean wordWrap, String componentKey) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.ui.UIText");

        JsonObject props = new JsonObject();
        if (componentKey != null) {
            props.addProperty("componentKey", componentKey);
        }
        props.addProperty("raycastTarget", true);
        props.addProperty("fontPath", fontPath);
        props.addProperty("fontSize", fontSize);
        props.addProperty("text", text);
        props.add("color", vec4(r, g, b, a));
        props.addProperty("horizontalAlignment", hAlign);
        props.addProperty("verticalAlignment", vAlign);
        props.addProperty("wordWrap", wordWrap);
        props.addProperty("autoFit", false);
        props.addProperty("shadow", false);

        comp.add("properties", props);
        return comp;
    }

    private static JsonObject uiVerticalLayoutGroup(float spacing, boolean forceExpandWidth) {
        JsonObject comp = new JsonObject();
        comp.addProperty("type", "com.pocket.rpg.components.ui.UIVerticalLayoutGroup");

        JsonObject props = new JsonObject();
        props.addProperty("spacing", spacing);
        props.addProperty("childForceExpandWidth", forceExpandWidth);
        props.addProperty("childForceExpandHeight", false);
        props.addProperty("paddingLeft", 0);
        props.addProperty("paddingRight", 0);
        props.addProperty("paddingTop", 0);
        props.addProperty("paddingBottom", 0);
        props.addProperty("childAlignment", "LEFT");

        comp.add("properties", props);
        return comp;
    }

    // ========================================================================
    // VECTOR HELPERS
    // ========================================================================

    private static JsonObject vec2(float x, float y) {
        JsonObject v = new JsonObject();
        v.addProperty("x", x);
        v.addProperty("y", y);
        return v;
    }

    private static JsonObject vec3(float x, float y, float z) {
        JsonObject v = new JsonObject();
        v.addProperty("x", x);
        v.addProperty("y", y);
        v.addProperty("z", z);
        return v;
    }

    private static JsonObject vec4(float x, float y, float z, float w) {
        JsonObject v = new JsonObject();
        v.addProperty("x", x);
        v.addProperty("y", y);
        v.addProperty("z", z);
        v.addProperty("w", w);
        return v;
    }

    // ========================================================================
    // ID GENERATION
    // ========================================================================

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
