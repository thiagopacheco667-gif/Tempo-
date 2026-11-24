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
        android:scaleType="fitXY" android:importantForAccessibility="no" android:src="@drawable/bg_night" />
    <ImageView android:id="@+id/bg_b" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitXY" android:importantForAccessibility="no" android:visibility="invisible" />

    <!-- Camadas ambientes animadas (apenas uma visível por vez; todas GONE com animações desligadas) -->
    <ProgressBar android:id="@+id/amb_rain" style="@style/WAmbient" android:layout_width="match_parent"
        android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_rain" />
    <ProgressBar android:id="@+id/amb_storm" style="@style/WAmbient" android:layout_width="match_parent"
        android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_storm" />
    <ProgressBar android:id="@+id/amb_stars" style="@style/WAmbient" android:layout_width="match_parent"
        android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_stars" />
    <ProgressBar android:id="@+id/amb_mist" style="@style/WAmbient" android:layout_width="match_parent"
        android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_mist" />

{content}
</FrameLayout>
'''
    open(os.path.join(D, name + ".xml"), "w").write(out)

def temp_pair(size, extra=""):
    return f'''<FrameLayout android:id="@+id/temp_box" android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:animateLayoutChanges="true"{extra}>
                <TextView android:id="@+id/temp_a" style="@style/WText.Temp" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:textSize="{size}sp" android:text="—°" />
                <TextView android:id="@+id/temp_b" style="@style/WText.Temp" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:textSize="{size}sp" android:visibility="invisible" />
            </FrameLayout>'''

def icon_box(size, glow):
    return f'''<FrameLayout android:id="@+id/icon_box" android:layout_width="{glow}dp" android:layout_height="{glow}dp"
                android:animateLayoutChanges="true">
                <ProgressBar android:id="@+id/amb_glow_sun" style="@style/WAmbient" android:layout_width="match_parent"
                    android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_glow_sun" />
                <ProgressBar android:id="@+id/amb_glow_sunset" style="@style/WAmbient" android:layout_width="match_parent"
                    android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_glow_sunset" />
                <ProgressBar android:id="@+id/amb_glow_moon" style="@style/WAmbient" android:layout_width="match_parent"
                    android:layout_height="match_parent" android:indeterminateDrawable="@drawable/amb_glow_moon" />
                <ImageView android:id="@+id/icon_a" android:layout_width="{size}dp" android:layout_height="{size}dp"
                    android:layout_gravity="center" android:contentDescription="@string/cd_condition_icon"
                    android:src="@drawable/wx_cloudy" />
                <ImageView android:id="@+id/icon_b" android:layout_width="{size}dp" android:layout_height="{size}dp"
                    android:layout_gravity="center" android:contentDescription="@string/cd_condition_icon"
                    android:visibility="invisible" />
            </FrameLayout>'''

def header(refresh=True):
    r = '''
            <ImageView android:id="@+id/status_dot" android:layout_width="6dp" android:layout_height="6dp"
                android:layout_marginEnd="6dp" android:src="@drawable/status_dot" android:importantForAccessibility="no" />
            <TextView android:id="@+id/updated" style="@style/WText.Caption" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:layout_marginEnd="6dp" />
            <FrameLayout android:layout_width="30dp" android:layout_height="30dp"
                android:animateLayoutChanges="true">
                <ImageView android:id="@+id/refresh" android:layout_width="match_parent" android:layout_height="match_parent"
                    android:background="@drawable/glass_button" android:padding="8dp" android:src="@drawable/gl_refresh"
                    android:alpha="0.85" android:contentDescription="@string/cd_refresh" />
                <ProgressBar android:id="@+id/loading" style="@style/WAmbient" android:layout_width="18dp"
                    android:layout_height="18dp" android:layout_gravity="center" android:indeterminateDrawable="@drawable/avd_loader" />
            </FrameLayout>''' if refresh else ""
    return f'''<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:layout_width="13dp" android:layout_height="13dp" android:layout_marginEnd="5dp"
                android:alpha="0.9" android:src="@drawable/gl_pin" android:importantForAccessibility="no" />
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                android:gravity="center_vertical" android:orientation="horizontal">
                <TextView android:id="@+id/city" style="@style/WText.City" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:text="@string/app_name" />
                <TextView android:id="@+id/region" style="@style/WText.Region" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" android:layout_marginStart="6dp" android:layout_marginEnd="8dp" />
            </LinearLayout>{r}
        </LinearLayout>'''

def details(cond_size, gap):
    return f'''<LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                android:layout_marginStart="{gap}dp" android:orientation="vertical">
                <TextView android:id="@+id/condition" style="@style/WText.Condition" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:textSize="{cond_size}sp" />
                <TextView android:id="@+id/hilo" style="@style/WText.Detail" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="4dp" />
                <TextView android:id="@+id/feels" style="@style/WText.Detail" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="3dp" />
            </LinearLayout>'''

def metric(i, glyph, cd, two_line):
    label = f'''
                    <TextView android:id="@+id/m{i}_label" style="@style/WText.MetricLabel" android:layout_width="wrap_content"
                        android:layout_height="wrap_content" android:layout_marginTop="2dp" android:visibility="gone" />''' if two_line else ""
    return f'''                <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                    android:gravity="center" android:orientation="vertical" android:contentDescription="@string/{cd}">
                    <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:gravity="center_vertical" android:orientation="horizontal">
                        <ImageView android:layout_width="12dp" android:layout_height="12dp" android:layout_marginEnd="4dp"
                            android:alpha="0.75" android:src="@drawable/{glyph}" android:importantForAccessibility="no" />
                        <TextView android:id="@+id/m{i}_value" style="@style/WText.MetricValue" android:layout_width="wrap_content"
                            android:layout_height="wrap_content" android:text="--" />
                    </LinearLayout>{label}
                </LinearLayout>'''

def metrics(height, two_line, top):
    cells = "\n".join(metric(i, g, cd, two_line) for i, (g, cd) in enumerate(
        [("gl_rain", "cd_rain"), ("gl_humidity", "cd_humidity"), ("gl_wind", "cd_wind"), ("gl_uv", "cd_uv")]))
    return f'''<LinearLayout android:id="@+id/metrics" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:minHeight="{height}dp" android:layout_marginTop="{top}dp" android:background="@drawable/glass_panel"
            android:orientation="horizontal" android:paddingStart="4dp" android:paddingEnd="4dp"
            android:paddingTop="8dp" android:paddingBottom="8dp">
{cells}
        </LinearLayout>'''

def forecast(top):
    cols = []
    for i in range(5):
        cols.append(f'''            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
                android:gravity="center_horizontal" android:orientation="vertical">
                <TextView android:id="@+id/d{i}_name" style="@style/WText.Day" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:text="--" />
                <ImageView android:id="@+id/d{i}_icon" android:layout_width="26dp" android:layout_height="26dp"
                    android:layout_marginTop="2dp" android:importantForAccessibility="no" />
                <TextView android:id="@+id/d{i}_max" style="@style/WText.DayMax" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="2dp" />
                <TextView android:id="@+id/d{i}_min" style="@style/WText.DayMin" android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:layout_marginTop="1dp" />
            </LinearLayout>''')
    return f'''<LinearLayout android:id="@+id/forecast" android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="{top}dp" android:background="@drawable/glass_panel" android:orientation="horizontal"
            android:paddingTop="7dp" android:paddingBottom="7dp" android:paddingStart="4dp" android:paddingEnd="4dp">
