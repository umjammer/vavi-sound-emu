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

package libgme;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;

import static java.lang.System.getLogger;


/**
 * Music emulator interface
 *
 * system properties
 * <ul>
 *  <li>libgme.endless ... loop audio playing or not, default {@code false}</li>
 * </ul>
 * @see "https://www.slack.net/~ant"
 */
public abstract class MusicEmu {

    protected static final Logger logger = getLogger(MusicEmu.class.getName());

    protected boolean endlessLoopFlag = Boolean.parseBoolean(System.getProperty("libgme.endless", "false"));

    protected MusicEmu() {
        trackCount = 0;
        trackEnded = true;
        currentTrack = 0;
    }

    /** Requests change of sample rate and returns sample rate used, which might be different */
    public final int setSampleRate(int rate) {
        return sampleRate = setSampleRate_(rate);
    }

    public final int sampleRate() {
        return sampleRate;
    }

    /** Loads music file into emulator. Might keep reference to data. */
    public void loadFile(byte[] data) {
        trackEnded = true;
        currentTrack = 0;
        currentTime = 0;
        trackCount = parseHeader(data);
    }

    /** Number of tracks */
    public final int trackCount() {
        return trackCount;
    }

    /** Starts track, where 0 is first track */
    public void startTrack(int track) {
        if (track < 0 || track > trackCount)
            throw new IllegalArgumentException("Invalid track");

        trackEnded = false;
        currentTrack = track;
        currentTime = 0;
        fadeStart = 0x4000_0000; // far into the future
        fadeStep = 1;
    }

    /** Currently started track */
    public final int currentTrack() {
        return currentTrack;
    }

    /**
     * Generates at most count samples into out and returns
     * number of samples written. If track has ended, fills
     * buffer with silence.
     */
    public final int play(byte[] out, int count) {
        if (!trackEnded) {
            count = play_(out, count);
            if ((currentTime += count >> 1) > fadeStart)
                applyFade(out, count);
        } else {
            java.util.Arrays.fill(out, 0, count * 2, (byte) 0);
        }
        return count;
    }

    /** Sets fade start and length, in seconds. Must be set after call to startTrack(). */
    public final void setFade(int start, int length) {
        fadeStart = sampleRate * Math.max(0, start);
        fadeStep = sampleRate * length / (fadeBlockSize * fadeShift);
        if (fadeStep < 1)
            fadeStep = 1;
    }

    /** Number of seconds current track has been played */
    public final int currentTime() {
        return currentTime / sampleRate;
    }

    /** True if track has reached end or setFade()'s fade has finished */
    public final boolean trackEnded() {
        return trackEnded;
    }

    public float setPlaybackRateFactor(float factor) {
        return 0;
    }

    public boolean isEndlessLoopFlag() {
        return endlessLoopFlag;
    }

    public void setEndlessLoopFlag(boolean endlessLoopFlag) {
        this.endlessLoopFlag = endlessLoopFlag;
    }

    // protected

    // must be defined in derived class

    /** @return real sampling rate define in the data */
    protected abstract int setSampleRate_(int rate);

    /** @return the number of truck count stored in the data */
    protected abstract int parseHeader(byte[] in);

    public abstract String getMagic();

    public boolean isSupported(InputStream in) throws IOException {
        String magic = getMagic();
        DataInputStream dis = new DataInputStream(in);
        byte[] buf = new byte[magic.length()];
        dis.readFully(buf);
        return isHeader(buf, magic);
    }

    /** @return real last data position in the data */
    protected abstract int play_(byte[] out, int count);

    /** Sets end of track flag and stops emulating file */
    protected void setTrackEnded() {
        trackEnded = true;
    }

    /** Reads 16 bit little endian int starting at in [pos] */
    protected static int getLE16(byte[] in, int pos) {
        return (in[pos] & 0xff) |
                (in[pos + 1] & 0xff) << 8;
    }

    /** Reads 32 bit little endian int starting at in [pos] */
    protected static int getLE32(byte[] in, int pos) {
        return (in[pos] & 0xff) |
                (in[pos + 1] & 0xff) << 8 |
                (in[pos + 2] & 0xff) << 16 |
                (in[pos + 3] & 0xff) << 24;
    }

    /** True if first bytes of file match expected string */
    protected static boolean isHeader(byte[] header, String expected) {
        for (int i = expected.length(); --i >= 0; ) {
            if ((byte) expected.charAt(i) != header[i])
                return false;
        }
        return true;
    }

    // private

    int sampleRate;
    int trackCount;
    int currentTrack;
    int currentTime;
    int fadeStart;
    int fadeStep;
    protected boolean trackEnded;

    static final int fadeBlockSize = 512;
    static final int fadeShift = 8; // fade ends with gain at 1.0 / (1 << fadeShift)

    /** unit / pow( 2.0, (double) x / step ) */
    static int intLog(int x, int step, int unit) {
        int shift = x / step;
        int fraction = (x - shift * step) * unit / step;
        return ((unit - fraction) + (fraction >> 1)) >> shift;
    }

    static final int gainShift = 14;
    static final int gainUnit = 1 << gainShift;

    /** Scales count big-endian 16-bit samples from io [pos*2] by gain/gainUnit */
    static void scaleSamples(byte[] io, int pos, int count, int gain) {
        pos <<= 1;
        count = (count << 1) + pos;
        do {
            int s;
            io[pos + 1] = (byte) (s = ((io[pos] << 8 | (io[pos + 1] & 0xff)) * gain) >> gainShift);
            io[pos] = (byte) (s >> 8);
        }
        while ((pos += 2) < count);
    }

    private void applyFade(byte[] io, int count) {
        // Apply successively smaller gains based on time since fade start
        for (int i = 0; i < count; i += fadeBlockSize) {
            // logarithmic progression
            int gain = intLog((currentTime + i - fadeStart) / fadeBlockSize, fadeStep, gainUnit);
            if (gain < (gainUnit >> fadeShift))
                setTrackEnded();

            int n = count - i;
            if (n > fadeBlockSize)
                n = fadeBlockSize;
            scaleSamples(io, i, n, gain);
        }
    }

    public abstract boolean isSupportedByName(String name);

    public boolean isGunzipNeeded(String name) {
        return false;
    }
}
