package com.pocket.rpg.transitions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Generates grayscale PNG images for luma wipe transitions.
 * Each image is 256x256, with brightness representing the wipe order:
 * black pixels (0) reveal first, white pixels (255) reveal last.
 * <p>
 * Run as a standalone utility to regenerate the transition images.
 */
public class LumaImageGenerator {

    private static final int SIZE = 256;
    private static final String OUTPUT_DIR = "gameData/assets/sprites/transitions";

    public static void main(String[] args) throws IOException {
        generateAll();
        System.out.println("All luma transition images generated in " + OUTPUT_DIR);
    }

    public static void generateAll() throws IOException {
        File dir = new File(OUTPUT_DIR);
        dir.mkdirs();

        generateGradient("wipe_left.png", true, false);      // Black left -> White right
        generateGradient("wipe_right.png", true, true);       // Black right -> White left
        generateGradient("wipe_up.png", false, false);        // Black top -> White bottom
        generateGradient("wipe_down.png", false, true);       // Black bottom -> White top
        generateRadial("circle_out.png", false);              // Black center -> White edges
        generateRadial("circle_in.png", true);                // Black edges -> White center
        generateDiagonal("diagonal.png");                     // Black top-left -> White bottom-right
        generateDiamond("diamond.png");                       // Diamond shape from center
    }

    /**
     * Generates a linear gradient image.
     *
     * @param filename   output filename
     * @param horizontal true for horizontal gradient, false for vertical
     * @param inverted   true to invert the gradient direction
     */
    private static void generateGradient(String filename, boolean horizontal, boolean inverted) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float t = horizontal ? (float) x / (SIZE - 1) : (float) y / (SIZE - 1);
                if (inverted) t = 1.0f - t;
                int gray = Math.round(t * 255);
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "png", new File(OUTPUT_DIR, filename));
        System.out.println("Generated: " + filename);
    }

    /**
     * Generates a radial gradient image (circle from center).
     *
     * @param filename output filename
     * @param inverted true = black edges, white center (circle_in); false = black center, white edges (circle_out)
     */
    private static void generateRadial(String filename, boolean inverted) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);

        float centerX = (SIZE - 1) / 2.0f;
        float centerY = (SIZE - 1) / 2.0f;
        float maxDist = (float) Math.sqrt(centerX * centerX + centerY * centerY);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = x - centerX;
                float dy = y - centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float t = Math.min(1.0f, dist / maxDist);
                if (inverted) t = 1.0f - t;
                int gray = Math.round(t * 255);
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "png", new File(OUTPUT_DIR, filename));
        System.out.println("Generated: " + filename);
    }

    /**
     * Generates a diagonal gradient image (top-left to bottom-right).
     */
    private static void generateDiagonal(String filename) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float t = ((float) x + (float) y) / (2.0f * (SIZE - 1));
                int gray = Math.round(t * 255);
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "png", new File(OUTPUT_DIR, filename));
        System.out.println("Generated: " + filename);
    }

    /**
     * Generates a diamond-shaped gradient from center.
     * Uses Manhattan distance for diamond shape.
     */
    private static void generateDiamond(String filename) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_BYTE_GRAY);

        float centerX = (SIZE - 1) / 2.0f;
        float centerY = (SIZE - 1) / 2.0f;
        float maxDist = centerX + centerY; // Manhattan distance to corner

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dist = Math.abs(x - centerX) + Math.abs(y - centerY);
                float t = Math.min(1.0f, dist / maxDist);
                int gray = Math.round(t * 255);
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "png", new File(OUTPUT_DIR, filename));
        System.out.println("Generated: " + filename);
    }
}
