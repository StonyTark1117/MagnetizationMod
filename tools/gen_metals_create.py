#!/usr/bin/env python3
"""Unified metal/storage block textures for the Magnetization ore families.

Problem: the five ferromagnetic families (magnetite, maghemite, pyrrhotite,
hematite, titanomagnetite) had their storage blocks authored by inconsistent
methods -- some were multiplicative recolors of vanilla iron_block, others were
flat colored squares. They don't read as one set.

Fix: gradient-map a single shared source (vanilla iron_block for refined blocks,
raw_iron_block for raw blocks, iron_ingot / raw_iron for the items) onto a
per-family two-tone ramp. Every family then shares the exact same polished-metal
shading and bevel -- only the hue changes -- which sits naturally next to
Create's brass_block / zinc_block. Luminance is normalized per source so each
ramp uses its full tonal range for punchy, readable metal.

Run: python3 tools/gen_metals_create.py
"""
from __future__ import annotations

import io
import os
import zipfile
from PIL import Image

CLIENT_JAR = os.path.expanduser(
    "~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
)
ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures"
BLOCK = os.path.join(ASSETS, "block")
ITEM = os.path.join(ASSETS, "item")

# Per-family (shadow, highlight) ramp. Tuned so each family matches its raw-ore
# item hue while keeping a metallic spread from deep shadow to bright specular.
FAMILIES = {
    "magnetite":       ((22, 24, 32),  (152, 162, 188)),  # steely blue-black
    "maghemite":       ((58, 26, 16),  (212, 122, 70)),   # gamma-iron orange
    "pyrrhotite":      ((52, 40, 16),  (222, 192, 112)),  # brassy bronze gold
    "hematite":        ((46, 14, 14),  (214, 82, 74)),    # blood-red oxide
    "titanomagnetite": ((20, 26, 44),  (124, 152, 206)),  # titanium steel-blue
}


def vanilla(path: str) -> Image.Image:
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        with zf.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(io.BytesIO(f.read())).convert("RGBA").copy()


def _luma_bounds(img: Image.Image):
    px = img.load()
    w, h = img.size
    lo, hi = 255.0, 0.0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 16:
                continue
            L = 0.299 * r + 0.587 * g + 0.114 * b
            lo = min(lo, L)
            hi = max(hi, L)
    return lo, max(hi, lo + 1.0)


def gradient_map(img: Image.Image, shadow, light, gamma: float = 0.9) -> Image.Image:
    """Map source luminance (normalized to its own range) onto shadow->light."""
    lo, hi = _luma_bounds(img)
    out = img.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            L = (0.299 * r + 0.587 * g + 0.114 * b - lo) / (hi - lo)
            L = max(0.0, min(1.0, L)) ** gamma
            nr = int(shadow[0] + (light[0] - shadow[0]) * L)
            ng = int(shadow[1] + (light[1] - shadow[1]) * L)
            nb = int(shadow[2] + (light[2] - shadow[2]) * L)
            px[x, y] = (nr, ng, nb, a)
    return out


def _darker(ramp, f=0.82):
    (s, l) = ramp
    return (tuple(int(c * f) for c in s), tuple(int(c * f) for c in l))


# Shared sources.
iron_block = vanilla("block/iron_block")
raw_iron_block = vanilla("block/raw_iron_block")
iron_ingot = vanilla("item/iron_ingot")
raw_iron = vanilla("item/raw_iron")

for fam, ramp in FAMILIES.items():
    s, l = ramp
    # Refined storage block -- crisp polished metal.
    gradient_map(iron_block, s, l).save(f"{BLOCK}/{fam}_block.png")
    # Raw block -- rougher source + slightly darker ramp.
    rs, rl = _darker(ramp, 0.85)
    gradient_map(raw_iron_block, rs, rl, gamma=1.0).save(f"{BLOCK}/raw_{fam}_block.png")
    # Ingot + raw item -- same ramp so the whole family is internally coherent.
    gradient_map(iron_ingot, s, l).save(f"{ITEM}/{fam}_ingot.png")
    gradient_map(raw_iron, rs, rl, gamma=1.0).save(f"{ITEM}/raw_{fam}.png")

print("Generated unified metal/storage block + ingot textures for all families.")
