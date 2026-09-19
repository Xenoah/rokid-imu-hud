#!/usr/bin/env python3
"""Publish the checked-out tag's existing signed APK; never rebuild or re-sign it."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
meta = json.loads(Path('release.json').read_text(encoding='utf-8'))
tag = meta['tag']
if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-preview)?', tag):
    raise SystemExit('Tag does not match the version manifest')
if (subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() !=
        os.environ['RELEASE_COMMIT']):
    raise SystemExit('Checkout does not match the triggering commit')
gradle = Path('app/build.gradle.kts').read_text(encoding='utf-8')
if re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1) != tag[1:]:
    raise SystemExit('Android versionName does not match the tag')
apk = Path(meta['apk'])
if hashlib.sha256(apk.read_bytes()).hexdigest() != meta['apk_sha256']:
    raise SystemExit('Signed APK checksum mismatch')
repo = os.environ['GITHUB_REPOSITORY']
out = Path('.release-assets')
out.mkdir(exist_ok=True)
assets = []
for source in [str(apk), *meta['images']]:
    src = Path(source)
    dest = out / src.name
    if not src.is_file() or dest.name in [p.name for p in assets]:
        raise SystemExit('Missing or duplicate asset: ' + source)
    shutil.copyfile(src, dest)
    assets.append(dest)
source_zip = out / ('rokid-imu-hud-' + tag[1:] + '-source.zip')
subprocess.run(['git', 'archive', '--format=zip',
                '--prefix=rokid-imu-hud-' + tag[1:] + '/',
                '-o', str(source_zip), 'HEAD'], check=True)
assets.append(source_zip)
checksums = out / 'SHA256SUMS.txt'
checksums.write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() +
                    '  ' + p.name + '\n' for p in assets), encoding='utf-8')
assets.append(checksums)
common = ['--repo', repo]
existing = subprocess.run(['gh', 'release', 'view', tag, *common,
                          '--json', 'isDraft,isPrerelease,assets'],
                         text=True, capture_output=True)
if existing.returncode == 0:
    found = json.loads(existing.stdout)
    if not found['isDraft']:
        raise SystemExit('Release is already published; refusing to replace its assets')
else:
    # Read the collection as well: authentication/transport errors must not be
    # mistaken for a missing release. Only create after a successful read.
    subprocess.run(['gh', 'api', 'repos/' + repo + '/releases', '--silent'], check=True)
    subprocess.run(['gh', 'release', 'create', tag, *common, '--draft',
                    '--target', os.environ['RELEASE_COMMIT'], '--title', meta['title'],
                    '--notes-file', meta['notes'],
                    '--prerelease=' + str(meta['prerelease']).lower()], check=True)
    found = {'assets': []}
uploaded = {a['name'] for a in found['assets']}
for asset in assets:
    if asset.name in uploaded:
        raise SystemExit('Draft already contains asset; inspect before retry: ' + asset.name)
    subprocess.run(['gh', 'release', 'upload', tag, str(asset), *common], check=True)
subprocess.run(['gh', 'release', 'edit', tag, *common, '--draft=false',
                '--prerelease=' + str(meta['prerelease']).lower(),
                '--latest=' + str(not meta['prerelease']).lower(),
                '--title', meta['title'], '--notes-file', meta['notes']], check=True)
print('Published https://github.com/' + repo + '/releases/tag/' + tag)
