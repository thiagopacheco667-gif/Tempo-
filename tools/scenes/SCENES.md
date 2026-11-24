# Cenários fotográficos do widget

O widget escolhe o fundo pela combinação **período do dia** (no fuso da cidade) × **condição**
(código WMO da Open-Meteo). Ver `app/src/main/java/app/tempo/weather/widget/Scenery.kt`.

Basta colocar um arquivo `scene_<nome>.webp` em `app/src/main/res/drawable-nodpi/` e o app passa a usá-lo
automaticamente. Enquanto a foto de um cenário não existir, o widget usa o degradê da condição correta,
nunca a foto de outra condição.

Para adicionar uma foto (qualquer JPG/PNG horizontal, idealmente com 1600 px de largura ou mais):

```bash
python3 tools/scenes/optimize.py minha_foto.jpg scene_rain_heavy_dia
```

Direção: fotografia realista, composição horizontal, céu amplo com horizonte baixo, cores naturais,
sem textos, números, pessoas ou elementos gráficos.

| Arquivo | Usado em | Status |
|---|---|---|
| `scene_clear_madrugada` | céu limpo · madrugada | ✅ |
| `scene_clear_manha` | céu limpo · manhã | ✅ |
| `scene_clear_tarde` | céu limpo · tarde | pendente |
| `scene_clear_entardecer` | céu limpo · entardecer | pendente |
| `scene_clear_noite` | céu limpo · noite | pendente |
| `scene_partly_manha` | parcialmente nublado · manhã | pendente |
| `scene_partly_tarde` | parcialmente nublado · tarde | pendente |
| `scene_partly_entardecer` | parcialmente nublado · entardecer | pendente |
| `scene_partly_noite` | parcialmente nublado · madrugada e noite | pendente |
| `scene_cloudy_dia` | nublado · manhã e tarde | pendente |
| `scene_cloudy_entardecer` | nublado · entardecer | pendente |
| `scene_cloudy_noite` | nublado · madrugada e noite | pendente |
| `scene_fog_manha` | nevoeiro · manhã (floresta com neblina, luz do amanhecer) | ✅ |
| `scene_fog_dia` | nevoeiro · tarde e entardecer | pendente |
| `scene_fog_noite` | nevoeiro · madrugada e noite | pendente |
| `scene_rain_light_dia` | chuva fraca / garoa · manhã e tarde | pendente |
| `scene_rain_light_noite` | chuva fraca / garoa · madrugada e noite | pendente |
| `scene_rain_moderate_dia` | chuva moderada · manhã e tarde | pendente |
| `scene_rain_moderate_noite` | chuva moderada · madrugada e noite | pendente |
| `scene_rain_heavy_dia` | chuva intensa · manhã e tarde | pendente |
| `scene_rain_heavy_noite` | chuva intensa · madrugada e noite | pendente |
| `scene_rain_entardecer` | qualquer chuva · entardecer (nuvens escuras, horizonte alaranjado) | pendente |
| `scene_storm_dia` | tempestade com raios · manhã e tarde | pendente |
| `scene_storm_entardecer` | tempestade com raios · entardecer | pendente |
| `scene_storm_noite` | tempestade com raios · madrugada e noite (relâmpagos) | pendente |
| `scene_hail_dia` | granizo · manhã, tarde e entardecer | pendente |
| `scene_hail_noite` | granizo · madrugada e noite | pendente |
| `scene_snow_dia` | neve · manhã, tarde e entardecer | pendente |
| `scene_snow_noite` | neve · madrugada e noite | pendente |

As fotos marcadas com ✅ foram geradas com IA no Canva da conta do usuário e otimizadas para 1280×720 WebP.
A de `clear_tarde` também foi gerada, mas os créditos acabaram antes de exportá-la em alta resolução.
Ela está na biblioteca do Canva (id `MAHbzj8sf4A`) e pode ser exportada depois.
