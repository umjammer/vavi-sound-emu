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
    public static final EmuEncoding SPC = new EmuEncoding("SPC");
    /** Specifies Game Boy sound data. */
    public static final EmuEncoding GBS = new EmuEncoding("GBS");
    /** Specifies VGM sound data. */
    public static final EmuEncoding VGM = new EmuEncoding("VGM");
    /** Specifies KSS sound data. */
    public static final EmuEncoding KSS = new EmuEncoding("KSS");
    /** Specifies GYM sound data. */
    public static final EmuEncoding GYM = new EmuEncoding("GYM");

    /**
     * Constructs a new encoding.
     *
     * @param name Name of the emulator audio encoding.
     */
    private EmuEncoding(String name) {
        super(name);
    }

    static final EmuEncoding[] encodings = {NSF, SPC, GBS, VGM, KSS};

    public static EmuEncoding valueOf(String name) {
        return Arrays.stream(encodings).filter(e -> name.equalsIgnoreCase(e.toString())).findFirst().orElseThrow();
    }
}
