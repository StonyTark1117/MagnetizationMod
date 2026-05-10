#!/usr/bin/env python3
"""Better procedural textures for the Magnetization addon.

Strategy: load real vanilla 1.21.1 block textures from the cached client jar
and composite addon-specific iconography on top. This gives every block face
the same noise / dithering / color depth as Minecraft itself, so the addon
visually fits in.

Run with `python3 tools/gen_textures_v2.py` — overwrites existing PNGs in the
asset tree. Active variants get a slightly emissive overlay.
"""
from __future__ import annotations

import io
import os
import zipfile
from PIL import Image, ImageChops, ImageDraw, ImageEnhance

CLIENT_JAR = os.path.expanduser(
    "~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
)
ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures"
BLOCK = os.path.join(ASSETS, "block")
ITEM = os.path.join(ASSETS, "item")
PARTICLE = os.path.join(ASSETS, "particle")
os.makedirs(BLOCK, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)
os.makedirs(PARTICLE, exist_ok=True)

# Iconography palette — keep tuned to vanilla saturation, not pure RGB.
NORTH = (200, 56, 56, 255)        # red oxide
SOUTH = (66, 130, 200, 255)       # cobalt blue
ACCENT = (235, 195, 80, 255)       # gold
WIRE = (180, 70, 50, 255)         # redstone wire
SLATE_DARK = (28, 28, 32, 255)
COPPER = (193, 105, 75, 255)       # vanilla copper hue
COPPER_HOT = (255, 168, 96, 255)


def vanilla(path: str) -> Image.Image:
    """Load a vanilla texture by path (e.g. 'block/iron_block')."""
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        with zf.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(io.BytesIO(f.read())).convert("RGBA").copy()


def overlay(base: Image.Image, draw_fn) -> Image.Image:
    """Copy `base`, then run `draw_fn(d, img)` for the iconography."""
    img = base.copy()
    d = ImageDraw.Draw(img, "RGBA")
    draw_fn(d, img)
    return img


def emissive(img: Image.Image, factor: float = 1.4) -> Image.Image:
    """Brighten the image to suggest the active state. Doesn't actually add fullbright
    rendering — that would need a separate emissive texture layer in 1.21+ — but
    visually distinguishes active from inactive."""
    enhancer = ImageEnhance.Brightness(img)
    return enhancer.enhance(factor)


def darken(img: Image.Image, factor: float = 0.55) -> Image.Image:
    """Knock the base brightness down. Used to shift vanilla-iron-based blocks
    (electromagnet, kinetic, switch, tractor, permanent magnet) onto a darker
    palette so the addon reads as 'industrial / metallic' instead of 'shiny iron'."""
    enhancer = ImageEnhance.Brightness(img)
    return enhancer.enhance(factor)


def border(d: ImageDraw.ImageDraw, color=SLATE_DARK):
    d.rectangle([0, 0, 15, 15], outline=color)


# ---------------- iconography helpers ----------------

def coil_band(d, color=COPPER):
    """Three horizontal coil bands across the side face."""
    for y in (4, 8, 12):
        d.rectangle([1, y - 1, 14, y], fill=color)


def pole_letter(d, letter, color, small=False):
    """Bold N or S centered horizontally and vertically in the 16x16 face. Letter
    spans x=4..12 (8 px), giving a true center at x=8. When `small` is True
    (kinetic side faces), the letter shrinks vertically and uses a 1-px stroke
    so it sits inside the y=7..11 strip below the shaft hole rather than
    colliding with it."""
    if small:
        # Compact glyph for kinetic faces. Sits below the shaft hole in the
        # y=7..11 band; uses 1-px strokes so it never overlaps the socket.
        if letter == "N":
            d.line([(5, 7), (5, 11)], fill=color, width=1)
            d.line([(10, 7), (10, 11)], fill=color, width=1)
            d.line([(5, 7), (10, 11)], fill=color, width=1)
        else:  # "S"
            d.line([(5, 7), (10, 7)], fill=color, width=1)
            d.line([(5, 7), (5, 9)], fill=color, width=1)
            d.line([(5, 9), (10, 9)], fill=color, width=1)
            d.line([(10, 9), (10, 11)], fill=color, width=1)
            d.line([(5, 11), (10, 11)], fill=color, width=1)
        return
    if letter == "N":
        d.line([(4, 4), (4, 12)], fill=color, width=2)
        d.line([(12, 4), (12, 12)], fill=color, width=2)
        d.line([(4, 4), (12, 12)], fill=color, width=2)
    else:
        d.line([(4, 4), (12, 4)], fill=color, width=2)
        d.line([(4, 4), (4, 8)], fill=color, width=2)
        d.line([(4, 8), (12, 8)], fill=color, width=2)
        d.line([(12, 8), (12, 12)], fill=color, width=2)
        d.line([(4, 12), (12, 12)], fill=color, width=2)


