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
# A failed rerun must not leave an earlier passing report at this path.
a.output.unlink(missing_ok=True)
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
strengths = set(re.findall(r'AIRCRAFT_ORDINARY type=\S+ .*?strength=(\w+) force=([\d.]+) ticks=400', server))
if ordinary:
    assert len(strengths) == 1, 'Mixed or missing nominal strength evidence'
speed_limits = set(re.findall(r'AIRCRAFT_ORDINARY .*?speedLimit=([\d.]+)', server))
if ordinary:
    assert len(speed_limits) <= 1, 'Mixed magnetic speed limits'
versions = {}
for label, log in [('server', server), ('pilot', pilot), ('observer', observer)]:
    found = re.search(r'^\s+Sable ([^ ]+) \(sable\)', log, re.MULTILINE)
    assert found, f'Missing {label} Sable version evidence'
    versions[label] = found[1]
assert len(set(versions.values())) == 1, 'Server/client Sable versions differ'
rows = []
for kind, uid in begins:
    traces = {}
    for label, log, is_pilot in [('pilot', pilot, 'true'), ('observer', observer, 'false')]:
        pattern = (r'AIRCRAFT_CLIENT player=\S+ type=' + re.escape(kind) + r' uuid=' + re.escape(uid)
                   + r' tick=(\d+) x=([-\d.Ee]+) y=([-\d.Ee]+) z=([-\d.Ee]+) vx=([-\d.Ee]+) pilot=' + is_pilot + r' entityTicks=(\d+)')
        points = [tuple(map(float, m)) for m in re.findall(pattern, log)]
        assert len(points) >= 8, f'{kind}: insufficient {label} observations ({len(points)})'
        changes = [b[1] - a[1] for a, b in zip(points, points[1:])]
        assert min(changes) < -0.01 and max(changes) > 0.01, f'{kind}: {label} did not observe both directions'
        traces[label] = {'samples': len(points), 'min_dx': min(changes), 'max_dx': max(changes)}
        if ordinary:
            # Use the interiors of each phase, avoiding spawn/network transition edges.
            attraction = [point for point in points if 130 <= point[5] <= 210]
            repulsion = [point for point in points if 270 <= point[5] <= 350]
            assert len(attraction) >= 4 and len(repulsion) >= 4, f'{kind}: insufficient {label} phase observations'
            attract_dx = attraction[-1][1] - attraction[0][1]
            repel_dx = repulsion[-1][1] - repulsion[0][1]
            assert attract_dx < -2 and repel_dx > 2, f'{kind}: {label} did not sustain each commanded direction ({attract_dx}, {repel_dx})'
            traces[label].update({'attraction_phase_dx': attract_dx, 'repulsion_phase_dx': repel_dx})

    row = {'entity_type': kind, 'uuid': uid, **traces}
    if ordinary:
        fuel = re.search(r'AIRCRAFT_FUEL_PASS type='+re.escape(kind)+r' initial=(\d+) remaining=(\d+) creative=false',server)
        recipe = re.search(r'AIRCRAFT_CRAFT_PASS type='+re.escape(kind)+r' recipe=(\S+) inputs=(\d+)',server)
        assert fuel and recipe, f'{kind}: missing native survival/crafting evidence'
        row.update({'initial_coal':int(fuel[1]),'remaining_coal':int(fuel[2]),'recipe':recipe[1],'crafting_inputs':int(recipe[2])})
        server_points = dict((int(age), float(x)) for age, x in re.findall(
            r'AIRCRAFT_SERVER type='+re.escape(kind)+r' uuid='+re.escape(uid)+r' age=(\d+) x=([-\d.Ee]+)', server))
        assert all(age in server_points for age in [100,240,380]), f'{kind}: missing server phase boundaries'
        attract_dx = server_points[240] - server_points[100]
        repel_dx = server_points[380] - server_points[240]
        assert attract_dx < -2 and repel_dx > 2, f'{kind}: server did not sustain each commanded direction ({attract_dx}, {repel_dx})'
        row['server'] = {'attraction_phase_dx': attract_dx, 'repulsion_phase_dx': repel_dx}

    rows.append(row)
a.output.write_text(json.dumps({'status': 'passed', 'observer_movement_warning_count': len(re.findall(r'AuditObserver moved too quickly', server)), 'sable_versions': versions, 'magnetic_speed_limit': float(next(iter(speed_limits))) if speed_limits else None, 'real_clients': 2, 'ticks_per_aircraft': 400 if ordinary else 180, 'survival': ordinary, 'nominal_strength': ordinary, 'strength': dict(zip(['tier', 'force'], next(iter(strengths)))) if ordinary else None,
                                'aircraft': rows}, indent=2) + '\n')
print(f'Passed: {len(rows)} aircraft, real pilot and observer, both motion directions')
