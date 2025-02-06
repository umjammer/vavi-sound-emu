/*
 * Ym2612.C : Ym2612 emulator
 *
 * Almost constants are taken from the MAME core
 *
 * This source is a part of Gens project
 * Written by Stéphane Dallongeville (gens@consolemul.com)
 * Copyright (c) 2002 by Stéphane Dallongeville
 *
 * Modified by Maxim, Blargg
 * - removed non-Sound-related functionality
 * - added high-pass PCM filter
 * - added per-channel muting control
 * - made it use a context struct to allow multiple
 * instances
 */

package libgme.vgm;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


/**
 * Test port of Gens YM2612 core.
 *
 * @author Stephan Dittrich
 * @author Maxim, Blargg
 * @version 2005
 */
public final class YM2612 {

    private static final Logger logger = getLogger(YM2612.class.getName());

    static final int NULL_RATE_SIZE = 32;

    /** YM2612 Hardware */
    private static final class Slot {

        int[] dt;
        int mul;
        int tl;
        int tll;
        int sll;
        int ksrS;
        int ksr;
        int seg;
        int ar;
        int dr;
        int sr;
        int rr;
        int fCnt;
        int fInc;
        int eCurp;
        int eCnt;
        int eInc;
        int eCmp;
        int eIncA;
        int eIncD;
        int eIncS;
        int eIncR;
        int inD;
        int chgEnM;
        int ams;
        int amsOn;
    }

    private static final class Channel {

        final int[] s0Out = new int[4];
        int oldOutD;
        int outD;
        int left;
        int right;
        int algo;
        int fb;
        int fms;
        int ams;
        final int[] fNum = new int[4];
        final int[] fOct = new int[4];
        final int[] kc = new int[4];
        final Slot[] slots = new Slot[4];
        int fFlag;

        public Channel() {
            for (int i = 0; i < 4; i++) slots[i] = new Slot();
        }
    }

    // Constants ( taken from MAME YM2612 core )
    private static final int UPD_SIZE = 4000;
    private static final int OUTP_BITS = 16;
//    private static final double PI = Math.PI;

    private static final int ATTACK = 0;
    private static final int DECAY = 1;
    private static final int SUSTAIN = 2;
    private static final int RELEASE = 3;

    private static final int SIN_HBITS = 12;
    private static final int SIN_LBITS = ((26 - SIN_HBITS) <= 16) ? (26 - SIN_HBITS) : 16;

    private static final int ENV_HBITS = 12;
    private static final int ENV_LBITS = (28 - ENV_HBITS);

    private static final int LFO_HBITS = 10;
    private static final int LFO_LBITS = (28 - LFO_HBITS);

    private static final int SINLEN = (1 << SIN_HBITS);
    private static final int ENVLEN = (1 << ENV_HBITS);
    private static final int LFOLEN = (1 << LFO_HBITS);

    private static final int TLLEN = (ENVLEN * 3);

    private static final int SIN_MSK = (SINLEN - 1);
    private static final int ENV_MSK = (ENVLEN - 1);
    private static final int LFO_MSK = (LFOLEN - 1);

    private static final double ENV_STEP = (96.0 / ENVLEN);

    private static final int ENV_ATTACK = ((ENVLEN * 0) << ENV_LBITS);
    private static final int ENV_DECAY = ((ENVLEN * 1) << ENV_LBITS);
    private static final int ENV_END = ((ENVLEN * 2) << ENV_LBITS);

    private static final int MAX_OUT_BITS = (SIN_HBITS + SIN_LBITS + 2);
    private static final int MAX_OUT = ((1 << MAX_OUT_BITS) - 1);

    private static final int OUT_BITS = (OUTP_BITS - 2);
    private static final int FINAL_SHFT = (MAX_OUT_BITS - OUT_BITS) + 1;
    private static final int LIMIT_CH_OUT = ((int) (((1 << OUT_BITS) * 1.5) - 1));

    private static final int PG_CUT_OFF = ((int) (78.0 / ENV_STEP));
//	  private static final int ENV_CUT_OFF = ((int) (68.0 / ENV_STEP));

    private static final int AR_RATE = 399128;
    private static final int DR_RATE = 5514396;

    private static final int LFO_FMS_LBITS = 9;
    private static final int LFO_FMS_BASE = ((int) (0.05946309436 * 0.0338 * (double) (1 << LFO_FMS_LBITS)));

    private static final int S0 = 0;
    private static final int S1 = 2;
    private static final int S2 = 1;
    private static final int S3 = 3;

    private static final int[] DT_DEF_TAB = {
            // FD = 0
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            // FD = 1
            0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
            2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,

            // FD = 2
            1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
            5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16,

            // FD = 3
            2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
            8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22
    };

    private static final int[] FKEY_TAB = {
            0, 0, 0, 0,
            0, 0, 0, 1,
            2, 3, 3, 3,
            3, 3, 3, 3
    };

    private static final int[] LFO_AMS_TAB = {
            31, 4, 1, 0
    };

    private static final int[] LFO_FMS_TAB = {
            LFO_FMS_BASE * 0, LFO_FMS_BASE * 1,
            LFO_FMS_BASE * 2, LFO_FMS_BASE * 3,
            LFO_FMS_BASE * 4, LFO_FMS_BASE * 6,
            LFO_FMS_BASE * 12, LFO_FMS_BASE * 24
    };

