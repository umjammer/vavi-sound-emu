[![Release](https://jitpack.io/v/umjammer/vavi-sound-emu.svg)](https://jitpack.io/#umjammer/vavi-sound-emu)
[![Java CI](https://github.com/umjammer/vavi-sound-emu/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-emu/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-emu/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-sound-emu/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-25-b07219)
[![Parent](https://img.shields.io/badge/Parent-vavi--sound--sandbox-pink)](https://github.com/umjammer/vavi-sound-sandbox)

# vavi-sound-emu

<img alt="logo" src="src/test/resources/duke_game.png" width="180" />

java port game music emu. mavenized and spi-nized also. 

| name | description | status | comment                                          |
|------|-------------|:------:|--------------------------------------------------|
| gbs  | Game Boy    |   ✅    | green                                            |
| nsf  | NES         |   ✅️   | green                                            |
| spc  | SNES        |   ✅️   | green                                            |
| vgm  | All         |   ✅    | Ym2612(mame:dallongeville+green), Sn76489(green) |
| kss  | MSX         |   ✅    | Ay38910(green), Sn76489(green)                   |

## Install

 * [maven](https://jitpack.io/#umjammer/vavi-sound-emu)

## Usage

```java
  AudioInputStream vgmAis = AudioSystem.getAudioInputStream(Paths.get(vgz).toFile());
  AudioFormat inFormat = sourceAis.getFormat();
  AudioFormat outFormat = new AudioFormat(inFormat.getSampleRate(), 16, inFormat.getChannels(), true, true, props);
  AudioInputStream pcmAis = AudioSystem.getAudioInputStream(outFormat, vgmAis);
  SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, pcmAis.getFormat()));
  line.open(pcmAis.getFormat());
  line.start();
  byte[] buffer = new byte[line.getBufferSize()];
  int bytesRead;
  while ((bytesRead = pcmAis.read(buffer)) != -1) {
    line.write(buffer, 0, bytesRead);
  }
  line.drain();
```

### properties for target `AudioFormat`

 * `track` ... specify track # in the file to play

### system properties

 * `libgme.endless` ... loop audio playing or not, default `false`
 * `vavi.sound.sampled.spi.emu` ... these reader and conversion provider enabled or not (works for `VGM` encoding only), default `true`

## References

 * https://www.slack.net/~ant/ (blargg's site)
 * https://github.com/GeoffWilson/VGM
 * https://github.com/libgme/game-music-emu
 * https://www.zophar.net/music/kss.html (kss)

## TODO

 * ~~make those using service loader~~
 * ~~javax sound spi~~
 * ~~vgm after 1.50~~
   * ~~game-music-emu cannot play "Magical Sound Shower"~~ not version, but chips impls
   * ~~MDPlayer can play above~~ ditto
 * ~~`vavi.sound.sampled.emu.TestCase#test5`~~
 * ~~spi properties for track # etc.~~
 * ~~kss~~
 * gym
 * sap
 * ~~off vgm spi by system property~~
   * ~~it's better to return empty at `FormatConversionProvider#getTargetFormats`?~~

---

<sub>image designed by @umjammer, drawn by nano banana</sub>

