package uk.co.kernite.VGM;

import java.awt.Label;
import java.util.ArrayList;
import java.util.List;

import libvgm.VGMPlayer;


public class PlayerList extends VGMPlayer {

    public Label titleLabel;
    public Label trackLabel;
    int playlistIndex;
    final List<Entry> list = new ArrayList<>();

    PlayerList(int rate) {
        super(rate);
    }

    private void updateTrack() {
        trackLabel.setText((playlistIndex + 1) + "/" + list.size());
    }

    public void prev() throws Exception {
        if (getCurrentTime() < 4 && isPlaying() && playlistIndex > 0)
            playlistIndex--;
        playIndex(playlistIndex);
    }

    public void next() throws Exception {
        if (playlistIndex < list.size() - 1) {
            playlistIndex++;
            playIndex(playlistIndex);
        }
    }

    private static final class Entry {
        String url;
        String path;
        int track;
        String title;
        int time;
    }

    private void playIndex(int i) throws Exception {
        playlistIndex = i;
        updateTrack();
        Entry e = list.get(i);
        titleLabel.setText(e.title);
        loadFile(e.path);
        startTrack(e.track - 1, e.time);
    }

    public void add(String url, String path, int track, String title, int time, boolean playNow) throws Exception {
        if (title.isEmpty()) {
            title = path;
            if (title.isEmpty())
                title = url;

            title = title.substring(title.lastIndexOf('/') + 1);
        }

        Entry e = new Entry();
        e.url = url;
        e.path = path;
        e.track = track;
        e.title = title;
        e.time = time;
        list.add(e);

        if (playNow)
            playIndex(list.size() - 1);
        else
            updateTrack();
    }
}
