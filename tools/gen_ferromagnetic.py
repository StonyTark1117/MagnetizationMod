"""Ferromagnetic Ingot texture: a blend of iron + magnetite color schemes.

Iron is bright steel; magnetite is steely-black with slight blue retention.
The ferromagnetic ingot is conceptually iron infused with magnetic properties,
so it should sit visually halfway between the two. We take the vanilla iron
ingot texture and apply ~half the darkening factor that magnetite uses, plus
a faint blue lift to evoke "magnetized iron".
"""

from pathlib import Path
import zipfile, sys

try:
    from PIL import Image
except ImportError:
    sys.stderr.write("Pillow not installed. pip install Pillow\n")
    sys.exit(1)

CACHE = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
ITEM = Path("src/main/resources/assets/magnetization/textures/item")


def vanilla(path: str) -> Image.Image:
    with zipfile.ZipFile(CACHE, "r") as z:
        with z.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(f).convert("RGBA")


def to_ferromagnetic(img: Image.Image) -> Image.Image:
    """Half-magnetite darkening + slight blue tint. Iron stays recognizable
    as iron, but with the slight steel-blue cast that says 'magnetized'."""
    out = img.copy()
    pixels = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            # Darkening factor ~halfway between iron (1.0) and magnetite (~0.55).
            r = int(r * 0.78)
            g = int(g * 0.80)
            # Blue lifts +5% on top of the darkening: hint of cool "magnetized" tint.
            b = min(255, int(b * 0.86) + 6)
            pixels[x, y] = (r, g, b, a)
    return out


to_ferromagnetic(vanilla("item/iron_ingot")).save(f"{ITEM}/ferromagnetic_ingot.png")
print(f"wrote {ITEM}/ferromagnetic_ingot.png")
