import sys, os
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
names = sys.argv[3:]
imgs = [Image.open(os.path.join(src, n + ".png")) for n in names]
scale = 0.5
imgs = [im.resize((int(im.width*scale), int(im.height*scale)), Image.LANCZOS) for im in imgs]
W = 1400
x = y = 0; rowh = 0; pos = []
for im in imgs:
    if x + im.width > W: x = 0; y += rowh; rowh = 0
    pos.append((x, y)); x += im.width; rowh = max(rowh, im.height)
sheet = Image.new("RGB", (W, y + rowh), (30, 34, 44))
for im, p in zip(imgs, pos): sheet.paste(im, p)
sheet.save(out)
