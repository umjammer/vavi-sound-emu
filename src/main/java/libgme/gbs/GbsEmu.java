/*
 * Copyright (C) 2003-2007 Shay Green.
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

package libgme.gbs;

import java.lang.System.Logger.Level;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat.Encoding;

import libgme.ClassicEmu;
import libgme.util.MemPager;
import vavi.sound.sampled.emu.EmuEncoding;
import vavi.sound.sampled.emu.EmuFileFormatType;
import vavi.util.ByteUtil;


/**
 * Nintendo Game Boy GBS music file emulator
 *
 * @see "https://www.slack.net/~ant"
 */
public final class GbsEmu extends ClassicEmu {

    // header offsets
    static final int trackCountOff = 0x04;
    static final int loadAddrOff = 0x06;
    static final int initAddrOff = 0x08;
    static final int playAddrOff = 0x0A;
    static final int stackPtrOff = 0x0C;
    static final int timerModuloOff = 0x0E;
    static final int timerModeOff = 0x0F;

    // memory addresses
    static final int idleAddr = 0xF00D;
    static final int ramAddr = 0xA000;
    static final int hiPage = 0xff00 - ramAddr;

    static final int ramSize = 0x4000 + 0x2000;
    static final int bankSize = 0x4000;

    GbCpu cpu = new GbCpu();
    final MemPager rom = new MemPager(bankSize, ramSize);
    final byte[] header = new byte[0x70];
    byte[] ram;

    int endTime;
    int playPeriod;
    int nextPlay;

    GbApu apu = new GbApu();

    public static final String MAGIC = "GBS\u0001";

    public GbsEmu() {
        cpu.reader = this::cpuRead;
        cpu.writer = this::cpuWrite;
    }

    @Override
    protected int parseHeader(byte[] in) {
        if (!isHeader(in, MAGIC))
            throw new IllegalArgumentException("Not a GBS file");

        cpu.rstBase = ByteUtil.readLeShort(in, loadAddrOff) & 0xffff;
        ram = rom.load(in, header, cpu.rstBase, 0xff);

        setClockRate(4194304);
        apu.setOutput(buf.center(), buf.left(), buf.right());

        return header[trackCountOff] & 0xff;
    }

    @Override
    public String getMagic() {
        return MAGIC;
    }

    void setBank(int n) {
        int addr = rom.maskAddr(n * bankSize);
        if (addr == 0 && rom.size() > bankSize)
            n = 1;
        cpu.mapMemory(bankSize, bankSize, rom.mapAddr(addr));
    }

    static final byte[] rates = {10, 4, 6, 8};

    void updateTimer() {
        playPeriod = 70224; // 59.73 Hz
        if ((header[timerModeOff] & 0x04) != 0) {
            int shift = rates[ram[hiPage + 7] & 3] - (header[timerModeOff] >> 7 & 1);
            playPeriod = (256 - (ram[hiPage + 6] & 0xff)) << shift;
        }
    }

    static final int[] sound_data = {
            0x80, 0xBF, 0x00, 0x00, 0xBF, // square 1
            0x00, 0x3F, 0x00, 0x00, 0xBF, // square 2
            0x7F, 0xff, 0x9F, 0x00, 0xBF, // wave
            0x00, 0xff, 0x00, 0x00, 0xBF, // noise
            0x77, 0xff, 0x80, // vin/volume, status, power mode
            0, 0, 0, 0, 0, 0, 0, 0, 0, // unused
            0xAC, 0xDD, 0xDA, 0x48, 0x36, 0x02, 0xCF, 0x16,    // waveform data
            0x2C, 0x04, 0xE5, 0x2C, 0xAC, 0xDD, 0xDA, 0x48
    };

    void cpuCall(int addr) {
        assert cpu.sp == (ByteUtil.readLeShort(header, stackPtrOff) & 0xffff);
        cpu.pc = addr;
        cpuWrite(--cpu.sp, idleAddr >> 8);
        cpuWrite(--cpu.sp, idleAddr & 0xff);
    }

    @Override
    public void startTrack(int track) {
        super.startTrack(track);

        apu.reset();
        apu.write(0, 0xff26, 0x80); // power on
        for (int i = 0; i < sound_data.length; i++) {
            apu.write(0, i + apu.startAddr, sound_data[i]);
        }

        cpu.reset(ram, rom.unmapped());
        cpu.mapMemory(ramAddr, 0x10000 - ramAddr, 0);
        cpu.mapMemory(0, bankSize, rom.mapAddr(0));
        setBank(1);

        java.util.Arrays.fill(ram, 0, 0x4000, (byte) 0);
        java.util.Arrays.fill(ram, 0x4000, 0x5F80, (byte) 0xff);
        java.util.Arrays.fill(ram, 0x5F80, ramSize, (byte) 0);

        ram[hiPage] = 0; // joypad reads back as 0
        ram[cpu.mapAddr(idleAddr)] = (byte) 0xED; // illegal instruction

        ram[hiPage + 6] = header[timerModuloOff];
        ram[hiPage + 7] = header[timerModeOff];
        updateTimer();
        nextPlay = playPeriod;

        cpu.a = track;
        cpu.pc = idleAddr;
        cpu.sp = ByteUtil.readLeShort(header, stackPtrOff) & 0xffff;
        cpuCall(ByteUtil.readLeShort(header, initAddrOff) & 0xffff);
    }

    @Override
    protected void mixSamples(byte[] out, int offset, int count) {
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
                // TODO: PC overflow handling
                cpu.pc = (cpu.pc + 1) & 0xffFF;
                setTrackEnded();
                logger.log(Level.ERROR, "emulation error");
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
            cpuCall(ByteUtil.readLeShort(header, playAddrOff) & 0xffff);
        }

        // End time frame
        endTime += cpu.time;
        nextPlay -= endTime;
        if (nextPlay < 0) // could go negative if routine is taking too long to return
            nextPlay = 0;
        apu.endFrame(endTime);

        return endTime;
    }

    private int cpuRead(int addr) {
        assert 0 <= addr && addr < 0x10000;

        if (GbApu.startAddr <= addr && addr <= GbApu.endAddr)
            return apu.read(cpu.time + endTime, addr);

        return ram[cpu.mapAddr(addr)] & 0xff;
    }

    private void cpuWrite(int addr, int data) {
        assert 0 <= data && data < 0x100;
        assert 0 <= addr && addr < 0x10000;

        int offset = addr - ramAddr;
        if (offset >= 0) {
            ram[offset] = (byte) data;
            if (addr < 0xff80 && addr >= 0xff00) {
                if (GbApu.startAddr <= addr && addr <= GbApu.endAddr) {
                    apu.write(cpu.time + endTime, addr, data);
                } else if ((addr ^ 0xff06) < 2) {
                    updateTimer();
                } else if (addr == 0xff00) {
                    ram[offset] = 0; // keep joypad return value 0
                } else {
                    ram[offset] = (byte) 0xff;
                }
            }
        } else if ((addr ^ 0x2000) <= 0x2000 - 1) {
            setBank(data);
        }
    }

    @Override
    public boolean isSupportedByName(String name) {
        return name.endsWith(".GBS");
    }

    @Override
    public Encoding getEncoding() {
        return new EmuEncoding("GBS");
    }

    @Override
    public Type getType() {
        return new EmuFileFormatType("GBS", "gbs");
    }
}
