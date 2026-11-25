#!/usr/bin/env python3
#
# Copyright (c) 2026 SubstratumReborn
# SPDX-License-Identifier: GPL-3.0-Or-Later
#
"""Generate the Reborn AMOLED overlays for SystemUI, Settings and every app that uses One UI's
design tokens, from a reference built by build_reference.py.

Usage: gen_amoled.py REFERENCE_DIR FRAMEWORK_RES_APK OUT_DIR
  OUT_DIR is the theme's assets/overlays folder.

Dark-only tokens are written to values/. Resources that have a night variant in the target are
written to values-night/ with the stock light value copied to values/, because an overlay
resource missing in light mode would crash the target.
"""
import csv, re, subprocess, sys, os
REF, FW_APK, OUT = sys.argv[1:4]
AAPT2 = os.path.join(os.environ.get("ANDROID_HOME", "/opt/android-sdk"), "build-tools", "37.0.0", "aapt2")
# framework id -> literal, for @0x0106xxxx references
fw={}
cur=None
for line in subprocess.run([AAPT2,'dump','resources',FW_APK],capture_output=True,text=True).stdout.splitlines():
    m=re.match(r'^    resource 0x([0-9a-f]+) color/(\S+)',line)
    if m: cur=('0x'+m.group(1),m.group(2)); continue
    m=re.match(r'^      \(\) (.*)$',line)
    if m and cur and cur[0] not in fw: fw[cur[0]]=m.group(1).strip()
fwnames={}
with open(REF+'/android/values/color.csv') as f:
    for r in csv.DictReader(f):
        if r['configuration']=='default': fwnames[r['name']]=r['value']
def table(pkg):
    t={}
    with open(f'{REF}/{pkg}/values/color.csv') as f:
        for r in csv.DictReader(f): t.setdefault(r['name'],{})[r['configuration']]=r['value']
    return t
def literal(v, t, depth=0):
    if depth>12 or v is None: return None
    if re.fullmatch(r'#[0-9a-fA-F]{6,8}',v): return v.lower()
    m=re.fullmatch(r'@0x(01[0-9a-f]{6})',v)
    if m: return literal(fw.get('0x'+m.group(1)),{},depth+1)
    m=re.fullmatch(r'@color/(\S+)',v)
    if m:
        n=m.group(1)
        if n in t: return literal(t[n].get('default'),t,depth+1)
        if n in fwnames: return literal(fwnames[n],{},depth+1)
    return None
def literal_night(v, t, depth=0):
    """Like literal(), but follows the night value of each link in the chain."""
    if depth>12 or v is None: return None
    if re.fullmatch(r'#[0-9a-fA-F]{6,8}',v): return v.lower()
    m=re.fullmatch(r'@0x(01[0-9a-f]{6})',v)
    if m: return literal(fw.get('0x'+m.group(1)),{},depth+1)
    m=re.fullmatch(r'@color/(\S+)',v)
    if m:
        n=m.group(1)
        if n in t: return literal_night(t[n].get('night',t[n].get('default')),t,depth+1)
        if n in fwnames: return literal(fwnames[n],{},depth+1)
    return None

# One UI's stock dark greys and their AMOLED replacements. Every color whose dark-mode value
# resolves to one of these gets the replacement in values-night, whatever chain of references
# leads there: the hook only sees direct lookups, and Android resolves references natively.
PALETTE={
 # pages, cards, lists and banners: full black
 '#ff010102':'#ff000000', '#ff000000':'#ff000000', '#ff17171a':'#ff000000', '#ff1d1d20':'#ff000000',
 '#ff111111':'#ff000000', '#ff121212':'#ff000000', '#ff1b1b1b':'#ff000000', '#ff1c1b1f':'#ff000000',
 '#ff1f1f1f':'#ff000000', '#ff081226':'#ff000000', '#ff191c1e':'#ff000000', '#ff1a1c1e':'#ff000000',
 # raised surfaces, dividers, floating bars, search fields: just off black so they still read
 '#ff252528':'#ff0a0a0a', '#ff2a2a2d':'#ff0a0a0a', '#ff2d2d30':'#ff121212', '#ff303030':'#ff121212',
 '#ff313131':'#ff121212', '#ff323234':'#ff121212', '#ff333333':'#ff121212', '#ff343437':'#ff121212',
 '#ff3a3a3c':'#ff121212', '#ff3a3a3d':'#ff121212', '#ff3b3b3b':'#ff121212',
 # selected states on those bars
 '#ff505053':'#ff262626', '#ff454548':'#ff262626',
}

def amoled(color):
    """AMOLED replacement for an opaque dark-mode color, or None to keep it.

    Any dark color (whatever its hue) becomes black; dark neutral greys used for dividers,
    bars, scrollbars and buttons become near black. Translucent colors (scrims, shadows) and
    anything lighter (text, accents) are kept.
    """
    if color in PALETTE: return PALETTE[color]
    if not color or len(color)!=9 or not color.startswith('#ff'): return None
    r,g,b=(int(color[i:i+2],16) for i in (3,5,7))
    hi,lo=max(r,g,b),min(r,g,b)
    if hi<=0x30: return '#ff000000'
    if hi-lo<=0x18 and hi<=0x60: return '#ff121212'
    if hi-lo<=0x18 and hi<=0x80: return '#ff262626'
    return None

