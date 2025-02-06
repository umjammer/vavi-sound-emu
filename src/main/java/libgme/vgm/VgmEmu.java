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

package libgme.vgm;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import libgme.ClassicEmu;
import libgme.util.DataReader;

import static java.lang.System.getLogger;


/**
 * Sega Master System, BBC Micro VGM music file emulator
 *
 * @see "https://www.slack.net/~ant"
 */
public final class VgmEmu extends ClassicEmu {

    private static final Logger logger = getLogger(VgmEmu.class.getName());

    public static final String MAGIC = "Vgm ";

    @Override
    protected int parseHeader(byte[] data) {
        if (!isHeader(data, MAGIC))
            throw new IllegalArgumentException("Not a VGM file");

        // TODO use custom noise taps if present

        // Data and loop
        this.data = data;
        loopBegin = getLE32(data, 28) + 28;
        if (loopBegin <= 28) {
            loopBegin = data.length;
        } else if (data[data.length - 1] != cmd_end) {
            data = DataReader.resize(data, data.length + 1);
            data[data.length - 1] = cmd_end;
        }

        // PSG clock rate
        int clockRate = getLE32(data, 0x0c);
        if (clockRate == 0)
            clockRate = 3579545;
logger.log(Level.DEBUG, "clockRate: %08x".formatted(clockRate));
        psgFactor = (int) ((float) psgTimeUnit / vgmRate * clockRate + 0.5);
        if ((clockRate & 0x4000_0000) != 0) {
logger.log(Level.DEBUG, "dual apu");
            apu[1] = new SmsApu();
        }

        // FM clock rate
        fm_clock_rate = getLE32(data, 0x2c);
        fm[0] = null;
        if (fm_clock_rate != 0) {
            fm_clock_rate &= ~0xc000_0000;
            fm[0] = new YM2612();
            buf.setVolume(0.7);
            fm[0].init(fm_clock_rate, sampleRate());
            if ((fm_clock_rate & 0x4000_0000) != 0) {
logger.log(Level.DEBUG, "dual fm: %08x".formatted(fm_clock_rate));
                fm[1] = new YM2612();
                buf.setVolume(0.7);
                fm[1].init(fm_clock_rate, sampleRate());
            }
        } else {
            buf.setVolume(1.0);
        }

        setClockRate(clockRate);
        apu[0].setOutput(buf.center(), buf.left(), buf.right());

        return 1;
    }

    @Override
    public String getMagic() {
        return MAGIC;
    }

    @Override
    public boolean isSupportedByName(String name) {
        return name.endsWith(".VGM") || name.endsWith(".VGZ");
    }

    @Override
    public boolean isGunzipNeeded(String name) {
        return name.endsWith(".VGZ");
    }

    // private

    static final int vgmRate = 44100;
    static final int psgTimeBits = 12;
    static final int psgTimeUnit = 1 << psgTimeBits;

    final SmsApu[] apu = {new SmsApu(), null};
    YM2612[] fm = new YM2612[2];
    int fm_clock_rate;
    int pos;
    byte[] data;
    int delay;
    int psgFactor;
    int loopBegin;
    final int[] fm_buf_lr = new int[48000 / 10 * 2];
    int[] fm_pos = new int[2];
    int dac_disabled; // -1 if disabled
    int pcm_data;
    int pcm_pos;
    int dac_amp;

    static final int cmd_gg_stereo = 0x4F;
    static final int cmd_psg = 0x50;
    static final int cmd_ym2413 = 0x51;
    static final int cmd_ym2612_port0 = 0x52;
    static final int cmd_ym2612_port1 = 0x53;
    static final int cmd_ym2151 = 0x54;
    static final int cmd_delay = 0x61;
    static final int cmd_delay_735 = 0x62;
    static final int cmd_delay_882 = 0x63;
    static final int cmd_byte_delay = 0x64;
    static final int cmd_end = 0x66;
    static final int cmd_data_block = 0x67;
    static final int cmd_short_delay = 0x70;
    static final int cmd_pcm_delay = 0x80;
    static final int cmd_pcm_seek = 0xe0;

