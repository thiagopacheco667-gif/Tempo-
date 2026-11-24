"""Gera os layouts RemoteViews do widget — Premium Atmospheric Weather 2.0.

Cada tamanho tem composição própria:
  compacta  — temperatura em destaque, local, condição, máx/mín e ícone
  média     — clima atual à esquerda, resumo de 3 dias à direita, faixa de indicadores
  expandida — cabeçalho, clima atual, alerta INMET, indicadores e previsão de 5 dias

Somente classes permitidas em RemoteViews (FrameLayout, LinearLayout, TextView, ImageView,
ProgressBar). Uso: python3 gen_layouts.py app/src/main/res/layout
"""
import sys, os
D = sys.argv[1]
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def shell(name, content, note):
    out = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} -->
<FrameLayout {NS}
    android:id="@android:id/background"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:animateLayoutChanges="true"
    android:clipToOutline="true"
    android:background="@drawable/bg_night">

    <!-- Duas camadas de atmosfera: a troca entre elas gera um crossfade suave -->
    <ImageView android:id="@+id/bg_a" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="centerCrop" android:importantForAccessibility="no" android:src="@drawable/bg_night" />
    <ImageView android:id="@+id/bg_b" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="centerCrop" android:importantForAccessibility="no" android:visibility="invisible" />
    <!-- Véu escuro translúcido sobre a fotografia (legibilidade) -->
    <ImageView android:id="@+id/scrim" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitXY" android:src="@drawable/scrim_photo" android:importantForAccessibility="no"
        android:visibility="gone" />
    <!-- Detalhe atmosférico estático (estrelas, chuva, névoa, luz) -->
    <ImageView android:id="@+id/deco" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitXY" android:importantForAccessibility="no" />

