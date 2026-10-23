# Changelog

What changed in each Tellyscope release, newest first. Each entry is written
for the people using the app. Headings are `## <version> — <date>`, where
`<version>` is the release's `versionName`. One line per change:
**New:**, **Improved:** or **Fixed:**, then a plain line of at most about
twelve words, ending with a link to its PR.

Changes merged into `dev` collect under `## Unreleased`. A release renames
that heading to its version and date. A pull request into `main` fails CI
until this file has a section for its version.

## Unreleased

- **New:** Uninstall apps you installed yourself; apps that came with the TV can only be disabled. [#33](../../pull/33)
- **Improved:** Apps that can't be changed fold into their own section below the list. [#33](../../pull/33)
- **Fixed:** App icons that failed to load once now come back, with a fallback to the banner. [#34](../../pull/34)
- **New:** Block ads on the whole TV with Private DNS, AdGuard by default. [#35](../../pull/35)
- **New:** Faster animations: menus move at twice the speed. [#35](../../pull/35)
- **New:** Clear every app's cache in one tap; app data stays. [#35](../../pull/35)
- **New:** Save a screenshot of the TV to your phone. [#35](../../pull/35)
- **New:** Allow or deny another device's connection request from your phone. [#36](../../pull/36)

## 0.1.0 — 2026-10-23

- **New:** A new app icon: a telescope with a TV screen for a lens. [#28](../../pull/28)
- **New:** Install apps on the TV from an APK, split APKs or a bundle. [#9](../../pull/9)
- **New:** Send files to the TV's Download or Movies folder, and save them back. [#9](../../pull/9)
- **New:** Suggested apps to disable, from a tested list for your TV. [#11](../../pull/11)
- **Fixed:** Processes, screen state, connections and data used now show on real TVs. [#17](../../pull/17)
- **Fixed:** A TV on Wi-Fi no longer shows its connection as Ethernet. [#17](../../pull/17)
- **New:** An icon shows whether each device is a TV, projector, Fire TV or stick. [#18](../../pull/18)
- **Improved:** The confirm before disabling suggested apps names every app. [#19](../../pull/19)
- **Improved:** System updaters, Wi-Fi resources and Chrome's library are now protected. [#19](../../pull/19)
- **Fixed:** Done and error messages stay on screen long enough to read. [#20](../../pull/20)
- **Fixed:** Undo and results always go to the TV the action ran on. [#20](../../pull/20)
- **New:** Debloat in two steps: pick your TV, then review every app it turns off. [#23](../../pull/23)
- **Improved:** The TV page leads with the TV, its vitals and big action buttons. [#23](../../pull/23)
- **Fixed:** Adding a TV with a port above 65535 shows a message, not a crash. [#25](../../pull/25)
- **Fixed:** Losing Wi-Fi while pairing shows a message, not a crash. [#25](../../pull/25)
- **Fixed:** A broken suggestions list can no longer crash the app. [#25](../../pull/25)
- **Improved:** Undoing a Turn on in History asks before disabling the app. [#25](../../pull/25)
