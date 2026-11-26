# Showcase shot list

The README and the docs use the screenshots below. None of them is in the repository yet: each
one is captured on the test device by the rules on this page, **reviewed by the device owner**,
and only then added here under its exact file name. Nothing else goes into this folder.

Prefer the app's own screens over system screens wherever a shot allows it: they are drawn with
demo data and show nothing personal.

## Slots

| File | Screen and state | Crop |
|---|---|---|
| `home.png` | Reborn Home, dark mode, Reborn AMOLED applied. Status hero all green, the *See the difference* carousel on its Quick Settings page with the divider near the middle. No snackbar. | Full screen, status bar included only in demo mode. Keep the *Try a look* row out of frame. |
| `themes.png` | Themes tab, dark mode, scrolled so only the **Built in** section shows (Reborn AMOLED). No installed theme in frame. | Full screen. Crop above the *Installed* header if it shows. |
| `detail.png` | Themes › **Reborn AMOLED**: its page at the top, the hero, the Preview and the floating toolbar (Apply or Customize). | Full screen. |
| `studio.png` | Themes › Reborn AMOLED › **Customize** (its Studio), scrolled so the Look presets and the Live preview show, preset *Pure black* or *Elevated cards* selected, nothing pending. | Full screen. Only the first two preset cards in view. |
| `inspector.png` | Create › **Inspect an app** › **Settings** (`com.android.settings`): its colour list, stock to themed, with rules. | Full screen. The app list behind it must not be visible. |
| `qs-before.png` | The real Quick Settings panel, fully expanded, **Reborn AMOLED removed** (stock One UI 9 dark mode). | The panel only, from the top of the tiles to the media card. |
| `qs-after.png` | The same panel with Reborn AMOLED applied (Pure black, opaque shade, dark tracks). Same tiles in the same order as `qs-before.png`. | Same crop as `qs-before.png`, pixel for pixel. |
| `settings-before.png` | Settings > **Display**, Reborn AMOLED removed. Not the main Settings page (it shows the account card). | From the page title to the fourth row. |
| `settings-after.png` | Settings > Display with Reborn AMOLED applied, scrolled to the same position. | Same crop as `settings-before.png`. |
| `applied.png` | Applied tab, filter *Quick Settings* or *Engine theme* with the search set to a system app, so only system entries show (Android system, System UI, Settings). | Full screen. |

`qs-before.png` and `qs-after.png` (and the two Settings shots) are a pair: take them back to
back, with the same wallpaper, brightness, tiles and scroll position, so the README can show
them side by side.

## Capture rules

### Status bar: demo mode

From a root or adb shell before capturing, and `exit` afterwards:

```
settings put global sysui_demo_allowed 1
am broadcast -a com.android.systemui.demo -e command enter
am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e level 4 -e datatype none
am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# when done
am broadcast -a com.android.systemui.demo -e command exit
```

The clock reads 12:00, the battery is full and no notification icons show. If One UI ignores a
demo command, crop the status bar out instead of capturing the real one.

### The device

- A neutral wallpaper: a plain dark gradient or solid colour, no photos.
- No notifications in the shade; clear them first.
- For the Quick Settings shots, turn on airplane mode with Wi-Fi and Bluetooth off, so no
  network name (SSID), carrier name or device name appears on a tile or label.
- Dark mode on, font and display size at their defaults.

### Never in a shot

- The account card, avatars, names or e-mail addresses (Samsung or Google account).
- Contacts, messages, call logs or phone numbers.
- Wi-Fi network names, Bluetooth device names, carrier names, IMEI or serial numbers.
- Location, maps or anything that reveals a place.
- Personal app lists: a list of installed apps is only shown filtered to system apps.
- Third-party themes: the Themes tab only with its built-in themes in frame (`themes.png`), and
  no third-party theme, or a look named after one, in any shot. Use the Pure black, Elevated
  cards or Dim look.

### The files

- PNG, straight from `screencap -p` (or the phone's screenshot), cropped as the slot says.
- Strip metadata before review, for example `exiftool -all= -overwrite_original <file>.png` or
  `mogrify -strip <file>.png`.
- File names exactly as in the slot table, in `docs/showcase/`.
- **The owner reviews every image before it is committed.** Until then the files stay out of
  git (`git status` must not list them as staged).

When the images are in, uncomment the screenshot block at the top of the root `README.md`.
