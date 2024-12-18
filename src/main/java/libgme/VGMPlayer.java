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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import libgme.util.DataReader;

import static java.lang.System.getLogger;


/**
 * Video game music player that runs emulator and plays through speaker
 * <p>
 * Load a music file into player, then start a track. Volume can be
 * adjusted, track can be paused and resumed, a new track can be started,
 * or a new file can be loaded at any time.
 * <p>
 * The file is specified as an HTTP address and optional filename to use
 * if it's a ZIP archive. To avoid loading file more than necessary over
 * HTTP, the most recently loaded file is kept in memory and a load request
 * for the same URL is eliminated. This allows a web page to switch between
 * several tracks in a ZIP archive or of a multi-track music file, without
 * having to keep track of whether the file was already loaded.
 *
 * @see "https://www.slack.net/~ant"
 */
public class VGMPlayer extends EmuPlayer {

    private static final Logger logger = getLogger(VGMPlayer.class.getName());

    private final int sampleRate;

    public VGMPlayer(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * Stops playback and loads file from given URL (HTTP only).
     * If it's an archive (.zip) then path specifies the file within
     * the archive.
     *
     * @throws IllegalArgumentException invalid file
     */
    public void loadFile(String path) throws IOException {
        stop();
        closeFile();

        if (!loadedPath.equals(path)) {
            byte[] data = readFile(path);

            String name = path.toUpperCase();
            if (name.endsWith(".ZIP"))
                name = path.toUpperCase();

            if (name.endsWith(".GZ"))
                name = name.substring(0, name.length() - 3);

            MusicEmu emu = createEmu(name);
            if (emu == null)
                throw new IllegalArgumentException("invalid file");
            int actualSampleRate = emu.setSampleRate(sampleRate);
            emu.loadFile(data);

            // now that new emulator is ready, replace old one
            setEmu(emu, actualSampleRate);
            loadedPath = path;
        }
    }

    /** Stops and closes current file and unloads things from memory */
    private void closeFile() {
        stop();
        setEmu(null, 0);

        loadedPath = "";
    }

    // private

    private String loadedPath = "";

    /**
     * Creates appropriate emulator for given filename
     * @return nullable
     */
    private static MusicEmu createEmu(String name) {
        for (MusicEmu musicEmu : serviceLoader) {
logger.log(Level.TRACE, musicEmu + ", " + name);
            if (musicEmu.isSupportedByName(name)) {
                return musicEmu;
            }
        }

        return null;
    }

    private static boolean isGunzipNeeded(String name) {
        for (MusicEmu musicEmu : serviceLoader) {
            if (musicEmu.isGunzipNeeded(name)) {
                return true;
            }
        }

        return false;
    }

    /** Loads given URL and file within archive, and caches archive for future access */
    private static byte[] readFile(String path) throws IOException {

        InputStream in = new FileInputStream(path);

        String name = path.toUpperCase();
//logger.log(Level.TRACE, "Unzip " + url);

        if (name.endsWith(".GZ") || isGunzipNeeded(name)) {
            in = DataReader.openGZIP(in);
        }

        return DataReader.loadData(in);
    }
}