    static final int cmd_gg_stereo_2 = 0x3f;
    static final int cmd_psg_2 = 0x30;
    static final int cmd_ym2413_2 = 0xa1;
    static final int cmd_ym2612_2_port0 = 0xa2;
    static final int cmd_ym2612_2_port1 = 0xa3;

    static final int ym2612_dac_port = 0x2a;
    static final int pcm_block_type = 0x00;

    /** */
    private static int commandLength(int command) {
        switch (command >> 4) {
            case 0x03:
            case 0x04:
                return 2;

            case 0x05:
            case 0x0A:
            case 0x0B:
                return 3;

            case 0x0C:
            case 0x0D:
                return 4;

            case 0x0E:
            case 0x0F:
                return 5;
        }

//        assert false : "unknown %02x".formatted(command);
        return 1;
    }

    @Override
    public void startTrack(int track) {
        super.startTrack(track);

        pos = 0x40;
        delay = 0;
        pcm_data = pos;
        pcm_pos = pos;
        dac_amp = -1;

        apu[0].reset();
        if (fm[0] != null)
            fm[0].reset();
        if (fm[1] != null)
            fm[1].reset();
    }

    private int toPSGTime(int vgmTime) {
        return (vgmTime * psgFactor + psgTimeUnit / 2) >> psgTimeBits;
    }

    private int toFMTime(int vgmTime) {
        return countSamples(toPSGTime(vgmTime));
    }

    private void runFM(int index, int vgmTime) {
        int count = toFMTime(vgmTime) - fm_pos[index];
        if (count > 0) {
            fm[index].update(fm_buf_lr, fm_pos[index], count);
            fm_pos[index] += count;
        }
    }

    private void write_pcm(int vgmTime, int amp) {
        int blip_time = toPSGTime(vgmTime);
        int old = dac_amp;
        int delta = amp - old;
        dac_amp = amp;
        if (old >= 0) // first write is ignored, to avoid click
            buf.center().addDelta(blip_time, delta * 300);
        else
            dac_amp |= dac_disabled;
    }

