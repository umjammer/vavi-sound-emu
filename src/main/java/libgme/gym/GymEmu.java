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

package libgme.gym;

import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat.Encoding;

import libgme.ClassicEmu;
import libgme.vgm.SmsApu;
import libgme.vgm.YM2612;
import vavi.sound.sampled.emu.EmuEncoding;
import vavi.sound.sampled.emu.EmuFileFormatType;
import vavi.util.ByteUtil;


/**
 * Sega Genesis/Mega Drive GYM music file emulator
 * <p>
 * Includes PCM timing recovery to improve sample quality.
 *
 * @see "https://www.slack.net/~ant"
 */
public final class GymEmu extends ClassicEmu {

    public static final String MAGIC = "GYMX";

    @Override
    protected int parseHeader(byte[] data) {
        int offset = 0;
        loopStart = 0;
        if (data.length >= 4 && isHeader(data, MAGIC)) {
            if (data.length < headerSize + 1)
                throw new IllegalArgumentException("Not a GYM file");
            if (ByteUtil.readLeInt(data, packedOff) != 0)
                throw new IllegalArgumentException("Packed GYM file not supported");
            offset = headerSize;
            loopStart = ByteUtil.readLeInt(data, loopStartOff);
        } else if (data.length < 1 || (data[0] & 0xff) > 3) {
            throw new IllegalArgumentException("Not a GYM file");
        }

        this.data = data;
        this.dataOffset = offset;

        setClockRate(clockRate);
        buf.setVolume(0.7);
        fm.init(baseClock / 7, sampleRate());
        apu.setOutput(buf.center(), buf.left(), buf.right());

        return 1;
    }

    @Override
    public String getMagic() {
        return MAGIC;
    }

    @Override
    public boolean isSupportedByName(String name) {
        return name.endsWith(".GYM");
    }

    // private

    /** GYM file header size, tag + song + game + copyright + emulator + dumper + comment */
    static final int headerSize = 428;
    /** loop start frame in 1/60 seconds, 0 if not looped */
    static final int loopStartOff = 420;
    static final int packedOff = 424;

    static final int baseClock = 53700300;
    static final int clockRate = baseClock / 15;
    /** GYM sequence runs at 60 frames per second */
    static final int clocksPerFrame = clockRate / 60;

    /** fixed-point fraction bits for spacing dac samples within a frame */
    static final int dacTimeBits = 8;
    /** dac delta amplification, same as VgmEmu pcm write */
    static final int dacGain = 300;

    final SmsApu apu = new SmsApu();
    final YM2612 fm = new YM2612();

    byte[] data;
    /** sequence data begin */
    int dataOffset;
    /** current position */
    int pos;
    /** loop begin, -1 until it has been located */
    int loopBegin;
    int loopStart;
    /** frames remaining until loop beginning has been located */
    int loopRemain;
    /** clocks of last frame extending into next buffer run */
    int delay;

    // dac (pcm)
    int dacAmp;
    int prevDacCount;
    boolean dacEnabled;
    final byte[] dacBuf = new byte[1024];

    /** FM sample buffer, PSG sample buffer is {@link #buf} */
    final int[] fm_buf_lr = new int[48000 / 10 * 2];
    int fm_pos;

    @Override
    public void startTrack(int track) {
        super.startTrack(track);

        pos = dataOffset;
        loopBegin = -1;
        loopRemain = loopStart;
        delay = 0;
        fm_pos = 0;

        prevDacCount = 0;
        dacEnabled = false;
        dacAmp = -1;

        fm.reset();
        apu.reset();
    }

    private void runFM(int time) {
        int count = countSamples(time) - fm_pos;
        if (count > 0) {
            fm.update(fm_buf_lr, fm_pos, count);
            fm_pos += count;
        }
    }

