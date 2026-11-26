# Substratum Reborn

**True black for every app on One UI 9, with a studio to see it before you apply it.**

Substratum Reborn brings the Substratum theme engine to rooted Galaxy phones on One UI 9
(Android 17). Its flagship is AMOLED Studio: pick a look, compare it on a live before and after
preview drawn with the engine's real colours, apply it in one tap, and inspect every colour it
changed in every app. Everything applied shows in one place, with one Undo.

<!--
  Screenshot slots. The images are not in the repository yet: they are captured in demo mode and
  reviewed by the owner first (docs/showcase/SHOTLIST.md lists the shots and the capture rules).
  TODO: uncomment once docs/showcase/home.png, studio.png and inspector.png are reviewed and added.

<p align="center">
  <img src="docs/showcase/home.png" width="31%" alt="Home: system status and the See the difference carousel">
  <img src="docs/showcase/studio.png" width="31%" alt="AMOLED Studio: looks, accent and the live before and after preview">
  <img src="docs/showcase/inspector.png" width="31%" alt="Inspector: every colour an app changes, stock to AMOLED">
</p>
-->

## What it does

| Feature | What you get |
|---|---|
| **AMOLED Studio** | Looks (Pure black, Elevated cards, Dim), a live before/after preview of Quick Settings and Settings drawn from the engine's computed colours, one-tap Apply with progress you can watch |
| **Levels and accent** | Pure, Dim, Elevated or Tinted surfaces; accent none, Monet (the wallpaper's), One UI blue, white or any colour; optionally in light mode too |
| **Per-app inspector** | Every covered app with search, filters, its route (native overlay or module) and a *Restart needed* state; each colour that changed, stock to AMOLED, and the rule that decided it; long-press to keep a colour stock or pin your own |
| **Applied and Undo everything** | Compiled theme overlays, AMOLED and Quick Settings tweaks in one list; swipe one off, or put everything back to stock in one go |
| **Quick Settings designer** | Opaque shade, near-black slider tracks, the active tile colour and a compact row height, carried out in System UI by the module |
| **Themes** | Full-width cards and one-tap Apply, install a theme from an APK file, rules themes open in the Studio; compatible legacy Substratum themes still work, and Classic Substratum is one tap away |
| **Safety** | A kill switch, a crash-loop guard for System UI and every hooked app, no restarts of the phone, Bluetooth or keyboard processes, boot restore, and snapshots on Remove |
| **Engine** | Fabricated overlays + LSPosed: cached, batched and flicker-free, and apps you update are re-themed on their own |

Measured on a Galaxy A36 with 229 apps: a first apply takes about 100 seconds, and a re-apply when
nothing changed a few seconds (the classic engine, still selectable, needs about four minutes).

## Requirements

- **One UI 9 / Android 17.** Tested device: **Galaxy A36**.
- **Root.** KernelSU is tested; Magisk and APatch should work but are untested.
- **LSPosed** with the Substratum Reborn module enabled. Its scope needs **System UI**,
  **Settings** and **Substratum Reborn** itself, plus the apps AMOLED Studio marks *Needs scope*
  (Play Store and other Google apps, and apps that refuse overlays).

## Setup

1. Install the APK (`./gradlew :app:assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`;
   it needs the Android SDK with platform 37.1 and a recent JDK).
2. Open Substratum Reborn and grant it root in the KernelSU manager.
3. In LSPosed, enable the module and add System UI, Settings and Substratum Reborn to its scope.
4. Follow the **Setup checklist** that opens on the first launch: four live checks (root, module,
   scope, System UI hooked), each with a Fix button, and unrestricted battery use as a
   recommendation so long applies finish with the screen off.
5. Open **AMOLED Studio**, pick a look and tap **Apply**. Apps marked *Needs scope* go into the
   module's scope; the Studio's filter lists them.

## How it works

```mermaid
flowchart LR
    Studio["AMOLED Studio<br/>look, accent, your rules"] --> Queue["Job queue<br/>foreground service"]
    Watch["Auto re-theme<br/>updated apps"] --> Queue
    Queue --> Compute["Compute<br/>each app's dark colours<br/>through the rules"]
    Cache[("Per-app cache")] -.-> Compute
    Compute -->|"colours by name"| Fabricated["Fabricated overlays<br/>cmd overlay fabricate, native"]
    Compute -->|"obfuscated or overlay-refusing apps"| Module["LSPosed module<br/>colours by resource id"]
    Fabricated --> Apps["Apps and System UI"]
    Module --> Apps
    Boot["Boot"] -->|"restore saved definitions"| Fabricated
```

- **Compute.** For every app the rules cover, the engine reads the dark-mode value each colour
  actually resolves to and decides what it becomes: surfaces go black (or to the level's step),
  text, icons, accents and light mode stay untouched.
- **Fabricated overlays (native).** Most colours are registered as night-only fabricated
  overlays with root. They are resolved by the system itself, apply live and need no overlay APK.
  Their definitions are kept in `/data/system/substratum` and registered again at every boot.
- **The LSPosed module (by id).** Apps with obfuscated resource names or that refuse shell
  overlays get their colours inside their own process, by resource id, keyed to the app version
  they were read from. The module also draws what One UI 9 paints in code (the Quick Settings
  shade, slider tracks, row height and the battery).
- **The cache and the auto re-theme job.** Each app's result is cached, so a re-apply only reads
  what changed; a periodic job re-themes apps after they update.

The engine, its files and the rules format are documented in
[docs/oneui9/README.md](docs/oneui9/README.md).

## If something goes wrong

Mildest first; none of these needs a reinstall.

1. **Pause hooks** (Home, Quick actions). The kill switch: the module installs no hooks anywhere
   and System UI restarts stock. **Resume hooks** brings everything back.
2. **Kill switch by hand**, from a root shell, if the app cannot open:
   `touch /data/resource-cache/substratum/hooks.off`, then restart System UI.
3. **Reset the crash-loop guard.** After repeated crashes the module leaves System UI (or an app)
   stock and Home shows *Guard tripped*; its Fix clears the guard and restarts System UI. By hand:
   `rm -f /data/user_de/0/com.android.systemui/reborn_guard_*`.
4. **Undo everything** (Home or Applied): AMOLED, theme overlays and Quick Settings tweaks back to
   stock, with a snapshot of the AMOLED colours kept.
5. **Disable the module** in LSPosed (Modules, Substratum Reborn off), then restart System UI.
6. **Full reboot**, the last resort: with temporary root, a full reboot drops root and LSPosed
   for that boot, so nothing of Reborn loads; set root up again afterwards.

The commands for each step are in [docs/oneui9/README.md#recovery](docs/oneui9/README.md#recovery).

## FAQ

**Why do the Play Store and Google apps need the module in their scope?**
Their resource names are obfuscated, or they refuse overlays from the shell, so no overlay can
reach their colours by name. The module applies them by resource id inside the app's own
process, and it can only do that in apps that are in its LSPosed scope.

**Why not overlay APKs, like classic Substratum?**
One UI 9 skips overlay APKs that Samsung did not preinstall, and Android rejects overlays signed
with another key. Fabricated overlays, registered with root, are resolved natively without an
APK; the module covers what they cannot carry.

**Does it change light mode?**
No. AMOLED colours are night-only; where an app's light value is a fixed colour, the stock light
value is registered next to it, and the wallpaper (Monet) colours are never touched.

**What happens when an app updates?**
Its colours are keyed to the version they were read from, so an update never paints black text
on black: the app shows stock until the auto re-theme job reads it again, usually within the
hour, or at once from the Studio's *Re-apply changed apps only*.

**Will my old themes work?**
Compatible legacy Substratum themes install from their APK and apply through fabricated
overlays and the module. Themes that ship AMOLED rules open in AMOLED Studio.

## Credits and licence

Substratum Reborn is based on [Substratum](https://github.com/substratum/substratum) by
Projekt Substratum, licensed under the GNU General Public License v3.0. Substratum Reborn is
free software under the same licence, version 3 or later: see [LICENSE.txt](LICENSE.txt). The
app lists the open-source libraries it uses, with their licences, under About.