def shaft_hole(d, color=SLATE_DARK):
    """Octagonal shaft socket on side faces of kinetic block. Sits in y=4..7
    so the small pole_letter (y=7..11) doesn't overlap it."""
    d.rectangle([5, 4, 10, 7], fill=color)
    d.rectangle([6, 3, 9, 8], fill=color)
    d.rectangle([4, 5, 11, 6], fill=color)


def horizontal_arrow(d, color=ACCENT):
    d.line([(2, 8), (12, 8)], fill=color, width=2)
    d.line([(10, 5), (13, 8)], fill=color, width=1)
    d.line([(10, 11), (13, 8)], fill=color, width=1)


def lens(d, color=SOUTH):
    """Round lens for tractor beam front."""
    d.ellipse([3, 3, 12, 12], outline=color, width=1)
    d.ellipse([6, 6, 9, 9], fill=color)


def horseshoe(d, color):
    """U-shaped horseshoe magnet silhouette."""
    d.rectangle([3, 3, 5, 11], fill=color)
    d.rectangle([10, 3, 12, 11], fill=color)
    d.rectangle([3, 9, 12, 11], fill=color)


def concentric(d, fg, bg=SLATE_DARK):
    d.rectangle([0, 0, 15, 15], fill=bg)
    d.rectangle([3, 3, 12, 12], outline=fg)
    d.rectangle([6, 6, 9, 9], fill=fg)


def lightning_zigzag(d, color=ACCENT):
    """Stylized 'redstone' zigzag for the inverter block."""
    d.line([(4, 3), (8, 7), (5, 8), (12, 13)], fill=color, width=2)


# ---------------- emit ----------------

def save(img: Image.Image, dest: str):
    img.save(dest)


# Bases. The user wants a darker addon palette — pull each iron-derived base
# toward black up front, then composite glyphs as before. Ore/lodestone/copper
# bases are already on the darker side, so they keep their vanilla brightness.
iron       = darken(vanilla("block/iron_block"))
dark_iron  = darken(vanilla("block/iron_block"), 0.40)  # for switch / inverter
lodestone_top  = vanilla("block/lodestone_top")
lodestone_side = vanilla("block/lodestone_side")
copper_grate   = vanilla("block/copper_grate")
copper_block   = vanilla("block/copper_block")
netherite      = vanilla("block/netherite_block")
raw_iron       = darken(vanilla("block/raw_iron_block"), 0.70)
smooth_stone   = darken(vanilla("block/smooth_stone"), 0.45)

# ---------------- electromagnet ----------------
save(overlay(iron, lambda d, img: (coil_band(d, COPPER), border(d))[0]),
     f"{BLOCK}/electromagnet_side.png")
save(overlay(iron, lambda d, img: pole_letter(d, "N", NORTH)),
     f"{BLOCK}/electromagnet_top.png")
save(overlay(iron, lambda d, img: pole_letter(d, "S", SOUTH)),
     f"{BLOCK}/electromagnet_bottom.png")

# active variants — brighter coils, brighter pole color
save(emissive(overlay(iron, lambda d, img: (coil_band(d, COPPER_HOT), border(d))[0])),
     f"{BLOCK}/electromagnet_side_active.png")
