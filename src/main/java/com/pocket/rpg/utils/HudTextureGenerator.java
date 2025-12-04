package com.pocket.rpg.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class HudTextureGenerator {

    private static void save(BufferedImage image, String filepath) throws IOException {
        File file = new File(filepath);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", file);
    }

    // Utility: draw subtle noise for texture
    private static void addNoise(Graphics2D g, int w, int h, int alpha) {
        for (int i = 0; i < w * h / 30; i++) {
            int x = (int) (Math.random() * w);
            int y = (int) (Math.random() * h);
            int a = (int) (Math.random() * alpha);
            g.setColor(new Color(255, 255, 255, a));
            g.fillRect(x, y, 1, 1);
        }
    }

    // Utility: draw decorative frame with corners
    private static void drawFantasyFrame(Graphics2D g, int w, int h, Color border, Color glow) {
        g.setStroke(new BasicStroke(3));
        g.setColor(border);
        g.drawRoundRect(2, 2, w - 4, h - 4, 18, 18);


        g.fillOval(0, 0, 12, 12);
        g.fillOval(w - 12, 0, 12, 12);
        g.fillOval(0, h - 12, 12, 12);
        g.fillOval(w - 12, h - 12, 12, 12);
    }

    // --- HUD Background (200x60) with runic pattern ---
    public static void createHudBackground(String filepath) throws IOException {
        int w = 200, h = 60;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark stone gradient
        GradientPaint gp = new GradientPaint(0, 0, new Color(35, 35, 40), 0, h, new Color(20, 20, 25));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, w, h, 18, 18);

        // Add stone noise
        addNoise(g, w, h, 40);

        // Decorative runic lines
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(100, 180, 255, 90));
        for (int i = 10; i < w - 10; i += 25) {
            g.drawLine(i, 20, i + 10, 10);
            g.drawLine(i + 10, 10, i + 20, 20);
        }

        drawFantasyFrame(g, w, h, new Color(90, 60, 20), new Color(255, 220, 120, 160));

        g.dispose();
        save(img, filepath);
    }

    // --- Life Bar Background (180x20) wood + carvings ---
    public static void createLifeBarBackground(String filepath) throws IOException {
        int w = 180, h = 20;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Wood texture gradient
        GradientPaint gp = new GradientPaint(0, 0, new Color(90, 60, 40), w, 0, new Color(70, 50, 30));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, w, h, 10, 10);

        // Wood grain lines
        g.setColor(new Color(50, 35, 20, 70));
        for (int y = 3; y < h - 3; y += 4)
            g.drawLine(4, y, w - 4, y + (int) (Math.random() * 3 - 1));

        drawFantasyFrame(g, w, h, new Color(60, 30, 10), new Color(255, 200, 80, 130));

        g.dispose();
        save(img, filepath);
    }

    // --- Life Bar (144x20) with fantasy crystal style ---
    public static void createLifeBar(String filepath) throws IOException {
        int w = 144, h = 20;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Crystal red gradient
        GradientPaint gp = new GradientPaint(0, 0, new Color(220, 60, 60), 0, h, new Color(140, 0, 0));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, w, h, 12, 12);

        // Facet shapes
        g.setColor(new Color(255, 120, 120, 60));
        Polygon p = new Polygon();
        p.addPoint(0, h);
        p.addPoint(w / 3, 0);
        p.addPoint(2 * w / 3, 0);
        p.addPoint(w, h);
        g.fillPolygon(p);

        // Bubbles
        g.setColor(new Color(255, 200, 200, 120));
        for (int i = 0; i < 8; i++) {
            int bx = (int) (Math.random() * (w - 12));
            int by = 4 + (int) (Math.random() * 8);
            int bs = 3 + (int) (Math.random() * 5);
            g.fillOval(bx, by, bs, bs);
        }

        // Shine
        GradientPaint shine = new GradientPaint(0, 0, new Color(255, 255, 255, 120), 0, h / 2, new Color(255, 255, 255, 0));
        g.setPaint(shine);
        g.fillRoundRect(2, 2, w - 4, h / 2, 12, 12);

        g.dispose();
        save(img, filepath);
    }

    // --- Mana Bar (108x15) arcane glyph style ---
    public static void createManaBar(String filepath) throws IOException {
        int w = 108, h = 15;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Arcane blue
        GradientPaint gp = new GradientPaint(0, 0, new Color(80, 130, 255), 0, h, new Color(20, 60, 160));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, w, h, 12, 12);

        // Mana symbol (arcane wave)
        g.setColor(new Color(200, 230, 255, 160));
        Path2D wave = new Path2D.Float();
        wave.moveTo(4, h - 4);
        for (int x = 0; x < w - 8; x++) {
            double y = 4 + Math.sin(x * 0.18) * 3;
            wave.lineTo(x + 4, y);
        }
        g.draw(wave);

        // Shine
        GradientPaint shine = new GradientPaint(0, 0, new Color(255, 255, 255, 140), 0, h / 2, new Color(255, 255, 255, 0));
        g.setPaint(shine);
        g.fillRoundRect(2, 2, w - 4, h / 2, 12, 12);

        g.dispose();
        save(img, filepath);
    }

    static void main() {
        try {

            createHudBackground("gameData/assets/sprites/hudBg.png");
            createLifeBar("gameData/assets/sprites/lifeBar.png");
            createLifeBarBackground("gameData/assets/sprites/lifeBarBg.png");
            createManaBar("gameData/assets/sprites/manaBar.png");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
