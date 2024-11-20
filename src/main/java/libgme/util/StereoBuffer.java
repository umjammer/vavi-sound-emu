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


public final class StereoBuffer {

    /** for debug */
    public interface Observer {
        void observe(byte[] out, int start, int end);
    }

    private final BlipBuffer[] bufs = new BlipBuffer[3];

    /** for debug */
    private Observer observer;

    // Same behavior as in BlipBuffer unless noted

    public StereoBuffer() {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i] = new BlipBuffer();
        }
    }

    public void setObserver(Observer observer) {
        this.observer = observer;
    }

    public void setSampleRate(int rate, int msec) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].setSampleRate(rate, msec);
        }
    }

    public void setClockRate(int rate) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].setClockRate(rate);
        }
    }

    public int clockRate() {
        return bufs[0].clockRate();
    }

    public int countSamples(int time) {
        return bufs[0].countSamples(time);
    }

    public void clear() {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].clear();
        }
    }

    public void setVolume(double v) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].setVolume(v);
        }
    }

    // The three channels that are mixed together
    // left output  = left  + center
    // right output = right + center

    public BlipBuffer center() {
        return bufs[2];
    }

    public BlipBuffer left() {
        return bufs[0];
    }

    public BlipBuffer right() {
        return bufs[1];
    }

    public void endFrame(int time) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].endFrame(time);
        }
    }

    public int samplesAvail() {
        return bufs[2].samplesAvail() << 1;
    }

    /** Output is in stereo, so count must always be a multiple of 2 */
    public int readSamples(byte[] out, int start, int count) {
        assert (count & 1) == 0;

        int avail = samplesAvail();
        if (count > avail)
            count = avail;

        if ((count >>= 1) > 0) {
            // TODO: optimize for mono case

            // calculate center in place
            int[] mono = bufs[2].buf;
            {
                int accum = bufs[2].accum;
                int i = 0;
                do {
                    mono[i] = (accum += mono[i] - (accum >> 9));
                }
                while (++i < count);
                bufs[2].accum = accum;
            }

            int pos = 0;
            // calculate left and right
            for (int ch = 2; --ch >= 0; ) {
                // add right and output
                int[] buf = bufs[ch].buf;
                int accum = bufs[ch].accum;
                pos = (start + ch) << 1;
                int i = 0;
                do {
                    int s = ((accum += buf[i] - (accum >> 9)) + mono[i]) >> 15;

                    // clamp to 16 bits
                    if ((short) s != s)
                        s = (s >> 24) ^ 0x7fff;

                    // write as big endian
                    out[pos] = (byte) (s >> 8);
                    out[pos + 1] = (byte) s;
                    pos += 4;
                }
                while (++i < count);
                bufs[ch].accum = accum;
            }
            if (observer != null)
                observer.observe(out, start * 2, pos);
            for (int i = bufs.length; --i >= 0; ) {
                bufs[i].removeSamples(count);
            }
        }
        return count << 1;
    }
}
