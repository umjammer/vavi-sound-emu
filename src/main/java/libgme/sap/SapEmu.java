/*
 * Copyright (C) 2006 Shay Green.
 *
 * This module is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This module is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this module; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package libgme.sap;

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;

import libgme.ClassicEmu;
import vavi.util.ByteUtil;


/**
 * Atari XL/XE SAP music file emulator
 *
 * @see "https://www.slack.net/~ant"
 */
public final class SapEmu extends ClassicEmu {

    public static final String MAGIC = "SAP\r\n";

    /** Header info of currently loaded file */
    public static class Info {
        public int romData;
        public int initAddr = -1;
        public int playAddr = -1;
        public int musicAddr = -1;
        public int type = 'B';
        public int trackCount = 1;
        public int fastplay = 312;
        public boolean stereo;
        public String author = "";
        public String name = "";
        public String copyright = "";
    }

    @Override
    protected int parseHeader(byte[] in) {
        data = in;
        info = new Info();
        parseInfo(in, info);

        playPeriod = info.fastplay * baseScanlinePeriod;

        setClockRate(clockRate);
        for (int i = 0; i < SapApu.oscCount; i++) {
            apu.oscOutput(i, info.stereo ? buf.left() : buf.center());
            if (info.stereo)
                apu2.oscOutput(i, buf.right());
        }

        return info.trackCount;
    }

    @Override
    public String getMagic() {
        return MAGIC;
    }

    @Override
    public boolean isSupportedByName(String name) {
        return name.endsWith(".SAP");
    }

    // Track info

    /** @return 16 or greater if not hex */
    private static int fromHexChar(int h) {
        h -= 0x30;
        if (h < 0 || h > 9)
            h = ((h - 0x11) & 0xDF) + 10;
        return h;
    }

    private static int fromHex(byte[] in, int pos) {
        if (pos + 4 > in.length)
            return -1;
        int result = 0;
        for (int n = 4; n-- != 0; ) {
            int h = fromHexChar(in[pos++] & 0xff);
            if (h > 15)
                return -1;
            result = result * 0x10 + h;
        }
        return result;
    }

    private static int fromDec(byte[] in, int pos, int end) {
        if (pos >= end)
            return -1;

        int n = 0;
        while (pos < end) {
            int dig = (in[pos++] & 0xff) - '0';
            if (dig < 0 || dig > 9)
                return -1;
            n = n * 10 + dig;
        }
        return n;
    }

    private static String parseString(byte[] in, int pos, int end, int len) {
        int start = pos;
        if ((in[pos++] & 0xff) == '\"') {
            start++;
            while (pos < end && (in[pos] & 0xff) != '\"')
                pos++;
        } else {
            pos = end;
        }
        len = Math.min(len - 1, pos - start);
        return new String(in, start, len, StandardCharsets.ISO_8859_1);
    }

