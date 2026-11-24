import os, sys
D = sys.argv[1]

def n(v):
    v = round(float(v), 2)
    return str(int(v)) if v == int(v) else f"{v:g}"

def circle(cx, cy, r):
    return f"M{n(cx-r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx+r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx-r)},{n(cy)} Z"

CLOUD = ("M30,50 H70 A10,10 0 0 1 70,70 H30 A10,10 0 0 1 30,50 Z "
         + circle(38, 48, 14) + " " + circle(56, 40, 18) + " " + circle(68, 50, 12))

def lin(x1, y1, x2, y2, stops):
    items = "\n".join(f'                    <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return f'''            <aapt:attr name="android:fillColor">
                <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">
{items}
                </gradient>
            </aapt:attr>'''

def rad(cx, cy, r, stops, attr="fillColor"):
    items = "\n".join(f'                    <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return f'''            <aapt:attr name="android:{attr}">
                <gradient android:type="radial" android:centerX="{n(cx)}" android:centerY="{n(cy)}" android:gradientRadius="{n(r)}">
{items}
                </gradient>
            </aapt:attr>'''

def path(d, fill_xml=None, color=None, alpha=None, extra=""):
    a = f' android:fillAlpha="{alpha}"' if alpha is not None else ""
    if fill_xml:
        return f'''        <path android:pathData="{d}"{a}{extra}>
{fill_xml}
        </path>'''
    return f'        <path android:pathData="{d}" android:fillColor="{color}"{a}{extra} />'

def group(content, tx=0, ty=0, s=1.0, rot=0, px=0, py=0):
    return f'''    <group android:translateX="{tx}" android:translateY="{ty}" android:scaleX="{s}" android:scaleY="{s}" android:rotation="{rot}" android:pivotX="{px}" android:pivotY="{py}">
{content}
    </group>'''

def vector(name, body, note):
    out = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="96" android:viewportHeight="96">
{body}
</vector>
'''
    open(os.path.join(D, name + ".xml"), "w").write(out)

# ---- building blocks (coordenadas no espaço 96x96) ----
def sun(cx, cy, r, glow=True):
    parts = []
    if glow:
        parts.append(path(circle(cx, cy, r * 1.9), rad(cx, cy, r * 1.9,
            [(0, "#66FFC24D"), (0.45, "#26FFA63D"), (1, "#00FF9A2E")])))
    # disco com volume: luz no canto superior esquerdo
    parts.append(path(circle(cx, cy, r), rad(cx - r * 0.35, cy - r * 0.4, r * 1.45,
        [(0, "#FFFFF6C9"), (0.35, "#FFFFD25A"), (0.8, "#FFFF9F2A"), (1, "#FFF2801C")])))
    # aro interno sutil (borda)
    parts.append(f'        <path android:pathData="{circle(cx, cy, r - 0.6)}" android:strokeColor="#33FFFFFF" android:strokeWidth="1.2" />')
    # brilho especular
    parts.append(path(circle(cx - r * 0.38, cy - r * 0.42, r * 0.28), rad(cx - r * 0.38, cy - r * 0.42, r * 0.28,
        [(0, "#B3FFFFFF"), (1, "#00FFFFFF")])))
    return "\n".join(parts)

def moon(cx, cy, r, glow=True):
    parts = []
    if glow:
        parts.append(path(circle(cx, cy, r * 1.8), rad(cx, cy, r * 1.8,
            [(0, "#4D9DBBFF"), (0.5, "#149DBBFF"), (1, "#009DBBFF")])))
    parts.append(path(circle(cx, cy, r), rad(cx - r * 0.4, cy - r * 0.45, r * 1.6,
        [(0, "#FFFFFFFF"), (0.45, "#FFE4EBFF"), (1, "#FF9EB0E0")])))
    # crateras
    for (dx, dy, rr, a) in [(0.28, -0.12, 0.22, 0.22), (-0.22, 0.32, 0.16, 0.18), (0.18, 0.45, 0.1, 0.16), (-0.35, -0.25, 0.09, 0.14)]:
        parts.append(path(circle(round(cx + dx * r, 2), round(cy + dy * r, 2), round(rr * r, 2)), color="#7F8FB8", alpha=a))
    parts.append(f'        <path android:pathData="{circle(cx, cy, r - 0.6)}" android:strokeColor="#40FFFFFF" android:strokeWidth="1" />')
    return "\n".join(parts)

def sparkle(cx, cy, s, a=0.9):
    d = (f"M{cx},{cy-s} Q{cx+s*0.18},{cy-s*0.18} {cx+s},{cy} Q{cx+s*0.18},{cy+s*0.18} {cx},{cy+s} "
         f"Q{cx-s*0.18},{cy+s*0.18} {cx-s},{cy} Q{cx-s*0.18},{cy-s*0.18} {cx},{cy-s} Z")
    return path(d, color="#FFFFFFFF", alpha=a)

def cloud(tone="light", tx=0, ty=0, s=1.0):
    tones = {
        "light": [(0, "#FFFFFFFF"), (0.55, "#FFEFF4FD"), (1, "#FFB9C8E8")],
        "mid":   [(0, "#FFF3F6FC"), (0.6, "#FFCED8EE"), (1, "#FF94A6CF")],
        "rain":  [(0, "#FFE6EDF9"), (0.6, "#FFB3C1E0"), (1, "#FF7486B2")],
        "storm": [(0, "#FFC9D1E6"), (0.55, "#FF8B97BA"), (1, "#FF4E5A80")],
        "back":  [(0, "#FFCAD5EC"), (1, "#FF8193BF")],
    }[tone]
    body = "\n".join([
        # sombra projetada (profundidade)
        path(CLOUD, color="#0A1A45", alpha=0.28, extra=' android:fillType="nonZero"').replace(CLOUD, CLOUD),
        path(CLOUD, lin(50, 22, 50, 70, tones)),
        # brilho no topo da nuvem
        path(circle(52, 33, 9), rad(52, 33, 9, [(0, "#99FFFFFF"), (1, "#00FFFFFF")])),
        # leve sombreamento inferior
        path("M24,62 H76 A8,8 0 0 1 70,70 H30 A8,8 0 0 1 24,62 Z", color="#24406B", alpha=0.16),
    ])
    # a primeira (sombra) deve ficar deslocada para baixo
    lines = body.split("\n", 1)
    shadow = group(lines[0], 0, 3.5)
    return group(shadow + "\n" + lines[1], tx, ty, s, 0, 0, 0)

def drop(x, y, s=1.0, rot=14, light=False):
    d = "M0,-8 C2.6,-3.6 5.2,-0.4 5.2,3.2 A5.2,5.2 0 0 1 -5.2,3.2 C-5.2,-0.4 -2.6,-3.6 0,-8 Z"
    stops = [(0, "#FFBFE8FF"), (0.5, "#FF6FB8FF"), (1, "#FF2F6FE8")] if not light else [(0, "#FFE0F3FF"), (1, "#FF86C2FF")]
    inner = path(d, lin(0, -8, 0, 8.4, stops)) + "\n" + path("M-2.2,1.2 A1.6,2.4 0 0 1 -1,-2.4 L-0.4,-1.6 A1.6,2.4 0 0 0 -1.2,1.6 Z", color="#FFFFFF", alpha=0.65)
    return group(inner, x, y, s, rot, 0, 0)

def bolt(tx, ty, s=1.0):
    d = "M52,52 L40,72 H50 L45,90 L64,64 H53 L60,52 Z"
    g = path(d, lin(50, 52, 52, 90, [(0, "#FFFFF7B8"), (0.5, "#FFFFD23C"), (1, "#FFFF9E1B")]))
    glow = path(circle(52, 70, 18), rad(52, 70, 18, [(0, "#55FFD23C"), (1, "#00FFD23C")]))
    return group(glow + "\n" + g, tx, ty, s)

def flake(x, y, s=1.0):
    arms = " ".join([f"M0,-5 L0,5", "M-4.33,-2.5 L4.33,2.5", "M-4.33,2.5 L4.33,-2.5"])
    inner = f'        <path android:pathData="{arms}" android:strokeColor="#FFF2F7FF" android:strokeWidth="1.6" android:strokeLineCap="round" />\n' + path(circle(0, 0, 1.4), color="#FFFFFFFF")
    return group(inner, x, y, s)

def fog_bar(y, x1, x2, a):
    return f'        <path android:pathData="M{x1},{y} H{x2}" android:strokeColor="#FFDCE5F5" android:strokeAlpha="{a}" android:strokeWidth="4.5" android:strokeLineCap="round" />'

# ---- ícones ----
vector("wx_clear_day", sun(48, 48, 22), "Ensolarado: disco solar com volume e halo")
vector("wx_clear_night", moon(46, 48, 21) + "\n" + sparkle(76, 24, 5) + "\n" + sparkle(20, 22, 3.2, 0.7) + "\n" + sparkle(78, 66, 2.6, 0.6), "Noite limpa: lua iluminada com estrelas")
vector("wx_partly_day", sun(36, 34, 17) + "\n" + cloud("light", 14, 18, 0.86), "Parcialmente nublado (dia)")
vector("wx_partly_night", moon(36, 33, 15) + "\n" + sparkle(70, 16, 3.6, 0.8) + "\n" + cloud("mid", 14, 18, 0.86), "Parcialmente nublado (noite)")
vector("wx_cloudy", cloud("back", 26, 2, 0.7) + "\n" + cloud("light", 2, 12, 0.9), "Nublado: duas camadas de nuvem")
vector("wx_fog", cloud("mid", 4, -2, 0.9) + "\n" + fog_bar(72, 22, 74, 0.85) + "\n" + fog_bar(82, 30, 66, 0.55), "Nevoeiro")
vector("wx_drizzle", cloud("rain", 2, -6, 0.92) + "\n" + drop(38, 76, 0.7, light=True) + "\n" + drop(54, 80, 0.7, light=True) + "\n" + drop(68, 74, 0.6, light=True), "Garoa")
vector("wx_rain", cloud("rain", 2, -8, 0.92) + "\n" + drop(34, 74, 1.0) + "\n" + drop(51, 80, 1.0) + "\n" + drop(67, 73, 0.9), "Chuva")
vector("wx_snow", cloud("mid", 2, -8, 0.92) + "\n" + flake(34, 76, 1.1) + "\n" + flake(52, 82, 1.0) + "\n" + flake(68, 74, 0.9), "Neve")
vector("wx_storm", cloud("storm", 2, -10, 0.92) + "\n" + bolt(-2, -6, 1.0) + "\n" + drop(30, 74, 0.8) + "\n" + drop(72, 72, 0.8), "Trovoadas")

# ---- glifos de linha (24x24) ----
def glyph(name, paths, note):
    body = "\n".join(f'    <path android:pathData="{p}" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" />' for p in paths)
    out = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
{body}
</vector>
'''
    open(os.path.join(D, name + ".xml"), "w").write(out)

glyph("gl_rain", ["M8,4 L6.5,8", "M13,4 L11.5,8", "M18,4 L16.5,8", "M9,12 L7,17", "M14.5,12 L12.5,17", "M11.5,19 L10.8,20.6"], "Probabilidade de chuva")
glyph("gl_humidity", ["M12,3.5 C15.5,8 18,11 18,14.5 A6,6 0 0 1 6,14.5 C6,11 8.5,8 12,3.5 Z", "M9.5,15.2 A2.6,2.6 0 0 0 12.2,17.6"], "Umidade")
glyph("gl_wind", ["M3,9 H14 A3,3 0 1 0 11,6", "M3,13 H18.5 A2.5,2.5 0 1 1 16,15.5", "M3,17 H10"], "Vento")
glyph("gl_uv", ["M12,8 A4,4 0 1 1 11.99,8 Z", "M12,2.5 V4.2", "M12,19.8 V21.5", "M2.5,12 H4.2", "M19.8,12 H21.5", "M5.3,5.3 L6.5,6.5", "M17.5,17.5 L18.7,18.7", "M5.3,18.7 L6.5,17.5", "M17.5,6.5 L18.7,5.3"], "Índice UV")
glyph("gl_pin", ["M12,21 C12,21 5.5,14.6 5.5,9.8 A6.5,6.5 0 0 1 18.5,9.8 C18.5,14.6 12,21 12,21 Z", "M12,7.6 A2.2,2.2 0 1 1 11.99,7.6 Z"], "Localização")
glyph("gl_refresh", ["M19,12 A7,7 0 1 1 16.6,6.7", "M17.2,3.2 L17.2,7.2 L13.2,7.2"], "Atualizar")
