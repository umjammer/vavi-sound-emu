package p3;

import java.awt.Graphics;
import javax.swing.ImageIcon;


public class Button {

    Main window;
    int x, y, width, height;
    String iconFilename, label;
    ImageIcon textureUp;
    ImageIcon textureDown;
    boolean pressed = false;
    boolean showLabel = true;

    Button(Main window, String icon, String label) {
        this.window = window;
        this.iconFilename = icon;
        this.label = label;
        loadTexture();
        setPressed(false);
    }

    Button(Main window, int x, int y, String icon, String label) {
        this.window = window;
        this.x = x;
        this.y = y;
        this.iconFilename = icon;
        this.label = label;
        loadTexture();
        setPressed(false);
    }

    private void loadTexture() {
        textureUp = new ImageIcon(Button.class.getResource("/data/buttons/" + iconFilename + "Up.png"));
        textureDown = new ImageIcon(Button.class.getResource("/data/buttons/" + iconFilename + "Down.png"));
    }

    public void setPressed(boolean how) {
        this.pressed = how;

        this.width = textureUp.getIconWidth();
        this.height = textureUp.getIconHeight();
    }

    public void paintComponent(Graphics g) {
        g.image(pressed ? textureDown : textureUp, x, y);
        g.fill(window.t.theme[0]);
        g.textAlign(window.CENTER);
        g.setFont(window.fonts[0]);
        if (showLabel) window.text(label, (float) (x + this.width / 2.0), y - 2);
    }

    public boolean collided() {
        return (window.mouseX > this.x && window.mouseX < this.width + this.x) &&
                (window.mouseY > this.y && window.mouseY < this.height + this.y);
    }
}
