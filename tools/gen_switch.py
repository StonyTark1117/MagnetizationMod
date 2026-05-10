#!/usr/bin/env python3
"""Magnetic Switch texture: dark slate face with a glowing red dot in the center.
Suggests 'sensor / proximity detector'."""
from PIL import Image, ImageDraw

IRON = (184, 184, 184, 255)
SLATE = (64, 64, 64, 255)
SLATE_DARK = (44, 44, 44, 255)
NORTH = (200, 56, 56, 255)
ACCENT = (255, 216, 72, 255)


def new(c=IRON):
    return Image.new("RGBA", (16, 16), c)


def border(img, c=(120, 120, 120, 255)):
    ImageDraw.Draw(img).rectangle([0, 0, 15, 15], outline=c)


# top: sensor pad with red dot
img = new(IRON)
d = ImageDraw.Draw(img)
d.rectangle([3, 3, 12, 12], fill=SLATE)
d.ellipse([6, 6, 9, 9], fill=NORTH)
border(img)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/magnetic_switch_top.png")

# side: just the body
img = new(IRON)
d = ImageDraw.Draw(img)
d.rectangle([2, 4, 13, 11], fill=SLATE)
d.line([(2, 7), (13, 7)], fill=SLATE_DARK)
d.line([(2, 8), (13, 8)], fill=SLATE_DARK)
border(img)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/magnetic_switch_side.png")

# bottom
img = new(IRON)
border(img)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/magnetic_switch_bottom.png")

print("Generated magnetic_switch textures.")
