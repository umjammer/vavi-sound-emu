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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import libgme.util.BlipBuffer;

import static java.lang.System.getLogger;


/**
 * Atari POKEY sound chip emulator
 *
 * @see "https://www.slack.net/~ant"
 */
public final class SapApu {

    private static final Logger logger = getLogger(SapApu.class.getName());

    public static final int oscCount = 4;

    public static final int startAddr = 0xD200;
    public static final int endAddr = 0xD209;

    /** pure waves above this frequency are silenced */
    static final int maxFrequency = 12000;

    static final int poly4Len = (1 << 4) - 1;
    static final int poly9Len = (1 << 9) - 1;
    static final int poly17Len = (1 << 17) - 1;

    static final int poly5Len = (1 << 5) - 1;
    static final int poly5Mask = (1 << poly5Len) - 1;
    static final int poly5 = 0x167C6EA1;

    private static class Osc {
        final byte[] regs = new byte[2];
        int phase;
        int invert;
        int lastAmp;
        /** always recalculated before use; here for convenience */
        int delay;
        int period;
        BlipBuffer output;

        void reset() {
            regs[0] = 0;
            regs[1] = 0;
            phase = 0;
            invert = 0;
            lastAmp = 0;
            delay = 0;
            period = 0;
        }
    }

    private final Osc[] oscs = new Osc[oscCount];
    private int lastTime;
    private int poly5Pos;
    private int poly4Pos;
    private int polymPos;
    private int control;

    private double volFactor;

    // common tables shared among all SapApu objects (C++ Sap_Apu_Impl)

    static final byte[] poly4 = new byte[poly4Len / 8 + 1];
    static final byte[] poly9 = new byte[poly9Len / 8 + 1];
    static final byte[] poly17 = new byte[poly17Len / 8 + 1];
    /** square wave, can be just 2 bits, but this is faster */
    static final byte[] poly1 = {0x55, 0x55};

    static int polyMask(int width, int tap1, int tap2) {
        return (1 << (width - 1 - tap1)) | (1 << (width - 1 - tap2));
    }

    static void genPoly(int mask, byte[] out) {
        int n = 1;
        for (int i = 0; i < out.length; i++) {
            int bits = 0;
            int b = 0;
            do {
                // implemented using "Galois configuration"
                bits |= (n & 1) << b;
                n = (n >> 1) ^ (mask & -(n & 1));
            } while (b++ < 7);
            out[i] = (byte) bits;
        }
    }

    static {
        genPoly(polyMask(4, 1, 0), poly4);
        genPoly(polyMask(9, 5, 0), poly9);
        genPoly(polyMask(17, 5, 0), poly17);
    }

    static int runPoly5(int in, int shift) {
        return (in << shift & poly5Mask) | (in >>> (poly5Len - shift));
    }

    public SapApu() {
        for (int i = 0; i < oscCount; i++) {
            oscs[i] = new Osc();
        }
        volume(1.0);
    }

    public void oscOutput(int i, BlipBuffer b) {
        assert 0 <= i && i < oscCount;
        oscs[i].output = b;
    }

    public void volume(double v) {
        this.volFactor = 1.0 / oscCount / 30 * v * 32768;
    }

    public void reset() {
        lastTime = 0;
        poly5Pos = 0;
        poly4Pos = 0;
        polymPos = 0;
        control = 0;

        for (int i = 0; i < oscCount; i++)
            oscs[i].reset();
    }

    static final byte[] fastBits = {1 << 6, 1 << 4, 1 << 5, 1 << 3};

    private void calcPeriods() {
        // 15/64 kHz clock
        int divider = 28;
        if ((this.control & 1) != 0)
            divider = 114;

        for (int i = 0; i < oscCount; i++) {
            Osc osc = oscs[i];

            int oscReload = osc.regs[0] & 0xff; // cache
            int period = (oscReload + 1) * divider;
            if ((this.control & fastBits[i]) != 0) {
                period = oscReload + 4;
                if ((i & 1) != 0) {
                    period = oscReload * 0x100 + (oscs[i - 1].regs[0] & 0xff) + 7;
                    if ((this.control & fastBits[i - 1]) == 0)
                        period = (period - 6) * divider;

                    if (((oscs[i - 1].regs[1] & 0xff) & 0x1F) > 0x10)
                        logger.log(Level.DEBUG, "Use of slave channel in 16-bit mode not supported");
                }
            }
            osc.period = period;
        }
    }

    static final byte[] hipassBits = {1 << 2, 1 << 1, 0, 0};

