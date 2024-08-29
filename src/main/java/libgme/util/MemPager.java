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

package libgme.util;

/**
 * Manages memory paging used by CPU emulators
 *
 * @see "https://www.slack.net/~ant"
 */
public final class MemPager {

    public MemPager(int pageSize, int ramSize) {
        this.pageSize = pageSize;
        this.romOffset = ramSize + pageSize;
    }

    /** Loads data and returns memory array */
    public byte[] load(byte[] in, byte[] header, int addr, int fill) {
        // allocate
        int romLength = in.length - header.length;
        int romSize = (romLength + addr + pageSize - 1) / pageSize * pageSize;
        data = new byte[romOffset + romSize + padding];

        // copy data
        java.util.Arrays.fill(data, 0, romOffset + addr, (byte) fill);
        java.util.Arrays.fill(data, data.length - pageSize - padding, data.length, (byte) fill);
        System.arraycopy(in, header.length, data, romOffset + addr, romLength);

        // addrMask
        int shift = 0;
        int maxAddr = romSize - 1;
        while ((maxAddr >> shift) != 0) {
            shift++;
        }
        addrMask = (1 << shift) - 1;

        // copy header
        System.arraycopy(in, 0, header, 0, header.length);
        return data;
    }

    // Size of ROM data, a multiple of pageSize */
    public int size() {
        return data.length - padding - romOffset;
    }

    // Page of unmapped fill value */
    public int unmapped() {
        return romOffset - pageSize;
    }

    // Masks address to nearest power of two greater than size() */
    public int maskAddr(int addr) {
        return addr & addrMask;
    }

    // Page starting at addr. Returns unmapped() if outside data. */
    public int mapAddr(int addr) {
        int offset = maskAddr(addr);
        if (offset < 0 || size() - pageSize < offset)
            offset = -pageSize;
        return offset + romOffset;
    }

    // private

    static final int padding = 8; // extra at end for CPU emulators that read past end
    byte[] data;
    int pageSize;
    int romOffset;
    int addrMask;
}
