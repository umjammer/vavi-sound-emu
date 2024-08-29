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

public class NesOsc {

    static final int squareUnit = (int) (0.125 / 15 * 65535);
    static final int triangleUnit = (int) (0.150 / 15 * 65535);
    static final int noiseUnit = (int) (0.095 / 15 * 65535);
    static final int dmcUnit = (int) (0.450 / 127 * 65535);

    final int[] regs = new int[4];
    final boolean[] regWritten = new boolean[4];
    int lengthCounter;// length counter (0 if unused by oscillator)
    int delay;        // delay until next (potential) transition
    int lastAmp;     // last amplitude oscillator was outputting

    void clockLength(int halt_mask) {
        if (lengthCounter != 0 && (regs[0] & halt_mask) == 0)
            lengthCounter--;
    }

    int period() {
        return (regs[3] & 7) * 0x100 + (regs[2] & 0xFF);
    }

    void reset() {
        delay = 0;
        lastAmp = 0;
    }

    int updateAmp(int amp) {
        int delta = amp - lastAmp;
        lastAmp = amp;
        return delta;
    }
}
