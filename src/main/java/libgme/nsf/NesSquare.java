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

package libgme.nsf;

import libgme.util.BlipBuffer;


public final class NesSquare extends NesEnvelope {

    static final int negateMask = 0x08;
    static final int shiftMask = 0x07;
    static final int phaseRange = 8;
    int phase;
    int sweepDelay;

    @Override
    void reset() {
        sweepDelay = 0;
        super.reset();
    }

    void clockSweep(int negative_adjust) {
        int sweep = regs[1];

        if (--sweepDelay < 0) {
            regWritten[1] = true;

            int period = this.period();
            int shift = sweep & shiftMask;
            if (shift != 0 && (sweep & 0x80) != 0 && period >= 8) {
                int offset = period >> shift;

                if ((sweep & negateMask) != 0)
                    offset = negative_adjust - offset;

                if (period + offset < 0x800) {
                    period += offset;
                    // rewrite period
                    regs[2] = period & 0xff;
                    regs[3] = (regs[3] & ~7) | ((period >> 8) & 7);
                }
            }
        }

        if (regWritten[1]) {
            regWritten[1] = false;
            sweepDelay = (sweep >> 4) & 7;
        }
    }

    void run(BlipBuffer output, int time, int endTime) {
        int period = this.period();
        int timer_period = (period + 1) * 2;

        int offset = period >> (regs[1] & shiftMask);
        if ((regs[1] & negateMask) != 0)
            offset = 0;

        int volume = this.volume();
        if (volume == 0 || period < 8 || (period + offset) > 0x7FF) {
            if (lastAmp != 0) {
                output.addDelta(time, lastAmp * -squareUnit);
                lastAmp = 0;
            }

            time += delay;

            int remain = endTime - time;
            if (remain > 0) {
                int count = (remain + timer_period - 1) / timer_period;
                phase = (phase + count) & (phaseRange - 1);
                time += count * timer_period;
            }
        } else {
            // handle duty select
            int duty_select = (regs[0] >> 6) & 3;
            int duty = 1 << duty_select; // 1, 2, 4, 2
            int amp = 0;
            if (duty_select == 3) {
                duty = 2; // negated 25%
                amp = volume;
            }
            if (phase < duty)
                amp ^= volume;

            {
                int delta = updateAmp(amp);
                if (delta != 0)
                    output.addDelta(time, delta * squareUnit);
            }

            time += delay;
            if (time < endTime) {
                int phase = this.phase; // cache
                int delta = (amp * 2 - volume) * squareUnit;

                do {
                    if ((phase = (phase + 1) & (phaseRange - 1)) == 0 ||
                            phase == duty)
                        output.addDelta(time, delta = -delta);
                }
                while ((time += timer_period) < endTime);

                this.phase = phase;
                lastAmp = (delta < 0 ? 0 : volume);
            }
        }

        delay = time - endTime;
    }
}
