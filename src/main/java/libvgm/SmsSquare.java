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

package libvgm;

public final class SmsSquare extends SmsOsc
{
    int period;
    int phase;

    void reset()
    {
        period = 0;
        phase = 0;
        super.reset();
    }

    void run(int time, int endTime)
    {
        final int period = this.period;

        int amp = volume;
        if (period > 128)
            amp = (amp * 2) & -phase;

        {
            int delta = amp - lastAmp;
            if (delta != 0)
            {
                lastAmp = amp;
                output.addDelta(time, delta * masterVolume);
            }
        }

        time += delay;
        delay = 0;
        if (period != 0)
        {
            if (time < endTime)
            {
                if (volume == 0 || period <= 128) // ignore 16kHz and higher
                {
                    // keep calculating phase
                    int count = (endTime - time + period - 1) / period;
                    phase = (phase + count) & 1;
                    time += count * period;
                }
                else
                {
                    final BlipBuffer output = this.output;
                    int delta = (amp - volume) * (2 * masterVolume);
                    do
                    {
                        output.addDelta(time, delta = -delta);
                    }
                    while ((time += period) < endTime);

                    phase = (delta >= 0 ? 1 : 0);
                    lastAmp = volume * (phase << 1);
                }
            }
            delay = time - endTime;
        }
    }
}
