package com.pocket.rpg.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.AssetsConfiguration;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.CacheStats;
import com.pocket.rpg.resources.EditorCapability;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.resources.LoadOptions;
import com.pocket.rpg.resources.ResourceCache;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReferenceMeta;
import com.pocket.rpg.serialization.ComponentReferenceResolver;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.serialization.Serializer;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * CLI tool for inspecting scene files without engine bootstrap.
 * <p>
 * Usage:
 * <pre>
 * mvn -q test -Dtest=SceneInspector -Dscene="gameData/scenes/MyScene.scene" -Dqueries="help"
 * </pre>
 * <p>
 * Run with {@code -Dqueries="help"} to see all available commands.
 */
class SceneInspector {

    private static final Gson OUTPUT_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    @BeforeAll
    static void init() {
        StubAssetContext stub = new StubAssetContext();
        Assets.setContext(stub);
        Serializer.init(stub);
        ComponentRegistry.initialize();
    }

    @Test
    void inspect() throws IOException {
        String scenePath = System.getProperty("scene");
        String queriesStr = System.getProperty("queries");

        if (queriesStr == null || queriesStr.isEmpty()) {
            System.out.println("{\"error\": \"Missing -Dqueries=help or -Dqueries=tree;node:Name;...\"}");
            return;
        }

        // Help can run without a scene
        if (queriesStr.trim().equals("help")) {
            System.out.println(OUTPUT_GSON.toJson(Map.of("help", buildHelpResult())));
            return;
        }

        if (scenePath == null || scenePath.isEmpty()) {
            System.out.println("{\"error\": \"Missing -Dscene=path/to/scene.scene\"}");
            return;
        }

        // Load and deserialize scene
        String json = Files.readString(Path.of(scenePath), StandardCharsets.UTF_8);
        SceneData sceneData = Serializer.fromJson(json, SceneData.class);

        // Build hierarchy
        List<NodeInfo> roots = buildHierarchy(sceneData.getGameObjects());

        // Process queries
        Map<String, Object> results = new LinkedHashMap<>();
        String[] queries = queriesStr.split(";");
        for (String query : queries) {
            query = query.trim();
            if (query.isEmpty()) continue;

            String command;
            String arg = null;
            int colonIdx = query.indexOf(':');
            if (colonIdx >= 0) {
                command = query.substring(0, colonIdx);
                arg = query.substring(colonIdx + 1);
            } else {
                command = query;
            }

            Object result = switch (command) {
                case "help" -> buildHelpResult();
                case "tree" -> buildTreeResult(sceneData.getName(), roots);
                case "node" -> buildNodeResult(roots, arg);
                case "search" -> buildSearchResult(roots, arg);
                case "find" -> buildFindResult(roots, arg);
                case "resolve" -> buildResolveResult(roots, arg);
                case "diff" -> buildDiffResult(roots, arg);
                case "query" -> buildQueryResult(roots, arg);
                case "validate" -> buildValidateResult(sceneData, roots);
                case "refs" -> buildRefsResult(roots, arg);
                case "stats" -> buildStatsResult(sceneData.getName(), roots);
                default -> Map.of("error", "Unknown command: " + command + ". Use 'help' to see available commands.");
            };

            results.put(query, result);
        }

        System.out.println(OUTPUT_GSON.toJson(results));
    }

    // ========================================================================
    // COMMAND: help
    // ========================================================================

