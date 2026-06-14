#!/usr/bin/env python3
"""Create-style machine & magnet block textures for the Magnetization addon.

Magnetization is a Create: Aeronautics addon, so its machines should read as
Create machinery, not recolored vanilla iron. Strategy: load Create's own
casing textures (andesite / brass / copper) straight from the Create mod jar
in the Gradle cache and composite Magnetization iconography into a dark inset
"face" panel on top. The shared casing frame ties every block into one set;
the casing *material* signals the block's role:

  copper  -> electromagnetic field emitters (electromagnet, repulsor coil,
             tractor beam, magnetic excavator)
  andesite-> structural / sensing (magnetic anchor, magnetic switch)
  brass   -> advanced control + permanent magnet (polarity inverter, magnets)

Run: python3 tools/gen_machines_create.py
Overwrites the machine/magnet PNGs in the asset tree. Existing .mcmeta files
for active variants are left untouched (active frames stay 16x16, single-frame).
"""
from __future__ import annotations

import glob
import io
import os
import zipfile
from PIL import Image, ImageDraw, ImageEnhance

# ---------------------------------------------------------------- sources
HOME = os.path.expanduser("~")
CREATE_JAR = glob.glob(
    f"{HOME}/.gradle/caches/modules-2/files-2.1/com.simibubi.create/"
    "create-1.21.1/*/*/create-1.21.1-*.jar"
)
if not CREATE_JAR:
    raise SystemExit("Create jar not found in gradle cache")
CREATE_JAR = CREATE_JAR[0]

ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures"
BLOCK = os.path.join(ASSETS, "block")

# ---------------------------------------------------------------- palette
NORTH = (206, 62, 58, 255)      # red oxide  (N pole)
SOUTH = (70, 132, 204, 255)     # cobalt blue (S pole)
ACCENT = (231, 190, 92, 255)    # brass gold
COPPER = (196, 110, 78, 255)
COPPER_HOT = (255, 176, 104, 255)
PANEL = (24, 24, 28, 200)       # recessed face fill
PANEL_EDGE = (10, 10, 12, 230)  # recess shadow
PANEL_LIP = (62, 62, 70, 150)   # recess bottom-right catch-light


def create(path: str) -> Image.Image:
    """Load a Create block texture, e.g. 'andesite_casing'. Crops to top 16px
    so animated/connected variants degrade to a plain 16x16 face."""
    with zipfile.ZipFile(CREATE_JAR) as zf:
        with zf.open(f"assets/create/textures/block/{path}.png") as f:
            im = Image.open(io.BytesIO(f.read())).convert("RGBA").copy()
    if im.size != (16, 16):
        im = im.crop((0, 0, 16, 16))
    return im


# ---------------------------------------------------------------- compositing
def face(base: Image.Image, draw_fn=None, inset=True) -> Image.Image:
    """Copy a casing base, optionally carve a dark inset face panel, then run
    draw_fn(d) for the glyph. The casing frame (outer 2px) is preserved."""
    img = base.copy()
    d = ImageDraw.Draw(img, "RGBA")
    if inset:
        d.rectangle([3, 3, 12, 12], fill=PANEL)
        d.line([(3, 3), (12, 3)], fill=PANEL_EDGE)   # top shadow
        d.line([(3, 3), (3, 12)], fill=PANEL_EDGE)   # left shadow
        d.line([(3, 12), (12, 12)], fill=PANEL_LIP)  # bottom catch-light
        d.line([(12, 3), (12, 12)], fill=PANEL_LIP)  # right catch-light
    if draw_fn:
        draw_fn(d)
    return img


def emissive(img: Image.Image, factor: float = 1.45) -> Image.Image:
    return ImageEnhance.Brightness(img).enhance(factor)


def save(img: Image.Image, name: str):
    img.save(f"{BLOCK}/{name}.png")


