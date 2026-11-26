# Theming One UI 9 with SubstratumReborn

One UI 9 (Android 17) skips overlay APKs that Samsung did not preinstall, and Android rejects
overlays signed with another key. SubstratumReborn therefore applies themes in two layers:

- **Colors, booleans and integers** become a *fabricated overlay* in the system Overlay Manager
  (root). They are resolved natively, so they also reach colors the app only uses through
  references or inside its own drawables, they follow qualifiers such as `values-night`, apply
  live without restarting the app, and framework values apply system-wide.
- **Everything else** (dimensions, drawables, layouts) is applied by SubstratumReborn's LSPosed
  module, which swaps the matching resources inside each target app as it starts.

Themes are still ordinary Substratum themes; the split happens when Substratum builds them.

## What you need on the device

- Root: KernelSU, Magisk or APatch, with Substratum granted root.
- LSPosed, with the **substratum** module enabled. Its scope must include Substratum itself and
  every app your theme targets. SystemUI and Settings are in the default scope; add the others.
- Reborn's Home says in plain words whether root, the module, its scope and the System UI hooks
  work, and the Setup checklist (first launch, or Home's checklist button) fixes each of them.

## Theme layout

A theme is an app with a `SubstratumLauncher` activity and its overlays in assets:

```
assets/overlays/<target package>/res/values/colors.xml
assets/overlays/<target package>/res/values-night/colors.xml
assets/overlays/<target package>/res/drawable/...
assets/overlays/<target package>/res/layout/...
```

`themes/amoled` in this repository is a complete, minimal example (Reborn AMOLED). Its manifest
needs `Substratum_Name`, `Substratum_Author` and `Substratum_Plugin` metadata. Give
`Substratum_Plugin` a value that is not a plain number (for example `15.0.0`), or Android stores
it as a number and Substratum skips the theme.

## What can be themed

| Kind | Works | Notes |
|---|---|---|
| Colors, booleans, integers | Yes, natively | Declare the same name in `res/values`; hex colors only (`#AARRGGBB`), references fall back to the module |
| Dimens, strings | Yes | Declare the same name in `res/values` |
| Drawables, vector or bitmap | Yes | Ship a file with the same name and qualifier |
| Layouts | Yes | Ship a file with the same name; keep every view id the code looks up |
| Framework (`android`) | Yes, in scoped apps | Applies in every app in the module's LSPosed scope |
| Styles, attrs | No | Change the colors and dimens they use instead |
| The status bar battery | Yes, through a hook | See [Battery](#battery) |

## Rules that keep the target from crashing

1. **Night variants need a light value.** If a resource exists in `values-night` of the target,
   a theme that overrides it in `values-night` must also declare it in `values`, or the target
   finds nothing in light mode and crashes. The samples already contain the stock light line.
2. **Only replace names the target has.** New names are ignored, except the battery names below.
3. **Use literal values or `@android:` references.** References to the target's own resources
   (`@*com.android.systemui:color/...`) do not resolve from a theme.
4. **Override the colors the app looks up, not only the end of a chain.** One UI colors are
   chains (`search field -> surface_container -> surface_container_dark -> gray_80`). Fabricated
   overlays handle chains natively; for values the module applies, override the name the app
   actually uses.
5. Restarting a target over and over trips LSPosed's crash detection, which then stops all
   modules (safe mode). Substratum restarts each target once per action.

## Design tokens

Most One UI 9 system apps share a palette of design tokens. Overriding them themes all of those
apps the same way:

- roles such as `basic_token_sys_background_dark` and `basic_token_sys_surface_container_light`,
  in `_light` and `_dark` versions;
- the palette they point to: `basic_token_gray_*`, `basic_token_primary_*`, `basic_token_red_*`,
  `basic_token_green_*` and `basic_token_orange_*`.

These names have no night variant, so they go in `res/values/` and rule 1 does not apply.
[`samples/design_tokens.xml`](samples/design_tokens.xml) lists all 208 with their stock values;
copy it into each app's overlay and keep the lines you change.

Apps that use the tokens on One UI 9: SystemUI, Settings, Settings suggestions, Accessibility,
Always On Display, Contacts, Phone, In-call UI, Call settings, Samsung Keyboard, One UI Home,
My Files, Gallery, Secure Folder, Galaxy Themes, Device care, Digital wellbeing, Smart View,
Weather, Software update and DressRoom.

## Samples

[`samples/overlays`](samples/overlays) is laid out like a theme's `assets/overlays` folder:

- `com.android.systemui`: QS panel, QS tiles, notifications, brightness slider, popups
  (`values-night` plus the matching light lines in `values`), QS tile and notification sizes,
  status bar spacing and clock size (`values/dimens.xml`), and a themed battery (`drawable/`).
- `android`: the framework's dark backgrounds and Material 3 surface roles.

Every value in the samples is the stock One UI 9 value.

## Rules themes (AMOLED for every app)

A theme can also be a set of **rules** instead of a list of overlays. SubstratumReborn then looks
at every installed app that the rules cover, reads the app's own dark-mode colors on the device,
and turns them into a night-only fabricated overlay. It works for whatever apps each user has
and on any One UI release, with nothing to regenerate. Tapping such a theme opens AMOLED Studio
(the AMOLED tab) with its rules instead of the overlay list; the work runs in the background
with a progress notification. The Studio uses the built-in rules when no rules theme is chosen.
[The AMOLED engine](#the-amoled-engine) describes the pipeline and where everything is kept.

A rules theme is an ordinary Substratum theme (manifest metadata, `SubstratumLauncher`, hero
image) with `assets/amoled/rules.json`. `themes/amoled` (Reborn AMOLED) is the reference:

```json
{
  "background": "#000000",
  "floating":   "#000000",
  "outline":    "#1f1f1f",
  "blur":       "#ff000000",
  "slider":     "#1a1a1a",
  "thumb":      "#2a2a2a",
  "include":    ["^(android|com\\.(samsung|sec|google|android)\\..*)$"],
  "exclude":    [],
  "colors": {
    "com.android.systemui": { "qs_tile_round_background_on": "#ffffff" },
    "com.android.settings": { "^sec_dashboard_(?!.*(text|layer)).*_color\\d*$": "#262626" }
  }
}
```

| Key | What it sets |
|---|---|
| `background` | every dark surface: pages, cards, list rows, buttons, keyboard keys |
| `floating` | popups, 3-dot menus, dialogs, floating bottom bars, search fields |
| `outline` | the thin border that keeps floating elements visible on a black page |
| `blur` | tint over blurred panels (notification shade, QS); its alpha is how much blur shows |
| `slider`, `thumb` | slider and seekbar tracks, scrollbar thumbs |
| `level` | `pure` (default: every dark surface is `background`), `dim`, `elevated` or `tinted` |
| `ramp` | four colors for the levels other than `pure`: page, card, raised and container surfaces |
| `accent` | `none`, `monet` (the wallpaper's accent), `oneui`, `sprite`, `white` or a color |
| `accentLight` | the accent in light mode too (default `false`) |
| `qsTrack` | One UI 9 brightness and volume bar tracks, drawn by the LSPosed module (default `#10ffffff`) |
| `include`, `exclude` | regexes of package names to theme |
| `colors` | hand-picked values per package; keys are resource names, or regexes when they start with `^` |

Every key is optional; what a theme leaves out keeps Reborn AMOLED's value. The look chosen on
the AMOLED screen (preset, level, accent, active tile) overrides the theme's, and the user's own
rules (keep a color stock, pin one; `files/amoled/user_rules.json`) come before everything. Make a variant by
copying `themes/amoled`, changing the package name, and editing the values: a dark grey
`background`, a coloured `outline`, a different QS accent. The [reference](reference) shows the
resource names for `colors`.

### How colors are chosen

For every color of every covered app, the dark-mode value it actually resolves to (through One
UI's reference chains) and its resource name decide:

| Dark-mode value | Becomes |
|---|---|
| Text, icons, press/selection states, accents, content colors (`on_*`, `inverse*`) | unchanged |
| Light-theme variants (names ending in `_light` / `_day`) | unchanged |
| Any dark color (brightest channel up to `0x30`), any hue | `background` (`floating` for floating elements) |
| Dark neutral grey up to `0x52` (cards, containers, lines) | `background` (`floating` for floating elements) |
| Mid grey (palette tones such as `neutral1_500`), used for secondary text | unchanged |
| Outline of a floating element | `outline` |
| See-through white or tinted surface (up to 40% opacity) | transparent |
| See-through dark tint over blur | `blur` |
| Slider or track / scrollbar thumb | `slider` / `thumb` |

### Levels and accent

| Level | Page | Card | Raised | Container |
|---|---|---|---|---|
| `pure` | `background` | `background` | `background` | `background` |
| `elevated` | `#000000` | `#0B0B0B` | `#141414` | `#1C1C1C` |
| `dim` | `#0E0E0E` | `#141414` | `#1A1A1A` | `#222222` |
| `tinted` | `#000000` | elevated, 5% accent | elevated, 5% accent | elevated, 5% accent |

One UI's own grey ramp keeps its roles (`basic_token_gray_100` page, `_95` card, `_90` raised,
`_80`/`_70` container); other dark surfaces go by their brightest channel (up to `0x20`, `0x2c`,
`0x3b`, else). Floating elements take at least the raised step.

The accent recolors `basic_token_primary_01` to `_100`, `sesl_primary_color_dark` and
`sesl_primary_dark_color_dark` (everything else that shows One UI's accent references them), and
the active QS tile when its option is "accent". Each shade keeps its tone: Material's HCT with the
accent's hue, its chroma scaled by the shade's chroma over the app's `basic_token_primary_50`
(0 to 1.2), and the shade's tone.

Each night value is registered together with the app's stock light value when that is fixed (a
literal color, or references that end in one), so light mode never changes and idmap2 maps the
entry reliably. A light value that follows a theme attribute or the wallpaper (a reference chain
through the framework's `system_*` Monet roles) gets no light entry and keeps following its
source, a color state list a rule names keeps its light list, and the Monet roles themselves are
never touched. Colors are always read with the app's previous AMOLED overlay disabled, so running it
again gives the same result.

## Classic AMOLED theme (static overlays)

`themes/amoled-static` (Reborn AMOLED Static) holds the same result as classic Substratum
overlays, exported from a One UI 9 device with `tools/oneui-reference/fab_to_theme.py`: one
overlay per app, the stock light values in `res/values` and the AMOLED values in
`res/values-night`. It only covers the apps of the device it came from, so use it as a sample of
exactly which attributes an AMOLED theme changes. [`samples/amoled_attributes.csv`](samples/amoled_attributes.csv)
lists them in one sheet.

Google Play services is excluded from the LSPosed module (its code modules share resource ids
with it); rules themes do not use the module, so they theme it too.

## Battery

One UI 9 draws the status bar battery in code. A SystemUI overlay can replace it with:

- `drawable/substratum_battery`: drawn in place of the battery. Its level is the battery percent
  times 100 (0 to 10000, the standard drawable level), so `level-list` and `clip` drawables
  follow the charge.
- `drawable/substratum_battery_charging` (optional): used while charging.
- `bool/substratum_battery_tint` (optional, default `true`): tint with the status bar icon
  color, so the battery follows light and dark status bars.

Without `substratum_battery` the stock battery is drawn.

## Reference

[`reference`](reference) indexes every resource of the framework, SystemUI, Settings and the
Samsung apps: values with their defaults and qualifiers, and the layouts and drawables that exist.
See its README for the format. To rebuild it for another One UI release:

```
tools/oneui-reference/pull_and_build.sh -s <device> OUT android com.android.systemui com.android.settings
tools/oneui-reference/pull_and_build.sh -s <device> OUT --from-theme <installed theme package>
tools/oneui-reference/gen_token_sample.py OUT/reference/com.android.systemui/values/color.csv docs/oneui9/samples/design_tokens.xml
tools/oneui-reference/gen_samples.py OUT/reference docs/oneui9/samples/overlays
# after "AMOLED for every app" ran on the device:
adb -s <device> shell su -c 'cp -r /data/system/substratum /data/local/tmp/ && chmod -R 755 /data/local/tmp/substratum'
adb -s <device> pull /data/local/tmp/substratum OUT/definitions
tools/oneui-reference/fab_to_theme.py OUT/definitions themes/amoled-static/src/main/assets/overlays docs/oneui9/samples/amoled_attributes.csv
```

The reference records names and values only; it does not contain Samsung's files.

### One UI 9 quick settings: what needs the LSPosed module

One UI 9 draws parts of quick settings in Compose code with fixed values, which no overlay can
change. While AMOLED for every app is applied, the module (scoped to System UI) handles them:

| Part | Stock | With the module |
|---|---|---|
| Shade background | faded to a max alpha below 1, apps show through | fully opaque |
| Brightness / volume track | 15% white drawn twice (grey) | the rules' `qsTrack` (6% white, near black) |
| Panel row height | `cell width x 0.917` | `cell width x` the chosen value (for example `0.65`) |

All of it is set in AMOLED Studio's **Quick Settings designer**: panel row height, active tile
color (white, accent, blue, stock), solid shade and dark tracks; the Studio's app list has a
switch per app. The options live in
`/data/resource-cache/substratum/qs-options` (`aspect=0.650`, `shade=1`, `track=#10ffffff`, the
rules' `qsTrack`, or `track=0` for stock) and take
effect when System UI restarts, which the screen does for you. The tile box columns are
QuickStar's setting.

A rules theme can suggest a row height; it is used until the user picks one:

```json
"qs": { "aspect": 0.65 }
```

Apps whose resource names are obfuscated (Play Store) or that refuse overlays get their colors
from the module inside their own process, by resource id and, for Compose screens, by their
own dark surface colors where they are drawn. Add such apps to the module's scope. Each color
list is keyed to the app version it was read from: after an app update the app stays stock
until AMOLED for every app is applied again, so ids that moved never turn text black.

## The AMOLED engine

Reborn 2.0's AMOLED for every app, from a tap on Apply to colors in every app.

### Pipeline

1. **Job queue.** Every change is a job: Apply, Remove, one app again, an app switched on or
   off, the Quick Settings options, the restore at boot. A foreground service runs them one
   after the other (`AmoledJobs`, `SystemAmoledService`); a job started while another runs waits
   for it, and the screens follow the progress.
2. **Read.** The default engine (v2, `AmoledEngine`) reads each app's dark-mode colors without
   turning its overlay off (`AmoledStock`), four apps at a time: apps with an activity, plus the
   framework, System UI, keyboards and widget providers. The framework is read first, on its
   own, so every app reads it as it is applied. The classic engine (one app at a time, its
   overlay disabled while it is read) stays selectable in the Studio.
3. **Compute.** Each color goes through the rules, the look (level, accent, active tile) and the
   user's own rules, as [How colors are chosen](#how-colors-are-chosen) describes.
4. **Cache.** Each app's result is kept under a key made of the app version, the system build,
   the engine version, a fingerprint of the rules and the look, the wallpaper palette and the
   overlay APKs the app shows. An app whose key did not change is neither read nor registered
   again.
5. **Register.** Colors an app reaches by name become a night-only fabricated overlay,
   `com.android.shell:<package>.RebornSystemAmoled`, committed in batches of up to 16 apps with
   one root call each (`AmoledCommit`). Colors a shell overlay may not change (no
   `<overlayable>` declaration in an app signed with another key) are left out up front. Apps
   with obfuscated names, or that refuse overlays, get a module color list instead.
6. **Record.** Each app's changes are saved for the inspector and the Studio's preview, and the
   state file is updated under one lock with an atomic write.
7. **Restore.** At boot (`BootCompletedDetector`) the saved definitions are registered again
   while AMOLED is applied.
8. **Watch.** `AmoledWatchJob` (about every hour, and when the app opens) reads apps updated
   since their colors were read again, in one silent job with one notification. After a system
   update it reads nothing on its own and offers a full re-apply instead.

Measured on a Galaxy A36 with 229 apps: the v2 engine's first apply took 101.5 s with Pure
(31,433 colors) and 82.9 s with Elevated, a re-apply with nothing changed 3.9 s; the classic
engine took 229 s.

### Where everything is kept

| Path | What it holds |
|---|---|
| `files/amoled/state.json` | The single source of truth: applied or not, the rules theme, when it was applied and restored, boot counts, the look of the last apply, and per app its route (`OVERLAY`, `MODULE`, `UNSUPPORTED`, `NONE`), switch, version key, color count, restart flag, level and changes file |
| `files/amoled/changes/<package>.json` | The change records: every color's name, id, type (color or state list), stock light and dark values, AMOLED value, the rule that decided it, and the light entry written |
| `files/amoled/user_rules.json` | The user's own rules: colors kept stock (`keep`) and pinned colors (`pin`), per package |
| `files/amoled/cache/<package>.json` | The v2 engine's cache entry: key, color resource ids, primary color and changes |
| `files/amoled/ids/<package>.ids` | Module colors of an app switched off, kept to put back when it is switched on |
| `/data/system/substratum/<package>.RebornSystemAmoled.xml` | Fabricated overlay definitions, registered again at boot |
| `/data/system/substratum/snapshots/<epoch>/` | What Remove took away (definitions and module lists); the newest three are kept |
| `/data/resource-cache/substratum/<package>.amoled.ids` | Module color lists (see below); `.off` appended while the app is switched off |
| `/data/resource-cache/substratum/amoled-hooks.on` | Present while AMOLED is applied: turns on the module's System UI code hooks |
| `/data/resource-cache/substratum/qs-options` | The Quick Settings options |
| `/data/resource-cache/substratum/hooks.off`, `hooks.off.<package>` | The kill switch, for every process or one app |
| `/data/user_de/0/<package>/reborn_guard_starts`, `reborn_guard_tripped` | The crash-loop guard's start times, and its mark while the app is kept stock |

`files/` is the app's own data directory (`/data/data/projekt.substratum/files`, or
`/data/user/0/...`).

A module color list starts with a header naming the app version its ids were read from, then
one line per color:

```
# key=/data/app/~~.../com.android.vending-.../base.apk|84251230
0x7f060140 0xff000000 0xff000000 0xff1f1f1f
0x7f0601c5 0xff000000 0xff000000 csl
```

The key is the APK path and the version code. The module ignores a list whose key does not match
the installed app, so after an update the app stays stock (never black on black) until it is read
again. Each line is the resource id, the light value (the night value when no light entry is
written), the night value, and the stock night value, or `csl` for a color state list.

### Heartbeat and guard

System UI writes `reborn_module_status` in `Settings.Global` once the module has set up (or
skipped) its hooks: `v=<versionCode>;t=<millis>;qs=..;layout=..;battery=..;guard=ok|tripped|off`
(see [Module status](#module-status)). Home reads it: no heartbeat since boot, a tripped guard or
paused hooks are problems with a Fix; a heartbeat from an older build (the app was updated after
System UI started) is not, and Home offers a System UI restart to load the new hooks. The guard
counts starts in each hooked app's device-protected data; see
[Crash loop guard](#crash-loop-guard).

## Recovery

If System UI or an app misbehaves after applying something, these steps undo it from a root
shell (`adb shell`, then `su`), mildest first. None of them needs a reinstall.

### Module status

System UI records what the module did each time it starts:

```
settings get global reborn_module_status
# v=1101;t=1764100000000;qs=1;layout=0;battery=1;guard=ok
```

`qs`, `layout` and `battery` say whether the quick settings AMOLED hooks, the panel row height
and the battery hook were set up. `guard` is `ok`, `off` (kill switch) or `tripped` (crash loop
guard). `t` is when System UI wrote the line, right after it started.

### Kill switch

While `hooks.off` exists, the module installs no hooks and forwards no resources in any process
that starts:

```
touch /data/resource-cache/substratum/hooks.off
chmod 644 /data/resource-cache/substratum/hooks.off
restorecon /data/resource-cache/substratum/hooks.off
kill $(pidof com.android.systemui)      # System UI comes back stock
am force-stop com.android.vending       # any themed app: stock from its next launch
```

To turn the module back on, remove the file and restart the same way:

```
rm -f /data/resource-cache/substratum/hooks.off
kill $(pidof com.android.systemui)
```

`hooks.off.<package>` (for example `hooks.off.com.android.vending`) does the same for one app,
and `hooks.off.android` for framework resources. Processes read the switch when they start, so
a running app keeps its hooks until it restarts; if framework resources stay themed, a soft
reboot clears them. Substratum still sees the module as active while the switch is on.

### Crash loop guard

System UI and every app the module changes count their starts. Three starts within 90 seconds
trip the guard: the app then starts stock, and System UI reports `guard=tripped`. A process
that stays up for 30 seconds clears the count, so a restart more than 30 seconds after the
previous one never trips it. Restarts and force-stops that Substratum does itself (applying
quick settings options, AMOLED for every app, Remove) clear the count first, so they never
trip it either. While an app is left stock, the module also leaves alone every other package's
resources in that app's processes. After fixing the cause, reset the guard and restart:

```
rm -f /data/user_de/0/com.android.systemui/reborn_guard_*
kill $(pidof com.android.systemui)
```

For another app, use its package in the path and force-stop it instead.

### AMOLED overlays

AMOLED for every app also registers fabricated overlays. They are not hooks, so the kill switch
leaves them on. To turn off one app's overlay:

```
cmd overlay list | grep RebornSystemAmoled
cmd overlay disable --user 0 com.android.shell:<package>.RebornSystemAmoled
```

The next boot registers them again until **Remove** runs in AMOLED Studio (or **Undo
everything** on Home or Applied).

### LSPosed

To turn the module off completely, open LSPosed Manager, go to Modules, and switch
**substratum** off (or take single apps out of its scope). Then force-stop the scoped apps and
restart System UI, or soft reboot if LSPosed asks for it.

### Full reboot

Where root (for example KernelSU) and LSPosed are loaded only for the current session, a full
reboot drops both: the module stops, and so does everything that needs root, including
re-registering the AMOLED overlays. Root and LSPosed then have to be set up again. This is the
last resort, and it is the device owner's call.