    private Object buildHelpResult() {
        List<Map<String, String>> commands = new ArrayList<>();

        commands.add(helpEntry("help",
                "Lists all available commands with descriptions and examples.",
                "help"));

        commands.add(helpEntry("tree",
                "Full node hierarchy showing name, id, active state, and component types for every node.",
                "tree"));

        commands.add(helpEntry("node:<name-or-id>",
                "Detailed view of a single node: all components with their field values. "
                + "For scratch entities shows full component data; for prefab instances shows overrides.",
                "node:Player"));

        commands.add(helpEntry("find:<name>",
                "Find nodes by name substring (case-insensitive). "
                + "Returns matching nodes with path, active state, components, and position.",
                "find:Enemy"));

        commands.add(helpEntry("search:<ComponentType>",
                "Find all nodes that have a specific component type (case-insensitive substring match). "
                + "Returns matching nodes with path and position.",
                "search:SpriteRenderer"));

        commands.add(helpEntry("resolve:<name-or-id>",
                "Resolve a prefab instance: loads the .prefab.json file, merges the scene's "
                + "componentOverrides on top, and shows the final component state the engine would use. "
                + "For scratch entities, behaves like 'node'.",
                "resolve:Guard"));

        commands.add(helpEntry("diff:<name-or-id>",
                "Compare a prefab instance's overridden fields against the prefab defaults. "
                + "Shows only the fields that differ, with both 'prefab' and 'scene' values. "
                + "For scratch entities, returns an error (no prefab to compare against).",
                "diff:Guard"));

        commands.add(helpEntry("query:<Component.field=value>",
                "Find nodes where a component field matches a value. Format: ComponentSimpleName.fieldName=value. "
                + "Comparison is string-based (field value is serialized then compared case-insensitively). "
                + "Works on scratch entities only (prefab instances don't have deserialized components).",
                "query:UITransform.widthMode=PERCENT"));

        commands.add(helpEntry("validate",
                "Check scene integrity. Detects: orphan parentId references (parent doesn't exist), "
                + "duplicate node IDs, missing prefab files, and nodes missing Transform or UITransform.",
                "validate"));

        commands.add(helpEntry("refs:<name-or-id>",
                "Show @ComponentReference dependencies for all components on a node. "
                + "Lists KEY references (with their stored key strings) and hierarchy references "
                + "(SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE) with target component types.",
                "refs:PlayerUI"));

        commands.add(helpEntry("stats",
                "Scene overview: total node count, max hierarchy depth, prefab vs scratch count, "
                + "and component type distribution (how many nodes use each component type).",
                "stats"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("usage", "mvn -q test -Dtest=SceneInspector -Dscene=\"path/to/scene.scene\" -Dqueries=\"cmd1;cmd2:arg;...\"");
        result.put("note", "Multiple queries separated by semicolons. Results returned as a JSON map with query as key.");
        result.put("commands", commands);
        return result;
    }

    private Map<String, String> helpEntry(String command, String description, String example) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("command", command);
        entry.put("description", description);
        entry.put("example", example);
        return entry;
    }

    // ========================================================================
    // HIERARCHY BUILDING
    // ========================================================================

    private List<NodeInfo> buildHierarchy(List<GameObjectData> gameObjects) {
        if (gameObjects == null) return List.of();

        // Index by id
        Map<String, GameObjectData> byId = new LinkedHashMap<>();
        for (GameObjectData go : gameObjects) {
            byId.put(go.getId(), go);
        }

        // Group children by parent
        Map<String, List<GameObjectData>> childrenByParent = new LinkedHashMap<>();
        List<GameObjectData> roots = new ArrayList<>();

        for (GameObjectData go : gameObjects) {
            String parentId = go.getParentId();
            if (parentId == null || parentId.isEmpty()) {
                roots.add(go);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(go);
            }
        }

        // Sort by order
        Comparator<GameObjectData> byOrder = Comparator.comparingInt(GameObjectData::getOrder);
        roots.sort(byOrder);
        childrenByParent.values().forEach(list -> list.sort(byOrder));

        // Build tree recursively
        List<NodeInfo> result = new ArrayList<>();
        for (GameObjectData root : roots) {
            result.add(buildNodeInfo(root, childrenByParent, ""));
        }
        return result;
    }

    private NodeInfo buildNodeInfo(GameObjectData go, Map<String, List<GameObjectData>> childrenByParent, String parentPath) {
        NodeInfo info = new NodeInfo();
        info.data = go;
        info.path = parentPath.isEmpty() ? go.getName() : parentPath + "/" + go.getName();

        List<GameObjectData> children = childrenByParent.getOrDefault(go.getId(), List.of());
        info.children = new ArrayList<>();
        for (GameObjectData child : children) {
            info.children.add(buildNodeInfo(child, childrenByParent, info.path));
        }
        return info;
    }

    // ========================================================================
    // COMMAND: tree
    // ========================================================================

    private Map<String, Object> buildTreeResult(String sceneName, List<NodeInfo> roots) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sceneName", sceneName);
        result.put("nodes", roots.stream().map(this::nodeToTreeMap).toList());
        return result;
    }

    private Map<String, Object> nodeToTreeMap(NodeInfo node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", node.data.getName());
        map.put("id", node.data.getId());
        map.put("active", node.data.isActive());

        if (node.data.isPrefabInstance()) {
            map.put("prefab", getPrefabRef(node.data));
        }

        map.put("components", getComponentSimpleNames(node.data));
        map.put("children", node.children.stream().map(this::nodeToTreeMap).toList());
        return map;
    }

    // ========================================================================
    // COMMAND: node
    // ========================================================================

    private Object buildNodeResult(List<NodeInfo> roots, String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return Map.of("error", "node command requires an argument: node:<name-or-id>");
        }

