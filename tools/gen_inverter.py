#!/usr/bin/env python3
"""Polarity Inverter texture: split half-red half-blue with a yellow zigzag.
Read as 'this thing flips polarity'."""
import io, os, zipfile
from PIL import Image, ImageDraw

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


# Top: half-red / half-blue with zigzag
img = vanilla("block/iron_block")
d = ImageDraw.Draw(img, "RGBA")
# Tinted halves overlay
for y in range(2, 8):
    for x in range(2, 14):
        img.putpixel((x, y), tuple(max(0, min(255, c)) for c in (
            (img.getpixel((x, y))[0] + NORTH[0]) // 2,
            (img.getpixel((x, y))[1] + NORTH[1]) // 2,
            (img.getpixel((x, y))[2] + NORTH[2]) // 2,
            255)))
for y in range(8, 14):
    for x in range(2, 14):
        img.putpixel((x, y), tuple(max(0, min(255, c)) for c in (
            (img.getpixel((x, y))[0] + SOUTH[0]) // 2,
            (img.getpixel((x, y))[1] + SOUTH[1]) // 2,
            (img.getpixel((x, y))[2] + SOUTH[2]) // 2,
            255)))
# zigzag accent line
d.line([(3, 5), (7, 8), (4, 11)], fill=ACCENT, width=2)
d.line([(7, 8), (12, 5)], fill=ACCENT, width=1)
d.line([(7, 8), (12, 11)], fill=ACCENT, width=1)
border(d)
img.save(f"{ASSETS}/block/polarity_inverter_top.png")

# Side: same but compressed band
img = vanilla("block/iron_block")
d = ImageDraw.Draw(img, "RGBA")
d.rectangle([0, 5, 15, 7], fill=NORTH)
d.rectangle([0, 8, 15, 10], fill=SOUTH)
d.line([(0, 7), (15, 8)], fill=ACCENT, width=1)
border(d)
img.save(f"{ASSETS}/block/polarity_inverter_side.png")

print("Generated polarity_inverter textures.")