{content}
</FrameLayout>
'''
    open(os.path.join(D, name + ".xml"), "w").write(out)


def temp_pair(size, extra=""):
    return f'''<FrameLayout android:id="@+id/temp_box" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:animateLayoutChanges="true"{extra}>
                <TextView android:id="@+id/temp_a" style="@style/WText.Temp" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:textSize="{size}sp" />
                <TextView android:id="@+id/temp_b" style="@style/WText.Temp" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:textSize="{size}sp" android:visibility="invisible" />
            </FrameLayout>'''


def icon_box(size, extra=""):
    return f'''<FrameLayout android:id="@+id/icon_box" android:layout_width="{size}dp" android:layout_height="{size}dp"
                android:animateLayoutChanges="true"{extra}>
                <ImageView android:id="@+id/icon_a" android:layout_width="match_parent" android:layout_height="match_parent"
                    android:contentDescription="@string/cd_condition_icon" android:src="@drawable/wx_cloudy" />
                <ImageView android:id="@+id/icon_b" android:layout_width="match_parent" android:layout_height="match_parent"
                    android:contentDescription="@string/cd_condition_icon" android:visibility="invisible" />
            </FrameLayout>'''


def refresh_button():
    return '''<FrameLayout android:layout_width="30dp" android:layout_height="30dp" android:animateLayoutChanges="true">
                <ImageView android:id="@+id/refresh" android:layout_width="match_parent" android:layout_height="match_parent"
                    android:background="@drawable/glass_button" android:padding="8dp" android:src="@drawable/gl_refresh"
                    android:alpha="0.9" android:contentDescription="@string/cd_refresh" />
                <ProgressBar android:id="@+id/loading" style="@style/WLoader" android:layout_width="16dp"
                    android:layout_height="16dp" android:layout_gravity="center" />
            </FrameLayout>'''


def status(extra=""):
    return f'''<ImageView android:id="@+id/status_dot" android:layout_width="5dp" android:layout_height="5dp"
                android:layout_marginEnd="5dp" android:src="@drawable/status_dot" android:importantForAccessibility="no" />
            <TextView android:id="@+id/updated" style="@style/WText.Caption" android:layout_width="wrap_content"
                android:layout_height="wrap_content"{extra} />'''


def alert_chip():
    return '''<LinearLayout android:id="@+id/alert_chip" android:layout_width="wrap_content" android:layout_height="22dp"
                    android:layout_marginStart="4dp" android:layout_marginEnd="8dp" android:background="@drawable/chip"
                    android:gravity="center_vertical" android:orientation="horizontal" android:paddingStart="8dp"
                    android:paddingEnd="8dp" android:visibility="gone">
                    <ImageView android:id="@+id/alert_icon" android:layout_width="11dp" android:layout_height="11dp"
                        android:src="@drawable/gl_alert" android:importantForAccessibility="no" />
                    <TextView android:id="@+id/alert_text" style="@style/WText.Chip" android:layout_width="wrap_content"
                        android:layout_height="wrap_content" android:layout_marginStart="5dp" android:layout_marginEnd="1dp" />
                </LinearLayout>'''


def header(with_status=True, height=30):
    st = f'''
            {status(' android:layout_marginEnd="8dp"')}''' if with_status else ""
    return f'''<LinearLayout android:layout_width="match_parent" android:layout_height="{height}dp"
            android:gravity="center_vertical" android:orientation="horizontal">
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                android:gravity="center_vertical" android:orientation="horizontal">
                <ImageView android:layout_width="13dp" android:layout_height="13dp" android:layout_marginEnd="5dp"
                    android:alpha="0.9" android:src="@drawable/gl_pin" android:importantForAccessibility="no" />
                <TextView android:id="@+id/city" style="@style/WText.City" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:text="@string/app_name" />
                <TextView android:id="@+id/region" style="@style/WText.Region" android:layout_width="0dp"
                    android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="6dp"
                    android:layout_marginEnd="6dp" />
            </LinearLayout>
            {alert_chip()}{st}
            {refresh_button()}
        </LinearLayout>'''


def details(cond_size, gap):
    return f'''<LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                android:layout_marginStart="{gap}dp" android:orientation="vertical">
                <TextView android:id="@+id/condition" style="@style/WText.Condition" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:textSize="{cond_size}sp" />
                <TextView android:id="@+id/hilo" style="@style/WText.HiLo" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="4dp" />
                <TextView android:id="@+id/feels" style="@style/WText.Detail" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="3dp" />
            </LinearLayout>'''


def hairline():
    return '''<ImageView android:layout_width="1dp" android:layout_height="22dp" android:layout_gravity="center_vertical"
                    android:src="@drawable/hairline_v" android:scaleType="fitXY" android:importantForAccessibility="no" />'''


METRICS = [("gl_rain", "cd_rain"), ("gl_humidity", "cd_humidity"), ("gl_wind", "cd_wind"), ("gl_uv", "cd_uv")]


def metrics(two_line, top, vpad):
    cells = []
    for i, (glyph, cd) in enumerate(METRICS):
        label = f'''
                    <TextView android:id="@+id/m{i}_label" style="@style/WText.MetricLabel" android:layout_width="wrap_content"
                        android:layout_height="wrap_content" android:layout_marginTop="3dp" />''' if two_line else ""
        cells.append(f'''                <LinearLayout android:id="@+id/m{i}" android:layout_width="0dp" android:layout_height="wrap_content"
                    android:layout_weight="1" android:gravity="center" android:orientation="vertical"
                    android:contentDescription="@string/{cd}">
                    <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:gravity="center_vertical" android:orientation="horizontal">
                        <ImageView android:id="@+id/m{i}_icon" android:layout_width="13dp" android:layout_height="13dp"
                            android:layout_marginEnd="4dp" android:src="@drawable/{glyph}" android:importantForAccessibility="no" />
                        <TextView android:id="@+id/m{i}_value" style="@style/WText.MetricValue" android:layout_width="wrap_content"
                            android:layout_height="wrap_content" />
                    </LinearLayout>{label}
                </LinearLayout>''')
    body = ("\n" + hairline() + "\n").join(cells)
    return f'''<LinearLayout android:id="@+id/metrics" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="{top}dp" android:background="@drawable/glass_soft" android:gravity="center_vertical"
            android:orientation="horizontal" android:paddingTop="{vpad}dp" android:paddingBottom="{vpad}dp">
{body}
        </LinearLayout>'''


def forecast(top):
    cols = []
    for i in range(5):
        cols.append(f'''            <LinearLayout android:id="@+id/d{i}" android:layout_width="0dp" android:layout_height="wrap_content"
                android:layout_weight="1" android:gravity="center_horizontal" android:orientation="vertical">
                <TextView android:id="@+id/d{i}_name" style="@style/WText.Day" android:layout_width="match_parent"
                    android:layout_height="wrap_content" />
                <ImageView android:id="@+id/d{i}_icon" android:layout_width="26dp" android:layout_height="26dp"
                    android:layout_marginTop="2dp" android:importantForAccessibility="no" />
                <TextView android:id="@+id/d{i}_max" style="@style/WText.DayMax" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="2dp" />
                <TextView android:id="@+id/d{i}_min" style="@style/WText.DayMin" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="1dp" />
            </LinearLayout>''')
    return f'''<LinearLayout android:id="@+id/forecast_title" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="12dp" android:gravity="center_vertical" android:orientation="horizontal"
            android:paddingStart="4dp" android:paddingEnd="4dp" android:visibility="gone">
            <TextView style="@style/WText.Section" android:layout_width="0dp" android:layout_height="wrap_content"
                android:layout_weight="1" android:text="@string/next_days" />
            <ImageView android:id="@+id/aqi_dot" android:layout_width="6dp" android:layout_height="6dp"
                android:layout_marginEnd="5dp" android:src="@drawable/status_dot" android:importantForAccessibility="no" />
            <TextView android:id="@+id/aqi_text" style="@style/WText.Caption" android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        </LinearLayout>
        <LinearLayout android:id="@+id/forecast" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="{top}dp" android:background="@drawable/glass_soft" android:orientation="horizontal"
            android:paddingTop="7dp" android:paddingBottom="7dp" android:paddingStart="2dp" android:paddingEnd="2dp">
