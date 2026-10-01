#!/usr/bin/env python3
"""Attribute sampled server-thread work to measured stress sprint intervals."""
import argparse
import collections
import datetime
import json
import re
from pathlib import Path
from zoneinfo import ZoneInfo


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('events', type=Path, help='JSON from jfr print --json --events jdk.ExecutionSample,jdk.ObjectAllocationSample')
    parser.add_argument('output', type=Path)
    parser.add_argument('--log-timezone', default='America/Phoenix')
    args = parser.parse_args()
    intervals = []
    scenario = start = None
    sample = 0
    for line in (args.report / 'latest.log').read_text().splitlines():
        match = re.match(r'\[([^]]+)\]', line)
        if not match:
            continue
        try:
            when = datetime.datetime.strptime(match[1], '%d%b%Y %H:%M:%S.%f').replace(
                tzinfo=ZoneInfo(args.log_timezone)).timestamp()
        except ValueError:
            continue
        match = re.search(r'MAG_STRESS_READY_(\w+)', line)
        if match:
            scenario, sample = match[1], 0
        if 'The game is sprinting' in line:
            start = when
        if 'Sprint completed with' in line and scenario and start is not None:
            if sample > 0:
                intervals.append((start, when, scenario))
            sample += 1
            start = None
    stats = {}
    for start, end, scenario in intervals:
        row = stats.setdefault(scenario, dict(sample_seconds=0, measured_intervals=0,
            execution_samples=0, mod_execution_samples=0, inclusive_mod_frames=collections.Counter(),
            nearest_mod_frames=collections.Counter(), allocation_weight=0, mod_allocation_weight=0,
            allocation_mod_frames=collections.Counter(), allocation_classes=collections.Counter()))
        row['sample_seconds'] += end - start
        row['measured_intervals'] += 1
    for event in json.loads(args.events.read_text())['recording']['events']:
        values = event['values']
        thread = values.get('sampledThread', values.get('eventThread', {}))
        if thread.get('javaName') != 'Server thread':
            continue
        when = datetime.datetime.fromisoformat(values['startTime']).timestamp()
        scenario = next((name for start, end, name in intervals if start <= when <= end), None)
        if scenario is None:
            continue
        names = [frame['method']['type']['name'].replace('/', '.') + '.' + frame['method']['name']
                 for frame in (values.get('stackTrace') or {}).get('frames', [])]
        mods = [name for name in names if name.startswith('com.stonytark.magnetization.')]
        row = stats[scenario]
        if event['type'] == 'jdk.ExecutionSample':
            row['execution_samples'] += 1
            if mods:
                row['mod_execution_samples'] += 1
                row['inclusive_mod_frames'].update(set(mods))
                row['nearest_mod_frames'][mods[0]] += 1
        elif event['type'] == 'jdk.ObjectAllocationSample':
            weight = values['weight']
            row['allocation_weight'] += weight
            if mods:
                row['mod_allocation_weight'] += weight
                row['allocation_mod_frames'].update({name: weight for name in set(mods)})
                row['allocation_classes'][values['objectClass']['name']] += weight
    for row in stats.values():
        for key in ('inclusive_mod_frames', 'nearest_mod_frames', 'allocation_mod_frames', 'allocation_classes'):
            row[key] = row[key].most_common()
    args.output.write_text(json.dumps(dict(method='Server-thread JFR events within measured sprint intervals only; setup, warmup and startup excluded. Inclusive frames overlap. Allocation weights are statistical estimates, not retained heap or exact bytes.',
        report=str(args.report.resolve()), scenarios=stats), indent=2) + '\n')


if __name__ == '__main__':
    main()
