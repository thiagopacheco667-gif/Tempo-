"""Busca fotos REAIS no Wikimedia Commons (somente 'Quality images', horizontais, licença livre)
para um cenário e baixa miniaturas candidatas + metadados de crédito.
Uso: python3 commons_search.py <pasta_saida> <nome_cenario> "<termos de busca>" [...mais termos]"""
import json, os, sys, urllib.parse, urllib.request

UA = "TempoWidget/1.0 (Android weather widget; asset curation)"
API = "https://commons.wikimedia.org/w/api.php"
FREE = ("CC0", "Public domain", "CC BY", "CC BY-SA")

def get(url, tries=5):
    import time, urllib.error
    for n in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=40) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or n == tries - 1:
                raise
            time.sleep(5 * (n + 1))

def search(terms, limit=12):
    q = {
        "action": "query", "format": "json", "generator": "search", "gsrnamespace": "6",
        "gsrlimit": str(limit), "gsrsearch": f"{terms} filetype:bitmap incategory:Quality_images",
        "prop": "imageinfo", "iiprop": "url|size|extmetadata", "iiurlwidth": "960",
        "iiextmetadatafilter": "LicenseShortName|Artist|LicenseUrl",
    }
    d = json.loads(get(API + "?" + urllib.parse.urlencode(q)))
    return list(d.get("query", {}).get("pages", {}).values())

out, scene = sys.argv[1], sys.argv[2]
os.makedirs(os.path.join(out, scene), exist_ok=True)
seen, picked = set(), []
for terms in sys.argv[3:]:
    for p in search(terms):
        if p["title"] in seen:
            continue
        seen.add(p["title"])
        ii = p["imageinfo"][0]
        m = ii.get("extmetadata", {})
        lic = m.get("LicenseShortName", {}).get("value", "")
        if ii["width"] < 1600 or ii["width"] / ii["height"] < 1.3 or not lic.startswith(FREE):
            continue
        picked.append({"title": p["title"], "thumb": ii["thumburl"].replace("//thumb.wikimedia.org/", "//upload.wikimedia.org/"), "page": ii["descriptionurl"],
                       "license": lic, "artist": m.get("Artist", {}).get("value", "")})
for i, c in enumerate(picked[:10]):
    import time; time.sleep(1.5)
    path = os.path.join(out, scene, f"{i}.jpg")
    open(path, "wb").write(get(c["thumb"]))
    c["file"] = path
json.dump(picked[:10], open(os.path.join(out, scene, "meta.json"), "w"), ensure_ascii=False, indent=1)
print(scene, len(picked[:10]))