''' + "\n".join(cols) + '''
        </LinearLayout>'''


def alert_banner():
    return '''<LinearLayout android:id="@+id/alert_banner" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="6dp" android:background="@drawable/chip_banner" android:gravity="center_vertical"
            android:orientation="horizontal" android:paddingStart="10dp" android:paddingEnd="10dp"
            android:paddingTop="6dp" android:paddingBottom="6dp" android:visibility="gone">
            <ImageView android:id="@+id/banner_icon" android:layout_width="13dp" android:layout_height="13dp"
                android:layout_marginEnd="7dp" android:src="@drawable/gl_alert" android:importantForAccessibility="no" />
            <TextView android:id="@+id/banner_text" style="@style/WText.Chip" android:layout_width="0dp"
                android:layout_height="wrap_content" android:layout_weight="1" android:textSize="12sp" android:maxLines="2" />
        </LinearLayout>'''


hint = '''<TextView android:id="@+id/hint" style="@style/WText.Detail" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="8dp" android:gravity="center"
            android:maxLines="2" android:visibility="gone" />'''

# ---------------- compacta (2x2) ----------------
compact = f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:padding="14dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:id="@+id/status_dot" android:layout_width="5dp" android:layout_height="5dp"
                android:layout_marginEnd="6dp" android:src="@drawable/status_dot" android:importantForAccessibility="no" />
            <TextView android:id="@+id/city" style="@style/WText.City" android:layout_width="0dp"
                android:layout_height="wrap_content" android:layout_weight="1" android:textSize="13.5sp"
                android:text="@string/app_name" />
            <ImageView android:id="@+id/alert_icon" android:layout_width="13dp" android:layout_height="13dp"
                android:layout_marginStart="4dp" android:src="@drawable/gl_alert" android:visibility="gone"
                android:contentDescription="@string/cd_alert" />
        </LinearLayout>
        <FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:gravity="bottom" android:orientation="horizontal">
            {temp_pair(46, ' android:layout_marginStart="-2dp"')}
            <FrameLayout android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="1" />
            {icon_box(44, ' android:layout_marginBottom="6dp"')}
        </LinearLayout>
        <TextView android:id="@+id/condition" style="@style/WText.Condition" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="2dp" android:textSize="12.5sp" />
        <TextView android:id="@+id/hilo" style="@style/WText.HiLo" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="3dp" android:textSize="11.5sp" />
    </LinearLayout>'''
