package com.pocket.rpg.editor.utils;

/**
 * Fuzzy string matching utility for search functionality.
 */
public class FuzzyMatcher {

    /**
     * Matches query against target with fuzzy logic:
     * 1. Contains match (fast path)
     * 2. Word prefix match: "sprite r" matches "Sprite Renderer"
     * 3. Subsequence match: "spritr" matches "Sprite Renderer"
     */
    public static boolean matches(String query, String target) {
        String q = query.toLowerCase();
        String t = target.toLowerCase();

        // Fast path: simple contains
        if (t.contains(q)) return true;

        // Word prefix matching for queries with spaces
        if (q.contains(" ")) {
            return wordPrefixMatch(q, t);
        }

        // Subsequence matching for queries without spaces
        return subsequenceMatch(q, t);
    }

    private static boolean wordPrefixMatch(String query, String target) {
        String[] queryWords = query.split("\\s+");
        String[] targetWords = target.split("\\s+");

        for (String qWord : queryWords) {
            if (qWord.isEmpty()) continue;
            boolean found = false;
            for (String tWord : targetWords) {
                if (tWord.startsWith(qWord)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean subsequenceMatch(String query, String target) {
        int qi = 0;
        for (int ti = 0; ti < target.length() && qi < query.length(); ti++) {
            if (query.charAt(qi) == target.charAt(ti)) {
                qi++;
            }
        }
        return qi == query.length();
    }
}
