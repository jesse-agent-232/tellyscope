# Tellyscope

See inside your Android TV from your phone. Live stats, processes, network and one-tap reversible debloat over Wi-Fi ADB. No root, no PC.

> **Status: pre-alpha.** The app builds and opens an empty screen. Nothing connects to a TV yet. Don't install it expecting it to do anything.

## What it will do

Tellyscope is a phone app that talks to your Android TV or Google TV over the network using ADB, the same debugging protocol developers use. Once connected, it shows you:

- **Live stats**: CPU, memory, storage and temperature.
- **Processes**: what's running and what's eating resources.
- **Network**: interfaces, addresses and traffic.
- **Debloat**: turn off preinstalled apps, ads and trackers you don't want, and turn them back on with one tap.
- **Install apps**: pick an APK, a set of split APKs or an `.apks`/`.xapk` bundle on your phone and install it on the TV. It tells you first whether it's new or an update, and won't put an older version over a newer one unless you tick the box.
- **Files**: send a file from your phone into the TV's Download or Movies folder, or save one from there to your phone. Nothing on the TV is overwritten.
- **Remote**: D-pad, Back, Home, volume and play/pause from the phone, typing into the TV's search boxes, and Sleep or Restart.

It connects either through classic network debugging (port 5555) or through Android 11+ wireless debugging with a pairing code.

## Safety rules

Debloating a TV can break it if done carelessly. Tellyscope is built around these rules, and contributions must keep them:

1. **Disable only, never uninstall.** Tellyscope uses `pm disable-user`, which is reversible from the app or with `pm enable`. It never runs `pm uninstall`, not even for the current user.
2. **Backup first.** Before the first change on a TV, Tellyscope records the list of enabled packages, so every change can be undone, even after an app reinstall.
3. **Protected list.** Packages the TV needs to boot, update, show the launcher or accept input are on a protected list. Tellyscope refuses to disable them, and there is no override in the app.
4. **No root, no exploits.** Only what ADB allows on a stock TV.
5. **Nothing left on the TV.** To show real app names and icons, Tellyscope copies a small helper to `/data/local/tmp`, runs it once and deletes it. The helper only reads through PackageManager: no writes, no permissions, no network.

## Project layout

| Module | What it is |
|---|---|
| `:app` | The Android phone app. Kotlin, Jetpack Compose, Material 3 Expressive. Package `app.tellyscope`. |
| `:adb` | Pure Kotlin/JVM library on top of [Kadb](https://github.com/flyfishxu/Kadb): connecting, pairing and running commands on a TV. Unit-testable without a device. |
| `app/src/helper` | The label helper: plain Java, built into a dex in the app's assets and run on the TV with `app_process`. |
| `:cli` | Desktop smoke test for `:adb` against a real TV (`./gradlew :cli:run --args="stats <tv-ip>"`). Not shipped. |

## Building

Requirements:

- **JDK 25.** Kadb ships Java 25 bytecode, so `:adb` tests need a Java 25 runtime. `:adb` itself still compiles to Java 17 bytecode so the app can use it.
- **Android SDK** with platform 37. Set `ANDROID_HOME` or put `sdk.dir=...` in `local.properties`.

```sh
./gradlew build          # assemble, unit tests and lint
./gradlew :app:installDebug
```

The app supports phones on Android 8.0 (API 26) and newer.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). If you have an Android TV, a [device report](../../issues/new?template=device_report.yml) helps even if nothing worked.

## License

Tellyscope is licensed under the [GNU General Public License v3.0](LICENSE). Third-party credits are in [NOTICE](NOTICE).
