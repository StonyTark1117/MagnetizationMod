#!/usr/bin/env python3
"""Verify complete native audit logs and copy reviewable evidence with provenance."""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--input', type=Path, default=Path('build/validation-audit'))
parser.add_argument('--output', type=Path, default=Path('docs/compatibility/evidence/lifecycle-presentation'))
args = parser.parse_args()
repo = Path(__file__).resolve().parent.parent
args.output.mkdir(parents=True, exist_ok=True)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


phases = ['initial', 'disabled', 'reenabled', 'repeat', 'gift-off']
report = {'reproduce': 'scripts/run-lifecycle-presentation-audit.sh', 'phases': {},
          'sources': {}, 'fullLogs': {}, 'artifacts': {}}
logs = {}
for phase in phases:
    texts = {}
    for role in ['server', 'client']:
        path = args.input / phase / (role + '.log')
        texts[role] = path.read_text()
        require('VALIDATION_' + role.upper() + '_FAILED' not in texts[role], f'{phase}/{role} failed')
        require(f'VALIDATION_{role.upper()}_PASS phase={phase}' in texts[role], f'{phase}/{role} incomplete')
        report['fullLogs'][f'{phase}/{role}.log'] = digest(path)
        # Retain all fixture events and original warning/error lines; command-line classpaths are omitted.
        excerpt = '\n'.join(line for line in texts[role].splitlines()
                            if 'VALIDATION_' in line or ('WARN]' in line or 'ERROR]' in line)
                            and 'Launch arguments' not in line and 'cpw.mods.modlauncher.Launcher/MODLAUNCHER' not in line)
        excerpt_path = args.output / f'{phase}-{role}.txt'
        excerpt_path.write_text(excerpt + '\n')
        report['artifacts'][excerpt_path.name] = digest(excerpt_path)
    require('All dimensions are saved' in texts['server'], f'{phase} did not save cleanly')
    login = re.search(r'VALIDATION_LOGIN_PASS phase=\S+ uuid=(\S+) manuals=(\d+) persistedFlag=(\S+) pid=(\d+)', texts['server'])
    require(login is not None, f'{phase} missing native login assertion')
    report['phases'][phase] = dict(zip(['uuid', 'manuals', 'persistedFlag', 'pid'], login.groups()))
    gifted = phase not in ['disabled', 'gift-off']
    require(login[2] == ('1' if gifted else '0') and login[3] == str(gifted).lower(),
            f'{phase} gift/count mismatch')
    if gifted:
        require(f'VALIDATION_MANUAL_OPEN_PASS phase={phase}' in texts['client'], f'{phase} manual did not open')
    logs[phase] = texts
    for screenshot in (args.input / phase / 'screenshots').glob('*.png'):
        target = args.output / 'screenshots' / screenshot.name
        target.parent.mkdir(exist_ok=True)
        shutil.copy2(screenshot, target)
        report['artifacts'][str(target.relative_to(args.output))] = digest(target)

require(report['phases']['initial']['uuid'] == report['phases']['repeat']['uuid'], 'Repeat player changed UUID')
require(report['phases']['disabled']['uuid'] == report['phases']['reenabled']['uuid'], 'Deferred player changed UUID')
require(len({v['pid'] for v in report['phases'].values()}) == 5, 'Restart checks reused JVMs')
initial = logs['initial']
expected_recipes = ['magnetite=true iron=false lodestone=true', 'magnetite=true iron=true lodestone=true',
                    'magnetite=false iron=true lodestone=true', 'magnetite=false iron=true lodestone=false',
                    'magnetite=false iron=false lodestone=false']
for combination in expected_recipes:
    require('VALIDATION_RECIPES_PASS ' + combination in initial['server'], 'Missing recipe state: ' + combination)
require(initial['server'].count('VALIDATION_RECIPES_PASS magnetite=true iron=false lodestone=true') == 2,
        'Default recipes not restored after re-enable')
require('VALIDATION_CLIENT_PACKAGE_OFF_PASS' in initial['client'] and 'VALIDATION_CLIENT_PACKAGE_ON_PASS' in initial['client'],
        'Missing connected-client lifecycle assertions')
