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

package libgme.gbs;

public class GbEnv extends GbOsc {

    int env_delay;
    int volume;

    int dac_enabled() {
        return regs[2] & 0xF8;
    }

    @Override
    void reset() {
        env_delay = 0;
        volume = 0;
        super.reset();
    }

    int reload_env_timer() {
        int raw = regs[2] & 7;
        env_delay = (raw != 0 ? raw : 8);
        return raw;
    }

    void clock_envelope() {
        if (--env_delay <= 0 && reload_env_timer() != 0) {
            int v = volume + ((regs[2] & 0x08) != 0 ? +1 : -1);
            if (0 <= v && v <= 15)
                volume = v;
        }
    }

    @Override
    boolean write_register(int frame_phase, int reg, int old_data, int data) {
        final int max_len = 64;

        switch (reg) {
            case 1:
                length = max_len - (data & (max_len - 1));
                break;

            case 2:
                if (dac_enabled() == 0)
                    enabled = 0;

                // TODO: once zombie mode used, envelope not clocked?
                if (((old_data ^ data) & 8) != 0) {
                    int step = 0;
                    if ((old_data & 7) != 0)
                        step = +1;
                    else if ((data & 7) != 0)
                        step = -1;

                    if ((data & 8) != 0)
                        step = -step;

                    volume = (15 + step - volume) & 15;
                } else {
                    int step = ((old_data & 7) != 0 ? 2 : 0) | ((data & 7) != 0 ? 0 : 1);
                    volume = (volume + step) & 15;
                }
                break;

            case 4:
                if (write_trig(frame_phase, max_len, old_data) != 0) {
                    volume = regs[2] >> 4;
                    reload_env_timer();
                    if (frame_phase == 7)
                        env_delay++;
                    if (dac_enabled() == 0)
                        enabled = 0;
                    return true;
                }
        }
        return false;
    }
}
