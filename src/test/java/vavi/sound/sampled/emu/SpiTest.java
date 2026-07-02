/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.emu;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vavi.sound.SoundUtil.volume;
import static vavix.util.DelayedWorker.later;


/**
 * SpiTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241118 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class SpiTest {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property
    String file = "src/test/resources/test.vgm";

    @Property
    String vgm = "src/test/resources/test.vgm";

    @Property
    String gbs;

    /** 1 origin */
    @Property(name = "track")
    int track = 1;

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
Debug.println("volume: " + volume);
    }

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 10 * 1000;

    @Test
    @DisplayName("directly")
    void test0() throws Exception {

        Path path = Path.of(file);
Debug.println(file);
        AudioInputStream sourceAis = new EmuAudioFileReader().getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);

        Map<String, Object> props = new HashMap<>();
        props.put("track", track);
        AudioFormat outAudioFormat = new AudioFormat(
                PCM_SIGNED,
                inAudioFormat.getSampleRate(),
                16,
                inAudioFormat.getChannels(),
                2 * inAudioFormat.getChannels(),
                inAudioFormat.getSampleRate(),
                true,
                props);
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = new EmuFormatConversionProvider().getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        volume(line, volume);

        byte[] buf = new byte[1024];
        while (!later(time).come()) {
            int r = pcmAis.read(buf, 0, 1024);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();
    }

    @Test
    @DisplayName("via spi")
    void test1() throws Exception {

        Path path = Path.of(file);
Debug.println(file);
        AudioInputStream sourceAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);

        Map<String, Object> props = new HashMap<>();
        props.put("track", track);
        AudioFormat outAudioFormat = new AudioFormat(
                PCM_SIGNED,
                inAudioFormat.getSampleRate(),
                16,
                inAudioFormat.getChannels(),
                2 * inAudioFormat.getChannels(),
                inAudioFormat.getSampleRate(),
                true,
                props);
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = AudioSystem.getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        volume(line, volume);

        byte[] buf = new byte[1024];
        while (!later(time).come()) {
            int r = pcmAis.read(buf, 0, 1024);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();
    }

    @Test
    @DisplayName("another input type 2")
    void test2() throws Exception {
        URL url = Paths.get(file).toUri().toURL();
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);
        assertEquals(EmuEncoding.valueOf("VGM"), ais.getFormat().getEncoding());
    }

    @Test
    @DisplayName("another input type 3")
    void test3() throws Exception {
        File file = Paths.get(this.file).toFile();
        AudioInputStream ais = AudioSystem.getAudioInputStream(file);
        assertEquals(EmuEncoding.valueOf("VGM"), ais.getFormat().getEncoding());
    }

    @Test
    @DisplayName("when unsupported file coming")
    void test5() throws Exception {
        InputStream is = SpiTest.class.getResourceAsStream("/test.caf");
        int available = is.available();
Debug.println("1: " + is.available());
        UnsupportedAudioFileException e = assertThrows(UnsupportedAudioFileException.class, () -> {
Debug.println("2: " + is.available());
            AudioSystem.getAudioInputStream(is);
        });
Debug.println("3: " + is.available());
        assertEquals(available, is.available()); // spi must not consume input stream even one byte
        is.close();
    }

    @Test
    @DisplayName("wav out")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test6() throws Exception {
        System.setProperty("javax.sound.sampled.SourceDataLine", "#WaveOut Mixer");

        Path path = Path.of(file);
Debug.println(file);
        AudioInputStream sourceAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);

        Map<String, Object> props = new HashMap<>();
        props.put("track", track);
        AudioFormat outAudioFormat = new AudioFormat(
                PCM_SIGNED,
                inAudioFormat.getSampleRate(),
                16,
                inAudioFormat.getChannels(),
                2 * inAudioFormat.getChannels(),
                inAudioFormat.getSampleRate(),
                true,
                props);
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = AudioSystem.getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        byte[] buf = new byte[1024];
        while (!later(120 * 1000).come()) {
            int r = pcmAis.read(buf, 0, 1024);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();

        if ("#WaveOut Mixer".equals(System.getProperty("javax.sound.sampled.SourceDataLine")))
            Files.move(Path.of(System.getProperty("vavi.sound.sampled.misc.waveout")), Path.of("tmp", "waveout.wav"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    @DisplayName("off vgm")
    void test7() throws Exception {

        System.setProperty("vavi.sound.sampled.spi.emu.vgm", "false");

        Path path = Path.of(vgm);
Debug.println(vgm);
        assertThrows(UnsupportedAudioFileException.class, () -> {
            AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
        });

        System.setProperty("vavi.sound.sampled.spi.emu.vgm", "true");

        AudioInputStream sourceAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
        assertEquals(EmuEncoding.valueOf("VGM"), sourceAis.getFormat().getEncoding());
    }

    @Test
    @DisplayName("off gbs")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test8() throws Exception {

        System.setProperty("vavi.sound.sampled.spi.emu.gbs", "false");

        Path path = Path.of(gbs);
Debug.println(gbs);
        assertThrows(UnsupportedAudioFileException.class, () -> {
            AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
        });

        System.setProperty("vavi.sound.sampled.spi.emu.gbs", "true");

        AudioInputStream sourceAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
        assertEquals(EmuEncoding.valueOf("GBS"), sourceAis.getFormat().getEncoding());
    }
}