    // Variables
    private static final int[] SIN_TAB = new int[SINLEN];
    private static final int[] TL_TAB = new int[TLLEN * 2];
    private static final int[] ENV_TAB = new int[2 * ENVLEN + 8]; // uint
    private static final int[] DECAY_TO_ATTACK = new int[ENVLEN];    // uint
    private final int[] FINC_TAB = new int[2048];    // uint
    static final int AR_NULL_RATE = 128;
    private final int[] AR_TAB = new int[AR_NULL_RATE + NULL_RATE_SIZE];    // uint
    static final int DR_NULL_RATE = 96;
    private final int[] DR_TAB = new int[DR_NULL_RATE + NULL_RATE_SIZE];    // uint
    private final int[][] DT_TAB = new int[8][32];    // uint
    private static final int[] SL_TAB = new int[16];         // uint
    private static final int[] LFO_ENV_TAB = new int[LFOLEN];
    private static final int[] LFO_FREQ_TAB = new int[LFOLEN];
    private final int[] LFO_ENV_UP = new int[UPD_SIZE];
    private final int[] LFO_FREQ_UP = new int[UPD_SIZE];
    private final int[] LFO_INC_TAB = new int[8];
    private int in0, in1, in2, in3;
    private int en0, en1, en2, en3;
    private int int_cnt;

    // Emulation State
    private static final boolean EnableSSGEG = false;

    private static final int MAIN_SHIFT = FINAL_SHFT;

    int clock;
    int rate;
    int timerBase;
    int status;
    int lfoCnt;
    int lfoInc;
    int timerA;
    int timerAL;
    int timerACnt;
    int timerB;
    int timerBL;
    int timerBCnt;
    int mode;
    int dac;
    double frequency;
    long interCnt;    // UINT
    long interStep;    // UINT
    final Channel[] channels = new Channel[6];
    final int[][] regs = new int[2][0x100];

    /**
     * Creates a new instance of YM2612
     */
    public YM2612() {
        for (int i = 0; i < 6; i++) channels[i] = new Channel();
    }

    // YM2612 Emulation Methods

    //
    // Public Access
    //

