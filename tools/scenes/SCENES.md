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
| `scene_clear_tarde` | céu limpo · tarde | ✅ |
| `scene_clear_entardecer` | céu limpo · entardecer | ✅ |
| `scene_clear_noite` | céu limpo · noite | ✅ |
| `scene_partly_manha` | parcialmente nublado · manhã | ✅ |
| `scene_partly_tarde` | parcialmente nublado · tarde | ✅ |
| `scene_partly_entardecer` | parcialmente nublado · entardecer | ✅ |
| `scene_partly_noite` | parcialmente nublado · madrugada e noite | ✅ |
| `scene_cloudy_dia` | nublado · manhã e tarde | ✅ |
| `scene_cloudy_entardecer` | nublado · entardecer | ✅ |
| `scene_cloudy_noite` | nublado · madrugada e noite | ✅ |
| `scene_fog_manha` | nevoeiro · manhã (floresta com neblina, luz do amanhecer) | ✅ |
| `scene_fog_dia` | nevoeiro · tarde e entardecer | ✅ |
| `scene_fog_noite` | nevoeiro · madrugada e noite | ✅ |
| `scene_rain_light_dia` | chuva fraca / garoa · manhã e tarde | ✅ |
| `scene_rain_light_noite` | chuva fraca / garoa · madrugada e noite | ✅ |
| `scene_rain_moderate_dia` | chuva moderada · manhã e tarde | ✅ |
| `scene_rain_moderate_noite` | chuva moderada · madrugada e noite | ✅ |
| `scene_rain_heavy_dia` | chuva intensa · manhã e tarde | ✅ |
| `scene_rain_heavy_noite` | chuva intensa · madrugada e noite | ✅ |
| `scene_rain_entardecer` | qualquer chuva · entardecer (nuvens escuras, horizonte alaranjado) | ✅ |
| `scene_storm_dia` | tempestade com raios · manhã e tarde | ✅ |
| `scene_storm_entardecer` | tempestade com raios · entardecer | ✅ |
| `scene_storm_noite` | tempestade com raios · madrugada e noite (relâmpagos) | ✅ |
| `scene_hail_dia` | granizo · manhã, tarde e entardecer | ✅ |
| `scene_hail_noite` | granizo · madrugada e noite | ✅ |
| `scene_snow_dia` | neve · manhã, tarde e entardecer | ✅ |
| `scene_snow_noite` | neve · madrugada e noite | ✅ |

Todas as fotografias são reais (Flickr via Openverse), com licenças CC0, domínio público, CC BY 2.0 ou
CC BY-SA 2.0. Autor, título e link de cada uma estão em `app/src/main/res/raw/photo_credits.json` e aparecem
em "Créditos das fotos" na tela de configurações. Para buscar candidatas: `tools/scenes/openverse_search.py`.
