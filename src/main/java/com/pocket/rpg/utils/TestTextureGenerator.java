package com.pocket.rpg.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility class to generate test textures programmatically.
 * Useful for testing the rendering system without external image files.
 */
public class TestTextureGenerator {

    /**
     * Creates a simple colored square with a border.
     *
     * @param filepath   Output file path (e.g., "gameData/assets/player.png")
     * @param size       Image size in pixels
     * @param color      Fill color
     * @param borderColor Border color
     * @throws IOException if file writing fails
     */
    public static void createColoredSquare(String filepath, int size, Color color, Color borderColor)
            throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Enable antialiasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill with color
        g.setColor(color);
        g.fillRect(0, 0, size, size);

        // Draw border
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(4));
        g.drawRect(2, 2, size - 4, size - 4);

        // Draw a cross in the middle
        g.drawLine(size / 2, 10, size / 2, size - 10);
        g.drawLine(10, size / 2, size - 10, size / 2);

        g.dispose();

        // Create directory if it doesn't exist
        File file = new File(filepath);
        file.getParentFile().mkdirs();

        // Save as PNG
        ImageIO.write(image, "PNG", file);
        System.out.println("Test texture created: " + filepath);
    }

    /**
     * Creates a checkerboard pattern.
     *
     * @param filepath    Output file path
     * @param size        Image size in pixels
     * @param squareSize  Size of each checkerboard square
     * @param color1      First color
     * @param color2      Second color
     * @throws IOException if file writing fails
     */
    public static void createCheckerboard(String filepath, int size, int squareSize,
                                          Color color1, Color color2) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        for (int y = 0; y < size; y += squareSize) {
            for (int x = 0; x < size; x += squareSize) {
                int row = y / squareSize;
                int col = x / squareSize;
                g.setColor(((row + col) % 2 == 0) ? color1 : color2);
                g.fillRect(x, y, squareSize, squareSize);
            }
        }

        g.dispose();

        File file = new File(filepath);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", file);
        System.out.println("Test texture created: " + filepath);
    }

    /**
     * Creates a gradient circle.
     *
     * @param filepath Output file path
     * @param size     Image size in pixels
     * @param color    Circle color
     * @throws IOException if file writing fails
     */
    public static void createGradientCircle(String filepath, int size, Color color) throws IOException {
        /*BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Create radial gradient
        Point2D center = new Point2D() {
        }.Float(size / 2f, size / 2f);
        float radius = size / 2f;
        float[] dist = {0.0f, 1.0f};
        Color[] colors = {color, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)};
        RadialGradientPaint gradient = new RadialGradientPaint(center, radius, dist, colors);

        g.setPaint(gradient);
        g.fillOval(0, 0, size, size);

        g.dispose();

        File file = new File(filepath);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", file);
        System.out.println("Test texture created: " + filepath);*/
    }

    /**
     * Main method to generate test textures.
     */
    public static void main(String[] args) {
        try {
            // Create assets directory
            new File("assets").mkdirs();

            // Generate test textures
            createColoredSquare("gameData/assets/player.png", 64,
                    new Color(255, 100, 50), Color.BLACK);

            createCheckerboard("gameData/assets/background.png", 256, 32,
                    new Color(100, 100, 100), new Color(150, 150, 150));

            createGradientCircle("gameData/assets/particle.png", 64,
                    new Color(255, 200, 100));

            System.out.println("\nAll test textures created successfully!");
            System.out.println("You can now run GameWindow.");

        } catch (IOException e) {
            System.err.println("Failed to create test textures: " + e.getMessage());
            e.printStackTrace();
        }
    }
}