    /**
     * @return 0: ok, 1: illegal argument
     */
    public int init(int clock, int rate) {

        if ((rate == 0) || (clock == 0)) return 1;

        this.clock = clock;
        this.rate = rate;

        frequency = ((double) this.clock / (double) this.rate) / 144.0;
        timerBase = (int) (frequency * 4096.0);

        interStep = 0x4000;
        interCnt = 0;

        // ----

        // Frequency Step Table

        for (int i = 0; i < 2048; i++) {
            double x = (double) i * frequency;

            x *= 1 << (SIN_LBITS + SIN_HBITS - (21 - 7));
            x /= 2.0; // because mul = value * 2
            FINC_TAB[i] = (int) x; // (unsigned int) x;
        }

        // Attack & Decay rate Table

        for (int i = 0; i < 4; i++) {
            AR_TAB[i] = 0;
            DR_TAB[i] = 0;
        }

        for (int i = 0; i < 60; i++) {
            double x = frequency;
            x *= 1.0 + ((i & 3) * 0.25); // bits 0-1 : x1.00, x1.25, x1.50, x1.75
            x *= 1 << ((i >> 2));        // bits 2-5 : shift bits (x2^0 - x2^15)
            x *= ENVLEN << ENV_LBITS;    // on ajuste pour le tableau ENV_TAB

            AR_TAB[i + 4] = (int) (x / AR_RATE); // (unsigned int) (x / AR_RATE);
            DR_TAB[i + 4] = (int) (x / DR_RATE); // (unsigned int) (x / DR_RATE);
        }

        for (int i = 64; i < 96; i++) {
            AR_TAB[i] = AR_TAB[63];
            DR_TAB[i] = DR_TAB[63];
            AR_TAB[i - 64 + AR_NULL_RATE] = 0;
            DR_TAB[i - 64 + DR_NULL_RATE] = 0;
        }

        // Detune Table
        int j;
        for (int i = 0; i < 4; i++) {
            for (j = 0; j < 32; j++) {
                double x = (double) DT_DEF_TAB[(i << 5) + j] * frequency * (double) (1 << (SIN_LBITS + SIN_HBITS - 21));
                DT_TAB[i + 0][j] = (int) x;
                DT_TAB[i + 4][j] = (int) -x;
            }
        }

        // LFO Table
        j = (int) ((this.rate * interStep) / 0x4000);

        LFO_INC_TAB[0] = (int) (3.98 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[1] = (int) (5.56 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[2] = (int) (6.02 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[3] = (int) (6.37 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[4] = (int) (6.88 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[5] = (int) (9.63 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[6] = (int) (48.1 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);
        LFO_INC_TAB[7] = (int) (72.2 * (double) (1 << (LFO_HBITS + LFO_LBITS)) / j);

        reset();
        return 0;
    }

    static {
        // tl Table :
        // [0	  -	 4095] = +output  [4095	 - ...] = +output overflow (fill with 0)
        // [12288 - 16383] = -output  [16384 - ...] = -output overflow (fill with 0)

        for (int i = 0; i < TLLEN; i++) {
            if (i >= PG_CUT_OFF) {
                TL_TAB[TLLEN + i] = TL_TAB[i] = 0;
            } else {
                double x = MAX_OUT; // Max output
                x /= Math.pow(10, (ENV_STEP * i) / 20);
                TL_TAB[i] = (int) x;
                TL_TAB[TLLEN + i] = -TL_TAB[i];
            }
        }

        // SIN Table :
        // SIN_TAB[x][y] = sin(x) * y;
        // x = phase and y = volume

        SIN_TAB[0] = PG_CUT_OFF;
        SIN_TAB[SINLEN / 2] = PG_CUT_OFF;

        for (int i = 1; i <= SINLEN / 4; i++) {
            double x = Math.sin(2.0 * Math.PI * (double) (i) / (double) (SINLEN)); // Sinus
            x = 20 * Math.log10(1 / x); // convert to dB

            int j = (int) (x / ENV_STEP); // Get tl range

            if (j > PG_CUT_OFF) j = PG_CUT_OFF;

            SIN_TAB[i] = j;
            SIN_TAB[(SINLEN / 2) - i] = j;
            SIN_TAB[(SINLEN / 2) + i] = TLLEN + j;
            SIN_TAB[SINLEN - i] = TLLEN + j;
        }

        // LFO Table (LFO wav) :

        for (int i = 0; i < LFOLEN; i++) {
            double x = Math.sin(2.0 * Math.PI * (double) (i) / (double) (LFOLEN)); // Sinus
            x += 1.0;
            x /= 2.0;
            x *= 11.8 / ENV_STEP;
            LFO_ENV_TAB[i] = (int) x;

            x = Math.sin(2.0 * Math.PI * (double) (i) / (double) (LFOLEN)); // Sinus
            x *= (1 << (LFO_HBITS - 1)) - 1;
            LFO_FREQ_TAB[i] = (int) x;
        }

        for (int i = 0; i < ENVLEN; i++) {
            double x = Math.pow(((double) ((ENVLEN - 1) - i) / (double) (ENVLEN)), 8);
            x *= ENVLEN;
            ENV_TAB[i] = (int) x;

            x = Math.pow(((double) (i) / (double) (ENVLEN)), 1);
            x *= ENVLEN;
            ENV_TAB[ENVLEN + i] = (int) x;
        }

        ENV_TAB[ENV_END >> ENV_LBITS] = ENVLEN - 1;

        // Table Decay and Decay

        for (int i = 0, j = ENVLEN - 1; i < ENVLEN; i++) {
            while (j != 0 && (ENV_TAB[j] < i)) j--;
            DECAY_TO_ATTACK[i] = j << ENV_LBITS;
        }

        // Sustain Level Table

        for (int i = 0; i < 15; i++) {
            double x = i * 3;
            x /= ENV_STEP;

            int j = (int) x;
            j <<= ENV_LBITS;
            SL_TAB[i] = j + ENV_DECAY;
        }

        int j = ENVLEN - 1; // special case : volume off
        j <<= ENV_LBITS;
        SL_TAB[15] = j + ENV_DECAY;
    }

    public int reset() {
        int i, j;

        lfoCnt = 0;
        timerA = 0;
        timerAL = 0;
        timerACnt = 0;
        timerB = 0;
        timerBL = 0;
        timerBCnt = 0;
        dac = 0;

        status = 0;

        interCnt = 0;

        for (i = 0; i < 6; i++) {
            channels[i].oldOutD = 0;
            channels[i].outD = 0;
            channels[i].left = 0xffff_ffff;
            channels[i].right = 0xffff_ffff;
            channels[i].algo = 0;
            channels[i].fb = 31;
            channels[i].fms = 0;
            channels[i].ams = 0;

            for (j = 0; j < 4; j++) {
                channels[i].s0Out[j] = 0;
                channels[i].fNum[j] = 0;
                channels[i].fOct[j] = 0;
                channels[i].kc[j] = 0;

                channels[i].slots[j].fCnt = 0;
                channels[i].slots[j].fInc = 0;
                channels[i].slots[j].eCnt = ENV_END; // Put it at the end of Decay phase...
                channels[i].slots[j].eInc = 0;
                channels[i].slots[j].eCmp = 0;
                channels[i].slots[j].eCurp = RELEASE;

                channels[i].slots[j].chgEnM = 0;
            }
        }

        for (i = 0; i < 0x100; i++) {
            regs[0][i] = -1;
            regs[1][i] = -1;
        }

        for (i = 0xb6; i >= 0xb4; i--) {
            write0(i, 0xc0);
            write1(i, 0xc0);
        }

        for (i = 0xb2; i >= 0x22; i--) {
            write0(i, 0);
            write1(i, 0);
        }

        write0(0x2a, 0x80);

        return 0;
    }

    public int read() {
        return status;
    }

    /** opnA */
    public void write0(int addr, int data) {
logger.log(Level.TRACE, String.format("fm0: a: %02x, d: %02x", addr, data));
        if (addr < 0x30) {
            regs[0][addr] = data;
            setYM(addr, data);
        } else if (regs[0][addr] != data) {
            regs[0][addr] = data;

            if (addr < 0xa0)
                setSlot(addr, data);
            else
                setChannel(addr, data);
        }
    }

    /** opnB */
    public void write1(int addr, int data) {
logger.log(Level.TRACE, String.format("fm1: a: %02x, d: %02x", addr, data));
        if (addr >= 0x30 && regs[1][addr] != data) {
            regs[1][addr] = data;

            if (addr < 0xa0)
                setSlot(addr + 0x100, data);
            else
                setChannel(addr + 0x100, data);
        }
    }

    public void update(int[] buf_lr, int offset, int end) {
        offset *= 2;
        end = end * 2 + offset;

        if (channels[0].slots[0].fInc == -1) calc_FINC_CH(channels[0]);
        if (channels[1].slots[0].fInc == -1) calc_FINC_CH(channels[1]);
        if (channels[2].slots[0].fInc == -1) {
            if ((mode & 0x40) != 0) {
                calc_FINC_SL((channels[2].slots[S0]), FINC_TAB[channels[2].fNum[2]] >> (7 - channels[2].fOct[2]), channels[2].kc[2]);
                calc_FINC_SL((channels[2].slots[S1]), FINC_TAB[channels[2].fNum[3]] >> (7 - channels[2].fOct[3]), channels[2].kc[3]);
                calc_FINC_SL((channels[2].slots[S2]), FINC_TAB[channels[2].fNum[1]] >> (7 - channels[2].fOct[1]), channels[2].kc[1]);
                calc_FINC_SL((channels[2].slots[S3]), FINC_TAB[channels[2].fNum[0]] >> (7 - channels[2].fOct[0]), channels[2].kc[0]);
            } else {
                calc_FINC_CH(channels[2]);
            }
        }
        if (channels[3].slots[0].fInc == -1) calc_FINC_CH(channels[3]);
        if (channels[4].slots[0].fInc == -1) calc_FINC_CH(channels[4]);
        if (channels[5].slots[0].fInc == -1) calc_FINC_CH(channels[5]);

//        if (interStep & 0x04000) algo_type = 0;
//        else algo_type = 16;
        int algo_type = 0;

        if (lfoInc != 0) {
            // Precalculate LFO wave
            for (int o = offset; o < end; o += 2) {
                int i = o >> 1;
                int j = ((lfoCnt += lfoInc) >> LFO_LBITS) & LFO_MSK;

                LFO_ENV_UP[i] = LFO_ENV_TAB[j];
                LFO_FREQ_UP[i] = LFO_FREQ_TAB[j];
            }

            algo_type |= 8;
        }

        updateChannel(channels[0].algo + algo_type, channels[0], buf_lr, offset, end);
        updateChannel(channels[1].algo + algo_type, channels[1], buf_lr, offset, end);
        updateChannel(channels[2].algo + algo_type, channels[2], buf_lr, offset, end);
        updateChannel(channels[3].algo + algo_type, channels[3], buf_lr, offset, end);
        updateChannel(channels[4].algo + algo_type, channels[4], buf_lr, offset, end);
        if (dac == 0)
            updateChannel(channels[5].algo + algo_type, channels[5], buf_lr, offset, end);

        interCnt = int_cnt;
    }

    public void synchronizeTimers(int length) {

        int i = timerBase * length;

        if ((mode & 1) != 0) {
//			  if ((timerACnt -= 14073) <= 0) {	   // 13879=NTSC (old: 14475=NTSC  14586=PAL)
            if ((timerACnt -= i) <= 0) {
                status |= (mode & 0x04) >> 2;
                timerACnt += timerAL;

                if ((mode & 0x80) != 0) controlCsmKey();
            }
        }
        if ((mode & 2) != 0) {
//			  if ((timerBCnt -= 14073) <= 0) {	   // 13879=NTSC (old: 14475=NTSC  14586=PAL)
            if ((timerBCnt -= i) <= 0) {
                status |= (mode & 0x08) >> 2;
                timerBCnt += timerBL;
            }
        }
    }

    //
    // Parameter Calculation
    //

    private void calc_FINC_SL(YM2612.Slot sl, int fInc, int kc) {
        int ksr;
        sl.fInc = (fInc + sl.dt[kc]) * sl.mul;
        ksr = kc >> sl.ksrS;
        if (sl.ksr != ksr) {
            sl.ksr = ksr;
            sl.eIncA = AR_TAB[sl.ar + ksr];
            sl.eIncD = DR_TAB[sl.dr + ksr];
            sl.eIncS = DR_TAB[sl.sr + ksr];
            sl.eIncR = DR_TAB[sl.rr + ksr];
            if (sl.eCurp == ATTACK) sl.eInc = sl.eIncA;
            else if (sl.eCurp == DECAY) sl.eInc = sl.eIncD;
            else if (sl.eCnt < ENV_END) {
                if (sl.eCurp == SUSTAIN) sl.eInc = sl.eIncS;
                else if (sl.eCurp == RELEASE) sl.eInc = sl.eIncR;
            }
        }
    }

    private void calc_FINC_CH(Channel ch) {
        int fInc = FINC_TAB[ch.fNum[0]] >> (7 - ch.fOct[0]);
        int kc = ch.kc[0];
        calc_FINC_SL(ch.slots[0], fInc, kc);
        calc_FINC_SL(ch.slots[1], fInc, kc);
        calc_FINC_SL(ch.slots[2], fInc, kc);
        calc_FINC_SL(ch.slots[3], fInc, kc);
    }

    //
    // Settings
    //

    private static void keyOn(Channel ch, int nsl) {
        Slot sl = ch.slots[nsl];
        if (sl.eCurp == RELEASE) {
            sl.fCnt = 0;
            // Fix Ecco 2 splash sound
            sl.eCnt = (DECAY_TO_ATTACK[ENV_TAB[sl.eCnt >> ENV_LBITS]] + ENV_ATTACK) & sl.chgEnM;
            sl.chgEnM = 0xffff_ffff;
            sl.eInc = sl.eIncA;
            sl.eCmp = ENV_DECAY;
            sl.eCurp = ATTACK;
        }
    }

    private static void keyOff(Channel ch, int nsl) {
        Slot sl = ch.slots[nsl];
        if (sl.eCurp != RELEASE) {
            if (sl.eCnt < ENV_DECAY) {
                sl.eCnt = (ENV_TAB[sl.eCnt >> ENV_LBITS] << ENV_LBITS) + ENV_DECAY;
            }
            sl.eInc = sl.eIncR;
            sl.eCmp = ENV_END;
            sl.eCurp = RELEASE;
        }
    }

    private void controlCsmKey() {
        keyOn(channels[2], 0);
        keyOn(channels[2], 1);
        keyOn(channels[2], 2);
        keyOn(channels[2], 3);
    }

    private int setSlot(int address, int data) {  // INT, UCHAR
        data &= 0xff;    // unsign
        int nch;

        if ((nch = address & 3) == 3) return 1;
        int nsl = (address >> 2) & 3;

        if ((address & 0x100) != 0) nch += 3;

        Channel ch = channels[nch];
        Slot sl = ch.slots[nsl];

        switch (address & 0xf0) {
            case 0x30:
                if ((sl.mul = (data & 0x0f)) != 0) sl.mul <<= 1;
                else sl.mul = 1;
                sl.dt = DT_TAB[(data >> 4) & 7]; // = DT_TAB[(data >> 4) & 7];
                ch.slots[0].fInc = -1;
                break;
            case 0x40:
                sl.tl = data & 0x7f;
                // SOR2 do a lot of tl adjustement and this fix R.Shinobi jump sound...
                sl.tll = sl.tl << (ENV_HBITS - 7);
                break;
            case 0x50:
                sl.ksrS = 3 - (data >> 6);
                ch.slots[0].fInc = -1;
                if ((data &= 0x1f) != 0) sl.ar = data << 1; // = &AR_TAB[data << 1];
                else sl.ar = AR_NULL_RATE;                  // &NULL_RATE[0];

                sl.eIncA = AR_TAB[sl.ar + sl.ksr];          // sl.ar[sl.ksr];
                if (sl.eCurp == ATTACK) sl.eInc = sl.eIncA;
                break;
            case 0x60:
                if ((sl.amsOn = (data & 0x80)) != 0) sl.ams = ch.ams;
                else sl.ams = 31;

                if ((data &= 0x1f) != 0) sl.dr = data << 1; // = &DR_TAB[data << 1];
                else sl.dr = DR_NULL_RATE;                  // = &NULL_RATE[0];

                sl.eIncD = DR_TAB[sl.dr + sl.ksr];          // sl.dr[sl.ksr];
                if (sl.eCurp == DECAY) sl.eInc = sl.eIncD;
                break;
            case 0x70:
                if ((data &= 0x1f) != 0) sl.sr = data << 1; // = &DR_TAB[data << 1];
                else sl.sr = DR_NULL_RATE;                  // = &NULL_RATE[0];
                sl.eIncS = DR_TAB[sl.sr + sl.ksr];
                if ((sl.eCurp == SUSTAIN) && (sl.eCnt < ENV_END)) sl.eInc = sl.eIncS;
                break;
            case 0x80:
                sl.sll = SL_TAB[data >> 4];
                sl.rr = ((data & 0xf) << 2) + 2;            // = &DR_TAB[((data & 0xF) << 2) + 2];
                sl.eIncR = DR_TAB[sl.rr + sl.ksr];          // [sl.ksr];
                if ((sl.eCurp == RELEASE) && (sl.eCnt < ENV_END)) sl.eInc = sl.eIncR;
                break;
            case 0x90:
                if (EnableSSGEG) {
                    if ((data & 0x08) != 0) sl.seg = data & 0x0f;
                    else sl.seg = 0;
                }
                break;
        }
        return 0;
    }

    private int setChannel(int address, int data) {   // INT,UCHAR
        data &= 0xff;        // unsign
        Channel ch;
        int num;

        if ((num = address & 3) == 3) return 1;

        switch (address & 0xfc) {
            case 0xa0:
                if ((address & 0x100) != 0) num += 3;
                ch = channels[num];
                ch.fNum[0] = (ch.fNum[0] & 0x700) + data;
                ch.kc[0] = (ch.fOct[0] << 2) | FKEY_TAB[ch.fNum[0] >> 7];
                ch.slots[0].fInc = -1;
                break;
            case 0xa4:
                if ((address & 0x100) != 0) num += 3;
                ch = channels[num];
                ch.fNum[0] = (ch.fNum[0] & 0x0ff) + ((data & 0x07) << 8);
                ch.fOct[0] = (data & 0x38) >> 3;
                ch.kc[0] = (ch.fOct[0] << 2) | FKEY_TAB[ch.fNum[0] >> 7];
                ch.slots[0].fInc = -1;
                break;
            case 0xa8:
                if (address < 0x100) {
                    num++;
                    channels[2].fNum[num] = (channels[2].fNum[num] & 0x700) + data;
                    channels[2].kc[num] = (channels[2].fOct[num] << 2) | FKEY_TAB[channels[2].fNum[num] >> 7];
                    channels[2].slots[0].fInc = -1;
                }
                break;
            case 0xac:
                if (address < 0x100) {
                    num++;
                    channels[2].fNum[num] = (channels[2].fNum[num] & 0x0ff) + ((data & 0x07) << 8);
                    channels[2].fOct[num] = (data & 0x38) >> 3;
                    channels[2].kc[num] = (channels[2].fOct[num] << 2) | FKEY_TAB[channels[2].fNum[num] >> 7];
                    channels[2].slots[0].fInc = -1;
                }
                break;
            case 0xb0:
                if ((address & 0x100) != 0) num += 3;
                ch = channels[num];
                if (ch.algo != (data & 7)) {
                    ch.algo = data & 7;
                    ch.slots[0].chgEnM = 0;
                    ch.slots[1].chgEnM = 0;
                    ch.slots[2].chgEnM = 0;
                    ch.slots[3].chgEnM = 0;
                }
                ch.fb = 9 - ((data >> 3) & 7);                  // Real thing ?
//                if(ch.fb = ((data >> 3) & 7)) ch.fb = 9 - ch.fb;	// Thunder force 4 (music stage 8), Gynoug, Aladdin bug sound...
//                else ch.fb = 31;
                break;
            case 0xb4:
                if ((address & 0x100) != 0) num += 3;
                ch = channels[num];
                if ((data & 0x80) != 0) ch.left = 0xffff_ffff;
                else ch.left = 0;
                if ((data & 0x40) != 0) ch.right = 0xffff_ffff;
                else ch.right = 0;
                ch.ams = LFO_AMS_TAB[(data >> 4) & 3];
                ch.fms = LFO_FMS_TAB[data & 7];
                if (ch.slots[0].amsOn != 0) ch.slots[0].ams = ch.ams;
                else ch.slots[0].ams = 31;
                if (ch.slots[1].amsOn != 0) ch.slots[1].ams = ch.ams;
                else ch.slots[1].ams = 31;
                if (ch.slots[2].amsOn != 0) ch.slots[2].ams = ch.ams;
                else ch.slots[2].ams = 31;
                if (ch.slots[3].amsOn != 0) ch.slots[3].ams = ch.ams;
                else ch.slots[3].ams = 31;
                break;
        }
        return 0;
    }

    private int setYM(int address, int data) {       // INT, UCHAR
        Channel ch;
        int nch;

        switch (address) {
            case 0x22:
                if ((data & 8) != 0) {
                    // Cool Spot music 1, LFO modified severals time which
                    // distorts the sound, have to check that on a real genesis...
                    lfoInc = LFO_INC_TAB[data & 7];
                } else {
                    lfoInc = lfoCnt = 0;
                }
                break;
            case 0x24:
                timerA = (timerA & 0x003) | (data << 2);
                if (timerAL != ((1024 - timerA) << 12)) {
                    timerACnt = timerAL = (1024 - timerA) << 12;
                }
//				  System.out.println("Timer AH: " + Integer.toHexString(timerA));
                break;
            case 0x25:
                timerA = (timerA & 0x3fc) | (data & 3);
                if (timerAL != ((1024 - timerA) << 12)) {
                    timerACnt = timerAL = (1024 - timerA) << 12;
                }
//				  System.out.println("Timer AL: " + Integer.toHexString(timerA));
                break;
            case 0x26:
                timerB = data;
                if (timerBL != ((256 - timerB) << (4 + 12))) {
                    timerBCnt = timerBL = (256 - timerB) << (4 + 12);
                }
//				  System.out.println("Timer B : " + Integer.toHexString(timerB));
                break;
            case 0x27:
                if (((data ^ mode) & 0x40) != 0) {
                    // We changed the channel 2 mode, so recalculate phase step
                    // This fix the punch sound in Street of Rage 2
                    channels[2].slots[0].fInc = -1;    // recalculate phase step
                }
                status &= (~data >> 4) & (data >> 2);  // Reset Status
                mode = data;
                break;
            case 0x28:
                if ((nch = data & 3) == 3) return 1;
                if ((data & 4) != 0) nch += 3;
                ch = channels[nch];
                if ((data & 0x10) != 0) keyOn(ch, S0);
                else keyOff(ch, S0);
                if ((data & 0x20) != 0) keyOn(ch, S1);
                else keyOff(ch, S1);
                if ((data & 0x40) != 0) keyOn(ch, S2);
                else keyOff(ch, S2);
                if ((data & 0x80) != 0) keyOn(ch, S3);
                else keyOff(ch, S3);
                break;
            case 0x2A:
                break;
            case 0x2B:
                dac = data & 0x80;
                break;
        }
        return 0;
    }

    //
    // Generation Methods
    //

    private void envNullNext(Slot sl) {
    }

    private static void envAttackNext(Slot sl) {
        sl.eCnt = ENV_DECAY;
        sl.eInc = sl.eIncD;
        sl.eCmp = sl.sll;
        sl.eCurp = DECAY;
    }

    private static void envDecayNext(Slot sl) {
        sl.eCnt = sl.sll;
        sl.eInc = sl.eIncS;
        sl.eCmp = ENV_END;
        sl.eCurp = SUSTAIN;
    }

    private static void envSustainNext(Slot sl) {
        if (EnableSSGEG) {
            if ((sl.seg & 8) != 0) {
                if ((sl.seg & 1) != 0) {
                    sl.eCnt = ENV_END;
                    sl.eInc = 0;
                    sl.eCmp = ENV_END + 1;
                } else {
                    sl.eCnt = 0;
                    sl.eInc = sl.eIncA;
                    sl.eCmp = ENV_DECAY;
                    sl.eCurp = ATTACK;
                }
                sl.seg ^= (sl.seg & 2) << 1;
            } else {
                sl.eCnt = ENV_END;
                sl.eInc = 0;
                sl.eCmp = ENV_END + 1;
            }
        } else {
            sl.eCnt = ENV_END;
            sl.eInc = 0;
            sl.eCmp = ENV_END + 1;
        }
    }

    private static void envReleaseNext(Slot sl) {
        sl.eCnt = ENV_END;
        sl.eInc = 0;
        sl.eCmp = ENV_END + 1;
    }

    private static void envNextEvent(int which, Slot sl) {
        switch (which) {
            case 0:
                envAttackNext(sl);
                return;
            case 1:
                envDecayNext(sl);
                return;
            case 2:
                envSustainNext(sl);
                return;
            case 3:
                envReleaseNext(sl);
                return;
//            default:
//                envNullNext(sl);
//                return;
        }
    }

    private void calcChannel(int algo, Channel ch) {
        // DO_FEEDBACK
        in0 += (ch.s0Out[0] + ch.s0Out[1]) >> ch.fb;
        ch.s0Out[1] = ch.s0Out[0];
        ch.s0Out[0] = TL_TAB[SIN_TAB[(in0 >> SIN_LBITS) & SIN_MSK] + en0];
        switch (algo) {
            case 0:
                in1 += ch.s0Out[1];
                in2 += TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1];
                in3 += TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2];
                ch.outD = TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] >> MAIN_SHIFT;
                break;
            case 1:
                in2 += ch.s0Out[1] + TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1];
                in3 += TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2];
                ch.outD = TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] >> MAIN_SHIFT;
                break;
            case 2:
                in2 += TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1];
                in3 += ch.s0Out[1] + TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2];
                ch.outD = TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] >> MAIN_SHIFT;
                break;
            case 3:
                in1 += ch.s0Out[1];
                in3 += TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1] + TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2];
                ch.outD = TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] >> MAIN_SHIFT;
                break;
            case 4:
                in1 += ch.s0Out[1];
                in3 += TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2];
                ch.outD = (TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] +
                        TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1]) >> MAIN_SHIFT;
                break;
            case 5:
                in1 += ch.s0Out[1];
                in2 += ch.s0Out[1];
                in3 += ch.s0Out[1];
                ch.outD = (TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] +
                        TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1] +
                        TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2]) >> MAIN_SHIFT;
                break;
            case 6:
                in1 += ch.s0Out[1];
                ch.outD = (TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] +
                        TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1] +
                        TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2]) >> MAIN_SHIFT;
                break;
            case 7:
                ch.outD = (TL_TAB[SIN_TAB[(in3 >> SIN_LBITS) & SIN_MSK] + en3] +
                        TL_TAB[SIN_TAB[(in1 >> SIN_LBITS) & SIN_MSK] + en1] +
                        TL_TAB[SIN_TAB[(in2 >> SIN_LBITS) & SIN_MSK] + en2] +
                        ch.s0Out[1]) >> MAIN_SHIFT;
                break;
        }
        // DO_LIMIT
        if (ch.outD > LIMIT_CH_OUT) ch.outD = LIMIT_CH_OUT;
        else if (ch.outD < -LIMIT_CH_OUT) ch.outD = -LIMIT_CH_OUT;
    }

    private void processChannel(Channel ch, int[] buf_lr, int offset, int end, int algo) {
        if (algo < 4) {
            if (ch.slots[S3].eCnt == ENV_END)
                return;
        } else if (algo == 4) {
            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;
        } else if (algo < 7) {
            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;
        } else {
            if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                    (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;
        }

        do {
            // GET_CURRENT_PHASE
            in0 = ch.slots[S0].fCnt;
            in1 = ch.slots[S1].fCnt;
            in2 = ch.slots[S2].fCnt;
            in3 = ch.slots[S3].fCnt;
            // UPDATE_PHASE
            ch.slots[S0].fCnt += ch.slots[S0].fInc;
            ch.slots[S1].fCnt += ch.slots[S1].fInc;
            ch.slots[S2].fCnt += ch.slots[S2].fInc;
            ch.slots[S3].fCnt += ch.slots[S3].fInc;
            // GET_CURRENT_ENV
            if ((ch.slots[S0].seg & 4) != 0) {
                if ((en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll) > ENV_MSK) en0 = 0;
                else en0 ^= ENV_MSK;
            } else en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll;
            if ((ch.slots[S1].seg & 4) != 0) {
                if ((en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll) > ENV_MSK) en1 = 0;
                else en1 ^= ENV_MSK;
            } else en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll;
            if ((ch.slots[S2].seg & 4) != 0) {
                if ((en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll) > ENV_MSK) en2 = 0;
                else en2 ^= ENV_MSK;
            } else en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll;
            if ((ch.slots[S3].seg & 4) != 0) {
                if ((en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll) > ENV_MSK) en3 = 0;
                else en3 ^= ENV_MSK;
            } else en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll;
            // UPDATE_ENV
            if ((ch.slots[S0].eCnt += ch.slots[S0].eInc) >= ch.slots[S0].eCmp) {
                envNextEvent(ch.slots[S0].eCurp, ch.slots[S0]);
            }
            if ((ch.slots[S1].eCnt += ch.slots[S1].eInc) >= ch.slots[S1].eCmp) {
                envNextEvent(ch.slots[S1].eCurp, ch.slots[S1]);
            }
            if ((ch.slots[S2].eCnt += ch.slots[S2].eInc) >= ch.slots[S2].eCmp) {
                envNextEvent(ch.slots[S2].eCurp, ch.slots[S2]);
            }
            if ((ch.slots[S3].eCnt += ch.slots[S3].eInc) >= ch.slots[S3].eCmp) {
                envNextEvent(ch.slots[S3].eCurp, ch.slots[S3]);
            }
            calcChannel(algo, ch);
            // DO_OUTPUT
            buf_lr[offset] += (ch.outD & ch.left);
            buf_lr[offset + 1] += (ch.outD & ch.right);
            offset += 2;
        }
        while (offset < end);
    }

    private void processChannel_LFO(Channel ch, int[] buf_lr, int offset, int end, int algo) {
        if (algo < 4) {
            if (ch.slots[S3].eCnt == ENV_END)
                return;
        } else if (algo == 4) {
            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;
        } else if (algo < 7) {
            if ((ch.slots[S1].eCnt == ENV_END) && (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;
        } else {
            if ((ch.slots[S0].eCnt == ENV_END) && (ch.slots[S1].eCnt == ENV_END) &&
                    (ch.slots[S2].eCnt == ENV_END) && (ch.slots[S3].eCnt == ENV_END))
                return;
        }

        do {
            int i = offset >> 1;

            // GET_CURRENT_PHASE
            in0 = ch.slots[S0].fCnt;
            in1 = ch.slots[S1].fCnt;
            in2 = ch.slots[S2].fCnt;
            in3 = ch.slots[S3].fCnt;
            // UPDATE_PHASE_LFO
            int freq_LFO = (ch.fms * LFO_FREQ_UP[i]) >> (LFO_HBITS - 1);
            if (freq_LFO != 0) {
                ch.slots[S0].fCnt += ch.slots[S0].fInc + ((ch.slots[S0].fInc * freq_LFO) >> LFO_FMS_LBITS);
                ch.slots[S1].fCnt += ch.slots[S1].fInc + ((ch.slots[S1].fInc * freq_LFO) >> LFO_FMS_LBITS);
                ch.slots[S2].fCnt += ch.slots[S2].fInc + ((ch.slots[S2].fInc * freq_LFO) >> LFO_FMS_LBITS);
                ch.slots[S3].fCnt += ch.slots[S3].fInc + ((ch.slots[S3].fInc * freq_LFO) >> LFO_FMS_LBITS);
            } else {
                ch.slots[S0].fCnt += ch.slots[S0].fInc;
                ch.slots[S1].fCnt += ch.slots[S1].fInc;
                ch.slots[S2].fCnt += ch.slots[S2].fInc;
                ch.slots[S3].fCnt += ch.slots[S3].fInc;
            }
            // GET_CURRENT_ENV_LFO
            int env_LFO = LFO_ENV_UP[i];
            if ((ch.slots[S0].seg & 4) != 0) {
                if ((en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll) > ENV_MSK) en0 = 0;
                else en0 = (en0 ^ ENV_MSK) + (env_LFO >> ch.slots[S0].ams);
            } else en0 = ENV_TAB[(ch.slots[S0].eCnt >> ENV_LBITS)] + ch.slots[S0].tll + (env_LFO >> ch.slots[S0].ams);
            if ((ch.slots[S1].seg & 4) != 0) {
                if ((en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll) > ENV_MSK) en1 = 0;
                else en1 = (en1 ^ ENV_MSK) + (env_LFO >> ch.slots[S1].ams);
            } else en1 = ENV_TAB[(ch.slots[S1].eCnt >> ENV_LBITS)] + ch.slots[S1].tll + (env_LFO >> ch.slots[S1].ams);
            if ((ch.slots[S2].seg & 4) != 0) {
                if ((en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll) > ENV_MSK) en2 = 0;
                else en2 = (en2 ^ ENV_MSK) + (env_LFO >> ch.slots[S2].ams);
            } else en2 = ENV_TAB[(ch.slots[S2].eCnt >> ENV_LBITS)] + ch.slots[S2].tll + (env_LFO >> ch.slots[S2].ams);
            if ((ch.slots[S3].seg & 4) != 0) {
                if ((en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll) > ENV_MSK) en3 = 0;
                else en3 = (en3 ^ ENV_MSK) + (env_LFO >> ch.slots[S3].ams);
            } else
                en3 = ENV_TAB[(ch.slots[S3].eCnt >> ENV_LBITS)] + ch.slots[S3].tll + (env_LFO >> ch.slots[S3].ams);

            // UPDATE_ENV
            if ((ch.slots[S0].eCnt += ch.slots[S0].eInc) >= ch.slots[S0].eCmp)
                envNextEvent(ch.slots[S0].eCurp, ch.slots[S0]);

            if ((ch.slots[S1].eCnt += ch.slots[S1].eInc) >= ch.slots[S1].eCmp)
                envNextEvent(ch.slots[S1].eCurp, ch.slots[S1]);

            if ((ch.slots[S2].eCnt += ch.slots[S2].eInc) >= ch.slots[S2].eCmp)
                envNextEvent(ch.slots[S2].eCurp, ch.slots[S2]);

            if ((ch.slots[S3].eCnt += ch.slots[S3].eInc) >= ch.slots[S3].eCmp)
                envNextEvent(ch.slots[S3].eCurp, ch.slots[S3]);

            calcChannel(algo, ch);
            // DO_OUTPUT
            int left = (ch.outD & ch.left);
            int right = (ch.outD & ch.right);

            buf_lr[offset] += left;
            buf_lr[offset + 1] += right;
            offset += 2;
        }
        while (offset < end);
    }

    private void updateChannel(int algo, Channel ch, int[] buf_lr, int offset, int end) {
        if (algo < 8) {
            processChannel(ch, buf_lr, offset, end, algo);
        } else {
            processChannel_LFO(ch, buf_lr, offset, end, algo - 8);
        }
    }
}
