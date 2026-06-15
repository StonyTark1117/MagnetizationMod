#!/usr/bin/env python3
"""Generate MR Fluid tool textures.

Mirrors the MR Liquid Armor approach: the tinted-water animation (32 frames)
is masked to the vanilla iron-tool silhouette to read as flowing fluid in the
tool shape. A static "hardened" variant recolours the iron tool to the rigid
grey-blue plate palette, swapped in by the magnetization:hardened item property
while the tool is actively used.
"""
import os
from PIL import Image

VAN = "/tmp/van/assets/minecraft/textures/item"
OUT = "src/main/resources/assets/magnetization/textures/item"
WATER = "/tmp/van/assets/minecraft/textures/block/water_still.png"

FLUID_TINT = (95, 95, 105)      # desaturated-water -> fluid palette (R==G, B higher)
HARD_TINT = (175, 175, 193)     # desaturated-iron  -> rigid grey-blue plate palette

TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]


def lum(px):
    r, g, b = px[0], px[1], px[2]
    return int(0.299 * r + 0.587 * g + 0.114 * b)


def tint_desat(img, tint):
    """Desaturate to luminance, then multiply by tint."""
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sp = img.load()
    op = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            L = lum((r, g, b))
            op[x, y] = (L * tint[0] // 255, L * tint[1] // 255, L * tint[2] // 255, a)
    return out


def main():
    water = Image.open(WATER).convert("RGBA")
    frames = water.height // 16
    # Pre-tint each water frame to the fluid palette.
    fluid_frames = []
    for i in range(frames):
        fr = water.crop((0, i * 16, 16, i * 16 + 16))
        fluid_frames.append(tint_desat(fr, FLUID_TINT))

    for tool in TOOLS:
        iron = Image.open(os.path.join(VAN, f"iron_{tool}.png")).convert("RGBA")
        mask = iron.split()[3]  # silhouette alpha

        # Animated fluid strip: fluid frame masked to tool shape.
        strip = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
        mp = mask.load()
        for i, ff in enumerate(fluid_frames):
            cell = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            fp = ff.load()
            cp = cell.load()
            for y in range(16):
                for x in range(16):
                    a = mp[x, y]
                    if a == 0:
                        continue
                    r, g, b, _ = fp[x, y]
                    cp[x, y] = (r, g, b, a)
            strip.paste(cell, (0, i * 16))
        strip.save(os.path.join(OUT, f"mr_fluid_{tool}.png"))
        with open(os.path.join(OUT, f"mr_fluid_{tool}.png.mcmeta"), "w") as f:
            f.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')

        # Static hardened: recoloured iron tool, rigid grey-blue plate.
        hard = tint_desat(iron, HARD_TINT)
        hard.save(os.path.join(OUT, f"mr_fluid_{tool}_hardened.png"))
        print(f"wrote mr_fluid_{tool} (+hardened), {frames} frames")


if __name__ == "__main__":
    main()
