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

package libvgm.nsf;

public class NesEnvelope extends NesOsc {

    int envVolume;
    int envDelay;

    void clockEnvelope() {
        int period = regs[0] & 15;
        if (regWritten[3]) {
            regWritten[3] = false;
            envDelay = period;
            envVolume = 15;
        } else if (--envDelay < 0) {
            envDelay = period;
            if ((envVolume | (regs[0] & 0x20)) != 0)
                envVolume = (envVolume - 1) & 15;
        }
    }

    int volume() {
        if (lengthCounter == 0)
            return 0;

        if ((regs[0] & 0x10) != 0)
            return regs[0] & 0x0F;

        return envVolume;
    }

    @Override
    void reset() {
        envVolume = 0;
        envDelay = 0;
        super.reset();
    }
}
