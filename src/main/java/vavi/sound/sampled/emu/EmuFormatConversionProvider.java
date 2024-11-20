/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.FormatConversionProvider;


/**
 * EmuFormatConversionProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241116 nsano initial version <br>
 */
public class EmuFormatConversionProvider extends FormatConversionProvider {

    @Override
    public AudioFormat.Encoding[] getSourceEncodings() {
        return new AudioFormat.Encoding[] {
                EmuEncoding.NSF,
                EmuEncoding.SPC,
                EmuEncoding.GBS,
                EmuEncoding.VGM,
                Encoding.PCM_SIGNED,
        };
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings() {
        return new AudioFormat.Encoding[] {
                EmuEncoding.NSF,
                EmuEncoding.SPC,
                EmuEncoding.GBS,
                EmuEncoding.VGM,
                Encoding.PCM_SIGNED,
        };
    }

    @Override
    public AudioFormat.Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(Encoding.PCM_SIGNED)) {
            return new AudioFormat.Encoding[] {
                    EmuEncoding.NSF,
                    EmuEncoding.SPC,
                    EmuEncoding.GBS,
                    EmuEncoding.VGM,
            };
        } else if (sourceFormat.getEncoding() instanceof EmuEncoding) {
            return new AudioFormat.Encoding[] {Encoding.PCM_SIGNED};
        } else {
            return new AudioFormat.Encoding[0];
        }
    }

    @Override
    public AudioFormat[] getTargetFormats(Encoding targetEncoding, AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(Encoding.PCM_SIGNED) &&
                targetEncoding instanceof EmuEncoding) {
            if (sourceFormat.getChannels() > 2 ||
                    sourceFormat.getChannels() <= 0 ||
                    sourceFormat.isBigEndian()) {
                return new AudioFormat[0];
            } else {
                return new AudioFormat[] {
                        new AudioFormat(targetEncoding,
                                sourceFormat.getSampleRate(),
                                -1,     // sample size in bits
                                sourceFormat.getChannels(),
                                -1,     // frame size
                                -1,     // frame rate
                                true)  // little endian
                };
            }
        } else if (sourceFormat.getEncoding() instanceof EmuEncoding && targetEncoding.equals(Encoding.PCM_SIGNED)) {
            return new AudioFormat[] {
                    new AudioFormat(sourceFormat.getSampleRate(),
                            16,         // sample size in bits
                            sourceFormat.getChannels(),
                            true,       // signed
                            true)      // little endian (for PCM wav)
            };
        } else {
            return new AudioFormat[0];
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
                } else if (sourceFormat.getEncoding() instanceof EmuEncoding && targetFormat.getEncoding().equals(Encoding.PCM_SIGNED)) {
                    try {
                        return new Emu2PcmAudioInputStream(sourceFormat, targetFormat, AudioSystem.NOT_SPECIFIED);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (sourceFormat.getEncoding().equals(Encoding.PCM_SIGNED) && targetFormat.getEncoding() instanceof EmuEncoding) {
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
                        targetFormat.getEncoding().equals(Encoding.PCM_SIGNED)) {
                    try {
                        return new Emu2PcmAudioInputStream(sourceFormat, targetFormat, AudioSystem.NOT_SPECIFIED);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (sourceFormat.getEncoding().equals(Encoding.PCM_SIGNED) && targetFormat.getEncoding() instanceof EmuEncoding) {
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