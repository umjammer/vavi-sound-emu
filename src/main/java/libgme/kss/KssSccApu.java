/*
 * Game Music Emulators
 *
 * copyright (C) 2003-2009 Shay Green.
 */

package libgme.kss;

import libgme.util.BlipBuffer;


/**
 * Konami SCC sound chip emulator
 */
public class KssSccApu {

    public static final int REG_COUNT = 0x90;
    public static final int OSC_COUNT = 5;
    private static final int AMP_RANGE = 0x8000;
    private static final int WAVE_SIZE = 0x20;
    private static final int INAUDIBLE_FREQ = 16384;

    private static class Osc {

        int delay;
        int phase;
        int lastAmp;
        BlipBuffer output;

        void reset() {
            delay = 0;
            phase = 0;
            lastAmp = 0;
        }
    }

    private final Osc[] oscs = new Osc[OSC_COUNT];
    private int lastTime;
    private final byte[] regs = new byte[REG_COUNT];

    private double volume = 1.0;
    private double volFactor;

    public KssSccApu() {
        for (int i = 0; i < OSC_COUNT; i++) {
            oscs[i] = new Osc();
        }
        volume(1.0);
    }

    public void output(BlipBuffer buf) {
        for (int i = 0; i < OSC_COUNT; i++) {
            oscs[i].output = buf;
        }
    }

    public void reset() {
        lastTime = 0;
        for (int i = 0; i < OSC_COUNT; i++) {
            oscs[i].reset();
        }
        for (int i = 0; i < REG_COUNT; i++) {
            regs[i] = 0;
        }
    }

    public void write(int time, int addr, int data) {
        if (addr < REG_COUNT) {
            runUntil(time);
            regs[addr] = (byte) data;
        }
    }

    public int read(int addr) {
        if (addr < REG_COUNT) return regs[addr] & 0xFF;
        return 0xFF;
    }

    public void endFrame(int length) {
        if (length > lastTime) {
            runUntil(length);
        }
        lastTime -= length;
    }

    public void oscOutput(int index, BlipBuffer b) {
        if (index < OSC_COUNT) {
            oscs[index].output = b;
        }
    }

    public void volume(double v) {
        this.volume = v;
        // 0.43 / OSC_COUNT / AMP_RANGE * v * 32768
        this.volFactor = 0.43 / OSC_COUNT / AMP_RANGE * v * 32768;
    }

    private void runUntil(int endTime) {
        for (int index = 0; index < OSC_COUNT; index++) {
            Osc osc = oscs[index];
            BlipBuffer output = osc.output;
            if (output == null) continue;

            int period = ((regs[0x80 + index * 2 + 1] & 0x0F) << 8) | (regs[0x80 + index * 2] & 0xFF);
            period++;

            int volume = 0;
            if ((regs[0x8F] & (1 << index)) != 0) {
                int inaudiblePeriod = (int) ((output.clockRate() + INAUDIBLE_FREQ * 32L) / (INAUDIBLE_FREQ * 16L));
                if (period > inaudiblePeriod) {
                    volume = (regs[0x8A + index] & 0x0F) * (AMP_RANGE / 256 / 15);
                }
            }

            int waveOffset = index * WAVE_SIZE;
            if (index == OSC_COUNT - 1) waveOffset -= WAVE_SIZE; // last two oscs share wave

            {
                int amp = regs[waveOffset + osc.phase] * volume;
                int delta = amp - osc.lastAmp;
                if (delta != 0) {
                    osc.lastAmp = amp;
                    output.addDelta(lastTime, (int) (delta * volFactor));
                }
            }

            int time = lastTime + osc.delay;
            if (time < endTime) {
                if (volume == 0) {
                    // maintain phase
                    int count = (endTime - time + period - 1) / period;
                    osc.phase = (osc.phase + count) & (WAVE_SIZE - 1);
                    time += count * period;
                } else {
                    int phase = osc.phase;
                    int lastWave = regs[waveOffset + phase];
                    phase = (phase + 1) & (WAVE_SIZE - 1); // pre-advance for optimal inner loop

                    do {
                        int amp = regs[waveOffset + phase];
                        phase = (phase + 1) & (WAVE_SIZE - 1);
                        int delta = amp - lastWave;
                        if (delta != 0) {
                            lastWave = amp;
                            output.addDelta(time, (int) (delta * volume * volFactor));
                        }
                        time += period;
                    } while (time < endTime);

                    osc.phase = (phase - 1) & (WAVE_SIZE - 1); // undo pre-advance
                    osc.lastAmp = regs[waveOffset + osc.phase] * volume;
                }
            }
            osc.delay = time - endTime;
        }
        lastTime = endTime;
    }
}
