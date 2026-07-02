/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import javax.sound.sampled.AudioFileFormat;

import libgme.MusicEmu;

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
     * Constructs a file type.
     *
     * @param name      the name of the emulator audio File Format.
     * @param extension the file extension for this emulator audio File Format.
     */
    public EmuFileFormatType(String name, String extension) {
        super(name, extension);
    }

    private static final List<EmuFileFormatType> types = new ArrayList<>();

    static {
        for (var fileFormat : ServiceLoader.load(MusicEmu.class)) {
            if (fileFormat.getType() != null) {
                types.add((EmuFileFormatType) fileFormat.getType());
            }
        }
    }

    public static EmuFileFormatType valueOf(String name) {
        return types.stream().filter(t -> name.equalsIgnoreCase(t.toString())).findFirst().orElseThrow();
    }
}