save(emissive(overlay(iron, lambda d, img: pole_letter(d, "N", NORTH))),
     f"{BLOCK}/electromagnet_top_active.png")
save(emissive(overlay(iron, lambda d, img: pole_letter(d, "S", SOUTH))),
     f"{BLOCK}/electromagnet_bottom_active.png")

# ---------------- kinetic electromagnet ----------------
# Side faces have a shaft hole (Create kinetic socket); the small pole_letter
# variant tucks below the hole at y=7..11 so the two never collide.
save(overlay(iron, lambda d, img: (coil_band(d), shaft_hole(d), border(d))[0]),
     f"{BLOCK}/kinetic_electromagnet_side.png")
save(overlay(iron, lambda d, img: (shaft_hole(d), pole_letter(d, "N", NORTH, small=True))[0]),
     f"{BLOCK}/kinetic_electromagnet_top.png")
save(overlay(iron, lambda d, img: (shaft_hole(d), pole_letter(d, "S", SOUTH, small=True))[0]),
     f"{BLOCK}/kinetic_electromagnet_bottom.png")
save(emissive(overlay(iron, lambda d, img: (coil_band(d, COPPER_HOT), shaft_hole(d), border(d))[0])),
     f"{BLOCK}/kinetic_electromagnet_side_active.png")
save(emissive(overlay(iron, lambda d, img: (shaft_hole(d), pole_letter(d, "N", NORTH, small=True))[0])),
     f"{BLOCK}/kinetic_electromagnet_top_active.png")
save(emissive(overlay(iron, lambda d, img: (shaft_hole(d), pole_letter(d, "S", SOUTH, small=True))[0])),
     f"{BLOCK}/kinetic_electromagnet_bottom_active.png")

# ---------------- magnetic anchor ----------------
save(overlay(lodestone_top, lambda d, img: concentric(d, NORTH, SLATE_DARK)),
     f"{BLOCK}/magnetic_anchor_top.png")
save(lodestone_side.copy(), f"{BLOCK}/magnetic_anchor_side.png")
save(emissive(overlay(lodestone_top, lambda d, img: concentric(d, NORTH, SLATE_DARK))),
     f"{BLOCK}/magnetic_anchor_top_active.png")
save(emissive(lodestone_side.copy()),
     f"{BLOCK}/magnetic_anchor_side_active.png")

# ---------------- repulsor coil ----------------
# Yellow arrows removed per UX feedback — the copper grate already reads as
# 'air vent / output' which is enough; players figure out direction from
# placement orientation rather than a glyph that can be wrong-side-up.
save(copper_grate.copy(), f"{BLOCK}/repulsor_coil_top.png")
save(overlay(copper_block, lambda d, img: (coil_band(d, COPPER), border(d))[0]),
     f"{BLOCK}/repulsor_coil_side.png")
save(copper_block.copy(), f"{BLOCK}/repulsor_coil_bottom.png")
save(emissive(copper_grate.copy()),
     f"{BLOCK}/repulsor_coil_top_active.png")
save(emissive(overlay(copper_block, lambda d, img: (coil_band(d, COPPER_HOT), border(d))[0])),
     f"{BLOCK}/repulsor_coil_side_active.png")

# ---------------- tractor beam ----------------
save(overlay(iron, lambda d, img: lens(d, SOUTH)),
     f"{BLOCK}/tractor_beam_front.png")
save(overlay(iron, lambda d, img: (coil_band(d, SLATE_DARK), border(d))[0]),
     f"{BLOCK}/tractor_beam_side.png")
save(iron.copy(), f"{BLOCK}/tractor_beam_back.png")
save(emissive(overlay(iron, lambda d, img: lens(d, SOUTH))),
     f"{BLOCK}/tractor_beam_front_active.png")
save(emissive(overlay(iron, lambda d, img: (coil_band(d, SLATE_DARK), border(d))[0])),
     f"{BLOCK}/tractor_beam_side_active.png")

# (maglev_rail removed in phase 13; texture generation deleted with it.)

# ---------------- lodestone core ----------------
save(overlay(lodestone_top, lambda d, img: concentric(d, ACCENT, SLATE_DARK)),
     f"{BLOCK}/lodestone_core.png")

