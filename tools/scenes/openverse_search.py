"""Busca fotos REAIS no Openverse (principalmente Flickr) com licença que permite uso comercial
e adaptação (CC0, PDM, CC BY, CC BY-SA), horizontais e com >= 1000 px; baixa candidatas e
grava metadados de crédito.
Uso: python3 openverse_search.py <pasta_saida> <cenario> "<busca>" ["<busca>" ...]"""
import json, os, sys, time, urllib.parse, urllib.request, urllib.error

UA = "TempoWidget/1.0 (Android weather widget; photo curation)"
OK_LIC = {"cc0", "pdm", "by", "by-sa"}

def get(url, tries=4):
    for n in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=40) as r:
                return r.read()
        except (urllib.error.HTTPError, urllib.error.URLError) as e:
            if n == tries - 1:
                raise
            time.sleep(4 * (n + 1))

out, scene = sys.argv[1], sys.argv[2]
d = os.path.join(out, scene); os.makedirs(d, exist_ok=True)
seen, picked = set(), []
for q in sys.argv[3:]:
    url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode(
        {"q": q, "license_type": "commercial", "page_size": "20"})
    for r in json.loads(get(url)).get("results", []):
        w, h = r.get("width") or 0, r.get("height") or 1
        if r["id"] in seen or r.get("license") not in OK_LIC or w < 1000 or w / h < 1.3:
            continue
        if "staticflickr.com" not in r["url"]:
            continue
        seen.add(r["id"])
        picked.append({"title": r.get("title") or "", "artist": r.get("creator") or "",
                       "license": ("CC " + r["license"].upper() + " " + (r.get("license_version") or "")).strip()
                       if r["license"] not in ("cc0", "pdm") else r["license"].upper(),
                       "page": r.get("foreign_landing_url") or "", "url": r["url"]})
    time.sleep(1.5)
keep = []
for c in picked[:10]:
    path = os.path.join(d, f"{len(keep)}.jpg")
    try:
        open(path, "wb").write(get(c["url"])); c["file"] = path; keep.append(c)
    except Exception:
        pass
    time.sleep(0.8)
json.dump(keep, open(os.path.join(d, "meta.json"), "w"), ensure_ascii=False, indent=1)
print(scene, len(keep), flush=True)
