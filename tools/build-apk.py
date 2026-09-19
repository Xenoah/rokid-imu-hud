#!/usr/bin/env python3
"""Build this dependency-free app with Android SDK tools, without downloading Gradle.

Requires JDK 17, official android.jar, aapt2, D8, zipalign and apksigner.
Uses the same sources, resources and version settings as the Gradle project.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME'))
parser.add_argument('--platform', default='35')
parser.add_argument('--build-tools', default='35.0.0')
parser.add_argument('--android-jar', type=Path)
parser.add_argument('--tools-dir', type=Path)
args = parser.parse_args()
sdk = Path(args.sdk).expanduser() if args.sdk else None
android = args.android_jar or (sdk / 'platforms' / ('android-' + args.platform) / 'android.jar' if sdk else None)
tools = args.tools_dir or (sdk / 'build-tools' / args.build_tools if sdk else None)
if not android or not tools:
    parser.error('Provide --sdk (or ANDROID_HOME), or --android-jar and --tools-dir')
android, tools = android.resolve(), tools.resolve()
java, keytool = shutil.which('java'), shutil.which('keytool')
if not java or not keytool:
    parser.error('JDK 17 with java and keytool is required')
suffix = '.exe' if os.name == 'nt' else ''
required = [android, tools / ('aapt2' + suffix), tools / ('zipalign' + suffix),
            tools / 'lib/d8.jar', tools / 'lib/apksigner.jar']
for p in required:
    if not p.is_file():
        parser.error('Missing Android SDK component: ' + str(p))

gradle = (ROOT / 'app/build.gradle.kts').read_text()
def setting(name):
    match = re.search(r'\b' + name + r'\s*=\s*(?:"([^"]+)"|(\d+))', gradle)
    if not match:
        raise ValueError('Cannot read Gradle setting: ' + name)
    return match.group(1) or match.group(2)

work = ROOT / 'app/build/sdk-direct'
if work.exists():
    shutil.rmtree(work)  # Only our own generated build directory.
work.mkdir(parents=True)
classes, dex, generated = (work / n for n in ('classes', 'dex', 'generated'))
for p in (classes, dex, generated):
    p.mkdir()
env = os.environ.copy()
if os.name != 'nt':
    env['LD_LIBRARY_PATH'] = str(tools / 'lib64') + os.pathsep + env.get('LD_LIBRARY_PATH', '')
log = []
result = {'status': 'BUILDING', 'applicationId': setting('applicationId'),
          'versionName': setting('versionName'), 'minSdk': int(setting('minSdk')),
          'targetSdk': int(setting('targetSdk')), 'physical_device_test': 'NOT_RUN',
          'tool_sha256': {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in required}}
def run(command):
    command = [str(v) for v in command]
    log.append('$ ' + ' '.join(command))
    proc = subprocess.run(command, cwd=ROOT, env=env, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    log.append(proc.stdout)
    print(proc.stdout, end='', flush=True)
    if proc.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(command[:3]))

try:
    ns = 'http://schemas.android.com/apk/res/android'
    ET.register_namespace('android', ns)
    tree = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
    manifest = tree.getroot()
    manifest.set('package', setting('applicationId'))
    for name in ('versionCode', 'versionName'):
        manifest.set('{' + ns + '}' + name, setting(name))
    manifest.find('application').set('{' + ns + '}debuggable', 'true')
    # AGP normally expands relative class names using namespace, not applicationId.
    for node in manifest.iter():
        key = '{' + ns + '}name'
        name = node.get(key, '')
        if name.startswith('.'):
            node.set(key, setting('namespace') + name)
    mf = work / 'AndroidManifest.xml'
    tree.write(mf, encoding='utf-8', xml_declaration=True)
    resources, unsigned, aligned = (work / n for n in ('resources.zip', 'unsigned.apk', 'aligned.apk'))
    run([tools / ('aapt2' + suffix), 'compile', '--dir', ROOT / 'app/src/main/res', '-o', resources])
    run([tools / ('aapt2' + suffix), 'link', '-I', android, '--manifest', mf,
         '--min-sdk-version', setting('minSdk'), '--target-sdk-version', setting('targetSdk'),
         '--java', generated, '--custom-package', setting('namespace'), '-o', unsigned, resources])
    sources = sorted((ROOT / 'core/src/main/java').rglob('*.java'))
    sources += sorted((ROOT / 'app/src/main/java').rglob('*.java')) + sorted(generated.rglob('*.java'))
    run([java, 'com.sun.tools.javac.Main', '--release', '17', '-Xlint:all', '-g',
         '-encoding', 'UTF-8', '-cp', android, '-d', classes, *sources])
    class_jar = work / 'classes.jar'
    with zipfile.ZipFile(class_jar, 'w', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(classes.rglob('*.class')):
            z.write(p, p.relative_to(classes).as_posix())
    run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--debug',
         '--min-api', setting('minSdk'), '--lib', android, '--output', dex, class_jar])
    if not (dex / 'classes.dex').is_file():
        raise RuntimeError('DEX generation produced no classes.dex')
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(dex.glob('*.dex')):
            z.write(p, p.name)
    run([tools / ('zipalign' + suffix), '-f', '4', unsigned, aligned])
    key = ROOT / '.build/hud-debug.keystore'
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run([keytool, '-genkeypair', '-keystore', key, '-alias', 'androiddebugkey',
             '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
             '-validity', '10000', '-dname', 'CN=Rokid HUD Debug,O=Local Development,C=JP'])
        key.chmod(0o600)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    apk.parent.mkdir(parents=True, exist_ok=True)
    run([java, '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', key,
         '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android',
         '--key-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', apk, aligned])
    run([java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose',
         '--print-certs', '--min-sdk-version', setting('minSdk'), apk])
    run([tools / ('zipalign' + suffix), '-c', '-v', '4', apk])
    run([tools / ('aapt2' + suffix), 'dump', 'badging', apk])
    result.update(status='SIGNED_AND_VERIFIED', apk=str(apk),
                  apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), apk_bytes=apk.stat().st_size)
    print('APK: ' + str(apk))
except (OSError, RuntimeError) as exc:
    result.update(status='FAILED', error=str(exc))
    print('BUILD FAILED: ' + str(exc), file=sys.stderr)
finally:
    reports = ROOT / 'artifacts'
    reports.mkdir(exist_ok=True)
    (reports / 'apk-build-log.txt').write_text('\n'.join(log))
    (reports / 'apk-build-status.json').write_text(json.dumps(result, indent=2) + '\n')
sys.exit(0 if result['status'] == 'SIGNED_AND_VERIFIED' else 1)
