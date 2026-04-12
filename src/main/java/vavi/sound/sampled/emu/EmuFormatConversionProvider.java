/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.io.IOException;
import java.lang.System.Logger;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

import static java.lang.System.Logger.Level.DEBUG;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static javax.sound.sampled.AudioSystem.NOT_SPECIFIED;
import static vavi.sound.sampled.emu.EmuEncoding.encodings;


/**
 * EmuFormatConversionProvider.
 * <p>
 * system property
 * <li>{@code vavi.sound.sampled.spi.emu.vgm} ... this conversion provider enabled vgm or not, default {@code true}</li>
 * <li>{@code vavi.sound.sampled.spi.emu.gbs} ... this conversion provider enabled gbs or not, default {@code true}</li>
 * </p>
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
public class EmuFormatConversionProvider extends FormatConversionProvider {

    private static final Logger logger = System.getLogger(EmuFormatConversionProvider.class.getName());

    @Override
    public AudioFormat.Encoding[] getSourceEncodings() {
        return Stream.concat(Arrays.stream(encodings), Stream.of(PCM_SIGNED))
                .toArray(Encoding[]::new);
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings() {
        return Stream.concat(Arrays.stream(encodings), Stream.of(PCM_SIGNED))
                .toArray(Encoding[]::new);
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(PCM_SIGNED)) {
            return Arrays.stream(encodings).toArray(Encoding[]::new);
        } else if (sourceFormat.getEncoding() instanceof EmuEncoding) {
            return new AudioFormat.Encoding[] {PCM_SIGNED};
        } else {
            return new AudioFormat.Encoding[0];
        }
    }

    @Override
    public AudioFormat[] getTargetFormats(Encoding targetEncoding, AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(PCM_SIGNED) &&
                targetEncoding instanceof EmuEncoding) {
            if (sourceFormat.getChannels() > 2 ||
                    sourceFormat.getChannels() <= 0 ||
                    sourceFormat.isBigEndian()) {
                return new AudioFormat[0];
            } else {
                return new AudioFormat[] {
                        new AudioFormat(targetEncoding,
                                sourceFormat.getSampleRate(),
                                -1,           // sample size in bits
                                sourceFormat.getChannels(),
                                -1,                // frame size
                                -1,                          // frame rate
                                true)                        // little endian
                };
            }
        } else if (sourceFormat.getEncoding() instanceof EmuEncoding && targetEncoding.equals(PCM_SIGNED)) {
            if (isDisabled(sourceFormat.getEncoding())) {
logger.log(DEBUG, "disabled conversion spi by system property");
                return new AudioFormat[0];
            } else {
                return new AudioFormat[] {
                        new AudioFormat(sourceFormat.getSampleRate(),
                                16,           // sample size in bits
                                sourceFormat.getChannels(),
                                true,                 // signed
                                true)                        // little endian (for PCM wav)
                };
            }
        } else {
            return new AudioFormat[0];
        }
    }

    //
    private static boolean isDisabled(Encoding encoding) {
        if (encoding.equals(EmuEncoding.VGM) &&
                !Boolean.parseBoolean(System.getProperty("vavi.sound.sampled.spi.emu.vgm", "true"))) {
            return true;
        } else if (encoding.equals(EmuEncoding.GBS) &&
                !Boolean.parseBoolean(System.getProperty("vavi.sound.sampled.spi.emu.gbs", "true"))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(Encoding targetEncoding, AudioInputStream sourceStream) {
        if (isConversionSupported(targetEncoding, sourceStream.getFormat())) {
            AudioFormat[] formats = getTargetFormats(targetEncoding, sourceStream.getFormat());
            if (formats != null && formats.length > 0) {
                AudioFormat sourceFormat = sourceStream.getFormat();
                AudioFormat targetFormat = formats[0];
                if (sourceFormat.equals(targetFormat)) {
                    return sourceStream;
                } else if (sourceFormat.getEncoding() instanceof EmuEncoding && targetFormat.getEncoding().equals(PCM_SIGNED)) {
                    try {
                        return new Emu2PcmAudioInputStream(sourceFormat, targetFormat, NOT_SPECIFIED, targetFormat.properties());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (sourceFormat.getEncoding().equals(PCM_SIGNED) && targetFormat.getEncoding() instanceof EmuEncoding) {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat);
                } else {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat.toString());
                }
            } else {
                throw new IllegalArgumentException("target format not found");
            }
        } else {
            throw new IllegalArgumentException("conversion not supported");
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream) {
        if (isConversionSupported(targetFormat, sourceStream.getFormat())) {
            AudioFormat[] formats = getTargetFormats(targetFormat.getEncoding(), sourceStream.getFormat());
            if (formats != null && formats.length > 0) {
                AudioFormat sourceFormat = sourceStream.getFormat();
                if (sourceFormat.equals(targetFormat)) {
                    return sourceStream;
                } else if (sourceFormat.getEncoding() instanceof EmuEncoding &&
                        targetFormat.getEncoding().equals(PCM_SIGNED)) {
                    try {
                        return new Emu2PcmAudioInputStream(sourceFormat, targetFormat, NOT_SPECIFIED, targetFormat.properties());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (sourceFormat.getEncoding().equals(PCM_SIGNED) && targetFormat.getEncoding() instanceof EmuEncoding) {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat);
                } else {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat);
                }
            } else {
                throw new IllegalArgumentException("target format not found");
            }
        } else {
            throw new IllegalArgumentException("conversion not supported");
        }
    }
}
