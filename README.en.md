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

- **Zero-latency engine** — attaches the effect directly to the system output mix and modulates the left/right channel gains with an **anti-phase triangle wave** (adjustable depth, period, and side/center time ratio; the period can also be randomized within a range). It never captures or replays audio, so there is no doubling, no echo, and no extra buffering.
- **Spatial audio engine** — captures a copy of whatever is currently playing, then renders it into a binaural sound field with a clear sense of position and motion using **ITD (interaural time difference) + ILD (interaural level difference) + head-shadow low-pass filtering**. Adds roughly 80–120 ms of latency and requires a one-time "record screen/audio" authorization.

**What it's for:** immersive mono ASMR; adding movement to ordinary music; giving streams — live broadcasts, videos you can't be bothered to download — an extra surround dimension. Anything currently playing on the phone works; local files are not required.

**Privacy & dependencies:** the app declares no `INTERNET` permission, makes no network calls, and ships no networking SDK (dependencies are local libraries only — AndroidX `annotation` / `appcompat` and the Kotlin stdlib). Audio stays on the device throughout — nothing is uploaded, written to disk, or passed to any external service.

---

## Features

- **Two engines**: a lightweight system-wide effect (zero latency) / high-fidelity captured spatial audio (some latency, more capable).
- **Seven surround modes** (spatial audio engine):
  - Panning: take left → both ears, take right → both ears, stereo downmixed to mono → both ears;
  - Binaural: stereo direct (L and R orbit independently), take-left source orbiting, take-right source orbiting, stereo downmixed to mono orbiting.
- **Random period range**: each sweep/orbit picks a fresh period within `[min, max]`; the phase stays continuous with no jumps. Invalid ranges are rejected with a prompt to re-enter them.
- **Live visualization**: pan position, per-channel gains, and triangle-wave phase refresh in sync with the audio.
- **Bilingual UI**: Simplified Chinese and English built in; switch in Settings (follow system / Chinese / English), defaulting to the system language.
- **Background survival**: foreground service, optional battery-optimization exemption, optional accessibility-based keep-alive, quick settings tile, and a notification showing live gains and parameters.
- **Unconstrained parameters**: depth, period, and ratio are free numeric inputs with no forced slider caps (output is clamped only at the int16 boundary).

## Parameters

| Parameter | Meaning | Applies to |
|---|---|---|
| Depth (amplitude) | Maximum left/right volume difference | Zero-latency / panning |
| Period (s) | Time for one back-and-forth sweep, or one orbit | All; rotation period in spatial mode |
| Side/center time ratio | 1 = original algorithm; higher lingers at the sides, lower at the center | Zero-latency / panning |
| Start azimuth (°) | 0 front / 90 right / 180 back / 270 left | Binaural |
| Radius (0–1) | Smaller = closer to the head, stronger binaural cues | Binaural |
| Elevation (°) | -60 to 60, source above or below | Binaural |

> Binaural mode uses only `period + start azimuth + radius + elevation`; depth and ratio have no effect on it (noted in the UI as well).

## Usage

1. Headphones or a stereo output device are recommended.
2. **Zero latency**: select the *Lightweight* engine → tap *Enable surround*, or add the *Realtime surround* quick tile. Defaults `20 / 20 / 1` are audible immediately.
3. **Spatial audio**: select the *Advanced* engine → tap *Authorize audio capture* → allow "record screen/audio" in the dialog → pick one of the seven modes → enable.
4. For a random period: check *Random period range* below the period field and enter the lower and upper bounds.
5. Settings offers: UI language (follow system / 简体中文 / English), battery-optimization exemption, optional accessibility keep-alive, and view/copy diagnostics.

## How it works

- **Zero latency / panning**: `DynamicsProcessing` is attached to the session 0 global mix, changing per-channel input gains in real time with an anti-phase triangle wave; panning modes first duplicate or downmix channels. No capture, so no doubling.
- **Binaural**: `AudioPlaybackCapture` grabs a copy of other apps' playback, renders a binaural field with ITD (delay line) + ILD (far-side attenuation) + head-shadow low-pass, then plays it back through `AudioTrack`. On start it mutes the media stream to kill the original (verified that muting does not affect capture) and plays back on a separate ASSISTANT stream — again with no doubling. Its own UID is excluded from capture to prevent the playback from being re-captured into a feedback loop.

## Compatibility & limitations

- Android 10 (API 29)+; `targetSdk 34`.
- Zero-latency: attached to the system's preferred output thread; calls and low-latency/direct paths are unaffected. On ROMs without session 0 global-effect support, the status shows the startup failure reason.
- Spatial audio: needs a one-time "record screen/audio" authorization; can only capture apps that allow capture (most music/video apps do by default, some DRM apps don't); works with Bluetooth headphones too; ~80–120 ms latency.
- Android's accessibility "mono audio" merges channels at the final output stage, which cancels out the left/right panning — not suitable for this tool.

## Build

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Requires Android SDK (API 34) and JDK 17. The project uses the Gradle Wrapper + AGP 8.6.1, with `compileSdk 34`, `minSdk 29`, `targetSdk 34`.

Just open the project root in Android Studio.

## Releasing

Push a tag starting with `v` (e.g. `git tag v0.1.0 && git push origin v0.1.0`). `.github/workflows/release.yml` stamps the version, builds a properly signed APK, and creates a GitHub Release with it attached. The signing key is not stored in the repo — CI restores it from repository Secrets (`SIGN_KEYSTORE_B64` / `SIGN_STORE_PASSWORD` / `SIGN_KEY_ALIAS` / `SIGN_KEY_PASSWORD`), so releases share one stable signature and users can upgrade in place.

## Contributing

Issues and pull requests are welcome. Please make sure `./gradlew :app:assembleDebug` passes before submitting; for changes to the audio algorithms or ROM compatibility, include notes on how you verified them.

## AI disclosure

This project was built with the help of an AI assistant using the model **hy4 flash**. The author defined the requirements, made the design decisions, tested on real devices, and reviewed the final result. All features were verified on actual hardware; the audio algorithms and device compatibility are based on real-world testing.

## License

GPL-3.0, see [LICENSE](LICENSE).
