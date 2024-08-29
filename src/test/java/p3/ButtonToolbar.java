package p3;

import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPanel;

public class ButtonToolbar extends JPanel {

    int x, y;
    double xSep, ySep;
    Map<String, JButton> buttons = new HashMap<>();

    ButtonToolbar(int x, int y, double xSep, double ySep, Button[] buttons) {
        this.xSep = xSep;
        this.ySep = ySep;
        this.x = x;
        this.y = y;

        int i = 0;
        for (Button b : buttons) {
            if (b == null) continue;
            this.buttons.put(b.iconFilename, b);
            b.x = (int) (i * (b.width * this.xSep) + x);
            b.y = (int) (i * (b.height * this.ySep) + y);
            i++;
        }
    }

    public JButton getButton(String filename) {
        return this.buttons.get(filename);
    }

    public void repaint(Graphics g) {
        for (JButton b : buttons.values()) b.repaint(g);
    }

    boolean collided(String bName) {
        JButton b = this.buttons.get(bName);
        if (b == null) return false;
        return b.collided();
    }
}
