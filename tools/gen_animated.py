#!/usr/bin/env python3
"""Generate animated active variants of the most visible block faces.

Each output is a 16x64 PNG (4 frames of 16x16) with a sinusoidal brightness
pulse, so the active emitter visibly throbs in-game. Each PNG is paired with a
{name}.png.mcmeta describing 4-frame, frametime-4 looping playback.
"""
from __future__ import annotations

import os
from PIL import Image, ImageEnhance

ASSETS = "/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/block"

# (file_basename, list of brightness multipliers per frame)
FRAMES_4 = [0.85, 1.05, 1.30, 1.05]
TARGETS = [
    "electromagnet_top_active",
    "electromagnet_bottom_active",
    "magnetic_anchor_top_active",
    "repulsor_coil_top_active",
    "tractor_beam_front_active",
    "kinetic_electromagnet_top_active",
    "kinetic_electromagnet_bottom_active",
]


def animate(basename: str):
    src = os.path.join(ASSETS, f"{basename}.png")
    if not os.path.exists(src):
        print(f"  skip {basename} — source not found")
        return
    base = Image.open(src).convert("RGBA")
    if base.size != (16, 16):
        # If we previously generated a non-16x16, resize to 16x16 for stack.
        base = base.resize((16, 16))
    stack = Image.new("RGBA", (16, 16 * len(FRAMES_4)))
    for i, mult in enumerate(FRAMES_4):
        frame = ImageEnhance.Brightness(base).enhance(mult)
        stack.paste(frame, (0, 16 * i))
    stack.save(src)
    # mcmeta sidecar
    mcmeta = src + ".mcmeta"
    with open(mcmeta, "w") as f:
        f.write(
            "{\n"
            "  \"animation\": {\n"
            "    \"frametime\": 4,\n"
            "    \"interpolate\": true\n"
            "  }\n"
            "}\n"
        )
    print(f"  animated {basename}")


for t in TARGETS:
    animate(t)
print("Done.")
