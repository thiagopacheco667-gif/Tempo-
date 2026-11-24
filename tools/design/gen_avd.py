import os, sys, random
D = sys.argv[1]
random.seed(7)

def n(v):
    v = round(float(v), 2)
    return str(int(v)) if v == int(v) else f"{v:g}"

def circle(cx, cy, r):
    return f"M{n(cx-r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx+r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx-r)},{n(cy)} Z"

def avd(name, note, w, h, vw, vh, body, targets):
    t = "\n".join(targets)
    out = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector android:width="{w}dp" android:height="{h}dp"
            android:viewportWidth="{vw}" android:viewportHeight="{vh}">
{body}
        </vector>
    </aapt:attr>
{t}
</animated-vector>
'''
    open(os.path.join(D, name + ".xml"), "w").write(out)

def target(name, animators):
    if len(animators) == 1:
        inner = animators[0]
    else:
        inner = '            <set android:ordering="together">\n' + "\n".join(animators) + '\n            </set>'
    return f'''    <target android:name="{name}">
        <aapt:attr name="android:animation">
{inner}
        </aapt:attr>
    </target>'''

def anim(prop, a, b, dur, offset=0, interp="linear", reverse=False, value_type="floatType"):
    rm = ' android:repeatMode="reverse"' if reverse else ""
    return (f'            <objectAnimator android:propertyName="{prop}" android:valueFrom="{n(a)}" android:valueTo="{n(b)}"'
            f' android:valueType="{value_type}" android:duration="{dur}" android:startOffset="{offset}"'
            f' android:repeatCount="infinite"{rm} android:interpolator="@android:interpolator/{interp}" />')

def keyframes(prop, dur, frames):
    k = "\n".join(f'                    <keyframe android:fraction="{f}" android:value="{n(v)}" />' for f, v in frames)
    return f'''            <objectAnimator android:duration="{dur}" android:repeatCount="infinite" android:interpolator="@android:interpolator/linear">
                <propertyValuesHolder android:propertyName="{prop}" android:valueType="floatType">
{k}
                </propertyValuesHolder>
            </objectAnimator>'''

# ---- chuva: estrias finas e inclinadas, translúcidas ----
def rain(name, count, alpha_rng, note, extra_body="", extra_targets=()):
    body, targets = [], []
    for i in range(count):
        x = random.uniform(-10, 230)
        ln = random.uniform(9, 16)
        a = random.uniform(*alpha_rng)
        dur = random.randint(760, 1150)
        off = random.randint(0, 1100)
        body.append(f'''            <group android:name="d{i}" android:translateY="-40">
                <path android:pathData="M{n(x)},0 L{n(x - ln * 0.22)},{n(ln)}" android:strokeColor="#FFCFE4FF" android:strokeAlpha="{n(a)}" android:strokeWidth="0.9" android:strokeLineCap="round" />
            </group>''')
        targets.append(target(f"d{i}", [anim("translateY", -40, 220, dur, off), anim("translateX", 0, -56, dur, off)]))
    avd(name, note, 200, 200, 200, 200, extra_body + "\n".join(body), list(extra_targets) + targets)

rain("amb_rain", 22, (0.16, 0.34), "Chuva ambiente: estrias caindo em diagonal")

flash_body = f'''            <path android:name="flash" android:pathData="M0,0 H200 V200 H0 Z" android:fillAlpha="0">
                <aapt:attr name="android:fillColor">
                    <gradient android:type="radial" android:centerX="150" android:centerY="0" android:gradientRadius="190">
                        <item android:offset="0" android:color="#FFE6E9FF" />
                        <item android:offset="0.5" android:color="#66B7B9FF" />
                        <item android:offset="1" android:color="#00B7B9FF" />
                    </gradient>
                </aapt:attr>
            </path>
