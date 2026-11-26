# Changelog

What changed in each version of Substratum Reborn, newest first. The build number is in brackets.

## Reborn 2.6 (2006)

- About shows the swatch-deck mark with "Reborn" over "Substratum", the tagline, the version,
  build and engine, and links to What's new, this changelog, the source code, the themers' guide,
  the open-source licences and the original Substratum.
- About credits Google Sans Flex and Material Symbols, and both are listed with their licences
  under Open-source licences.
- What's new covers the new Home, the Engine screen, the new mark and classic themes in dark mode
  only.
- A new README, this changelog and an index of the docs. The LSPosed module's description
  carries the tagline.

## Reborn 2.5 (2005)

- Classic themes apply in dark mode only by default, so light mode stays stock. The switch is on
  the theme's page and in Classic's theme menu; a theme made for light mode too can say so with
  the `Reborn_LightMode` meta-data.
- Entries a shell overlay may not change no longer sink the whole overlay: they go through the
  LSPosed module, and so do bools and integers the shell refuses.
- The classic compile drops only the lines this system no longer has and lists them in the apply
  summary.
- The module checks a theme's drawables and layouts before it uses them, and names the theme part
  a crash points at. The Engine screen then offers to pause that part and restart System UI.
- A classic theme can be turned off from its page or its long-press sheet, with Undo, and each of
  its parts has its own switch.
- A screen recreated during an apply no longer stops the apply before the overlays are enabled.

## Reborn 2.4 (2004)

- Home follows the approved design: a brand bar with the swatch-deck mark and the engine status
  pill, the colour-blocked Now card, Re-apply, Customize and Undo, a Try next carousel, and Make
  your own theme.
- Fix banners, the update notice and the quick actions live on the Engine screen.
- Themes: a compact header with search and Add, connected filters, and one grid of small cards,
  each with a mini Quick Settings in the theme's colours.

## Reborn 2.3 (2003)

- Home leads with the theme in use and the themes you have.
- The Engine screen: the status, fixes, what the engine can do on this phone, quick actions and
  the Rescue shortcut.
- Previews for every theme, classic ones too, drawn from the theme's own colours, and palette dots
  that a screen reader names.
- A new brand mark, the Substratum swatch deck in the app's colours, and a new launcher icon with
  a themed-icon layer.

## Reborn 2.2 (2002)

- Themes first: the tabs are Home, Themes, Create and Applied, on a floating navigation bar.
- Every theme has its own page: a preview, what it changes on this phone, Apply and Customize.
- Reborn AMOLED is a built-in theme; Customize opens its Studio.
- Create gathers the tools for themers: the app inspector, the preview roles and the theming
  guide.
- Launcher shortcuts for Themes, Create and Applied, plus Customize after an apply.

## Reborn 2.1 (2001)

- Material 3 Expressive throughout: Google Sans Flex, expressive shapes and motion, grouped menus,
  loading indicators, pull to refresh and Material Symbols icons.
- The Classic screens get expressive dialogs, switches and progress indicators.
- Predictive back.
- Reborn no longer needs to be in its own LSPosed scope: System UI and Settings are enough.

## Reborn 2.0 (2000)

The first release as Substratum Reborn.

- The engine: fabricated overlays registered with root plus an LSPosed module, a job queue with
  progress you can watch, restore at boot, a per-app cache so a re-apply takes seconds, and
  updated apps re-themed on their own.
- Safety: a kill switch, a crash-loop guard for System UI and every hooked app, no restarts of
  the phone, Bluetooth or keyboard processes, and a snapshot on Remove.
- The AMOLED Studio: presets, surfaces, an accent, the Quick Settings designer and a per-app
  inspector.
- A Home that says in plain words whether everything works, the setup checklist, the Themes and
  Applied tabs, Undo everything, and installing a theme from a file.
- See the difference, What's new and About.
