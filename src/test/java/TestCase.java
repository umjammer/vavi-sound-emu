/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.BufferedInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.LineEvent.Type;

import libgme.EmuPlayer.JavaEngine;
import libgme.VGMPlayer;
import libgme.kss.AyApu;
import libgme.kss.KssEmu;
import libgme.util.DataReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vavi.sound.sampled.emu.EmuAudioManager;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static vavi.sound.SoundUtil.volume;
import static vavix.util.DelayedWorker.later;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-08-26 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property
    String vgz = "src/test/resources/test.vgm";

    /** 1 origin */
    @Property(name = "track")
    int track = 1;

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
    @DisplayName("file")
    void test1() throws Exception {
        System.setProperty("libgme.endless", String.valueOf(onIde));
Debug.print("libgme.endless: " + System.getProperty("libgme.endless"));

        VGMPlayer player = new VGMPlayer(44100);
Debug.println(vgz);
        CountDownLatch cdl = new CountDownLatch(1);

        JavaEngine engine = new JavaEngine();
        engine.addLineListener(e -> { if (e.getType() == Type.STOP) cdl.countDown(); });
        engine.setVolume(volume);

        player.setEngine(engine);
        player.loadFile(vgz);
        player.setTrack(track);
        player.play();

        if (!onIde) later(time, cdl::countDown);
        cdl.await();

        player.stop();
    }

    @Test
    @DisplayName("stream")
    void test2() throws Exception {
        System.setProperty("libgme.endless", String.valueOf(onIde));

        EmuAudioManager manager = new EmuAudioManager(44100);
Debug.println(vgz);
        CountDownLatch cdl = new CountDownLatch(1);

        JavaEngine engine = new JavaEngine();
        engine.addLineListener(e -> { if (e.getType() == Type.STOP) cdl.countDown(); });
        engine.setVolume(volume);

        manager.setEngine(engine);
        manager.loadFile(new BufferedInputStream(Files.newInputStream(Path.of(vgz))));
        manager.setTrack(track);
        manager.play();

        if (!onIde) later(time, cdl::countDown);
        cdl.await();

        manager.stop();
    }

    @Test
    @Disabled("for ai iteration")
    @DisplayName("probe")
    void test3() throws Exception {
        System.setProperty("libgme.endless", "false");

        VGMPlayer player = new VGMPlayer(44100);
Debug.println(vgz);
        CountDownLatch cdl = new CountDownLatch(1);

        class ProbingEngine extends JavaEngine {
            boolean sounded = false;
            @Override
            public void run() {
                try {
                    line.start();
                    volume(line, volume);
                    playing = true;
                    byte[] buf = new byte[8192];
                    while (playing && !emu.trackEnded()) {
                        int count = emu.play(buf, buf.length / 2);
                        for (int i = 0; i < count * 2; i++) {
                            if (buf[i] != 0) {
                                sounded = true;
                                break;
                            }
                        }
                        line.write(buf, 0, count * 2);
                    }
                } catch (Exception e) {
                    Debug.printStackTrace(e);
                } finally {
                    playing = false;
                    line.stop();
                    cdl.countDown();
                }
            }
        }

        ProbingEngine engine = new ProbingEngine();
        engine.setVolume(volume);

        player.setEngine(engine);
        player.loadFile(vgz);
        System.err.println("File size: " + Files.size(Path.of(vgz)));
        for (int t = 0; t < 10; t++) {
            System.err.println("Trying track " + t);
            player.setTrack(t);
            player.play();
            Thread.sleep(1000);
            if (engine.sounded) {
                System.err.println("Sound detected on track " + t);
                break;
            }
            player.pause();
        }

        if (!engine.sounded) {
            later(2000, cdl::countDown);
            cdl.await();
        } else {
            cdl.countDown();
        }

        player.stop();
        org.junit.jupiter.api.Assertions.assertTrue(engine.sounded, "No sound detected (all zeros)");
    }

    @Test
    @Disabled("for ai iteration")
    @DisplayName("trace PSG registers on track 1")
    void testTraceTrack1() throws Exception {
        String kssFile = "tmp/Final Fantasy (MSX)(1989)(Microcabin, Square).kss";
        int traceTrack = 1;
        int totalFrames = 120; // ~2 seconds at 60fps

        // Load KSS file directly
        KssEmu emu = new KssEmu();
        byte[] data = DataReader.loadData(Files.newInputStream(Path.of(kssFile)));
        emu.setSampleRate(44100);
        emu.loadFile(data);
        emu.startTrack(traceTrack);

        // Access AyApu via reflection to read registers
        Field ayField = KssEmu.class.getDeclaredField("ay");
        ayField.setAccessible(true);
        AyApu ay = (AyApu) ayField.get(emu);

        // Buffer for play output (stereo 16-bit, ~32ms worth)
        byte[] buf = new byte[44100 * 4 / 30]; // ~32ms of audio

        System.err.println("=== PSG Register Trace: Track " + traceTrack + " ===");
        System.err.println("Frame | ChA Period | ChB Period | ChC Period | VolA | VolB | VolC | Mixer(R7)");
        System.err.println("------+------------+------------+------------+------+------+------+----------");

        int lastPeriodA = -1, lastPeriodB = -1, lastPeriodC = -1;
        int lastVolA = -1, lastVolB = -1, lastVolC = -1;
        int noteChanges = 0;

        for (int frame = 0; frame < totalFrames; frame++) {
            // Run one frame of emulation (~16ms of audio = ~735 samples stereo)
            int count = emu.play(buf, buf.length / 2);

            // Read PSG registers
            int regA_lo = ay.readData(0);
            int regA_hi = ay.readData(1);
            int regB_lo = ay.readData(2);
            int regB_hi = ay.readData(3);
            int regC_lo = ay.readData(4);
            int regC_hi = ay.readData(5);
            int mixer  = ay.readData(7);
            int volA   = ay.readData(8);
            int volB   = ay.readData(9);
            int volC   = ay.readData(10);

            int periodA = ((regA_hi & 0x0F) << 8) | regA_lo;
            int periodB = ((regB_hi & 0x0F) << 8) | regB_lo;
            int periodC = ((regC_hi & 0x0F) << 8) | regC_lo;

            // Only print when something changes (to reduce noise)
            if (periodA != lastPeriodA || periodB != lastPeriodB || periodC != lastPeriodC
                    || volA != lastVolA || volB != lastVolB || volC != lastVolC) {
                // Convert period to approximate frequency for reference
                // PSG freq = 3579545 / (32 * period), but period=0 means channel silent/DC
                String freqA = periodA > 0 ? String.format("%.1fHz", 3579545.0 / (32.0 * periodA)) : "---";
                String freqB = periodB > 0 ? String.format("%.1fHz", 3579545.0 / (32.0 * periodB)) : "---";
                String freqC = periodC > 0 ? String.format("%.1fHz", 3579545.0 / (32.0 * periodC)) : "---";

                System.err.printf("%5d | %4d (%7s) | %4d (%7s) | %4d (%7s) | %4d | %4d | %4d | 0x%02X%n",
                        frame, periodA, freqA, periodB, freqB, periodC, freqC,
                        volA, volB, volC, mixer);

                if (periodA != lastPeriodA || periodB != lastPeriodB || periodC != lastPeriodC) {
                    noteChanges++;
                }
                lastPeriodA = periodA; lastPeriodB = periodB; lastPeriodC = periodC;
                lastVolA = volA; lastVolB = volB; lastVolC = volC;
            }
        }

        System.err.println("------+------------+------------+------------+------+------+------+----------");
        System.err.println("Total note period changes: " + noteChanges);
        System.err.println("Track ended: " + emu.trackEnded());

        // Verify that we saw some note activity
        org.junit.jupiter.api.Assertions.assertTrue(noteChanges > 0,
                "Expected at least some note changes in 2 seconds of playback");
    }
}
