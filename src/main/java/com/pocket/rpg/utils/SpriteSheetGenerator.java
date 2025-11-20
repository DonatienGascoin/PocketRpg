package com.pocket.rpg.utils;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility class to generate a test sprite sheet.
 * Creates a 4x4 grid of animated frames (128x128 pixels total).
 */
public class SpriteSheetGenerator {

    private static final int SPRITE_SIZE = 32;
    private static final int GRID_SIZE = 4;
    private static final int SHEET_SIZE = SPRITE_SIZE * GRID_SIZE; // 128x128

    /**
     * Creates a sprite sheet with a bouncing ball animation.
     *
     * @param filepath Output file path
     * @throws IOException if file writing fails
     */
    public static void createBouncingBallSheet(String filepath) throws IOException {
        BufferedImage sheet = new BufferedImage(SHEET_SIZE, SHEET_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Clear background (transparent)
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, SHEET_SIZE, SHEET_SIZE);
        g.setComposite(AlphaComposite.Src);

        // Generate 16 frames of animation
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = col * SPRITE_SIZE;
                int y = row * SPRITE_SIZE;

                drawFrame(g, x, y, frameIndex);
            }
        }

        g.dispose();

        // Save file
        File file = new File(filepath);
        file.getParentFile().mkdirs();
        ImageIO.write(sheet, "PNG", file);
        System.out.println("Sprite sheet created: " + filepath);
    }

    /**
     * Draws a single animation frame.
     *
     * @param g           Graphics context
     * @param x           X position in sheet
     * @param y           Y position in sheet
     * @param frameIndex  Frame number (0-15)
     */
    private static void drawFrame(Graphics2D g, int x, int y, int frameIndex) {
        // Calculate animation progress (0-1)
        float progress = frameIndex / 15.0f;

        // Bouncing ball animation
        float bounceHeight = (float) Math.abs(Math.sin(progress * Math.PI * 2)) * 12;

        // Ball position within sprite
        int centerX = x + SPRITE_SIZE / 2;
        int centerY = y + SPRITE_SIZE / 2 - (int) bounceHeight;
        int radius = 10;

        // Shadow
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(x + 6, y + SPRITE_SIZE - 8, radius * 2, 4);

        // Ball with gradient
        GradientPaint gradient = new GradientPaint(
                centerX - radius / 2, centerY - radius / 2, new Color(255, 100, 50),
                centerX + radius / 2, centerY + radius / 2, new Color(200, 50, 20)
        );
        g.setPaint(gradient);
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Highlight
        g.setColor(new Color(255, 200, 150, 200));
        g.fillOval(centerX - radius / 2, centerY - radius / 2, radius / 2, radius / 2);

        // Frame number (for debugging)
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 8));
        g.drawString(String.valueOf(frameIndex), x + 2, y + 10);
    }

    /**
     * Creates a sprite sheet with colored squares (simpler).
     *
     * @param filepath Output file path
     * @throws IOException if file writing fails
     */
    public static void createColoredSquaresSheet(String filepath) throws IOException {
        BufferedImage sheet = new BufferedImage(SHEET_SIZE, SHEET_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();

        // Generate 16 frames with different colors
        Color[] colors = {
                Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN,
                Color.CYAN, Color.BLUE, Color.MAGENTA, Color.PINK,
                new Color(255, 100, 100), new Color(100, 255, 100),
                new Color(100, 100, 255), new Color(255, 255, 100),
                new Color(255, 100, 255), new Color(100, 255, 255),
                new Color(200, 200, 200), new Color(100, 100, 100)
        };

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int frameIndex = row * GRID_SIZE + col;
                int x = col * SPRITE_SIZE;
                int y = row * SPRITE_SIZE;

                // Draw colored square
                g.setColor(colors[frameIndex]);
                g.fillRect(x + 2, y + 2, SPRITE_SIZE - 4, SPRITE_SIZE - 4);

                // Border
                g.setColor(Color.BLACK);
                g.drawRect(x + 2, y + 2, SPRITE_SIZE - 4, SPRITE_SIZE - 4);

                // Frame number
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 10));
                g.drawString(String.valueOf(frameIndex), x + SPRITE_SIZE / 2 - 5, y + SPRITE_SIZE / 2 + 5);
            }
        }

        g.dispose();

        // Save file
        File file = new File(filepath);
        file.getParentFile().mkdirs();
        ImageIO.write(sheet, "PNG", file);
        System.out.println("Sprite sheet created: " + filepath);
    }

    /**
     * Main method to generate test sprite sheets.
     */
    public static void main(String[] args) {
        try {
            // Create assets directory
            new File("assets").mkdirs();

            // Generate sprite sheets
            createBouncingBallSheet("assets/spritesheet.png");
            createColoredSquaresSheet("assets/spritesheet_simple.png");

            System.out.println("\nAll sprite sheets created successfully!");
            System.out.println("Sprite sheet info:");
            System.out.println("  - Size: 128x128 pixels");
            System.out.println("  - Grid: 4x4 (16 frames)");
            System.out.println("  - Sprite size: 32x32 pixels each");
            System.out.println("\nYou can now run GameWindow to see the animation!");

        } catch (IOException e) {
            System.err.println("Failed to create sprite sheets: " + e.getMessage());
            e.printStackTrace();
        }
    }
}