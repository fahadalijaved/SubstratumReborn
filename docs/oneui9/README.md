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

## Making an AMOLED theme

`themes/amoled` (Reborn AMOLED) is generated from the reference by
`tools/oneui-reference/gen_amoled.py`, and is the easiest way to see what an AMOLED One UI theme
has to touch. [`samples/amoled_attributes.csv`](samples/amoled_attributes.csv) lists every
attribute it changes: package, resource name, the stock dark value and the AMOLED value. Copy the
lines you want into your own theme's `res/values-night/colors.xml`, together with the stock
light value in `res/values/colors.xml` (rule 1 above).

The generator decides by the **dark-mode value each color actually resolves to**, following One
UI's reference chains, and by the resource name:

| Dark-mode value | Becomes | Examples |
|---|---|---|
| Any dark color (brightest channel up to `0x30`), any hue | `#000000` | page, cards, list rows, the blue suggestion card |
| Dark neutral grey up to `0x80`, background-like name | `#000000` | cards `#17171a`, keyboard keys `#3a3a3d` |
| Same, line-like name (`divider`, `outline`, `stroke`, `border`) | `#000000` | dividers, card outlines |
| Same, floating element (`popup`, `menu`, `dialog`, `search` field, `navi`, `toolbar`, `chip`, `fab`) | `#0a0a0a` | popups, sheets, nav pill, search field |
| Scrollbar thumb / track | `#2a2a2a` / `#000000` | the scroll position stays visible |
| See-through white up to 25% (`#1affffff`...) | transparent | `basic_token_sys_tone_on_tone`, button backgrounds, outlines |
| See-through dark tint over blur | at least 80% black | panel and sheet blur |
| Text, icons, press/selection states, accents | unchanged | |

A few names are set by hand on top of the rules: QS tiles on `#387aff` / off `#141414`, the QS
tile container transparent, Settings' coloured icon plates `#262626` (the glyphs stay white), and
the framework's Material 3 surfaces. Light mode is never changed.

Google Play services is left out: its code modules share resource ids with it, and the LSPosed
part broke them.

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
tools/oneui-reference/gen_amoled.py OUT/reference OUT/apks/android.apk themes/amoled/src/main/assets/overlays docs/oneui9/samples/amoled_attributes.csv
```

The reference records names and values only; it does not contain Samsung's files.
