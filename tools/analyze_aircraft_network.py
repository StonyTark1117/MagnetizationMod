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
ordinary = 'AIRCRAFT_ORDINARY' in server
if ordinary:
    assert not re.search(r'audit-\d+ .*moved too quickly', server), 'Vehicle movement validation rejected the magnetic run'
    assert len(re.findall(r'AIRCRAFT_CRAFT_PASS type=', server)) == 15, 'Missing native crafting evidence'
    assert len(re.findall(r'AIRCRAFT_FUEL_PASS type=.*creative=false', server)) == 15, 'Missing survival fuel evidence'
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
    row = {'entity_type': kind, 'uuid': uid, **traces}
    if ordinary:
        fuel = re.search(r'AIRCRAFT_FUEL_PASS type='+re.escape(kind)+r' initial=(\d+) remaining=(\d+) creative=false',server)
        recipe = re.search(r'AIRCRAFT_CRAFT_PASS type='+re.escape(kind)+r' recipe=(\S+) inputs=(\d+)',server)
        assert fuel and recipe, f'{kind}: missing native survival/crafting evidence'
        row.update({'initial_coal':int(fuel[1]),'remaining_coal':int(fuel[2]),'recipe':recipe[1],'crafting_inputs':int(recipe[2])})
    rows.append(row)
a.output.write_text(json.dumps({'status': 'passed', 'real_clients': 2, 'ticks_per_aircraft': 400 if ordinary else 180, 'survival': ordinary, 'nominal_strength': ordinary,
                                'aircraft': rows}, indent=2) + '\n')
print(f'Passed: {len(rows)} aircraft, real pilot and observer, both motion directions')
