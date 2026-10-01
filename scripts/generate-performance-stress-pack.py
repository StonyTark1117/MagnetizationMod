#!/usr/bin/env python3
"""Generate the disposable datapack used by the performance stress runner."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path


SCENARIOS = (
    "empty_start",
    "block_item_control",
    "idle_emitters",
    "active_emitters",
    "external_fields",
    "railgun_emitters",
    "air_separators",
    "gas_volume",
    "mixed_pack",
    "dense_external",
    "equipment_changes",
    "ordinary_player",
    "ordinary_mobs",
    "external_no_targets",
    "ferrofluid_pool",
    "ferrofluid_external",
    "ferrofluid_native",
    "mr_armor",
    "mr_mainhand",
    "mr_offhand",
    "native_queries_control", "native_queries_64", "native_queries_256",
    "gas_stable", "shafts_16", "shafts_64", "shafts_256", "ships_4", "ships_16",
    "empty_end",
)

SPACING = 8
BASE_Y = 80


def grid_positions(size: int) -> list[tuple[int, int]]:
    offset = ((size - 1) * SPACING) // 2
    return [(x * SPACING - offset, z * SPACING - offset) for z in range(size) for x in range(size)]


def item_command(x: int, y: int, z: int) -> str:
    return (
        f'summon minecraft:item {x} {y} {z} '
        '{Tags:["mag_stress"],NoGravity:1b,PickupDelay:32767s,Age:-32768s,'
        'Item:{id:"minecraft:iron_ingot",count:1}}'
    )


def item_cell_commands(x: int, z: int) -> list[str]:
    item_y = BASE_Y + 2
    return [
        f"setblock {x} {item_y - 1} {z} minecraft:glass",
        f"setblock {x} {item_y + 1} {z} minecraft:glass",
        f"setblock {x - 1} {item_y} {z} minecraft:glass",
        f"setblock {x + 1} {item_y} {z} minecraft:glass",
        f"setblock {x} {item_y} {z - 1} minecraft:glass",
        f"setblock {x} {item_y} {z + 1} minecraft:glass",
        item_command(x, item_y, z),
    ]


def cleanup_commands(size: int) -> list[str]:
    positions = grid_positions(size)
    min_x = min(x for x, _ in positions) - 4
    max_x = max(x for x, _ in positions) + 4
    min_z = min(z for _, z in positions) - 4
    max_z = max(z for _, z in positions) + 4
    commands = [
        "magperf clear",
        "gamerule doMobSpawning false",
        "gamerule doMobLoot false",
        "gamerule doEntityDrops false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule randomTickSpeed 0",
        "weather clear",
        "kill @e[tag=mag_stress]",
        "kill @e[type=minecraft:item]",
        f"forceload add {min_x - 16} {min_z - 16} {max_x + 16} {max_z + 16}",
    ]
    # Keep each fill comfortably below Minecraft's 32,768-block command limit.
    stripe_width = 12
    for stripe_start in range(min_z, max_z + 1, stripe_width):
        stripe_end = min(max_z, stripe_start + stripe_width - 1)
        commands.append(
            f"fill {min_x} {BASE_Y - 4} {stripe_start} "
            f"{max_x} {BASE_Y + 12} {stripe_end} minecraft:air"
        )
    return commands


def grid_scenario(size: int, block: str, *, powered: bool = False, items: bool = True) -> list[str]:
    commands: list[str] = []
    for x, z in grid_positions(size):
        if powered:
            commands.append(f"setblock {x} {BASE_Y - 1} {z} minecraft:redstone_block")
        commands.append(f"setblock {x} {BASE_Y} {z} {block}")
        if items:
            commands.extend(item_cell_commands(x, z))
    return commands


def scenario_commands(name: str, size: int) -> list[str]:
    if name in {"empty_start", "empty_end"}:
        return []
    if name.startswith("native_queries_"):
        count = 0 if name.endswith("control") else int(name.rsplit("_", 1)[1])
        return [f"magperf scale native {count}"]
    if name.startswith(("shafts_", "ships_")):
        kind, count = name.split("_")
        return [f"magperf scale {kind} {count}"]
    if name == "dense_external":
        return [f"fill -8 {BASE_Y - 4} -8 7 {BASE_Y - 1} 7 create_new_age:netherite_magnet"] + [
            command for x, z in grid_positions(size) for command in item_cell_commands(x, z)
        ]
    if name == "equipment_changes":
        return grid_scenario(size, "create_new_age:magnetite_block", items=False) + ["magperf player cycle"]
    if name == "ordinary_player":
        return grid_scenario(size, "create_new_age:magnetite_block", items=False) + ["magperf player none"]
    if name == "external_no_targets":
        return grid_scenario(size, "create_new_age:magnetite_block", items=False)
    if name == "ordinary_mobs":
        return grid_scenario(size, "create_new_age:magnetite_block", items=False) + [
            f'summon minecraft:cow {x} {BASE_Y + 2} {z} {{Tags:["mag_stress"],NoAI:1b,NoGravity:1b}}'
            for x, z in grid_positions(size)
        ]
    if name in {"ferrofluid_pool", "ferrofluid_external", "ferrofluid_native"}:
        # Enclosed two-block-deep pool, 16x16 at the quick grid, 32x32 at standard.
        # Its walls keep flowing-fluid updates out of steady-state measurements.
        side = size * 4
        lo, hi = -(side // 2), side // 2 - 1
        commands = [
            f"fill {lo - 1} {BASE_Y - 1} {lo - 1} {hi + 1} {BASE_Y + 2} {hi + 1} minecraft:glass hollow",
            f"fill {lo} {BASE_Y} {lo} {hi} {BASE_Y + 1} {hi} magnetization:ferrofluid",
        ]
        if name == "ferrofluid_external":
            commands += [f"setblock {x} {BASE_Y - 2} {z} create_new_age:magnetite_block"
                         for x, z in grid_positions(size)]
        if name == "ferrofluid_native":
            commands += [f"setblock 0 {BASE_Y - 3} 0 minecraft:redstone_block",
                         f"setblock 0 {BASE_Y - 2} 0 magnetization:electromagnet"]
        return commands
    if name in {"mr_armor", "mr_mainhand", "mr_offhand"}:
        equipment = {"mr_armor": "armor", "mr_mainhand": "main", "mr_offhand": "off"}[name]
        return grid_scenario(size, "create_new_age:magnetite_block", items=False) + [f"magperf player {equipment}"]
    if name == "block_item_control":
        return grid_scenario(size, "minecraft:iron_block")
    if name == "idle_emitters":
        return grid_scenario(size, "magnetization:electromagnet")
    if name == "active_emitters":
        return grid_scenario(size, "magnetization:electromagnet", powered=True)
    if name == "external_fields":
        return grid_scenario(size, "create_new_age:magnetite_block")
    if name == "railgun_emitters":
        return grid_scenario(size, "magnetization:railgun_emitter", powered=True)
    if name == "air_separators":
        return grid_scenario(size, "magnetization:air_separator", items=False)
    if name in {"gas_volume", "gas_stable"}:
        side = size * 2
        height = size
        min_x = -(side // 2)
        max_x = min_x + side - 1
        min_z = min_x
        max_z = max_x
        bottom = BASE_Y - 1
        top = BASE_Y + height
        commands = [
            f"fill {min_x - 1} {bottom} {min_z - 1} {max_x + 1} {bottom} {max_z + 1} minecraft:glass",
            f"fill {min_x - 1} {top} {min_z - 1} {max_x + 1} {top} {max_z + 1} minecraft:glass",
            f"fill {min_x - 1} {BASE_Y} {min_z - 1} {min_x - 1} {top - 1} {max_z + 1} minecraft:glass",
            f"fill {max_x + 1} {BASE_Y} {min_z - 1} {max_x + 1} {top - 1} {max_z + 1} minecraft:glass",
            f"fill {min_x} {BASE_Y} {min_z - 1} {max_x} {top - 1} {min_z - 1} minecraft:glass",
            f"fill {min_x} {BASE_Y} {max_z + 1} {max_x} {top - 1} {max_z + 1} minecraft:glass",
            f"fill {min_x} {BASE_Y} {min_z} {max_x} {top - 1} {max_z} magnetization:argon",
            "scoreboard objectives add mag_stress dummy",
            "scoreboard players set #gas_churn mag_stress 0",
            f'summon minecraft:marker {min_x} {BASE_Y} {min_z} '
            '{Tags:["mag_stress","mag_stress_gas_churn"]}',
        ]
        return commands if name == "gas_volume" else commands[:-1]
    if name == "mixed_pack":
        blocks = (
            "minecraft:iron_block",
            "magnetization:electromagnet",
            "create_new_age:magnetite_block",
            "magnetization:railgun_emitter",
            "magnetization:air_separator",
        )
        commands = []
        for index, (x, z) in enumerate(grid_positions(size)):
            block = blocks[index % len(blocks)]
            powered = block in {"magnetization:electromagnet", "magnetization:railgun_emitter"}
            if powered:
                commands.append(f"setblock {x} {BASE_Y - 1} {z} minecraft:redstone_block")
            commands.append(f"setblock {x} {BASE_Y} {z} {block}")
            if block != "magnetization:air_separator":
                commands.extend(item_cell_commands(x, z))
        return commands
    raise ValueError(f"unknown scenario: {name}")


def write_function(path: Path, commands: list[str]) -> None:
    path.write_text("\n".join(commands) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--grid-size", type=int, required=True)
    args = parser.parse_args()
    if not 1 <= args.grid_size <= 20:
        parser.error("--grid-size must be between 1 and 20")

    output = args.output.resolve()
    if output.exists():
        shutil.rmtree(output)
    functions = output / "data" / "magnetization_stress" / "function"
    functions.mkdir(parents=True)
    (output / "pack.mcmeta").write_text(
        json.dumps(
            {
                "pack": {
                    "pack_format": 48,
                    "description": "Disposable Magnetization performance stress scenarios",
                }
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    common = cleanup_commands(args.grid_size)
    for name in SCENARIOS:
        commands = common + scenario_commands(name, args.grid_size)
        commands.append(f"magperf reindex {args.grid_size * 4 + 20}")
        if name in {"dense_external", "equipment_changes", "ordinary_player", "ordinary_mobs", "external_no_targets", "ferrofluid_external",
                    "external_fields", "mixed_pack", "mr_armor", "mr_mainhand", "mr_offhand"}:
            commands.append("magperf verifyexternal")
        commands.append(f"say MAG_STRESS_READY_{name}")
        write_function(functions / f"{name}.mcfunction", commands)

    gas_side = args.grid_size * 2
    gas_min = -(gas_side // 2)
    write_function(
        functions / "tick.mcfunction",
        [
            "execute if entity @e[tag=mag_stress_gas_churn,limit=1] run function magnetization_stress:gas_churn"
        ],
    )
    write_function(
        functions / "gas_churn.mcfunction",
        [
            "scoreboard players add #gas_churn mag_stress 1",
            f"execute if score #gas_churn mag_stress matches 1 run setblock {gas_min} {BASE_Y} {gas_min} minecraft:air",
            f"execute if score #gas_churn mag_stress matches 2.. run setblock {gas_min} {BASE_Y} {gas_min} magnetization:argon",
            "execute if score #gas_churn mag_stress matches 2.. run scoreboard players set #gas_churn mag_stress 0",
        ],
    )
    tick_tag = output / "data" / "minecraft" / "tags" / "function"
    tick_tag.mkdir(parents=True)
    (tick_tag / "tick.json").write_text(
        json.dumps({"values": ["magnetization_stress:tick"]}, indent=2) + "\n",
        encoding="utf-8",
    )

    manifest = {
        "schema_version": 1,
        "grid_size": args.grid_size,
        "instance_count": args.grid_size**2,
        "gas_block_count": (args.grid_size * 2) ** 2 * args.grid_size,
        "spacing_blocks": SPACING,
        "base_y": BASE_Y,
        "scenarios": list(SCENARIOS),
    }
    (output / "stress-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, sort_keys=True))


if __name__ == "__main__":
    main()
