/*
 * Game Music Emulators
 *
 * copyright (C) 2003-2009 Shay Green.
 */

package libgme.kss;

import java.io.IOException;
import java.util.Arrays;

import libgme.ClassicEmu;
import libgme.vgm.SmsApu;
import vavi.util.ByteUtil;


/**
 * MSX computer KSS music file emulator
 */
public class KssEmu extends ClassicEmu {

    private static final int CLOCK_RATE = 3579545;
    private static final int MEM_SIZE = 0x10000;
    private static final int HEADER_SIZE = 0x10;
    private static final int EXT_HEADER_SIZE = 0x10;

    public static class Header {

        public byte[] tag = new byte[4];
        public int loadAddr, loadSize, initAddr, playAddr;
        public int firstBank, bankMode, extraHeader, deviceFlags;
        public int dataSize, firstTrack, lastTrack;
        public int psgVol, sccVol, msxMusicVol, msxAudioVol;

        public void read(byte[] in) {
            System.arraycopy(in, 0,tag, 0, 4);
            loadAddr = ByteUtil.readLeShort(in, 4) & 0xffff;
            loadSize = ByteUtil.readLeShort(in, 6) & 0xffff;
            initAddr = ByteUtil.readLeShort(in, 8) & 0xffff;
            playAddr = ByteUtil.readLeShort(in, 10) & 0xffff;
            firstBank = in[12] & 0xff;
            bankMode = in[13] & 0xff;
            extraHeader = in[14] & 0xff;
            deviceFlags = in[15] & 0xff;

            if (tag[3] == 'X' && extraHeader >= 0x10) {
                dataSize = ByteUtil.readLeInt(in, 16);
                firstTrack = ByteUtil.readLeShort(in, 24) & 0xffff;
                lastTrack = ByteUtil.readLeShort(in, 26) & 0xffff;
                psgVol = in[28] & 0xff;
                sccVol = in[29] & 0xff;
                msxMusicVol = in[30] & 0xff;
                msxAudioVol = in[31] & 0xff;
            }
        }
    }

    private final KssCpu cpu = new KssCpu() {
        @Override
        public void out(int time, int addr, int data) {
            KssEmu.this.cpuOut(time, addr, data);
        }

        @Override
        public int in(int time, int addr) {
            return KssEmu.this.cpuIn(time, addr);
        }

        @Override
        public void writeMem(int addr, int data) {
            KssEmu.this.cpuWrite(addr, data);
        }
    };

    private static final String MAGIC = "KSCC";
    private final Header header = new Header();
    private byte[] romData;
    private final byte[] ram = new byte[MEM_SIZE + 0x100];
    private final AyApu ay = new AyApu();
    private final KssSccApu scc = new KssSccApu();
    private SmsApu sn;
    private final byte[] unmappedRead = new byte[MEM_SIZE];
    private final byte[] unmappedWrite = new byte[MEM_SIZE];

    private boolean sccAccessed;
    private boolean gainUpdated;
    private int sccEnabled;
    private int bankCount;
    private int playPeriod;
    private int nextPlay;
    private int ayLatch;

    public KssEmu() {
        this.gain = 1.0;
        Arrays.fill(unmappedRead, (byte) 0xFF);
    }

    public String trackInfo(int track) {
        String system = "MSX";
        if ((header.deviceFlags & 0x02) != 0) {
            system = (header.deviceFlags & 0x04) != 0 ? "Game Gear" : "Sega Master System";
        }
        return system;
    }

    @Override
    public void startTrack(int track) {
        super.startTrack(track);
        Arrays.fill(ram, 0, 0x4000, (byte) 0xC9);
        Arrays.fill(ram, 0x4000, MEM_SIZE, (byte) 0);

        byte[] bios = {(byte) 0xD3, (byte) 0xA0, (byte) 0xF5, 0x7B, (byte) 0xD3, (byte) 0xA1, (byte) 0xF1, (byte) 0xC9, (byte) 0xD3, (byte) 0xA0, (byte) 0xDB, (byte) 0xA2, (byte) 0xC9};
        System.arraycopy(bios, 0, ram, 0x01, bios.length);
        byte[] vectors = {(byte) 0xC3, 0x01, 0x00, (byte) 0xC3, 0x09, 0x00};
        System.arraycopy(vectors, 0, ram, 0x93, vectors.length);

        int headerSize = 0x10 + header.extraHeader;
        int loadSize = Math.min(header.loadSize, romData.length - headerSize);
        System.arraycopy(romData, headerSize, ram, header.loadAddr, loadSize);

        int bankSize = (16 * 1024) >> ((header.bankMode >> 7) & 1);
        int maxBanks = (romData.length - loadSize - headerSize + bankSize - 1) / bankSize;
        bankCount = header.bankMode & 0x7F;
        if (bankCount > maxBanks) {
            bankCount = maxBanks;
        }

        ram[0xFFFF] = (byte) 0xFF;
        cpu.reset(unmappedWrite, unmappedRead);
        
        cpu.mapMem(0, MEM_SIZE, ram, 0, ram, 0);

        ay.reset();
        scc.reset();
        if (sn != null) sn.reset(0, 0);

        cpu.r.sp = 0xF380;
        ram[--cpu.r.sp] = (byte) 0xFF;
        ram[--cpu.r.sp] = (byte) 0xFF;
        cpu.r.a = track;
        cpu.r.pc = header.initAddr;

        setTempo(1.0);
        nextPlay = playPeriod;
        sccAccessed = false;
        gainUpdated = false;
        updateGain();
        ayLatch = 0;
    }

