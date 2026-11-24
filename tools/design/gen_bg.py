import os, sys
D = sys.argv[1]
HDR = '<?xml version="1.0" encoding="utf-8"?>\n'
def radial(cx, cy, r, s, c, e):
    return f'''    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_radius" />
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}"
                android:gradientRadius="{r}%" android:startColor="{s}" android:centerColor="{c}" android:endColor="{e}" />
        </shape>
    </item>
'''
def bg(name, base, g1, g2, note):
    s, c, e = base
    out = HDR + f'''<!-- Atmosfera: {note} -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_radius" />
            <gradient android:angle="270" android:startColor="{s}" android:centerColor="{c}" android:endColor="{e}" />
        </shape>
    </item>
''' + radial(*g1) + radial(*g2) + '''    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_radius" />
            <gradient android:angle="270" android:startColor="#12FFFFFF" android:centerColor="#00FFFFFF" android:endColor="#14000000" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_radius" />
            <stroke android:width="1dp" android:color="#1CFFFFFF" />
        </shape>
    </item>
</layer-list>
'''
    open(os.path.join(D, f'bg_{name}.xml'), 'w').write(out)

bg('clear_day', ('#FF12306E', '#FF0B2154', '#FF07132F'),
   (0.92, 0.02, 95, '#70FFB860', '#22FF9A4A', '#00FF9A4A'),
   (0.0, 1.0, 85, '#383B7BFF', '#103B7BFF', '#003B7BFF'), 'ensolarado — azul profundo com luz quente')
bg('cloudy', ('#FF2E3E5C', '#FF1F2C47', '#FF121B2F'),
   (0.80, 0.0, 100, '#3DDCE6F5', '#12DCE6F5', '#00DCE6F5'),
   (0.05, 1.0, 80, '#2A6C7FA8', '#0C6C7FA8', '#006C7FA8'), 'nublado — azul acinzentado e luz difusa')
bg('rain', ('#FF11284D', '#FF0A1A37', '#FF050E21'),
   (0.85, 0.0, 95, '#504A8EE8', '#164A8EE8', '#004A8EE8'),
   (0.0, 1.0, 80, '#2E2FA3C7', '#0C2FA3C7', '#002FA3C7'), 'chuva — azul profundo e frio')
bg('storm', ('#FF161C38', '#FF0B0F23', '#FF04060E'),
   (0.78, 0.0, 95, '#527A6CFF', '#167A6CFF', '#007A6CFF'),
   (0.08, 1.0, 75, '#24E0B85C', '#08E0B85C', '#00E0B85C'), 'tempestade — escuro com luz elétrica')
bg('night', ('#FF0B1433', '#FF060C21', '#FF02040D'),
   (0.86, 0.04, 85, '#3A8FB2FF', '#0E8FB2FF', '#008FB2FF'),
   (0.0, 1.0, 80, '#2A5B45B8', '#0A5B45B8', '#005B45B8'), 'noite — quase preto com brilho lunar')
bg('sunset', ('#FF142A60', '#FF2B2C66', '#FF4E3159'),
   (0.95, 1.0, 115, '#D9FF8A4C', '#46FF6A3D', '#00FF6A3D'),
   (0.0, 0.0, 85, '#384A7BE0', '#0E4A7BE0', '#004A7BE0'), 'pôr do sol — azul que se dissolve em laranja')

glass = HDR + '''<!-- Painel de vidro: preenchimento translúcido, brilho superior e contorno fino -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_inner_radius" />
            <solid android:color="@color/glass_fill" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_inner_radius" />
            <gradient android:angle="270" android:startColor="#1AFFFFFF" android:endColor="#00FFFFFF" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/widget_inner_radius" />
            <stroke android:width="1dp" android:color="@color/glass_edge" />
        </shape>
    </item>
</layer-list>
'''
open(os.path.join(D, 'glass_panel.xml'), 'w').write(glass)

btn = HDR + '''<!-- Botão circular de vidro (atualizar) -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:width="30dp" android:height="30dp" android:gravity="center">
        <shape android:shape="oval">
            <solid android:color="#1AFFFFFF" />
            <stroke android:width="1dp" android:color="#22FFFFFF" />
        </shape>
    </item>
</layer-list>
'''
open(os.path.join(D, 'glass_button.xml'), 'w').write(btn)

dot = HDR + '''<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <size android:width="6dp" android:height="6dp" />
    <solid android:color="#FFFFFFFF" />
</shape>
'''
open(os.path.join(D, 'status_dot.xml'), 'w').write(dot)
