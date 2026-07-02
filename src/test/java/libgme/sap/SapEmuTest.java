/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package libgme.sap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * SapEmuTest.
 * <p>
 * Headless tests rendering into a buffer, no audio device needed.
 * A synthetic tune (hand-assembled 6502) is generated in code; real rips
 * are tested from the directory given by {@code sap.dir=...} in
 * local.properties (default {@code tmp}), skipped when none are present.
 * Real files can be downloaded from the Atari SAP Music Archive
 * (https://asma.atari.org/).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-02 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class SapEmuTest {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    /** directory scanned for real *.sap rips */
    @Property(name = "sap.dir")
    String sapDir = "tmp/sap";

    /** optional real stereo (dual POKEY) rip */
    @Property(name = "sap.stereo")
    String sapStereo = "tmp/sap/Atari_Style.sap";

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    // synthetic SAP building

    /**
     * TYPE B tune: INIT sets a pure tone on POKEY channel 1,
     * PLAY decrements AUDF1 each frame (siren).
     */
    static byte[] synthSap() {
        byte[] init = {
                (byte) 0xA9, 0x40,                     // LDA #$40
                (byte) 0x8D, 0x00, (byte) 0xD2,        // STA $D200 (AUDF1)
                (byte) 0xA9, (byte) 0xA8,              // LDA #$A8 (pure tone, vol 8)
                (byte) 0x8D, 0x01, (byte) 0xD2,        // STA $D201 (AUDC1)
                (byte) 0xA9, 0x00,                     // LDA #$00
                (byte) 0x8D, 0x08, (byte) 0xD2,        // STA $D208 (AUDCTL)
                0x60,                                  // RTS
        };
        byte[] play = {
                (byte) 0xCE, 0x00, (byte) 0xD2,        // DEC $D200
                (byte) 0xAD, 0x00, (byte) 0xD2,        // LDA $D200
                (byte) 0xD0, 0x02,                     // BNE +2
                (byte) 0xA9, 0x40,                     // LDA #$40
                (byte) 0x8D, 0x00, (byte) 0xD2,        // STA $D200
                0x60,                                  // RTS
        };

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(("SAP\r\n" +
                "AUTHOR \"test\"\r\n" +
                "NAME \"synth test\"\r\n" +
                "SONGS 2\r\n" +
                "TYPE B\r\n" +
                "INIT 2000\r\n" +
                "PLAYER 2020\r\n").getBytes(StandardCharsets.ISO_8859_1));
        o.write(0xFF); o.write(0xFF);
        // block 1: init routine at $2000
        o.write(0x00); o.write(0x20);
        o.write(init.length - 1); o.write(0x20); // end address is inclusive
        o.writeBytes(init);
        o.write(0xFF); o.write(0xFF);            // optional block separator
        // block 2: play routine at $2020
        o.write(0x20); o.write(0x20);
        o.write(0x20 + play.length - 1); o.write(0x20);
        o.writeBytes(play);
        return o.toByteArray();
    }

    /** @return {total 16-bit samples, non-zero 16-bit samples, max abs} */
    static long[] render(SapEmu emu, int maxReads) {
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
    @DisplayName("synthetic tune plays on both tracks")
    void test1() {
        for (int track = 0; track < 2; track++) {
            SapEmu emu = new SapEmu();
            assertEquals(44100, emu.setSampleRate(44100));
            emu.loadFile(synthSap());
            assertEquals(2, emu.trackCount());
            assertEquals('B', emu.info().type);
            assertEquals("test", emu.info().author);
            assertEquals("synth test", emu.info().name);
            emu.startTrack(track);

            long[] r = render(emu, 50); // ~4.6 s
Debug.println("track " + track + ": samples: " + r[0] + ", nonZero: " + r[1] + ", maxAbs: " + r[2]);
            assertTrue(r[1] > r[0] / 2, "output mostly silent");
            assertTrue(r[2] > 1000, "output too quiet");
            assertFalse(emu.trackEnded(), "sap has no end marker; should still be playing");
        }
    }

    @Test
    @DisplayName("header parse failures")
    void test2() {
        // too short / wrong magic
        assertThrows(IllegalArgumentException.class, () ->
                new SapEmu().loadFile("NOT A SAP FILE AT ALL........".getBytes(StandardCharsets.ISO_8859_1)));

        // digimusic
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                new SapEmu().loadFile(("SAP\r\nTYPE D\r\nPADPADPAD\r\nÿÿXX")
                        .getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(e.getMessage().contains("Digimusic"), e.getMessage());

        // unsupported player type
        assertThrows(IllegalArgumentException.class, () ->
                new SapEmu().loadFile(("SAP\r\nTYPE Z\r\nPADPADPAD\r\nÿÿXX")
                        .getBytes(StandardCharsets.ISO_8859_1)));

        // rom data missing
        assertThrows(IllegalArgumentException.class, () ->
                new SapEmu().loadFile(("SAP\r\nTYPE B\r\nINIT 2000\r\nPLAYER 2010\r\nPADDING")
                        .getBytes(StandardCharsets.ISO_8859_1)));

        // invalid init address
        assertThrows(IllegalArgumentException.class, () ->
                new SapEmu().loadFile(("SAP\r\nINIT XYZW\r\nPADPADPAD\r\nÿÿXX")
                        .getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    @DisplayName("magic detection for spi")
    void test3() throws Exception {
        assertTrue(new SapEmu().isSupported(new ByteArrayInputStream(synthSap())));
        assertFalse(new SapEmu().isSupported(new ByteArrayInputStream("GYMX....".getBytes())));
    }

    @Test
    @DisplayName("real rips render (put *.sap files in tmp, e.g. from asma.atari.org)")
    void test4() throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of(sapDir))) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".sap")).sorted().toList();
        }
        assumeTrue(!files.isEmpty(), "no *.sap files in " + sapDir);

        for (Path f : files) {
            byte[] b = Files.readAllBytes(f);
            SapEmu emu = new SapEmu();
            emu.setSampleRate(44100);
            emu.loadFile(b);
            emu.startTrack(0);

            long[] r = render(emu, 120); // ~11 s
Debug.println("%-30s type=%c stereo=%b tracks=%d: samples: %d, nonZero: %d, maxAbs: %d".formatted(
        f.getFileName(), emu.info().type, emu.info().stereo, emu.trackCount(), r[0], r[1], r[2]));
            assertTrue(r[1] > r[0] / 20, f + " (almost) silent");
            assertTrue(r[2] > 500, f + " too quiet");
        }
    }

    @Test
    @DisplayName("stereo rip drives both channels differently")
    void test5() throws Exception {
        assumeTrue(sapStereo != null && Files.exists(Path.of(sapStereo)), "no stereo sap file: " + sapStereo);

        SapEmu emu = new SapEmu();
        emu.setSampleRate(44100);
        emu.loadFile(Files.readAllBytes(Path.of(sapStereo)));
        assumeTrue(emu.info().stereo, sapStereo + " is not a stereo sap");
        emu.startTrack(0);

        byte[] buf = new byte[8192];
        long left = 0, right = 0, diff = 0;
        for (int r = 0; r < 240 && !emu.trackEnded(); r++) {
            int n = emu.play(buf, buf.length / 2);
            for (int i = 0; i + 3 < n * 2; i += 4) {
                int l = (buf[i] << 8) | (buf[i + 1] & 0xff);
                int rr = (buf[i + 2] << 8) | (buf[i + 3] & 0xff);
                left += Math.abs(l);
                right += Math.abs(rr);
                diff += Math.abs(l - rr);
            }
        }
Debug.println("left: " + left + ", right: " + right + ", diff: " + diff);
        assertTrue(left > 0, "left channel silent");
        assertTrue(right > 0, "right channel silent");
        assertTrue(diff > 0, "channels identical; stereo routing broken");
    }
}