# ---------------------------------------------------------------- glyphs
def pole_letter(d, letter, color):
    """Bold N or S, 2px stroke, centered in the inset panel (x 4..12, y 4..12)."""
    if letter == "N":
        d.line([(5, 4), (5, 12)], fill=color, width=2)
        d.line([(11, 4), (11, 12)], fill=color, width=2)
        d.line([(5, 4), (11, 12)], fill=color, width=2)
    else:  # S
        d.line([(5, 4), (11, 4)], fill=color, width=2)
        d.line([(5, 4), (5, 8)], fill=color, width=2)
        d.line([(5, 8), (11, 8)], fill=color, width=2)
        d.line([(11, 8), (11, 12)], fill=color, width=2)
        d.line([(5, 12), (11, 12)], fill=color, width=2)


def coil_bands(d, color=COPPER):
    """Horizontal coil windings across the inset panel side face. Full-width
    bands with thin dark gaps read as a wound solenoid, not a grid."""
    hi = tuple(min(255, c + 35) for c in color[:3]) + (255,)
    for y in (4, 7, 10):
        d.rectangle([4, y, 11, y + 1], fill=color)
        d.line([(4, y), (11, y)], fill=hi)  # top highlight per winding


def concentric(d, color, dot=True):
    d.rectangle([4, 4, 11, 11], outline=color)
    d.rectangle([6, 6, 9, 9], outline=color)
    if dot:
        d.rectangle([7, 7, 8, 8], fill=color)


def sensor_dot(d, color=NORTH):
    d.ellipse([5, 5, 10, 10], outline=(60, 60, 66, 255))
    d.ellipse([6, 6, 9, 9], fill=color)


def drill_crosshair(d, color):
    d.rectangle([4, 4, 11, 11], outline=color)
    d.rectangle([6, 6, 9, 9], outline=color)
    d.line([(7, 4), (7, 11)], fill=color)
    d.line([(8, 4), (8, 11)], fill=color)
    d.line([(4, 7), (11, 7)], fill=color)
    d.line([(4, 8), (11, 8)], fill=color)


def lens(d, color=SOUTH):
    d.ellipse([4, 4, 11, 11], outline=color)
    d.ellipse([5, 5, 10, 10], outline=(180, 210, 240, 200))
    d.ellipse([6, 6, 9, 9], fill=color)


def horseshoe(d, color):
    """U-shaped horseshoe magnet, tips up, with bright pole caps."""
    d.rectangle([4, 5, 6, 11], fill=color)
    d.rectangle([9, 5, 11, 11], fill=color)
    d.rectangle([4, 9, 11, 11], fill=color)
    # pole tips brightened
    cap = tuple(min(255, c + 40) for c in color[:3]) + (255,)
    d.rectangle([4, 4, 6, 5], fill=cap)
    d.rectangle([9, 4, 11, 5], fill=cap)


def polarity_flip(d):
    """Reversed-polarity glyph: two curved arrows forming a rotation loop, the
    top arc red (N) and the bottom arc blue (S) -> the poles swap. Clean,
    high-contrast, reads at 16px."""
    box = [4, 4, 11, 11]
    # top half = red arc sweeping right, bottom half = blue arc sweeping left
    d.arc(box, start=200, end=350, fill=NORTH, width=2)   # upper arc (red)
    d.arc(box, start=20, end=170, fill=SOUTH, width=2)    # lower arc (blue)
    # arrowheads continuing each arc's direction (clockwise loop)
    d.polygon([(11, 6), (9, 5), (12, 9)], fill=NORTH)     # red head, top-right
    d.polygon([(4, 9), (6, 10), (3, 6)], fill=SOUTH)      # blue head, bottom-left


# ---------------------------------------------------------------- bases
andesite = create("andesite_casing")
brass = create("brass_casing")
copper = create("copper_casing")

# ================================================================ ELECTROMAGNET
# Copper casing = electromagnetic coil. N pole top, S pole bottom, windings side.
save(face(copper, lambda d: pole_letter(d, "N", NORTH)), "electromagnet_top")
save(face(copper, lambda d: pole_letter(d, "S", SOUTH)), "electromagnet_bottom")
save(face(copper, lambda d: coil_bands(d, COPPER)), "electromagnet_side")
save(emissive(face(copper, lambda d: pole_letter(d, "N", NORTH))), "electromagnet_top_active")
save(emissive(face(copper, lambda d: pole_letter(d, "S", SOUTH))), "electromagnet_bottom_active")
save(emissive(face(copper, lambda d: coil_bands(d, COPPER_HOT))), "electromagnet_side_active")

