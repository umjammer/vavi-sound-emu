/*
 * Game Music Emulators
 *
 * copyright (C) 2003-2009 Shay Green.
 */

package libgme.kss;

import libgme.util.BlipBuffer;


/**
 * AY-3-8910 sound chip emulator
 */
public class AyApu {

    public static final int REG_COUNT = 16;
    public static final int OSC_COUNT = 3;
    public static final int AMP_RANGE = 255;
    private static final int PERIOD_FACTOR = 16;
    private static final int INAUDIBLE_FREQ = 16384;

    private static final byte[] AMP_TABLE = new byte[16];

    static {
        for (int i = 0; i < 16; i++) {
            double v = (i == 0) ? 0 : Math.pow(2, (i - 15) / 2.0);
            if (i == 0) v = 0;
            else if (i == 1) v = 0.007813;
            else if (i == 2) v = 0.011049;
            else if (i == 3) v = 0.015625;
            else if (i == 4) v = 0.022097;
            else if (i == 5) v = 0.031250;
            else if (i == 6) v = 0.044194;
            else if (i == 7) v = 0.062500;
            else if (i == 8) v = 0.088388;
            else if (i == 9) v = 0.125000;
            else if (i == 10) v = 0.176777;
            else if (i == 11) v = 0.250000;
            else if (i == 12) v = 0.353553;
            else if (i == 13) v = 0.500000;
            else if (i == 14) v = 0.707107;
            else v = 1.0;
            AMP_TABLE[i] = (byte) (v * AMP_RANGE + 0.5);
        }
    }

    private static final byte[] MODES = {
            0x15, 0x01, 0x19, 0x3D, 0x2A, 0x3E, 0x26, 0x02
    };

    private static class Osc {

        int period;
        int delay;
        int lastAmp;
        int phase;
        BlipBuffer output;

        void reset() {
            period = PERIOD_FACTOR;
            delay = 0;
            lastAmp = 0;
            phase = 0;
        }
    }

    private final Osc[] oscs = new Osc[OSC_COUNT];
    private int lastTime;
    private int latch;
    private final byte[] regs = new byte[REG_COUNT];

    private double volume = 1.0;
    private double volFactor;

    private static class Noise {

        int delay;
        int lfsr;
    }

    private final Noise noise = new Noise();

    private static class Env {

        int delay;
        byte[] wave;
        int pos;
        final byte[][] modes = new byte[8][48];
    }

    private final Env env = new Env();

    public AyApu() {
        for (int m = 0; m < 8; m++) {
            int flags = MODES[m];
            int outIdx = 0;
            for (int x = 0; x < 3; x++) {
                int amp = flags & 1;
                int end = (flags >> 1) & 1;
                int step = end - amp;
                amp *= 15;
                for (int y = 0; y < 16; y++) {
                    env.modes[m][outIdx++] = AMP_TABLE[amp];
                    amp += step;
                }
                flags >>= 2;
            }
        }

        for (int i = 0; i < OSC_COUNT; i++) {
            oscs[i] = new Osc();
        }
        volume(1.0);
        reset();
    }

    public void output(BlipBuffer buf) {
        for (int i = 0; i < OSC_COUNT; i++) {
            oscs[i].output = buf;
        }
    }

    public void reset() {
        lastTime = 0;
        noise.delay = 0;
        noise.lfsr = 1;
        for (int i = 0; i < OSC_COUNT; i++) {
            oscs[i].reset();
        }
        for (int i = 0; i < REG_COUNT; i++) {
            regs[i] = 0;
        }
        regs[7] = (byte) 0xFF;
        writeData(13, 0);
    }

    public void write(int time, int addr, int data) {
        runUntil(time);
        writeData(addr, data);
    }

    public int readData(int addr) {
        return regs[addr & 0x0F] & 0xFF;
    }

    private void writeData(int addr, int data) {
        if (addr >= REG_COUNT) return;

        if (addr == 13) {
            int mode = data;
            if ((mode & 8) == 0) mode = (mode & 4) != 0 ? 15 : 9;
            env.wave = env.modes[mode - 8];
            env.pos = -48;
            env.delay = 0;
            data = mode;
        }
        regs[addr] = (byte) data;

        int i = addr >> 1;
        if (i < OSC_COUNT) {
            int period = ((regs[i * 2 + 1] & 0x0F) << 8 | (regs[i * 2] & 0xFF)) * PERIOD_FACTOR;
            if (period == 0) period = PERIOD_FACTOR;
            Osc osc = oscs[i];
            osc.delay += period - osc.period;
            if (osc.delay < 0) osc.delay = 0;
            osc.period = period;
        }
    }

    public void endFrame(int length) {
        if (length > lastTime) {
            runUntil(length);
        }
        lastTime -= length;
    }

    public void oscOutput(int i, BlipBuffer buf) {
        if (i < OSC_COUNT) {
            oscs[i].output = buf;
        }
    }

    public void volume(double v) {
        this.volume = v;
        // 0.7 / OSC_COUNT / AMP_RANGE * v * 32768
        this.volFactor = 0.7 / OSC_COUNT / AMP_RANGE * v * 32768;
    }

