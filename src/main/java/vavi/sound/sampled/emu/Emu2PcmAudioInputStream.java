/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import libgme.MusicEmu;
import vavi.io.OutputEngine;
import vavi.io.OutputEngineInputStream;

import static java.lang.System.getLogger;


/**
 * Converts an Emulator music BitStream into a PCM 16bits/sample audio stream.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
class Emu2PcmAudioInputStream extends AudioInputStream {

    private static final Logger logger = getLogger(Emu2PcmAudioInputStream.class.getName());

    /**
     * Constructor.
     *
     * @param sourceFormat the underlying input stream.
     * @param format the target format of this stream's audio data.
     * @param length the length in sample frames of the data in this stream.
     */
    public Emu2PcmAudioInputStream(AudioFormat sourceFormat, AudioFormat format, long length) throws IOException {
        this(sourceFormat, format, length, format.properties());
    }

    /** */
    public Emu2PcmAudioInputStream(AudioFormat sourceFormat, AudioFormat format, long length, Map<String, Object> props) throws IOException {
        super(new OutputEngineInputStream(new EmuOutputEngine(sourceFormat, props)), format, length);
    }

    /** */
    private static class EmuOutputEngine implements OutputEngine {

        /** */
        private DataOutputStream out;

        /** */
        final MusicEmu emu;

        byte[] buf = new byte[8192];

        /** */
        public EmuOutputEngine(AudioFormat format, Map<String, Object> props) throws IOException {
            this.emu = (MusicEmu) format.getProperty("emu");
logger.log(Level.DEBUG, emu.getClass().getName());
            int track = 1;
            try {
                track = (int) props.get("track");
                if (track < 1 || track > emu.trackCount()) track = 1;
            } catch (Exception e) {
logger.log(Level.WARNING, "wrong props::track: " + e.toString());
            }
            emu.startTrack(track);
logger.log(Level.DEBUG, "props: " + props  + ", track: " + track + " / " + emu.trackCount());
        }

        @Override
        public void initialize(OutputStream out) throws IOException {
            if (this.out != null) {
                throw new IOException("Already initialized");
            } else {
                this.out = new DataOutputStream(out);
            }
        }

        @Override
        public void execute() throws IOException {
            if (out == null) {
                throw new IOException("Not yet initialized");
            } else {
                if (!emu.trackEnded()) {
                    int count = emu.play(buf, buf.length / 2);
logger.log(Level.TRACE, "count: " + count);
                    out.write(buf, 0, count * 2);
                } else {
                    out.close();
                }
            }
        }

        @Override
        public void finish() throws IOException {
        }
    }
}
