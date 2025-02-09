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

public final class GbSweepSquare extends GbSquare {

    static final int period_mask = 0x70;
    static final int shift_mask = 0x07;

    int sweep_freq;
    int sweep_delay;
    int sweep_enabled;
    int sweep_neg;

    @Override
    void reset() {
        sweep_freq = 0;
        sweep_delay = 0;
        sweep_enabled = 0;
        sweep_neg = 0;
        super.reset();
    }

    void reload_sweep_timer() {
        sweep_delay = (regs[0] & period_mask) >> 4;
        if (sweep_delay == 0)
            sweep_delay = 8;
    }

    void calc_sweep(boolean update) {
        int freq = sweep_freq;
        int shift = regs[0] & shift_mask;
        int delta = freq >> shift;
        sweep_neg = regs[0] & 0x08;
        if (sweep_neg != 0)
            delta = -delta;
        freq += delta;

        if (freq > 0x7FF) {
            enabled = 0;
        } else if (shift != 0 && update) {
            sweep_freq = freq;
            regs[3] = freq & 0xff;
            regs[4] = (regs[4] & ~0x07) | (freq >> 8 & 0x07);
        }
    }

    void clock_sweep() {
        if (--sweep_delay <= 0) {
            reload_sweep_timer();
            if (sweep_enabled != 0 && (regs[0] & period_mask) != 0) {
                calc_sweep(true);
                calc_sweep(false);
            }
        }
    }

    @Override
    boolean write_register(int frame_phase, int reg, int old_data, int data) {
        if (reg == 0 && (sweep_neg & 0x08 & ~data) != 0)
            enabled = 0;

        if (super.write_register(frame_phase, reg, old_data, data)) {
            sweep_freq = frequency();
            reload_sweep_timer();
            sweep_enabled = regs[0] & (period_mask | shift_mask);
            if ((regs[0] & shift_mask) != 0)
                calc_sweep(false);
        }

        return false;
    }
}
