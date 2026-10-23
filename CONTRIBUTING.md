# Contributing to Tellyscope

Thanks for helping! Tellyscope is in alpha, so the code and its structure are still moving.

## Ground rules

- **Read the [safety rules](README.md#safety-rules) first.** A pull request that uninstalls packages, bypasses the protected list or skips recording what was already off won't be merged.
- **Open pull requests against `dev`.** `main` is the release branch and only takes `dev`.
- **One change per pull request.** Smaller is easier to review.
- **`./gradlew build` must pass** before you open a PR. CI runs the same command.
- **New files start with an SPDX header:** `// SPDX-License-Identifier: GPL-3.0-only`.
- **Licensing.** By contributing you agree that your work is licensed under GPL-3.0. If you adapt code or lists from another project, check that its license is compatible and add it to [NOTICE](NOTICE) in the same PR.

## Releases

- **Alpha:** merging into `dev` runs CI and publishes nothing. To put out an alpha, tag a commit that is on `dev` with `vX.Y.Z-alpha.N`, where `X.Y.Z` is the current `versionName`, and push the tag. For example, with `versionName` 0.0.1: `git tag v0.0.1-alpha.1 origin/dev && git push origin v0.0.1-alpha.1`. CI builds `app.tellyscope.alpha`, which installs next to the release, and publishes it as a GitHub pre-release under that tag, named `Tellyscope-vX.Y.Z-alpha.N.apk`. The tag's `X.Y.Z` must equal `versionName` in `app/build.gradle.kts`, and its commit must be on `dev`; otherwise the build fails and publishes nothing.
- **Shipping:** a release is a pull request from `dev` into `main`, merged with a merge commit. It must raise `versionName` and `versionCode` in `app/build.gradle.kts` past the latest `v*` tag and turn the `## Unreleased` section of [CHANGELOG.md](CHANGELOG.md) into that version's section; CI refuses anything else. Merging it tags `vX.Y.Z` and publishes a GitHub release with the signed APK, notes from the changelog section and the commits since the previous tag.
- **Signing:** CI signs both builds from three repository secrets: `RELEASE_KEYSTORE_BASE64` (the keystore, base64-encoded), `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, which is both the store and the key password (keytool's PKCS12 default uses one for both). Only the maintainer holds the key; never commit a keystore. Local release builds are unsigned.

## Debloat lists

The per-TV lists behind **Suggested for this TV** live in the [tellyscope-devices](../../../tellyscope-devices) repository, not here; add or fix a list there. When you add a package to a list:

- Say which TV models you tested on, and what changed after disabling it.
- If disabling it broke anything (launcher, updates, remote, input, HDMI-CEC), put it in the list's keep entries, or propose it for the built-in protected list (`adb/src/main/kotlin/app/tellyscope/adb/Protected.kt`) here.

## Style

- Kotlin official code style, as set up in `.editorconfig`.
- Logic that doesn't need Android goes in `:adb`, with unit tests.
- UI uses Material 3 Expressive components and the app theme. No hard-coded colors.

## Reporting a TV

Open a [device report](../../issues/new?template=device_report.yml). Leave out serial numbers, IP addresses and account names.
