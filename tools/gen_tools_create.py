#!/usr/bin/env python3
"""Tool & armor textures for the three equipment tiers.

Problem: ferromagnetic and magnetite tools/armor were both pale blue-grey and
read as the same tier; only maghemite (orange) stood out.

Fix: regenerate every tool and armor piece from the vanilla iron sources,
gradient-mapping ONLY the metallic (low-saturation) pixels onto a per-tier
ramp. Wooden handles (saturated brown) are left untouched. Tier ramps are
chosen for clear separation and to match each family's blocks/ingots:

  magnetite       -> dark gunmetal blue-black   (matches magnetite blocks)
  ferromagnetic   -> bright magnetized steel     (lighter + cooler than magnetite)
  maghemite       -> oxide orange                (matches maghemite blocks)

Run: python3 tools/gen_tools_create.py
"""
from __future__ import annotations

import io
import os
import zipfile
from PIL import Image

CLIENT_JAR = os.path.expanduser(
    "~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
)
ITEM = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/item"

# (shadow, highlight) metal ramps per tier. magnetite/maghemite match their
# block ramps from gen_metals_create.py; ferromagnetic is a brighter steel so
# the two grey tiers no longer collide.
TIERS = {
    "magnetite":     ((22, 24, 32),  (150, 160, 185)),
    "ferromagnetic": ((66, 74, 92),  (214, 226, 244)),
    "maghemite":     ((58, 26, 16),  (212, 122, 70)),
}

TOOLS = ["pickaxe", "axe", "shovel", "hoe", "sword"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]

SAT_METAL = 0.32  # pixels below this saturation are treated as metal (recolored)


def vanilla(path: str) -> Image.Image:
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        with zf.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(io.BytesIO(f.read())).convert("RGBA").copy()


def _sat(r, g, b):
    mx = max(r, g, b)
    if mx == 0:
        return 0.0
    return (mx - min(r, g, b)) / mx


def _metal_luma_bounds(img: Image.Image):
    px = img.load()
    w, h = img.size
    lo, hi = 255.0, 0.0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 16 or _sat(r, g, b) >= SAT_METAL:
                continue
            L = 0.299 * r + 0.587 * g + 0.114 * b
            lo, hi = min(lo, L), max(hi, L)
    if hi <= lo:
        lo, hi = 0.0, 255.0
    return lo, hi


def recolor_metal(img: Image.Image, shadow, light, gamma=0.9) -> Image.Image:
    """Gradient-map only the low-saturation (metal) pixels; leave the wooden
    handle and any colored detail alone."""
    lo, hi = _metal_luma_bounds(img)
    out = img.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0 or _sat(r, g, b) >= SAT_METAL:
                continue
            L = max(0.0, min(1.0, (0.299 * r + 0.587 * g + 0.114 * b - lo) / (hi - lo))) ** gamma
            nr = int(shadow[0] + (light[0] - shadow[0]) * L)
            ng = int(shadow[1] + (light[1] - shadow[1]) * L)
            nb = int(shadow[2] + (light[2] - shadow[2]) * L)
            px[x, y] = (nr, ng, nb, a)
    return out


# Sources (vanilla iron equipment shares the wood-handle + grey-metal template).
src_tools = {t: vanilla(f"item/iron_{t}") for t in TOOLS}
src_armor = {a: vanilla(f"item/iron_{a}") for a in ARMOR}

for tier, ramp in TIERS.items():
    s, l = ramp
    for t in TOOLS:
        recolor_metal(src_tools[t], s, l).save(f"{ITEM}/{tier}_{t}.png")
    for a in ARMOR:
        recolor_metal(src_armor[a], s, l).save(f"{ITEM}/{tier}_{a}.png")

# Keep the ferromagnetic ingot in sync with its (now brighter) tools.
recolor_metal(vanilla("item/iron_ingot"), *TIERS["ferromagnetic"]).save(
    f"{ITEM}/ferromagnetic_ingot.png"
)

print("Generated tier-distinct tool/armor textures (metal-only gradient map).")
