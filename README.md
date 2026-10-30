# Microcosm Live Wallpaper

A modern remake of the classic **Microbes** live wallpaper for Android, rendered with OpenGL ES 2.0.

> Independent, non-commercial remake. All credit for the original idea goes to the author of the classic Microbes live wallpaper.

## Requirements
- Android 8.0 (API 26) or newer
- A device with OpenGL ES 2.0 and live wallpaper support

## Install
1. Download the latest `microcosm-debug.apk` from the [Releases](../../releases) page.
2. Install it (allow installs from unknown sources if asked).
3. Open the **Microcosm** app icon: it opens the live wallpaper preview, then tap **Set wallpaper**.
   You can also pick it from *Settings > Wallpaper > Live wallpapers*.

## Build from source
The easy way: push to GitHub and let the included workflow build it
(**Actions > Build APK > Run workflow**, then download the `microcosm-debug.apk` artifact).

Locally: open the project in Android Studio (JDK 17) and run the `app` module,
or with Gradle 8.11+ installed:

```bash
gradle assembleDebug
```

The APK is generated as `app/build/outputs/apk/debug/microcosm-debug.apk`.

## Ecosystem
- Producers: algae, euglena, dinoflagellate plankton (photosynthesis, day/night cycle)
- Consumers: microbes, paramecia (some with algal symbionts), amoebae, diatoms, rotifers, tardigrades, a large predator
- Decomposers: yeast and spirilli recycle dead cells back into nutrients
- Reproduction: mitosis (animated), yeast budding, sexual reproduction with meiosis and recombination, ciliate conjugation, bacterial gene transfer
- Realism: speeds in body-lengths/s (compressed on screen), generation times and lifespans per species, run-and-tumble bacteria, phototaxis, daily plankton migration
- Evolution: heritable speed and size traits and colour drift, with mutations and natural selection
- Survival: bacterial spores and tardigrade cryptobiosis, viral infections, plankton migration and bioluminescence (tap the screen)

## Project info
- Package: `com.retro.microbes`
- Min SDK 26, target SDK 36
- Language: Kotlin

## Feedback
Bug reports and ideas are welcome: open an issue or reply in the XDA thread.

## License
[MIT](LICENSE)
