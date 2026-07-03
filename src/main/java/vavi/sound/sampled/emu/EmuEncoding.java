/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import javax.sound.sampled.AudioFormat;

import libgme.MusicEmu;


/**
 * Encodings used by the emulator audio decoder.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
public class EmuEncoding extends AudioFormat.Encoding {

    /**
     * Constructs a new encoding.
     *
     * @param name Name of the emulator audio encoding.
     */
    public EmuEncoding(String name) {
        super(name);
    }

    public static final List<EmuEncoding> encodings = new ArrayList<>();

    static {
        for (var fileFormat : ServiceLoader.load(MusicEmu.class)) {
            if (fileFormat.getEncoding() != null) {
                encodings.add((EmuEncoding) fileFormat.getEncoding());
            }
        }
    }

    public static EmuEncoding valueOf(String name) {
        return encodings.stream().filter(e -> name.equalsIgnoreCase(e.toString())).findFirst().orElseThrow();
    }
}
