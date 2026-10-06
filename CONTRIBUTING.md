# Contributing to Wault

Thanks for taking an interest in Wault. This file explains how to get the project running, what we expect in a pull request, and which changes need a conversation before you spend time on them.

Wault is built by the same people as [Stade](https://stade.dev) and follows the same conventions. If you have contributed there, none of this will surprise you.

## Everything is in English

Wault is developed in English. That means **code, identifiers, commit messages, pull request titles and descriptions, and issues** — all of it in English, regardless of the language you and the maintainers happen to share.

**Names that belong to Wault are never translated.** Wault and Vault (the screen) are product names rather than descriptions, so they stay in Latin script in every locale — neither translated nor transliterated.

The exceptions to English are the obvious ones: translated interface strings live in the locale files, a language's own name stays in its own language, and test fixtures may contain whatever text the test is actually about.

## Getting it building

You need **JDK 17 or newer**. For Android you also need the Android SDK, pointed at by a `local.properties` file in the repository root:

```properties
sdk.dir=/path/to/Android/sdk
```

```bash
./gradlew :composeApp:compileKotlinDesktop        # desktop
./gradlew :composeApp:compileDebugKotlinAndroid   # Android
./gradlew :composeApp:run                         # run the desktop app
./gradlew :composeApp:assembleDebug               # debug APK
```

The first build that packages or runs the app downloads the Tor binaries that ship inside it, one per architecture. Expect around 400 MB on disk, once. Every archive is checked against a pinned SHA-256 before it is unpacked, and the build fails on a mismatch — so a download that does not match is a real problem, not something to work around.

While you are only compiling or running tests, `-x downloadTorBinaries -x downloadAndroidTorBinaries` skips it.

## Tests

```bash
./gradlew :composeApp:desktopTest                 # JVM, no device
./gradlew :composeApp:connectedDebugAndroidTest   # needs a device or emulator
```

The first is plain JVM tests with no emulator involved, and is what CI runs on every pull request. Run it before you push.

The second launches the real activity on a device. It exists because a green JVM suite says nothing about whether the app starts — SQLite, the framework, and the Compose runtime all behave differently there. **If you touched anything under `androidMain`, or anything the app touches during startup, run it.** Both targets compiling is not evidence the app runs.

If you are fixing a bug, **add a test that fails without your fix**. A test that passes either way still has value as a guard, but say so in the description rather than implying it reproduces the problem — we would rather know.

Anything that can only be checked by hand (an animation, a QR scan, a fingerprint prompt) should say so explicitly, along with what you did check.

## Before you open a pull request

- **Both targets have to compile.** Most of the code is shared, and it is easy to change common code in a way that only breaks one platform.
- **Keep it to one subject.** A bug fix and a refactor in the same pull request take much longer to review than two pull requests.
- **Match the surrounding code.** Naming, formatting, and structure should look like the file you are editing.
- **No code comments.** This is deliberate and near-universal in the codebase. Prefer a well-named function or variable over a comment explaining an unnamed one. If something genuinely cannot be made self-explanatory, put the explanation in the pull request description or in `ARCHITECTURE.md`, where reviewers will read it and it cannot drift out of date.

## Adding or changing interface text

Never hard-code text that a user will see. Every string goes through the localization layer:

1. Declare it in `AppStrings.kt`.
2. Add the English text in `EnglishStrings.kt`.
3. Add it to **every** other locale file in the same directory. The build will not compile until all of them have it.

If you cannot translate into a language, a machine translation clearly flagged in the pull request description is fine. We would rather know it needs review than discover it later.

## Changes that need a discussion first

Open an issue before writing code if your change touches any of these. It is not bureaucracy — these areas are load-bearing for the guarantees the app makes, and a change here can be correct code that we still cannot take.

- **Cryptography** — key derivation, the item envelope, the pairing handshake, the vault format. In particular: anything that would cause a secret to be written to disk unencrypted, however briefly, will be declined.
- **The merge rules.** Conflict resolution has to converge, and a change that is right on one device and wrong on the other loses people their data.
- **Transport** — Tor, the local network, connection management. Much of this cannot be verified without two real devices, so unverifiable changes here are held to a higher bar.
- **Anything that contacts a server.** Wault has none, and a new outbound endpoint is a change to the threat model rather than a feature. This includes breach-database lookups, favicon fetching, and update checks.
- **New dependencies**, especially ones with their own network behaviour.
- **Database schema.** Existing installs are migrated in place; a change that only works on a clean install will lose people their vaults.

## What we will not merge

Some things are deliberately absent, and a pull request adding them will be declined however well it is written:

- **Analytics, telemetry, or crash reporting that leaves the device.**
- **Cloud sync, accounts, or a central directory.** Devices sync to each other or not at all.
- **Any feature that uploads vault contents anywhere**, including "encrypted backup to our server".
- **Unauthenticated sync.** Every paired device is verified face-to-face; a pairing flow without a compared short authentication string is a machine-in-the-middle waiting to happen.

If you think one of these deserves reconsidering, open an issue and make the case there. Do not open it as a pull request.

## Reporting a security problem

Do not open a public issue for a vulnerability. Contact us privately and give us a chance to ship a fix first.