# ================================================================ REPULSOR COIL
# Copper casing, repelling concentric rings on top, windings on sides.
save(face(copper, lambda d: concentric(d, COPPER, dot=True)), "repulsor_coil_top")
save(face(copper, lambda d: coil_bands(d, COPPER)), "repulsor_coil_side")
save(face(copper, None, inset=False), "repulsor_coil_bottom")
save(emissive(face(copper, lambda d: concentric(d, COPPER_HOT, dot=True))), "repulsor_coil_top_active")
save(emissive(face(copper, lambda d: coil_bands(d, COPPER_HOT))), "repulsor_coil_side_active")

# ================================================================ TRACTOR BEAM
save(face(copper, lambda d: lens(d, SOUTH)), "tractor_beam_front")
save(face(copper, lambda d: coil_bands(d, SOUTH)), "tractor_beam_side")
save(face(copper, None, inset=False), "tractor_beam_back")
save(emissive(face(copper, lambda d: lens(d, SOUTH))), "tractor_beam_front_active")
save(emissive(face(copper, lambda d: coil_bands(d, SOUTH))), "tractor_beam_side_active")

# ================================================================ MAGNETIC EXCAVATOR
save(face(copper, lambda d: drill_crosshair(d, NORTH)), "magnetic_excavator_face")
save(face(copper, lambda d: coil_bands(d, COPPER)), "magnetic_excavator_side")
save(face(copper, None, inset=False), "magnetic_excavator_back")
save(emissive(face(copper, lambda d: drill_crosshair(d, NORTH))), "magnetic_excavator_face_active")

# ================================================================ MAGNETIC ANCHOR
# Andesite casing = structural. Concentric "tether target" on top.
save(face(andesite, lambda d: concentric(d, ACCENT, dot=True)), "magnetic_anchor_top")
save(face(andesite, None, inset=False), "magnetic_anchor_side")
save(emissive(face(andesite, lambda d: concentric(d, ACCENT, dot=True))), "magnetic_anchor_top_active")
save(emissive(face(andesite, None, inset=False)), "magnetic_anchor_side_active")

# ================================================================ MAGNETIC SWITCH
save(face(andesite, lambda d: sensor_dot(d, NORTH)), "magnetic_switch_top")
save(face(andesite, None, inset=False), "magnetic_switch_side")
save(face(andesite, None, inset=False), "magnetic_switch_bottom")

# ================================================================ POLARITY INVERTER
# Brass casing = advanced control. Reversed-polarity swap glyph on top.
save(face(brass, lambda d: polarity_flip(d)), "polarity_inverter_top")
save(face(brass, lambda d: coil_bands(d, ACCENT)), "polarity_inverter_side")

# ================================================================ PERMANENT MAGNET
# Brass casing + horseshoe. North/South faced variants on top.
save(face(brass, lambda d: horseshoe(d, NORTH)), "permanent_magnet_top_north")
save(face(brass, lambda d: horseshoe(d, SOUTH)), "permanent_magnet_top_south")
save(face(brass, lambda d: coil_bands(d, ACCENT)), "permanent_magnet_side")
save(face(brass, None, inset=False), "permanent_magnet_bottom")

# ================================================================ TEMPORARY MAGNET
# Andesite casing (lesser tier) + horseshoe.
save(face(andesite, lambda d: horseshoe(d, NORTH)), "temporary_magnet_top_north")
save(face(andesite, lambda d: horseshoe(d, SOUTH)), "temporary_magnet_top_south")
save(face(andesite, lambda d: coil_bands(d, (150, 150, 158, 255))), "temporary_magnet_side")
save(face(andesite, None, inset=False), "temporary_magnet_bottom")

print("Generated Create-style machine/magnet textures.")
