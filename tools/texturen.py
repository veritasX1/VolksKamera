#!/usr/bin/env python3
"""
Gehäuse-Texturen aus ambientCG-Paketen (CC0) für die App aufbereiten.

Je Paket <Name>_4K-PNG.zip (oder 2K) entstehen in app/src/main/assets/gehaeuse/:
  <Name>_farbe.webp   Farbe × Ambient Occlusion (Stoffe mit Löchern auf dunklem Grund), 1024², kachelbar
  <Name>_flaeche.webp R,G = Normale (OpenGL), B = Rauheit – für das Licht beim Bewegen
                      (kein Alphakanal: transparente Pixel verlieren beim Kodieren/Laden ihre Farbe;
                       der Metallanteil steht als ein Wert je Textur in texturen.json)
  <Name>_vorschau.jpg Ausschnitt mit festem Licht von oben links (wie in der App), 160²
                      (die Kugel-Vorschauen von ambientCG passen nicht immer zur Farbkarte)
und texturen.json mit Namen, Kategorie und Farbe. Unfertige Downloads (.crdownload) werden übersprungen,
schon aufbereitete Pakete ebenfalls (--neu erzwingt alles).
"""
import colorsys, io, json, os, re, sys, zipfile
from PIL import Image, ImageChops, ImageMath, ImageStat

QUELLE = os.path.expanduser("~/Dokumente/Tests for APK/Volkskamera Textures/Creative Commons/ambientcg.com")
ZIEL = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "gehaeuse")
GROESSE = 1024
KAT = {"Leather": "Leder", "Fabric": "Stoff", "Rope": "Stoff", "Wood": "Holz", "PaintedWood": "Holz",
       "Metal": "Metall", "SheetMetal": "Metall", "DiamondPlate": "Metall", "Foil": "Metall",
       "Marble": "Stein", "Onyx": "Stein", "Travertine": "Stein", "Rock": "Stein", "Asphalt": "Stein",
       "Ground": "Stein", "Plaster": "Stein", "Tiles": "Fliesen", "Porcelain": "Fliesen"}
# als Gehäuse ungeeignet (technischer Verlauf / nur Fleck-Overlays)
AUSLASSEN = {"ChristmasTreeOrnament019", "Leaking012C", "Leaking019C"}