shell("widget_compact", compact, "Compacta: temperatura em destaque, local, condição, máx/mín e ícone")

# ---------------- média (4x2) ----------------
rows = []
for i in range(3):
    rows.append(f'''                <LinearLayout android:id="@+id/r{i}" android:layout_width="match_parent" android:layout_height="0dp"
                    android:layout_weight="1" android:gravity="center_vertical" android:orientation="horizontal">
                    <TextView android:id="@+id/r{i}_name" style="@style/WText.RowDay" android:layout_width="31dp"
                        android:layout_height="wrap_content" />
                    <ImageView android:id="@+id/r{i}_icon" android:layout_width="18dp" android:layout_height="18dp"
                        android:importantForAccessibility="no" />
                    <TextView android:id="@+id/r{i}_max" style="@style/WText.RowMax" android:layout_width="0dp"
                        android:layout_height="wrap_content" android:layout_weight="1" />
                    <TextView android:id="@+id/r{i}_min" style="@style/WText.RowMin" android:layout_width="29dp"
                        android:layout_height="wrap_content" />
                </LinearLayout>''')
medium = f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="14dp"
        android:paddingTop="8dp" android:paddingBottom="10dp">
        {header(height=28)}
        <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
            android:gravity="center_vertical" android:orientation="horizontal">
            <!-- Temperatura e, ao lado, ícone + condição + máx/mín: pouca altura, resistente a fontes grandes -->
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1.35"
                android:gravity="center_vertical" android:orientation="horizontal">
                {temp_pair(46, ' android:layout_marginStart="-2dp"')}
                <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                    android:layout_marginStart="8dp" android:orientation="vertical">
                    {icon_box(26)}
                    <TextView android:id="@+id/condition" style="@style/WText.Condition" android:layout_width="match_parent"
                        android:layout_height="wrap_content" android:layout_marginTop="2dp" android:textSize="12.5sp" />
                    <TextView android:id="@+id/hilo" style="@style/WText.HiLo" android:layout_width="match_parent"
                        android:layout_height="wrap_content" android:layout_marginTop="2dp" android:textSize="12sp" />
                </LinearLayout>
            </LinearLayout>
            <ImageView android:id="@+id/divider" android:layout_width="1dp" android:layout_height="58dp"
                android:layout_marginStart="10dp" android:layout_marginEnd="12dp" android:src="@drawable/hairline_v"
                android:scaleType="fitXY" android:importantForAccessibility="no" />
            <LinearLayout android:id="@+id/mini_forecast" android:layout_width="0dp" android:layout_height="66dp"
                android:layout_weight="1" android:orientation="vertical">
{chr(10).join(rows)}
            </LinearLayout>
        </LinearLayout>
        {metrics(False, 4, 6)}
        {hint}
    </LinearLayout>'''
shell("widget_medium", medium, "Média: clima atual + resumo de 3 dias + indicadores")

# ---------------- expandida (4x3+) ----------------
expanded = f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="14dp"
        android:paddingTop="10dp" android:paddingBottom="14dp">
        {header()}
        <LinearLayout android:id="@+id/hero" android:layout_width="match_parent" android:layout_height="0dp"
            android:layout_weight="1" android:gravity="center_vertical" android:orientation="horizontal">
            {temp_pair(62, ' android:layout_marginStart="-3dp"')}
            {details(15, 12)}
            {icon_box(64, ' android:layout_marginEnd="2dp"')}
        </LinearLayout>
        {alert_banner()}
        {metrics(True, 6, 7)}
        {forecast(8)}
        {hint}
    </LinearLayout>'''
shell("widget_expanded", expanded, "Expandida: clima atual, alerta, indicadores e 5 dias")