    private static void parseInfo(byte[] in, Info out) {
        if (in.length < 16 || !isHeader(in, MAGIC))
            throw new IllegalArgumentException("Not a SAP file");

        int fileEnd = in.length - 5;
        int pos = 5;
        while (pos < fileEnd && ((in[pos] & 0xff) != 0xFF || (in[pos + 1] & 0xff) != 0xFF)) {
            int lineEnd = pos;
            while (lineEnd < fileEnd && (in[lineEnd] & 0xff) != 0x0D)
                lineEnd++;

            int tag = pos;
            while (pos < lineEnd && (in[pos] & 0xff) > ' ')
                pos++;
            String tagStr = new String(in, tag, pos - tag, StandardCharsets.ISO_8859_1);

            while (pos < lineEnd && (in[pos] & 0xff) <= ' ')
                pos++;

            if (tagStr.isEmpty()) {
                // skip line
            } else if ("INIT".startsWith(tagStr)) {
                out.initAddr = fromHex(in, pos);
                if (out.initAddr < 0 || out.initAddr > 0xFFFF)
                    throw new IllegalArgumentException("Invalid init address");
            } else if ("PLAYER".startsWith(tagStr)) {
                out.playAddr = fromHex(in, pos);
                if (out.playAddr < 0 || out.playAddr > 0xFFFF)
                    throw new IllegalArgumentException("Invalid play address");
            } else if ("MUSIC".startsWith(tagStr)) {
                out.musicAddr = fromHex(in, pos);
                if (out.musicAddr < 0 || out.musicAddr > 0xFFFF)
                    throw new IllegalArgumentException("Invalid music address");
            } else if ("SONGS".startsWith(tagStr)) {
                out.trackCount = fromDec(in, pos, lineEnd);
                if (out.trackCount <= 0)
                    throw new IllegalArgumentException("Invalid track count");
            } else if ("TYPE".startsWith(tagStr)) {
                switch (out.type = in[pos] & 0xff) {
                    case 'C':
                    case 'B':
                        break;

                    case 'D':
                        throw new IllegalArgumentException("Digimusic not supported");

                    default:
                        throw new IllegalArgumentException("Unsupported player type");
                }
            } else if ("STEREO".startsWith(tagStr)) {
                out.stereo = true;
            } else if ("FASTPLAY".startsWith(tagStr)) {
                out.fastplay = fromDec(in, pos, lineEnd);
                if (out.fastplay <= 0)
                    throw new IllegalArgumentException("Invalid fastplay value");
            } else if ("AUTHOR".startsWith(tagStr)) {
                out.author = parseString(in, pos, lineEnd, 256);
            } else if ("NAME".startsWith(tagStr)) {
                out.name = parseString(in, pos, lineEnd, 256);
            } else if ("DATE".startsWith(tagStr)) {
                out.copyright = parseString(in, pos, lineEnd, 32);
            }

            pos = lineEnd + 2;
        }

        if ((in[pos] & 0xff) != 0xFF || (in[pos + 1] & 0xff) != 0xFF)
            throw new IllegalArgumentException("ROM data missing");
        out.romData = pos + 2;
    }

    public Info info() {
        return info;
    }

    // private

    static final int baseScanlinePeriod = 114;
    static final int clockRate = 1773447;
    static final int idleAddr = SapCpu.idleAddr;

    /** 64K ram + padding so that instruction fetches and reads past the end are harmless */
    static final int ramSize = 0x10000 + 0x100;

    final SapCpu cpu = new SapCpu();
    final SapApu apu = new SapApu();
    final SapApu apu2 = new SapApu();
    final byte[] ram = new byte[ramSize];

    byte[] data;
    Info info = new Info();
    int endTime;
    int playPeriod;
    int nextPlay;
    /** 0 disables sound during init, -1 otherwise */
    int timeMask;

    public SapEmu() {
        cpu.reader = this::cpuRead;
        cpu.writer = this::cpuWrite;
    }

    // Emulation

    private int time() {
        return cpu.time + endTime;
    }

    private void cpuJsr(int addr) {
        cpu.pc = addr & 0xffff;
        int highByte = (idleAddr - 1) >> 8;
        if (cpu.s == 0xFE && ram[0x1FF] == (byte) highByte)
            cpu.s = 0xFF; // pop extra byte off
        ram[0x100 + cpu.s] = (byte) highByte; // some routines use RTI to return
        cpu.s = (cpu.s - 1) & 0xff;
        ram[0x100 + cpu.s] = (byte) highByte;
        cpu.s = (cpu.s - 1) & 0xff;
        ram[0x100 + cpu.s] = (byte) ((idleAddr - 1) & 0xFF);
        cpu.s = (cpu.s - 1) & 0xff;
    }

