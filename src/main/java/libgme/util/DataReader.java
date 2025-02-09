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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * @see "https://www.slack.net/~ant"
 */
public class DataReader {

    /** Opens InputStream to file stored in various ways */
    static InputStream openHttp(String path) throws IOException {
        return new URL(path).openConnection().getInputStream();
    }

    static InputStream openFile(String path) throws IOException {
        return new FileInputStream(path);
    }

    public static InputStream openGZIP(InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }

    /** "Resizes" array to new size and preserves elements from in */
    public static byte[] resize(byte[] in, int size) {
        byte[] out = new byte[size];
        if (size > in.length)
            size = in.length;
        System.arraycopy(in, 0, out, 0, size);
        return out;
    }

    /** Loads entire stream into byte array, then closes stream */
    public static byte[] loadData(InputStream in) throws IOException {
        byte[] data = new byte[256 * 1024];
        int size = 0;
        int count;
        while ((count = in.read(data, size, data.length - size)) != -1) {
            size += count;
            if (size >= data.length)
                data = resize(data, data.length * 2);
        }
        in.close();

        if (data.length - size > data.length / 4)
            data = resize(data, size);

        return data;
    }

    /** Loads stream into ByteArrayInputStream */
    static ByteArrayInputStream cacheStream(InputStream in) throws IOException {
        return new ByteArrayInputStream(loadData(in));
    }

    /**
     * Finds file named 'path' inside zip file, or returns null if not found.
     * You should use a BufferedInputStream or cacheStream() for input.
     */
    static InputStream openZip(InputStream in, String path) throws IOException {
        ZipInputStream zis = new ZipInputStream(in);
        for (ZipEntry entry; (entry = zis.getNextEntry()) != null; ) {
            if (path.equals(entry.getName()))
                return zis;
        }
        return null;
    }
}
