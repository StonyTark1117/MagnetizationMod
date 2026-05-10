#!/usr/bin/env python3
"""Magnetic Grapple item icon: hook silhouette with a string + magnet head."""
from PIL import Image, ImageDraw

NORTH = (200, 56, 56, 255)
SOUTH = (66, 130, 200, 255)
IRON = (184, 184, 184, 255)
SLATE_DARK = (28, 28, 32, 255)
STRING = (220, 215, 195, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img, "RGBA")

# Handle (bottom): wood-like vertical bar
d.rectangle([6, 9, 9, 14], fill=(127, 96, 60, 255))
d.line([(6, 9), (6, 14)], fill=SLATE_DARK)
d.line([(9, 9), (9, 14)], fill=SLATE_DARK)

# String (curved): from handle top up to the magnet head
for i, (x, y) in enumerate([(7, 8), (7, 7), (8, 6), (8, 5), (9, 4), (10, 3)]):
    img.putpixel((x, y), STRING)

# Magnet head: U-shape with red and blue tips
d.rectangle([10, 1, 11, 5], fill=NORTH)  # left arm (red)
d.rectangle([13, 1, 14, 5], fill=SOUTH)  # right arm (blue)
d.rectangle([10, 4, 14, 5], fill=IRON)   # crossbar

img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/item/magnetic_grapple.png")
print("OK")
