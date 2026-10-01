#!/usr/bin/env python3
"""Validate saved native UI audit evidence, including exact case coverage and files."""
import argparse
import hashlib
import json
import re
from pathlib import Path

MODES = ("curios", "jade", "wthit", "top", "goggles", "emi", "jei", "rei", "jer")
TOPICS = {"ferromagnetic_items", "excavator_targets", "magnetite", "iron_oxide_ores",
          "rare_earth_progression", "lithium", "gallium", "fusion_fuels", "electrolyzer",
          "noble_gases", "air_separator", "ion_thruster", "dipole_electromagnet",
          "structural_inducer", "mhd_jet", "fusion_thruster", "tokamak", "railgun"}
HUD_BLOCKS = {"permanent_magnet", "kinetic_electromagnet", "gas_exciter", "gas_vent", "air_separator",
              "magnetostrictive_sensor", "barkhausen_generator", "gyrostabilizer", "induction_pad"}

def analyze(root, modes=MODES):
    reports = {}
    for mode in modes:
        directory = root / mode
        client = (directory / "client.log").read_text()
        server = (directory / "server.log").read_text()
        assert "UI_AUDIT_FAILED" not in client + server, f"{mode}: runtime assertion failed"
        assert client.count("UI_AUDIT_PASS ") == 1, f"{mode}: missing/duplicate completion"
        if mode == "curios":
            assert "UI_CURIOS_SERVER_PASS" in server and "UI_CURIOS_REPULSOR_SERVER_PASS" in server
            assert "UI_CURIOS_NEEDLE_PASS" in client
            assert "E  (90°)" in client, "incorrect compass HUD bearing"
            assert "UI_CURIOS_KEY repulsor=F9" in client and "UI_CURIOS_KEY grapple=F10" in client
            cases = 3
        elif mode in ("jade", "wthit", "top", "goggles"):
            cases_found = re.findall(r"UI_HUD_READOUT viewer=" + mode + r" block=magnetization:([a-z_]+)", client)
            assert len(cases_found) == 9 and set(cases_found) == HUD_BLOCKS, f"{mode}: incomplete block coverage"
            expected_switches = 9 if mode == "goggles" else 18
            assert client.count("UI_HUD_SWITCH viewer=" + mode) == expected_switches, f"{mode}: incomplete switches"
            assert client.count("UI_HUD_RESTORED viewer=" + mode) == expected_switches, f"{mode}: incomplete restoration"
            assert server.count("UI_HUD_SHIP_MOTION") >= 2, "moving ship evidence missing"
            assert client.count("UI_HUD_CASE_MOTION viewer=" + mode) == 9, "each moving target must be observed"
            assert "no_duplicate_lines=true" in client
            if mode != "goggles":
                assert server.count("UI_HUD_MASTER_SYNC viewer=" + mode + " enabled=false") == 9
                assert server.count("UI_HUD_MASTER_SYNC viewer=" + mode + " enabled=true") == 9
            cases = 9
        elif mode == "jer":
            charts = re.findall(r"UI_JER_CHART source=([a-z0-9_]+) display=([a-z0-9_]+)", client)
            assert len(charts) == len(set(charts)) == 28, "JER requires all 28 native charts"
            assert "dimension=minecraft:the_end" in client, "End geode chart not inspected"
            drops = re.findall(r"UI_JER_DROP source=([a-z0-9_]+) display=([a-z0-9_]+) item=([a-z0-9_]+)", client)
            assert len(drops) == len(set(drops)) == 31, "JER requires all 31 hovered drop displays"
            cases = 28
        else:
            topics = re.findall(r"UI_VIEWER_PAGE viewer=" + mode + r" topic=magnetization:info/([a-z_]+)", client)
            assert len(topics) == 18 and set(topics) == TOPICS, f"{mode}: expected 18 native topics"
            assert "rendered_text_verified=true" in client
            cases = 18
        captures = re.findall(r"UI_CAPTURE (ui-[a-z0-9_-]+\.png) Saved screenshot", client)
        assert captures, f"{mode}: no framebuffer captures"
        hashes = {}
        for name in captures:
            path = directory / name
            data = path.read_bytes()
            assert data.startswith(b"\x89PNG\r\n\x1a\n"), f"invalid capture {path}"
            hashes[name] = hashlib.sha256(data).hexdigest()
        reports[mode] = {"passed": True, "native_cases": cases, "screenshots": hashes,
                         "client_log_sha256": hashlib.sha256(client.encode()).hexdigest(),
                         "server_log_sha256": hashlib.sha256(server.encode()).hexdigest()}
    return reports

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--modes", nargs="+", choices=MODES, default=MODES)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = {"profiles": analyze(args.evidence, args.modes)}
    text = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(text)
    else:
        print(text, end="")
