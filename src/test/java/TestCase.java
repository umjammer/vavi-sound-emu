/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.LineEvent.Type;

import libgme.EmuPlayer.JavaEngine;
import libgme.VGMPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
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
@EnabledIf("localPropertiesExists")
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
    @DisplayName("gui")
    void test() throws Exception {
        gme.main(new String[] { vgz });

        CountDownLatch cdl = new CountDownLatch(1);
        cdl.await();
    }

    @Test
    @DisplayName("headless")
    void test1() throws Exception {
        System.setProperty("libgme.endless", "true");

        VGMPlayer player = new VGMPlayer(44100);
Debug.println(vgz);
        CountDownLatch cdl = new CountDownLatch(1);

        JavaEngine engine = new JavaEngine();
        engine.addLineListener(e -> { if (e.getType() == Type.STOP) cdl.countDown(); });

        player.setVolume(volume);
        player.setEngine(engine);
        player.loadFile(vgz);
        player.setTrack(1);
        player.play();

        cdl.await();
    }
}
