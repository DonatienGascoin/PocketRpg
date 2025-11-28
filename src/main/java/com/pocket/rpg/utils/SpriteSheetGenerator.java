package com.pocket.rpg.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility class to generate test sprite sheets demonstrating different layouts:
 * - No spacing/offset (tightly packed)
 * - Uniform spacing
 * - Different X/Y spacing
 * - With margins/offsets
 */
public class SpriteSheetGenerator {

    private static final int SPRITE_SIZE = 32;
    private static final int GRID_SIZE = 4;

    /**
     * Creates a tightly packed sprite sheet (no spacing, no offset).
     * Perfect for testing: new SpriteSheet(texture, 32, 32)
     */
    public static void createTightlyPackedSheet(String filepath) throws IOException {
        int sheetSize = SPRITE_SIZE * GRID_SIZE; // 128x128
        BufferedImage sheet = new BufferedImage(sheetSize, sheetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Clear background
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, sheetSize, sheetSize);

        // Generate 16 frames with bouncing ball
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = col * SPRITE_SIZE;
                int y = row * SPRITE_SIZE;
                drawBouncingBall(g, x, y, SPRITE_SIZE, frameIndex);
            }
        }

        g.dispose();
        saveImage(sheet, filepath);
        System.out.println("✓ Tightly packed sheet (128x128, no spacing): " + filepath);
    }

    /**
     * Creates a sprite sheet with uniform spacing.
     * Perfect for testing: new SpriteSheet(texture, 32, 32, 2)
     */
    public static void createUniformSpacingSheet(String filepath) throws IOException {
        int spacing = 2;
        int sheetSize = SPRITE_SIZE * GRID_SIZE + spacing * (GRID_SIZE - 1); // 134x134
        BufferedImage sheet = new BufferedImage(sheetSize, sheetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background with visible spacing
        g.setColor(new Color(50, 50, 50));
        g.fillRect(0, 0, sheetSize, sheetSize);

        // Generate 16 frames with spacing
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = col * (SPRITE_SIZE + spacing);
                int y = row * (SPRITE_SIZE + spacing);
                drawColoredSquare(g, x, y, SPRITE_SIZE, frameIndex);
            }
        }

        g.dispose();
        saveImage(sheet, filepath);
        System.out.println("✓ Uniform spacing sheet (134x134, 2px spacing): " + filepath);
    }

    /**
     * Creates a sprite sheet with different X/Y spacing.
     * Perfect for testing: new SpriteSheet(texture, 32, 32, 4, 2, 0, 0)
     */
    public static void createDifferentXYSpacingSheet(String filepath) throws IOException {
        int spacingX = 4;
        int spacingY = 2;
        int sheetWidth = SPRITE_SIZE * GRID_SIZE + spacingX * (GRID_SIZE - 1);  // 140
        int sheetHeight = SPRITE_SIZE * GRID_SIZE + spacingY * (GRID_SIZE - 1); // 134
        BufferedImage sheet = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background with grid pattern to show spacing
        g.setColor(new Color(60, 60, 60));
        g.fillRect(0, 0, sheetWidth, sheetHeight);

        // Generate 16 frames with different spacing
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = col * (SPRITE_SIZE + spacingX);
                int y = row * (SPRITE_SIZE + spacingY);
                drawPulsingCircle(g, x, y, SPRITE_SIZE, frameIndex);
            }
        }

        g.dispose();
        saveImage(sheet, filepath);
        System.out.println("✓ X/Y spacing sheet (140x134, 4px/2px spacing): " + filepath);
    }

    /**
     * Creates a sprite sheet with margin/offset.
     * Perfect for testing: new SpriteSheet(texture, 32, 32, 0, 0, 8, 8)
     */
    public static void createMarginSheet(String filepath) throws IOException {
        int margin = 8;
        int sheetSize = SPRITE_SIZE * GRID_SIZE + margin * 2; // 144x144
        BufferedImage sheet = new BufferedImage(sheetSize, sheetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background with decorative border
        g.setColor(new Color(100, 100, 120));
        g.fillRect(0, 0, sheetSize, sheetSize);
        g.setColor(new Color(80, 80, 100));
        g.fillRect(margin / 2, margin / 2, sheetSize - margin, sheetSize - margin);

        // Generate 16 frames with offset
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = margin + col * SPRITE_SIZE;
                int y = margin + row * SPRITE_SIZE;
                drawRotatingSquare(g, x, y, SPRITE_SIZE, frameIndex);
            }
        }

        g.dispose();
        saveImage(sheet, filepath);
        System.out.println("✓ Margin sheet (144x144, 8px margin): " + filepath);
    }

    /**
     * Creates a complex sprite sheet with both spacing and offset.
     * Perfect for testing: new SpriteSheet(texture, 32, 32, 2, 4, 8, 8)
     */
    public static void createComplexSheet(String filepath) throws IOException {
        int spacingX = 2;
        int spacingY = 4;
        int offsetX = 8;
        int offsetY = 8;
        int sheetWidth = SPRITE_SIZE * GRID_SIZE + spacingX * (GRID_SIZE - 1) + offsetX * 2;  // 150
        int sheetHeight = SPRITE_SIZE * GRID_SIZE + spacingY * (GRID_SIZE - 1) + offsetY * 2; // 154
        BufferedImage sheet = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background with fancy border
        g.setColor(new Color(40, 40, 50));
        g.fillRect(0, 0, sheetWidth, sheetHeight);
        g.setColor(new Color(60, 60, 80));
        g.fillRect(offsetX / 2, offsetY / 2, sheetWidth - offsetX, sheetHeight - offsetY);

        // Generate 16 frames
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = offsetX + col * (SPRITE_SIZE + spacingX);
                int y = offsetY + row * (SPRITE_SIZE + spacingY);
                drawStar(g, x, y, SPRITE_SIZE, frameIndex);
            }
        }

        g.dispose();
        saveImage(sheet, filepath);
        System.out.println("✓ Complex sheet (150x154, 2/4px spacing, 8/8px offset): " + filepath);
    }

    // ==================== Drawing Methods ====================

    private static void drawBouncingBall(Graphics2D g, int x, int y, int size, int frame) {
        float progress = frame / 15.0f;
        float bounceHeight = (float) Math.abs(Math.sin(progress * Math.PI * 2)) * 12;

        int centerX = x + size / 2;
        int centerY = y + size / 2 - (int) bounceHeight;
        int radius = 10;

        // Shadow
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(x + 6, y + size - 8, radius * 2, 4);

        // Ball
        GradientPaint gradient = new GradientPaint(
                centerX - radius / 2, centerY - radius / 2, new Color(255, 100, 50),
                centerX + radius / 2, centerY + radius / 2, new Color(200, 50, 20)
        );
        g.setPaint(gradient);
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Highlight
        g.setColor(new Color(255, 200, 150, 200));
        g.fillOval(centerX - radius / 2, centerY - radius / 2, radius / 2, radius / 2);

        // Frame number
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        g.drawString(String.valueOf(frame), x + 2, y + 10);
    }

    private static void drawColoredSquare(Graphics2D g, int x, int y, int size, int frame) {
        Color[] colors = {
                new Color(255, 100, 100), new Color(255, 150, 100), new Color(255, 200, 100), new Color(255, 255, 100),
                new Color(200, 255, 100), new Color(150, 255, 100), new Color(100, 255, 100), new Color(100, 255, 150),
                new Color(100, 255, 200), new Color(100, 255, 255), new Color(100, 200, 255), new Color(100, 150, 255),
                new Color(100, 100, 255), new Color(150, 100, 255), new Color(200, 100, 255), new Color(255, 100, 255)
        };

        g.setColor(colors[frame]);
        g.fillRect(x, y, size, size);
        g.setColor(new Color(0, 0, 0, 100));
        g.drawRect(x, y, size - 1, size - 1);

        // Frame number
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        String text = String.valueOf(frame);
        FontMetrics fm = g.getFontMetrics();
        int textX = x + (size - fm.stringWidth(text)) / 2;
        int textY = y + (size + fm.getAscent()) / 2;
        g.drawString(text, textX, textY);
    }

    private static void drawPulsingCircle(Graphics2D g, int x, int y, int size, int frame) {
        float progress = frame / 15.0f;
        float scale = 0.7f + (float) Math.abs(Math.sin(progress * Math.PI * 2)) * 0.3f;

        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = (int) (size / 2 * scale);

        // Outer glow
        for (int i = 0; i < 3; i++) {
            int alpha = 30 - i * 10;
            g.setColor(new Color(100, 200, 255, alpha));
            g.fillOval(centerX - radius - i * 2, centerY - radius - i * 2,
                    (radius + i * 2) * 2, (radius + i * 2) * 2);
        }

        // Circle
        g.setColor(new Color(100, 200, 255));
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Frame number
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        g.drawString(String.valueOf(frame), x + 2, y + 10);
    }

    private static void drawRotatingSquare(Graphics2D g, int x, int y, int size, int frame) {
        float progress = frame / 15.0f;
        double angle = progress * Math.PI * 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x + size / 2, y + size / 2);
        g2.rotate(angle);

        int halfSize = size / 3;

        // Square
        GradientPaint gradient = new GradientPaint(
                -halfSize, -halfSize, new Color(255, 150, 50),
                halfSize, halfSize, new Color(255, 100, 0)
        );
        g2.setPaint(gradient);
        g2.fillRect(-halfSize, -halfSize, halfSize * 2, halfSize * 2);

        // Border
        g2.setColor(new Color(200, 80, 0));
        g2.drawRect(-halfSize, -halfSize, halfSize * 2 - 1, halfSize * 2 - 1);

        g2.dispose();

        // Frame number
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        g.drawString(String.valueOf(frame), x + 2, y + 10);
    }

    private static void drawStar(Graphics2D g, int x, int y, int size, int frame) {
        float progress = frame / 15.0f;
        float scale = 0.8f + (float) Math.abs(Math.sin(progress * Math.PI * 4)) * 0.2f;

        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = (int) (size / 3 * scale);

        // Create star shape
        int[] xPoints = new int[10];
        int[] yPoints = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5 - Math.PI / 2;
            int r = (i % 2 == 0) ? radius : radius / 2;
            xPoints[i] = centerX + (int) (r * Math.cos(angle));
            yPoints[i] = centerY + (int) (r * Math.sin(angle));
        }

        // Star with gradient
        g.setColor(new Color(255, 220, 100));
        g.fillPolygon(xPoints, yPoints, 10);
        g.setColor(new Color(200, 150, 0));
        g.drawPolygon(xPoints, yPoints, 10);

        // Frame number
        g.setColor(new Color(100, 50, 0));
        g.setFont(new Font("Arial", Font.BOLD, 8));
        String text = String.valueOf(frame);
        FontMetrics fm = g.getFontMetrics();
        int textX = x + (size - fm.stringWidth(text)) / 2;
        int textY = y + (size + fm.getAscent()) / 2;
        g.drawString(text, textX, textY);
    }

    private static void saveImage(BufferedImage image, String filepath) throws IOException {
        File file = new File(filepath);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", file);
    }

    /**
     * Main method to generate all test sprite sheets.
     */
    public static void main(String[] args) {
        try {
            System.out.println("Generating sprite sheets...\n");

            createTightlyPackedSheet("gameData/assets/sheet_tight.png");
            createUniformSpacingSheet("gameData/assets/sheet_spacing.png");
            createDifferentXYSpacingSheet("gameData/assets/sheet_xy_spacing.png");
            createMarginSheet("gameData/assets/sheet_margin.png");
            createComplexSheet("gameData/assets/sheet_complex.png");

            System.out.println("\n✓ All 5 sprite sheets created successfully!");
            System.out.println("\nSprite sheet types:");
            System.out.println("  1. sheet_tight.png       - No spacing, no offset (128x128)");
            System.out.println("  2. sheet_spacing.png     - Uniform 2px spacing (134x134)");
            System.out.println("  3. sheet_xy_spacing.png  - 4px/2px X/Y spacing (140x134)");
            System.out.println("  4. sheet_margin.png      - 8px margin/offset (144x144)");
            System.out.println("  5. sheet_complex.png     - 2/4px spacing + 8/8px offset (150x154)");
            System.out.println("\nRun ExampleScene to see them in action!");

        } catch (IOException e) {
            System.err.println("Failed to create sprite sheets: " + e.getMessage());
            e.printStackTrace();
        }
    }
}