catalog = repo / 'src/main/java/com/stonytark/magnetization/compat/ponder/PonderSceneCatalog.java'
expected_scenes = set(re.findall(r'(?:custom|generic)\("([a-z_]+)"', catalog.read_text()))
scenes = re.findall(r'VALIDATION_PONDER_PASS id=magnetization:(\S+) elapsed=(\d+) total=(\d+)', initial['client'])
require(len(scenes) == len(expected_scenes) and {s[0] for s in scenes} == expected_scenes, 'Advertised Ponder scene coverage incomplete')
require(all(int(elapsed) >= int(total) > 0 for _, elapsed, total in scenes), 'Incomplete Ponder playback')
report['ponder'] = [{'id': name, 'elapsed': int(elapsed), 'total': int(total)} for name, elapsed, total in scenes]
styles = re.findall(r'VALIDATION_STYLE_PASS style=(\S+)', initial['client'])
fixture = (repo / 'src/main/java/com/stonytark/magnetization/gametest/LifecyclePresentationAudit.java').read_text()
style_array = fixture.split('public static final String[] STYLES =', 1)[1].split(';', 1)[0]
expected_styles = set(re.findall(r'"([a-z_]+)"', style_array))
require(len(styles) == len(set(styles)) == len(expected_styles) and set(styles) == expected_styles,
        'Advertised Track Styles coverage incomplete')
report['trackStyles'] = styles
powers = re.findall(r'VALIDATION_POWER_VISUAL_PASS mode=(\S+) field=(\S+) model=(\S+)', initial['client'])
require(len(powers) == 8, 'Expected eight powered-visual transition captures')
for mode in ['magnet', 'brake']:
    require([power for actual_mode, power, _ in powers if actual_mode == mode] == ['false', 'true', 'false', 'true'],
            'Incomplete field transition sequence for ' + mode)
report['poweredVisuals'] = [dict(zip(['mode', 'field', 'model'], p)) for p in powers]
for scene in expected_scenes:
    for frame in ['first', 'last']:
        require((args.output / 'screenshots' / f'validation-ponder-{scene}-{frame}.png').is_file(), 'Missing scene frame: ' + scene)
for source in ['build.gradle', 'gradle.properties', 'scripts/run-lifecycle-presentation-audit.sh', 'scripts/summarize-lifecycle-presentation-audit.py',
               'src/main/java/com/stonytark/magnetization/client/LifecyclePresentationAuditClient.java',
               'src/main/java/com/stonytark/magnetization/gametest/LifecyclePresentationAudit.java',
               'src/main/java/com/stonytark/magnetization/network/CommonConfigSyncPayload.java',
               'src/main/java/com/stonytark/magnetization/client/MagPonderPlugin.java',
               'src/main/java/com/stonytark/magnetization/compat/ponder/PonderSceneCatalog.java',
               'src/main/resources/assets/magnetization/lang/en_us.json',
               'src/main/java/com/stonytark/magnetization/content/FieldManualGiver.java']:
    report['sources'][source] = digest(repo / source)
report['runtimeArtifacts'] = {}
classpath = args.input / 'initial/runtime-classpath.txt'
for entry in classpath.read_text().splitlines():
    artifact = Path(entry)
    if artifact.is_file() and artifact.suffix == '.jar':
        report['runtimeArtifacts'][artifact.name] = digest(artifact)
properties = dict(re.findall(r'(?m)^([a-z_]+)=(.*)$', (repo / 'gradle.properties').read_text()))
for key in ['simulated_coasters_test_file', 'simulated_coasters_track_styles_test_file',
            'coasters_magnetized_test_file', 'steam_rails_test_file', 'patchouli_version', 'copycats_test_version']:
    pin = properties[key]
    require(any(pin in name for name in report['runtimeArtifacts']), 'Missing runtime artifact pin: ' + pin)
graphics = args.input / 'graphics.txt'
if graphics.is_file():
    graphics_target = args.output / graphics.name
    graphics_target.write_text(graphics.read_text().rstrip() + '\n')
    report['artifacts'][graphics.name] = digest(graphics_target)
(args.output / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
print(f'PASS: {len(phases)} process pairs, {len(scenes)} Ponder scenes, {len(styles)} styles, {len(powers)} field visual captures')
