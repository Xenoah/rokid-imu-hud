#!/usr/bin/env python3
"""Inspect the built APK/DEX itself. This is not Android runtime execution."""
import argparse
import hashlib
from pathlib import Path
import struct
import subprocess
import zipfile
import zlib

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--aapt2', type=Path, required=True)
args = parser.parse_args()
checks = 0
def check(condition, message):
    global checks
    if not condition:
        raise SystemExit('FAIL: ' + message)
    checks += 1
    print('PASS: ' + message)

with zipfile.ZipFile(args.apk) as apk:
    names = apk.namelist()
    check(apk.testzip() is None and len(names) == len(set(names)), 'ZIP CRCs and unique entries')
    check({'AndroidManifest.xml', 'resources.arsc', 'classes.dex'} <= set(names), 'Manifest, resources and executable DEX present')
    check(not any(n.startswith('lib/') or n.endswith('.class') for n in names), 'Universal Java APK; no ABI-specific libraries or raw class files')
    dex = apk.read('classes.dex')
    u32 = lambda offset: struct.unpack_from('<I', dex, offset)[0]
    check(dex[:8] == b'dex\n039\0' and u32(32) == len(dex) and u32(36) == 112,
          'DEX 039 header and size (supported since API 28)')
    check(dex[12:32] == hashlib.sha1(dex[32:]).digest() and u32(8) == zlib.adler32(dex[12:]) & 0xffffffff,
          'DEX SHA-1 and Adler-32 integrity')
    strings = []
    for i in range(u32(56)):
        offset = u32(u32(60) + i * 4)
        while dex[offset] & 0x80:
            offset += 1
        offset += 1
        strings.append(dex[offset:dex.index(b'\0', offset)].decode('utf-8', errors='replace'))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    classes = {types[u32(u32(100) + i * 32)] for i in range(u32(96))}
    required = {'Ldev/xenoah/hud/MainActivity;', 'Ldev/xenoah/hud/SensorController;',
                'Ldev/xenoah/hud/HudSurface;', 'Ldev/xenoah/hud/core/HudEngine;',
                'Ldev/xenoah/hud/core/HudRenderer;', 'Ldev/xenoah/hud/core/Fusion;'}
    check(required <= classes, 'Launcher, sensor, render, fusion and engine implementations in DEX')
    check(all(c.startswith('Ldev/xenoah/hud/') for c in classes), 'No Android stubs, desktop preview, or test classes packaged')
    check(not any(t.startswith('Lj$/') for t in types), 'No undeclared desugared Java library dependency')
    print('INFO: %d DEX classes, %d methods, %d bytes' % (len(classes), u32(88), len(dex)))

badging = subprocess.check_output([str(args.aapt2), 'dump', 'badging', str(args.apk)], text=True)
check("name='dev.xenoah.rokidhud'" in badging and
      "launchable-activity: name='dev.xenoah.hud.MainActivity'" in badging,
      'Manifest launcher resolves to the actual DEX class')
check("minSdkVersion:'31'" in badging and "targetSdkVersion:'35'" in badging and
      'application-debuggable' in badging, 'Android 12 minimum; target 35; debug logging access enabled')
check('uses-permission' not in badging, 'No Internet, Bluetooth, or other requested permissions')
print('RESULT: %d static APK checks passed. Device execution: NOT RUN.' % checks)
print('SHA256: ' + hashlib.sha256(args.apk.read_bytes()).hexdigest())
