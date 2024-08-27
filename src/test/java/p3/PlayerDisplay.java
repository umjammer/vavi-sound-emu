package p3;

import libvgm.VGMPlayer;
import processing.core.PConstants;


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

    void redraw() {
        updateAllValues();

        // Filename label
        window.fill(window.t.theme[0]);
        window.textFont(window.fonts[3]);
        window.text(labelFilename, 150, POS_Y_POSBAR - 12);

        // Pos meter
        window.stroke(window.t.theme[0]);
        window.fill(window.t.theme[1]);
        window.rect(POS_X_POSBAR, POS_Y_POSBAR, WIDTH_POSBAR, HEIGHT_POSBAR, 6);

        // Song pos and track labels
        window.textAlign(PConstants.CENTER, PConstants.CENTER);
        window.fill(window.t.theme[4]);
        window.textFont(window.fonts[1]);
        window.text(labelCurrTrack + " of " + labelTrackCount + "  ·  " + labelTimestamp,
                (float) (POS_X_POSBAR + (WIDTH_POSBAR / 2.0)), POS_Y_POSBAR + 9);

        // File type label
        window.textFont(window.fonts[5]);
        window.fill(window.t.theme[0], 100);
        window.text(labelEmuName, 150, 86);
    }
}
