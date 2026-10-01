# Structura Sonorum — Changelog

Versions are listed newest first. This history is reconstructed from the development conversations and GitHub commits. Changes from the earliest builds are grouped together because their individual version numbers could not be verified. Dates use Vienna local time.

## 1.12 — 2026-10-01

- Removed the v1.11 platform media session and headset-button integration because it caused regressions on Samsung devices: the triangle status icon disappeared, Exit was omitted, and Android added a nonfunctional progress bar. Restored the compact notification presentation used before v1.11.
- Corrected playback shutdown so the queued fade-out reaches the audio device before the stream is flushed and released, addressing hard clicks during pause, import, and exit.
- Smoothed stereo pan changes inside the audio engine so tapping a new pan position does not create an abrupt waveform discontinuity.
- Changed configuration import to preserve the preceding playback state: importing while playing automatically starts the imported configuration after the old sound fades out; importing while paused remains paused.
- Updated release metadata to **version 1.12** with `versionCode 22`.

## 1.11 — 2026-10-01

- Extended the Impulse speed range from **20–200 BPM** to **20–400 BPM**, with **0.1 BPM** internal resolution.
- Added precise numerical entry for every slider by tapping its value label. Signed and decimal values are accepted where applicable, comma and point decimal separators are supported, and entries are validated against each control's range.
- Restored the v1.9 noise synthesis mix and fixed compensation curve after the v1.10 adaptive normalizer weakened the preferred dark bass character; retained v1.10's **0.1%** color resolution and labeled anchors.
- Added short master fade-in and fade-out envelopes to reduce discontinuity clicks during play, pause, master mute, import, and exit.
- Added a platform media session so standard wired and Bluetooth earphone play/pause controls can operate global playback without adding a large external media framework.
- Added a one-time explanation before Android's notification permission request, clarifying that notifications provide background playback controls and are not promotional.
- Updated configuration migration so existing Impulse speeds and older JSON backups remain compatible with the expanded speed range.
- Updated the README with the approximate **40 KB** release size, precise label-tap input, perceptual interaction between generators, headset controls, fade behavior, and revised noise implementation.
- Updated release metadata to **version 1.11** with `versionCode 21`.

## 1.10 — 2026-09-30

- Renamed the Click generator to **Impulse** throughout the user interface and README.
- Increased noise color resolution to **0.1%** steps and smoothed the transition near the darkest end of the slider.
- Added labeled noise color anchors: **0% sub-Brownian/dark**, **25% Brownian**, **65% Pink**, and **100% White/bright**. These describe the app's synthesis settings rather than measurements of ideal noise spectra.
- Replaced the earlier noise level compensation with slow adaptive normalization and reduced the level toward the white end to improve perceived loudness consistency.
- Updated saved settings and JSON backup migration for the finer noise color resolution, retaining compatibility with older configurations.
- Changed the About credit to “Built 2026 by DFKT”.
- Added the simplified theory behind the three generators and triangle logo to the README.
- Corrected the delivered source archive's version metadata so builds and the About dialog report **1.10**, with `versionCode 20`.
- Confirmed successful user testing on Samsung Galaxy S25+ (One UI 8.5) and Galaxy Tab S8+ (One UI 8.0), both running Android 16.

## 1.9 — 2026-09-19

- Standardized user-facing terminology to **generator**.
- Increased volume slider resolution to **0.1%** steps for finer low-level adjustments.
- Added progressive level compensation for darker noise settings.
- Added equal-power stereo pan to every generator, from **−100% left** to **+100% right**, centered by default.
- Displayed sine fluctuation speed in both **Hz and BPM**.
- Retained compatibility with older configuration backups.
- Published the source on GitHub on September 20 and documented successful testing on the two Android 16 Samsung devices.

## 1.8 — 2026-09-19

- Replaced the export-only control with a **two-arrows Import/Export button** and a combined menu.
- Added configuration import through the Android file picker.
- Validated selected JSON backups and asked for confirmation before replacing the current configuration.
- Stopped playback when importing a configuration.

## 1.7 — 2026-09-19

- Added confirmation before removing a generator: “Do you really want to remove this generator?”
- Changed the sine fine-frequency range to **−25 Hz through +25 Hz**, centered at zero.
- Corrected the total sine frequency display to account for both coarse and fine controls.
- Automatically scrolled to newly added generator cards.
- Improved generator button brightness and increased bottom-button text size.
- Added JSON configuration export directly to Downloads on Android 10 and newer, using filenames such as `△-2026-09-18-21-45-00.json`; older supported Android versions use the system save picker.

## 1.6 — 2026-09-18

- Added brighter pressed-state feedback to controls.
- Dimmed muted generator cards, including their text, sliders, and icons, and restored their appearance when unmuted.
- Refined the centered About dialog, with a blank line before the clickable website link.

## 1.5 — 2026-09-18

- Reduced the launcher triangle size to fit Samsung's icon shape more comfortably.
- Added the header triangle and an About dialog showing the app version, author, and website.
- Updated notification Exit to stop playback and close/remove the app task.
- Requested compact notification action visibility; the final presentation remains controlled by Android and the device manufacturer.

## Early development — through 1.4, September 2026

The available records do not establish a reliable per-version mapping for 1.0–1.4. The following delivered revisions are therefore recorded in reverse chronological order without assigning unverified version numbers.

### Button and layout refinements — 2026-09-18

- Added spacing between generator card buttons and balanced card layout spacing.
- Changed the top controls to muted dark red.
- Aligned the outer add-generator buttons.
- Refined the reset confirmation wording: “Are you sure you want to reset the application to its initial state?”

### Icons, reset, and notification controls — 2026-09-18

- Added matching vector play/pause, remove, and reset icons.
- Set the top Play/Pause button to three quarters of the available width and Reset to one quarter.
- Added reset confirmation and restoration of the initial configuration.
- Added the white outlined triangle launcher icon.
- Added persistent notification controls for muting/unmuting output and exiting.
- Tightened card corners, used the singular label “Click”, and extended the dark end of the noise color range.

### Structura Sonorum controls and identity — 2026-09-18

- Adopted the name **Structura Sonorum** and the Latin subtitle.
- Added separate Click controls for speed, randomness, brightness, and decay.
- Added coarse and fine sine frequency controls, plus fluctuation depth and speed.
- Grouped generators by type and added mute/remove controls.
- Established the subdued purple Noise, orange Click, and green Sine palette.

### Initial Noise Triangle prototype — 2026-09-18

- Introduced multiple independently addable Noise, Click, and Sine generators with volume and sound-character controls.
- Saved the generator configuration and settings between sessions.
- Started with one generator of each type, sliders at the left, and playback paused.

## Documentation updates outside numbered releases

- **2026-10-01:** Replaced the README interface screenshot and documented successful version 1.10 device testing.
- **2026-09-20:** Added the initial README screenshot and device-testing notes; removed the translation of the Latin subtitle.

## Sources

- Original Structura Sonorum development conversations, September 18–30, 2026.
- [GitHub repository and commit history](https://github.com/dfkt/structura-sonorum/commits/main/).
- [Version 1.10 source changes](https://github.com/dfkt/structura-sonorum/commit/37274f569eefb3aabda7f1ff9c7d93a6f5cdec41).
