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
- Substratum shows **LSPosed mode** under Settings, System status, when all of that is in place.

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
and on any One UI release, with nothing to regenerate. Tapping such a theme in Substratum opens
"AMOLED for every app" (Apply / Remove) instead of the overlay list; the work runs in the
background with a progress notification. The same is available for the built-in rules from the
Themes tab menu.

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
| `include`, `exclude` | regexes of package names to theme |
| `colors` | hand-picked values per package; keys are resource names, or regexes when they start with `^` |

Every key is optional; what a theme leaves out keeps Reborn AMOLED's value. Make a variant by
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

Each night value is registered together with the app's stock light value, so light mode never
changes. Colors are always read with the app's previous AMOLED overlay disabled, so running it
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
| Brightness / volume track | 15% white drawn twice (grey) | 6% white (near black) |
| Panel row height | `cell width x 0.917` | `cell width x` the value in `/data/resource-cache/substratum/qs-cell-aspect` (for example `0.65`; delete the file for stock) |

Restart System UI after changing `qs-cell-aspect`. The tile box columns are QuickStar's setting.

Apps whose resource names are obfuscated (Play Store) or that refuse overlays get their colors
from the module inside their own process, by resource id and, for Compose screens, by their
own dark surface colors where they are drawn. Add such apps to the module's scope.
