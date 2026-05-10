#!/usr/bin/env python3
"""Permanent magnet textures: top face shows the polarity (red N or blue S),
sides show banded ferrite. North and south variants share the side texture but
swap the top face."""
from PIL import Image, ImageDraw

IRON = (184, 184, 184, 255)
IRON_DARK = (120, 120, 120, 255)
NORTH = (200, 56, 56, 255)
SOUTH = (56, 120, 200, 255)
SLATE = (64, 64, 64, 255)


def new(c=IRON):
    return Image.new("RGBA", (16, 16), c)


def border(img, c=IRON_DARK):
    ImageDraw.Draw(img).rectangle([0, 0, 15, 15], outline=c)


def horseshoe(img, color):
    """U-shaped horseshoe magnet silhouette."""
    d = ImageDraw.Draw(img)
    # Left arm
    d.rectangle([3, 3, 5, 11], fill=color)
    # Right arm
    d.rectangle([10, 3, 12, 11], fill=color)
    # Bottom curve
    d.rectangle([3, 9, 12, 11], fill=color)


def pole_letter(img, letter, color):
    d = ImageDraw.Draw(img)
    if letter == "N":
        d.line([(5, 4), (5, 12)], fill=color, width=2)
        d.line([(11, 4), (11, 12)], fill=color, width=2)
        d.line([(5, 4), (11, 12)], fill=color, width=2)
    else:
        d.line([(5, 4), (11, 4)], fill=color, width=2)
        d.line([(5, 4), (5, 8)], fill=color, width=2)
        d.line([(5, 8), (11, 8)], fill=color, width=2)
        d.line([(11, 8), (11, 12)], fill=color, width=2)
        d.line([(5, 12), (11, 12)], fill=color, width=2)


# Top: north variant (red horseshoe + N letter overlay)
img = new(IRON)
horseshoe(img, NORTH)
border(img)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/permanent_magnet_top_north.png")

# Top: south variant
img = new(IRON)
horseshoe(img, SOUTH)
border(img)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/permanent_magnet_top_south.png")

# Side: striped ferrite — same for both polarities
img = new(IRON)
d = ImageDraw.Draw(img)
for y in (3, 7, 11):
    d.rectangle([1, y, 14, y + 1], fill=SLATE)
border(img)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/permanent_magnet_side.png")

# Bottom
img = new(IRON_DARK); border(img, c=IRON_DARK)
img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block/permanent_magnet_bottom.png")

print("Generated permanent_magnet textures.")
