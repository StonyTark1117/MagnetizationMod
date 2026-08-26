#!/usr/bin/env python3
"""Verify that an exact pre-release TOML configuration survived a pack boot."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import tomllib


def flatten(value: dict[str, object], prefix: tuple[str, ...] = ()) -> dict[str, object]:
    flattened: dict[str, object] = {}
    for key, child in value.items():
        path = prefix + (key,)
        if isinstance(child, dict):
            flattened.update(flatten(child, path))
        else:
            flattened[".".join(path)] = child
    return flattened


def load(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        return flatten(tomllib.load(source))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--require-added", action="append", default=[])
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    before = load(args.before)
    after = load(args.after)
    missing = {key: value for key, value in before.items() if key not in after}
    changed = {
        key: {"before": value, "after": after[key]}
        for key, value in before.items()
        if key in after and after[key] != value
    }
    absent_added = [key for key in args.require_added if key not in after]
    added = {key: value for key, value in after.items() if key not in before}
    report = {
        "beforeKeys": len(before),
        "afterKeys": len(after),
        "preservedKeys": len(before) - len(missing) - len(changed),
        "missing": missing,
        "changed": changed,
        "added": added,
        "requiredAddedMissing": absent_added,
    }
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 1 if missing or changed or absent_added else 0


if __name__ == "__main__":
    raise SystemExit(main())
