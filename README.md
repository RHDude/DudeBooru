<div align="center">

# DudeBooru

**A fast, private booru client for Android — Danbooru, Safebooru, Yande.re and Konachan in one app.**

[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin · Jetpack Compose](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

**English** · [Русский](README.ru.md)

</div>

---

DudeBooru brings image boards to your phone the way they should feel on a phone: a full-width feed
with a folder for every source, a gesture-driven viewer, and your likes, saved posts and downloads
kept on the device. No ads, no analytics, no Google Play Services.

## Contents

- [Features](#features)
- [Supported sites](#supported-sites)
- [Installation](#installation)
- [Privacy](#privacy)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Releases](#releases)
- [Disclaimer](#disclaimer)
- [License](#license)

## Features

**Browsing**
- Every source in its own folder: switch with a swipe, reorder or hide folders, see how many posts are new since your last visit.
- Sorting: new, hot, best, most favorited, popular by day, week, month or year, largest, landscape, portrait and random.
- Tag search with autocomplete. When a free account limits the number of tags, DudeBooru plans the query itself:
  the sort and the rarest tags go to the server, the rest are checked on the device.
- Parent and child posts are grouped into carousels.
- Negative tags for one source or all of them; hidden posts can be counted and revealed.

**Viewer**
- Pinch and double-tap zoom with tiled decoding for very large images; the original loads when you zoom in.
- Video player with a seekable progress bar, pause and sound. Turning the sound on pauses music playing in other apps.
- Videos and GIFs are marked differently in the feed and the grid.
- Swipe down to close, swipe up for details, and swipe right on any other screen to go back.

**Content and safety**
- Three content modes — safe, NSFW and all — with an 18+ confirmation for explicit content.
- Censoring as a spoiler, blur or pixelation with adjustable strength, in the feed and in the viewer.

**Collections**
- Likes, saved posts with folders, viewing history and downloads.
- Downloads in light or original quality, with a filename template and tags embedded as XMP metadata.
- Optional sync of likes and favorites with your account on the site.

**Recommendations**
- A taste profile built on the device from what you like and save, using only the tags that keep recurring.
- Each recommendation explains itself; “Find similar” works for any post.

**Notifications**
- Follow artists and get notified about new works (checked every 6 hours); mute a single artist with the bell.
- Notifications about new app versions, with in-app download and signature check.

**Personalization**
- Theme presets — Monet (dynamic color), Classic, AMOLED, Miku, Teto, Maid and Sakura — and a theme editor.
- Share themes as a file or a short code; set any post as the feed background.
- Automatic night mode by schedule or by sunset.
- Alternative app icons, including a neutral one.
- Russian and English interface.

## Supported sites

| Site | Engine | Sign-in | Notes |
|---|---|---|---|
| [Danbooru](https://danbooru.donmai.us) | Danbooru 2 | Login and API key | Free accounts allow 2 tags per search; DudeBooru works around the limit |
| [Safebooru](https://safebooru.donmai.us) | Danbooru 2 | Shared with Danbooru | Safe-only mirror of Danbooru |
| [Yande.re](https://yande.re) | Moebooru | Login and password | |
| [Konachan](https://konachan.com) | Moebooru | Login and password | Uses konachan.net in safe mode |

## Installation

1. Download the latest `DudeBooru-x.y.z.apk` from [Releases](../../releases).
2. Open it on your phone and allow installation from this source when Android asks.

Requires Android 8.0 or newer. Updates can be checked in **Settings → About** and are installed over the
current version.

Every release is signed with the same key. To verify an APK, compare the certificate fingerprint:

```
apksigner verify --print-certs DudeBooru-x.y.z.apk
```

```
SHA-256: 97c4df13bdc40fe0ba8211e0ea7f9addb5cf9004aa0377b501ded4c23451df58
```

The public certificate is in [`.github/release/signing-cert.pem`](.github/release/signing-cert.pem).

## Privacy

- Everything stays on your device: likes, history, downloads and your taste profile. DudeBooru has no server of its own.
- The app talks only to the sites you use and, for update checks, to GitHub.
- API keys and passwords are encrypted with the Android Keystore.
- Optional app lock with fingerprint, face or device PIN, and screenshot blocking.
- DNS over HTTPS (Cloudflare, Google, Quad9, AdGuard) and a custom proxy for networks where sites are blocked.

## Building from source

Requirements: JDK 21 and the Android SDK (platform 37).

```
./gradlew :app:assembleDebug     # debug APK
./gradlew :booru:test            # unit tests
```

Tests against the live sites are skipped by default:

```
./gradlew :booru:test -Plive --tests '*LiveApiTest*'
```

If the sites are not reachable directly, the tests use the proxy from `HTTPS_PROXY`.

A build without the update check (for F-Droid and similar stores):

```
./gradlew :app:assembleRelease -PnoUpdateCheck
```

## Project structure

| Module | Contents |
|---|---|
| `booru` | Pure Kotlin library: Danbooru and Moebooru engines, rate-limited networking, tag-limit query planning, carousel grouping, blacklist, recommender, filename templates, XMP, release checks. Tested on the JVM. |
| `app` | The Android app: Jetpack Compose (Material 3), Room, DataStore, WorkManager, Media3, Coil. |

## Releases

GitHub Actions builds and tests every push (`CI`, debug APK in the artifacts). A commit with `[release]`
in its message builds the version from `app/build.gradle.kts`; release notes live in
`.github/release/notes/<version>.md`.

Signing works in one of two ways:

- **With the `DUDEBOORU_SIGNING_KEY` secret** (a P-256 private key, hex or PEM), the release is signed and published right away.
- **Without the secret**, the key never leaves its owner. The build prints the APK content digest; the owner signs it
  (`python3 .github/scripts/apk_v2.py signed-data <digest> .github/release/signing-cert.pem data.bin`,
  then `openssl dgst -sha256 -sign key.pem -out sig.der data.bin`), commits
  `.github/release/signatures/<version>.json` with `version`, `run_id`, `commit` and `signature` (hex),
  and pushes a commit with `[publish]`.

For a local signed build, put `keystore.properties` in the project root (it is ignored by git):

```
storeFile=release.p12
storePassword=…
keyAlias=dudebooru
keyPassword=…
```

## Disclaimer

DudeBooru is an independent client. It is not affiliated with or endorsed by Danbooru, Safebooru,
Yande.re or Konachan. All images and tags belong to their authors and the respective sites. Some content
on these sites is intended for adults only; explicit content is hidden until you confirm you are 18 or older.

## License

Copyright © 2026 RHDude

DudeBooru is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License](LICENSE) as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version.

DudeBooru is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
for more details.

Third-party libraries are used under their own licenses (Apache 2.0); the full list is in the app under
**Settings → About → Open source licenses**.
