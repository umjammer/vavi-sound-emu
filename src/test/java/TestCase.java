/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.LineEvent.Type;

import libgme.EmuPlayer.JavaEngine;
import libgme.VGMPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vavi.sound.sampled.emu.EmuAudioManager;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static vavix.util.DelayedWorker.later;


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
    String vgz = "src/test/resources/test.vgm";

    @Property(name = "track")
    int track = 1;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
Debug.println("volume: " + volume);
    }

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 10 * 1000;

    @Test
    @DisplayName("file")
    void test1() throws Exception {
        System.setProperty("libgme.endless", String.valueOf(onIde));
Debug.print("libgme.endless: " + System.getProperty("libgme.endless"));

        VGMPlayer player = new VGMPlayer(44100);
Debug.println(vgz);
        CountDownLatch cdl = new CountDownLatch(1);

        JavaEngine engine = new JavaEngine();
        engine.addLineListener(e -> { if (e.getType() == Type.STOP) cdl.countDown(); });
        engine.setVolume(volume);

        player.setEngine(engine);
        player.loadFile(vgz);
        player.setTrack(track);
        player.play();

        if (!onIde) later(time, cdl::countDown);
        cdl.await();

        player.stop();
    }

    @Test
    @DisplayName("stream")
    void test2() throws Exception {
        System.setProperty("libgme.endless", String.valueOf(onIde));

        EmuAudioManager manager = new EmuAudioManager(44100);
Debug.println(vgz);
        CountDownLatch cdl = new CountDownLatch(1);

        JavaEngine engine = new JavaEngine();
        engine.addLineListener(e -> { if (e.getType() == Type.STOP) cdl.countDown(); });
        engine.setVolume(volume);

        manager.setEngine(engine);
        manager.loadFile(Files.newInputStream(Path.of(vgz)));
        manager.setTrack(track);
        manager.play();

        if (!onIde) later(time, cdl::countDown);
        cdl.await();

        manager.stop();
    }
}