# ---------------- magnetic switch ----------------
# Darker base so the red sensor dot pops; smooth_stone alone read too white.
save(overlay(smooth_stone, lambda d, img: (
    d.rectangle([3, 3, 12, 12], fill=SLATE_DARK),
    d.ellipse([6, 6, 9, 9], fill=NORTH),
    border(d)
)[0]), f"{BLOCK}/magnetic_switch_top.png")
save(overlay(smooth_stone, lambda d, img: (
    d.rectangle([2, 4, 13, 11], fill=SLATE_DARK),
    border(d)
)[0]), f"{BLOCK}/magnetic_switch_side.png")
save(smooth_stone.copy(), f"{BLOCK}/magnetic_switch_bottom.png")

# ---------------- permanent magnet ----------------
save(overlay(raw_iron, lambda d, img: (horseshoe(d, NORTH), border(d))[0]),
     f"{BLOCK}/permanent_magnet_top_north.png")
save(overlay(raw_iron, lambda d, img: (horseshoe(d, SOUTH), border(d))[0]),
     f"{BLOCK}/permanent_magnet_top_south.png")
save(overlay(raw_iron, lambda d, img: (
    d.rectangle([1, 3, 14, 4], fill=SLATE_DARK),
    d.rectangle([1, 7, 14, 8], fill=SLATE_DARK),
    d.rectangle([1, 11, 14, 12], fill=SLATE_DARK),
    border(d)
)[0]), f"{BLOCK}/permanent_magnet_side.png")
save(raw_iron.copy(), f"{BLOCK}/permanent_magnet_bottom.png")

# ---------------- items ----------------
def ingot_overlay(base: Image.Image, accent_color) -> Image.Image:
    """Take a vanilla iron-ingot-shaped texture and tint it with a polarity dot."""
    img = base.copy()
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([7, 9, 8, 10], fill=accent_color)
    return img


vanilla_iron_ingot = vanilla("item/iron_ingot")
vanilla_iron_nugget = vanilla("item/iron_nugget")
vanilla_compass_16 = vanilla("item/compass_16")  # try 16-frame compass; fall back below

save(ingot_overlay(vanilla_iron_ingot, NORTH), f"{ITEM}/ferromagnetic_ingot.png")

# Plate: take iron_nugget and stretch the highlight horizontally.
plate = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
pd = ImageDraw.Draw(plate, "RGBA")
pd.rectangle([2, 6, 13, 10], fill=(165, 175, 200, 255))
pd.rectangle([2, 6, 13, 10], outline=(80, 90, 120, 255))
pd.line([(3, 7), (12, 7)], fill=(220, 220, 240, 255))
pd.line([(7, 7), (8, 7)], fill=NORTH)
pd.line([(7, 9), (8, 9)], fill=SOUTH)
save(plate, f"{ITEM}/magnetic_plate.png")

# Field compass — overlay polarity dial onto vanilla compass frame 0.
try:
    compass_base = vanilla("item/compass/compass_00")
except Exception:
    compass_base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cd = ImageDraw.Draw(compass_base, "RGBA")
    cd.ellipse([1, 1, 14, 14], outline=ACCENT)
fc = compass_base.copy()
fcd = ImageDraw.Draw(fc, "RGBA")
fcd.line([(8, 4), (8, 8)], fill=NORTH, width=2)
fcd.line([(8, 8), (8, 11)], fill=SOUTH, width=2)
fcd.rectangle([7, 7, 8, 8], fill=(240, 240, 240, 255))
save(fc, f"{ITEM}/field_compass.png")

# ---------------- particle ----------------
particle = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
for y in range(8):
    for x in range(8):
        dx = x - 3.5
        dy = y - 3.5
        r = (dx * dx + dy * dy) ** 0.5
        if r > 3.5:
            continue
        a = int(255 * max(0.0, 1.0 - r / 3.5))
        particle.putpixel((x, y), (255, 255, 255, a))
save(particle, f"{PARTICLE}/mag_field.png")

print("Re-authored textures using vanilla bases.")