    private void runUntil(int endTime) {
        calcPeriods();

        // 17/9-bit poly selection
        byte[] polym = poly17;
        int polymLen = poly17Len;
        if ((this.control & 0x80) != 0) {
            polymLen = poly9Len;
            polym = poly9;
        }
        polymPos %= polymLen;

        for (int i = 0; i < oscCount; i++) {
            Osc osc = oscs[i];
            int time = lastTime + osc.delay;
            int period = osc.period;

            // output
            BlipBuffer output = osc.output;
            if (output != null) {
                int oscControl = osc.regs[1] & 0xff; // cache
                int volume = (oscControl & 0x0F) * 2;
                if (volume == 0 || (oscControl & 0x10) != 0 || // silent, DAC mode, or inaudible frequency
                        ((oscControl & 0xA0) == 0xA0 && period < 1789773 / 2 / maxFrequency)) {
                    if ((oscControl & 0x10) == 0)
                        volume >>= 1; // inaudible frequency = half volume

                    int delta = volume - osc.lastAmp;
                    if (delta != 0) {
                        osc.lastAmp = volume;
                        output.addDelta(lastTime, (int) (delta * volFactor));
                    }

                    // TODO: doesn't maintain high pass flip-flop (very minor issue)
                } else {
                    // high pass
                    int period2 = 0; // unused if no high pass
                    int time2 = endTime;
                    if ((this.control & hipassBits[i]) != 0) {
                        period2 = oscs[i + 2].period;
                        time2 = lastTime + oscs[i + 2].delay;
                        if (osc.invert != 0) {
                            // trick inner wave loop into inverting output
                            osc.lastAmp -= volume;
                            volume = -volume;
                        }
                    }

                    if (time < endTime || time2 < endTime) {
                        // poly source
                        byte[] poly = poly1;
                        int polyLen = 8 * poly1.length;
                        int polyPos = osc.phase & 1;
                        int polyInc = 1;
                        if ((oscControl & 0x20) == 0) {
                            poly = polym;
                            polyLen = polymLen;
                            polyPos = polymPos;
                            if ((oscControl & 0x40) != 0) {
                                poly = poly4;
                                polyLen = poly4Len;
                                polyPos = poly4Pos;
                            }
                            polyInc = period % polyLen;
                            polyPos = (polyPos + osc.delay) % polyLen;
                        }
                        polyInc -= polyLen; // allows more optimized inner loop below

                        // square/poly5 wave
                        int wave = poly5;
                        assert (poly5 & 1) != 0; // low bit is set for pure wave
                        int poly5Inc = 0;
                        if ((oscControl & 0x80) == 0) {
                            wave = runPoly5(wave, (osc.delay + poly5Pos) % poly5Len);
                            poly5Inc = period % poly5Len;
                        }

                        // Run wave and high pass interleved with each catching up to the other.
                        // Disabled high pass has no performance effect since inner wave loop
                        // makes no compromise for high pass, and only runs once in that case.
                        int oscLastAmp = osc.lastAmp;
                        do {
                            // run high pass
                            if (time2 < time) {
                                int delta = -oscLastAmp;
                                if (volume < 0)
                                    delta += volume;
                                if (delta != 0) {
                                    oscLastAmp += delta - volume;
                                    volume = -volume;
                                    output.addDelta(time2, (int) (delta * volFactor));
                                }
                            }
                            while (time2 <= time) // must advance *past* time to avoid hang
                                time2 += period2;

                            // run wave
                            int end = endTime;
                            if (end > time2)
                                end = time2;
                            while (time < end) {
                                if ((wave & 1) != 0) {
                                    int amp = volume & -(poly[polyPos >> 3] >> (polyPos & 7) & 1);
                                    if ((polyPos += polyInc) < 0)
                                        polyPos += polyLen;
                                    int delta = amp - oscLastAmp;
                                    if (delta != 0) {
                                        oscLastAmp = amp;
                                        output.addDelta(time, (int) (delta * volFactor));
                                    }
                                }
                                wave = runPoly5(wave, poly5Inc);
                                time += period;
                            }
                        } while (time < endTime || time2 < endTime);

                        osc.phase = polyPos & 0xff;
                        osc.lastAmp = oscLastAmp;
                    }

                    osc.invert = 0;
                    if (volume < 0) {
                        // undo inversion trickery
                        osc.lastAmp -= volume;
                        osc.invert = 1;
                    }
                }
            }

            // maintain divider
            int remain = endTime - time;
            if (remain > 0) {
                int count = (remain + period - 1) / period;
                osc.phase = (osc.phase ^ count) & 0xff;
                time += count * period;
            }
            osc.delay = time - endTime;
        }

        // advance polies
        int duration = endTime - lastTime;
        lastTime = endTime;
        poly4Pos = (poly4Pos + duration) % poly4Len;
        poly5Pos = (poly5Pos + duration) % poly5Len;
        polymPos += duration; // will get %'d on next call
    }

    public void writeData(int time, int addr, int data) {
        runUntil(time);
        int i = (addr ^ 0xD200) >> 1;
        if (i < oscCount) {
            oscs[i].regs[addr & 1] = (byte) data;
        } else if (addr == 0xD208) {
            control = data;
        } else if (addr == 0xD209) {
            oscs[0].delay = 0;
            oscs[1].delay = 0;
            oscs[2].delay = 0;
            oscs[3].delay = 0;
        }
//        // TODO: are polynomials reset in this case?
//        else if (addr == 0xD20F) {
//            if ((data & 3) == 0)
//                polymPos = 0;
//        }
    }

    public void endFrame(int endTime) {
        if (endTime > lastTime)
            runUntil(endTime);

        lastTime -= endTime;
    }
}
