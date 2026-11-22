"""Adds Dishcover's small see-through "AI" mark to the bottom-right corner of a generated dish photo.

    python3 ai_mark.py in.png out.jpg
"""
import sys
from PIL import Image, ImageDraw, ImageFont

src, dst = sys.argv[1], sys.argv[2]
img = Image.open(src).convert("RGB").resize((800, 800), Image.LANCZOS)
layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
d = ImageDraw.Draw(layer)
size = img.width // 22
try:
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)
except OSError:
    font = ImageFont.load_default()
pad = size // 3
box = d.textbbox((0, 0), "AI", font=font)
w, h = box[2] - box[0] + pad * 2, box[3] - box[1] + pad * 2
x, y = img.width - w - size // 2, img.height - h - size // 2
d.rounded_rectangle((x, y, x + w, y + h), radius=h // 3, fill=(0, 0, 0, 70))
d.text((x + pad - box[0], y + pad - box[1]), "AI", font=font, fill=(255, 255, 255, 150))
Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB").save(dst, quality=88)