    /** Guesses beginning and end of sample and adjusts rate and buffer position accordingly */
    private void runDac(int time, int dacCount) {
        // count dac samples in next frame
        int nextDacCount = 0;
        int p = pos;
        int cmd;
        while (p < data.length && (cmd = data[p++] & 0xff) != 0) {
            int data1 = data[p++] & 0xff;
            if (cmd <= 2)
                ++p;
            if (cmd == 1 && data1 == 0x2A)
                nextDacCount++;
        }

        // detect beginning and end of sample
        int rateCount = dacCount;
        int start = 0;
        if (prevDacCount == 0 && nextDacCount != 0 && dacCount < nextDacCount) {
            rateCount = nextDacCount;
            start = nextDacCount - dacCount;
        } else if (prevDacCount != 0 && nextDacCount == 0 && dacCount < prevDacCount) {
            rateCount = prevDacCount;
        }

        // evenly space samples within buffer section being used
        int period = (clocksPerFrame << dacTimeBits) / rateCount;
        int dacTime = (time << dacTimeBits) + period * start + (period >> 1);

        int dacAmp = this.dacAmp;
        if (dacAmp < 0)
            dacAmp = dacBuf[0] & 0xff;

        for (int i = 0; i < dacCount; i++) {
            int delta = (dacBuf[i] & 0xff) - dacAmp;
            dacAmp += delta;
            buf.center().addDelta(dacTime >> dacTimeBits, delta * dacGain);
            dacTime += period;
        }
        this.dacAmp = dacAmp;
    }

    private void parseFrame(int time) {
        int dacCount = 0;
        int pos = this.pos;

        if (loopRemain != 0 && --loopRemain == 0)
            loopBegin = pos; // find loop on first time through sequence

        runFM(time); // writes below take effect at the beginning of this frame

        int cmd;
        while (pos < data.length && (cmd = data[pos++] & 0xff) != 0) {
            if (cmd > 3) {
                // to do: many GYM streams are full of errors, and error count should
                // reflect cases where music is really having problems
                continue;
            }
            int data1 = data[pos++] & 0xff;
            if (cmd == 1) {
                int data2 = data[pos++] & 0xff;
                if (data1 != 0x2A) {
                    if (data1 == 0x2B)
                        dacEnabled = (data2 & 0x80) != 0;

                    fm.write0(data1, data2);
                } else if (dacCount < dacBuf.length) {
                    dacBuf[dacCount] = (byte) data2;
                    if (dacEnabled)
                        dacCount++;
                }
            } else if (cmd == 2) {
                fm.write1(data1, data[pos++] & 0xff);
            } else { // cmd == 3
                apu.writeData(time, data1);
            }
        }

        // loop
        if (pos >= data.length) {
            if (loopBegin >= 0) {
                pos = loopBegin;
                if (!isEndlessLoopFlag())
                    setTrackEnded();
            } else {
                setTrackEnded();
            }
        }
        this.pos = pos;

        // dac
        if (dacCount != 0)
            runDac(time, dacCount);
        prevDacCount = dacCount;
    }

    @Override
    protected int runMsec(int msec) {
        int duration = (int) ((long) clockRate * msec / 1000);

        {
            int sampleCount = countSamples(duration);
            Arrays.fill(fm_buf_lr, 0, sampleCount * 2, 0);
        }
        fm_pos = 0;

        int time = delay;
        while (time < duration && !trackEnded) {
            parseFrame(time);
            time += clocksPerFrame;
        }
        delay = time - duration;

        runFM(duration);
        apu.endFrame(duration);

        fm_pos = 0;

        return duration;
    }

    @Override
    protected void mixSamples(byte[] out, int out_off, int count) {
        out_off *= 2;
        int in_off = fm_pos;

        while (--count >= 0) {
            int s = (out[out_off] << 8) + (out[out_off + 1] & 0xff);
            s = (s >> 2) + fm_buf_lr[in_off];
            in_off++;
            if ((short) s != s)
                s = (s >> 31) ^ 0x7fff;
            out[out_off] = (byte) (s >> 8);
            out_off++;
            out[out_off] = (byte) s;
            out_off++;
        }

        fm_pos = in_off;
    }

    @Override
    public Encoding getEncoding() {
        return new EmuEncoding("GYM");
    }

    @Override
    public Type getType() {
        return new EmuFileFormatType("GYM", "gym");
    }
}
