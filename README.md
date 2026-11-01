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
The easy way: push to GitHub and run the included workflow
(**Actions > Setup and Build > Run workflow**), then take `microcosm-debug.apk` from the **Releases** page.

Locally: open the project in Android Studio (JDK 17) and run the `app` module,
or with Gradle 8.11+ installed:

```bash
gradle assembleDebug
```

The APK is generated as `app/build/outputs/apk/debug/microcosm-debug.apk`.

## Ecosystem
A dark-field microscope slide with 13 organisms, simulated agent by agent:
- **Producers:** green algae, diatoms, euglena, dinoflagellate plankton (photosynthesis limited by light and dissolved nutrients; euglena swim toward light, plankton migrate vertically and flash when disturbed)
- **Bacteria, decomposers and yeast:** bacteria with run-and-tumble swimming and spores, spirilla, budding yeast; dead cells and detritus return nutrients to the pool
- **Grazers and predators:** paramecia (some with algal symbionts), amoebae, rotifers, tardigrades, and *Didinium*, which hunts paramecia
- **Bacteriophages:** adsorb to bacteria, inject their DNA, and the host lyses after a latent period releasing new phages; empty capsids dissolve slowly
- **Feeding:** each predator has its own capture and digestion: pseudopod cup (amoeba), ciliary current to the mouth and food vacuole cyclosis (paramecium), grinding mastax (rotifer), swallowing whole (Didinium), stylet sucking (tardigrade); failed attacks, satiety and prey refuges keep the populations balanced
- **Reproduction:** animated mitosis with nuclear division and a cleavage furrow, yeast budding, rotifer eggs (parthenogenesis), gamete fusion (diatoms, tardigrades), ciliate conjugation
- **Evolution:** heritable speed and size, colour drift, mutation and selection
- **Paramecium avoidance reaction:** ciliary reversal, a step backward, a pivot and a new direction, triggered by predators, failed attacks or at random
- **Survival:** bacterial spores, Didinium cysts and tardigrade cryptobiosis
- **Realism:** speeds in body lengths per second (compressed on screen), lifespans and generation times per species, soft bodies bend only while turning, and rigid parts never deform
- **Battery:** everything is suspended 15 seconds after the wallpaper stops being visible

Tap the screen to drop nutrients.

## Project info
- Package: `com.retro.microbes`
- Min SDK 26, target SDK 36
- Language: Kotlin

## Feedback
Bug reports and ideas are welcome: open an issue or reply in the XDA thread.

## License
[MIT](LICENSE)
