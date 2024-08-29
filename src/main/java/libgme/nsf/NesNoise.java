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


public final class NesNoise extends NesEnvelope {

    int lfsr;

    static final int[] noisePeriods = {
            0x004, 0x008, 0x010, 0x020, 0x040, 0x060, 0x080, 0x0A0,
            0x0CA, 0x0FE, 0x17C, 0x1FC, 0x2FA, 0x3F8, 0x7F2, 0xFE4
    };

    void run(BlipBuffer output, int time, int endTime) {
        int volume = this.volume();
        int amp = (lfsr & 1) != 0 ? volume : 0;
        {
            int delta = updateAmp(amp);
            if (delta != 0)
                output.addDelta(time, delta * noiseUnit);
        }

        time += delay;
        if (time < endTime) {
            int period = noisePeriods[regs[2] & 15];
            int tap = (regs[2] & 0x80) != 0 ? 8 : 13;

            if (volume == 0) {
                // round to next multiple of period
                time += (endTime - time + period - 1) / period * period;

                // approximate noise cycling while muted, by shuffling up noise register
                int feedback = (lfsr << tap) ^ (lfsr << 14);
                lfsr = (feedback & 0x4000) | (lfsr >> 1);
            } else {
                int lfsr = this.lfsr; // cache
                int delta = (amp * 2 - volume) * noiseUnit;

                do {
                    if (((lfsr + 1) & 2) != 0)
                        output.addDelta(time, delta = -delta);

                    lfsr = ((lfsr << tap) ^ (lfsr << 14)) & 0x4000 | (lfsr >> 1);
                }
                while ((time += period) < endTime);

                this.lfsr = lfsr;
                lastAmp = (delta < 0 ? 0 : volume);
            }
        }

        delay = time - endTime;
    }

    @Override
    void reset() {
        lfsr = 1 << 14;
        super.reset();
    }
}