    @Override
    protected int parseHeader(byte[] in) {
        header.read(in);
        String tag = new String(header.tag);
        if (!tag.equals(MAGIC) && !tag.equals("KSSX")) return 0;

        // Match C++: scc_enabled = 0xC000; if (device_flags & 0x04) scc_enabled = 0;
        sccEnabled = 0xC000;
        if ((header.deviceFlags & 0x04) != 0)
            sccEnabled = 0;
        if ((header.deviceFlags & 0x02) != 0) sn = new SmsApu();

        setClockRate(CLOCK_RATE);

        ay.output(buf.center());
        scc.output(buf.center());
        if (sn != null) sn.setOutput(buf.center(), buf.left(), buf.right());

        this.romData = in;

        if (tag.equals("KSSX")) {
            return header.lastTrack - header.firstTrack + 1;
        } else {
            return 256;
        }
    }

    @Override
    public String getMagic() {
        return MAGIC;
    }

    @Override
    public boolean isSupported(java.io.InputStream in) throws IOException {
        byte[] buf = new byte[4];
        int read = 0;
        int offset = 0;
        while (offset < 4 && (read = in.read(buf, offset, 4 - offset)) != -1) {
            offset += read;
        }
        if (offset < 4) return false;
        String tag = new String(buf, 0, 4);
        return tag.equals(MAGIC) || tag.equals("KSSX");
    }

    @Override
    public boolean isSupportedByName(String name) {
        return name.toLowerCase().endsWith(".kss");
    }

    @Override
    protected void mixSamples(byte[] out, int offset, int count) {
    }

    private double gain;

    private void updateGain() {
        double g = gain * 1.4;
        if (sccAccessed) g *= 1.5;
        ay.volume(g);
        scc.volume(g);
        if (sn != null) sn.volume(g);
    }

    private void setBank(int logical, int physical) {
        int bankSize = (16 * 1024) >> ((header.bankMode >> 7) & 1);
        int addr = 0x8000;
        if (logical != 0 && bankSize == 8192)
            addr = 0xA000;

        physical -= header.firstBank;
        if (physical >= 0 && physical < bankCount) {
            // Valid bank: read from ROM, writes to unmappedWrite (matching C++)
            int headerSize = 0x10 + header.extraHeader;
            int offset = headerSize + header.loadSize + physical * bankSize;
            cpu.mapMem(addr, bankSize, unmappedWrite, 0, romData, offset);
        } else {
            // Out of range: map RAM for both read and write
            cpu.mapMem(addr, bankSize, ram, addr, ram, addr);
        }
    }

    private void cpuWrite(int addr, int data) {
        // Write to the mapped write page (matches C++: *cpu->write(addr) = data)
        // For bank areas this goes to unmappedWrite (discarded), for RAM areas to ram
        cpu.writeToPage(addr, data);

        // Handle SCC and bank switching for addresses 0x4000-0xBFFF
        if ((addr & sccEnabled) == 0x8000) {
            if (addr == 0x9000) setBank(0, data);
            else if (addr == 0xB000) setBank(1, data);
            else {
                int sccAddr = (addr & 0xDFFF) ^ 0x9800;
                if (sccAddr < KssSccApu.REG_COUNT) {
                    sccAccessed = true;
                    scc.write(cpu.time(), sccAddr, data);
                }
            }
        }
    }

    private void cpuOut(int time, int addr, int data) {
        int port = addr & 0xFF;
        switch (port) {
            case 0xA0: // MSX PSG latch
                ayLatch = data & 0x0F;
                break;
            case 0xA1: // MSX PSG write
                ay.write(time, ayLatch, data);
                break;
            case 0x06: // GG stereo
                if (sn != null && (header.deviceFlags & 0x04) != 0) sn.writeGG(time, data);
                break;
            case 0x7E: // SMS/GG PSG
            case 0x7F:
                if (sn != null) {
                    sn.writeData(time, data);
                }
                break;
            case 0xFE: // Sega bank mapping
                setBank(0, data);
                break;
        }
    }

    private int cpuIn(int time, int addr) {
        return 0;
    }

    @Override
    public int runClocks(int duration) {
        while (cpu.time() < duration) {
            int end = Math.min(duration, nextPlay);
            if (cpu.r.pc != 0xFFFF)
                cpu.run(end);

            if (cpu.r.pc == 0xFFFF)
                cpu.setTime(end);

            if (cpu.time() >= nextPlay) {
                nextPlay += playPeriod;
                if (cpu.r.pc == 0xFFFF) {
                    if (!gainUpdated) {
                        gainUpdated = true;
                        if (sccAccessed)
                            updateGain();
                    }
                    ram[--cpu.r.sp] = (byte) 0xFF;
                    ram[--cpu.r.sp] = (byte) 0xFF;
                    cpu.r.pc = header.playAddr;
                }
            }
        }
        duration = cpu.time();
        nextPlay -= duration;
        cpu.adjustTime(-duration);
        ay.endFrame(duration);
        scc.endFrame(duration);
        if (sn != null) sn.endFrame(duration);
        return duration;
    }

    protected void setTempo(double t) {
        int period = (header.deviceFlags & 0x40) != 0 ? CLOCK_RATE / 50 : CLOCK_RATE / 60;
        playPeriod = (int) (period / t);
    }
}
