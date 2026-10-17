package com.appbrim;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Dev tool: a 500x500 always-on-top window that imitates VALORANT's full-map overlay, so spike
 * detection can be tried without being in a match. It shows a red "spike" blob at (150,150) and
 * a white "you" blob at (100,400) in map coordinates.
 *
 * <p>Use it with {@link MockSite} (whose callouts put those two points at "A Site" and "A Main"):
 * start both, run Orbital pointed at the mock, drag-calibrate a box around this window, press F9.
 * Run: {@code java -cp target/classes;target/test-classes com.appbrim.FakeMap}
 */
public final class FakeMap {

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("Orbital fake map");
            f.setAlwaysOnTop(true);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(20, 30, 45));
                    g2.fillRect(0, 0, 500, 500);
                    g2.setColor(new Color(60, 80, 100));
                    g2.drawRect(40, 40, 420, 420);
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                    g2.drawString("fake map: calibrate a box around this window", 12, 24);
                    g2.setColor(new Color(255, 70, 85));
                    g2.fillOval(138, 138, 24, 24);      // spike
                    g2.setColor(Color.WHITE);
                    g2.fillOval(90, 390, 20, 20);       // you
                }
            };
            panel.setPreferredSize(new java.awt.Dimension(500, 500));
            f.setContentPane(panel);
            f.pack();
            f.setLocation(800, 150);
            f.setVisible(true);
        });
    }

    private FakeMap() {
    }
}