'''
flash_target = target("flash", [keyframes("fillAlpha", 9000, [
    (0, 0), (0.62, 0), (0.63, 0.30), (0.645, 0.05), (0.66, 0.22), (0.70, 0), (1, 0)])])
rain("amb_storm", 14, (0.14, 0.28), "Tempestade: chuva e relâmpago sutil entre as nuvens", flash_body, [flash_target])

# ---- noite: estrelas cintilando + estrela cadente rara ----
body, targets = [], []
for i in range(16):
    x = random.uniform(6, 194)
    y = random.uniform(4, 120) if i < 12 else random.uniform(120, 190)
    r = random.uniform(0.55, 1.25)
    body.append(f'            <path android:name="s{i}" android:pathData="{circle(x, y, r)}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.3" />')
    dur = random.randint(1800, 4200)
    targets.append(target(f"s{i}", [anim("fillAlpha", random.uniform(0.08, 0.25), random.uniform(0.6, 0.95), dur, random.randint(0, 2000), "accelerate_decelerate", True)]))
body.append('            <path android:name="meteor" android:pathData="M182,14 L132,44" android:strokeColor="#FFFFFFFF" android:strokeAlpha="0.7" android:strokeWidth="0.9" android:strokeLineCap="round" android:trimPathEnd="0" />')
targets.append(target("meteor", [
    keyframes("trimPathEnd", 14000, [(0, 0), (0.86, 0), (0.9, 1), (1, 1)]),
    keyframes("trimPathStart", 14000, [(0, 0), (0.875, 0), (0.92, 1), (1, 1)]),
]))
avd("amb_stars", "Noite: estrelas cintilantes e uma estrela cadente ocasional", 200, 200, 200, 200, "\n".join(body), targets)

# ---- nublado/nevoeiro: névoa à deriva ----
def mist_blob(name, cx, cy, rx, ry, a):
    d = f"M{n(cx-rx)},{n(cy)} A{n(rx)},{n(ry)} 0 1 1 {n(cx+rx)},{n(cy)} A{n(rx)},{n(ry)} 0 1 1 {n(cx-rx)},{n(cy)} Z"
    return f'''            <group android:name="{name}">
                <path android:pathData="{d}" android:fillAlpha="{a}">
                    <aapt:attr name="android:fillColor">
                        <gradient android:type="radial" android:centerX="{n(cx)}" android:centerY="{n(cy)}" android:gradientRadius="{n(rx)}">
                            <item android:offset="0" android:color="#FFE8EEF8" />
                            <item android:offset="1" android:color="#00E8EEF8" />
                        </gradient>
                    </aapt:attr>
                </path>
            </group>'''
body = [mist_blob("m1", 60, 70, 90, 40, 0.09), mist_blob("m2", 150, 150, 100, 45, 0.07), mist_blob("m3", 170, 40, 70, 30, 0.06)]
targets = [target("m1", [anim("translateX", -18, 26, 16000, 0, "accelerate_decelerate", True)]),
           target("m2", [anim("translateX", 22, -24, 21000, 0, "accelerate_decelerate", True)]),
           target("m3", [anim("translateX", 10, -20, 18000, 0, "accelerate_decelerate", True)])]
avd("amb_mist", "Nublado: névoa luminosa que se desloca lentamente", 200, 200, 200, 200, "\n".join(body), targets)

# ---- halo solar que "respira" atrás do ícone ----
def glow(name, c0, c1, note):
    body = f'''            <group android:name="g" android:pivotX="50" android:pivotY="50">
                <path android:name="halo" android:pathData="{circle(50, 50, 50)}" android:fillAlpha="0.7">
                    <aapt:attr name="android:fillColor">
                        <gradient android:type="radial" android:centerX="50" android:centerY="50" android:gradientRadius="50">
                            <item android:offset="0" android:color="{c0}" />
                            <item android:offset="0.45" android:color="{c1}" />
                            <item android:offset="1" android:color="#00FFFFFF" />
                        </gradient>
                    </aapt:attr>
                </path>
            </group>'''
    targets = [target("g", [anim("scaleX", 0.9, 1.05, 4800, 0, "accelerate_decelerate", True),
                            anim("scaleY", 0.9, 1.05, 4800, 0, "accelerate_decelerate", True)]),
               target("halo", [anim("fillAlpha", 0.45, 0.85, 4800, 0, "accelerate_decelerate", True)])]
    avd(name, note, 100, 100, 100, 100, body, targets)

glow("amb_glow_sun", "#55FFD27A", "#1FFFB04A", "Halo solar quente que pulsa suavemente")
glow("amb_glow_sunset", "#66FF9A5A", "#22FF6A3D", "Halo do entardecer, mais alaranjado")
glow("amb_glow_moon", "#338FB2FF", "#108FB2FF", "Halo lunar frio")

# ---- carregamento: arco elegante girando ----
body = f'''            <group android:name="rot" android:pivotX="24" android:pivotY="24">
                <path android:pathData="{circle(24, 24, 17)}" android:strokeColor="#26FFFFFF" android:strokeWidth="2.6" />
                <path android:name="arc" android:pathData="M24,7 A17,17 0 1 1 23.99,7" android:strokeColor="#FFFFFFFF" android:strokeWidth="2.6"
                    android:strokeLineCap="round" android:trimPathStart="0" android:trimPathEnd="0.3" />
            </group>'''
targets = [target("rot", [anim("rotation", 0, 360, 1100, 0, "linear")])]
avd("avd_loader", "Indicador de carregamento", 48, 48, 48, 48, body, targets)
