#!/usr/bin/env python3
"""Validate live Slugterra audit logs and summarize client trajectory observations.

Match by UUID and wall time: Minecraft periodically corrects the client world
clock, so comparing equal game-time values alone gives false drift after stalls.
The window allows normal packet latency/interpolation; this is not a WAN test.
"""
import argparse
from datetime import datetime
import json
import math
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path, help='build/slugterra-audit or archived roles with logs/latest.log')
args = parser.parse_args()
root = args.directory
server = (root / 'server/logs/latest.log').read_text()
if 'AUDIT_COMPLETE cases=64 failures=[]' not in server or 'AUDIT_FAILURE' in server:
    raise SystemExit('Server audit has not passed all 64 cases')
pattern = re.compile(r'^\[([^]]+)\].*?tick=(\d+) uuid=([\w-]+).*?pos=\(([^)]+)\)')
cases = dict(re.findall(r'CASE_START case=(\d+).*?uuid=([\w-]+)', server))
if len(cases) != 64:
    raise SystemExit(f'Expected 64 distinct cases, found {len(cases)}')

def sample(line):
    match = pattern.search(line)
    if not match:
        return None
    return (datetime.strptime(match[1], '%d%b%Y %H:%M:%S.%f').timestamp(),
            match[3], tuple(map(float, match[4].split(','))))

paths = {}
for line in server.splitlines():
    if 'SERVER_SAMPLE' in line:
        row = sample(line)
        if row:
            paths.setdefault(row[1], []).append((row[0], row[2]))
report = {'server_cases': 64, 'clients': {}, 'matching_window_seconds': [-0.20, 0.35]}
for role in ('client', 'observer'):
    log = (root / role / 'logs/latest.log').read_text()
    visible = set(re.findall(r'RENDER_FRAME uuid=([\w-]+) visible=true', log))
    observed = set()
    errors = []
    for line in log.splitlines():
        if 'CLIENT_SAMPLE' not in line:
            continue
        row = sample(line)
        if not row or row[1] not in paths:
            continue
        observed.add(row[1])
        candidates = [pos for timestamp, pos in paths[row[1]] if -0.20 <= row[0] - timestamp <= 0.35]
        if candidates:
            errors.append(min(math.dist(row[2], pos) for pos in candidates))
    missing = set(cases.values()) - observed
    invisible = set(cases.values()) - visible
    if missing or invisible or not errors:
        raise SystemExit(f'{role}: missing {len(missing)} observed and {len(invisible)} visible cases')
    errors.sort()
    stats = {'observed_cases': len(observed), 'visible_cases': len(set(cases.values()) & visible),
             'matched_samples': len(errors), 'position_error_p95_blocks': errors[int(len(errors) * .95)],
             'position_error_max_blocks': max(errors)}
    report['clients'][role] = stats
    if stats['position_error_p95_blocks'] > 2 or stats['position_error_max_blocks'] > 4:
        raise SystemExit(f'{role}: investigate trajectory divergence: {stats}')
print(json.dumps(report, indent=2))
