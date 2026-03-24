#!/bin/bash
# Scene Inspector — inspect .scene files from the command line.
#
# Usage:
#   bash scripts/inspect-scene.sh <scene> <queries>
#
# Examples:
#   bash scripts/inspect-scene.sh MenuMockups.scene "tree"
#   bash scripts/inspect-scene.sh MenuMockups.scene "tree;stats;find:Canvas"
#   bash scripts/inspect-scene.sh gameData/scenes/DemoScene.scene "validate;refs:Player"
#   bash scripts/inspect-scene.sh MenuMockups.scene "help"
#
# The scene path is resolved automatically:
#   "MenuMockups.scene"              -> gameData/scenes/MenuMockups.scene
#   "MenuMockups"                    -> gameData/scenes/MenuMockups.scene
#   "gameData/scenes/MenuMockups.scene" -> used as-is

if [ $# -lt 1 ]; then
    echo "Usage: bash scripts/inspect-scene.sh <scene> <queries>"
    echo ""
    echo "Examples:"
    echo "  bash scripts/inspect-scene.sh MenuMockups.scene \"tree;stats\""
    echo "  bash scripts/inspect-scene.sh DemoScene \"find:Player;refs:031706b5\""
    echo "  bash scripts/inspect-scene.sh help"
    exit 1
fi

# Handle "help" as a standalone argument (no scene needed)
if [ "$1" = "help" ]; then
    mvn -q test -Dtest="com.pocket.rpg.tools.SceneInspector" -Dqueries="help" 2>/dev/null | grep -A 9999 "^{"
    exit 0
fi

SCENE="$1"
QUERIES="${2:-tree}"

# Resolve scene path
if [ -f "$SCENE" ]; then
    # Full path provided and exists — use as-is
    SCENE_PATH="$SCENE"
else
    # Try adding gameData/scenes/ prefix and/or .scene extension
    if [ -f "gameData/scenes/$SCENE" ]; then
        SCENE_PATH="gameData/scenes/$SCENE"
    elif [ -f "gameData/scenes/${SCENE}.scene" ]; then
        SCENE_PATH="gameData/scenes/${SCENE}.scene"
    else
        echo "Error: Scene file not found: $SCENE"
        echo "Tried: $SCENE, gameData/scenes/$SCENE, gameData/scenes/${SCENE}.scene"
        exit 1
    fi
fi

mvn -q test -Dtest="com.pocket.rpg.tools.SceneInspector" \
    -Dscene="$SCENE_PATH" \
    -Dqueries="$QUERIES" \
    2>/dev/null | grep -A 9999 "^{"
