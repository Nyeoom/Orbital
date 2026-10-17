package com.appbrim;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Captures the center of the primary screen (where the app window shows). */
public final class ShotBot {

    public static void main(String[] args) throws Exception {
        Thread.sleep(Long.parseLong(args[0]));
        var gd = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle screen = gd.getDefaultConfiguration().getBounds();
        int w = Math.min(1280, screen.width);
        int h = Math.min(800, screen.height);
        int x = screen.x + (screen.width - w) / 2;
        int y = screen.y + (screen.height - h) / 2;
        BufferedImage img = new Robot(gd).createScreenCapture(new Rectangle(x, y, w, h));
        ImageIO.write(img, "png", new File(args[1]));
        System.out.println("saved " + args[1] + " " + w + "x" + h);
    }

    private ShotBot() {
    }
}
