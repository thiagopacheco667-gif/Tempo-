"""Otimiza fotografias de cenário para o widget: 1280x720 (16:9), WebP qualidade 80,
gravadas em res/drawable-nodpi (sem reescala por densidade).
Uso: python3 tools/scenes/optimize.py origem.jpg scene_<condicao>_<periodo>"""
import sys, os
from PIL import Image
src, name = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
target = 16 / 9
if w / h > target:            # corta as laterais
    nw = int(h * target); im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
elif w / h < target:          # corta topo/base mantendo mais céu
    nh = int(w / target); top = int((h - nh) * 0.35); im = im.crop((0, top, w, top + nh))
im = im.resize((1280, 720), Image.LANCZOS)
out = os.path.join(os.path.dirname(__file__), "..", "..", "app/src/main/res/drawable-nodpi", name + ".webp")
im.save(out, "WEBP", quality=80, method=6)
print(out, os.path.getsize(out) // 1024, "KB")
