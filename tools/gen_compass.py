#!/usr/bin/env python3
"""Field compass icon: gold ring with red N at top, blue S at bottom, accent dial."""
from PIL import Image, ImageDraw

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
# Outer ring (gold)
d.ellipse([1, 1, 14, 14], outline=(255, 216, 72, 255), width=1)
# Face (slate)
d.ellipse([3, 3, 12, 12], fill=(64, 64, 64, 255))
# Dial: red top half pointing north
d.line([(8, 4), (8, 8)], fill=(200, 56, 56, 255), width=2)
# Dial: blue bottom half pointing south
d.line([(8, 8), (8, 11)], fill=(56, 120, 200, 255), width=2)
# Pivot dot
d.rectangle([7, 7, 8, 8], fill=(240, 240, 240, 255))

img.save("/home/braydon/Magnetization-API/src/main/resources/assets/magnetization/textures/item/field_compass.png")
print("OK")
