package p3;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.filechooser.FileFilter;

import libgme.VGMPlayer;

public class Main extends JFrame {

    VGMPlayer player;
    PlayerDisplay playerDisplay;
    ThemeEngine t;
    ImageIcon logoIcon;
    Font[] fonts;
    JButton currMidPressed;
    JToolBar mediaButtons;
    JButton buttonHelp;

    static final int TIME = 120;

    public void settings() {
        setPreferredSize(new Dimension(300, 180));
    }

    public void setup() {
        this.setTitle("vlco_o P3synthVG");

        this.player = new VGMPlayer(44100);
        player.setVolume(0.2);

        playerDisplay = new PlayerDisplay(this, player);
        t = new ThemeEngine();

        setupImages();
        setupFonts();
        setupButtons();
        t.setTheme();
    }

    private void setupButtons() {
        JButton b1 = new JButton("Prev", new ImageIcon(Main.class.getResource("/buttons/previous.png")));
        JButton b4 = new JButton("Open", new ImageIcon(Main.class.getResource("/buttons/open.png")));
        JButton b2 = new JButton("Pause", new ImageIcon(Main.class.getResource("/buttons/pause.png")));
        JButton b3 = new JButton("Next", new ImageIcon(Main.class.getResource("/buttons/next.png")));
        JButton[] buttons1 = {b1, b4, b2, b3};
        mediaButtons = new JToolBar();
        mediaButtons.setPreferredSize(new Dimension(154, 16));
        Arrays.stream(buttons1).forEach(mediaButtons::add);

        buttonHelp = new JButton("info"); // 0, 0,
    }

    private void setupFonts() {
        try {
            fonts = new Font[6];
            fonts[0] = Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/fonts/terminus12.vlw"));
            fonts[1] = Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/fonts/terminus14.vlw"));
            fonts[2] = Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/fonts/terminusB14.vlw"));
            fonts[3] = Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/fonts/terminusBI14.vlw"));
            fonts[4] = Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/fonts/robotoBI16.vlw"));
            fonts[5] = Font.createFont(Font.TRUETYPE_FONT, Main.class.getResourceAsStream("/fonts/robotoBI64.vlw"));
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setupImages() {
        logoIcon = new ImageIcon(Main.class.getResource("/graphics/logo.png"));
    }

    public void paint(Graphics g) {
        g.setColor(t.theme[2]);

        g.drawImage(logoIcon.getImage(), 16, 8, null);
        playerDisplay.repaint(g);
        mediaButtons.repaint();
        buttonHelp.repaint();
    }

    KeyListener kl = new KeyAdapter() {
        public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_UP) {
                player.setPlaybackRateFactor((float) (player.getPlaybackRateFactor() + 0.1));
            } else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                player.setPlaybackRateFactor((float) (player.getPlaybackRateFactor() - 0.1));
            }
        }
    };

    MouseListener ml = new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
            // messy... but it works
            if (event.getButton() == MouseEvent.BUTTON1) {
                for (int i = 0; i < mediaButtons.getComponentCount(); i++) {
                    JButton b = (JButton) mediaButtons.getComponentAtIndex(i);
                    if (b.getText().equalsIgnoreCase("pause")) continue;
                    if (event.getComponent() == b) currMidPressed = b;
                }
                if (event.getComponent() == buttonHelp) currMidPressed = buttonHelp;

                if (currMidPressed != null) currMidPressed.setSelected(true);
            }
        }

        JFileChooser fc = new JFileChooser();

        public void mouseReleased(MouseEvent event) {
            if (event.getButton() == MouseEvent.BUTTON1) {
                if (player.isPlaying()) {
                    if (event.getComponent() == mediaButtons.getComponentAtIndex(0)) { // previous
                        try {
                            player.startTrack(
                                    Math.clamp(player.getCurrentTrack() - 1, 0, player.getTrackCount()), TIME);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else if (event.getComponent() == mediaButtons.getComponentAtIndex(3)) { // next
                        try {
                            player.startTrack(
                                    Math.clamp(player.getCurrentTrack() + 1, 0, player.getTrackCount()), TIME);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                if (event.getComponent() == mediaButtons.getComponentAtIndex(1)) { // open
                    fc.setFileFilter(new FileFilter() {
                        final String[] exts = {"vgm", "nsf", "gbs", "spc", "vgz", "zip", "gz"};
                        @Override public boolean accept(File f) {
                            return Arrays.stream(exts).anyMatch(e -> f.toString().toLowerCase().endsWith(e));
                        }

                        @Override public String getDescription() {
                            return "game music file";
                        }
                    });
                    fc.setDialogTitle("Video Game Music files");
                    fc.showOpenDialog(Main.this);
                    fileSelected(fc.getSelectedFile());
                } else if (event.getComponent() == mediaButtons.getComponentAtIndex(2)) { // pause
                    JButton me = (JButton) event.getComponent();

                    try {
                        if (me.isSelected()) player.play();
                        else player.pause();
                    } catch (Exception ignored) {

                    }

                    if (currMidPressed != null)
                        currMidPressed.setSelected(false); // avoid button stucking when click slide
                    me.setSelected(!me.isSelected());
                    currMidPressed = null;
                } else if (event.getComponent() == buttonHelp) {
                    JOptionPane.showMessageDialog(Main.this, """
                            Thanks for using P3synthVG.
                            
                            \
                            Further development of this program has been cancelled,
                            so this remains as a proof of concept.
                            
                            \
                            vlcoo.net  |  github.com/vlcoo/P3synthVG""", "Message", JOptionPane.INFORMATION_MESSAGE);
                }

                if (currMidPressed != null) {
                    currMidPressed.setSelected(false);
                    currMidPressed = null;
                }
            }
        }
    };

    static String shrinkString(String original) {
        if (original.length() > 68) original = original.substring(0, 68 - 3) + "...";
        return original;
    }

    public void fileSelected(File sel) {
        if (sel == null) {
            System.err.println("None file");
        } else {
            try {
                player.loadFile(sel.getAbsolutePath());
                player.startTrack(0, TIME);

                ((JButton) mediaButtons.getComponentAtIndex(1)).setSelected(false);
            } catch (IllegalArgumentException iae) {
                player.customInfoMsg = "Invalid file";
            } catch (Exception e) {
                player.customInfoMsg = "Library error";
            }
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.setVisible(true);
    }
}