/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;


import java.util.Arrays;
import javax.sound.sampled.AudioFormat;


/**
 * Encodings used by the emulator audio decoder.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
public class EmuEncoding extends AudioFormat.Encoding {

    /** Specifies NES sound data. */
    public static final EmuEncoding NSF = new EmuEncoding("NSF");
    /** Specifies SNES sound data. */
    public static final EmuEncoding SPC = new EmuEncoding("NSF");
    /** Specifies Game Boy sound data. */
    public static final EmuEncoding GBS = new EmuEncoding("GBS");
    /** Specifies VGM sound data. */
    public static final EmuEncoding VGM = new EmuEncoding("VGM");

    /**
     * Constructs a new encoding.
     *
     * @param name Name of the emulator audio encoding.
     */
    public EmuEncoding(String name) {
        super(name);
    }

    private static final EmuEncoding[] encodings = {NSF, SPC, GBS, VGM};

    public static EmuEncoding valueOf(String name) {
        return Arrays.stream(encodings).filter(e -> name.equalsIgnoreCase(e.toString())).findFirst().get();
    }
}
