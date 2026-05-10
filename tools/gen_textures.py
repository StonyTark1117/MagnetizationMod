#!/usr/bin/env python3
"""Generate 16x16 placeholder textures for the Magnetization mod.

Each texture is recognizable but obviously placeholder — solid bases with
distinct iconography per block face / item. Saves directly into the mod's
asset tree.

Color palette
- iron base: #b8b8b8
- darker iron: #888888
- copper coil: #c87038
- pole north (red): #c83838
- pole south (blue): #3878c8
- lodestone slate: #404040
- arrow / accent: #ffd848
"""
from PIL import Image, ImageDraw
import os

ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures"
BLOCK = os.path.join(ASSETS, "block")
ITEM = os.path.join(ASSETS, "item")
os.makedirs(BLOCK, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)

IRON = (184, 184, 184, 255)
IRON_DARK = (136, 136, 136, 255)
COPPER = (200, 112, 56, 255)
NORTH = (200, 56, 56, 255)
SOUTH = (56, 120, 200, 255)
SLATE = (64, 64, 64, 255)
SLATE_DARK = (44, 44, 44, 255)
ACCENT = (255, 216, 72, 255)
WHITE = (240, 240, 240, 255)


def new(color=IRON):
    return Image.new("RGBA", (16, 16), color)


def hatch(img, color=IRON_DARK, step=4):
    """Diagonal hatch lines."""
    d = ImageDraw.Draw(img)
    for i in range(-16, 16, step):
        d.line([(i, 0), (i + 16, 16)], fill=color)


def border(img, color=IRON_DARK, width=1):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=color, width=width)


def rivets(img, color=IRON_DARK):
    """4 corner rivets."""
    d = ImageDraw.Draw(img)
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        d.rectangle([x, y, x + 1, y + 1], fill=color)


def coil_band(img, color=COPPER):
    """Horizontal coil bands."""
    d = ImageDraw.Draw(img)
    for y in (4, 8, 12):
        d.rectangle([1, y - 1, 14, y], fill=color)


def pole_letter(img, letter, color):
    """Big bold letter centered (N/S)."""
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


def concentric(img, fg=ACCENT, bg=SLATE_DARK):
    """Concentric squares / pole indicator."""
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], fill=bg)
    d.rectangle([3, 3, 12, 12], outline=fg)
    d.rectangle([6, 6, 9, 9], fill=fg)


def upward_arrows(img, color=ACCENT):
    d = ImageDraw.Draw(img)
    for x in (3, 8, 13):
        d.line([(x, 12), (x, 5)], fill=color)
        d.line([(x - 2, 7), (x, 5)], fill=color)
        d.line([(x + 2, 7), (x, 5)], fill=color)


def lens(img, color=SOUTH):
    """Round lens for tractor beam front."""
    d = ImageDraw.Draw(img)
    d.ellipse([3, 3, 12, 12], outline=color, width=1)
    d.ellipse([6, 6, 9, 9], fill=color)


def horizontal_arrow(img, color=ACCENT):
    """Right-pointing arrow on top of mag-lev rail."""
    d = ImageDraw.Draw(img)
    d.line([(2, 8), (12, 8)], fill=color, width=2)
    d.line([(10, 5), (13, 8)], fill=color, width=1)
    d.line([(10, 11), (13, 8)], fill=color, width=1)


def shaft_hole(img, color=SLATE_DARK):
    """Octagonal shaft socket on side faces of kinetic block."""
    d = ImageDraw.Draw(img)
    d.rectangle([5, 5, 10, 10], fill=color)
    d.rectangle([6, 4, 9, 11], fill=color)
    d.rectangle([4, 6, 11, 9], fill=color)


# ---------------- electromagnet ----------------
img = new(IRON); coil_band(img); border(img); rivets(img)
img.save(f"{BLOCK}/electromagnet_side.png")

img = new(IRON); pole_letter(img, "N", NORTH); border(img)
img.save(f"{BLOCK}/electromagnet_top.png")