# Anzeigenamen (von Hand vergeben); fehlt einer, wird aus Kategorie + Farbe ein Name gebildet
NAMEN = {
    "Fabric009": "Steppstoff Altrosa", "Fabric010": "Korbgeflecht Oliv", "Fabric011": "Korbgeflecht Sand",
    "Fabric013": "Korbgeflecht Braun", "Fabric015": "Webband Blau-Weiß", "Fabric016": "Strick Bordeaux",
    "Fabric020": "Samt Königsblau", "Fabric029": "Samt Schwarz", "Fabric043": "Filz Grau meliert",
    "Fabric050": "Karo Bunt", "Fabric053": "Schottenkaro Braun", "Fabric054": "Schottenkaro Rot",
    "Fabric061": "Leinen Grau", "Fabric062": "Leinen Hellgrau", "Fabric063": "Canvas Anthrazit",
    "Fabric070": "Stoff Limette", "Fabric076": "Streifen Marine", "Fabric080": "Karo Pastell",
    "Leather002": "Leder Schwarzbraun", "Leather003": "Wildleder Braun", "Leather004": "Leder Sand",
    "Leather006": "Leder Espresso", "Leather007": "Vintage-Leder Braun", "Leather016": "Lochleder Cognac",
    "Leather018": "Lochleder Weiß", "Leather020": "Leder Kirschrot", "Leather024": "Leder Ziegelrot",
    "Leather028": "Leder Mokka", "Leather029": "Leder Terrakotta", "Leather030": "Leder Dunkelbraun",
    "Leather032": "Leder Schwarz", "Leather033A": "Vintage-Leder Rotbraun", "Leather034B": "Steppleder Schwarz",
    "Leather035A": "Steppleder Creme, Doppelnaht", "Leather035B": "Steppleder Creme", "Leather036A": "Steppleder Rot",
    "Leather037": "Leder Kastanie", "Leather038": "Leder Orange",
    "Metal007": "Messing gealtert", "Metal009": "Aluminium gebürstet", "Metal035": "Kupfer",
    "Metal038": "Stahl verzinkt", "Metal041A": "Stahl hell", "Metal042A": "Gold", "Metal046A": "Stahl dunkel",
    "Metal047B": "Rostblech", "Metal048A": "Messing poliert", "Metal048B": "Messing matt",
    "Metal048C": "Messing verkratzt", "Metal050A": "Chrom", "Metal052C": "Zinkblech fleckig",
    "Metal053B": "Blech mit Rostspuren", "Metal053C": "Blech stark verrostet", "Metal055A": "Edelstahl",
    "Metal061B": "Aluminium matt", "Metal062C": "Blech verwittert", "Metal063": "Stahl brüniert",
    "Asphalt019": "Asphalt", "DiamondPlate008C": "Riffelblech", "Fabric060": "Leinen Weiß", "Fabric077": "Jeans",
    "Foil002": "Goldfolie zerknittert", "Ground093C": "Sand", "Ground102": "Erde mit Moos",
    "Marble007": "Marmor Sand", "Marble009": "Marmor Verde", "Marble012": "Marmor Grau", "Marble015": "Marmor Creme",
    "Marble016": "Marmor Nero", "Marble019": "Marmor Weiß", "Marble020": "Marmor Rosé", "Marble023": "Marmor Anthrazit",
    "Marble024": "Marmor Taupe", "Metal004": "Stahlblech fleckig", "Metal008": "Kupfer gealtert", "Metal012": "Aluminium hell",
    "Metal016": "Weißblech verwittert", "Metal018": "Rostblech dunkel", "Metal027": "Lack Nachtblau", "Metal028": "Lack Schwarz",
    "Metal029": "Lack Schwarz matt", "Metal051A": "Lack Weiß", "Metal052A": "Aluminium eloxiert", "Metal054A": "Aluminium geschliffen",
    "Metal056A": "Blech zerkratzt", "Metal056B": "Blech zerkratzt, Rost", "Metal057A": "Roségold", "Metal058C": "Kupfer mit Patina",
    "MetalPlates008": "Stahlplatten", "MetalWalkway014": "Lochblech rostig", "SheetMetal001": "Lochblech Schwarz",
    "Onyx001": "Onyx Grau", "Onyx004": "Onyx Elfenbein", "Onyx006": "Onyx Blau", "Onyx010": "Onyx Rot",
    "Onyx011": "Onyx Petrol", "Onyx012": "Onyx Sand", "Onyx015": "Onyx Weiß",
    "PaintedWood008C": "Holz lackiert, verwittert", "Plaster001": "Putz Weiß", "Plaster004": "Putz Grau",
    "Porcelain001": "Porzellan Weiß", "Rock020": "Fels Grau-Grün", "Rock037": "Fels Dunkelgrün", "Rope001": "Tauwerk",
    "Tiles032": "Fliesen Metrogrün", "Tiles075": "Fliesen Marmor Schwarz", "Tiles078": "Fliesen Travertin",
    "Tiles081": "Fliesen Sternmuster", "Tiles108": "Fliesen Schwarz", "Tiles129B": "Mosaik Schwarz",
    "Tiles131": "Fliesen Kreuzmuster", "Tiles133D": "Fliesen Weiß, gesprungen",
    "Travertine004": "Travertin Grau", "Travertine005": "Travertin Braun", "Travertine013": "Travertin Blau",
    "Wood048": "Kiefer", "Wood058": "Eiche", "Wood060": "Nussbaum", "Wood061": "Ahorn", "Wood062": "Treibholz",
    "Wood067": "Mahagoni", "Wood068": "Buche", "Wood094": "Esche hell", "Wood095": "Fichte",
    "WoodFloor041": "Dielen verwittert", "WoodFloor051": "Parkett Eiche",
}

def farbname(rgb):
    r, g, b = [c / 255 for c in rgb]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if l < 0.13: return "Schwarz"
    if s < 0.18 or l > 0.9:
        return "Weiß" if l > 0.8 else "Hellgrau" if l > 0.6 else "Grau" if l > 0.3 else "Dunkelgrau"
    h *= 360
    if (h < 40 or h > 330) and l < 0.45 and s < 0.7: return "Braun" if h < 40 else "Bordeaux"
    if h < 15 or h >= 330: return "Rot"
    if h < 40: return "Orange" if l > 0.45 else "Braun"
    if h < 55: return "Ocker" if l < 0.6 else "Beige"
    if h < 75: return "Gelb"
    if h < 160: return "Grün"
    if h < 200: return "Türkis"
    if h < 255: return "Blau"
    return "Violett"

def lade(z, name, suffix):
    kandidaten = [n for n in z.namelist() if n.endswith(f"_{suffix}.png")]
    if not kandidaten: return None
    im = Image.open(io.BytesIO(z.read(kandidaten[0])))
    im.load()
    return im

def graustufe(im):
    if im.mode in ("I;16", "I;16B", "I"):   # 16-Bit-Graustufen auf 8 Bit
        im = im.point(lambda v: v / 256).convert("L")
    return im.convert("L").resize((GROESSE, GROESSE), Image.LANCZOS)

