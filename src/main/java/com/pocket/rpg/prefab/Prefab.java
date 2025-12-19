package com.pocket.rpg.prefab;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Prefab defines a template for creating configured GameObjects.
 * <p>
 * Prefabs act as factories that produce GameObjects with specific
 * components and default configurations. They also define which
 * properties can be overridden per-instance in the editor.
 * <p>
 * Example implementation:
 * <pre>
 * public class ChestPrefab implements Prefab {
 *     public String getId() { return "chest"; }
 *     public String getDisplayName() { return "Treasure Chest"; }
 *
 *     public List&lt;PropertyDefinition&gt; getEditableProperties() {
 *         return List.of(
 *             new PropertyDefinition("lootTable", PropertyType.STRING, "common_loot"),
 *             new PropertyDefinition("locked", PropertyType.BOOLEAN, false)
 *         );
 *     }
 *
 *     public GameObject instantiate(Vector3f position, Map&lt;String, Object&gt; overrides) {
 *         GameObject chest = new GameObject("Chest", position);
 *         // Add components...
 *         // Apply overrides...
 *         return chest;
 *     }
 * }
 * </pre>
 */
public interface Prefab {

    /**
     * Gets the unique identifier for this prefab.
     * <p>
     * This ID is used for serialization and registry lookup.
     * Should be lowercase with underscores (e.g., "npc_villager", "chest_wooden").
     *
     * @return Unique prefab ID
     */
    String getId();

    /**
     * Gets the human-readable display name for the editor UI.
     *
     * @return Display name (e.g., "Villager NPC", "Wooden Chest")
     */
    String getDisplayName();

    /**
     * Creates a configured GameObject at the given position.
     * <p>
     * The prefab is responsible for:
     * - Creating the GameObject with appropriate name
     * - Adding all required components
     * - Applying property overrides from the map
     *
     * @param position  World position for the entity
     * @param overrides Property values to override defaults (may be empty)
     * @return Fully configured GameObject ready for scene
     */
    GameObject instantiate(Vector3f position, Map<String, Object> overrides);

    /**
     * Gets the preview sprite for editor display.
     * <p>
     * This sprite is shown in:
     * - Prefab browser panel
     * - Entity placer tool ghost preview
     * - Hierarchy panel icon (optional)
     *
     * @return Preview sprite, or null to use default placeholder
     */
    Sprite getPreviewSprite();

    /**
     * Defines which properties can be edited per-instance in the editor.
     * <p>
     * Each property definition specifies:
     * - Name (used as key and display label)
     * - Type (determines UI control)
     * - Default value
     * - Optional tooltip
     *
     * @return List of editable properties (empty if none)
     */
    default List<PropertyDefinition> getEditableProperties() {
        return Collections.emptyList();
    }

    /**
     * Gets the category for grouping in the prefab browser.
     * <p>
     * Examples: "Characters", "Props", "Interactables", "Environment"
     *
     * @return Category name, or null for uncategorized
     */
    default String getCategory() {
        return null;
    }
}