img = new(IRON); pole_letter(img, "S", SOUTH); border(img)
img.save(f"{BLOCK}/electromagnet_bottom.png")

# ---------------- kinetic electromagnet ----------------
img = new(IRON); coil_band(img); shaft_hole(img); border(img)
img.save(f"{BLOCK}/kinetic_electromagnet_side.png")

img = new(IRON); pole_letter(img, "N", NORTH); shaft_hole(img); border(img)
img.save(f"{BLOCK}/kinetic_electromagnet_top.png")

img = new(IRON); pole_letter(img, "S", SOUTH); shaft_hole(img); border(img)
img.save(f"{BLOCK}/kinetic_electromagnet_bottom.png")

# ---------------- magnetic anchor ----------------
img = new(SLATE); concentric(img, fg=NORTH, bg=SLATE_DARK)
img.save(f"{BLOCK}/magnetic_anchor_top.png")

img = new(SLATE); hatch(img, color=SLATE_DARK, step=3); border(img, color=SLATE_DARK); rivets(img, color=ACCENT)
img.save(f"{BLOCK}/magnetic_anchor_side.png")

# ---------------- repulsor coil ----------------
img = new(IRON); upward_arrows(img); border(img)
img.save(f"{BLOCK}/repulsor_coil_top.png")

img = new(IRON); coil_band(img, color=ACCENT); border(img); rivets(img)
img.save(f"{BLOCK}/repulsor_coil_side.png")

img = new(IRON_DARK); border(img); rivets(img, color=IRON)
img.save(f"{BLOCK}/repulsor_coil_bottom.png")

# ---------------- tractor beam ----------------
img = new(IRON); lens(img, color=SOUTH); border(img)
img.save(f"{BLOCK}/tractor_beam_front.png")

img = new(IRON); border(img); rivets(img); coil_band(img, color=SLATE_DARK)
img.save(f"{BLOCK}/tractor_beam_side.png")

img = new(IRON); border(img); rivets(img)
img.save(f"{BLOCK}/tractor_beam_back.png")

# ---------------- maglev rail ----------------
img = new(IRON); horizontal_arrow(img); border(img)
img.save(f"{BLOCK}/maglev_rail_top.png")

img = new(IRON); border(img); coil_band(img, color=ACCENT)
img.save(f"{BLOCK}/maglev_rail_side.png")

img = new(IRON_DARK); border(img)
img.save(f"{BLOCK}/maglev_rail_bottom.png")

# ---------------- lodestone core ----------------
img = new(SLATE); concentric(img, fg=ACCENT, bg=SLATE_DARK); border(img, color=SLATE_DARK)
img.save(f"{BLOCK}/lodestone_core.png")

# ---------------- items ----------------
def ingot(img, base=IRON, accent=NORTH):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], fill=(0, 0, 0, 0))  # transparent
    # Body of ingot
    d.polygon([(2, 9), (4, 6), (12, 6), (14, 9), (14, 11), (12, 12), (4, 12), (2, 11)], fill=base)
    # Highlight
    d.line([(4, 7), (12, 7)], fill=(220, 220, 220, 255))
    # Polarity dot
    d.rectangle([7, 9, 9, 10], fill=accent)


img = new((0, 0, 0, 0)); ingot(img, base=(120, 140, 180, 255), accent=NORTH)
img.save(f"{ITEM}/ferromagnetic_ingot.png")

# Magnetic plate: flatter rectangle
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rectangle([2, 6, 13, 10], fill=(160, 170, 200, 255))
d.rectangle([2, 6, 13, 10], outline=(80, 90, 120, 255))
d.line([(7, 7), (8, 7)], fill=NORTH)
d.line([(7, 9), (8, 9)], fill=SOUTH)
img.save(f"{ITEM}/magnetic_plate.png")

print("Generated textures:")
for d in (BLOCK, ITEM):
    for f in sorted(os.listdir(d)):
        print(f"  {d}/{f}")
