#!/usr/bin/env python3
"""Generate the MR Fluid Golem's coherent animated entity texture.

This deliberately mirrors the worn MR armor rather than adding a renderer
overlay: 16 evenly sampled vanilla water-still frames, tint (95, 95, 105), and
three ticks per frame. Each 16x16 water cell is tiled across the golem's native
128x128 UV map and clipped to the existing hardened texture's alpha silhouette.
The renderer selects one complete frame every three ticks, exactly as the worn
armor layer does. Entity textures do not honor atlas animation metadata.
"""

import os
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ENTITY_DIR = ROOT / "src/main/resources/assets/magnetization/textures/entity"
SOFT = ENTITY_DIR / "mr_fluid_golem.png"
HARDENED = ENTITY_DIR / "mr_fluid_golem_hardened.png"
VANILLA_ROOT = Path(os.environ.get(
    "VANILLA_TEXTURE_ROOT", "/tmp/van/assets/minecraft/textures"))
WATER = VANILLA_ROOT / "block/water_still.png"

FRAME_SIZE = 128
FRAMES = 16
FLUID_TINT = (95, 95, 105)
EYE_PIXELS = ((9, 13), (10, 13), (13, 13), (14, 13),
              (9, 14), (10, 14), (13, 14), (14, 14))


def luminance(pixel):
    return int(0.299 * pixel[0] + 0.587 * pixel[1] + 0.114 * pixel[2])


def tint_desaturated(image):
    source = image.convert("RGBA")
    result = Image.new("RGBA", source.size, (0, 0, 0, 0))
    for y in range(source.height):
        for x in range(source.width):
            red, green, blue, alpha = source.getpixel((x, y))
            if alpha == 0:
                continue
            level = luminance((red, green, blue))
            result.putpixel((x, y), (
                level * FLUID_TINT[0] // 255,
                level * FLUID_TINT[1] // 255,
                level * FLUID_TINT[2] // 255,
                alpha,
            ))
    return result


def fluid_frame(water_frame, silhouette):
    tinted = tint_desaturated(water_frame)
    result = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            alpha = silhouette.getpixel((x, y))
            if alpha == 0:
                continue
            red, green, blue, _ = tinted.getpixel((x % 16, y % 16))
            result.putpixel((x, y), (red, green, blue, alpha))
    for point in EYE_PIXELS:
        if silhouette.getpixel(point):
            result.putpixel(point, (35, 35, 42, 255))
    return result


def main():
    if not WATER.is_file():
        raise SystemExit(
            f"missing {WATER}; set VANILLA_TEXTURE_ROOT to extracted Minecraft textures")

    hardened = Image.open(HARDENED).convert("RGBA")
    if hardened.size != (FRAME_SIZE, FRAME_SIZE):
        raise SystemExit(f"expected {HARDENED} to be {FRAME_SIZE}x{FRAME_SIZE}")
    silhouette = hardened.getchannel("A")

    water = Image.open(WATER).convert("RGBA")
    available_frames = water.height // water.width
    if water.width != 16 or available_frames < FRAMES:
        raise SystemExit(f"unexpected water texture dimensions: {water.size}")
    step = max(1, available_frames // FRAMES)

    first_frame = None
    for index in range(FRAMES):
        water_index = (index * step) % available_frames
        water_frame = water.crop((0, water_index * 16, 16, water_index * 16 + 16))
        frame = fluid_frame(water_frame, silhouette)
        frame.save(ENTITY_DIR / f"mr_fluid_golem_{index}.png")
        if first_frame is None:
            first_frame = frame
    first_frame.save(SOFT)
    print(f"wrote {FRAMES} armor-matched golem frames and {SOFT.name} fallback")


if __name__ == "__main__":
    main()