''' + "\n".join(cols) + '''
        </LinearLayout>'''

hint = '''<TextView android:id="@+id/hint" style="@style/WText.Detail" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="8dp" android:gravity="center"
            android:maxLines="2" android:visibility="gone" />'''

# ---------------- compacto (2x2) ----------------
compact = f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:padding="13dp">
        <TextView android:id="@+id/city" style="@style/WText.City" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textSize="13.5sp" android:text="@string/app_name" />
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:layout_marginTop="4dp" android:gravity="center_vertical" android:orientation="horizontal">
            <ImageView android:id="@+id/status_dot" android:layout_width="5dp" android:layout_height="5dp"
                android:layout_marginEnd="5dp" android:src="@drawable/status_dot" android:importantForAccessibility="no" />
            <TextView android:id="@+id/updated" style="@style/WText.Caption" android:layout_width="0dp"
                android:layout_height="wrap_content" android:layout_weight="1" />
        </LinearLayout>
        <FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:gravity="center_vertical" android:orientation="horizontal">
            {temp_pair(44, ' android:layout_marginStart="-2dp"')}
            <FrameLayout android:layout_width="0dp" android:layout_height="50dp" android:layout_weight="1" />
            {icon_box(46, 58).replace('android:layout_width="58dp" android:layout_height="58dp"', 'android:layout_width="58dp" android:layout_height="58dp" android:layout_marginEnd="-8dp" android:layout_marginTop="-4dp" android:layout_marginBottom="-4dp"')}
        </LinearLayout>
        <TextView android:id="@+id/condition" style="@style/WText.Condition" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="1dp" android:textSize="12.5sp" />
        <TextView android:id="@+id/hilo" style="@style/WText.Detail" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="3dp" android:textSize="11.5sp" />
        <ProgressBar android:id="@+id/loading" style="@style/WAmbient" android:layout_width="0dp" android:layout_height="0dp"
            android:indeterminateDrawable="@drawable/avd_loader" />
    </LinearLayout>'''
shell("widget_compact", compact, "Versão compacta: temperatura, local e condição")

# ---------------- médio (4x2) ----------------
medium = f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="16dp"
        android:paddingTop="12dp" android:paddingBottom="14dp">
        {header()}
        <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
            android:gravity="center_vertical" android:orientation="horizontal">
            {temp_pair(52, ' android:layout_marginStart="-2dp"')}
            {details(13.5, 10)}
            {icon_box(58, 72).replace('android:layout_width="72dp" android:layout_height="72dp"', 'android:layout_width="72dp" android:layout_height="72dp" android:layout_marginEnd="-8dp"')}
        </LinearLayout>
        {metrics(32, False, 6)}
        {hint}
    </LinearLayout>'''
shell("widget_medium", medium, "Versão média: clima atual + indicadores")

# ---------------- expandido (4x3+) ----------------
expanded = f'''    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="vertical" android:paddingStart="16dp" android:paddingEnd="16dp"
        android:paddingTop="12dp" android:paddingBottom="14dp">
        {header()}
        <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"
            android:gravity="center_vertical" android:orientation="horizontal">
            {temp_pair(58, ' android:layout_marginStart="-3dp"')}
            {details(14.5, 12)}
            {icon_box(68, 84).replace('android:layout_width="84dp" android:layout_height="84dp"', 'android:layout_width="84dp" android:layout_height="84dp" android:layout_marginEnd="-8dp"')}
        </LinearLayout>
        {metrics(32, True, 6)}
        {forecast(8)}
        {hint}
    </LinearLayout>'''
shell("widget_expanded", expanded, "Versão expandida: todas as informações")
