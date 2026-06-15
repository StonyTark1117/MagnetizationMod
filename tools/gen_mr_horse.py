#!/usr/bin/env python3
"""Generate MR Fluid horse-armor textures — the equine counterpart of the
player MR armor: animated tinted-water frames masked to the horse-armor
silhouette, a rigid grey-blue hardened variant, a transparent base (so vanilla
draws nothing and our custom layer is the sole renderer), and an item icon.
"""
import os
from PIL import Image

VAN_IRON = "/tmp/van/assets/minecraft/textures/entity/horse/armor/horse_armor_iron.png"
WATER = "/tmp/van/assets/minecraft/textures/block/water_still.png"
ARMOR_OUT = "src/main/resources/assets/magnetization/textures/entity/horse/armor"
ITEM_OUT = "src/main/resources/assets/magnetization/textures/item"
MAGH_ICON = os.path.join(ITEM_OUT, "maghemite_horse_armor.png")

FLUID_TINT = (95, 95, 105)
HARD_TINT = (175, 175, 193)
ICON_TINT = (120, 120, 138)
FRAMES = 16


def lum(px):
    return int(0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2])


def tint_desat(img, tint):
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sp, op = img.load(), out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            L = lum((r, g, b))
            op[x, y] = (L * tint[0] // 255, L * tint[1] // 255, L * tint[2] // 255, a)
    return out


def mask_to(fluid, mask):
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    fp, mp, op = fluid.load(), mask.load(), out.load()
    for y in range(mask.height):
        for x in range(mask.width):
            a = mp[x, y]
            if a == 0:
                continue
            r, g, b, _ = fp[x % fluid.width, y % fluid.height]
            op[x, y] = (r, g, b, a)
    return out


def main():
    iron = Image.open(VAN_IRON).convert("RGBA")
    mask = iron.split()[3]
    water = Image.open(WATER).convert("RGBA")
    wframes = water.height // 16
    step = max(1, wframes // FRAMES)

    for i in range(FRAMES):
        wf = water.crop((0, (i * step % wframes) * 16, 16, (i * step % wframes) * 16 + 16))
        wf = tint_desat(wf, FLUID_TINT)
        frame = mask_to(wf, mask)  # tiles the 16x16 fluid across the 64x64 silhouette
        frame.save(os.path.join(ARMOR_OUT, f"horse_armor_mr_liquid_{i}.png"))

    tint_desat(iron, HARD_TINT).save(os.path.join(ARMOR_OUT, "horse_armor_mr_liquid_hardened.png"))
    Image.new("RGBA", (64, 64), (0, 0, 0, 0)).save(os.path.join(ARMOR_OUT, "horse_armor_mr_liquid.png"))

    # Item icon: recolour the maghemite horse-armor icon to a visible fluid blue-grey.
    icon = Image.open(MAGH_ICON).convert("RGBA")
    tint_desat(icon, ICON_TINT).save(os.path.join(ITEM_OUT, "mr_fluid_horse_armor.png"))
    print(f"wrote {FRAMES} horse frames + hardened + transparent base + item icon")


if __name__ == "__main__":
    main()
