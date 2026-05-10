#!/usr/bin/env python3
"""Magnetite textures: recolored vanilla iron variants.

Real-world magnetite is steely-black with a metallic luster; we shift the
iron palette toward darker, slightly cooler/bluer hues. Reusing the iron
texture's noise pattern gives us authentic-looking variation without authoring
art from scratch — this is the same trick vanilla uses for raw_iron_block
vs. iron_block (palette swap on a shared template).

Sources used (all from the vanilla 1.21.1 client jar):
- block/iron_ore       → block/magnetite_ore
- block/deepslate_iron_ore → block/deepslate_magnetite_ore
- block/iron_block     → block/magnetite_block
- block/raw_iron_block → block/raw_magnetite_block
- item/iron_ingot      → item/magnetite_ingot
- item/raw_iron        → item/raw_magnetite
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


def vanilla(path: str) -> Image.Image:
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        with zf.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(io.BytesIO(f.read())).convert("RGBA").copy()


def recolor_to_magnetite(img: Image.Image) -> Image.Image:
    """Shift the iron-grey palette to a darker, cooler steely-black.

    Each pixel: pull the brightness down ~40% and inject a small blue tint, keep
    alpha. This gives a uniform "darkened iron" look that reads as a different
    metal at a glance while preserving every speckle and gradient of the source.
    """
    out = img.copy()
    pixels = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            # Darken: 60% of original brightness.
            r = int(r * 0.55)
            g = int(g * 0.58)
            b = int(b * 0.68)  # slightly more blue retention for the "steely" look
            # Floor (so we never lose detail in deep shadows).
            r = max(r, 18)
            g = max(g, 18)
            b = max(b, 22)
            pixels[x, y] = (r, g, b, a)
    return out


def recolor_raw(img: Image.Image) -> Image.Image:
    """Raw magnetite is closer to true black than the smelted ingot — emphasize
    the dark/dirt parts of the source raw_iron texture."""
    out = img.copy()
    pixels = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            # Heavier darkening + slight blue shift; the rust hues in raw_iron
            # become deep gunmetal.
            r = max(int(r * 0.45) - 5, 14)
            g = max(int(g * 0.48) - 5, 14)
            b = max(int(b * 0.62), 24)
            pixels[x, y] = (r, g, b, a)
    return out


# ---------------- ores ----------------
recolor_to_magnetite(vanilla("block/iron_ore")).save(f"{BLOCK}/magnetite_ore.png")
recolor_to_magnetite(vanilla("block/deepslate_iron_ore")).save(f"{BLOCK}/deepslate_magnetite_ore.png")

# ---------------- ingot block + raw block ----------------
recolor_to_magnetite(vanilla("block/iron_block")).save(f"{BLOCK}/magnetite_block.png")
recolor_raw(vanilla("block/raw_iron_block")).save(f"{BLOCK}/raw_magnetite_block.png")

# ---------------- items ----------------
recolor_to_magnetite(vanilla("item/iron_ingot")).save(f"{ITEM}/magnetite_ingot.png")
recolor_raw(vanilla("item/raw_iron")).save(f"{ITEM}/raw_magnetite.png")

print("Generated magnetite textures from vanilla iron sources.")
