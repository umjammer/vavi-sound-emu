[![Release](https://jitpack.io/v/umjammer/vavi-sound-emu.svg)](https://jitpack.io/#umjammer/vavi-sound-emu)
[![Java CI](https://github.com/umjammer/vavi-sound-emu/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-emu/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-emu/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-sound-emu/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-sound-emu

java port game music emu. mavenized and spi-zed also. 

| name | description | status  | comment                    |
|------|-------------|---------|----------------------------|
| gbs  | Game Boy    | TBD     | not tested but probably ok |
| nsf  | NES         | ✅️      |                            |
| spc  | SNES        | TBD     | not tested but probably ok |
| vgm  | Mega Drive  | ✅       |                            |

## Install

 * [maven](https://jitpack.io/#umjammer/vavi-sound-emu)

## Usage

```java
AudioInputStream ais = AudioSystem.getAudioInputStream(Paths.get(vgz).toFile());
Clip clip = AudioSystem.getClip();
clip.open(AudioSystem.getAudioInputStream(new AudioFormat(Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, true, props), ais));
clip.loop(Clip.LOOP_CONTINUOUSLY);
```

### properties for target `AudioFormat`

 * `track` ... specify track # in the file to play

### system properties

 * `libgme.endless` ... loop audio playing or not, default `false`

## References

 * https://github.com/GeoffWilson/VGM
 * https://github.com/libgme/game-music-emu

## TODO

 * ~~make those using service loader~~
 * ~~javax sound spi~~
 * vgm after 1.50
 * `vavi.sound.sampled.emu.TestCase#test5`
 * ~~spi properties for track # etc.~~