        NodeInfo found = findNode(roots, nameOrId);
        if (found == null) {
            return Map.of("error", "Node not found: " + nameOrId);
        }

        return buildNodeDetailMap(found);
    }

    private Map<String, Object> buildNodeDetailMap(NodeInfo node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", node.data.getName());
        result.put("id", node.data.getId());
        result.put("active", node.data.isActive());
        result.put("tag", node.data.getTag());
        result.put("parentId", node.data.getParentId());
        result.put("path", node.path);

        if (node.data.isPrefabInstance()) {
            result.put("prefab", getPrefabRef(node.data));
            result.put("componentOverrides", node.data.getComponentOverrides());
            result.put("childOverrides", node.data.getChildOverrides());
        } else {
            result.put("components", buildComponentDetails(node.data));
        }

        result.put("childCount", node.children.size());
        result.put("childNames", node.children.stream().map(c -> c.data.getName()).toList());

        return result;
    }

    private List<Map<String, Object>> buildComponentDetails(GameObjectData go) {
        List<Component> components = go.getComponents();
        if (components == null) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Component comp : components) {
            result.add(buildSingleComponentDetail(comp));
        }
        return result;
    }

    private Map<String, Object> buildSingleComponentDetail(Component comp) {
        Map<String, Object> compMap = new LinkedHashMap<>();
        String fullType = comp.getClass().getName();
        compMap.put("type", comp.getClass().getSimpleName());
        compMap.put("fullType", fullType);

        Map<String, Object> fields = new LinkedHashMap<>();
        ComponentMeta meta = ComponentRegistry.getByClassName(fullType);
        if (meta != null) {
            for (FieldMeta fieldMeta : meta.fields()) {
                try {
                    fieldMeta.field().setAccessible(true);
                    Object value = fieldMeta.field().get(comp);
                    fields.put(fieldMeta.name(), toSerializable(value));
                } catch (IllegalAccessException e) {
                    fields.put(fieldMeta.name(), "<access error>");
                }
            }
        }
        compMap.put("fields", fields);
        return compMap;
    }

    // ========================================================================
    // COMMAND: search
    // ========================================================================

    private Object buildSearchResult(List<NodeInfo> roots, String componentType) {
        if (componentType == null || componentType.isEmpty()) {
            return Map.of("error", "search command requires an argument: search:<ComponentType>");
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        collectByComponent(roots, componentType.toLowerCase(), matches);
        return matches;
    }

    private void collectByComponent(List<NodeInfo> nodes, String typeLower, List<Map<String, Object>> matches) {
        for (NodeInfo node : nodes) {
            List<String> names = getComponentSimpleNames(node.data);
            boolean hasMatch = names.stream().anyMatch(n -> n.toLowerCase().contains(typeLower));
            if (hasMatch) {
                matches.add(buildMatchEntry(node));
            }
            collectByComponent(node.children, typeLower, matches);
        }
    }

    // ========================================================================
    // COMMAND: find
    // ========================================================================

    private Object buildFindResult(List<NodeInfo> roots, String name) {
        if (name == null || name.isEmpty()) {
            return Map.of("error", "find command requires an argument: find:<name>");
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        collectByName(roots, name.toLowerCase(), matches);
        return matches;
    }

    private void collectByName(List<NodeInfo> nodes, String nameLower, List<Map<String, Object>> matches) {
        for (NodeInfo node : nodes) {
            if (node.data.getName() != null && node.data.getName().toLowerCase().contains(nameLower)) {
                matches.add(buildMatchEntry(node));
            }
            collectByName(node.children, nameLower, matches);
        }
    }

    // ========================================================================
    // COMMAND: resolve
    // ========================================================================

    private Object buildResolveResult(List<NodeInfo> roots, String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return Map.of("error", "resolve command requires an argument: resolve:<name-or-id>");
        }

        NodeInfo found = findNode(roots, nameOrId);
        if (found == null) {
            return Map.of("error", "Node not found: " + nameOrId);
        }

        // Scratch entities: same as node command
        if (found.data.isScratchEntity()) {
            return buildNodeDetailMap(found);
        }

        // Prefab instance: load prefab and merge overrides
        String prefabPath = found.data.getPrefab();
        if (prefabPath == null || prefabPath.isEmpty()) {
            return Map.of("error", "Prefab instance uses legacy prefabId ('" + found.data.getPrefabId()
                    + "'), which cannot be resolved without the runtime registry. Only file-based prefabs are supported.");
        }

        try {
            JsonPrefab prefab = loadPrefab(prefabPath);
            if (prefab == null) {
                return Map.of("error", "Failed to load prefab file: " + prefabPath);
            }

            // Find the root node in the prefab (the one without a parentId)
            GameObjectData prefabRoot = findPrefabRoot(prefab);
            if (prefabRoot == null) {
                return Map.of("error", "Prefab has no root node: " + prefabPath);
            }

            // Build resolved component list: prefab defaults + scene overrides
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", found.data.getName());
            result.put("id", found.data.getId());
            result.put("active", found.data.isActive());
            result.put("path", found.path);
            result.put("prefab", prefabPath);
            result.put("resolvedComponents", resolveComponents(prefabRoot, found.data.getComponentOverrides()));

            // Resolve child overrides too
            if (found.data.getChildOverrides() != null && !found.data.getChildOverrides().isEmpty()) {
                Map<String, Object> resolvedChildren = new LinkedHashMap<>();
                List<NodeInfo> prefabHierarchy = buildHierarchy(prefab.getGameObjects());

                for (Map.Entry<String, GameObjectData.ChildNodeOverrides> entry : found.data.getChildOverrides().entrySet()) {
                    String childId = entry.getKey();
                    GameObjectData.ChildNodeOverrides childOverrides = entry.getValue();

                    // Find the child in the prefab hierarchy
                    NodeInfo prefabChild = findNodeInList(prefabHierarchy, childId);
                    if (prefabChild != null && prefabChild.data.isScratchEntity()) {
                        Map<String, Object> childResult = new LinkedHashMap<>();
                        if (childOverrides.getName() != null) {
                            childResult.put("nameOverride", childOverrides.getName());
                        }
                        if (childOverrides.getActive() != null) {
                            childResult.put("activeOverride", childOverrides.getActive());
                        }
                        childResult.put("resolvedComponents",
                                resolveComponents(prefabChild.data, childOverrides.getComponentOverrides()));
                        resolvedChildren.put(childId, childResult);
                    }
                }
                if (!resolvedChildren.isEmpty()) {
                    result.put("resolvedChildOverrides", resolvedChildren);
                }
            }

            return result;

        } catch (IOException e) {
            return Map.of("error", "Failed to read prefab file: " + prefabPath + " — " + e.getMessage());
        }
    }

    private List<Map<String, Object>> resolveComponents(GameObjectData prefabNode,
                                                         Map<String, Map<String, Object>> overrides) {
        List<Component> prefabComponents = prefabNode.getComponents();
        if (prefabComponents == null) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Component comp : prefabComponents) {
            Map<String, Object> compDetail = buildSingleComponentDetail(comp);

            // Apply overrides on top
            String fullType = comp.getClass().getName();
            if (overrides != null && overrides.containsKey(fullType)) {
                Map<String, Object> fieldOverrides = overrides.get(fullType);
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = (Map<String, Object>) compDetail.get("fields");
                Map<String, Object> mergedFields = new LinkedHashMap<>(fields);

                for (Map.Entry<String, Object> ov : fieldOverrides.entrySet()) {
                    mergedFields.put(ov.getKey(), toSerializable(ov.getValue()));
                }

                compDetail.put("fields", mergedFields);
                compDetail.put("hasOverrides", true);
                compDetail.put("overriddenFields", new ArrayList<>(fieldOverrides.keySet()));
            }

            result.add(compDetail);
        }
        return result;
    }

    // ========================================================================
    // COMMAND: diff
    // ========================================================================

    private Object buildDiffResult(List<NodeInfo> roots, String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return Map.of("error", "diff command requires an argument: diff:<name-or-id>");
        }

        NodeInfo found = findNode(roots, nameOrId);
        if (found == null) {
            return Map.of("error", "Node not found: " + nameOrId);
        }

        if (found.data.isScratchEntity()) {
            return Map.of("error", "Node '" + nameOrId + "' is a scratch entity, not a prefab instance. "
                    + "Diff only works on prefab instances (compares overrides vs defaults).");
        }

        String prefabPath = found.data.getPrefab();
        if (prefabPath == null || prefabPath.isEmpty()) {
            return Map.of("error", "Prefab instance uses legacy prefabId, cannot resolve for diff.");
        }

        try {
            JsonPrefab prefab = loadPrefab(prefabPath);
            if (prefab == null) {
                return Map.of("error", "Failed to load prefab file: " + prefabPath);
            }

            GameObjectData prefabRoot = findPrefabRoot(prefab);
            if (prefabRoot == null) {
                return Map.of("error", "Prefab has no root node: " + prefabPath);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", found.data.getName());
            result.put("id", found.data.getId());
            result.put("prefab", prefabPath);

            Map<String, Map<String, Object>> overrides = found.data.getComponentOverrides();
            if (overrides == null || overrides.isEmpty()) {
                result.put("differences", Map.of());
                result.put("summary", "No overrides — node uses prefab defaults");
                return result;
            }

            // Build diff per component
            Map<String, Object> differences = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : overrides.entrySet()) {
                String compType = entry.getKey();
                Map<String, Object> fieldOverrides = entry.getValue();
                String simpleName = compType.substring(compType.lastIndexOf('.') + 1);

                // Find the matching component in the prefab
                Component prefabComp = findComponentByType(prefabRoot, compType);

                Map<String, Object> fieldDiffs = new LinkedHashMap<>();
                for (Map.Entry<String, Object> fieldEntry : fieldOverrides.entrySet()) {
                    String fieldName = fieldEntry.getKey();
                    Object sceneValue = toSerializable(fieldEntry.getValue());

                    Object prefabValue = null;
                    if (prefabComp != null) {
                        prefabValue = getFieldValue(prefabComp, fieldName);
                    }

                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("prefab", prefabValue);
                    diff.put("scene", sceneValue);
                    fieldDiffs.put(fieldName, diff);
                }

                differences.put(simpleName, fieldDiffs);
            }

            result.put("differences", differences);
            return result;

        } catch (IOException e) {
            return Map.of("error", "Failed to read prefab file: " + prefabPath + " — " + e.getMessage());
        }
    }

    // ========================================================================
    // COMMAND: query
    // ========================================================================

    private Object buildQueryResult(List<NodeInfo> roots, String queryExpr) {
        if (queryExpr == null || queryExpr.isEmpty()) {
            return Map.of("error", "query command requires an argument: query:<Component.field=value>. "
                    + "Example: query:UITransform.widthMode=PERCENT");
        }

        // Parse Component.field=value
        int dotIdx = queryExpr.indexOf('.');
        if (dotIdx < 0) {
            return Map.of("error", "Invalid query format. Expected: Component.field=value (e.g., UITransform.widthMode=PERCENT)");
        }

        String componentName = queryExpr.substring(0, dotIdx);
        String fieldAndValue = queryExpr.substring(dotIdx + 1);

        int eqIdx = fieldAndValue.indexOf('=');
        if (eqIdx < 0) {
            return Map.of("error", "Invalid query format. Expected: Component.field=value (e.g., UITransform.widthMode=PERCENT)");
        }

        String fieldName = fieldAndValue.substring(0, eqIdx);
        String targetValue = fieldAndValue.substring(eqIdx + 1).toLowerCase();

        List<Map<String, Object>> matches = new ArrayList<>();
        collectByFieldValue(roots, componentName.toLowerCase(), fieldName, targetValue, matches);
        return matches;
    }

    private void collectByFieldValue(List<NodeInfo> nodes, String compNameLower,
                                      String fieldName, String targetValueLower,
                                      List<Map<String, Object>> matches) {
        for (NodeInfo node : nodes) {
            if (node.data.isScratchEntity()) {
                List<Component> components = node.data.getComponents();
                if (components != null) {
                    for (Component comp : components) {
                        if (!comp.getClass().getSimpleName().toLowerCase().contains(compNameLower)) continue;

                        Object value = getFieldValue(comp, fieldName);
                        if (value != null) {
                            String serialized = String.valueOf(toSerializable(value)).toLowerCase();
                            if (serialized.equals(targetValueLower)) {
                                Map<String, Object> entry = buildMatchEntry(node);
                                entry.put("matchedComponent", comp.getClass().getSimpleName());
                                entry.put("matchedField", fieldName);
                                entry.put("matchedValue", toSerializable(value));
                                matches.add(entry);
                            }
                        }
                    }
                }
            }
            collectByFieldValue(node.children, compNameLower, fieldName, targetValueLower, matches);
        }
    }

    // ========================================================================
    // COMMAND: validate
    // ========================================================================

    private Object buildValidateResult(SceneData sceneData, List<NodeInfo> roots) {
        List<GameObjectData> allObjects = sceneData.getGameObjects();
        if (allObjects == null) allObjects = List.of();

        List<Map<String, String>> issues = new ArrayList<>();

        // Collect all IDs
        Map<String, Integer> idCounts = new LinkedHashMap<>();
        Set<String> allIds = new java.util.HashSet<>();
        for (GameObjectData go : allObjects) {
            String id = go.getId();
            idCounts.merge(id, 1, Integer::sum);
            allIds.add(id);
        }

        for (GameObjectData go : allObjects) {
            // Duplicate IDs
            if (idCounts.getOrDefault(go.getId(), 0) > 1) {
                issues.add(issue("DUPLICATE_ID",
                        "Node '" + go.getName() + "' has duplicate id '" + go.getId() + "'"));
            }

            // Orphan parentId
            String parentId = go.getParentId();
            if (parentId != null && !parentId.isEmpty() && !allIds.contains(parentId)) {
                issues.add(issue("ORPHAN_PARENT",
                        "Node '" + go.getName() + "' (id=" + go.getId()
                                + ") references parentId '" + parentId + "' which doesn't exist"));
            }

            // Missing prefab files
            if (go.isPrefabInstance()) {
                String prefabPath = go.getPrefab();
                if (prefabPath != null && !prefabPath.isEmpty()) {
                    if (!Files.exists(Path.of(prefabPath))) {
                        issues.add(issue("MISSING_PREFAB",
                                "Node '" + go.getName() + "' references prefab '" + prefabPath + "' which doesn't exist"));
                    }
                }
            }

            // Missing Transform/UITransform (scratch entities only)
            if (go.isScratchEntity() && go.getComponents() != null) {
                boolean hasTransform = go.getComponents().stream()
                        .anyMatch(c -> c instanceof Transform || c instanceof UITransform);
                if (!hasTransform) {
                    issues.add(issue("MISSING_TRANSFORM",
                            "Node '" + go.getName() + "' (id=" + go.getId()
                                    + ") has no Transform or UITransform component"));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalNodes", allObjects.size());
        result.put("issueCount", issues.size());
        result.put("issues", issues);
        if (issues.isEmpty()) {
            result.put("summary", "No issues found — scene is valid");
        }
        return result;
    }

    private Map<String, String> issue(String type, String message) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("message", message);
        return entry;
    }

    // ========================================================================
    // COMMAND: refs
    // ========================================================================

    private Object buildRefsResult(List<NodeInfo> roots, String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return Map.of("error", "refs command requires an argument: refs:<name-or-id>");
        }

        NodeInfo found = findNode(roots, nameOrId);
        if (found == null) {
            return Map.of("error", "Node not found: " + nameOrId);
        }

        if (found.data.isPrefabInstance()) {
            return Map.of("error", "Node '" + nameOrId + "' is a prefab instance. "
                    + "refs only works on scratch entities with deserialized components.");
        }

        List<Component> components = found.data.getComponents();
        if (components == null || components.isEmpty()) {
            return Map.of("name", found.data.getName(), "references", List.of());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", found.data.getName());
        result.put("id", found.data.getId());
        result.put("path", found.path);

        List<Map<String, Object>> allRefs = new ArrayList<>();
        for (Component comp : components) {
            String fullType = comp.getClass().getName();
            ComponentMeta meta = ComponentRegistry.getByClassName(fullType);
            if (meta == null || meta.componentReferences().isEmpty()) continue;

            for (ComponentReferenceMeta refMeta : meta.componentReferences()) {
                Map<String, Object> refEntry = new LinkedHashMap<>();
                refEntry.put("ownerComponent", comp.getClass().getSimpleName());
                refEntry.put("fieldName", refMeta.fieldName());
                refEntry.put("targetType", refMeta.componentType().getSimpleName());
                refEntry.put("source", refMeta.source().name());
                refEntry.put("required", refMeta.required());
                refEntry.put("isList", refMeta.isList());

                // For KEY refs, show the stored key
                if (refMeta.isKeySource()) {
                    if (refMeta.isList()) {
                        List<String> keys = ComponentReferenceResolver.getPendingKeyList(comp, refMeta.fieldName());
                        refEntry.put("keys", keys);
                    } else {
                        String key = ComponentReferenceResolver.getPendingKey(comp, refMeta.fieldName());
                        refEntry.put("key", key.isEmpty() ? null : key);
                    }
                }

                allRefs.add(refEntry);
            }
        }

        result.put("references", allRefs);
        return result;
    }

    // ========================================================================
    // COMMAND: stats
    // ========================================================================

    private Object buildStatsResult(String sceneName, List<NodeInfo> roots) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sceneName", sceneName);

        // Counters
        int[] counts = {0, 0, 0}; // total, prefab, scratch
        int[] maxDepth = {0};
        Map<String, Integer> componentCounts = new TreeMap<>();

        collectStats(roots, 0, counts, maxDepth, componentCounts);

        result.put("totalNodes", counts[0]);
        result.put("prefabInstances", counts[1]);
        result.put("scratchEntities", counts[2]);
        result.put("rootNodes", roots.size());
        result.put("maxDepth", maxDepth[0]);
        result.put("componentDistribution", componentCounts);

        return result;
    }

    private void collectStats(List<NodeInfo> nodes, int depth,
                               int[] counts, int[] maxDepth,
                               Map<String, Integer> componentCounts) {
        for (NodeInfo node : nodes) {
            counts[0]++;
            if (node.data.isPrefabInstance()) {
                counts[1]++;
            } else {
                counts[2]++;
            }
            if (depth > maxDepth[0]) {
                maxDepth[0] = depth;
            }

            // Count component types
            for (String compName : getComponentSimpleNames(node.data)) {
                if (!"[prefab]".equals(compName)) {
                    componentCounts.merge(compName, 1, Integer::sum);
                }
            }

            collectStats(node.children, depth + 1, counts, maxDepth, componentCounts);
        }
    }

    // ========================================================================
    // SHARED HELPERS
    // ========================================================================

    /**
     * Builds a match entry for find/search results, including position info.
     */
    private Map<String, Object> buildMatchEntry(NodeInfo node) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", node.data.getName());
        entry.put("id", node.data.getId());
        entry.put("path", node.path);
        entry.put("active", node.data.isActive());
        entry.put("components", getComponentSimpleNames(node.data));

        // Include position from Transform or UITransform
        Object position = extractPosition(node.data);
        if (position != null) {
            entry.put("position", position);
        }

        return entry;
    }

    private Object extractPosition(GameObjectData go) {
        if (go.isPrefabInstance()) {
            // Check overrides for position
            float[] pos = go.getPosition();
            if (pos[0] != 0 || pos[1] != 0 || pos[2] != 0) {
                return toSerializable(pos);
            }
            return null;
        }

        List<Component> components = go.getComponents();
        if (components == null) return null;

        for (Component comp : components) {
            if (comp instanceof Transform t) {
                var pos = t.getPosition();
                return List.of(pos.x, pos.y, pos.z);
            }
            if (comp instanceof UITransform uit) {
                // UITransform uses localPosition from its base
                try {
                    var field = UITransform.class.getDeclaredField("localPosition");
                    field.setAccessible(true);
                    Object val = field.get(uit);
                    return toSerializable(val);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private List<String> getComponentSimpleNames(GameObjectData go) {
        if (go.isPrefabInstance()) {
            Map<String, Map<String, Object>> overrides = go.getComponentOverrides();
            if (overrides == null || overrides.isEmpty()) {
                return List.of("[prefab]");
            }
            List<String> names = new ArrayList<>();
            names.add("[prefab]");
            for (String key : overrides.keySet()) {
                int lastDot = key.lastIndexOf('.');
                names.add(lastDot >= 0 ? key.substring(lastDot + 1) : key);
            }
            return names;
        }

        List<Component> components = go.getComponents();
        if (components == null) return List.of();

        return components.stream()
                .map(c -> c.getClass().getSimpleName())
                .toList();
    }

    private String getPrefabRef(GameObjectData go) {
        return go.getPrefab() != null ? go.getPrefab() : go.getPrefabId();
    }

    private NodeInfo findNode(List<NodeInfo> nodes, String nameOrId) {
        for (NodeInfo node : nodes) {
            if (nameOrId.equals(node.data.getName()) || nameOrId.equals(node.data.getId())) {
                return node;
            }
            NodeInfo found = findNode(node.children, nameOrId);
            if (found != null) return found;
        }
        return null;
    }

    private NodeInfo findNodeInList(List<NodeInfo> nodes, String id) {
        for (NodeInfo node : nodes) {
            if (id.equals(node.data.getId()) || id.equals(node.data.getName())) {
                return node;
            }
            NodeInfo found = findNodeInList(node.children, id);
            if (found != null) return found;
        }
        return null;
    }

    private Object getFieldValue(Component comp, String fieldName) {
        String fullType = comp.getClass().getName();
        ComponentMeta meta = ComponentRegistry.getByClassName(fullType);
        if (meta == null) return null;

        for (FieldMeta fm : meta.fields()) {
            if (fm.name().equals(fieldName)) {
                try {
                    fm.field().setAccessible(true);
                    return toSerializable(fm.field().get(comp));
                } catch (IllegalAccessException e) {
                    return "<access error>";
                }
            }
        }
        return null;
    }

    private Component findComponentByType(GameObjectData go, String fullType) {
        if (go.getComponents() == null) return null;
        for (Component comp : go.getComponents()) {
            if (comp.getClass().getName().equals(fullType)) return comp;
        }
        return null;
    }

    // ========================================================================
    // PREFAB LOADING
    // ========================================================================

    private JsonPrefab loadPrefab(String prefabPath) throws IOException {
        Path path = Path.of(prefabPath);
        if (!Files.exists(path)) return null;
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return Serializer.fromJson(json, JsonPrefab.class);
    }

    private GameObjectData findPrefabRoot(JsonPrefab prefab) {
        if (prefab.getGameObjects() == null) return null;
        for (GameObjectData go : prefab.getGameObjects()) {
            if (go.getParentId() == null || go.getParentId().isEmpty()) {
                return go;
            }
        }
        return prefab.getGameObjects().isEmpty() ? null : prefab.getGameObjects().get(0);
    }

    // ========================================================================
    // SERIALIZATION HELPERS
    // ========================================================================

    private Object toSerializable(Object value) {
        if (value == null) return null;

        // Primitives and strings pass through
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }

        // Enums -> string
        if (value instanceof Enum<?> e) {
            return e.name();
        }

        // Arrays
        if (value instanceof float[] arr) {
            List<Float> list = new ArrayList<>();
            for (float f : arr) list.add(f);
            return list;
        }
        if (value instanceof int[] arr) {
            List<Integer> list = new ArrayList<>();
            for (int i : arr) list.add(i);
            return list;
        }

        // Vectors
        if (value instanceof Vector2f v) {
            return List.of(v.x, v.y);
        }
        if (value instanceof Vector3f v) {
            return List.of(v.x, v.y, v.z);
        }
        if (value instanceof Vector4f v) {
            return List.of(v.x, v.y, v.z, v.w);
        }

        // Lists
        if (value instanceof List<?> list) {
            return list.stream().map(this::toSerializable).toList();
        }

        // Maps
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), toSerializable(entry.getValue()));
            }
            return result;
        }

        // Component references -> just show class name
        if (value instanceof Component comp) {
            return "[ref:" + comp.getClass().getSimpleName() + "]";
        }

        // Fallback: toString
        return value.toString();
    }

    // ========================================================================
    // DATA STRUCTURES
    // ========================================================================

    private static class NodeInfo {
        GameObjectData data;
        String path;
        List<NodeInfo> children;
    }

    // ========================================================================
    // STUB ASSET CONTEXT
    // ========================================================================

    private static class StubAssetContext implements AssetContext {
        @Override public <T> T load(String path) { return null; }
        @Override public <T> T load(String path, LoadOptions loadOptions) { return null; }
        @Override public <T> T load(String path, Class<T> type) { return null; }
        @Override public <T> T load(String path, LoadOptions loadOptions, Class<T> type) { return null; }
        @Override public <T> T get(String path) { return null; }
        @Override public <T> List<T> getAll(Class<T> type) { return Collections.emptyList(); }
        @Override public boolean isLoaded(String path) { return false; }
        @Override public Set<String> getLoadedPaths() { return Collections.emptySet(); }
        @Override public String getPathForResource(Object resource) { return null; }
        @Override public void persist(Object resource) {}
        @Override public void persist(Object resource, String path) {}
        @Override public void persist(Object resource, String path, LoadOptions options) {}
        @Override public AssetsConfiguration configure() { return null; }
        @Override public CacheStats getStats() { return null; }
        @Override public List<String> scanByType(Class<?> type) { return Collections.emptyList(); }
        @Override public List<String> scanByType(Class<?> type, String directory) { return Collections.emptyList(); }
        @Override public List<String> scanAll() { return Collections.emptyList(); }
        @Override public List<String> scanAll(String directory) { return Collections.emptyList(); }
        @Override public void setAssetRoot(String assetRoot) {}
        @Override public String getAssetRoot() { return null; }
        @Override public ResourceCache getCache() { return null; }
        @Override public void setErrorMode(ErrorMode errorMode) {}
        @Override public void setStatisticsEnabled(boolean enableStatistics) {}
        @Override public String getRelativePath(String fullPath) { return null; }
        @Override public Sprite getPreviewSprite(String path, Class<?> type) { return null; }
        @Override public Class<?> getTypeForPath(String path) { return null; }
        @Override public void registerResource(Object resource, String path) {}
        @Override public void unregisterResource(Object resource) {}
        @Override public boolean isAssetType(Class<?> type) { return false; }
        @Override public boolean canInstantiate(Class<?> type) { return false; }
        @Override public EditorGameObject instantiate(String path, Class<?> type, Vector3f position) { return null; }
        @Override public boolean canSave(Class<?> type) { return false; }
        @Override public EditorPanelType getEditorPanelType(Class<?> type) { return null; }
        @Override public Set<EditorCapability> getEditorCapabilities(Class<?> type) { return Collections.emptySet(); }
        @Override public String getIconCodepoint(Class<?> type) { return null; }
    }
}
