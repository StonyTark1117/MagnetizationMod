#!/usr/bin/env python3
"""Require real server, pilot and observer traces for all 15 pinned aircraft types."""
import argparse
import json
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('server', type=Path)
parser.add_argument('pilot', type=Path)
parser.add_argument('observer', type=Path)
parser.add_argument('--output', type=Path, required=True)
a = parser.parse_args()
server, pilot, observer = [p.read_text() for p in (a.server, a.pilot, a.observer)]
assert 'AIRCRAFT_NETWORK_SERVER_PASS types=15' in server, 'Missing complete server pass'
assert 'AIRCRAFT_NETWORK_FAILED' not in server, 'Server audit failed'
begins = re.findall(r'AIRCRAFT_BEGIN type=(\S+) uuid=(\S+)', server)
assert len(begins) == 15 and len({t for t, _ in begins}) == 15, 'Wrong aircraft coverage'
rows = []
for kind, uid in begins:
    traces = {}
    for label, log, is_pilot in [('pilot', pilot, 'true'), ('observer', observer, 'false')]:
        pattern = (r'AIRCRAFT_CLIENT player=\S+ type=' + re.escape(kind) + r' uuid=' + re.escape(uid)
                   + r' tick=(\d+) x=([-\d.Ee]+) y=([-\d.Ee]+) z=([-\d.Ee]+) vx=([-\d.Ee]+) pilot=' + is_pilot)
        points = [tuple(map(float, m)) for m in re.findall(pattern, log)]
        assert len(points) >= 8, f'{kind}: insufficient {label} observations ({len(points)})'
        changes = [b[1] - a[1] for a, b in zip(points, points[1:])]
        assert min(changes) < -0.01 and max(changes) > 0.01, f'{kind}: {label} did not observe both directions'
        traces[label] = {'samples': len(points), 'min_dx': min(changes), 'max_dx': max(changes)}
    rows.append({'entity_type': kind, 'uuid': uid, **traces})
a.output.write_text(json.dumps({'status': 'passed', 'real_clients': 2, 'ticks_per_aircraft': 180,
                                'aircraft': rows}, indent=2) + '\n')
print(f'Passed: {len(rows)} aircraft, real pilot and observer, both motion directions')
