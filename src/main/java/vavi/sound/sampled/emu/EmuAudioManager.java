/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import libgme.EmuPlayer;
import libgme.MusicEmu;
import libgme.VGMPlayer;
import libgme.util.DataReader;
import vavi.util.archive.Archives;

import static java.lang.System.getLogger;


/**
 * EmuAudioManager.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-11-21 nsano initial version <br>
 */
public class EmuAudioManager extends EmuPlayer {

    private static final Logger logger = getLogger(EmuAudioManager.class.getName());

    private final int sampleRate;
    private int actualSampleRate;
    private InputStream loadedStream = null;

    public EmuAudioManager(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /** should use after {@link VGMPlayer#loadFile} */
    public int getSampleRate() {
        return this.actualSampleRate;
    }

    /**
     * @param is mark must be supported
     * @throws IllegalArgumentException invalid file
     */
    public void loadFile(InputStream is) throws IOException {

logger.log(Level.TRACE, "input stream B: " + is.getClass().getName() + ", " + is.available());
        InputStream in = Archives.getInputStream(is);
        loadedStream = in;
logger.log(Level.TRACE, "input stream A: " + is.getClass().getName() + ", " + is.available());
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }

        MusicEmu emu = createEmu(in);
        if (emu == null)
            throw new IllegalArgumentException("unsupported file");
        actualSampleRate = emu.setSampleRate(sampleRate);
        emu.loadFile(DataReader.loadData(in));

        // now that new emulator is ready, replace old one
        setEmu(emu, actualSampleRate);
    }

    /**
     * Creates appropriate emulator for given stream
     * @return nullable
     */
    private static MusicEmu createEmu(InputStream is) {
        if (!is.markSupported()) {
            throw new IllegalArgumentException("stream is not supported mark: " + is);
        }

        for (MusicEmu musicEmu : serviceLoader) {
            try {
logger.log(Level.TRACE, musicEmu + ": " + is.available());
                is.mark(musicEmu.getMagic().length());

                if (musicEmu.isSupported(is)) {
                    return musicEmu;
                }
            } catch (IOException e) {
logger.log(Level.TRACE, musicEmu + ": " + e);
            } finally {
                try {
                    is.reset();
logger.log(Level.TRACE, musicEmu + ": " + is.available());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        return null;
    }

    private static final String[] compressedStream;

    static {
        List<String> result = new ArrayList<>();
        Scanner s = new Scanner(VGMPlayer.class.getResourceAsStream("/compressedStream.properties"));
        while (s.hasNextLine()) {
            String line = s.nextLine();
            result.add(line);
        }
        s.close();
        compressedStream = result.toArray(String[]::new);
    }

    public boolean isCompressed() {
logger.log(Level.TRACE, Arrays.toString(compressedStream) + ", " + loadedStream.getClass().getName());
        return Arrays.stream(compressedStream).anyMatch(c -> loadedStream.getClass().getName().equals(c));
    }
}
