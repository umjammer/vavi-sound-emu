/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import libgme.VGMPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.kernite.VGM.gme;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-08-26 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property
    String vgz;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
Debug.println("volume: " + volume);
    }

    @Test
    void test() throws Exception {
        gme.main(new String[] { vgz });
        CountDownLatch cdl = new CountDownLatch(1);
        cdl.await();
    }

    @Test
    void test1() throws Exception {
        VGMPlayer player = new VGMPlayer(44100);
Debug.println(vgz);
        player.setVolume(volume);
        player.loadFile(vgz);
        player.setTrack(1);
        player.play();
        while (player.isPlaying()) Thread.yield(); // TODO add event system
    }
}