    private void runRoutine(int addr) {
        cpuJsr(addr);
        cpu.time = -(312 * baseScanlinePeriod * 60);
        cpu.runCpu();
        if (cpu.time < 0 && cpu.pc != idleAddr)
            logger.log(Level.WARNING, "%s stopped at $%04X".formatted(getClass().getSimpleName(), cpu.pc));
    }

    private void callInit(int track) {
        switch (info.type) {
            case 'B':
                cpu.a = track;
                runRoutine(info.initAddr);
                break;

            case 'C':
                cpu.a = 0x70;
                cpu.x = info.musicAddr & 0xFF;
                cpu.y = info.musicAddr >> 8;
                runRoutine(info.playAddr + 3);
                cpu.a = 0;
                cpu.x = track;
                runRoutine(info.playAddr + 3);
                break;
        }
    }

    @Override
    public void startTrack(int track) {
        super.startTrack(track);

        java.util.Arrays.fill(ram, (byte) 0);

        int in = info.romData;
        while (data.length - in >= 5) {
            int start = ByteUtil.readLeShort(data, in) & 0xffff;
            int end = ByteUtil.readLeShort(data, in + 2) & 0xffff;
            in += 4;
            if (end < start) {
                logger.log(Level.WARNING, "Invalid file data block");
                break;
            }
            int len = end - start + 1;
            if (len > data.length - in) {
                logger.log(Level.WARNING, "Invalid file data block");
                break;
            }

            System.arraycopy(data, in, ram, start, len);
            in += len;
            if (data.length - in >= 2 && (data[in] & 0xff) == 0xFF && (data[in + 1] & 0xff) == 0xFF)
                in += 2;
        }

        apu.reset();
        apu2.reset();
        cpu.reset(ram);
        endTime = 0;
        timeMask = 0; // disables sound during init
        callInit(track);
        timeMask = -1;

        nextPlay = playPeriod;
    }

    private void callPlay() {
        switch (info.type) {
            case 'B':
                cpuJsr(info.playAddr);
                break;

            case 'C':
                cpuJsr(info.playAddr + 6);
                break;
        }
    }

    @Override
    protected int runClocks(int clockCount) {
        endTime = clockCount;
        cpu.time = -endTime;

        while (true) {
            cpu.runCpu();
            if (cpu.time >= 0)
                break;

            if (cpu.pc != idleAddr) {
                setTrackEnded();
                logger.log(Level.ERROR, "emulation error (illegal instruction)");
                return endTime;
            }

            // Next play call
            int next = nextPlay - endTime;
            if (cpu.time < next) {
                cpu.time = 0;
                if (next > 0)
                    break;
                cpu.time = next;
            }

            nextPlay += playPeriod;
            callPlay();
        }

        // End time frame
        endTime += cpu.time;
        nextPlay -= endTime;
        if (nextPlay < 0) // could go negative if routine is taking too long to return
            nextPlay = 0;
        apu.endFrame(endTime);
        if (info.stereo)
            apu2.endFrame(endTime);

        return endTime;
    }

    @Override
    protected void mixSamples(byte[] out, int offset, int count) {
    }

    private int cpuRead(int addr) {
        return ram[addr] & 0xff;
    }

    private void cpuWrite(int addr, int data) {
        ram[addr] = (byte) data;
        if ((addr >> 8) == 0xD2)
            cpuWrite_(addr, data);
    }

    private void cpuWrite_(int addr, int data) {
        if ((addr ^ SapApu.startAddr) <= (SapApu.endAddr - SapApu.startAddr)) {
            apu.writeData(time() & timeMask, addr, data);
            return;
        }

        if ((addr ^ (SapApu.startAddr + 0x10)) <= (SapApu.endAddr - SapApu.startAddr) &&
                info.stereo) {
            apu2.writeData(time() & timeMask, addr ^ 0x10, data);
            return;
        }

        if ((addr & ~0x0010) != 0xD20F || data != 0x03)
            logger.log(Level.DEBUG, "Unmapped write $%04X <- $%02X".formatted(addr, data));
    }
}