SURF={  # One UI design tokens, dark
 'basic_token_sys_background_dark':'#ff000000','basic_token_sys_background_variant_dark':'#ff000000',
 'basic_token_sys_surface_dark':'#ff000000','basic_token_sys_surface_variant_dark':'#ff000000',
 'basic_token_sys_surface_container_lowest_dark':'#ff000000','basic_token_sys_surface_container_low_dark':'#ff000000',
 'basic_token_sys_surface_container_dark':'#ff000000','basic_token_sys_surface_container_high_dark':'#ff0a0a0a',
 'basic_token_sys_surface_container_higher_dark':'#ff121212','basic_token_sys_surface_container_highest_dark':'#ff121212',
 'basic_token_sys_surface_bright_dark':'#ff0a0a0a','basic_token_sys_surface_brightest_dark':'#ff121212',
 'basic_token_effect_blur_surface_dark':'#ff000000','basic_token_component_navibar_translucent_background_dark':'#ff000000',
 'basic_token_component_dialog_surface_container_dark':'#ff121212','basic_token_component_dialog_surface_container_low_dark':'#ff0a0a0a',
}
NIGHT={
 'com.android.systemui':{
  'panel_bg_color':'#ff000000','notification_material_background_color':'#ff0a0a0a',
  'notification_material_background_dimmed_color':'#ff111111','notification_material_background_on_lockscreen':'#ff000000',
  'notification_material_background_color_reduce_transparency_and_blur':'#ff0a0a0a',
  'notification_legacy_background_color':'#ff0a0a0a','notification_shelf_button_background_fill_color':'#ff0a0a0a',
  'qs_detail_layout_popup_menu_background_color':'#ff111111','qs_detail_layout_popup_menu_background_color_no_blur':'#ff111111',
  'qs_tile_round_background_off':'#ff1a1a1a','qs_multisim_popup_background':'#ff111111','qs_onboarding_bg_color':'#ff111111',
  'sec_brightness_mirror_background_color':'#ff000000','sec_notification_guts_content_bg_color':'#ff0a0a0a',
  'sec_notification_guts_header_bg_color':'#ff111111','sec_fgs_content_button_background_color':'#ff1a1a1a',
  'sec_control_default_background':'#ff0a0a0a','sec_control_enabled_default_background':'#ff111111',
  'panel_background_blur_color':'#ff000000',
 },
 'com.android.settings':{'background_common':'#ff000000','sesl_fragment_bgcolor':'#ff000000'},
}
# name -> AMOLED value for names matched by a regex, dark mode only (stock light value is kept)
NIGHT_PATTERNS={
 'com.android.settings':[(r'^sec_dashboard_(?!.*(text|layer)).*_color\d*$','#ff262626')],
}
DEFAULT_ONLY={'com.android.systemui':{'qs_tile_container_bg':'#ff0a0a0a','sec_panel_background_color':'#ff000000'}}
SKIP=re.compile(r'^(android|com\.google\.android\.gms|projekt\.substratum.*|com\.google\.android\.gms\.policy_.*|com\.monotype\..*)$')
TOKEN_PKGS=sorted(p for p in os.listdir(REF) if os.path.exists(f'{REF}/{p}/values/color.csv') and not SKIP.match(p))
for pkg in TOKEN_PKGS:
    t=table(pkg); day=[]; night=[]; skipped=[]
    for n,v in SURF.items():
        if n in t: day.append((n,v,'design token'))
    for n,v in DEFAULT_ONLY.get(pkg,{}).items():
        if n in t: day.append((n,v,''))
    night_items=dict(NIGHT.get(pkg,{}))
    for pattern,v in NIGHT_PATTERNS.get(pkg,[]):
        for n in t:
            if re.match(pattern,n): night_items.setdefault(n,v)
    # Palette mapping, for every color that ends on a stock dark grey in dark mode
    direct=set(SURF)|set(DEFAULT_ONLY.get(pkg,{}))  # already set for every mode in values/
    for n,configs in t.items():
        if n in night_items or n in direct: continue
        dark=literal_night(configs.get('night',configs.get('default')),t)
        mapped=amoled(dark)
        if mapped and mapped!=dark:
            night_items[n]=mapped
    for n,v in night_items.items():
        if n not in t: skipped.append(n+' (absent)'); continue
        # values-night only works if light mode keeps a value, so copy the stock light value
        if 'default' not in t[n]:
            night.append((n,v)); continue  # night-only in the target: no light fallback needed
        d=literal(t[n].get('default'),t)
        if d is None: skipped.append(n+' (light value not literal: %s)'%t[n].get('default')); continue
        day.append((n,d,'stock light value')); night.append((n,v))
    base=f'{OUT}/{pkg}/res'
    os.makedirs(base+'/values',exist_ok=True)
    if night: os.makedirs(base+'/values-night',exist_ok=True)
    hdr='<?xml version="1.0" encoding="utf-8"?>\n<!-- Reborn AMOLED, %s, %s. Generated from the One UI 9 reference. -->\n<resources>\n'
    with open(base+'/values/colors.xml','w') as f:
        f.write(hdr%(pkg,'all modes: dark-only design tokens, plus the stock light values of the night overrides so light mode keeps working'))
        for n,v,c in day: f.write(f'    <color name="{n}">{v}</color>\n')
        f.write('</resources>\n')
    if night:
      with open(base+'/values-night/colors.xml','w') as f:
        f.write(hdr%(pkg,'dark mode'))
        for n,v in night: f.write(f'    <color name="{n}">{v}</color>\n')
        f.write('</resources>\n')
    print(pkg, 'day', len(day), 'night', len(night), 'skipped', skipped)
