#!/usr/bin/env python3
"""Generate the complete Magnetization config reference from the Java spec."""

from __future__ import annotations

import argparse
import ast
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/com/stonytark/magnetization/config/MagConfig.java"
LANG = ROOT / "src/main/resources/assets/magnetization/lang/en_us.json"
OUTPUT = ROOT / "docs/configuration.md"


def java_string(value: str) -> str:
    try:
        return ast.literal_eval(value)
    except (SyntaxError, ValueError):
        return value[1:-1]


def quoted_strings(value: str) -> list[str]:
    return [java_string(m.group(0)) for m in re.finditer(r'"(?:\\.|[^"\\])*"', value)]


def split_java_args(value: str) -> list[str]:
    """Split a Java argument list without breaking nested List.of calls."""
    parts: list[str] = []
    start = 0
    depth = 0
    quoted = False
    escaped = False
    for index, char in enumerate(value):
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
            continue
        if char == '"':
            quoted = True
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            parts.append(value[start:index].strip())
            start = index + 1
    parts.append(value[start:].strip())
    return parts


def normalize_default(value: str) -> str:
    value = value.strip().replace("_", "")
    if value in {"true", "false"}:
        return value
    if value.startswith("List.of(") and value.endswith(")"):
        inner = value[len("List.of("):-1]
        return "[" + ", ".join(normalize_default(part) for part in split_java_args(inner)) + "]"
    if value.startswith('"'):
        return java_string(value)
    if re.fullmatch(r"-?(?:\d+(?:\.\d*)?|\.\d+)[dDfFlL]?", value):
        return value.rstrip("dDfFlL")
    if re.fullmatch(r"[A-Za-z0-9_.]+", value):
        return value.rsplit(".", 1)[-1]
    return value


def parse_spec() -> list[dict[str, str]]:
    lines = SOURCE.read_text().splitlines()
    sections: dict[str, list[str]] = {"b": [], "sb": []}
    owners: dict[str, str] = {}
    values: list[dict[str, str]] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        push = re.search(r"\.push\(\"([^\"]+)\"\)", line)
        if push:
            section = push.group(1)
            owner = "b"
            for lookahead in lines[i + 1:]:
                next_assignment = re.search(r"^\s*[A-Z][A-Z0-9_]*\s*=\s*(b|sb)(?:\s|\.|$)", lookahead)
                if next_assignment:
                    owner = next_assignment.group(1)
                    break
            owners[section] = owner
            sections[owner].append(section)
        pop = re.search(r"\b(b|sb)\.pop\(\)", line)
        if pop and sections[pop.group(1)]:
            sections[pop.group(1)].pop()

        assignment = re.search(r"^\s*([A-Z][A-Z0-9_]*)\s*=\s*(b|sb)(?:\s|\.|$)", line)
        if not assignment:
            i += 1
            continue
        name, builder = assignment.groups()
        chain = [line]
        if not re.search(r";\s*$", line):
            i += 1
            while i < len(lines):
                chain.append(lines[i])
                # A comment string may contain a semicolon. Only the statement's
                # trailing semicolon ends this fluent builder chain.
                if re.search(r";\s*$", lines[i]):
                    break
                i += 1
        text = "\n".join(chain)
        define = re.search(r"\.define(InRange|Enum|ListAllowEmpty)?\((.*?)\);", text, re.S)
        if not define:
            i += 1
            continue
        kind, args = define.groups()
        args = re.sub(r"\s+", " ", args).strip()
        arg_values = split_java_args(args)
        key = java_string(arg_values[0])
        if kind == "InRange":
            default = normalize_default(arg_values[1])
            detail = f"{default}; {normalize_default(arg_values[2])}-{normalize_default(arg_values[3])}"
            value_type = "number"
        elif kind == "Enum":
            default = normalize_default(arg_values[1])
            detail = default
            value_type = "enum"
        elif kind == "ListAllowEmpty":
            default = normalize_default(arg_values[1])
            detail = default
            value_type = "list"
        else:
            default = normalize_default(arg_values[1])
            detail = default
            value_type = "boolean" if default in {"true", "false"} else "value"
        translation = re.search(r'\.translation\("([^"]+)"\)', text)
        comments = " ".join(quoted_strings(" ".join(re.findall(r"\.comment\((.*?)\)\n?", text, re.S))))
        values.append({
            "scope": "COMMON" if builder == "b" else "SERVER",
            "section": sections[builder][-1] if sections[builder] else "(root)",
            "key": key,
            "path": ".".join(sections[builder] + [key]) if sections[builder] else key,
            "type": value_type,
            "detail": detail,
            "translation": translation.group(1) if translation else "",
            "comment": comments,
        })
        i += 1
    return values


def render(values: list[dict[str, str]], lang: dict[str, str]) -> str:
    sections: list[str] = []
    for value in values:
        if value["section"] not in sections:
            sections.append(value["section"])
    out = [
        "# Magnetization configuration reference",
        "",
        "<!-- Generated by scripts/generate-config-reference.py; do not edit by hand. -->",
        "",
        "This is the complete reference for the live configuration spec. COMMON values are",
        "stored in config/magnetization-common.toml; SERVER values are stored in",
        "config/magnetization-server.toml. Defaults and ranges are taken from",
        "MagConfig.java; descriptions come from the English translation catalog.",
        "",
        "Regenerate with python3 scripts/generate-config-reference.py; use --check to detect stale output.",
        "",
    ]
    for section in sections:
        title = {"guiLimits": "GUI limits", "nobleGases": "Noble gases"}.get(
            section, section.replace("_", " ").title())
        out += [f"## {title}", "", "| Scope | Key | Type | Default / range | Description |", "|---|---|---|---|---|"]
        for value in (item for item in values if item["section"] == section):
            translation = value["translation"]
            description = lang.get(f"{translation}.tooltip") or lang.get(translation) or value["comment"] or "—"
            description = description.replace("|", "\\|").replace("\n", " ")
            out.append(f"| {value['scope']} | {value['path']} | {value['type']} | {value['detail']} | {description} |")
        out.append("")
    return "\n".join(out)


def validate_fully_documented_sections(values: list[dict[str, str]], lang: dict[str, str]) -> None:
    """Keep release-critical config groups complete in TOML, config UIs, and docs."""
    for section in ("nobleGases",):
        for value in (item for item in values if item["section"] == section):
            missing: list[str] = []
            if not value["comment"]:
                missing.append("comment")
            translation = value["translation"]
            if not translation:
                missing.append("translation metadata")
            else:
                if translation not in lang:
                    missing.append("translation label")
                if f"{translation}.tooltip" not in lang:
                    missing.append("translation tooltip")
            if missing:
                raise SystemExit(f"{value['path']} is missing {', '.join(missing)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail when the generated file is stale")
    args = parser.parse_args()
    values = parse_spec()
    lang = json.loads(LANG.read_text())
    if not values:
        raise SystemExit("No config values found")
    validate_fully_documented_sections(values, lang)
    output = render(values, lang)
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != output:
            print(f"{OUTPUT} is stale; run the generator")
            return 1
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(output)
    print(f"Generated {OUTPUT} ({len(values)} options)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