    private void runUntil(int finalEndTime) {
        int noisePeriodFactor = PERIOD_FACTOR * 2;
        int noisePeriod = (regs[6] & 0x1F) * noisePeriodFactor;
        if (noisePeriod == 0) noisePeriod = noisePeriodFactor;
        int oldNoiseDelay = noise.delay;
        int oldNoiseLfsr = noise.lfsr;

        int envPeriodFactor = PERIOD_FACTOR * 2;
        int envPeriod = ((regs[12] & 0xFF) << 8 | (regs[11] & 0xFF)) * envPeriodFactor;
        if (envPeriod == 0) envPeriod = envPeriodFactor;
        if (env.delay == 0) env.delay = envPeriod;

        for (int index = 0; index < OSC_COUNT; index++) {
            Osc osc = oscs[index];
            int oscMode = (regs[7] & 0xFF) >> index;
            BlipBuffer output = osc.output;
            if (output == null) continue;

            int halfVol = 0;
            int inaudiblePeriod = (output.clockRate() + INAUDIBLE_FREQ) / (INAUDIBLE_FREQ * 2);
            if (osc.period <= inaudiblePeriod && (oscMode & 1) == 0) {
                halfVol = 1;
                oscMode |= 1;
            }

            int startTime = lastTime;
            int endTime = finalEndTime;
            int volMode = regs[0x08 + index] & 0xFF;
            int volume = (AMP_TABLE[volMode & 0x0F] & 0xFF) >> halfVol;
            int oscEnvPos = env.pos;

            if ((volMode & 0x10) != 0) {
                volume = (env.wave[oscEnvPos + 48] & 0xFF) >> halfVol;
                if ((regs[13] & 1) == 0 || oscEnvPos < -32) {
                    endTime = Math.min(finalEndTime, startTime + env.delay);
                } else if (volume == 0) {
                    oscMode = 0x08 | 0x01;
                }
            } else if (volume == 0) {
                oscMode = 0x08 | 0x01;
            }

            int period = osc.period;
            int time = startTime + osc.delay;
            if ((oscMode & 0x01) != 0) {
                int count = (finalEndTime - time + period - 1) / period;
                time += count * period;
                osc.phase ^= count & 1;
            }

            int ntime = finalEndTime;
            int noiseLfsr = 1;
            if ((oscMode & 0x08) == 0) {
                ntime = startTime + oldNoiseDelay;
                noiseLfsr = oldNoiseLfsr;
            }

            while (true) {
                int amp = 0;
                if (((oscMode | osc.phase) & 1 & ((oscMode >>> 3) | noiseLfsr) & 1) != 0) {
                    amp = volume;
                }
                int delta = amp - osc.lastAmp;
                if (delta != 0) {
                    osc.lastAmp = amp;
                    output.addDelta(startTime, (int) (delta * volFactor));
                }

                if (ntime < endTime || time < endTime) {
                    int d = amp * 2 - volume;
                    boolean dNonZero = d != 0;
                    int phase = osc.phase | (oscMode & 0x01);
                    do {
                        int end = Math.min(endTime, time);
                        if ((phase & (dNonZero ? 1 : 0)) != 0) {
                            while (ntime <= end) {
                                int changed = noiseLfsr + 1;
                                noiseLfsr = (-(noiseLfsr & 1) & 0x12000) ^ (noiseLfsr >>> 1);
                                if ((changed & 2) != 0) {
                                    d = -d;
                                    output.addDelta(ntime, (int) (d * volFactor));
                                }
                                ntime += noisePeriod;
                            }
                        } else {
                            int remain = end - ntime;
                            if (remain >= 0) ntime += noisePeriod + (remain / noisePeriod) * noisePeriod;
                        }

                        end = Math.min(endTime, ntime);
                        if ((noiseLfsr & (dNonZero ? 1 : 0)) != 0) {
                            while (time < end) {
                                d = -d;
                                output.addDelta(time, (int) (d * volFactor));
                                time += period;
                            }
                            phase = (d > 0) ? 1 : 0;
                        } else {
                            while (time < end) {
                                time += period;
                                phase ^= 1;
                            }
                        }
                    } while (time < endTime || ntime < endTime);
                    osc.lastAmp = (d + volume) >> 1;
                    if ((oscMode & 0x01) == 0) osc.phase = phase;
                }

                if (endTime >= finalEndTime) break;

                if (++oscEnvPos >= 0) oscEnvPos -= 32;
                volume = (env.wave[oscEnvPos + 48] & 0xFF) >> halfVol;
                startTime = endTime;
                endTime = Math.min(finalEndTime, endTime + envPeriod);
            }
            osc.delay = time - finalEndTime;
            if ((oscMode & 0x08) == 0) {
                noise.delay = ntime - finalEndTime;
                noise.lfsr = noiseLfsr;
            }
        }

        int remain = finalEndTime - lastTime - env.delay;
        if (remain >= 0) {
            int count = (remain + envPeriod) / envPeriod;
            env.pos += count;
            if (env.pos >= 0) env.pos = (env.pos & 31) - 32;
            remain -= count * envPeriod;
        }
        env.delay = -remain;
        lastTime = finalEndTime;
    }
}
