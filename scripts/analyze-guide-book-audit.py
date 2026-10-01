#!/usr/bin/env python3
"""Check native guide sweep coverage; screenshots still require visual review.

Run against the exact source snapshot used by the client, not a changing checkout.
The lifecycle report supplies the complementary runtime/artifact provenance.
"""
import argparse
import hashlib
import json
import math
import re
import struct
from pathlib import Path

PASSES = {0: ('en_us', 2), 1: ('en_us', 3), 2: ('de_de', 2), 3: ('de_de', 3)}
LINK = re.compile(r'\$\(l:(magnetization:[^)#]+)(?:#[^)]*)?\)')
PAGE_CLASSES = {'patchouli:text': 'PageText', 'patchouli:spotlight': 'PageSpotlight',
                'patchouli:crafting': 'PageCrafting', 'patchouli:smelting': 'PageSmelting'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def numeric(value):
    return type(value) in (int, float) and math.isfinite(value)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inventory(repo):
    root = repo / 'src/main/resources/assets/magnetization'
    lang = json.loads((root / 'lang/en_us.json').read_text())
    entries = root / 'patchouli_books/field_manual/en_us/entries'
    authored, sources = {}, {}
    for path in sorted(entries.rglob('*.json')):
        entry_id = 'magnetization:' + path.relative_to(entries).with_suffix('').as_posix()
        data = json.loads(path.read_text())
        for index, page in enumerate(data['pages'], 1):
            authored[(entry_id, index)] = page
        sources[str(path.relative_to(repo))] = digest(path)
    require(authored, 'No authored pages found in source snapshot')
    sources[str((root / 'lang/en_us.json').relative_to(repo))] = digest(root / 'lang/en_us.json')
    return authored, lang, sources


def verify(records, authored, lang, screenshots):
    seen, spreads, captures = set(), set(), {}
    clicked = 0
    min_scale = 1.0
    expected = {(p, entry, index) for p in PASSES for entry, index in authored}
    for row in records:
        phase = row['pass']
        require(phase in PASSES, f'Unexpected pass {phase}')
        require((row['locale'], row['guiScale']) == PASSES[phase], f'Wrong locale/GUI scale in pass {phase}')
        effective = row.get('effectiveGuiScale')
        require(numeric(effective) and effective == PASSES[phase][1],
                f'Missing or clamped effective GUI scale in pass {phase}: {effective}')
        entry, spread = row['entry'], row['spread']
        require(isinstance(spread, int) and spread >= 0, f'Invalid spread in {entry}')
        spread_key = (phase, entry, spread)
        require(spread_key not in spreads, f'Duplicate spread {spread_key}; use a fresh JSONL file')
        spreads.add(spread_key)
        expected_indices = [i for i in (spread * 2 + 1, spread * 2 + 2) if (entry, i) in authored]
        require(expected_indices and [p['page'] for p in row['pages']] == expected_indices,
                f'Incomplete or unexpected spread {spread_key}')
        name = row['capture']
        require(Path(name).name == name and name.endswith('.png'), f'Invalid capture name {name}')
        require(name not in captures, f'Capture reused for another spread: {name}')
        path = screenshots / name
        require(path.is_file(), f'Missing native capture {path}')
        with path.open('rb') as image:
            header = image.read(24)
            if len(header) == 24:
                image.seek(-12, 2)
            trailer = image.read(12)
        require(len(header) == 24 and header[:8] == b'\x89PNG\r\n\x1a\n' and header[12:16] == b'IHDR',
                f'Capture is not a PNG: {path}')
        require(trailer == b'\0\0\0\0IEND\xaeB`\x82', f'Truncated PNG capture: {path}')
        width, height = struct.unpack('>II', header[16:24])
        require(width >= 320 and height >= 240, f'Capture is too small: {path}')
        captures[name] = {'sha256': digest(path), 'width': width, 'height': height}
        for page in row['pages']:
            index = page['page']
            key = (phase, entry, index)
            require(key in expected and key not in seen, f'Unexpected or repeated native page {key}')
            seen.add(key)
            source = authored[(entry, index)]
            require(isinstance(page.get('definition'), dict), f'No native page definition: {key}')
            for field, value in source.items():
                require(page['definition'].get(field) == value, f'Native/source definition differs: {key}/{field}')
            require(bool(page.get('class')), f'No native page class: {key}')
            if source.get('type') in PAGE_CLASSES:
                require(page['class'] == PAGE_CLASSES[source['type']], f'Wrong native page class: {key}')
            if source.get('type') in PAGE_CLASSES:
                width = page.get('titleWidth')
                require(numeric(width) and 0 <= width <= 116,
                        f'Missing or overflowing native title width: {key}/{width}')
            text_key = source.get('text', '')
            text = lang.get(text_key, text_key)
            if text:
                require('textScale' in page and page.get('words', 0) > 0, f'Native text layout missing: {key}')
                bounds = page.get('wordBounds')
                require(isinstance(bounds, dict) and all(numeric(bounds.get(k))
                        for k in ('maxX', 'maxY', 'widthLimit', 'heightLimit')),
                        f'Native word bounds missing/invalid: {key}')
                require(bounds['widthLimit'] == 116 and bounds['heightLimit'] == 156,
                        f'Unexpected native page limits: {key}')
                require(bounds['maxX'] <= 116 and bounds['maxY'] <= 156,
                        f'Native word bounds overflow: {key}/{bounds}')
            if 'textScale' in page:
                scale = page['textScale']
                # Native captures show lost glyph strokes even at 0.98 on GUI
                # scale 2. Paginate authored text rather than accepting shrink.
                require(numeric(scale) and scale == 1.0,
                        f'Undersized/invalid native text scale {scale}: {key}')
                min_scale = min(min_scale, scale)
            if phase == 0:
                targets = LINK.findall(text)
                require(page.get('clickedLinks', 0) == len(targets), f'Native click coverage differs: {key}')
                clicked += len(targets)
    missing = sorted(expected - seen)
    require(not missing, f'Missing {len(missing)} native pages: {missing[:8]}')
    require(clicked > 0, 'Authored-link interaction coverage is vacuous')
    return {'authoredPages': len(authored), 'renderedPages': len(seen), 'spreads': len(spreads),
            'clickedLinks': clicked, 'minimumTextScale': min_scale, 'captures': captures,
            'passes': {str(k): {'locale': v[0], 'guiScale': v[1]} for k, v in PASSES.items()},
            'limits': 'Checks complete native records, source definitions, effective GUI scale, text-scale threshold, visible word bounds, title width, '
                      'reported clicks and capture provenance. Does not independently prove visual '
                      'readability, item rendering, machine behavior or non-English translation quality.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--records', required=True, type=Path)
    parser.add_argument('--screenshots', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    try:
        authored, lang, sources = inventory(args.repo)
        records = [json.loads(line) for line in args.records.read_text().splitlines() if line.strip()]
        report = verify(records, authored, lang, args.screenshots)
        report.update(recordsSha256=digest(args.records), sources=sources,
                      analyzerSha256=digest(Path(__file__)))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n')
        print(f"PASS: {report['renderedPages']} native pages, {report['spreads']} spreads, "
              f"{report['clickedLinks']} clicked links across four passes")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f'FAIL: {error}\n')


if __name__ == '__main__':
    main()
