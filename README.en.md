# SoundOrbit (Realtime Surround)

[![Latest Release](https://img.shields.io/github/v/release/Cesium-developer/SoundOrbit?display_name=tag)](https://github.com/Cesium-developer/SoundOrbit/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-green.svg)](https://developer.android.com)
[![Java](https://img.shields.io/badge/language-Java-orange.svg)](https://www.java.com)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/Cesium-developer/SoundOrbit/pulls)

[中文文档](README.md)

> Orbital surround sound, in real time, for whatever your phone is playing — fully on-device, no root, no network.

SoundOrbit is an Android realtime surround-sound tool that does all its work on the device. It leaves the source audio untouched and processes it only on the way to your headphones: either sweeping the image between the two ears, or orbiting a sound source around your head.

**Two engines, pick what fits:**

- **Lightweight engine** — attaches the effect directly to the system output mix and modulates the left/right channel gains with an **anti-phase triangle wave** (adjustable depth, period, and side/center time ratio; the period can also be randomized within a range). It never captures or replays audio, so there is no doubling, no echo, and no extra buffering.
- **Advanced engine** — captures a copy of whatever is currently playing, then renders it into a binaural sound field that moves around your head using **ITD (interaural time difference) + ILD (interaural level difference) + head-shadow low-pass + pinna notch filtering**. Left/right, front/back, and up/down are distinguishable. Adds some processing latency and requires a one-time "record screen/audio" authorization.

**What it's for:** immersive mono ASMR; adding movement to ordinary music; giving streams — live broadcasts, videos you can't be bothered to download — an extra surround dimension. Anything currently playing on the phone works; local files are not required.

**Privacy & dependencies:** the app declares no `INTERNET` permission, makes no network calls, and ships no networking SDK (dependencies are local libraries only — AndroidX `annotation` / `appcompat` and the Kotlin stdlib). Audio stays on the device throughout — nothing is uploaded, written to disk, or passed to any external service.

---

## Features

- **Two engines**: **Lightweight** (session 0, zero latency, left/right sweep) / **Advanced** (capture, pseudo / true surround, some latency, more capable).
- **Seven surround modes** (Advanced engine):
  - Panning: take left → both ears, take right → both ears, stereo downmixed to mono → both ears;
  - Binaural: stereo direct (L and R orbit independently), take-left source orbiting, take-right source orbiting, stereo downmixed to mono orbiting.
- **Random period range**: each sweep/orbit picks a fresh period within `[min, max]`; the phase stays continuous with no jumps. Invalid ranges are rejected with a prompt to re-enter them.
- **Live visualization**: refreshes in sync with the audio and switches view by engine/mode — panning shows pan position, per-channel gains, and triangle-wave phase; binaural shows a top-down head view (source azimuth, elevation, motion trail; stereo shows two sources).
- **Output-stage DSP** (Advanced engine, under Settings → DSP settings):
  - **Soft limiter**: on by default, threshold -1 dBTP (0.89); smoothly compresses only peaks without touching normal levels; threshold adjustable (0.1–0.99).
  - **10-band graphic EQ**: ISO octave centers (31.5 Hz–16 kHz), ±12 dB, flat by default (no processing); save multiple custom presets — selecting a preset requires tapping *Apply preset* to take effect.
  - **Bass enhancement**: psychoacoustic bass, strength 0–1, default 0 (off).
- **Runtime guard**: while surround is on, all sound-affecting parameters are locked; tapping them prompts you to turn surround off first (keep-alive and language settings are unaffected).
- **Bilingual UI**: Simplified Chinese and English built in; switch in Settings (follow system / Chinese / English), defaulting to the system language.
- **Background survival**: foreground service, optional battery-optimization exemption, optional accessibility-based keep-alive, quick settings tile, and a persistent notification.
- **Unconstrained parameters**: depth, period, and ratio are free numeric inputs with no forced slider caps (output is clamped only at the int16 boundary).

## Parameters

| Parameter | Meaning | Applies to |
|---|---|---|
| Depth (amplitude) | Maximum left/right volume difference | Zero-latency / panning |
| Period (s) | Time for one back-and-forth sweep, or one orbit | All; rotation period in Advanced engine |
| Side/center time ratio | 1 = original algorithm; higher lingers at the sides, lower at the center | Zero-latency / panning |
| Start azimuth (°) | 0 front / 90 right / 180 back / 270 left | Binaural |
| Radius (0–1) | Smaller = closer to the head, stronger binaural cues | Binaural |
| Elevation (°) | -60 to 60, source above or below | Binaural |

> Binaural **source motion** uses only `period + start azimuth + radius + elevation`; depth and ratio have no effect on it (noted in the UI). Output DSP (soft limiter, EQ, bass enhancement) still applies before binaural output.

### DSP settings (Settings · Advanced)

Collapsed by default under **Settings → DSP settings (advanced)**, containing three sections, all for the Advanced engine:

1. **Output processing · soft limiter**: limiter threshold, bass-enhancement strength;
2. **Graphic equalizer (10 bands)**: 10 band gains and preset management;
3. **HRTF settings (spatial rendering)**: low-level binaural controls.

These are for fine-tuning — bad values may make audio muffled, distorted, or break positioning. Out-of-range values are clamped and each section has a reset button. Defaults are recommended.

#### Output-processing parameters

| Parameter | Meaning | Default | Range |
|---|---|---|---|
| Soft-limiter threshold | Audio below the threshold passes untouched; only peaks are smoothly compressed with tanh to prevent clipping. Lower = earlier engagement, more conservative sound | 0.89 (-1 dBTP) | 0.1–0.99 |
| Bass enhancement strength | Psychoacoustic bass: harmonics imply low notes on small speakers; 0 = off, too high may distort | 0 (off) | 0–1 |

#### Graphic equalizer (10 bands)

ISO octave center frequencies, in dB, flat (0) by default:

| Band | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Frequency (Hz) | 31.5 | 63 | 125 | 250 | 500 | 1k | 2k | 4k | 8k | 16k |

Each band's gain ranges ±12 dB (positive boosts, negative cuts).

**Preset behavior** (important):

- The dropdown has three kinds of entries: **No preset (all zero)**, **Custom (unsaved)**, and any presets you saved;
- **Selecting a preset only fills the input fields as a draft — it does not take effect**; you must tap *Apply preset (to audio)* to write it to the engine and get a confirmation toast;
- On apply, if the current values differ from the selected preset, the dropdown switches to *Custom (unsaved)* (or *No preset* if all zero) — so after editing preset A you can naturally re-select A from the dropdown to restore its original values;
- *Save current as new preset*: names and saves the current 10 bands (unlimited count); saving also applies the current values;
- *Delete current preset*: removes the currently selected saved preset (No preset / Custom entries cannot be deleted);
- *Reset EQ*: zeros all 10 bands as a draft and switches to *No preset*; still needs Apply to take effect;
- **Leaving Settings without tapping Apply discards the draft — the engine keeps the previously applied values.**

#### HRTF parameters

| Parameter | Meaning | Default | Range |
|---|---|---|---|
| Max interaural time difference (ms) | ITD ceiling, controls stage width / head-size calibration | 0.7 | 0.4–1.0 |
| Interaural level difference | ILD depth: higher = sharper positioning, lower = more natural | 0.55 | 0–1 |
| Head-shadow strength | High-frequency attenuation on the far ear, a spatial cue | 0.55 | 0–1 |
| Pinna notch sharpness | Spectrum-notch bandwidth behind front/back and up/down cues; closer to 1 = narrower and deeper | 0.8 | 0.5–0.95 |

> **When changes apply**: soft-limiter threshold and bass strength save on returning to the main screen; EQ edits are drafts and only take effect after tapping *Apply preset*; HRTF changes apply on returning to the main screen (next frame). All are locked while surround is on. If you mess things up, tap the corresponding reset button.

## Usage

1. Headphones or a stereo output device are recommended.
2. **Lightweight engine**: select the *Lightweight* engine → tap *Enable surround*, or add the *Realtime surround* quick tile. Defaults `20 / 20 / 1` are audible immediately.
3. **Advanced engine**: select the *Advanced* engine → tap *Authorize audio capture* → allow "record screen/audio" in the dialog → pick one of the seven modes → enable.
4. For a random period: check *Random period range* below the period field and enter the lower and upper bounds.
5. Settings offers: UI language (follow system / 简体中文 / English), battery-optimization exemption, optional accessibility keep-alive, view/copy diagnostics, and the collapsed *DSP settings (advanced)* panel for output processing and HRTF fine-tuning.
6. To tune sound: turn surround off → open *DSP settings* → adjust the soft limiter / bass in Output processing, or use the 10-band EQ (tap *Apply preset* to commit changes; multiple custom presets can be saved) → return and re-enable surround. These parameters are locked while running.

## How it works

- **Zero latency / panning**: `DynamicsProcessing` is attached to the session 0 global mix, changing per-channel input gains in real time with an anti-phase triangle wave; panning modes first duplicate or downmix channels. No capture, so no doubling.
- **Binaural**: `AudioPlaybackCapture` grabs a copy of other apps' playback, renders a binaural field with ITD (delay line) + ILD (far-side attenuation) + head-shadow low-pass, then plays it back through `AudioTrack`. On start it mutes the media stream to kill the original (verified that muting does not affect capture) and plays back on a separate ASSISTANT stream — again with no doubling. Its own UID is excluded from capture to prevent the playback from being re-captured into a feedback loop.
- **Output DSP**: before writing, the Advanced engine passes audio through a 10-band peaking-biquad EQ and a soft limiter (tanh compression, unity below the threshold); bass enhancement adds harmonics for a psychoacoustic low end, off by default. The Lightweight engine works by modulating gains in the system mix and does not go through these DSP stages.

## Compatibility & limitations

- Android 10 (API 29)+; `targetSdk 34`.
- Zero-latency: attached to the system's preferred output thread; calls and low-latency/direct paths are unaffected. On ROMs without session 0 global-effect support, the status shows the startup failure reason.
- Advanced engine: needs a one-time "record screen/audio" authorization; can only capture apps that allow capture (most music/video apps do by default, some DRM apps don't); works with Bluetooth headphones too; capturing and replaying introduces some latency.
- Android's accessibility "mono audio" merges channels at the final output stage, which cancels out the left/right panning — not suitable for this tool.

## Build

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # release APK (unsigned, for F-Droid/CI to sign)
```

APKs land in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/` (the latter as `app-release-unsigned.apk`). Requires Android SDK (API 34) and JDK 17. The project uses the Gradle Wrapper + AGP 8.6.1, with `compileSdk 34`, `minSdk 29`, `targetSdk 34`. The Android module is in the `app/` subdirectory.

**Reproducible builds**: `build.gradle` reads signing keys only from environment variables (`SIGN_KEYSTORE`, etc.). Local and F-Droid builds do not inject these, so they produce an unsigned APK that F-Droid signs with its own key. No signing keys or machine-specific absolute paths live in the source tree (`local.properties` is gitignored). No network dependencies, no proprietary SDKs, AndroidX-only — suitable for independent source builds.

Just open the project root in Android Studio.

## Releasing

Push a tag starting with `v` (e.g. `git tag v0.1.3 && git push origin v0.1.3`). `.github/workflows/release.yml` stamps the version, builds a properly signed APK, and creates a GitHub Release with it attached. The signing key is not stored in the repo — CI restores it from repository Secrets (`SIGN_KEYSTORE_B64` / `SIGN_STORE_PASSWORD` / `SIGN_KEY_ALIAS` / `SIGN_KEY_PASSWORD`), so releases share one stable signature and users can upgrade in place.

Tags strictly follow `v<versionName>` (e.g. `v0.1.3`). F-Droid's `AutoUpdateMode: Version v%v` / `UpdateCheckMode: Tags` relies on this naming to discover and build new versions — do not change the format.

## F-Droid

This app is GPL-3.0, has no `INTERNET` permission, makes no network calls, and has no proprietary dependencies; it builds independently from source and is suitable for F-Droid. The F-Droid metadata (`.yml`) lives in F-Droid's official `fdroiddata` repository following that repo's submission process. The app source is maintained only in this GitHub repository — F-Droid pulls by tag, builds, and signs with its own key. Consequently the F-Droid build and the GitHub Release have different signatures; switching between them on one device requires uninstalling the other first.

## Contributing

Issues and pull requests are welcome. Please make sure `./gradlew :app:assembleDebug` passes before submitting; for changes to the audio algorithms or ROM compatibility, include notes on how you verified them.

## AI disclosure

This project was built with the help of an AI assistant using the model **hy4 flash**. The author defined the requirements, made the design decisions, tested on real devices, and reviewed the final result. All features were verified on actual hardware; the audio algorithms and device compatibility are based on real-world testing.

## License

GPL-3.0, see [LICENSE](LICENSE).
