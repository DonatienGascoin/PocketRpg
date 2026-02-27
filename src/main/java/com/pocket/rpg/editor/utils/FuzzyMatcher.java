package com.pocket.rpg.editor.utils;

/**
 * Fuzzy string matching utility for search functionality.
 */
public class FuzzyMatcher {

    /**
     * Scores how well query matches target (higher = better match).
     * Returns 0 if no match.
     * Scoring: exact match = 100, starts-with = 80, contains = 60,
     * word prefix = 40, subsequence = 20.
     */
    public static int score(String query, String target) {
        String q = query.toLowerCase();
        String t = target.toLowerCase();

        if (t.equals(q)) return 100;
        if (t.startsWith(q)) return 80;

        // Check filename portion for starts-with (target may be a path)
        int lastSlash = t.lastIndexOf('/');
        String filename = lastSlash >= 0 ? t.substring(lastSlash + 1) : t;
        if (filename.startsWith(q)) return 75;
        if (filename.contains(q)) return 65;

        if (t.contains(q)) return 60;
        if (q.contains(" ") && wordPrefixMatch(q, t)) return 40;
        if (subsequenceMatch(q, t)) return 20;
        return 0;
    }

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
