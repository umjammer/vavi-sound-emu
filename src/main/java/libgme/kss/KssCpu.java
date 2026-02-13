/*
 * Game Music Emulators
 *
 * copyright (C) 2003-2009 Shay Green.
 */

package libgme.kss;


/**
 * Z80 CPU emulator
 */
public abstract class KssCpu {

    public static final int PAGE_SIZE = 0x2000;
    public static final int PAGE_SHIFT = 13;
    public static final int PAGE_COUNT = 0x10000 >> PAGE_SHIFT;

    public static class Registers {

        public int pc, sp, ix, iy;
        public int a, f, b, c, d, e, h, l;
        public int altA, altF, altB, altC, altD, altE, altH, altL;
        public int iff1, iff2, r, i, im;

        public void reset() {
            pc = sp = ix = iy = 0;
            a = f = b = c = d = e = h = l = 0;
            altA = altF = altB = altC = altD = altE = altH = altL = 0;
            iff1 = iff2 = r = i = im = 0;
        }
    }

    public final Registers r = new Registers();
    private final byte[][] readPages = new byte[PAGE_COUNT + 1][];
    private final int[] readOffsets = new int[PAGE_COUNT + 1];
    private final byte[][] writePages = new byte[PAGE_COUNT + 1][];
    private final int[] writeOffsets = new int[PAGE_COUNT + 1];
    private int baseTime, currentTime;
    private final int[] szpc = new int[0x200];

    public KssCpu() {
        for (int i = 0; i < 0x100; i++) {
            int even = 1;
            for (int p = i; p != 0; p >>= 1) even ^= p;
            int n = (i & (0x80 | 0x20 | 0x08)) | ((even & 1) << 2);
            szpc[i] = n;
            szpc[i + 0x100] = n | 0x01;
        }
        szpc[0x000] |= 0x40;
        szpc[0x100] |= 0x40;
    }

    public abstract void out(int time, int addr, int data);

    public abstract int in(int time, int addr);

    public abstract void writeMem(int addr, int data);

    public void reset(byte[] unmappedWrite, byte[] unmappedRead) {
        currentTime = 0;
        baseTime = 0;
        for (int i = 0; i < PAGE_COUNT + 1; i++) {
            setPage(i, unmappedWrite, 0, unmappedRead, 0);
        }
        r.reset();
    }

    private void setPage(int i, byte[] write, int writeOff, byte[] read, int readOff) {
        readPages[i] = read;
        readOffsets[i] = readOff - (i << PAGE_SHIFT);
        writePages[i] = write;
        writeOffsets[i] = writeOff - (i << PAGE_SHIFT);
    }

    public void mapMem(int addr, int size, byte[] write, int writeOff, byte[] read, int readOff) {
        int firstPage = addr >> PAGE_SHIFT;
        for (int i = 0; i < (size >> PAGE_SHIFT); i++) {
            int offset = i << PAGE_SHIFT;
            setPage(firstPage + i, write, writeOff + offset, read, readOff + offset);
        }
    }

    public int time() {
        return currentTime + baseTime;
    }

    public void setTime(int t) {
        currentTime = t - baseTime;
    }

    public void adjustTime(int delta) {
        currentTime += delta;
    }

    public int readMem(int addr) {
        int page = addr >> PAGE_SHIFT;
        return readPages[page][readOffsets[page] + addr] & 0xFF;
    }

    private int read8(int addr) {
        return readMem(addr);
    }

    private int read16(int addr) {
        return read8(addr) | (read8(addr + 1) << 8);
    }

    private void write8(int addr, int data) {
        writeMem(addr, data & 0xFF);
    }

    private void write16(int addr, int data) {
        write8(addr, data);
        write8(addr + 1, data >> 8);
    }

    /** Write directly to the mapped write page (used by cpuWrite to match C++ behavior) */
    public void writeToPage(int addr, int data) {
        int page = addr >> PAGE_SHIFT;
        writePages[page][writeOffsets[page] + addr] = (byte) data;
    }

    private void writeDirect8(int addr, int data) {
        writeToPage(addr, data);
    }

    private void writeDirect16(int addr, int data) {
        writeDirect8(addr, data);
        writeDirect8(addr + 1, data >> 8);
    }

    private static final int S80 = 0x80, Z40 = 0x40, F20 = 0x20, H10 = 0x10, F08 = 0x08, V04 = 0x04, P04 = 0x04, N02 = 0x02, C01 = 0x01;

    private static final int[] baseTiming = {
            4, 10, 7, 6, 4, 4, 7, 4, 4, 11, 7, 6, 4, 4, 7, 4, 13, 10, 7, 6, 4, 4, 7, 4, 12, 11, 7, 6, 4, 4, 7, 4,
            12, 10, 16, 6, 4, 4, 7, 4, 12, 11, 16, 6, 4, 4, 7, 4, 12, 10, 13, 6, 11, 11, 10, 4, 12, 11, 13, 6, 4, 4, 7, 4,
            4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
            4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 7, 7, 7, 7, 7, 7, 4, 7, 4, 4, 4, 4, 4, 4, 7, 4,
            4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
            4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
            11, 10, 10, 10, 17, 11, 7, 11, 11, 10, 10, 8, 17, 17, 7, 11, 11, 10, 10, 11, 17, 11, 7, 11, 11, 4, 10, 11, 17, 8, 7, 11,
            11, 10, 10, 19, 17, 11, 7, 11, 11, 4, 10, 4, 17, 8, 7, 11, 11, 10, 10, 4, 17, 11, 7, 11, 11, 6, 10, 4, 17, 8, 7, 11
    };

