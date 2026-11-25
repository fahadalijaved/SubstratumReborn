#!/usr/bin/env python3
#
# Copyright (c) 2026 SubstratumReborn
# SPDX-License-Identifier: GPL-3.0-Or-Later
#
"""Write a ready-to-edit sample of One UI's design tokens from a reference.

Usage: gen_token_sample.py COLOR_CSV OUT_XML
  COLOR_CSV is <reference>/<package>/values/color.csv of an app that uses the tokens
  (com.android.systemui is a good choice).

The sample lists the light and dark role tokens and the palette, each with its resolved stock
value and where it points to. These names have no night variant, so overriding them from
res/values/ is safe in both light and dark mode.
"""
import csv
import re
import sys
from collections import OrderedDict

GROUPS = OrderedDict([
    ("sys", "System roles: backgrounds, surfaces, containers, primary, text"),
    ("component", "Component roles: dialogs, navigation bar, switches, sliders, cards"),
    ("state", "State layers: pressed and selected overlays"),
    ("effect", "Effects: blur surfaces and shadows"),
    ("support", "Support: headers, canvases, library templates"),
    ("palette", "Palette: raw tones the roles point to"),
])
PALETTES = ("gray", "primary", "red", "green", "orange")


def main():
    color_csv, out = sys.argv[1:3]
    table = {}
    with open(color_csv) as f:
        for row in csv.DictReader(f):
            if row["name"].startswith("basic_token_"):
                table.setdefault(row["name"], {})[row["configuration"]] = row["value"]

    def resolve(value, depth=0):
        if value is None or depth > 10:
            return None
        if re.fullmatch(r"#[0-9a-fA-F]{6,8}", value):
            return value.lower()
        m = re.fullmatch(r"@color/(\S+)", value)
        if m and m.group(1) in table:
            return resolve(table[m.group(1)].get("default"), depth + 1)
        return None

    groups = OrderedDict((g, []) for g in GROUPS)
    for name in sorted(table):
        if "default" not in table[name] or "night" in table[name]:
            continue  # switching roles: theme their _light/_dark variants instead
        body = name[len("basic_token_"):]
        group = "palette" if body.split("_")[0] in PALETTES else body.split("_")[0]
        if group not in groups:
            continue
        stock = table[name]["default"]
        groups[group].append((name, resolve(stock), stock))

    with open(out, "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<!--\n'
                '  One UI 9 design tokens, stock values (generated from the device reference).\n\n'
                '  Copy into assets/overlays/<package>/res/values/colors.xml of any app that uses\n'
                '  the tokens (SystemUI, Settings and most Samsung apps; see the reference README),\n'
                '  keep the lines you want to change and delete the rest.\n\n'
                '  *_light tokens are used in light mode, *_dark in dark mode. Changing a palette\n'
                '  tone changes every role that points to it.\n-->\n<resources>\n')
        for group, rows in groups.items():
            if not rows:
                continue
            f.write("\n    <!-- %s -->\n" % GROUPS[group])
            for name, value, stock in rows:
                note = "" if stock.startswith("#") else "  <!-- stock: %s -->" % stock
                f.write('    <color name="%s">%s</color>%s\n' % (name, value or stock, note))
        f.write("</resources>\n")
    print(out, sum(len(r) for r in groups.values()), "tokens")


if __name__ == "__main__":
    main()
