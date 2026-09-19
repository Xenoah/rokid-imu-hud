#!/usr/bin/env python3
"""Install/start the HUD and save a bounded, app-only runtime diagnostic capture.
Requires Platform Tools (adb) and a connected, authorized Rokid Glasses device.
Does not simulate hardware buttons or certify motion/fps results.
"""
import argparse
import csv
import datetime
import io
import json
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial')
parser.add_argument('--apk', type=Path, default=ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
parser.add_argument('--seconds', type=int, default=15)
parser.add_argument('--output', type=Path)
parser.add_argument('--force-fusion', action='store_true')
args = parser.parse_args()
if not 5 <= args.seconds <= 3600:
    parser.error('--seconds must be between 5 and 3600')
if not args.apk.is_file():
    parser.error('APK not found: ' + str(args.apk))
adb = shutil.which('adb')
if not adb:
    parser.error('Install Android SDK Platform Tools and add adb to PATH')
devices = subprocess.check_output([adb, 'devices'], text=True)
ready = [line.split()[0] for line in devices.splitlines()[1:] if line.endswith('\tdevice')]
if args.serial:
    if args.serial not in ready:
        parser.error('Requested device is not connected/authorized')
elif len(ready) != 1:
    parser.error('Connect one authorized device, or select it with --serial')
else:
    args.serial = ready[0]
out = args.output or ROOT / 'device-logs' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
out.mkdir(parents=True, exist_ok=True)
command = [adb, '-s', args.serial]
package = 'dev.xenoah.rokidhud'
def run(*tail, required=True):
    p = subprocess.run(command + [str(v) for v in tail], text=True, encoding='utf-8',
                       errors='replace', stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
    if required and p.returncode:
        raise RuntimeError(p.stdout)
    return p
def save(name, content):
    (out / name).write_text(content, encoding='utf-8')
result = {'status': 'STARTING', 'motion_tests': 'MANUAL_REQUIRED', 'button_test': 'MANUAL_REQUIRED'}
try:
    sdk = int(run('shell', 'getprop', 'ro.build.version.sdk').stdout.strip())
    if sdk < 31:
        raise RuntimeError('This APK requires Android API 31 or newer')
    save('firmware.txt', run('shell', 'getprop', 'ro.build.fingerprint').stdout)
    save('display.txt', run('shell', 'wm', 'size').stdout)
    installed = run('install', '-r', args.apk)
    save('install.txt', installed.stdout)
    if 'Success' not in installed.stdout:
        raise RuntimeError('Install did not report Success; no uninstall is attempted')
    run('shell', 'am', 'force-stop', package)
    started = run('shell', 'am', 'start', '-W', '-n', package + '/dev.xenoah.hud.MainActivity',
                  '--ez', 'debug', 'true', '--ez', 'force_fusion', str(args.force_fusion).lower())
    save('start.txt', started.stdout)
    if 'Status: ok' not in started.stdout:
        raise RuntimeError('Activity start did not report Status: ok')
    pid = run('shell', 'pidof', package).stdout.strip()
    if not pid.isdigit():
        raise RuntimeError('No single HUD process after launch')
    print('HUD started. Keep head still for initial calibration; capturing %d seconds.' % args.seconds, flush=True)
    alive = True
    for _ in range(args.seconds):
        time.sleep(1)
        if run('shell', 'pidof', package, required=False).stdout.strip() != pid:
            alive = False
            break
    logs = run('logcat', '-d', '--pid=' + pid, '-v', 'threadtime', required=False).stdout
    save('logcat.txt', logs)
    capture = run('shell', 'run-as', package, 'cat', 'files/hud-debug.csv', required=False)
    save('hud-debug.csv', capture.stdout)
    rows = list(csv.DictReader(io.StringIO(capture.stdout))) if capture.returncode == 0 else []
    valid = [row for row in rows if row.get('valid') == 'true']
    result.update(process_survived=alive, csv_rows=len(rows), valid_rows=len(valid),
                  native_attitude=valid[-1].get('native') if valid else None)
    if not alive or 'FATAL EXCEPTION' in logs:
        raise RuntimeError('HUD process exited or crashed; inspect logcat.txt')
    result['status'] = 'RUNTIME_SMOKE_PASS' if valid else 'CALIBRATION_OR_SENSOR_CHECK_REQUIRED'
    print(result['status'] + ': ' + str(out))
except (RuntimeError, ValueError, subprocess.SubprocessError) as exc:
    result.update(status='FAILED', error=str(exc))
    print('FAILED: ' + str(exc))
finally:
    save('result.json', json.dumps(result, indent=2) + '\n')
raise SystemExit(0 if result['status'] == 'RUNTIME_SMOKE_PASS' else 1)
