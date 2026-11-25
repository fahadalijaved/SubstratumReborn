#!/usr/bin/env python3
#
# Copyright (c) 2026 SubstratumReborn
# SPDX-License-Identifier: GPL-3.0-Or-Later
#
"""Turn the definitions "AMOLED for every app" registered on a device into a classic Substratum
theme: one overlay per app, stock light values in res/values, AMOLED in res/values-night.

Usage: fab_to_theme.py DEFINITIONS_DIR OVERLAYS_DIR [ATTRIBUTE_SHEET.csv]
  DEFINITIONS_DIR holds <package>.RebornSystemAmoled.xml, pulled from /data/system/substratum.
"""
import csv
import os
import sys
import xml.etree.ElementTree as ET

SUFFIX = '.RebornSystemAmoled.xml'


def color(value):
    return '#%08x' % (int(value, 16) & 0xffffffff)


def main():
    src, out = sys.argv[1:3]
    sheet = sys.argv[3] if len(sys.argv) > 3 else None
    rows = []
    apps = 0
    for f in sorted(os.listdir(src)):
        if not f.endswith(SUFFIX):
            continue
        pkg = f[:-len(SUFFIX)]
        day, night = {}, {}
        for item in ET.parse(os.path.join(src, f)).getroot():
            name = item.get('target').split('/', 1)[1]
            (night if item.get('config') == 'night' else day)[name] = color(item.get('value'))
        if not night:
            continue
        rows += [(pkg, name, day.get(name, ''), night[name]) for name in sorted(night)]
        for folder, values in (('values', day), ('values-night', night)):
            path = os.path.join(out, pkg, 'res', folder)
            os.makedirs(path, exist_ok=True)
            with open(os.path.join(path, 'colors.xml'), 'w') as w:
                w.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Reborn AMOLED Static, %s, %s -->\n<resources>\n'
                        % (pkg, 'stock light values' if folder == 'values' else 'AMOLED'))
                for name in sorted(values):
                    w.write('    <color name="%s">%s</color>\n' % (name, values[name]))
                w.write('</resources>\n')
        apps += 1
    if sheet:
        with open(sheet, 'w', newline='') as f:
            w = csv.writer(f)
            w.writerow(['package', 'name', 'stock_light', 'amoled'])
            w.writerows(rows)
    print(apps, 'apps,', len(rows), 'attributes')


if __name__ == '__main__':
    main()
