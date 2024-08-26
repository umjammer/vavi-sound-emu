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

public final class SmsNoise extends SmsOsc
{
    int shifter;
    int feedback;
    int select;

    void reset()
    {
        select = 0;
        shifter = 0x8000;
        feedback = 0x9000;
        super.reset();
    }

    void run(int time, int endTime, int period)
    {
        // TODO: probably also not zero-centered
        final BlipBuffer output = this.output;

        int amp = volume;
        if ((shifter & 1) != 0)
            amp = -amp;

        {
            int delta = amp - lastAmp;
            if (delta != 0)
            {
                lastAmp = amp;
                output.addDelta(time, delta * masterVolume);
            }
        }

        time += delay;
        if (volume == 0)
            time = endTime;

        if (time < endTime)
        {
            final int feedback = this.feedback;
            int shifter = this.shifter;
            int delta = amp * (2 * masterVolume);
            if ((period *= 2) == 0)
                period = 16;

            do
            {
                int changed = shifter + 1;
                shifter = (feedback & -(shifter & 1)) ^ (shifter >> 1);
                if ((changed & 2) != 0) // true if bits 0 and 1 differ
                    output.addDelta(time, delta = -delta);
            }
            while ((time += period) < endTime);

            this.shifter = shifter;
            lastAmp = (delta < 0 ? -volume : volume);
        }
        delay = time - endTime;
    }
}
