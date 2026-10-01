#!/usr/bin/env python3
"""Model the pinned Registrate callback collection race; this is not a Minecraft startup gate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SHA256 = 'bc65dea7cfd9e4dacf8419d8af0e741655857d27885bb35d943d7187fc3a8fce'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--guava', type=Path, help='Path to pinned guava-32.1.2-jre.jar; defaults to Gradle cache')
parser.add_argument('--attempts', type=int, default=10000)
args = parser.parse_args()
if args.attempts <= 0:
    parser.error('--attempts must be positive')
if args.guava is None:
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    pins = list((cache/'caches/modules-2/files-2.1/com.google.guava/guava/32.1.2-jre').glob('*/guava-32.1.2-jre.jar'))
    if len(pins) != 1:
        parser.error('Supply --guava with the pinned runtime JAR')
    args.guava = pins[0]
if hashlib.sha256(args.guava.read_bytes()).hexdigest() != SHA256:
    parser.error('Guava artifact hash differs from the inspected Minecraft runtime')
out = ROOT/'build/compat-audit/startup-race'
out.mkdir(parents=True, exist_ok=True)
configured_home = re.search(r'^org.gradle.java.home=(.+)$', (ROOT/'gradle.properties').read_text(), re.MULTILINE)
java_home = os.environ.get('JAVA_HOME') or (configured_home[1] if configured_home else None)
def java_tool(name):
    return str(Path(java_home)/'bin'/name) if java_home else name
subprocess.run([java_tool('javac'), '-cp', str(args.guava), '-d', str(out),
                str(ROOT/'tools/compatibility/RegistrateCallbackRaceProbe.java')], check=True)
results = []
for mode in ['unsynchronized', 'synchronized']:
    completed = subprocess.run([java_tool('java'), '-cp', str(out)+os.pathsep+str(args.guava),
                                'RegistrateCallbackRaceProbe', str(args.attempts), mode],
                               capture_output=True, text=True, check=True, timeout=60)
    (out/(mode+'.log')).write_text(completed.stdout+completed.stderr)
    found = re.search(r'RACE_RESULT synchronized=(\w+) attempts=(\d+) inconsistent=(\d+) nonemptyWithoutEntries=(\d+)', completed.stdout)
    if found is None or 'SEQUENTIAL_CONTROL_PASS' not in completed.stdout:
        raise RuntimeError('Missing model result/control evidence')
    row = dict(zip(['attempts', 'inconsistent', 'nonemptyWithoutEntries'], map(int, found.groups()[1:])))
    row['synchronized'] = found[1] == 'true'
    results.append(row)
    print(completed.stdout.strip())
    if row['synchronized'] and row['inconsistent']:
        raise RuntimeError('Synchronized control was inconsistent')
(out/'results.json').write_text(json.dumps({'model_only': True, 'guava': '32.1.2-jre',
    'guava_sha256': SHA256, 'registrate': 'MC1.21-1.3.0+67', 'results': results}, indent=2)+'\n')