def beleuchtet(farbe, flaeche, metall):
    """Vorschau: Ausschnitt mit Streulicht aus der Normalenkarte, Licht von oben links (wie App-Shader)."""
    f = farbe.crop((0, 0, 480, 480)).resize((160, 160), Image.LANCZOS)
    n = flaeche.crop((0, 0, 480, 480)).resize((160, 160), Image.LANCZOS)
    nr, ng, _ = n.split()
    lx, ly, lz = -0.45, -0.5, 0.74
    schatten = ImageMath.lambda_eval(
        lambda a: ((0.42 + 0.72 * a["max"]((a["float"](a["r"]) / 127.5 - 1) * lx + (1 - a["float"](a["g"]) / 127.5) * ly + 0.9 * lz, 0.0))
                   * (1 - 0.55 * metall) + 0.45 * metall) * 255, r=nr, g=ng).convert("L")
    return ImageChops.multiply(f, Image.merge("RGB", (schatten,) * 3)).point(lambda v: min(255, int(v * 1.25)))

def bearbeite(pfad, name):
    with zipfile.ZipFile(pfad) as z:
        farbe = lade(z, name, "Color").convert("RGB").resize((GROESSE, GROESSE), Image.LANCZOS)
        ao = lade(z, name, "AmbientOcclusion")
        if ao is not None:   # AO nur zu 70 % einrechnen, sonst wird es zu dunkel
            ao = graustufe(ao).point(lambda v: int(255 - (255 - v) * 0.7))
            farbe = ImageChops.multiply(farbe, Image.merge("RGB", (ao, ao, ao)))
        deck = lade(z, name, "Opacity")
        if deck is not None:   # Gewebe mit Löchern: dunkler Untergrund scheint durch
            farbe = Image.composite(farbe, Image.new("RGB", farbe.size, (18, 16, 14)), graustufe(deck))
        normal = lade(z, name, "NormalGL").convert("RGB").resize((GROESSE, GROESSE), Image.LANCZOS)
        rau = lade(z, name, "Roughness")
        rau = graustufe(rau) if rau is not None else Image.new("L", farbe.size, 180)
        met = lade(z, name, "Metalness")
        met = graustufe(met) if met is not None else Image.new("L", farbe.size, 0)
        nr, ng, _ = normal.split()
        flaeche = Image.merge("RGB", (nr, ng, rau))
        vorschau = beleuchtet(farbe, flaeche, ImageStat.Stat(met).mean[0] / 255)
    farbe.save(os.path.join(ZIEL, f"{name}_farbe.webp"), "WEBP", quality=88, method=6)
    flaeche.save(os.path.join(ZIEL, f"{name}_flaeche.webp"), "WEBP", quality=92, method=6)
    vorschau.save(os.path.join(ZIEL, f"{name}_vorschau.jpg"), quality=85)
    mittel = tuple(int(v) for v in ImageStat.Stat(farbe.resize((64, 64))).mean)
    metall = ImageStat.Stat(met).mean[0] / 255
    return mittel, round(metall, 2)

def main():
    neu = "--neu" in sys.argv
    os.makedirs(ZIEL, exist_ok=True)
    jpath = os.path.join(ZIEL, "texturen.json")
    daten = json.load(open(jpath)) if os.path.exists(jpath) and not neu else {}
    pakete = {}
    for f in sorted(os.listdir(QUELLE)):
        m = re.match(r"(.+)_(\d)K-PNG\.zip$", f)
        if not m: continue
        name, k = m.group(1), int(m.group(2))
        if name not in pakete or k > pakete[name][1]: pakete[name] = (f, k)   # höchste Auflösung nehmen
    for weg in AUSLASSEN:   # ungeeignete Pakete entfernen
        daten.pop(weg, None)
        for suf in ("_farbe.webp", "_flaeche.webp", "_vorschau.jpg"):
            p = os.path.join(ZIEL, weg + suf)
            if os.path.exists(p): os.remove(p)
    for name, (f, _) in pakete.items():
        if name in AUSLASSEN: continue
        if name in daten and os.path.exists(os.path.join(ZIEL, f"{name}_farbe.webp")): continue
        try:
            mittel, metall = bearbeite(os.path.join(QUELLE, f), name)
        except Exception as e:
            print(f"übersprungen {f}: {e}"); continue
        kat = next((v for k, v in KAT.items() if name.startswith(k)), "Material")
        nr = re.sub(r"^\D+", "", name)
        daten[name] = {"name": f"{kat} {farbname(mittel)}", "nr": nr, "kategorie": kat, "farbe": "#%02x%02x%02x" % mittel,
                       "metall": metall, "quelle": f"ambientCG {name}", "lizenz": "CC0 1.0"}
        print(f"fertig {name}: {daten[name]['name']}")
    for k, v in daten.items():
        v["kategorie"] = next((KAT[kk] for kk in sorted(KAT, key=len, reverse=True) if k.startswith(kk)), "Material")
        if k in NAMEN: v["name"] = NAMEN[k]
        elif v["name"].startswith("Material "): v["name"] = v["kategorie"] + v["name"][len("Material"):]
    json.dump(dict(sorted(daten.items())), open(jpath, "w"), ensure_ascii=False, indent=1)
    print(len(daten), "Texturen")

if __name__ == "__main__":
    main()
