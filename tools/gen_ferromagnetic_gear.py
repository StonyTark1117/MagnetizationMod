"""Ferromagnetic tool + armor textures, recolored from vanilla iron equivalents.

Uses the same blue-tinted half-darkening as the ferromagnetic ingot so the
whole tier reads as a coherent set: iron-like silhouette, slight steely-blue
cast that says 'magnetized'.
"""
from pathlib import Path
import zipfile, sys
try:
    from PIL import Image
except ImportError:
    sys.stderr.write("Pillow not installed. pip install Pillow\n")
    sys.exit(1)

CACHE = Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
ASSETS = Path("src/main/resources/assets/magnetization/textures")


def vanilla(path: str) -> Image.Image:
    with zipfile.ZipFile(CACHE, "r") as z:
        with z.open(f"assets/minecraft/textures/{path}.png") as f:
            return Image.open(f).convert("RGBA")


def recolor(img: Image.Image) -> Image.Image:
    """Same shift as the ferromagnetic ingot: half-magnetite darkening + slight
    blue lift. Iron stays recognizable, with a cool 'magnetized' tint."""
    out = img.copy()
    pixels = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            r = int(r * 0.78)
            g = int(g * 0.80)
            b = min(255, int(b * 0.86) + 6)
            pixels[x, y] = (r, g, b, a)
    return out


tools = ["sword", "pickaxe", "axe", "shovel", "hoe"]
armor_items = ["helmet", "chestplate", "leggings", "boots"]

(ASSETS / "item").mkdir(parents=True, exist_ok=True)
(ASSETS / "models/armor").mkdir(parents=True, exist_ok=True)

for t in tools:
    out = ASSETS / "item" / f"ferromagnetic_{t}.png"
    recolor(vanilla(f"item/iron_{t}")).save(out)
    print(f"wrote {out}")

for a in armor_items:
    out = ASSETS / "item" / f"ferromagnetic_{a}.png"
    recolor(vanilla(f"item/iron_{a}")).save(out)
    print(f"wrote {out}")

# Worn-armor layers
for layer in ("layer_1", "layer_2"):
    out = ASSETS / "models/armor" / f"ferromagnetic_{layer}.png"
    recolor(vanilla(f"models/armor/iron_{layer}")).save(out)
    print(f"wrote {out}")
