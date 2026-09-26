#!/usr/bin/env python3
"""Kleiner Webserver für die Volkskamera-Homepage, mit Range-Anfragen und Download-/Besucherzähler.
Dateien: /media/olaf/5TB1/volkskamera-homepage  ·  Port 8091  ·  Start per crontab @reboot.

Zähler (ohne JavaScript/Cookies, Zahlen werden beim Ausliefern in %%DL%% / %%BES%% eingesetzt):
- Besucher: eindeutige Besucher je Tag. Gespeichert wird nur ein Prüfwert aus IP + Browser mit täglich neuem
  Zufallssalz; Salz und Prüfwerte werden beim Tageswechsel verworfen, IP-Adressen nie gespeichert.
- Downloads: alle APKs (jede Sprache) zusammen, je Besucher und Tag einmal, plus Release-Downloads auf GitHub.
- Nicht gezählt: Bots/Werkzeuge (kein Browser) und das eigene Heimnetz."""
import http.server, io, ipaddress, json, os, re, secrets, socketserver, threading, time, urllib.request
from datetime import date
from hashlib import sha256

ROOT = "/media/olaf/5TB1/volkskamera-homepage"
DATA = "/media/olaf/5TB1/volkskamera-daten/zaehler.json"
PORT = 8091
GITHUB = "https://api.github.com/repos/veritasX1/VolksKamera/releases"
BOTS = re.compile(r"bot|crawl|spider|slurp|preview|headless|curl|wget|python|java/|go-http|okhttp|check|monitor|"
                  r"facebookexternalhit|whatsapp|telegram|signal|lighthouse|scan", re.I)

lock = threading.Lock()


def lade():
    try:
        with open(DATA) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {"seit": date.today().isoformat(), "downloads": 0, "besucher": 0, "je_datei": {}, "github": 0}


Z = lade()


def speichere():
    os.makedirs(os.path.dirname(DATA), exist_ok=True)
    tmp = DATA + ".tmp"
    with open(tmp, "w") as f:
        json.dump(Z, f, indent=1)
    os.replace(tmp, DATA)


def heute():
    """Tagesdaten (Salz + Prüfwerte) – beim Tageswechsel neu."""
    t = date.today().isoformat()
    if Z.get("tag") != t:
        Z.update(tag=t, salz=secrets.token_hex(16), besucht=[], geladen=[])
    return Z


def eigenes_netz(ip):
    try:
        a = ipaddress.ip_address(ip)
    except ValueError:
        return True
    if a.is_private or a.is_loopback or a.is_link_local:
        return True
    for datei in ("/home/olaf/.goip_last_ipv4", "/home/olaf/.goip_last_ipv6"):
        try:
            eigen = ipaddress.ip_address(open(datei).read().strip())
        except (OSError, ValueError):
            continue
        netz = ipaddress.ip_network(f"{eigen}/{32 if eigen.version == 4 else 64}", strict=False)
        if a.version == eigen.version and a in netz:
            return True
    return False


def github_loop():
    while True:
        try:
            req = urllib.request.Request(GITHUB, headers={"User-Agent": "volkskamera-homepage", "Accept": "application/vnd.github+json"})
            with urllib.request.urlopen(req, timeout=20) as r:
                n = sum(a.get("download_count", 0) for rel in json.load(r) for a in rel.get("assets", []))
            with lock:
                if Z.get("github") != n:
                    Z["github"] = n
                    speichere()
        except Exception:
            pass
        time.sleep(3600)


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      ".mp4": "video/mp4", ".svg": "image/svg+xml", ".js": "text/javascript", ".css": "text/css",
                      ".apk": "application/vnd.android.package-archive"}

    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def log_message(self, *a):
        pass

    def end_headers(self):
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Cache-Control", "no-cache" if self.path.split("?")[0].endswith((".html", "/")) else "max-age=3600")
        super().end_headers()

    def do_GET(self):
        self._get = True
        super().do_GET()

    def pruefwert(self):
        """None = nicht zählen (Bot, Werkzeug, eigenes Netz)."""
        ua = self.headers.get("User-Agent", "")
        ip = self.headers.get("X-Real-IP") or self.client_address[0]
        if "Mozilla" not in ua or BOTS.search(ua) or eigenes_netz(ip):
            return None
        return sha256((heute()["salz"] + ip + ua).encode()).hexdigest()[:20]

    def zaehle(self, art, datei=None):
        if not getattr(self, "_get", False):
            return
        with lock:
            h = self.pruefwert()
            if h is None:
                return
            if art == "besuch" and h not in Z["besucht"]:
                Z["besucht"].append(h)
                Z["besucher"] += 1
            elif art == "download" and h not in Z["geladen"]:
                Z["geladen"].append(h)
                Z["downloads"] += 1
                Z["je_datei"][datei] = Z["je_datei"].get(datei, 0) + 1
            else:
                return
            speichere()

    def send_head(self):
        url = self.path.split("?")[0]
        path = self.translate_path(self.path)
        if os.path.isdir(path) and url.endswith("/"):
            path = os.path.join(path, "index.html")
        if path.endswith(".html") and os.path.isfile(path):
            self.zaehle("besuch")
            with lock:
                dl, bes = Z["downloads"] + Z.get("github", 0), Z["besucher"]
            data = open(path, "rb").read().replace(b"%%DL%%", f"{dl:06d}".encode()).replace(b"%%BES%%", f"{bes:06d}".encode())
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            return io.BytesIO(data)

        rng = self.headers.get("Range")
        if path.endswith(".apk") and os.path.isfile(path) and (not rng or re.match(r"bytes=0-", rng)):
            self.zaehle("download", os.path.basename(path))
        if not rng or not os.path.isfile(path):
            return super().send_head()
        m = re.match(r"bytes=(\d*)-(\d*)", rng)
        size = os.path.getsize(path)
        if not m:
            return super().send_head()
        start = int(m.group(1)) if m.group(1) else max(0, size - int(m.group(2) or 0))
        end = int(m.group(2)) if m.group(1) and m.group(2) else size - 1
        end = min(end, size - 1)
        if start > end:
            self.send_error(416)
            return None
        f = open(path, "rb")
        f.seek(start)
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        self._remaining = end - start + 1
        return f

    def copyfile(self, source, outputfile):
        n = getattr(self, "_remaining", None)
        if n is None:
            return super().copyfile(source, outputfile)
        while n > 0:
            chunk = source.read(min(65536, n))
            if not chunk:
                break
            outputfile.write(chunk)
            n -= len(chunk)
        self._remaining = None


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    threading.Thread(target=github_loop, daemon=True).start()
    Server(("0.0.0.0", PORT), Handler).serve_forever()
