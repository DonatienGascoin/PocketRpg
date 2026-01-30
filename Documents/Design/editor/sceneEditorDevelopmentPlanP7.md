## Phase 7: Advanced Features (Future)

Lower priority, implement as needed.

### 7.1 Sprite Pivot Editor

**Goal:** Edit pivot points for sprites and spritesheets.

**Access methods:**
1. Right-click on sprite/spritesheet in asset browser → "Edit Pivot"
2. Menu: Assets → Pivot Editor
3. Selection dropdown in the pivot editor panel

**Features:**
- Visual pivot point indicator on sprite preview
- Click to set pivot position
- Preset buttons: Center, Bottom-Center, Bottom-Left, Top-Left
- Per-sprite pivot override for spritesheets
- Default pivot for entire spritesheet
- Save pivot data to .spritesheet file

```java
public class PivotEditorPanel {
    private Sprite selectedSprite;
    private SpriteSheet selectedSheet;
    private int selectedSpriteIndex = -1;
    
    public void render() {
        ImGui.begin("Pivot Editor");
        
        // Sprite/sheet selector
        renderAssetSelector();
        
        if (selectedSprite == null && selectedSheet == null) {
            ImGui.textDisabled("Select a sprite or spritesheet");
            ImGui.end();
            return;
        }
        
        // Large preview with grid
        renderPreview();
        
        // Preset buttons
        if (ImGui.button("Center")) setPivot(0.5f, 0.5f);
        ImGui.sameLine();
        if (ImGui.button("Bottom-Center")) setPivot(0.5f, 0f);
        ImGui.sameLine();
        if (ImGui.button("Bottom-Left")) setPivot(0f, 0f);
        
        // Manual input
        float[] pivot = {currentPivotX, currentPivotY};
        if (ImGui.sliderFloat2("Pivot", pivot, 0f, 1f)) {
            setPivot(pivot[0], pivot[1]);
        }
        
        // Apply/Reset
        if (ImGui.button("Apply")) applyChanges();
        ImGui.sameLine();
        if (ImGui.button("Reset")) resetChanges();
        
        ImGui.end();
    }
    
    private void renderPreview() {
        // Draw sprite with checkerboard background
        // Draw pivot marker (crosshair)
        // Handle click to set pivot position
    }
}
```

### 7.2 Scene Transitions / Triggers

```java
public class TriggerEditorTool implements EditorTool {
    // Draw rectangle to define trigger bounds
    // Configure trigger type and properties
}

public class TriggerInspectorPanel {
    // Type selector: SCENE_TRANSITION, EVENT, DIALOGUE, etc.
    // Target scene (for transitions)
    // Spawn point name
    // Custom properties
}
```

### 7.3 JSON-based Prefabs

Load prefab definitions from JSON files instead of code.

```json
{
  "id": "NPC_Villager",
  "components": [
    {
      "type": "SpriteRenderer",
      "sprite": "sprites/npc_villager.png",
      "zIndex": 1
    },
    {
      "type": "GridMovement",
      "speed": 2.0
    },
    {
      "type": "NPCBehavior",
      "dialogueId": "villager_01"
    }
  ],
  "properties": {
    "facing": { "type": "enum", "values": ["UP", "DOWN", "LEFT", "RIGHT"] },
    "dialogueId": { "type": "string" }
  }
}
```

### 7.4 Copy/Paste for Tiles and Entities

### 7.5 Tileset Auto-Tiling

Define rules for automatic tile selection based on neighbors.

---