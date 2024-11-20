/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.sampled.AudioFileFormat;

import static java.lang.System.getLogger;


/**
 * FileFormatTypes used by the emulator audio decoder.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
public class EmuFileFormatType extends AudioFileFormat.Type {

    private static final Logger logger = getLogger(EmuFileFormatType.class.getName());

    /**
     * Specifies an emulator audio file.
     */
    public static final EmuFileFormatType NSF = new EmuFileFormatType("NES", "nsf");
    public static final EmuFileFormatType SFC = new EmuFileFormatType("SNES", "sfc");
    public static final EmuFileFormatType GBS = new EmuFileFormatType("Game Boy", "gbs");
    public static final EmuFileFormatType VGM = new EmuFileFormatType("VGM", "vgm");
    public static final EmuFileFormatType VGZ = new EmuFileFormatType("compressed VGM", "vgz");

    /**
     * Constructs a file type.
     *
     * @param name      the name of the emulator audio File Format.
     * @param extension the file extension for this emulator audio File Format.
     */
    public EmuFileFormatType(String name, String extension) {
        super(name, extension);
    }

    private static final EmuFileFormatType[] types = {NSF, SFC, GBS, VGM, VGZ};

    public static EmuFileFormatType valueOf(String name, boolean isCompressed) {
logger.log(Level.TRACE, "name: " + name + ", isCompressed: " + isCompressed);
        return null; // TODO impl
    }
}
