#!/usr/bin/env python3
"""Polarity Inverter texture: split half-red half-blue with a bidirectional
swap-arrow glyph (⇌) communicating 'this thing flips polarity'."""
import io, os, zipfile
from PIL import Image, ImageDraw, ImageEnhance

CLIENT_JAR = os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures"

NORTH = (200, 56, 56, 255)
SOUTH = (66, 130, 200, 255)
ACCENT = (235, 195, 80, 255)
SLATE_DARK = (28, 28, 32, 255)


def vanilla(path):
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        with zf.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(io.BytesIO(f.read())).convert("RGBA").copy()


def border(d, c=SLATE_DARK):
    d.rectangle([0, 0, 15, 15], outline=c)


def darken(img, factor=0.45):
    """Pull the iron base toward black so red/blue tints pop and the block
    reads as 'industrial / signal-routing' rather than 'shiny iron'."""
    return ImageEnhance.Brightness(img).enhance(factor)


def blend_overlay(img, x0, y0, x1, y1, color):
    """Average each pixel in the rect toward `color` — preserves base noise."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            r, g, b, _ = img.getpixel((x, y))
            img.putpixel((x, y), (
                (r + color[0]) // 2,
                (g + color[1]) // 2,
                (b + color[2]) // 2,
                255,
            ))


# Top: half-red (N) / half-blue (S) with a bidirectional swap arrow centered on it
img = darken(vanilla("block/iron_block"))
d = ImageDraw.Draw(img, "RGBA")
blend_overlay(img, 2, 2, 13, 7, NORTH)
blend_overlay(img, 2, 8, 13, 13, SOUTH)
# Bidirectional swap-arrow glyph (⇌). Upper line points right, lower line points left.
# This reads as 'reverse direction' / 'flip polarity' rather than the old electric
# zigzag. Both arrows are 1px to keep them legible at 16x16.
# Upper arrow: → (left to right, in the NORTH band)
d.line([(4, 5), (11, 5)], fill=ACCENT, width=1)
d.line([(11, 5), (9, 3)], fill=ACCENT, width=1)
d.line([(11, 5), (9, 7)], fill=ACCENT, width=1)
# Lower arrow: ← (right to left, in the SOUTH band)
d.line([(4, 10), (11, 10)], fill=ACCENT, width=1)
d.line([(4, 10), (6, 8)], fill=ACCENT, width=1)
d.line([(4, 10), (6, 12)], fill=ACCENT, width=1)
border(d)
img.save(f"{ASSETS}/block/polarity_inverter_top.png")

# Side: compressed N/S bands with two reciprocating arrows, also pointing in
# opposite directions. Reads as 'signal flips going through the block.'
img = darken(vanilla("block/iron_block"))
d = ImageDraw.Draw(img, "RGBA")
blend_overlay(img, 1, 4, 14, 6, NORTH)
blend_overlay(img, 1, 9, 14, 11, SOUTH)
# Tiny right-arrow in the N band, left-arrow in the S band.
d.line([(3, 5), (12, 5)], fill=ACCENT, width=1)
d.line([(12, 5), (10, 3)], fill=ACCENT, width=1)
d.line([(12, 5), (10, 7)], fill=ACCENT, width=1)
d.line([(3, 10), (12, 10)], fill=ACCENT, width=1)
d.line([(3, 10), (5, 8)], fill=ACCENT, width=1)
d.line([(3, 10), (5, 12)], fill=ACCENT, width=1)
border(d)
img.save(f"{ASSETS}/block/polarity_inverter_side.png")

print("Generated polarity_inverter textures (with ⇌ swap glyph + dark base).")
