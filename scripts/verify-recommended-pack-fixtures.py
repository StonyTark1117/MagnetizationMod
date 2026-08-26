#!/usr/bin/env python3
"""Validate the complete normalized manifests used by full-pack release smokes."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "compat-fixtures" / "recommended-modpacks.json"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    seen_profiles: set[tuple[str, int, int]] = set()
    for profile in catalog["individualAddonProfiles"]:
        identity = (profile["modId"], profile["projectId"], profile["fileId"])
        if identity in seen_profiles:
            raise SystemExit(f"duplicate individual addon profile: {identity}")
        seen_profiles.add(identity)

    for pack in catalog["packs"]:
        manifest_path = CATALOG.parent / pack["normalizedManifest"]
        if digest(manifest_path) != pack["normalizedManifestSha256"]:
            raise SystemExit(f"normalized manifest digest drifted: {manifest_path}")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        files = manifest["files"]
        if len(files) != pack["fileCount"]:
            raise SystemExit(f"manifest file count drifted for {pack['name']}")
        if manifest["minecraft"]["version"] != pack["minecraft"]:
            raise SystemExit(f"Minecraft version drifted for {pack['name']}")
        loaders = manifest["minecraft"]["modLoaders"]
        if not any(loader["primary"] and loader["id"] == pack["loader"] for loader in loaders):
            raise SystemExit(f"primary loader drifted for {pack['name']}")
        expected = pack["magnetization"]
        matches = [entry for entry in files
                   if entry["projectID"] == expected["projectId"]
                   and entry["fileID"] == expected["fileId"]]
        if len(matches) != 1 or not matches[0]["required"]:
            raise SystemExit(f"bundled Magnetization pin drifted for {pack['name']}")

    print(f"Verified {len(catalog['packs'])} complete recommended-pack manifests "
          f"and {len(seen_profiles)} individual-addon pins")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
