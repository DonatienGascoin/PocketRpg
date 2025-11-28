package com.pocket.rpg.utils;

public class LogUtils {

    public static String buildBox(String... lines) {
        // Find longest line
        int maxLength = 0;
        for (String line : lines) {
            if (line.length() > maxLength) {
                maxLength = line.length();
            }
        }

        String top = "╔" + "═".repeat(maxLength + 2) + "╗\n";
        String mid = "╠" + "═".repeat(maxLength + 2) + "╣\n";
        String bottom = "╚" + "═".repeat(maxLength + 2) + "╝";

        StringBuilder sb = new StringBuilder();
        sb.append(top);

        // First line
        if (lines.length > 0) {
            sb.append("║ ").append(padRight(lines[0], maxLength)).append(" ║\n");
        }

        // Middle separator if multiple lines
        if (lines.length > 1) {
            sb.append(mid);
        }

        // Remaining lines
        for (int i = 1; i < lines.length; i++) {
            sb.append("║ ").append(padRight(lines[i], maxLength)).append(" ║\n");
        }

        sb.append(bottom);

        return sb.toString();
    }

    // Padding helper
    private static String padRight(String text, int length) {
        return String.format("%-" + length + "s", text);
    }
}
