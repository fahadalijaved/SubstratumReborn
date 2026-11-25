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
SURF={  # One UI design tokens, dark
 'basic_token_sys_background_dark':'#ff000000','basic_token_sys_background_variant_dark':'#ff000000',
 'basic_token_sys_surface_dark':'#ff000000','basic_token_sys_surface_variant_dark':'#ff000000',
 'basic_token_sys_surface_container_lowest_dark':'#ff000000','basic_token_sys_surface_container_low_dark':'#ff0a0a0a',
 'basic_token_sys_surface_container_dark':'#ff121212','basic_token_sys_surface_container_high_dark':'#ff1a1a1a',
 'basic_token_sys_surface_container_higher_dark':'#ff222222','basic_token_sys_surface_container_highest_dark':'#ff2a2a2a',
 'basic_token_sys_surface_bright_dark':'#ff1a1a1a','basic_token_sys_surface_brightest_dark':'#ff262626',
 'basic_token_effect_blur_surface_dark':'#ff0a0a0a','basic_token_component_navibar_translucent_background_dark':'#ff000000',
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
 'com.android.settings':{'background_common':'#ff000000'},
}
DEFAULT_ONLY={'com.android.systemui':{'qs_tile_container_bg':'#ff0a0a0a','sec_panel_background_color':'#ff000000'}}
TOKEN_PKGS=sorted(p for p in os.listdir(REF) if os.path.exists(f'{REF}/{p}/values/color.csv') and any(l.startswith('basic_token_sys_background_dark,') for l in open(f'{REF}/{p}/values/color.csv')))
for pkg in TOKEN_PKGS:
    t=table(pkg); day=[]; night=[]; skipped=[]
    for n,v in SURF.items():
        if n in t: day.append((n,v,'design token'))
    for n,v in DEFAULT_ONLY.get(pkg,{}).items():
        if n in t: day.append((n,v,''))
    for n,v in NIGHT.get(pkg,{}).items():
        if n not in t: skipped.append(n+' (absent)'); continue
        if 'night' not in t[n]:
            skipped.append(n+' (no night variant)'); continue
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
