#!/usr/bin/env python3
#
# Copyright (c) 2026 SubstratumReborn
# SPDX-License-Identifier: GPL-3.0-Or-Later
#
"""Write curated, ready-to-edit overlay samples for the most themed One UI resources.

Usage: gen_samples.py REFERENCE_DIR OUT_DIR

Stock values come from the reference. Each sample is laid out like a theme's
assets/overlays/<package>/res folder, so it can be copied as-is and trimmed.
"""
import csv
import os
import re
import sys

# package -> type -> [(name, what it changes)]
CURATED = {
    "com.android.systemui": {
        "color": [
            ("panel_bg_color", "Notification shade / QS panel background"),
            ("sec_panel_background_color", "Panel background behind the blur"),
            ("panel_background_blur_color", "Tint over the panel blur"),
            ("qs_tile_container_bg", "Container behind the QS tiles"),
            ("qs_tile_round_background_on", "QS tile, on"),
            ("qs_tile_round_background_off", "QS tile, off"),
            ("qs_tile_round_background_dim", "QS tile, unavailable"),
            ("qs_tile_label", "QS tile label text"),
            ("notification_material_background_color", "Notification card"),
            ("notification_material_background_dimmed_color", "Notification card, dimmed"),
            ("notification_material_background_on_lockscreen", "Notification card on the lock screen"),
            ("notification_primary_text_color", "Notification title text"),
            ("notification_shelf_button_background_fill_color", "Shelf buttons under the notifications"),
            ("qs_detail_layout_popup_menu_background_color", "QS popup menus (with blur)"),
            ("qs_detail_layout_popup_menu_background_color_no_blur", "QS popup menus (no blur)"),
            ("sec_brightness_mirror_background_color", "Brightness slider while dragging"),
            ("sec_notification_guts_content_bg_color", "Notification settings (long press), body"),
            ("sec_notification_guts_header_bg_color", "Notification settings (long press), header"),
        ],
        "dimen": [
            ("notification_corner_radius", "Notification card corners"),
            ("notification_side_paddings", "Space left and right of notifications"),
            ("sec_qs_tile_height_no_label", "QS tile height without labels"),
            ("sec_qs_tile_label_text_size", "QS tile label size"),
            ("brightness_slider_height", "Brightness slider height"),
            ("brightness_slider_expanded_height", "Brightness slider height, expanded"),
            ("status_bar_clock_size", "Status bar clock text size"),
            ("status_bar_padding_start", "Status bar inset, left"),
            ("status_bar_padding_end", "Status bar inset, right"),
            ("signal_icon_size", "Mobile signal icon size"),
            ("battery_height", "Battery icon height"),
            ("battery_width", "Battery icon width"),
        ],
    },
    "android": {
        "color": [
            ("background_device_default_dark", "Default dark window background"),
            ("background_floating_device_default_dark", "Dark dialogs and popups"),
            ("system_background_dark", "Material 3 background, dark"),
            ("system_surface_dark", "Material 3 surface, dark"),
            ("system_surface_container_lowest_dark", "Material 3 container, lowest"),
            ("system_surface_container_low_dark", "Material 3 container, low"),
            ("system_surface_container_dark", "Material 3 container"),
            ("system_surface_container_high_dark", "Material 3 container, high"),
            ("system_surface_container_highest_dark", "Material 3 container, highest"),
            ("tw_screen_background_color_dark", "One UI screen background, dark"),
            ("tw_dialog_window_background_color_material_dark", "One UI dialogs, dark"),
        ],
    },
}


def load(reference, package, rtype):
    table = {}
    path = os.path.join(reference, package, "values", rtype + ".csv")
    with open(path) as f:
        for row in csv.DictReader(f):
            table.setdefault(row["name"], {})[row["configuration"]] = row["value"]
    return table


def resolve(table, value, depth=0):
    if value is None or depth > 10:
        return value
    m = re.fullmatch(r"@(color|dimen)/(\S+)", value)
    if m and m.group(2) in table:
        return resolve(table, table[m.group(2)].get("default"), depth + 1)
    m = re.fullmatch(r"(-?[0-9.]+)(dp|sp|px)", value)
    if m:
        number = float(m.group(1))
        return ("%g" % number) + m.group(2)
    return value


def main():
    reference, out = sys.argv[1:3]
    for package, types in CURATED.items():
        for rtype, items in types.items():
            table = load(reference, package, rtype)
            day, night = [], []
            for name, what in items:
                if name not in table:
                    print("missing in %s: %s/%s" % (package, rtype, name), file=sys.stderr)
                    continue
                configs = table[name]
                light = resolve(table, configs.get("default"))
                dark = resolve(table, configs.get("night"))
                if "night" in configs:
                    night.append((name, dark, what))
                    if light is not None:
                        day.append((name, light, what + " (light mode; keep this line when you "
                                                         "change the dark value)"))
                else:
                    day.append((name, light, what))
            tag = "color" if rtype == "color" else "dimen"
            for folder, rows in (("values", day), ("values-night", night)):
                if not rows:
                    continue
                path = os.path.join(out, package, "res", folder, rtype + "s.xml")
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w") as f:
                    f.write('<?xml version="1.0" encoding="utf-8"?>\n<!--\n'
                            "  %s, %s: stock One UI 9 values, generated from the reference.\n"
                            "  Change the values you want and delete the other lines.\n" %
                            (package, folder))
                    if folder == "values-night":
                        f.write("  Every name here also needs a line in values/ (already there with\n"
                                "  the stock light value), or the target crashes in light mode.\n")
                    f.write("-->\n<resources>\n")
                    for name, value, what in rows:
                        unresolved = value is None or value.startswith("@0x")
                        f.write("    <!-- %s%s -->\n" % (what, "; stock value is a framework "
                                                                "reference, set your own" if unresolved else ""))
                        f.write('    <%s name="%s">%s</%s>\n' % (
                            tag, name, "TODO" if unresolved else value, tag))
                    f.write("</resources>\n")
                print(path, len(rows))


if __name__ == "__main__":
    main()
