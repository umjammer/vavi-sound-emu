package p3;

import java.awt.Graphics;

import javax.swing.SwingConstants;

import libgme.VGMPlayer;

public class PlayerDisplay {

    Main window;
    VGMPlayer player;

    static final int POS_X_POSBAR = 16;
    static final int POS_Y_POSBAR = 150;
    static final int WIDTH_POSBAR = 268;
    static final int HEIGHT_POSBAR = 18;

    String labelFilename = "";
    String labelEmuName = "";
    String labelTimestamp = "-:--";
    String labelCurrTrack = "-";
    String labelTrackCount = "-";

    PlayerDisplay(Main parentWindow, VGMPlayer player) {
        window = parentWindow;
        this.player = player;
    }

    private void updateAllValues() {
        if (player.customInfoMsg.isEmpty()) {
            labelFilename = java.nio.file.Paths.get(player.currFilename)
                    .getFileName().toString().replaceFirst("[.][^.]+$", "");
            // what a mess... but it works
            labelFilename = Main.shrinkString(labelFilename);
        } else labelFilename = player.customInfoMsg;

        if (player != null && player.isPlaying()) {
            int secPos = player.getCurrentTime();
            labelTimestamp = secPos / 60 + ":" + String.format("%02d", secPos % 60);
            labelCurrTrack = String.valueOf(player.getCurrentTrack());
            labelTrackCount = String.valueOf(player.getTrackCount());
            labelEmuName = player.emuName;
        } else {
            labelTimestamp = "-:--";
            labelCurrTrack = "-";
            labelTrackCount = "-";
            labelEmuName = "";
        }
    }

    public void repaint(Graphics g) {
        updateAllValues();

        // Filename label
        g.fill(window.t.theme[0]);
        g.setFont(window.fonts[3]);
        g.drawString(labelFilename, 150, POS_Y_POSBAR - 12);

        // Pos meter
        g.setStroke(window.t.theme[0]);
        g.fill(window.t.theme[1]);
        g.drawRect(POS_X_POSBAR, POS_Y_POSBAR, WIDTH_POSBAR, HEIGHT_POSBAR, 6);

        // Song pos and track labels
        g.textAlign(SwingConstants.CENTER, SwingConstants.CENTER);
        g.fill(window.t.theme[4]);
        g.setFont(window.fonts[1]);
        g.drawString(labelCurrTrack + " of " + labelTrackCount + "  ·  " + labelTimestamp,
                (float) (POS_X_POSBAR + (WIDTH_POSBAR / 2.0)), POS_Y_POSBAR + 9);

        // File type label
        g.setFont(window.fonts[5]);
        g.fill(window.t.theme[0], 100);
        g.drawString(labelEmuName, 150, 86);
    }
}
