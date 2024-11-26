/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.getLogger;
import static javax.sound.sampled.AudioSystem.NOT_SPECIFIED;


/**
 * Provider for emulator audio file reading services. This implementation can parse
 * the format information from emulator audio file, and can produce audio input
 * streams from files of this type.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
public class EmuAudioFileReader extends AudioFileReader {

    private static final Logger logger = getLogger(EmuAudioFileReader.class.getName());

    // TODO what's this?
    private URI uri;

    @Override
    public AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            uri = file.toURI();
            return getAudioFileFormat(inputStream, (int) file.length());
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
            try {
                uri = url.toURI();
            } catch (URISyntaxException ignore) {
            }
            return getAudioFileFormat(inputStream);
        }
    }

    /**
     * @param stream mark must be supported
     */
    @Override
    public AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioFileFormat(stream, NOT_SPECIFIED);
    }

    /**
     * Return the AudioFileFormat from the given InputStream. Implementation.
     *
     * @param bitStream input to decode
     * @param mediaLength unused
     * @return an AudioInputStream object based on the audio file data contained
     * in the input stream.
     * @throws UnsupportedAudioFileException if the File does not point to a
     *                                       valid audio file data recognized by the system.
     * @throws IOException                   if an I/O exception occurs.
     */
    protected static AudioFileFormat getAudioFileFormat(InputStream bitStream, int mediaLength) throws UnsupportedAudioFileException, IOException {
logger.log(DEBUG, "enter: available: " + bitStream.available());
        EmuAudioManager manager = new EmuAudioManager(44100);
        AudioFormat.Encoding encoding;
        float samplingRate;
        String emu;
        try {
            manager.loadFile(bitStream);

            emu = manager.getEmu().getClass().getSimpleName().replace("Emu", "");
            encoding = EmuEncoding.valueOf(emu);
            samplingRate = manager.getSampleRate();
        } catch (IllegalArgumentException e) {
logger.log(DEBUG, "error exit: available: " + bitStream.available());
logger.log(TRACE, e.getMessage(), e);
            throw (UnsupportedAudioFileException) new UnsupportedAudioFileException().initCause(e);
        }
        AudioFileFormat.Type type = EmuFileFormatType.valueOf(emu, manager.isCompressed());
logger.log(TRACE, "type: " + type);
        Map<String, Object> props = new HashMap<>();
        props.put("emu", manager.getEmu());
        AudioFormat format = new AudioFormat(encoding, samplingRate, NOT_SPECIFIED, 2, NOT_SPECIFIED, NOT_SPECIFIED, true, props);
        return new AudioFileFormat(type, format, NOT_SPECIFIED);
    }

    @Override
    public AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));
        try {
            return getAudioInputStream(inputStream, (int) file.length());
        } catch (UnsupportedAudioFileException | IOException e) {
            inputStream.close();
            throw e;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream inputStream = new BufferedInputStream(url.openStream());
        try {
            return getAudioInputStream(inputStream);
        } catch (UnsupportedAudioFileException | IOException e) {
            inputStream.close();
            throw e;
        }
    }

    /**
     * @param stream mark must be supported
     */
    @Override
    public AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(stream, NOT_SPECIFIED);
    }

    /**
     * Obtains an audio input stream from the input stream provided. The stream
     * must point to valid audio file data.
     *
     * @param inputStream the input stream from which the AudioInputStream
     *                    should be constructed.
     * @param mediaLength unused
     * @return an AudioInputStream object based on the audio file data contained
     * in the input stream.
     * @throws UnsupportedAudioFileException if the File does not point to a
     *                                       valid audio file data recognized by the system.
     * @throws IOException                   if an I/O exception occurs.
     */
    protected static AudioInputStream getAudioInputStream(InputStream inputStream, int mediaLength) throws UnsupportedAudioFileException, IOException {
        AudioFileFormat audioFileFormat = getAudioFileFormat(inputStream, mediaLength);
        return new AudioInputStream(inputStream, audioFileFormat.getFormat(), audioFileFormat.getFrameLength());
    }
}