    @Override
    protected int runMsec(int msec) {
        int duration = vgmRate / 100 * msec / 10;

        {
            int sampleCount = toFMTime(duration);
            java.util.Arrays.fill(fm_buf_lr, 0, sampleCount * 2, 0);
        }
        fm_pos[0] = 0;

        int time = delay;
        boolean endOfStream = false;
        while (time < duration && !endOfStream) {
            int cmd = cmd_end;
            if (pos < data.length)
                cmd = data[pos++] & 0xff;
            switch (cmd) {
                case cmd_end:
                    endOfStream = !endlessLoopFlag;
logger.log(Level.TRACE, "LOOP: " + endlessLoopFlag);
                    pos = loopBegin;
                    break;

                case cmd_delay_735:
                    time += 735;
                    break;

                case cmd_delay_882:
                    time += 882;
                    break;

                case cmd_gg_stereo:
                    apu[0].writeGG(toPSGTime(time), data[pos++] & 0xff);
                    break;

                case cmd_psg:
                    apu[0].writeData(toPSGTime(time), data[pos++] & 0xff);
                    break;

                case cmd_gg_stereo_2:
                    apu[1].writeGG(toPSGTime(time), data[pos++] & 0xff);
                    break;

                case cmd_psg_2:
                    apu[1].writeData(toPSGTime(time), data[pos++] & 0xff);
                    break;

                case cmd_delay:
                    time += (data[pos + 1] & 0xff) * 0x100 + (data[pos] & 0xff);
                    pos += 2;
                    break;

                case cmd_byte_delay:
                    time += data[pos++] & 0xff;
                    break;

                case cmd_ym2413:
//                    if ( ym2413[0].run_until( to_fm_time( vgm_time ) ) )
//                        ym2413[0].write( pos [0], pos [1] );
                    pos += 2;
                    break;

                case cmd_ym2413_2:
//                    if ( ym2413[1].run_until( to_fm_time( vgm_time ) ) )
//                        ym2413[1].write( pos [0], pos [1] );
                    pos += 2;
                    break;

                case cmd_ym2612_port0:
                    if (fm[0] != null) {
                        int port = data[pos++] & 0xff;
                        int val = data[pos++] & 0xff;
                        if (port == ym2612_dac_port) {
                            write_pcm(time, val);
                        } else {
                            if (port == 0x2B) {
                                dac_disabled = (val >> 7 & 1) - 1;
                                dac_amp |= dac_disabled;
                            }
                            runFM(0, time);
                            fm[0].write0(port, val);
                        }
                    } else {
                        pos += 2;
                    }
                    break;

                case cmd_ym2612_port1:
                    if (fm[0] != null) {
                        runFM(0, time);
                        int port = data[pos++] & 0xff;
                        fm[0].write1(port, data[pos++] & 0xff);
                    } else {
                        pos += 2;
                    }
                    break;

                case cmd_ym2612_2_port0:
                    if (fm[1] != null) {
                        int port = data[pos++] & 0xff;
                        int val = data[pos++] & 0xff;
                        if (port == ym2612_dac_port) {
                            write_pcm(time, val);
                        } else {
                            if (port == 0x2B) {
                                dac_disabled = (val >> 7 & 1) - 1;
                                dac_amp |= dac_disabled;
                            }
                            runFM(1, time);
                            fm[1].write0(port, val);
                        }
                    } else {
                        pos += 2;
                    }
                    break;

                case cmd_ym2612_2_port1:
                    if (fm[1] != null) {
                        runFM(1, time);
                        int port = data[pos++] & 0xff;
                        fm[1].write1(port, data[pos++] & 0xff);
                    } else {
                        pos += 2;
                    }
                    break;

                case cmd_data_block:
                    if (data[pos++] != cmd_end) {
                        setTrackEnded();
                        logger.log(Level.ERROR, "emulation error");
                    }
                    int type = data[pos++];
                    long size = getLE32(data, pos);
                    pos += 4;
                    if (type == pcm_block_type)
                        pcm_data = pos;
                    pos += (int) size;
                    break;

                case cmd_pcm_seek:
                    pcm_pos = pcm_data + getLE32(data, pos);
                    pos += 4;
                    break;

                default:
                    switch (cmd & 0xF0) {
                        case cmd_pcm_delay:
                            write_pcm(time, data[pcm_pos++] & 0xff);
                            time += cmd & 0x0F;
                            break;

                        case cmd_short_delay:
                            time += (cmd & 0x0F) + 1;
                            break;

                        case 0x50:
                            pos += 2;
                            break;

                        default:
//                            setTrackEnded();
                            pos += commandLength(cmd) - 1;
                            logger.log(Level.WARNING, String.format("emulation error: p: %02x, c: %02x", pos, cmd));
                            break;
                    }
            }
        }

        if (fm[0] != null)
            runFM(0, duration);

        int endTime = toPSGTime(duration);
        delay = time - duration;
        apu[0].endFrame(endTime);
        if (pos >= data.length || endOfStream) {
            setTrackEnded();
            if (pos > data.length) {
                pos = data.length;
                setTrackEnded(); // went past end
                logger.log(Level.ERROR, "emulation error");
            }
        }

        fm_pos[0] = 0;

        return endTime;
    }

    @Override
    protected void mixSamples(byte[] out, int out_off, int count) {
        if (fm[0] == null)
            return;

        out_off *= 2;
        int in_off = fm_pos[0];

        while (--count >= 0) {
            int s = (out[out_off] << 8) + (out[out_off + 1] & 0xff);
            s = (s >> 2) + fm_buf_lr[in_off];
            in_off++;
            if ((short) s != s)
                s = (s >> 31) ^ 0x7fff;
            out[out_off] = (byte) (s >> 8);
            out_off++;
            out[out_off] = (byte) s;
            out_off++;
        }

        fm_pos[0] = in_off;
    }
}
