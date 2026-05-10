#!/usr/bin/env python3
"""8x8 white-fading-circle particle sprite. Tinted at runtime by MagParticleFactories."""
from PIL import Image, ImageDraw

img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
# Soft round dot — render-tint will color this red or blue.
for y in range(8):
    for x in range(8):
        dx = x - 3.5
        dy = y - 3.5
        r = (dx * dx + dy * dy) ** 0.5
        if r > 3.5:
            continue
        a = int(255 * max(0.0, 1.0 - r / 3.5))
        img.putpixel((x, y), (255, 255, 255, a))

img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/particle/mag_field.png")
print("Generated particle texture.")
