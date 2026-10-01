#!/usr/bin/env python3
"""Require real client/force rows, clean shutdowns and matching persisted NBT."""
import argparse
import gzip
import io
import json
import re
import struct
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_nbt(path):
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))

    def number(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number('H')).decode('utf-8')

    def payload(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 7:
            return stream.read(number('i'))
        if kind == 8:
            return string()
        if kind == 9:
            element = number('B')
            return [payload(element) for _ in range(number('i'))]
        if kind == 10:
            result = {}
            while (child := number('B')):
                name = string()
                result[name] = payload(child)
            return result
        if kind in (11, 12):
            return [number('i' if kind == 11 else 'q') for _ in range(number('i'))]
        raise ValueError(f'Unsupported NBT type {kind}')

    require(number('B') == 10, f'{path}: expected a compound root')
    string()
    result = payload(10)
    require(stream.read() == b'', f'{path}: unconsumed NBT bytes')
    return result


def position(packed):
    def signed(value, bits):
        return value - (1 << bits) if value & (1 << (bits - 1)) else value
    return [signed((packed >> 38) & ((1 << 26) - 1), 26),
            signed(packed & 4095, 12), signed((packed >> 12) & ((1 << 26) - 1), 26)]


def analyze(root):
    expected = {
        'create': [('ae2', True), ('native', True), ('native', False), ('none', False), ('ae2', True)],
        'verify': [('ae2', True), ('native', True), ('native', False), ('ae2', True)],
    }
    result = {'phases': {}}
    saved = {}
    for phase, stages in expected.items():
        server = (root / phase / 'server.log').read_text()
        client = (root / phase / 'client.log').read_text()
        require('AE_AUDIT_SERVER_FAILED' not in server and 'AE_AUDIT_CLIENT_FAILED' not in client, f'{phase}: audit failure')
        require('All dimensions are saved' in server, f'{phase}: no clean full-save shutdown')
        require(f'AE_AUDIT_SERVER_PASS phase={phase}' in server, f'{phase}: server did not finish')
        observations = re.findall(r'AE_AUDIT_CLIENT_PASS phase=(\w+) stage=(\d+) selection=(\w+) packetPos=(.*?) packetExpires=(-?\d+) clientTime=(-?\d+) angle=([\d.]+) texture=(\S+) quads=(\d+) pid=(\d+)', client)
        forces = re.findall(r'AE_AUDIT_FORCE_PASS phase=(\w+) stage=(\d+) enabled=(\w+) samples=(\d+) aeDelta=(\S+) nativeDelta=(\S+)', server)
        require(len(observations) == len(stages) and len(forces) == len(stages), f'{phase}: missing/duplicate stage rows')
        rows = []
        for index, (selection, hook) in enumerate(stages):
            p, stage, actual, packet, expiry, now, angle, texture, quads, pid = observations[index]
            require(p == phase and int(stage) == index and actual == selection, f'{phase}/{index}: wrong client stage')
            expected_angle = {'ae2': .75, 'native': .5, 'none': 0}[selection]
            require(float(angle) == expected_angle, f'{phase}/{index}: wrong selected target angle')
            frame = int(expected_angle * 32)
            require(texture == f'magnetization:item/cosmic_compass_{frame:02d}' and int(quads) > 0, f'{phase}/{index}: wrong rendered frame')
            require(packet == ('Optional[BlockPos{x=-40, y=100, z=0}]' if hook else 'Optional.empty'), f'{phase}/{index}: wrong delivered packet')
            require(not hook or int(expiry) > int(now), f'{phase}/{index}: stale delivered target')
            fp, fs, enabled, samples, ae, native = forces[index]
            require(fp == phase and int(fs) == index and (enabled == 'true') == hook and int(samples) >= 10, f'{phase}/{index}: invalid force sampling')
            require(float(ae) > 1e-6 if hook else abs(float(ae)) < 1e-12, f'{phase}/{index}: AE2 suppression failed')
            if selection != 'none':
                require(float(native) > 1e-6, f'{phase}/{index}: native control force missing')
            screenshot = root / phase / 'screenshots' / f'ae-compass-{phase}-{index}-{selection}.png'
            require(screenshot.is_file() and screenshot.stat().st_size > 1000, f'{phase}/{index}: rendered screenshot missing')
            rows.append({'stage': index, 'selection': selection, 'hook': hook, 'angle': float(angle), 'frame': frame,
                         'packet': packet, 'packetExpires': int(expiry), 'clientTime': int(now), 'clientPid': int(pid),
                         'forceSamples': int(samples), 'aeVelocityDelta': float(ae), 'nativeVelocityDelta': float(native),
                         'screenshot': str(screenshot.relative_to(root))})
        saved[phase] = sorted((position(e['Pos']), e['ChargedAt']) for e in read_nbt(root / phase / 'saved-data' / 'magnetization_meteorite_fields.dat')['data']['Entries'])
        result['phases'][phase] = {'stages': rows, 'savedSources': saved[phase],
                                   'savedGameTime': read_nbt(root / phase / 'saved-data' / 'level.dat')['Data']['Time']}
    require(saved['create'] == saved['verify'] and len(saved['create']) == 2, 'Save/restart changed virtual source positions or charge times')
    require([p for p, _ in saved['create']] == [[-40, 100, 0], [5, 100, 0]], 'Unexpected persisted sources')
    restored = re.search(r'AE_AUDIT_RESTART_PASS creatorPid=(\d+) verifierPid=(\d+) sources=2 activeChargedAt=(-?\d+) deadChargedAt=(-?\d+) nativeChargedAt=(-?\d+) now=(\d+)', (root / 'verify/server.log').read_text())
    require(restored is not None, 'Missing full restart verification')
    creator, verifier, active, dead, native, now = map(int, restored.groups())
    require(creator != verifier, 'Restart reused the original server JVM')
    require(saved['create'] == [([-40, 100, 0], active), ([5, 100, 0], dead)], 'Loaded charge times do not match saved NBT')
    require(now >= result['phases']['create']['savedGameTime'], 'Restored world clock rolled back')
    result['restart'] = {'creatorPid': creator, 'verifierPid': verifier, 'activeChargedAt': active,
                         'deadChargedAt': dead, 'nativeChargedAt': native, 'restoredGameTime': now}
    result['result'] = 'pass'
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('evidence', nargs='?', type=Path, default=Path('build/ae-meteorite-audit'))
    args = parser.parse_args()
    summary = analyze(args.evidence)
    output = args.evidence / 'summary.json'
    output.write_text(json.dumps(summary, indent=2) + '\n')
    print(f'AE2 audit verified: 9 real-client/render/force stages, full save/restart, unchanged persisted NBT. {output}')
