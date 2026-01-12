package com.pocket.rpg.rendering.postfx;

/**
 * Metadata for a PostEffect implementation.
 */
public record PostEffectMeta(
        String className,
        String simpleName,
        String displayName,
        Class<? extends PostEffect> effectClass,
        boolean hasNoArgConstructor
) {
    /**
     * Converts class name to display name.
     * "BloomEffect" -> "Bloom"
     * "ChromaticAberrationEffect" -> "Chromatic Aberration"
     */
    public static String toDisplayName(String simpleName) {
        String name = simpleName;
        if (name.endsWith("Effect")) {
            name = name.substring(0, name.length() - 6);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString();
    }
}
