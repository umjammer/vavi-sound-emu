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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import static java.lang.System.getLogger;
import static vavi.sound.SoundUtil.volume;


public class EmuPlayer {

    private static final Logger logger = getLogger(EmuPlayer.class.getName());

    public interface Engine extends Runnable {

        void setEmu(MusicEmu emu);

        void init();

        void reset();

        void setVolume(double v);

        void setSampleRate(int sampleRate);

        void stop();

        boolean isPlaying();

        void setPlaying(boolean playing);
    }

    public static class JavaEngine implements Engine {

        SourceDataLine line;
        AudioFormat audioFormat;
        DataLine.Info lineInfo;
        private int sampleRate = 0;
        volatile boolean playing;
        MusicEmu emu;
        double volume = 0.2;

        @Override
        public void setEmu(MusicEmu emu) {
            this.emu = emu;
        }

        /** @throws IllegalStateException line error */
        @Override
        public void init() {
            if (line == null) {
                try {
                    line = (SourceDataLine) AudioSystem.getLine(lineInfo);
                    line.open(audioFormat);
                    listeners.forEach(line::addLineListener);
                } catch (LineUnavailableException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private final List<LineListener> listeners = new ArrayList<>();

        public void addLineListener(LineListener listener) {
            listeners.add(listener);
        }

        public void removeLineListener(LineListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void reset() {
            if (line != null)
                line.flush();
        }

        @Override
        public void setVolume(double v) {
            this.volume = v;
        }

        @Override
        public void setSampleRate(int sampleRate) {
            if (line == null && this.sampleRate != sampleRate) {
                audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate, 16, 2, 4, sampleRate, true);
                lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
                this.sampleRate = sampleRate;
            }
        }

        @Override
        public void stop() {
            if (line != null) {
                line.close();
                line = null;
            }
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void setPlaying(boolean playing) {
            this.playing = playing;
        }

        @Override
        public void run() {
            try {
                line.start();
                volume(line, volume);
                playing = true;
logger.log(Level.DEBUG, "START: playing: " + playing + ", trackEnd: " + emu.trackEnded());

                // play track until stop signal
                byte[] buf = new byte[8192];
                while (playing && !emu.trackEnded()) {
                    int count = emu.play(buf, buf.length / 2);
logger.log(Level.TRACE, "count: " + count + ", playing: " + playing);
                    line.write(buf, 0, count * 2);
                    this.idle();
                }

logger.log(Level.DEBUG, "STOP");
            } catch (Exception e) {
logger.log(Level.ERROR, e.getMessage(), e);
            } finally {
                playing = false;
                line.stop();
            }
        }

        final static long SLEEP_NS = (ClassicEmu.bufLength / 3) * Duration.ofMillis(1).toNanos();

        /** Called periodically when a track is playing */
        protected void idle() {
//        LockSupport.parkNanos(SLEEP_NS);
        }
    }

    private Engine engine;

    ExecutorService es = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("simplevgm");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        return thread;
    });

    /** Number of tracks */
    public int getTrackCount() {
        return emu.trackCount();
    }

    public void startTrack(int track) {
        if (engine.isPlaying()) pause();
        engine.reset();
        emu.startTrack(track);
        play();
    }

    /**
     * Starts new track playing, where 0 is the first track.
     * After time seconds, the track starts fading.
     */
    public void startTrack(int track, int time) {
        if (engine.isPlaying()) pause();
        engine.reset();
        emu.startTrack(track);
        emu.setFade(time, 6);
        play();
    }

    public void setTrack(int track) {
        emu.startTrack(track);
    }

    /** Currently playing track */
    public int getCurrentTrack() {
        return emu.currentTrack();
    }

    /** Number of seconds played since last startTrack() call */
    public int getCurrentTime() {
        return (emu == null ? 0 : emu.currentTime());
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

    /** Pauses if track was playing. */
    public void pause() {
        if (engine != null) {
            engine.setPlaying(false);
//            es.shutdownNow();
        }
    }

    /** True if track is currently playing */
    public boolean isPlaying() {
        return engine.isPlaying();
    }

    /** Resumes playback where it was paused */
    public void play() {
        engine.init();
logger.log(Level.DEBUG, "PLAY: endless: " + emu.isEndlessLoopFlag());
        es.submit(engine);
    }

    /** Stops playback and closes audio */
    public void stop() {
        pause();

        if (engine != null)
            engine.stop();
    }

    // private

    /** Sets music emulator to get samples from */
    protected void setEmu(MusicEmu emu, int sampleRate) {
        stop();
        this.emu = emu;
        if (this.engine != null && emu != null) {
            this.engine.setEmu(emu);
            this.engine.setSampleRate(sampleRate);
        }
    }

    public MusicEmu getEmu() {
        return emu;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    protected MusicEmu emu;
    private float playRateFactor = 1;

    protected static final ServiceLoader<MusicEmu> serviceLoader = ServiceLoader.load(MusicEmu.class);
}
