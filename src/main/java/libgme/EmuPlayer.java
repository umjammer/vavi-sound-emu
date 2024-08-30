/*
 * Copyright (C) 2003-2007 Shay Green.
 *
 * This module is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This module is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this module; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package libgme;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import static java.lang.System.getLogger;


public class EmuPlayer implements Runnable {

    private static final Logger logger = getLogger(EmuPlayer.class.getName());

    public String emuName = "";

    // Number of tracks
    public int getTrackCount() {
        return emu.trackCount();
    }

    public void startTrack(int track) throws Exception {
        if (playing) pause();
        if (line != null)
            line.flush();
        emu.startTrack(track);
        play();
    }

    /**
     * Starts new track playing, where 0 is the first track.
     * After time seconds, the track starts fading.
     */
    public void startTrack(int track, int time) throws IOException {
        if (playing) pause();
        if (line != null)
            line.flush();
        emu.startTrack(track);
        emu.setFade(time, 6);
        setEmuName();
        play();
    }

    public void setTrack(int track) throws IOException {
        emu.startTrack(track);
        setEmuName();
    }

    /** Currently playing track */
    public int getCurrentTrack() {
        return emu.currentTrack();
    }

    /** Number of seconds played since last startTrack() call */
    public int getCurrentTime() {
        return (emu == null ? 0 : emu.currentTime());
    }

    /**
     * Sets playback volume, where 1.0 is normal, 2.0 is twice as loud.
     * Can be changed while track is playing.
     */
    public void setVolume(double v) {
        volume = v;

        if (line != null) {
            FloatControl mg = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            if (mg != null)
                mg.setValue((float) (Math.log(v) / Math.log(10.0) * 20.0));
        }
    }

    /** Current playback volume */
    public double getVolume() {
        return volume;
    }

    public void setPlaybackRateFactor(float factor) {
        if (factor < 0.0) factor = 0;
        if (factor > 2.0) factor = 2;

        playRateFactor = factor;
        emu.setPlaybackRateFactor(factor);
    }

    public float getPlaybackRateFactor() {
        return playRateFactor;
    }

    private void setEmuName() {
        this.emuName = emu.getClass().getName().replace("Emu", "").replace("libgme.", "").toUpperCase();
    }

    /** Pauses if track was playing. */
    public void pause() {
        if (thread != null) {
            playing = false;
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
            thread = null;
        }
    }

    /** True if track is currently playing */
    public boolean isPlaying() {
        return playing;
    }

    /** Resumes playback where it was paused */
    public void play() throws IOException {
        try {
            if (line == null) {
                line = (SourceDataLine) AudioSystem.getLine(lineInfo);
                line.open(audioFormat);
                setVolume(volume);
            }
            thread = new Thread(this);
            thread.setName("simplevgm");
            thread.setPriority(Thread.MAX_PRIORITY - 1);
            playing = true;
            thread.start();
logger.log(Level.DEBUG, "PLAY");
        } catch (LineUnavailableException e) {
            throw new IOException(e);
        }
    }

    /** Stops playback and closes audio */
    public void stop() {
        pause();

        if (line != null) {
            line.close();
            line = null;
        }
    }

    final static long SLEEP_NS = (ClassicEmu.bufLength / 3) * Duration.ofMillis(1).toNanos();

    /** Called periodically when a track is playing */
    protected void idle() {
//        LockSupport.parkNanos(SLEEP_NS);
    }

    // private

    /** Sets music emulator to get samples from */
    protected void setEmu(MusicEmu emu, int sampleRate) {
        stop();
        this.emu = emu;
        if (emu != null && line == null && this.sampleRate != sampleRate) {
            audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, 2, 4, sampleRate, true);
            lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            this.sampleRate = sampleRate;
        }
    }

    public MusicEmu getEmu() {
        return emu;
    }

    private int sampleRate = 0;
    AudioFormat audioFormat;
    DataLine.Info lineInfo;
    protected MusicEmu emu;
    Thread thread;
    volatile boolean playing;
    SourceDataLine line;
    double volume = 1.0;
    float playRateFactor = 1;

    @Override
    public void run() {
        line.start();
logger.log(Level.DEBUG, "START: " + playing + ", " + emu.trackEnded());

        // play track until stop signal
        byte[] buf = new byte[8192];
        while (playing && !emu.trackEnded()) {
            int count = emu.play(buf, buf.length / 2);
logger.log(Level.TRACE, "count: " + count + ", playing: " + playing);
            line.write(buf, 0, count * 2);
            this.idle();
        }

        playing = false;
        line.stop();
logger.log(Level.DEBUG, "STOP");
    }
}
