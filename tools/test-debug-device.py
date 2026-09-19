#!/usr/bin/env python3
"""Regression tests for diagnostic capture when adb reports startup failure.
Uses a fake adb executable; these are NOT Android or Rokid execution tests.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('debug-device.py')
FAKE_ADB = '''#!/usr/bin/env python3
import os, sys
a=sys.argv[1:]
if a[:1]==['-s']: a=a[2:]
failure=os.environ['HUD_TEST_FAILURE']
if a==['devices']: print('List of devices attached\\nTEST\\tdevice')
elif a[:3]==['shell','getprop','ro.build.version.sdk']: print('31')
elif a[:2]==['shell','getprop']: print('FAKE-test-firmware')
elif a[:3]==['shell','wm','size']: print('Physical size: 480x640')
elif a[:1]==['install']:
    print('Failure [TEST_INSTALL_FAILURE]' if failure=='install' else 'Success')
    sys.exit(1 if failure=='install' else 0)
elif a[:4]==['shell','pm','list','packages']: print('package:dev.xenoah.rokidhud uid:10123')
elif a[:3]==['shell','am','start']:
    print('Error: FAKE activity launch failed' if failure=='launch' else 'Status: ok')
elif a[:2]==['shell','pidof']: sys.exit(1)
elif a[:1]==['logcat']:
    assert '--uid=10123' in a
    print('FAKE regression-test startup crash record')
elif a[:3]==['shell','dumpsys','package']: print('FAKE package diagnostics')
'''

class CaptureTests(unittest.TestCase):
    def scenario(self, failure):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            adb = root / 'adb'
            adb.write_text(FAKE_ADB)
            adb.chmod(0o755)
            apk = root / 'test.apk'
            apk.write_bytes(b'Test fixture only, not an APK')
            output = root / 'logs'
            env = dict(os.environ, HUD_TEST_FAILURE=failure,
                       PATH=str(root) + os.pathsep + os.environ.get('PATH', ''))
            run = subprocess.run([sys.executable, str(SCRIPT), '--apk', str(apk),
                                  '--output', str(output)], env=env, capture_output=True, text=True)
            self.assertEqual(run.returncode, 1, run.stdout + run.stderr)
            self.assertEqual(json.loads((output / 'result.json').read_text())['status'], 'FAILED')
            if failure == 'install':
                self.assertIn('TEST_INSTALL_FAILURE', (output / 'install.txt').read_text())
            else:
                self.assertIn('FAKE regression-test startup crash record',
                              (output / 'startup-logcat.txt').read_text())
                self.assertTrue((output / 'start.txt').is_file())
                self.assertTrue((output / 'package.txt').is_file())
    def test_install_failure_is_saved(self): self.scenario('install')
    def test_activity_launch_error_still_collects_logs(self): self.scenario('launch')
    def test_immediate_process_exit_still_collects_logs(self): self.scenario('pid')

if __name__ == '__main__': unittest.main(verbosity=2)
