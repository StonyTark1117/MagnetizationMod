#!/usr/bin/env python3
"""Inventory optional tag references against supplied upstream JARs.

Resource matches are leads, NOT proof of registry membership or runtime behavior.
This deliberately never promotes an integration to 'verified' from static data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jars", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--include-mod", action="append", default=["golems"],
                        help="Inspect a mod whose actual ID differs from our references")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    refs = []
    for source in ("src/main/resources", "src/generated/resources"):
        for path in sorted((root / source).glob("data/*/tags/**/*.json")):
            for value in json.loads(path.read_text()).get("values", []):
                rid = value.get("id") if isinstance(value, dict) else value
                namespace = rid.lstrip("#").split(":")[0]
                if namespace in {"minecraft", "magnetization", "c", "neoforge", "create"}:
                    continue
                refs.append({"source": str(path.relative_to(root)), "id": rid,
                             "namespace": namespace,
                             "required": value.get("required", True) if isinstance(value, dict) else True})
    wanted = {r["namespace"] for r in refs} | set(args.include_mod)
    artifacts = []
    errors = []
    seen = set()
    for directory in args.jars:
        for path in sorted(directory.rglob("*.jar")):
            if path.name.endswith(("-sources.jar", "-javadoc.jar")):
                continue  # Unexpanded source descriptors are not runtime artifacts.
            try:
                with zipfile.ZipFile(path) as jar:
                    names = set(jar.namelist())
                    descriptor = next((n for n in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml") if n in names), None)
                    if descriptor is None:
                        continue
                    metadata = tomllib.loads(jar.read(descriptor).decode())
                    mods = [m for m in metadata.get("mods", []) if m.get("modId") in wanted]
                    if not mods:
                        continue
                    digest = hashlib.sha256(path.read_bytes()).hexdigest()
                    if digest in seen:
                        continue
                    seen.add(digest)
                    evidence = []
                    for ref in refs:
                        if ref["namespace"] not in {m["modId"] for m in mods}:
                            continue
                        ns, rid = ref["id"].lstrip("#").split(":", 1)
                        kind = ref["source"].split("/tags/", 1)[1].split("/", 1)[0]
                        if ref["id"].startswith("#"):
                            candidates = [f"data/{ns}/tags/{kind}/{rid}.json"]
                        elif kind == "damage_type":
                            candidates = [f"data/{ns}/damage_type/{rid}.json"]
                        elif kind == "block":
                            candidates = [f"assets/{ns}/blockstates/{rid}.json"]
                        elif kind == "item":
                            candidates = [f"assets/{ns}/models/item/{rid}.json"]
                        else:
                            candidates = [f"data/{ns}/loot_table/entities/{rid}.json"]
                        evidence.append({"id": ref["id"], "source": ref["source"],
                                         "matching_resources": [n for n in candidates if n in names]})
                    artifacts.append({"filename": path.name, "sha256": digest,
                                      "descriptor": descriptor, "mods": mods,
                                      "dependencies": metadata.get("dependencies", {}),
                                      "resource_evidence": evidence})
            except (OSError, zipfile.BadZipFile, tomllib.TOMLDecodeError, UnicodeDecodeError) as exc:
                errors.append({"filename": path.name, "error": str(exc)})
    result = {"scope": "Static optional-tag inventory; registry resolution and behavior require runtime tests",
              "references": refs, "artifacts": artifacts, "scan_errors": errors,
              "namespaces_without_artifact": sorted(wanted - {m["modId"] for a in artifacts for m in a["mods"]})}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"{len(refs)} references, {len(artifacts)} distinct artifacts; {len(errors)} scan errors")
    print("No supplied artifact: " + ", ".join(result["namespaces_without_artifact"]))


if __name__ == "__main__":
    main()
