/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package libgme.gym;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * GymEmuTest.
 * <p>
 * Headless tests rendering into a buffer, no audio device needed.
 * Synthetic GYM streams are generated in code; a real rip can be tested
 * by setting {@code gym=...} in local.properties.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-02 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class GymEmuTest {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    /** optional real GYM rip */
    @Property
    String gym = "tmp/gym/test.gym";

    String endlessBackup;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
        endlessBackup = System.getProperty("libgme.endless");
        System.setProperty("libgme.endless", "false");
    }

    @AfterEach
    void teardown() {
        if (endlessBackup == null)
            System.clearProperty("libgme.endless");
        else
            System.setProperty("libgme.endless", endlessBackup);
    }

    // synthetic GYM building

    static void fm0(ByteArrayOutputStream o, int reg, int val) { o.write(1); o.write(reg); o.write(val); }
    static void fm1(ByteArrayOutputStream o, int reg, int val) { o.write(2); o.write(reg); o.write(val); }
    static void psg(ByteArrayOutputStream o, int val) { o.write(3); o.write(val); }
    static void endFrame(ByteArrayOutputStream o) { o.write(0); }

    /** PSG tone on channel 0, one frame, then {@code frames - 1} empty frames */
    static byte[] psgBody(int frames) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        psg(o, 0x8E); psg(o, 0x0C); // tone ch0
        psg(o, 0x90);               // vol ch0 = max
        endFrame(o);
        for (int f = 1; f < frames; f++)
            endFrame(o);
        return o.toByteArray();
    }

    static byte[] withHeader(byte[] body, int loopStart) {
        byte[] g = new byte[428 + body.length];
        g[0] = 'G'; g[1] = 'Y'; g[2] = 'M'; g[3] = 'X';
        g[420] = (byte) loopStart; // loop start frame, little endian
        System.arraycopy(body, 0, g, 428, body.length);
        return g;
    }

    /** FM note + PSG tone + DAC ramp, 121 frames (~2 s) */
    static byte[] fullSyntheticGym() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // header: GYMX + song/game/... all zero, loop_start=0, packed=0
        out.write('G'); out.write('Y'); out.write('M'); out.write('X');
        for (int i = 4; i < 428; i++) out.write(0);

        // frame 0: set up FM ch0, algorithm 7, all carriers, key on
        fm0(out, 0x22, 0x00); // LFO off
        fm0(out, 0x27, 0x00); // ch3 normal mode
        fm0(out, 0x28, 0x00); // key off ch0
        for (int op = 0; op < 4; op++) {
            int s = op * 4;
            fm0(out, 0x30 + s, 0x01); // DT/MUL
            fm0(out, 0x40 + s, 0x00); // TL = 0 (max)
            fm0(out, 0x50 + s, 0x1F); // AR = max
            fm0(out, 0x60 + s, 0x00); // DR
            fm0(out, 0x70 + s, 0x00); // SR
            fm0(out, 0x80 + s, 0x0F); // SL/RR
        }
        fm0(out, 0xB0, 0x07); // feedback 0 / algorithm 7
        fm0(out, 0xB4, 0xC0); // L+R on
        fm0(out, 0xA4, 0x22); // freq high / block
        fm0(out, 0xA0, 0x69); // freq low
        fm0(out, 0x28, 0xF0); // key on ch0, all slots

        // PSG: ch0 tone + full volume
        psg(out, 0x8E); psg(out, 0x0C);
        psg(out, 0x90);
        endFrame(out);

        // frames 1..59: hold the note
        for (int f = 1; f < 60; f++) endFrame(out);

        // frames 60..119: DAC ramp (sine-ish), ~15 samples per frame
        fm0(out, 0x2B, 0x80); // DAC enable
        for (int f = 60; f < 120; f++) {
            for (int i = 0; i < 15; i++) {
                int v = 0x80 + (int) (100 * Math.sin((f * 15 + i) * 0.15));
                fm0(out, 0x2A, v);
            }
            endFrame(out);
        }
        // also exercise port 1 write path
        fm1(out, 0xB6, 0xC0);
        endFrame(out);

        return out.toByteArray();
    }

    /** @return {total 16-bit samples, non-zero 16-bit samples, max abs} */
    static long[] render(GymEmu emu, int maxReads) {
        byte[] buf = new byte[8192];
        long total = 0, nonZero = 0, maxAbs = 0;
        int reads = 0;
        while (!emu.trackEnded() && reads++ < maxReads) {
            int n = emu.play(buf, buf.length / 2);
            for (int i = 0; i < n * 2; i += 2) {
                int s = (buf[i] << 8) | (buf[i + 1] & 0xff);
                if (s != 0) nonZero++;
                maxAbs = Math.max(maxAbs, Math.abs(s));
            }
            total += n;
        }
        return new long[] {total, nonZero, maxAbs};
    }

    @Test
    @DisplayName("FM + PSG + DAC all sound, duration matches frame count")
    void test1() {
        GymEmu emu = new GymEmu();
        assertEquals(44100, emu.setSampleRate(44100));
        emu.loadFile(fullSyntheticGym());
        assertEquals(1, emu.trackCount());
        emu.startTrack(0);

        long[] r = render(emu, 200);
Debug.println("samples: " + r[0] + ", nonZero: " + r[1] + ", maxAbs: " + r[2]);
        assertTrue(emu.trackEnded(), "track should end");
        // 121 frames at 60 fps ~= 2.02 s ~= 178k 16-bit samples (stereo)
        long expected = (long) (121 / 60.0 * 44100) * 2;
        assertTrue(Math.abs(r[0] - expected) < 44100 / 5, "duration off: " + r[0] + " vs " + expected);
        assertTrue(r[1] > r[0] / 2, "output mostly silent");
        assertTrue(r[2] > 1000, "output too quiet");
    }

    @Test
    @DisplayName("looped track plays forever when endless")
    void test2() {
        System.setProperty("libgme.endless", "true");
        GymEmu emu = new GymEmu();
        emu.setSampleRate(44100);
        emu.loadFile(withHeader(psgBody(30), 10)); // loop at frame 10
        emu.startTrack(0);

        byte[] buf = new byte[8192];
        for (int i = 0; i < 100; i++)
            emu.play(buf, buf.length / 2);
        assertFalse(emu.trackEnded(), "endless loop should keep playing");
    }

    @Test
    @DisplayName("looped track ends when not endless")
    void test3() {
        GymEmu emu = new GymEmu();
        emu.setSampleRate(44100);
        emu.loadFile(withHeader(psgBody(30), 10));
        emu.startTrack(0);

        long[] r = render(emu, 100);
Debug.println("samples: " + r[0]);
        assertTrue(emu.trackEnded(), "should end at loop point when not endless");
    }

    @Test
    @DisplayName("headerless GYM (first byte <= 3) is accepted")
    void test4() {
        GymEmu emu = new GymEmu();
        emu.setSampleRate(44100);
        emu.loadFile(psgBody(30));
        emu.startTrack(0);

        long[] r = render(emu, 100);
Debug.println("samples: " + r[0] + ", nonZero: " + r[1]);
        assertTrue(r[1] > 0, "headerless GYM should produce sound");
        assertTrue(emu.trackEnded());
    }

    @Test
    @DisplayName("packed GYM and garbage are rejected")
    void test5() {
        byte[] packed = withHeader(psgBody(1), 0);
        packed[424] = 1; // packed flag
        assertThrows(IllegalArgumentException.class, () -> new GymEmu().loadFile(packed));

        byte[] garbage = {(byte) 0x7f, 0x45, 0x4c, 0x46};
        assertThrows(IllegalArgumentException.class, () -> new GymEmu().loadFile(garbage));
    }

    @Test
    @DisplayName("magic detection for spi")
    void test6() throws Exception {
        assertTrue(new GymEmu().isSupported(new ByteArrayInputStream(withHeader(psgBody(1), 0))));
        assertFalse(new GymEmu().isSupported(new ByteArrayInputStream("Vgm ....".getBytes())));
    }

    @Test
    @DisplayName("real GYM rip (needs gym=... in local.properties)")
    @EnabledIf("localPropertiesExists")
    void test7() throws Exception {
        assumeTrue(gym != null && Files.exists(Path.of(gym)), "no real gym file: " + gym);

        GymEmu emu = new GymEmu();
        emu.setSampleRate(44100);
        emu.loadFile(Files.readAllBytes(Path.of(gym)));
        emu.startTrack(0);

        long[] r = render(emu, 120); // ~11 s
Debug.println(gym + ": samples: " + r[0] + ", nonZero: " + r[1] + ", maxAbs: " + r[2]);
        assertTrue(r[1] > r[0] / 20, "real gym (almost) silent");
    }
}
