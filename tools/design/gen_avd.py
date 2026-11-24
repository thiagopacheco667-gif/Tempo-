"""Camadas atmosféricas ESTÁTICAS (sem animação contínua, para não gastar bateria)
e o único AnimatedVectorDrawable do widget: o indicador de carregamento, visível só
durante uma atualização."""
import os, sys, random
D = sys.argv[1]
random.seed(7)

def n(v):
    v = round(float(v), 2)
    return str(int(v)) if v == int(v) else f"{v:g}"

def circle(cx, cy, r):
    return f"M{n(cx-r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx+r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx-r)},{n(cy)} Z"

def vector(name, note, body, vw=200, vh=200):
    out = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="{vw}dp" android:height="{vh}dp"
    android:viewportWidth="{vw}" android:viewportHeight="{vh}">
{body}
</vector>
'''
    open(os.path.join(D, name + ".xml"), "w").write(out)

def streaks(count, alpha_rng, y_rng=(0, 200)):
    out = []
    for _ in range(count):
        x = random.uniform(-10, 215)
        y = random.uniform(*y_rng)
        ln = random.uniform(8, 15)
        a = random.uniform(*alpha_rng)
        out.append(f'    <path android:pathData="M{n(x)},{n(y)} L{n(x - ln * 0.22)},{n(y + ln)}" android:strokeColor="#FFCFE4FF" android:strokeAlpha="{n(a)}" android:strokeWidth="0.8" android:strokeLineCap="round" />')
    return "\n".join(out)

vector("deco_rain", "Chuva: estrias finas e translúcidas", streaks(26, (0.08, 0.2)))

flash = '''    <path android:pathData="M0,0 H200 V200 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="160" android:centerY="0" android:gradientRadius="150">
                <item android:offset="0" android:color="#2EC9C6FF" />
                <item android:offset="0.6" android:color="#0A9C98FF" />
                <item android:offset="1" android:color="#009C98FF" />
            </gradient>
        </aapt:attr>
    </path>'''
vector("deco_storm", "Tempestade: claridade elétrica entre nuvens e chuva", flash + "\n" + streaks(16, (0.07, 0.16)))

stars = []
for i in range(18):
    x = random.uniform(6, 194)
    y = random.uniform(4, 110) if i < 14 else random.uniform(110, 190)
    r = random.uniform(0.5, 1.15)
    a = random.uniform(0.18, 0.7)
    stars.append(f'    <path android:pathData="{circle(x, y, r)}" android:fillColor="#FFFFFFFF" android:fillAlpha="{n(a)}" />')
vector("deco_stars", "Noite: campo de estrelas discreto", "\n".join(stars))

def blob(cx, cy, rx, ry, a):
    d = f"M{n(cx-rx)},{n(cy)} A{n(rx)},{n(ry)} 0 1 1 {n(cx+rx)},{n(cy)} A{n(rx)},{n(ry)} 0 1 1 {n(cx-rx)},{n(cy)} Z"
    return f'''    <path android:pathData="{d}" android:fillAlpha="{a}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{n(cx)}" android:centerY="{n(cy)}" android:gradientRadius="{n(rx)}">
                <item android:offset="0" android:color="#FFE8EEF8" />
                <item android:offset="1" android:color="#00E8EEF8" />
            </gradient>
        </aapt:attr>
    </path>'''
vector("deco_mist", "Nublado: névoa luminosa difusa",
       "\n".join([blob(60, 70, 90, 40, 0.08), blob(150, 150, 100, 45, 0.06), blob(170, 40, 70, 30, 0.05)]))

sun = f'''    <path android:pathData="M0,0 H200 V200 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="180" android:centerY="10" android:gradientRadius="120">
                <item android:offset="0" android:color="#33FFD38A" />
                <item android:offset="0.5" android:color="#0FFFB45C" />
                <item android:offset="1" android:color="#00FFB45C" />
            </gradient>
        </aapt:attr>
    </path>'''
vector("deco_sun", "Sol: raio de luz quente no canto superior", sun)

# ---- carregamento (único elemento animado; aparece só durante a atualização) ----
loader = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Indicador de carregamento -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
            <group android:name="rot" android:pivotX="24" android:pivotY="24">
                <path android:pathData="{circle(24, 24, 17)}" android:strokeColor="#26FFFFFF" android:strokeWidth="2.6" />
                <path android:pathData="M24,7 A17,17 0 1 1 23.99,7" android:strokeColor="#FFFFFFFF" android:strokeWidth="2.6"
                    android:strokeLineCap="round" android:trimPathStart="0" android:trimPathEnd="0.3" />
            </group>
        </vector>
    </aapt:attr>
    <target android:name="rot">
        <aapt:attr name="android:animation">
            <objectAnimator android:propertyName="rotation" android:valueFrom="0" android:valueTo="360"
                android:valueType="floatType" android:duration="1100" android:repeatCount="infinite"
                android:interpolator="@android:interpolator/linear" />
        </aapt:attr>
    </target>
</animated-vector>
'''
open(os.path.join(D, "avd_loader.xml"), "w").write(loader)
