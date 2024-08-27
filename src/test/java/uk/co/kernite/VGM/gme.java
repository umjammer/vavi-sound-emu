package uk.co.kernite.VGM;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Dimension;
import java.awt.Label;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import vavi.util.Debug;


/**
 * Simple front-end for VGMPlayer
 */
public final class gme {

    JFrame frame = new JFrame("gme");
    JPanel panel = new JPanel();

    /**
     * Plays file at given URL (HTTP only). If it's an archive (.zip)
     * then path specifies the file within the archive. Track ranges
     * from 1 to number of tracks in file.
     */
    public void playFile(String url, String path, int track, String title, int time) {
        try {
            player.add(url, path, track, title, time, !playlistEnabled.getState() || !player.isPlaying());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playFile(String url, String path, int track, String title) {
        playFile(url, path, track, title, 150);
    }

    public void playFile(String url, String path, int track) {
        playFile(url, path, track, "");
    }

    /** Stops currently playing file, if any */
    public void stopFile() {
        try {
            player.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Applet

    PlayerWithUpdate player;
    boolean backgroundPlayback;
    Checkbox playlistEnabled;

    private Button newBut(String name) {
        Button b = new Button(name);
        b.setActionCommand(name);
        b.addActionListener(al);
        panel.add(b);
        return b;
    }

    void createGUI() {
Debug.println("here");
        panel.add(player.time = new Label("          "));
        panel.add(player.trackLabel = new Label("          "));

        newBut("Prev");
        newBut("Next");
        newBut("Stop");

        panel.add(player.titleLabel = new Label("                                                  "));

        playlistEnabled = new Checkbox("Playlist");
        panel.add(playlistEnabled);
    }

    // Returns integer parameter passed to applet, or defaultValue if missing
    static int getIntParameter(String name, int defaultValue) {
        String p = System.getProperty(name);
        return (p != null ? Integer.parseInt(p) : defaultValue);
    }

    // Returns string parameter passed to applet, or defaultValue if missing
    static String getStringParameter(String name, String defaultValue) {
        String p = System.getProperty(name);
        return (p != null ? p : defaultValue);
    }

    // Called when applet is first loaded
    public void init() {
        // Setup player and sample rate
        int sampleRate = getIntParameter("SAMPLERATE", 44100);
        player = new PlayerWithUpdate(sampleRate);
        player.setVolume(0.02);

        backgroundPlayback = getIntParameter("BACKGROUND", 0) != 0;
        int ip = getIntParameter("NOGUI", 0);
Debug.print("NOGUI: " + ip);
        if (ip == 0)
            createGUI();

        // Optionally start playing file immediately
        String url = System.getProperty("PLAYURL");
Debug.print("url: " + url);
        if (url != null) {
Debug.print("playFile: " + getStringParameter("PLAYPATH", ""));
            playFile(url, getStringParameter("PLAYPATH", ""),
                    getIntParameter("PLAYTRACK", 1));
        }
    }

    static int rand(int range) {
        return (int) (java.lang.Math.random() * range + 0.5);
    }

    // Called when button is clicked
    final ActionListener al = e -> {
        try {
            String cmd = e.getActionCommand();
            if (Objects.equals(cmd, "Stop")) {
                if (player.isPlaying())
                    player.pause();
                else
                    player.play();
                return;
            }

            if (Objects.equals(cmd, "Prev")) {
                player.prev();
                return;
            }

            if (Objects.equals(cmd, "Next")) {
                player.next();
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    };

    public static void main(String[] args) {
        System.setProperty("PLAYPATH", args[0]);
        System.setProperty("NOGUI", "0");
        gme app = new gme();
        app.panel.setPreferredSize(new Dimension( 640, 480));
        app.frame.getContentPane().add(app.panel);
        app.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        app.frame.pack();
        app.frame.setVisible(true);
        app.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                app.stop();
                app.destroy();
            }
        });
        app.init();
    }

    // Called when applet's page isn't active
    public void stop() {
        if (!backgroundPlayback)
            stopFile();
    }

    public void destroy() {
        try {
            stopFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
