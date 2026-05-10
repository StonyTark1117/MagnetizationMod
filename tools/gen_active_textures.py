#!/usr/bin/env python3
"""Generate _active variants of block textures: brighter coil bands, glowing
accents. Mirrors gen_textures.py but with hotter colors so the active
blockstate visibly differs from inactive."""
from PIL import Image, ImageDraw
import os

ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures"
BLOCK = os.path.join(ASSETS, "block")
os.makedirs(BLOCK, exist_ok=True)

IRON = (200, 200, 210, 255)         # slightly cooler/brighter than inactive
IRON_DARK = (136, 136, 136, 255)
COIL_HOT = (255, 168, 80, 255)      # glowing copper
NORTH_HOT = (255, 90, 90, 255)
SOUTH_HOT = (90, 160, 255, 255)
SLATE = (72, 72, 72, 255)
SLATE_DARK = (44, 44, 44, 255)
ACCENT_HOT = (255, 240, 120, 255)


def new(c=IRON):
    return Image.new("RGBA", (16, 16), c)


def border(img, c=IRON_DARK):
    ImageDraw.Draw(img).rectangle([0, 0, 15, 15], outline=c)


def rivets(img, c=ACCENT_HOT):
    d = ImageDraw.Draw(img)
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        d.rectangle([x, y, x + 1, y + 1], fill=c)


def coil_band(img, c=COIL_HOT):
    d = ImageDraw.Draw(img)
    for y in (4, 8, 12):
        d.rectangle([1, y - 1, 14, y], fill=c)


def pole_letter(img, letter, color):
    d = ImageDraw.Draw(img)
    if letter == "N":
        d.line([(5, 4), (5, 12)], fill=color, width=2)
        d.line([(11, 4), (11, 12)], fill=color, width=2)
        d.line([(5, 4), (11, 12)], fill=color, width=2)
    elif letter == "S":
        d.line([(5, 4), (11, 4)], fill=color, width=2)
        d.line([(5, 4), (5, 8)], fill=color, width=2)
        d.line([(5, 8), (11, 8)], fill=color, width=2)
        d.line([(11, 8), (11, 12)], fill=color, width=2)
        d.line([(5, 12), (11, 12)], fill=color, width=2)


def shaft_hole(img, c=SLATE_DARK):
    d = ImageDraw.Draw(img)
    d.rectangle([5, 5, 10, 10], fill=c)
    d.rectangle([6, 4, 9, 11], fill=c)
    d.rectangle([4, 6, 11, 9], fill=c)


def concentric(img, fg, bg=SLATE_DARK):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], fill=bg)
    d.rectangle([3, 3, 12, 12], outline=fg)
    d.rectangle([6, 6, 9, 9], fill=fg)


def upward_arrows(img, c=ACCENT_HOT):
    d = ImageDraw.Draw(img)
    for x in (3, 8, 13):
        d.line([(x, 12), (x, 5)], fill=c)
        d.line([(x - 2, 7), (x, 5)], fill=c)
        d.line([(x + 2, 7), (x, 5)], fill=c)


def lens(img, c=SOUTH_HOT):
    d = ImageDraw.Draw(img)
    d.ellipse([3, 3, 12, 12], outline=c)
    d.ellipse([5, 5, 10, 10], fill=c)


def horizontal_arrow(img, c=ACCENT_HOT):
    d = ImageDraw.Draw(img)
    d.line([(2, 8), (12, 8)], fill=c, width=2)
    d.line([(10, 5), (13, 8)], fill=c, width=1)
    d.line([(10, 11), (13, 8)], fill=c, width=1)


def hatch(img, c=SLATE_DARK, step=3):
    d = ImageDraw.Draw(img)
    for i in range(-16, 16, step):
        d.line([(i, 0), (i + 16, 16)], fill=c)


# electromagnet (active)
img = new(IRON); coil_band(img); border(img); rivets(img)
img.save(f"{BLOCK}/electromagnet_side_active.png")
img = new(IRON); pole_letter(img, "N", NORTH_HOT); border(img)
img.save(f"{BLOCK}/electromagnet_top_active.png")
img = new(IRON); pole_letter(img, "S", SOUTH_HOT); border(img)
img.save(f"{BLOCK}/electromagnet_bottom_active.png")

# kinetic electromagnet (active)
img = new(IRON); coil_band(img); shaft_hole(img); border(img)
img.save(f"{BLOCK}/kinetic_electromagnet_side_active.png")
img = new(IRON); pole_letter(img, "N", NORTH_HOT); shaft_hole(img); border(img)
img.save(f"{BLOCK}/kinetic_electromagnet_top_active.png")
img = new(IRON); pole_letter(img, "S", SOUTH_HOT); shaft_hole(img); border(img)
img.save(f"{BLOCK}/kinetic_electromagnet_bottom_active.png")

# magnetic anchor (active)
img = new(SLATE); concentric(img, fg=NORTH_HOT, bg=SLATE_DARK)
img.save(f"{BLOCK}/magnetic_anchor_top_active.png")
img = new(SLATE); hatch(img); border(img, c=SLATE_DARK); rivets(img, c=ACCENT_HOT)
img.save(f"{BLOCK}/magnetic_anchor_side_active.png")

# repulsor coil (active)
img = new(IRON); upward_arrows(img); border(img)
img.save(f"{BLOCK}/repulsor_coil_top_active.png")
img = new(IRON); coil_band(img, c=ACCENT_HOT); border(img); rivets(img)
img.save(f"{BLOCK}/repulsor_coil_side_active.png")

# tractor beam (active)
img = new(IRON); lens(img, c=SOUTH_HOT); border(img)
img.save(f"{BLOCK}/tractor_beam_front_active.png")
img = new(IRON); border(img); rivets(img); coil_band(img, c=SLATE_DARK)
img.save(f"{BLOCK}/tractor_beam_side_active.png")

# maglev rail (active)
img = new(IRON); horizontal_arrow(img); border(img)
img.save(f"{BLOCK}/maglev_rail_top_active.png")
img = new(IRON); border(img); coil_band(img, c=ACCENT_HOT)
img.save(f"{BLOCK}/maglev_rail_side_active.png")

print("Generated active textures.")
