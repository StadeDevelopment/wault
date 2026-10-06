# Wault

Wault is a serverless password manager. Every secret is encrypted on your own device with a key derived from your master password, and nothing is ever uploaded — there is no account to create, no server to trust, and nothing in the middle to breach.

When you have more than one device, they sync **directly to each other** over Tor onion services or the local network, using the same peer-to-peer transport that carries [Stade](https://stade.dev) messages. Your vault never exists anywhere except on hardware you own.

Built with Kotlin Multiplatform and Compose Multiplatform, Wault runs natively on **Android**, **Windows**, **macOS**, and **Linux** from a single shared codebase.

## Core principles

- **No servers.** Vaults sync device-to-device. There is nothing to breach, subpoena, or go down.
- **No accounts.** Your vault is identified by a key you generate, not a login someone issues you.
- **Nothing readable at rest.** The database holds ciphertext and nothing else — not even item names.

## Security

- **Argon2id** derives a key-encryption key from your master password, with memory-hard parameters tuned per platform. That KEK wraps a random 256-bit data key; the data key is what actually encrypts your items.
- **Per-item envelope encryption.** Every item gets its own key via `HKDF(dataKey, itemId)` and is sealed with ChaCha20-Poly1305. The item id, revision, and key generation are bound in as associated data, so a record cannot be silently swapped, rolled back, moved onto a different entry, or replayed across a key rotation.
- **The data key never touches disk unwrapped.** It exists only in memory, only while unlocked, and is zeroed on lock. Callers reach it through a scoped `withDataKey { }` rather than holding a reference.
- **Encrypted metadata.** Titles, usernames, URLs, folders and item types all live *inside* the encrypted payload. What remains in plaintext on disk is only what synchronisation arithmetic requires: an opaque id, a revision counter, and a timestamp.
- **Recovery key.** A one-time 160-bit key, shown once at setup, that independently unwraps the data key. Losing your master password does not have to mean losing your vault.
- **Duress password.** A second password that wipes the device instead of unlocking it.
- **Escalating lockout** after repeated wrong master passwords, persisted across restarts.
- **Auto-lock** on leaving the app — immediately, or after a chosen delay — plus sensitive-clipboard flagging and a clipboard that clears itself after 30 seconds.
- **Biometric unlock** on Android, with the master password wrapped under a Keystore key that requires a fingerprint and is invalidated the moment your enrolled biometrics change. If it is set up, Wault asks for it the moment you open the app.

## Autofill

Wault fills logins two ways, because one is not enough:

- **The Android Autofill Framework**, for apps that support it.
- **An accessibility-based overlay**, for everything that doesn't — above all browsers, since Chrome for Android ignores third-party autofill services. Focus a login field and a small Wault tile appears under it; tap it, unlock, pick an entry, and both fields fill.

The accessibility service reads only what it needs to recognise a login field — whether a field is editable, whether the platform flags it as a password, its hint and resource id, and where it sits on screen. It never reads what you have typed, nothing is recorded, and nothing leaves the device. The only text it ever writes is the credential you explicitly picked. It is off until you turn it on.

## Sync

- **Device pairing is face-to-face.** One device shows a QR code carrying its public keys and a pairing nonce; the other scans it. Both then display a six-digit short authentication string derived from the agreed session key — if the numbers differ, someone is in the middle.
- **The data key is handed over sealed** under that verified session, so a paired device holds the same vault rather than a copy of it. Each device wraps that key under its own locally-salted KEK.
- **Every sync frame is authenticated by a ratchet.** Pairing also seeds a hybrid post-quantum Double Ratchet — X25519 mixed with **ML-KEM-768** at every step — so only a device you actually paired with can produce a frame the other end will open, and replayed frames are rejected. Note that this authenticates the *channel*; it does not give the payloads forward secrecy, because both devices share a long-lived data key. Rotating that key is what limits exposure, and revocation does exactly that.
- **Conflicts resolve deterministically.** Every record carries `(revision, updatedAt, updatedBy)` and merges last-writer-wins with a fixed tiebreak order, so two devices that see the same edits in different orders land on the same result.
- **Revoking a device rotates the vault key.** Cutting off replication is not enough on its own — a revoked device still holds a key that opens everything it ever saw. Revocation generates a fresh data key, re-encrypts every record under it, and hands the new key to your remaining devices over their existing sessions. The revoked device is left on a generation that no longer decrypts anything new.

## Platforms

| Platform | Package format |
|---|---|
| Android | native APK |
| Windows | `.exe` installer |
| macOS | `.dmg` |
| Linux | `.deb`, `.rpm` |

## Building

You need **JDK 17 or newer**. For Android you also need the Android SDK, pointed at by `local.properties` in the repository root:

```properties
sdk.dir=/path/to/Android/sdk
```

```bash
./gradlew :composeApp:compileKotlinDesktop        # desktop
./gradlew :composeApp:compileDebugKotlinAndroid   # Android
./gradlew :composeApp:run                         # run the desktop app
./gradlew :composeApp:desktopTest                 # tests (JVM, no device)
./gradlew :composeApp:connectedDebugAndroidTest   # startup smoke test, needs a device
```

The JVM suite covers the crypto, vault, merge, and sync logic. It cannot catch anything that only breaks inside the Android runtime, so the connected test launches the real activity and checks the database lands where the vault says it does — **run it before claiming an Android change works.**

The first build that packages or runs the app downloads the Tor binaries that ship inside it, one per architecture — around 400 MB on disk, once. Every archive is checked against a pinned SHA-256 and the build fails on a mismatch. While you are only compiling or running tests you can skip it with `-x downloadTorBinaries -x downloadAndroidTorBinaries`.

## What Wault deliberately doesn't do

- **No cloud sync, no accounts, no directory.** Devices find each other over Tor or the LAN, or not at all.
- **No analytics, telemetry, or crash reporting.** Nothing about how you use Wault leaves your device.
- **No breach-database lookups by default.** Checking a password against Have I Been Pwned means talking to a server, which is a change to the threat model rather than a feature. If it is ever added it will be opt-in and routed over Tor.

## Status

Early, but the spine is real and tested (194 JVM tests plus an on-device startup check). The vault, per-item encryption, item model, generator, strength meter, TOTP, health analysis, merge engine, Double Ratchet, key rotation, and the full sync exchange are implemented — the test suite pairs two complete stacks and syncs real items between them over a pipe.

Pairing works end to end: show a QR on one device, scan it on the other (camera on Android, paste anywhere), compare six digits, done. Both autofill routes, folders, CSV import and biometric unlock are built and verified on a device — accessibility fill against a real Chrome login page. The Tor and LAN transports are ported and compile, but can only be verified on two real devices. See [ARCHITECTURE.md](ARCHITECTURE.md) for exactly what is and is not done.