    private static final int[] edDdTiming = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x06, 0x0C, 0x02, 0x00, 0x00, 0x03, 0x00, 0x00, 0x07, 0x0C, 0x02, 0x00, 0x00, 0x03, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x0F, 0x0F, 0x0B, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0x10, 0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0x10,
            0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0x10, 0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0x10,
            0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0xA0, 0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0xA0,
            0x4B, 0x4B, 0x7B, 0xCB, 0x0B, 0x6B, 0x00, 0x0B, 0x40, 0x40, 0x70, 0xC0, 0x00, 0x60, 0x0B, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B, 0x00,
            0x80, 0x80, 0x80, 0x80, 0x00, 0x00, 0x0B, 0x00, 0x80, 0x80, 0x80, 0x80, 0x00, 0x00, 0x0B, 0x00,
            0xD0, 0xD0, 0xD0, 0xD0, 0x00, 0x00, 0x0B, 0x00, 0xD0, 0xD0, 0xD0, 0xD0, 0x00, 0x00, 0x0B, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x06, 0x00, 0x0F, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    };

    public boolean run(int endTime) {
        int s_time = currentTime + baseTime - endTime;
        int pc = r.pc, sp = r.sp, ix = r.ix, iy = r.iy;
        int a = r.a, f = r.f, b = r.b, c = r.c, d = r.d, e = r.e, h = r.h, l = r.l;
        boolean warning = false;

main_loop:
        while (true) {
            int opcode = read8(pc++);
            int timing = baseTiming[opcode];
            s_time += timing;
            if (s_time >= 0) {
                if (s_time >= timing) {
                    s_time -= timing;
                    pc--;
                    break main_loop;
                }
            }

            switch (opcode) {
                case 0x00:
                    continue main_loop; // NOP
                case 0x08: {
                    int t = r.altA;
                    r.altA = a;
                    a = t;
                    t = r.altF;
                    r.altF = f;
                    f = t;
                    continue main_loop;
                } // EX AF,AF'
                case 0xD3:
                    out(s_time + endTime, read8(pc++) | ((a & 0xFF) << 8), a);
                    continue main_loop; // OUT (imm),A
                case 0x2E:
                    l = read8(pc++);
                    continue main_loop; // LD L,imm
                case 0x3E:
                    a = read8(pc++);
                    continue main_loop; // LD A,imm
                case 0x3A:
                    a = read8(read16(pc));
                    pc += 2;
                    continue main_loop; // LD A,(addr)
                case 0x18:
                case 0x20:
                case 0x28:
                case 0x30:
                case 0x38: { // JR
                    int off = (byte) read8(pc++);
                    boolean cond = opcode == 0x18 || (opcode == 0x20 && (f & Z40) == 0) || (opcode == 0x28 && (f & Z40) != 0) || (opcode == 0x30 && (f & C01) == 0) || (opcode == 0x38 && (f & C01) != 0);
                    if (cond) pc = (pc + off) & 0xFFFF;
                    else s_time -= 5;
                    continue main_loop;
                }
                case 0x10: {
                    int off = (byte) read8(pc++);
                    b = (b - 1) & 0xFF;
                    if (b != 0) pc = (pc + off) & 0xFFFF;
                    else s_time -= 5;
                    continue main_loop;
                } // DJNZ
                case 0xC3:
                    pc = read16(pc);
                    continue main_loop; // JP addr
                case 0xE9:
                    pc = (h << 8) | l;
                    continue main_loop; // JP HL
                case 0xC2:
                case 0xCA:
                case 0xD2:
                case 0xDA:
                case 0xE2:
                case 0xEA:
                case 0xF2:
                case 0xFA: { // JP cond
                    int addr = read16(pc);
                    boolean cond = (opcode == 0xC2 && (f & Z40) == 0) || (opcode == 0xCA && (f & Z40) != 0) || (opcode == 0xD2 && (f & C01) == 0) || (opcode == 0xDA && (f & C01) != 0) || (opcode == 0xE2 && (f & P04) == 0) || (opcode == 0xEA && (f & P04) != 0) || (opcode == 0xF2 && (f & S80) == 0) || (opcode == 0xFA && (f & S80) != 0);
                    if (cond) pc = addr;
                    else pc += 2;
                    continue main_loop;
                }
                case 0xC9:
                    pc = read16(sp);
                    sp = (sp + 2) & 0xFFFF;
                    continue main_loop; // RET
                case 0xC0:
                case 0xC8:
                case 0xD0:
                case 0xD8:
                case 0xE0:
                case 0xE8:
                case 0xF0:
                case 0xF8: { // RET cond
                    boolean cond = (opcode == 0xC0 && (f & Z40) == 0) || (opcode == 0xC8 && (f & Z40) != 0) || (opcode == 0xD0 && (f & C01) == 0) || (opcode == 0xD8 && (f & C01) != 0) || (opcode == 0xE0 && (f & P04) == 0) || (opcode == 0xE8 && (f & P04) != 0) || (opcode == 0xF0 && (f & S80) == 0) || (opcode == 0xF8 && (f & S80) != 0);
                    if (cond) {
                        pc = read16(sp);
                        sp = (sp + 2) & 0xFFFF;
                    } else s_time -= 6;
                    continue main_loop;
                }
                case 0xCD: {
                    int addr = read16(pc);
                    pc += 2;
                    sp = (sp - 2) & 0xFFFF;
                    writeDirect16(sp, pc);
                    pc = addr;
                    continue main_loop;
                } // CALL addr
                case 0xC4:
                case 0xCC:
                case 0xD4:
                case 0xDC:
                case 0xE4:
                case 0xEC:
                case 0xF4:
                case 0xFC: { // CALL cond
                    int addr = read16(pc);
                    pc += 2;
                    boolean cond = (opcode == 0xC4 && (f & Z40) == 0) || (opcode == 0xCC && (f & Z40) != 0) || (opcode == 0xD4 && (f & C01) == 0) || (opcode == 0xDC && (f & C01) != 0) || (opcode == 0xE4 && (f & P04) == 0) || (opcode == 0xEC && (f & P04) != 0) || (opcode == 0xF4 && (f & S80) == 0) || (opcode == 0xFC && (f & S80) != 0);
                    if (cond) {
                        sp = (sp - 2) & 0xFFFF;
                        writeDirect16(sp, pc);
                        pc = addr;
                    } else s_time -= 7;
                    continue main_loop;
                }
                case 0xFF:
                    if (pc > 0xFFFF) {
                        s_time -= 11;
                        pc--;
                        break main_loop;
                    } // RST / IDLE
                case 0xC7:
                case 0xCF:
                case 0xD7:
                case 0xDF:
                case 0xE7:
                case 0xEF:
                case 0xF7: { // RST
                    sp = (sp - 2) & 0xFFFF;
                    writeDirect16(sp, pc);
                    pc = opcode & 0x38;
                    continue main_loop;
                }
                case 0xF5:
                    sp = (sp - 2) & 0xFFFF;
                    writeDirect16(sp, ((a & 0xFF) << 8) | (f & 0xFF));
                    continue main_loop; // PUSH AF
                case 0xC5:
                    sp = (sp - 2) & 0xFFFF;
                    writeDirect16(sp, ((b & 0xFF) << 8) | (c & 0xFF));
                    continue main_loop; // PUSH BC
                case 0xD5:
                    sp = (sp - 2) & 0xFFFF;
                    writeDirect16(sp, ((d & 0xFF) << 8) | (e & 0xFF));
                    continue main_loop; // PUSH DE
                case 0xE5:
                    sp = (sp - 2) & 0xFFFF;
                    writeDirect16(sp, ((h & 0xFF) << 8) | (l & 0xFF));
                    continue main_loop; // PUSH HL
                case 0xF1:
                    f = read8(sp);
                    a = read8(sp + 1);
                    sp = (sp + 2) & 0xFFFF;
                    continue main_loop; // POP AF
                case 0xC1:
                    c = read8(sp);
                    b = read8(sp + 1);
                    sp = (sp + 2) & 0xFFFF;
                    continue main_loop; // POP BC
                case 0xD1:
                    e = read8(sp);
                    d = read8(sp + 1);
                    sp = (sp + 2) & 0xFFFF;
                    continue main_loop; // POP DE
                case 0xE1:
                    l = read8(sp);
                    h = read8(sp + 1);
                    sp = (sp + 2) & 0xFFFF;
                    continue main_loop; // POP HL
                case 0x96:
                case 0x86:
                case 0x9E:
                case 0x8E:
                case 0xD6:
                case 0xC6:
                case 0xDE:
                case 0xCE:
                case 0x90:
                case 0x91:
                case 0x92:
                case 0x93:
                case 0x94:
                case 0x95:
                case 0x97:
                case 0x80:
                case 0x81:
                case 0x82:
                case 0x83:
                case 0x84:
                case 0x85:
                case 0x87:
                case 0x98:
                case 0x99:
                case 0x9A:
                case 0x9B:
                case 0x9C:
                case 0x9D:
                case 0x9F:
                case 0x88:
                case 0x89:
                case 0x8A:
                case 0x8B:
                case 0x8C:
                case 0x8D:
                case 0x8F: { // ADC/ADD/SBC/SUB
                    int val;
                    if ((opcode & 0xC0) == 0x80) {
                        int rIdx = opcode & 7;
                        val = (rIdx == 0) ? b : (rIdx == 1) ? c : (rIdx == 2) ? d : (rIdx == 3) ? e : (rIdx == 4) ? h : (rIdx == 5) ? l : (rIdx == 7) ? a : read8((h << 8) | l);
                    } else val = read8(pc++);
                    if ((opcode & 0x08) == 0) f &= ~C01;
                    int res = val + (f & C01);
                    int lookup = val ^ a;
                    int f_base = (opcode >> 3) & N02;
                    if (f_base != 0) res = -res;
                    res += a;
                    lookup ^= res;
                    f = f_base | (lookup & H10) | (((lookup - -0x80) >> 6) & V04) | (szpc[res & 0x1FF] & ~P04);
                    a = res & 0xFF;
                    continue main_loop;
                }
                case 0xBE:
                case 0xFE:
                case 0xB8:
                case 0xB9:
                case 0xBA:
                case 0xBB:
                case 0xBC:
                case 0xBD:
                case 0xBF: { // CP
                    int val;
                    if (opcode == 0xBE) val = read8((h << 8) | l);
                    else if (opcode == 0xFE) val = read8(pc++);
                    else {
                        int rIdx = opcode & 7;
                        val = (rIdx == 0) ? b : (rIdx == 1) ? c : (rIdx == 2) ? d : (rIdx == 3) ? e : (rIdx == 4) ? h : (rIdx == 5) ? l : a;
                    }
                    int res = a - val;
                    f = N02 | (val & (F20 | F08)) | ((res >> 8) & C01);
                    int lookup = val ^ a;
                    f |= (((res ^ a) & lookup) >> 5 & V04) | (((lookup & H10) ^ res) & (S80 | H10));
                    if ((res & 0xFF) == 0) f |= Z40;
                    continue main_loop;
                }
                case 0x39:
                case 0x09:
                case 0x19:
                case 0x29: { // ADD HL,rp
                    int val = (opcode == 0x39) ? sp : (opcode == 0x09) ? (b << 8) | c : (opcode == 0x19) ? (d << 8) | e : (h << 8) | l;
                    int hl = (h << 8) | l;
                    int sum = hl + val;
                    int lookup = val ^ hl;
                    h = (sum >> 8) & 0xFF;
                    l = sum & 0xFF;
                    f = (f & (S80 | Z40 | V04)) | (sum >> 16) | ((sum >> 8) & (F20 | F08)) | (((lookup ^ sum) >> 8) & H10);
                    continue main_loop;
                }
                case 0x27: { // DAA
                    int adj = 0;
                    if ((f & C01) != 0 || a > 0x99) {
                        adj |= 0x60;
                        f |= C01;
                    }
                    if ((f & H10) != 0 || (a & 0x0F) > 9) adj |= 0x06;
                    int oldA = a;
                    if ((f & N02) != 0) a -= adj;
                    else a += adj;
                    f = (f & (C01 | N02)) | ((oldA ^ a) & H10) | szpc[a & 0xFF];
                    a &= 0xFF;
                    continue main_loop;
                }
                case 0x34:
                case 0x04:
                case 0x0C:
                case 0x14:
                case 0x1C:
                case 0x24:
                case 0x2C:
                case 0x3C: { // INC
                    int val;
                    if (opcode == 0x34) {
                        int addr = (h << 8) | l;
                        val = (read8(addr) + 1) & 0xFF;
                        write8(addr, val);
                    } else {
                        int rIdx = opcode >> 3;
                        if (rIdx == 0) b = val = (b + 1) & 0xFF;
                        else if (rIdx == 1) c = val = (c + 1) & 0xFF;
                        else if (rIdx == 2) d = val = (d + 1) & 0xFF;
                        else if (rIdx == 3) e = val = (e + 1) & 0xFF;
                        else if (rIdx == 4) h = val = (h + 1) & 0xFF;
                        else if (rIdx == 5) l = val = (l + 1) & 0xFF;
                        else a = val = (a + 1) & 0xFF;
                    }
                    f = (f & C01) | (((val & 0x0F) == 0) ? H10 : 0) | (szpc[val] & ~P04);
                    if (val == 0x80) f |= V04;
                    continue main_loop;
                }
                case 0x35:
                case 0x05:
                case 0x0D:
                case 0x15:
                case 0x1D:
                case 0x25:
                case 0x2D:
                case 0x3D: { // DEC
                    int val;
                    if (opcode == 0x35) {
                        int addr = (h << 8) | l;
                        val = (read8(addr) - 1) & 0xFF;
                        write8(addr, val);
                    } else {
                        int rIdx = opcode >> 3;
                        if (rIdx == 0) b = val = (b - 1) & 0xFF;
                        else if (rIdx == 1) c = val = (c - 1) & 0xFF;
                        else if (rIdx == 2) d = val = (d - 1) & 0xFF;
                        else if (rIdx == 3) e = val = (e - 1) & 0xFF;
                        else if (rIdx == 4) h = val = (h - 1) & 0xFF;
                        else if (rIdx == 5) l = val = (l - 1) & 0xFF;
                        else a = val = (a - 1) & 0xFF;
                    }
                    f = (f & C01) | N02 | (((val & 0x0F) == 0x0F) ? H10 : 0) | (szpc[val] & ~P04);
                    if (val == 0x7F) f |= V04;
                    continue main_loop;
                }
                case 0x03: {
                    int v = ((b << 8) | c) + 1;
                    b = (v >> 8) & 0xFF;
                    c = v & 0xFF;
                    continue main_loop;
                }
                case 0x13: {
                    int v = ((d << 8) | e) + 1;
                    d = (v >> 8) & 0xFF;
                    e = v & 0xFF;
                    continue main_loop;
                }
                case 0x23: {
                    int v = ((h << 8) | l) + 1;
                    h = (v >> 8) & 0xFF;
                    l = v & 0xFF;
                    continue main_loop;
                }
                case 0x33:
                    sp = (sp + 1) & 0xFFFF;
                    continue main_loop;
                case 0x0B: {
                    int v = ((b << 8) | c) - 1;
                    b = (v >> 8) & 0xFF;
                    c = v & 0xFF;
                    continue main_loop;
                }
                case 0x1B: {
                    int v = ((d << 8) | e) - 1;
                    d = (v >> 8) & 0xFF;
                    e = v & 0xFF;
                    continue main_loop;
                }
                case 0x2B: {
                    int v = ((h << 8) | l) - 1;
                    h = (v >> 8) & 0xFF;
                    l = v & 0xFF;
                    continue main_loop;
                }
                case 0x3B:
                    sp = (sp - 1) & 0xFFFF;
                    continue main_loop;
                case 0xA6:
                case 0xE6:
                case 0xA0:
                case 0xA1:
                case 0xA2:
                case 0xA3:
                case 0xA4:
                case 0xA5:
                case 0xA7: { // AND
                    int val = (opcode == 0xA6) ? read8((h << 8) | l) : (opcode == 0xE6) ? read8(pc++) : (opcode == 0xA0) ? b : (opcode == 0xA1) ? c : (opcode == 0xA2) ? d : (opcode == 0xA3) ? e : (opcode == 0xA4) ? h : (opcode == 0xA5) ? l : a;
                    a &= val;
                    f = szpc[a] | H10;
                    continue main_loop;
                }
                case 0xB6:
                case 0xF6:
                case 0xB0:
                case 0xB1:
                case 0xB2:
                case 0xB3:
                case 0xB4:
                case 0xB5:
                case 0xB7: { // OR
                    int val = (opcode == 0xB6) ? read8((h << 8) | l) : (opcode == 0xF6) ? read8(pc++) : (opcode == 0xB0) ? b : (opcode == 0xB1) ? c : (opcode == 0xB2) ? d : (opcode == 0xB3) ? e : (opcode == 0xB4) ? h : (opcode == 0xB5) ? l : a;
                    a |= val;
                    f = szpc[a];
                    continue main_loop;
                }
                case 0xAE:
                case 0xEE:
                case 0xA8:
                case 0xA9:
                case 0xAA:
                case 0xAB:
                case 0xAC:
                case 0xAD:
                case 0xAF: { // XOR
                    int val = (opcode == 0xAE) ? read8((h << 8) | l) : (opcode == 0xEE) ? read8(pc++) : (opcode == 0xA8) ? b : (opcode == 0xA9) ? c : (opcode == 0xAA) ? d : (opcode == 0xAB) ? e : (opcode == 0xAC) ? h : (opcode == 0xAD) ? l : a;
                    a ^= val;
                    f = szpc[a];
                    continue main_loop;
                }
                case 0x70:
                case 0x71:
                case 0x72:
                case 0x73:
                case 0x74:
                case 0x75:
                case 0x77: { // LD (HL),r
                    int rIdx = opcode & 7;
                    int val = (rIdx == 0) ? b : (rIdx == 1) ? c : (rIdx == 2) ? d : (rIdx == 3) ? e : (rIdx == 4) ? h : (rIdx == 5) ? l : a;
                    write8((h << 8) | l, val);
                    continue main_loop;
                }
                case 0x40:
                case 0x41:
                case 0x42:
                case 0x43:
                case 0x44:
                case 0x45:
                case 0x47:
                case 0x48:
                case 0x49:
                case 0x4A:
                case 0x4B:
                case 0x4C:
                case 0x4D:
                case 0x4F:
                case 0x50:
                case 0x51:
                case 0x52:
                case 0x53:
                case 0x54:
                case 0x55:
                case 0x57:
                case 0x58:
                case 0x59:
                case 0x5A:
                case 0x5B:
                case 0x5C:
                case 0x5D:
                case 0x5F:
                case 0x60:
                case 0x61:
                case 0x62:
                case 0x63:
                case 0x64:
                case 0x65:
                case 0x67:
                case 0x68:
                case 0x69:
                case 0x6A:
                case 0x6B:
                case 0x6C:
                case 0x6D:
                case 0x6F:
                case 0x78:
                case 0x79:
                case 0x7A:
                case 0x7B:
                case 0x7C:
                case 0x7D:
                case 0x7F: { // LD r,r
                    int rIdx = opcode & 7;
                    int val = (rIdx == 0) ? b : (rIdx == 1) ? c : (rIdx == 2) ? d : (rIdx == 3) ? e : (rIdx == 4) ? h : (rIdx == 5) ? l : (rIdx == 7) ? a : read8((h << 8) | l);
                    int dIdx = (opcode >> 3) & 7;
                    if (dIdx == 0) b = val;
                    else if (dIdx == 1) c = val;
                    else if (dIdx == 2) d = val;
                    else if (dIdx == 3) e = val;
                    else if (dIdx == 4) h = val;
                    else if (dIdx == 5) l = val;
                    else if (dIdx == 7) a = val;
                    continue main_loop;
                }
                case 0x06:
                    b = read8(pc++);
                    continue main_loop;
                case 0x0E:
                    c = read8(pc++);
                    continue main_loop;
                case 0x16:
                    d = read8(pc++);
                    continue main_loop;
                case 0x1E:
                    e = read8(pc++);
                    continue main_loop;
                case 0x26:
                    h = read8(pc++);
                    continue main_loop;
                case 0x36:
                    write8((h << 8) | l, read8(pc++));
                    continue main_loop;
                case 0x46:
                    b = read8((h << 8) | l);
                    continue main_loop;
                case 0x4E:
                    c = read8((h << 8) | l);
                    continue main_loop;
                case 0x56:
                    d = read8((h << 8) | l);
                    continue main_loop;
                case 0x5E:
                    e = read8((h << 8) | l);
                    continue main_loop;
                case 0x66:
                    h = read8((h << 8) | l);
                    continue main_loop;
                case 0x6E:
                    l = read8((h << 8) | l);
                    continue main_loop;
                case 0x7E:
                    a = read8((h << 8) | l);
                    continue main_loop;
                case 0x01:
                    c = read8(pc++);
                    b = read8(pc++);
                    continue main_loop;
                case 0x11:
                    e = read8(pc++);
                    d = read8(pc++);
                    continue main_loop;
                case 0x21:
                    l = read8(pc++);
                    h = read8(pc++);
                    continue main_loop;
                case 0x31:
                    sp = read16(pc);
                    pc += 2;
                    continue main_loop;
                case 0x2A: {
                    int addr = read16(pc);
                    pc += 2;
                    l = read8(addr);
                    h = read8(addr + 1);
                    continue main_loop;
                }
                case 0x32: {
                    int addr = read16(pc);
                    pc += 2;
                    write8(addr, a);
                    continue main_loop;
                }
                case 0x22: {
                    int addr = read16(pc);
                    pc += 2;
                    writeDirect16(addr, ((h & 0xFF) << 8) | (l & 0xFF));
                    continue main_loop;
                }
                case 0x02:
                    write8((b << 8) | c, a);
                    continue main_loop;
                case 0x12:
                    write8((d << 8) | e, a);
                    continue main_loop;
                case 0x0A:
                    a = read8((b << 8) | c);
                    continue main_loop;
                case 0x1A:
                    a = read8((d << 8) | e);
                    continue main_loop;
                case 0xF9:
                    sp = (h << 8) | l;
                    continue main_loop;
                case 0x07: {
                    int t = a;
                    a = ((a << 1) | (a >> 7)) & 0xFF;
                    f = (f & (S80 | Z40 | P04)) | (a & (F20 | F08 | C01));
                    continue main_loop;
                }
                case 0x0F: {
                    f = (f & (S80 | Z40 | P04)) | (a & C01);
                    a = ((a >> 1) | (a << 7)) & 0xFF;
                    f |= a & (F20 | F08);
                    continue main_loop;
                }
                case 0x17: {
                    int t = a;
                    a = ((a << 1) | (f & C01)) & 0xFF;
                    f = (f & (S80 | Z40 | P04)) | (a & (F20 | F08)) | (t >> 7);
                    continue main_loop;
                }
                case 0x1F: {
                    int t = a;
                    a = ((a >> 1) | (f << 7)) & 0xFF;
                    f = (f & (S80 | Z40 | P04)) | (a & (F20 | F08)) | (t & C01);
                    continue main_loop;
                }
                case 0x2F:
                    a = (~a) & 0xFF;
                    f = (f & (S80 | Z40 | P04 | C01)) | (a & (F20 | F08)) | H10 | N02;
                    continue main_loop;
                case 0x3F:
                    f = ((f & (S80 | Z40 | P04 | C01)) ^ C01) | ((f << 4) & H10) | (a & (F20 | F08));
                    continue main_loop;
                case 0x37:
                    f = (f & (S80 | Z40 | P04)) | C01 | (a & (F20 | F08));
                    continue main_loop;
                case 0xDB:
                    a = in(s_time + endTime, read8(pc++) | ((a & 0xFF) << 8));
                    continue main_loop;
                case 0xE3: {
                    int t = read16(sp);
                    writeDirect16(sp, ((h & 0xFF) << 8) | (l & 0xFF));
                    l = t & 0xFF;
                    h = (t >> 8) & 0xFF;
                    continue main_loop;
                }
                case 0xEB: {
                    int t = h;
                    h = d;
                    d = t;
                    t = l;
                    l = e;
                    e = t;
                    continue main_loop;
                }
                case 0xD9: {
                    int t;
                    t = r.altB;
                    r.altB = b;
                    b = t;
                    t = r.altC;
                    r.altC = c;
                    c = t;
                    t = r.altD;
                    r.altD = d;
                    d = t;
                    t = r.altE;
                    r.altE = e;
                    e = t;
                    t = r.altH;
                    r.altH = h;
                    h = t;
                    t = r.altL;
                    r.altL = l;
                    l = t;
                    continue main_loop;
                }
                case 0xF3:
                    r.iff1 = 0;
                    r.iff2 = 0;
                    continue main_loop;
                case 0xFB:
                    r.iff1 = 1;
                    r.iff2 = 1;
                    continue main_loop;
                case 0x76:
                    pc--;
                    s_time &= 3;
                    break main_loop;

                case 0xCB: {
                    int op2 = read8(pc++);
                    int val, res;
                    int addr = -1;
                    if ((op2 & 7) == 6) {
                        addr = (h << 8) | l;
                        val = read8(addr);
                        s_time += 7;
                    } else {
                        int rIdx = op2 & 7;
                        val = (rIdx == 0) ? b : (rIdx == 1) ? c : (rIdx == 2) ? d : (rIdx == 3) ? e : (rIdx == 4) ? h : (rIdx == 5) ? l : a;
                    }
                    switch (op2 >> 3) {
                        case 0:
                            res = ((val << 1) | (val >> 7)) & 0xFF;
                            f = szpc[res] | (res & C01);
                            break;
                        case 1:
                            res = ((val >> 1) | (val << 7)) & 0xFF;
                            f = szpc[res] | (val & C01);
                            break;
                        case 2:
                            res = ((val << 1) | (f & C01)) & 0xFF;
                            f = szpc[res] | (val >> 7);
                            break;
                        case 3:
                            res = ((val >> 1) | (f << 7)) & 0xFF;
                            f = szpc[res] | (val & C01);
                            break;
                        case 4:
                            res = (val << 1) & 0xFF;
                            f = szpc[res] | (val >> 7);
                            break;
                        case 5:
                            res = (val >> 1) | (val & 0x80);
                            f = szpc[res] | (val & C01);
                            break;
                        case 6:
                            res = ((val << 1) | 1) & 0xFF;
                            f = szpc[res] | (val >> 7);
                            break;
                        case 7:
                            res = (val >> 1) & 0xFF;
                            f = szpc[res] | (val & C01);
                            break;
                        default: {
                            if ((op2 & 0xC0) == 0x40) {
                                int m = val & (1 << ((op2 >> 3) & 7));
                                f = (f & C01) | (((op2 & 7) == 6) ? 0 : (val & (F20 | F08))) | (m & S80) | H10 | ((m == 0) ? Z40 | P04 : 0);
                                continue main_loop;
                            }
                            res = (op2 & 0x40) != 0 ? val | (1 << ((op2 >> 3) & 7)) : val & ~(1 << ((op2 >> 3) & 7));
                            break;
                        }
                    }
                    if (addr != -1) write8(addr, res);
                    else {
                        int dIdx = op2 & 7;
                        if (dIdx == 0) b = res;
                        else if (dIdx == 1) c = res;
                        else if (dIdx == 2) d = res;
                        else if (dIdx == 3) e = res;
                        else if (dIdx == 4) h = res;
                        else if (dIdx == 5) l = res;
                        else a = res;
                    }
                    continue main_loop;
                }

                case 0xED: {
                    int op2 = read8(pc++);
                    s_time += (edDdTiming[op2] >> 4) & 0x0F;
                    switch (op2) {
                        case 0x42:
                        case 0x52:
                        case 0x62:
                        case 0x72:
                        case 0x4A:
                        case 0x5A:
                        case 0x6A:
                        case 0x7A: {
                            int val = (op2 == 0x72 || op2 == 0x7A) ? sp : (op2 == 0x42 || op2 == 0x4A) ? (b << 8) | c : (op2 == 0x52 || op2 == 0x5A) ? (d << 8) | e : (h << 8) | l;
                            int hl = (h << 8) | l;
                            int carry = (f & C01);
                            int f_base = (~op2 >> 2) & N02;
                            int res = val + carry;
                            int sum = hl + (f_base != 0 ? -res : res);
                            int lookup = val ^ hl ^ sum;
                            f = f_base | (sum >> 16 & C01) | (lookup >> 8 & H10) | (sum >> 8 & (S80 | F20 | F08)) | ((lookup - -0x8000) >> 14 & V04);
                            if ((sum & 0xFFFF) == 0) f |= Z40;
                            h = (sum >> 8) & 0xFF;
                            l = sum & 0xFF;
                            continue main_loop;
                        }
                        case 0x40:
                        case 0x48:
                        case 0x50:
                        case 0x58:
                        case 0x60:
                        case 0x68:
                        case 0x70:
                        case 0x78: {
                            int val = in(s_time + endTime, ((b & 0xFF) << 8) | (c & 0xFF));
                            int dIdx = (op2 >> 3) & 7;
                            if (dIdx == 0) b = val;
                            else if (dIdx == 1) c = val;
                            else if (dIdx == 2) d = val;
                            else if (dIdx == 3) e = val;
                            else if (dIdx == 4) h = val;
                            else if (dIdx == 5) l = val;
                            else if (dIdx == 7) a = val;
                            f = (f & C01) | szpc[val];
                            continue main_loop;
                        }
                        case 0x41:
                        case 0x49:
                        case 0x51:
                        case 0x59:
                        case 0x61:
                        case 0x69:
                        case 0x71:
                        case 0x79: {
                            int dIdx = (op2 >> 3) & 7;
                            int val = (dIdx == 0) ? b : (dIdx == 1) ? c : (dIdx == 2) ? d : (dIdx == 3) ? e : (dIdx == 4) ? h : (dIdx == 5) ? l : (dIdx == 7) ? a : 0;
                            out(s_time + endTime, ((b & 0xFF) << 8) | (c & 0xFF), val);
                            continue main_loop;
                        }
                        case 0x43:
                        case 0x53:
                        case 0x73: {
                            int val = (op2 == 0x73) ? sp : (op2 == 0x43) ? (((b & 0xFF) << 8) | (c & 0xFF)) : (((d & 0xFF) << 8) | (e & 0xFF));
                            int addr = read16(pc);
                            pc += 2;
                            writeDirect16(addr, val);
                            continue main_loop;
                        }
                        case 0x4B:
                        case 0x5B:
                        case 0x7B: {
                            int addr = read16(pc);
                            pc += 2;
                            int val = read16(addr);
                            if (op2 == 0x4B) {
                                b = val >> 8;
                                c = val & 0xFF;
                            } else if (op2 == 0x5B) {
                                d = val >> 8;
                                e = val & 0xFF;
                            } else sp = val;
                            continue main_loop;
                        }
                        case 0x67: {
                            int hl = (h << 8) | l;
                            int val = read8(hl);
                            write8(hl, (a << 4) | (val >> 4));
                            a = (a & 0xF0) | (val & 0x0F);
                            f = (f & C01) | szpc[a];
                            continue main_loop;
                        }
                        case 0x6F: {
                            int hl = (h << 8) | l;
                            int val = read8(hl);
                            write8(hl, (val << 4) | (a & 0x0F));
                            a = (a & 0xF0) | (val >> 4);
                            f = (f & C01) | szpc[a];
                            continue main_loop;
                        }
                        case 0xA0:
                        case 0xA1:
                        case 0xA8:
                        case 0xA9:
                        case 0xB0:
                        case 0xB1:
                        case 0xB8:
                        case 0xB9: {
                            int inc = (op2 & 0x08) != 0 ? -1 : 1;
                            int hl = (h << 8) | l, de = (d << 8) | e, bc = (b << 8) | c;
                            if ((op2 & 1) == 0) {
                                int val = read8(hl);
                                write8(de, val);
                                hl = (hl + inc) & 0xFFFF;
                                de = (de + inc) & 0xFFFF;
                                bc = (bc - 1) & 0xFFFF;
                                int t = val + a;
                                f = (f & (S80 | Z40 | C01)) | (t & F08) | ((t << 4) & F20);
                            } else {
                                int val = read8(hl);
                                hl = (hl + inc) & 0xFFFF;
                                bc = (bc - 1) & 0xFFFF;
                                int res = a - val;
                                f = (f & C01) | N02 | ((((val ^ a) & H10) ^ res) & (S80 | H10));
                                if ((res & 0xFF) == 0) f |= Z40;
                                res -= (f & H10) >> 4;
                                f |= (res & F08) | ((res << 4) & F20);
                            }
                            h = hl >> 8;
                            l = hl & 0xFF;
                            d = de >> 8;
                            e = de & 0xFF;
                            b = bc >> 8;
                            c = bc & 0xFF;
                            if (bc != 0) {
                                f |= V04;
                                if ((op2 & 0x10) != 0 && ((op2 & 1) == 0 || (f & Z40) == 0)) {
                                    pc -= 2;
                                    s_time += 5;
                                }
                            }
                            continue main_loop;
                        }
                        case 0xA2:
                        case 0xA3:
                        case 0xAA:
                        case 0xAB:
                        case 0xB2:
                        case 0xB3:
                        case 0xBA:
                        case 0xBB: {
                            int inc = (op2 & 0x08) != 0 ? -1 : 1;
                            int hl = (h << 8) | l;
                            int val;
                            if ((op2 & 1) != 0) { // OUTI/OTIR/OUTD/OTDR
                                val = read8(hl);
                                out(s_time + endTime, (b << 8) | c, val);
                            } else { // INI/INIR/IND/INDR
                                val = in(s_time + endTime, (b << 8) | c);
                                write8(hl, val);
                            }
                            hl = (hl + inc) & 0xFFFF;
                            h = hl >> 8;
                            l = hl & 0xFF;
                            b = (b - 1) & 0xFF;
                            f = ((val >> 6) & N02) | (szpc[b] & ~P04); // SZ28
                            if (b != 0) {
                                if ((op2 & 0x10) != 0) { // Repeat
                                    pc -= 2;
                                    s_time += 5;
                                }
                            } else {
                                f |= Z40; // SZ28 includes Z if b==0
                            }
                            continue main_loop;
                        }
                        case 0x47:
                            r.i = a;
                            continue main_loop;
                        case 0x57:
                            a = r.i;
                            f = (f & C01) | (szpc[a] & ~0x04) | (r.iff2 << 2);
                            continue main_loop;
                        case 0x4F:
                            r.r = a;
                            warning = true;
                            continue main_loop;
                        case 0x5F:
                            a = r.r;
                            f = (f & C01) | (szpc[a] & ~0x04) | (r.iff2 << 2);
                            warning = true;
                            continue main_loop;
                        case 0x45:
                        case 0x4D:
                        case 0x55:
                        case 0x5D:
                        case 0x65:
                        case 0x6D:
                        case 0x75:
                        case 0x7D:
                            r.iff1 = r.iff2;
                            pc = read16(sp);
                            sp = (sp + 2) & 0xFFFF;
                            continue main_loop;
                        case 0x46:
                        case 0x4E:
                        case 0x66:
                        case 0x6E:
                            r.im = 0;
                            continue main_loop;
                        case 0x56:
                        case 0x76:
                            r.im = 1;
                            continue main_loop;
                        case 0x5E:
                        case 0x7E:
                            r.im = 2;
                            continue main_loop;
                        case 0x44: case 0x4C: case 0x54: case 0x5C:
                        case 0x64: case 0x6C: case 0x74: case 0x7C: { // NEG
                            int val = a;
                            a = 0;
                            f &= ~C01;
                            int res = val; // val + 0 (carry cleared)
                            int lookup = val; // val ^ a(=0)
                            res = -res; // negate for SUB mode
                            lookup ^= res;
                            f = N02 | (lookup & H10) | (((lookup - -0x80) >> 6) & V04) | (szpc[res & 0x1FF] & ~P04);
                            a = res & 0xFF;
                            continue main_loop;
                        }
                        default:
                            warning = true;
                            continue main_loop;
                    }
                }

                case 0xDD:
                case 0xFD: {
                    int ixy = (opcode == 0xDD) ? ix : iy;
                    int op2 = read8(pc++);
                    s_time += edDdTiming[op2] & 0x0F;
                    switch (op2) {
                        case 0x09:
                        case 0x19:
                        case 0x29:
                        case 0x39: {
                            int val = (op2 == 0x09) ? (b << 8) | c : (op2 == 0x19) ? (d << 8) | e : (op2 == 0x29) ? ixy : sp;
                            int sum = ixy + val;
                            int lookup = val ^ ixy ^ sum;
                            ixy = sum & 0xFFFF;
                            f = (f & (S80 | Z40 | V04)) | (sum >> 16 & C01) | (sum >> 8 & (F20 | F08)) | (lookup >> 8 & H10);
                            break;
                        }
                        case 0x21:
                            ixy = read16(pc);
                            pc += 2;
                            break;
                        case 0x22:
                            writeDirect16(read16(pc), ixy & 0xFFFF);
                            pc += 2;
                            break;
                        case 0x2A:
                            ixy = read16(read16(pc));
                            pc += 2;
                            break;
                        case 0x23:
                            ixy = (ixy + 1) & 0xFFFF;
                            break;
                        case 0x2B:
                            ixy = (ixy - 1) & 0xFFFF;
                            break;
                        case 0xE1:
                            ixy = read16(sp);
                            sp = (sp + 2) & 0xFFFF;
                            break;
                        case 0xE5:
                            sp = (sp - 2) & 0xFFFF;
                            writeDirect16(sp, ixy);
                            break;
                        case 0xF9:
                            sp = ixy;
                            break;
                        case 0xE3: {
                            int t = read16(sp);
                            writeDirect16(sp, ixy);
                            ixy = t;
                            break;
                        }
                        case 0xE9:
                            pc = ixy;
                            break;
                        case 0xCB: {
                            int off = (byte) read8(pc++);
                            int addr = (ixy + off) & 0xFFFF;
                            int op3 = read8(pc++);
                            int val = read8(addr), res = 0;
                            switch (op3 >> 3) {
                                case 0:
                                    res = ((val << 1) | (val >> 7)) & 0xFF;
                                    f = szpc[res] | (res & C01);
                                    break;
                                case 1:
                                    res = ((val >> 1) | (val << 7)) & 0xFF;
                                    f = szpc[res] | (val & C01);
                                    break;
                                case 2:
                                    res = ((val << 1) | (f & C01)) & 0xFF;
                                    f = szpc[res] | (val >> 7);
                                    break;
                                case 3:
                                    res = ((val >> 1) | (f << 7)) & 0xFF;
                                    f = szpc[res] | (val & C01);
                                    break;
                                case 4:
                                    res = (val << 1) & 0xFF;
                                    f = szpc[res] | (val >> 7);
                                    break;
                                case 5:
                                    res = (val >> 1) | (val & 0x80);
                                    f = szpc[res] | (val & C01);
                                    break;
                                case 6:
                                    res = ((val << 1) | 1) & 0xFF;
                                    f = szpc[res] | (val >> 7);
                                    break;
                                case 7:
                                    res = (val >> 1) & 0xFF;
                                    f = szpc[res] | (val & C01);
                                    break;
                                default: {
                                    if ((op3 & 0xC0) == 0x40) {
                                        int m = val & (1 << ((op3 >> 3) & 7));
                                        f = (f & C01) | H10 | (m & S80) | ((m == 0) ? Z40 | P04 : 0);
                                    } else {
                                        res = (op3 & 0x40) != 0 ? val | (1 << ((op3 >> 3) & 7)) : val & ~(1 << ((op3 >> 3) & 7));
                                        write8(addr, res);
                                    }
                                    continue main_loop;
                                }
                            }
                            write8(addr, res);
                            continue main_loop;
                        }
                        // INC/DEC IXH/IXL (undocumented)
                        case 0x24: { // INC IXH
                            ixy = (ixy + 0x100) & 0xFFFF;
                            int data = ixy >> 8;
                            f = (f & C01) | (((data & 0x0F) == 0) ? H10 : 0) | (szpc[data] & ~P04);
                            if (data == 0x80) f |= V04;
                            break;
                        }
                        case 0x2C: { // INC IXL
                            int data = ((ixy & 0xFF) + 1) & 0xFF;
                            ixy = (ixy & 0xFF00) | data;
                            f = (f & C01) | (((data & 0x0F) == 0) ? H10 : 0) | (szpc[data] & ~P04);
                            if (data == 0x80) f |= V04;
                            break;
                        }
                        case 0x25: { // DEC IXH
                            ixy = (ixy - 0x100) & 0xFFFF;
                            int data = ixy >> 8;
                            f = (f & C01) | N02 | (((data & 0x0F) == 0x0F) ? H10 : 0) | (szpc[data] & ~P04);
                            if (data == 0x7F) f |= V04;
                            break;
                        }
                        case 0x2D: { // DEC IXL
                            int data = ((ixy & 0xFF) - 1) & 0xFF;
                            ixy = (ixy & 0xFF00) | data;
                            f = (f & C01) | N02 | (((data & 0x0F) == 0x0F) ? H10 : 0) | (szpc[data] & ~P04);
                            if (data == 0x7F) f |= V04;
                            break;
                        }
                        // LD IXH/IXL,imm (undocumented)
                        case 0x26: { // LD IXH,imm
                            ixy = (ixy & 0xFF) | (read8(pc++) << 8);
                            break;
                        }
                        case 0x2E: { // LD IXL,imm
                            ixy = (ixy & 0xFF00) | read8(pc++);
                            break;
                        }
                        // ADD/ADC/SUB/SBC A,IXH/IXL (undocumented)
                        case 0x84: // ADD A,IXH
                        case 0x94: // SUB IXH
                            f &= ~C01;
                            // fall through
                        case 0x8C: // ADC A,IXH
                        case 0x9C: { // SBC A,IXH
                            int val = ixy >> 8;
                            int res = val + (f & C01);
                            int lookup = val ^ a;
                            int f_base = (op2 >> 3) & N02;
                            if (f_base != 0) res = -res;
                            res += a;
                            lookup ^= res;
                            f = f_base | (lookup & H10) | (((lookup - -0x80) >> 6) & V04) | (szpc[res & 0x1FF] & ~P04);
                            a = res & 0xFF;
                            break;
                        }
                        case 0x85: // ADD A,IXL
                        case 0x95: // SUB IXL
                            f &= ~C01;
                            // fall through
                        case 0x8D: // ADC A,IXL
                        case 0x9D: { // SBC A,IXL
                            int val = ixy & 0xFF;
                            int res = val + (f & C01);
                            int lookup = val ^ a;
                            int f_base = (op2 >> 3) & N02;
                            if (f_base != 0) res = -res;
                            res += a;
                            lookup ^= res;
                            f = f_base | (lookup & H10) | (((lookup - -0x80) >> 6) & V04) | (szpc[res & 0x1FF] & ~P04);
                            a = res & 0xFF;
                            break;
                        }
                        // AND/OR/XOR/CP IXH/IXL (undocumented)
                        case 0xA4: { // AND IXH
                            a &= ixy >> 8;
                            f = H10 | szpc[a];
                            break;
                        }
                        case 0xA5: { // AND IXL
                            a &= ixy & 0xFF;
                            f = H10 | szpc[a];
                            break;
                        }
                        case 0xB4: { // OR IXH
                            a |= ixy >> 8;
                            f = szpc[a];
                            break;
                        }
                        case 0xB5: { // OR IXL
                            a |= ixy & 0xFF;
                            f = szpc[a];
                            break;
                        }
                        case 0xAC: { // XOR IXH
                            a ^= ixy >> 8;
                            f = szpc[a];
                            break;
                        }
                        case 0xAD: { // XOR IXL
                            a ^= ixy & 0xFF;
                            f = szpc[a];
                            break;
                        }
                        case 0xBC: { // CP IXH
                            int val = ixy >> 8;
                            int res = a - val;
                            f = N02 | (val & (F20 | F08)) | ((res >> 8) & C01);
                            int lookup = val ^ a;
                            f |= (((res ^ a) & lookup) >> 5 & V04) | (((lookup & H10) ^ res) & (S80 | H10));
                            if ((res & 0xFF) == 0) f |= Z40;
                            break;
                        }
                        case 0xBD: { // CP IXL
                            int val = ixy & 0xFF;
                            int res = a - val;
                            f = N02 | (val & (F20 | F08)) | ((res >> 8) & C01);
                            int lookup = val ^ a;
                            f |= (((res ^ a) & lookup) >> 5 & V04) | (((lookup & H10) ^ res) & (S80 | H10));
                            if ((res & 0xFF) == 0) f |= Z40;
                            break;
                        }
                        // LD HXY,r / LD LXY,r / LD r,HXY / LD r,LXY / LD HXY,LXY / LD LXY,HXY (undocumented)
                        case 0x60: case 0x61: case 0x62: case 0x63: case 0x67: { // LD HXY,r
                            int v = (op2 == 0x60) ? b : (op2 == 0x61) ? c : (op2 == 0x62) ? d : (op2 == 0x63) ? e : a;
                            ixy = (ixy & 0xFF) | (v << 8);
                            break;
                        }
                        case 0x65: { // LD HXY,LXY
                            ixy = (ixy & 0xFF) | ((ixy & 0xFF) << 8);
                            break;
                        }
                        case 0x68: case 0x69: case 0x6A: case 0x6B: case 0x6F: { // LD LXY,r
                            int v = (op2 == 0x68) ? b : (op2 == 0x69) ? c : (op2 == 0x6A) ? d : (op2 == 0x6B) ? e : a;
                            ixy = (ixy & 0xFF00) | v;
                            break;
                        }
                        case 0x6C: { // LD LXY,HXY
                            ixy = (ixy & 0xFF00) | (ixy >> 8);
                            break;
                        }
                        case 0x64: // LD HXY,HXY (nop)
                        case 0x6D: // LD LXY,LXY (nop)
                            break;
                        case 0x44: case 0x4C: case 0x54: case 0x5C: case 0x7C: { // LD r,HXY
                            int v = ixy >> 8;
                            int dst = (op2 >> 3) & 7;
                            if (dst == 0) b = v;
                            else if (dst == 1) c = v;
                            else if (dst == 2) d = v;
                            else if (dst == 3) e = v;
                            else a = v;
                            break;
                        }
                        case 0x45: case 0x4D: case 0x55: case 0x5D: case 0x7D: { // LD r,LXY
                            int v = ixy & 0xFF;
                            int dst = (op2 >> 3) & 7;
                            if (dst == 0) b = v;
                            else if (dst == 1) c = v;
                            else if (dst == 2) d = v;
                            else if (dst == 3) e = v;
                            else a = v;
                            break;
                        }
                        default: {
                            // Check if this opcode uses (IX+d) addressing
                            // These are opcodes where register field 6 ((HL)) appears as src or dst
                            boolean isIxDisp = op2 == 0x34 || op2 == 0x35 || op2 == 0x36 // INC/DEC/LD (IX+d)
                                    || (op2 & 0xC7) == 0x46 // LD r,(IX+d): 0x46,0x4E,0x56,0x5E,0x66,0x6E,0x7E
                                    || (op2 & 0xF8) == 0x70 // LD (IX+d),r: 0x70-0x77
                                    || (op2 & 0xC7) == 0x86 // ADD/ADC/SUB/SBC A,(IX+d)
                                    || (op2 & 0xC7) == 0x06 && ((op2 >> 3) & 7) == 6; // only 0x36 (already covered above)
                            if (isIxDisp) {
                                int off = (byte) read8(pc++);
                                int addr = (ixy + off) & 0xFFFF;
                                if (op2 == 0x34) {
                                    // INC (IX+d)
                                    int v = (read8(addr) + 1) & 0xFF;
                                    write8(addr, v);
                                    f = (f & C01) | (((v & 0x0F) == 0) ? H10 : 0) | (szpc[v] & ~P04);
                                    if (v == 0x80) f |= V04;
                                } else if (op2 == 0x35) {
                                    // DEC (IX+d)
                                    int v = (read8(addr) - 1) & 0xFF;
                                    write8(addr, v);
                                    f = (f & C01) | N02 | (((v & 0x0F) == 0x0F) ? H10 : 0) | (szpc[v] & ~P04);
                                    if (v == 0x7F) f |= V04;
                                } else if (op2 == 0x36) {
                                    // LD (IX+d),n
                                    write8(addr, read8(pc++));
                                } else if ((op2 & 0xF8) == 0x70) {
                                    // LD (IX+d),r
                                    int rIdx = op2 & 7;
                                    int v = (rIdx == 0) ? b : (rIdx == 1) ? c : (rIdx == 2) ? d : (rIdx == 3) ? e : (rIdx == 4) ? h : (rIdx == 5) ? l : a;
                                    write8(addr, v);
                                } else if ((op2 & 0xC7) == 0x46) {
                                    // LD r,(IX+d)
                                    int dst = (op2 >> 3) & 7;
                                    int v = read8(addr);
                                    if (dst == 0) b = v;
                                    else if (dst == 1) c = v;
                                    else if (dst == 2) d = v;
                                    else if (dst == 3) e = v;
                                    else if (dst == 4) h = v;
                                    else if (dst == 5) l = v;
                                    else a = v;
                                } else if ((op2 & 0xC7) == 0x86) {
                                    // Arithmetic/logic with (IX+d): ADD/ADC/SUB/SBC/AND/XOR/OR/CP
                                    int val = read8(addr);
                                    int group = (op2 >> 3) & 7;
                                    switch (group) {
                                        case 0: // ADD A,(IX+d)
                                        case 1: // ADC A,(IX+d)
                                        case 2: // SUB (IX+d)
                                        case 3: { // SBC A,(IX+d)
                                            if ((op2 & 0x08) == 0) f &= ~C01;
                                            int res = val + (f & C01);
                                            int lookup = val ^ a;
                                            int f_base = (op2 >> 3) & N02;
                                            if (f_base != 0) res = -res;
                                            res += a;
                                            lookup ^= res;
                                            f = f_base | (lookup & H10) | (((lookup - -0x80) >> 6) & V04) | (szpc[res & 0x1FF] & ~P04);
                                            a = res & 0xFF;
                                            break;
                                        }
                                        case 4: // AND (IX+d)
                                            a &= val;
                                            f = H10 | szpc[a];
                                            break;
                                        case 5: // XOR (IX+d)
                                            a ^= val;
                                            f = szpc[a];
                                            break;
                                        case 6: // OR (IX+d)
                                            a |= val;
                                            f = szpc[a];
                                            break;
                                        case 7: { // CP (IX+d)
                                            int res = a - val;
                                            f = N02 | (val & (F20 | F08)) | ((res >> 8) & C01);
                                            int lookup = val ^ a;
                                            f |= (((res ^ a) & lookup) >> 5 & V04) | (((lookup & H10) ^ res) & (S80 | H10));
                                            if ((res & 0xFF) == 0) f |= Z40;
                                            break;
                                        }
                                    }
                                }
                                continue main_loop;
                            }
                            // IXH/IXL register operations (no displacement byte)
                            int dst = (op2 >> 3) & 7, src = op2 & 7;
                            if (dst == 4 || dst == 5) {
                                int v;
                                if (src == 4) v = ixy >> 8;
                                else if (src == 5) v = ixy & 0xFF;
                                else if (src == 0) v = b;
                                else if (src == 1) v = c;
                                else if (src == 2) v = d;
                                else if (src == 3) v = e;
                                else if (src == 7) v = a;
                                else {
                                    warning = true;
                                    pc--;
                                    continue main_loop;
                                }
                                if (dst == 4) ixy = (ixy & 0xFF) | (v << 8);
                                else ixy = (ixy & 0xFF00) | v;
                            } else if (src == 4 || src == 5) {
                                int v = (src == 4) ? (ixy >> 8) : (ixy & 0xFF);
                                if (dst == 0) b = v;
                                else if (dst == 1) c = v;
                                else if (dst == 2) d = v;
                                else if (dst == 3) e = v;
                                else if (dst == 7) a = v;
                                else {
                                    warning = true;
                                    pc--;
                                    continue main_loop;
                                }
                            } else {
                                warning = true;
                                pc--;
                            }
                        }
                    }
                    if (opcode == 0xDD) ix = ixy;
                    else iy = ixy;
                    continue main_loop;
                }
                default:
                    pc--;
                    warning = true;
                    break main_loop;
            }
        }
        r.a = a;
        r.f = f;
        r.b = b;
        r.c = c;
        r.d = d;
        r.e = e;
        r.h = h;
        r.l = l;
        r.pc = pc;
        r.sp = sp;
        r.ix = ix;
        r.iy = iy;
        currentTime = s_time + endTime - baseTime;
        return warning;
    }
}
