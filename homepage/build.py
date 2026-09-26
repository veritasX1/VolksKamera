#!/usr/bin/env python3
"""
Erzeugt die Homepage in vier Sprachen (de = Standard im Wurzelverzeichnis, en/fr/ru in Unterordnern),
je mit Hauptseite und Anleitung. Die Anleitung nutzt dieselben Hilfetexte wie die App
(HelpScreen.kt + assets/i18n/*.json). Aufruf: python3 build.py [version]  – die SHA-256-Werte werden aus den APKs in diesem Ordner berechnet.
"""
import hashlib, html, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(HERE, "..", "app", "src", "main")
VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.0"
FDROID = "https://volkskamera.goip.de/fdroid/repo"
FDROID_FP = "8DA9DEF44C65856A1B098DB7BF876D48514997EF17C7256E23E91EAC6C486500"
CERT = "7806bf101cca62f7bd57a9e08d8e2d0514bd58888d4d94a78100064c46fbebfc"
LANGS = ["de", "en", "fr", "ru"]
NAMES = {"de": "Deutsch", "en": "English", "fr": "Français", "ru": "Русский"}
VFILE = VERSION.replace(" ", "-")

def apk(l): return f"Volkskamera-{VFILE}-{l}.apk"

def sha(l):
    with open(os.path.join(HERE, apk(l)), "rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()

SHAS = {l: sha(l) for l in LANGS}  # fehlt eine APK, bricht der Aufruf ab, statt leere Prüfsummen zu veröffentlichen

# ---------- Hilfetexte aus der App ----------
src = open(os.path.join(APP, "java/com/volkskamera/app/ui/HelpScreen.kt"), encoding="utf-8").read()
def unesc(s): return s.replace('\\n', '\n').replace('\\"', '"').replace('\\$', '$')
TOPICS = [(unesc(a), unesc(b)) for a, b in re.findall(r'HelpTopic\(t\("((?:[^"\\]|\\.)*)"\),\s*t\("((?:[^"\\]|\\.)*)"\)\)', src)]
TR = {l: (json.load(open(os.path.join(APP, f"assets/i18n/{l}.json"), encoding="utf-8")) if l != "de" else {}) for l in LANGS}
def app_t(l, de): return TR[l].get(de, de)

T = {
 "title": {"de": "Volkskamera – die Filmkamera für Android", "en": "Volkskamera – the film camera for Android",
           "fr": "Volkskamera – la caméra argentique pour Android", "ru": "Volkskamera – киноплёночная камера для Android"},
 "claim": {"de": "Die Filmkamera für dein Android-Handy", "en": "The film camera for your Android phone",
           "fr": "La caméra argentique pour ton téléphone Android", "ru": "Киноплёночная камера для вашего Android-телефона"},
 "desc": {"de": "Kostenlose Android-App: filmen mit 289 echten Filmmaterialien, Farbfiltern und historischen Mikrofonen. Ohne Werbung, ohne Tracking, ohne Internet-Berechtigung.",
          "en": "Free Android app: film with 289 real film stocks, colour filters and historic microphones. No ads, no tracking, no internet permission.",
          "fr": "Application Android gratuite : filmer avec 289 vraies pellicules, des filtres colorés et des micros historiques. Sans publicité, sans pistage, sans autorisation Internet.",
          "ru": "Бесплатное приложение для Android: съёмка на 289 настоящих плёнок, с цветными фильтрами и историческими микрофонами. Без рекламы, без слежки, без доступа к интернету."},
 "download": {"de": "APK herunterladen · Version {v}", "en": "Download APK · version {v}", "fr": "Télécharger l’APK · version {v}", "ru": "Скачать APK · версия {v}"},
 "meta": {"de": "Android 8.0 oder neuer · {mb} MB · kostenlos · App-Sprache: Deutsch (in der App umschaltbar)",
          "en": "Android 8.0 or newer · {mb} MB · free · app language: English (switchable in the app)",
          "fr": "Android 8.0 ou plus récent · {mb} Mo · gratuit · langue : français (modifiable dans l’app)",
          "ru": "Android 8.0 или новее · {mb} МБ · бесплатно · язык приложения: русский (можно сменить в приложении)"},
 "cert": {"de": "Signatur-Zertifikat", "en": "Signing certificate", "fr": "Certificat de signature", "ru": "Сертификат подписи"},
 "guide_link": {"de": "Anleitung & Erklärungen", "en": "Guide & explanations", "fr": "Guide & explications", "ru": "Руководство и пояснения"},
 "h_what": {"de": "Was ist das", "en": "What it is", "fr": "Qu’est-ce que c’est", "ru": "Что это"},
 "what": {"de": "Die Volkskamera filmt wie eine analoge Filmkamera. Du wählst ein echtes Filmmaterial – vom orthochromatischen Kinofilm der 1890er über Kodachrome und Agfacolor bis zum heutigen Diafilm – und filmst mit seinen Farben, seinem Kontrast und seinem Korn. Wie bei einer echten Kamera hat jeder Film eine feste Empfindlichkeit; die Helligkeit regelst du über die Belichtungszeit.",
          "en": "Volkskamera films like an analogue movie camera. You choose a real film stock – from the orthochromatic cinema film of the 1890s via Kodachrome and Agfacolor to today’s slide film – and film with its colours, its contrast and its grain. As with a real camera, every film has a fixed sensitivity; you control brightness with the shutter speed.",
          "fr": "Volkskamera filme comme une caméra argentique. Tu choisis une vraie pellicule – du film cinéma orthochromatique des années 1890 à la diapositive d’aujourd’hui en passant par le Kodachrome et l’Agfacolor – et tu filmes avec ses couleurs, son contraste et son grain. Comme sur une vraie caméra, chaque film a une sensibilité fixe ; tu règles la luminosité avec la vitesse d’obturation.",
          "ru": "Volkskamera снимает как аналоговая кинокамера. Вы выбираете настоящую плёнку – от ортохроматической киноплёнки 1890-х через Kodachrome и Agfacolor до современной обращаемой – и снимаете с её цветами, контрастом и зерном. Как у настоящей камеры, у каждой плёнки фиксированная светочувствительность; яркость регулируется выдержкой."},
 "features": {
  "de": [("289 Filmmaterialien", "von 26 Herstellern in 8, 16 und 35 mm – jeder mit Steckbrief, Beispielbild und Quellen."),
         ("Farbfilter für Schwarzweiß", "Gelb, Orange, Rot, Grün, Blau – mit Kodak-Wratten-, B+W- und Hoya-Bezeichnung."),
         ("Echte Belichtung", "Feste Film-Empfindlichkeit, Zeiten von 1/5 bis 1/5000 – keine Automatik, kein Nachbelichten."),
         ("Sensor-Kalibrierung", "Misst, wie dein Handy das Bild verändert, und gleicht es vor jedem Film aus."),
         ("50 Mikrofone", "vom Edison-Phonographen bis zum Camcorder, dazu Rauschen, Knacken und Brummen."),
         ("Kombinationen teilen", "Film, Filter und Ton speichern und als kurzen Text weitergeben."),
         ("LUT-Editor", "Jeden Film anpassen und als eigene Fassung speichern."),
         ("Dein Gehäuse", "127 Materialien mit Licht, das Neigen und Schwenken folgt; eigener Schriftzug, vier App-Symbole."),
         ("Analog-Look", "Schaltet die Nachschärfung des Handys ab – weiche Zeichnung, Schimmer und Lichthof, bis zu 100-fach verstärkbar."),
         ("Moiré-Filter", "Ruhige Stoffe, Gitter und Bildschirme: optischer Tiefpass und vollständige Sensor-Auslesung.")],
  "en": [("289 film stocks", "from 26 manufacturers in 8, 16 and 35 mm – each with profile, sample image and sources."),
         ("Colour filters for B&W", "yellow, orange, red, green, blue – with Kodak Wratten, B+W and Hoya names."),
         ("Real exposure", "Fixed film sensitivity, shutter speeds from 1/5 to 1/5000 – no auto mode, no re-exposure."),
         ("Sensor calibration", "Measures how your phone alters the image and compensates before every film."),
         ("50 microphones", "from the Edison phonograph to the camcorder, plus noise, crackle and hum."),
         ("Share combinations", "Save film, filter and sound and pass them on as a short text."),
         ("LUT editor", "Adjust any film and save it as your own version."),
         ("Your camera body", "127 materials with light that follows tilting and turning; your own lettering, four app icons."),
         ("Analogue look", "Switches off the phone’s sharpening – soft rendering, glow and halation, boostable up to 100×."),
         ("Moiré filter", "Calm fabrics, fences and screens: optical low-pass and full sensor readout.")],
  "fr": [("289 pellicules", "de 26 fabricants en 8, 16 et 35 mm – chacune avec fiche, image d’exemple et sources."),
         ("Filtres colorés pour le N&B", "jaune, orange, rouge, vert, bleu – avec désignations Kodak Wratten, B+W et Hoya."),
         ("Vraie exposition", "Sensibilité du film fixe, vitesses de 1/5 à 1/5000 – pas d’automatisme, pas de correction."),
         ("Calibrage du capteur", "Mesure comment ton téléphone modifie l’image et le compense avant chaque film."),
         ("50 microphones", "du phonographe d’Edison au caméscope, plus souffle, craquements et ronflement."),
         ("Partager des combinaisons", "Enregistre film, filtre et son et transmets-les sous forme de court texte."),
         ("Éditeur de LUT", "Adapte chaque film et enregistre ta propre version."),
         ("Ton boîtier", "127 matières avec une lumière qui suit l’inclinaison et la rotation ; inscription perso, quatre icônes."),
         ("Rendu analogique", "Désactive l’accentuation du téléphone – rendu doux, lueur et halo, amplifiable jusqu’à 100×."),
         ("Filtre anti-moiré", "Tissus, grilles et écrans apaisés : passe-bas optique et lecture complète du capteur.")],
  "ru": [("289 плёнок", "от 26 производителей в 8, 16 и 35 мм – у каждой карточка, пример изображения и источники."),
         ("Цветные фильтры для ч/б", "жёлтый, оранжевый, красный, зелёный, синий – с обозначениями Kodak Wratten, B+W и Hoya."),
         ("Настоящая экспозиция", "Фиксированная светочувствительность плёнки, выдержки от 1/5 до 1/5000 – без автоматики."),
         ("Калибровка сенсора", "Измеряет, как телефон изменяет изображение, и компенсирует это перед каждой плёнкой."),
         ("50 микрофонов", "от фонографа Эдисона до видеокамеры, плюс шум, треск и гул."),
         ("Делитесь комбинациями", "Сохраняйте плёнку, фильтр и звук и передавайте их коротким текстом."),
         ("Редактор LUT", "Настройте любую плёнку и сохраните свою версию."),
         ("Ваш корпус", "127 материалов со светом, который следует за наклоном и поворотом; своя надпись, четыре значка."),
         ("Аналоговый вид", "Отключает повышение резкости телефона – мягкий рисунок, свечение и ореол, усиление до 100×."),
         ("Антимуаровый фильтр", "Спокойные ткани, решётки и экраны: оптический фильтр нижних частот и полное считывание сенсора.")]},
 "h_shots": {"de": "Einblicke", "en": "Screenshots", "fr": "Aperçus", "ru": "Как это выглядит"},
 "shots_note": {"de": "", "en": "Screenshots show the German interface; the app is fully available in English.",
                "fr": "Les captures montrent l’interface allemande ; l’application est entièrement disponible en français.",
                "ru": "На снимках показан немецкий интерфейс; приложение полностью доступно на русском."},
 "shots": {"de": ["Die Kamera", "Analog-Look und Moiré-Filter", "Filmsteckbrief", "Farbfilter für Schwarzweiß", "LUT-Editor", "Mikrofone der Zeit", "Filmauswahl", "Dein Gehäuse"],
           "en": ["The camera", "Analogue look and moiré filter", "Film profile", "Colour filters for B&W", "LUT editor", "Microphones of the era", "Film selection", "Your camera body"],
           "fr": ["La caméra", "Rendu analogique et filtre anti-moiré", "Fiche du film", "Filtres colorés pour le N&B", "Éditeur de LUT", "Micros d’époque", "Sélection des films", "Ton boîtier"],
           "ru": ["Камера", "Аналоговый вид и антимуаровый фильтр", "Карточка плёнки", "Цветные фильтры для ч/б", "Редактор LUT", "Микрофоны эпохи", "Выбор плёнки", "Ваш корпус"]},
 "h_privacy": {"de": "Datenschutz", "en": "Privacy", "fr": "Confidentialité", "ru": "Конфиденциальность"},
 "privacy": {"de": ["<b>Die Volkskamera hat keine Internet-Berechtigung.</b> Sie kann technisch nichts senden: keine Werbung, kein Tracking, keine Konten, keine Cloud, keine Analyse. Deine Aufnahmen bleiben auf deinem Gerät. Die App fragt nur nach Kamera und Mikrofon – zum Filmen.",
                    "Auch diese Seite setzt keine Cookies, bindet nichts von fremden Servern ein (keine Schriften, keine Skripte, keine Statistik) und kommt ohne JavaScript aus."],
             "en": ["<b>Volkskamera has no internet permission.</b> It technically cannot send anything: no ads, no tracking, no accounts, no cloud, no analytics. Your recordings stay on your device. The app only asks for camera and microphone – to film.",
                    "This site sets no cookies either, loads nothing from third-party servers (no fonts, no scripts, no statistics) and works without JavaScript."],
             "fr": ["<b>Volkskamera n’a pas d’autorisation Internet.</b> Elle ne peut techniquement rien envoyer : pas de publicité, pas de pistage, pas de compte, pas de cloud, pas d’analyse. Tes enregistrements restent sur ton appareil. L’application ne demande que la caméra et le micro – pour filmer.",
                    "Ce site ne dépose pas non plus de cookies, ne charge rien depuis des serveurs tiers (ni polices, ni scripts, ni statistiques) et fonctionne sans JavaScript."],
             "ru": ["<b>У Volkskamera нет доступа к интернету.</b> Технически она ничего не может отправить: ни рекламы, ни слежки, ни учётных записей, ни облака, ни аналитики. Ваши записи остаются на устройстве. Приложение запрашивает только камеру и микрофон – для съёмки.",
                    "Этот сайт тоже не использует cookies, ничего не загружает со сторонних серверов (ни шрифтов, ни скриптов, ни статистики) и работает без JavaScript."]},
 "source_code": {"de": "Der Quellcode ist offen – jeder kann nachprüfen, was die App tut:", "en": "The source code is open – anyone can check what the app does:",
                 "fr": "Le code source est ouvert – chacun peut vérifier ce que fait l’application :", "ru": "Исходный код открыт – каждый может проверить, что делает приложение:"},
 "h_only": {"de": "Nur hier herunterladen", "en": "Download only here", "fr": "Télécharger uniquement ici", "ru": "Скачивайте только здесь"},
 "only": {"de": "Die Volkskamera ist <b>kostenlos</b> und hat <b>keine Bezahlfunktionen</b>. Es gibt keine „Premium“-, „Pro“- oder „Mod“-Version. Angebote auf anderen Seiten oder in fremden App-Stores sind nicht von uns und können verändert sein. Offizielle Quellen sind ausschließlich <b>volkskamera.goip.de</b> und <a href=\"https://github.com/veritasX1/VolksKamera/releases\">GitHub-Releases</a>. Prüfe im Zweifel die SHA-256-Prüfsumme oben.",
          "en": "Volkskamera is <b>free</b> and has <b>no paid features</b>. There is no “premium”, “pro” or “mod” version. Offers on other sites or in third-party app stores are not ours and may be tampered with. The only official sources are <b>volkskamera.goip.de</b> and <a href=\"https://github.com/veritasX1/VolksKamera/releases\">GitHub releases</a>. If in doubt, check the SHA-256 checksum above.",
          "fr": "Volkskamera est <b>gratuite</b> et n’a <b>aucune fonction payante</b>. Il n’existe ni version « premium », ni « pro », ni « mod ». Les offres sur d’autres sites ou dans des boutiques tierces ne viennent pas de nous et peuvent être modifiées. Les seules sources officielles sont <b>volkskamera.goip.de</b> et les <a href=\"https://github.com/veritasX1/VolksKamera/releases\">versions GitHub</a>. En cas de doute, vérifie la somme SHA-256 ci-dessus.",
          "ru": "Volkskamera <b>бесплатна</b> и <b>не содержит платных функций</b>. Никаких «премиум», «про» или «мод»-версий нет. Предложения на других сайтах или в сторонних магазинах приложений – не наши и могут быть изменены. Официальные источники – только <b>volkskamera.goip.de</b> и <a href=\"https://github.com/veritasX1/VolksKamera/releases\">релизы на GitHub</a>. При сомнениях сверьте контрольную сумму SHA-256 выше."},
 "h_install": {"de": "Installation", "en": "Installation", "fr": "Installation", "ru": "Установка"},
 "install": {"de": ["APK auf dem Handy herunterladen.", "Öffnen und die Installation aus dieser Quelle einmalig erlauben.", "Beim ersten Start Kamera und Mikrofon freigeben – fertig."],
             "en": ["Download the APK on your phone.", "Open it and allow installation from this source once.", "Grant camera and microphone access on first start – done."],
             "fr": ["Télécharge l’APK sur ton téléphone.", "Ouvre-le et autorise une fois l’installation depuis cette source.", "Au premier lancement, autorise la caméra et le micro – c’est tout."],
             "ru": ["Скачайте APK на телефон.", "Откройте его и один раз разрешите установку из этого источника.", "При первом запуске разрешите доступ к камере и микрофону – готово."]},
 "update_note": {"de": "Wer eine frühere Test-Version installiert hat, muss sie einmal deinstallieren. Alle Sprachversionen sind dieselbe App – nur die Startsprache unterscheidet sich.",
                 "en": "If you installed an earlier test version, uninstall it once. All language versions are the same app – only the starting language differs.",
                 "fr": "Si tu as installé une ancienne version de test, désinstalle-la une fois. Toutes les versions linguistiques sont la même application – seule la langue de départ diffère.",
                 "ru": "Если вы устанавливали раннюю тестовую версию, один раз удалите её. Все языковые версии – одно и то же приложение, отличается только язык при запуске."},
 "h_license": {"de": "Lizenz", "en": "Licence", "fr": "Licence", "ru": "Лицензия"},
 "license": {"de": "Die Volkskamera ist frei nutzbar – privat und beruflich. <b>Alles, was du damit aufnimmst, gehört dir</b>, auch zur kommerziellen Verwendung. Nicht erlaubt ist, die App oder Teile davon zu verkaufen, Kaufpakete, Abos oder Werbung einzubauen oder sie über andere Seiten zu verbreiten.",
             "en": "Volkskamera is free to use – privately and professionally. <b>Everything you record with it belongs to you</b>, including for commercial use. It is not permitted to sell the app or parts of it, to add paid packs, subscriptions or ads, or to distribute it via other sites.",
             "fr": "Volkskamera est libre d’utilisation – à titre privé comme professionnel. <b>Tout ce que tu enregistres t’appartient</b>, y compris pour un usage commercial. Il est interdit de vendre l’application ou une partie de celle-ci, d’y ajouter des achats, abonnements ou publicités, ou de la diffuser via d’autres sites.",
             "ru": "Volkskamera можно свободно использовать – в личных и профессиональных целях. <b>Всё, что вы с её помощью снимаете, принадлежит вам</b>, в том числе для коммерческого использования. Запрещено продавать приложение или его части, встраивать платные пакеты, подписки или рекламу, а также распространять его через другие сайты."},
 "license_link": {"de": "Vollständige Lizenz", "en": "Full licence (German, with English summary)", "fr": "Licence complète (en allemand, résumé en anglais)", "ru": "Полная лицензия (на немецком, с кратким изложением на английском)"},
 "h_sources": {"de": "Quellen & Rechte", "en": "Sources & rights", "fr": "Sources & droits", "ru": "Источники и права"},
 "sources": {
  "de": ["<b>Film-Looks:</b> selbst berechnet aus den technischen Datenblättern der Hersteller (Eastman Kodak, Fujifilm, Ilford/Harman, Foma, Agfa/Agfa-Gevaert, ADOX, ORWO, CineStill, Lomography u. a.) sowie der Kennwert-Sammlung im Projekt <a href=\"https://github.com/peva3/Lightroom-Presets\">peva3/Lightroom-Presets</a>. Die Quellen zu jedem Film stehen in der App im Steckbrief.",
         "<b>Gehäuse-Texturen:</b> <a href=\"https://ambientcg.com\">ambientCG.com</a>, Creative Commons CC0 1.0 (gemeinfrei). Danke!",
         "<b>Schriften:</b> Great Vibes (TypeSETit) und Kaushan Script (Impallari Type), SIL Open Font License 1.1.",
         "<b>Hersteller-Logos:</b> Wikipedia/Wikimedia Commons. Kodak, Kodachrome, Fujifilm, Agfa, Ilford, ORWO und alle weiteren genannten Namen und Logos sind <b>Marken ihrer jeweiligen Inhaber</b>. Die Volkskamera steht mit keinem dieser Unternehmen in Verbindung.",
         "<b>Ton:</b> Mikrofone, Rauschen, Knacken und Brummen werden in der App berechnet – keine fremden Aufnahmen.",
         "<b>Beispielbilder:</b> eigene Fotos aus Schleswig-Holstein (Fröruper Berge)."],
  "en": ["<b>Film looks:</b> calculated by us from the manufacturers’ technical data sheets (Eastman Kodak, Fujifilm, Ilford/Harman, Foma, Agfa/Agfa-Gevaert, ADOX, ORWO, CineStill, Lomography and others) and the collection of characteristic values in the project <a href=\"https://github.com/peva3/Lightroom-Presets\">peva3/Lightroom-Presets</a>. The sources for each film are listed in the app’s film profile.",
         "<b>Body textures:</b> <a href=\"https://ambientcg.com\">ambientCG.com</a>, Creative Commons CC0 1.0 (public domain). Thank you!",
         "<b>Fonts:</b> Great Vibes (TypeSETit) and Kaushan Script (Impallari Type), SIL Open Font License 1.1.",
         "<b>Manufacturer logos:</b> Wikipedia/Wikimedia Commons. Kodak, Kodachrome, Fujifilm, Agfa, Ilford, ORWO and all other names and logos mentioned are <b>trademarks of their respective owners</b>. Volkskamera is not affiliated with any of these companies.",
         "<b>Sound:</b> microphones, noise, crackle and hum are calculated in the app – no third-party recordings.",
         "<b>Sample images:</b> our own photos from Schleswig-Holstein (Fröruper Berge)."],
  "fr": ["<b>Rendus des films :</b> calculés par nos soins à partir des fiches techniques des fabricants (Eastman Kodak, Fujifilm, Ilford/Harman, Foma, Agfa/Agfa-Gevaert, ADOX, ORWO, CineStill, Lomography, etc.) et du recueil de valeurs du projet <a href=\"https://github.com/peva3/Lightroom-Presets\">peva3/Lightroom-Presets</a>. Les sources de chaque film figurent dans sa fiche dans l’application.",
         "<b>Textures du boîtier :</b> <a href=\"https://ambientcg.com\">ambientCG.com</a>, Creative Commons CC0 1.0 (domaine public). Merci !",
         "<b>Polices :</b> Great Vibes (TypeSETit) et Kaushan Script (Impallari Type), SIL Open Font License 1.1.",
         "<b>Logos des fabricants :</b> Wikipédia/Wikimedia Commons. Kodak, Kodachrome, Fujifilm, Agfa, Ilford, ORWO et tous les autres noms et logos cités sont des <b>marques de leurs propriétaires respectifs</b>. Volkskamera n’est liée à aucune de ces entreprises.",
         "<b>Son :</b> micros, souffle, craquements et ronflement sont calculés dans l’application – aucun enregistrement tiers.",
         "<b>Images d’exemple :</b> nos propres photos du Schleswig-Holstein (Fröruper Berge)."],
  "ru": ["<b>Характер плёнок:</b> рассчитан нами по техническим паспортам производителей (Eastman Kodak, Fujifilm, Ilford/Harman, Foma, Agfa/Agfa-Gevaert, ADOX, ORWO, CineStill, Lomography и др.) и по сборнику характеристик в проекте <a href=\"https://github.com/peva3/Lightroom-Presets\">peva3/Lightroom-Presets</a>. Источники для каждой плёнки указаны в её карточке в приложении.",
         "<b>Текстуры корпуса:</b> <a href=\"https://ambientcg.com\">ambientCG.com</a>, Creative Commons CC0 1.0 (общественное достояние). Спасибо!",
         "<b>Шрифты:</b> Great Vibes (TypeSETit) и Kaushan Script (Impallari Type), SIL Open Font License 1.1.",
         "<b>Логотипы производителей:</b> Википедия/Wikimedia Commons. Kodak, Kodachrome, Fujifilm, Agfa, Ilford, ORWO и все прочие упомянутые названия и логотипы являются <b>товарными знаками их владельцев</b>. Volkskamera не связана ни с одной из этих компаний.",
         "<b>Звук:</b> микрофоны, шум, треск и гул рассчитываются в приложении – никаких сторонних записей.",
         "<b>Примеры изображений:</b> собственные фотографии из Шлезвиг-Гольштейна (Fröruper Berge)."]},
 "sources_full": {"de": "Vollständige Liste", "en": "Full list (German)", "fr": "Liste complète (en allemand)", "ru": "Полный список (на немецком)"},
 "credit": {"de": "© seit 2026 Olaf Winkler · Entwickelt in Schleswig-Holstein", "en": "© since 2026 Olaf Winkler · Developed in Schleswig-Holstein, Germany",
            "fr": "© depuis 2026 Olaf Winkler · Développé au Schleswig-Holstein (Allemagne)", "ru": "© с 2026 года, Olaf Winkler · Разработано в Шлезвиг-Гольштейне (Германия)"},
 "c_dl": {"de": "Downloads", "en": "downloads", "fr": "téléchargements", "ru": "загрузок"},
 "c_vis": {"de": "Besucher", "en": "visitors", "fr": "visiteurs", "ru": "посетителей"},
 "h_fdroid": {"de": "Über F-Droid", "en": "Via F-Droid", "fr": "Via F-Droid", "ru": "Через F-Droid"},
 "fdroid": {"de": "Mit der App <a href=\"https://f-droid.org\">F-Droid</a> bekommst du jedes Update automatisch. Füge dazu einmal meine Paketquelle hinzu – darin findest du auch meine Apps <b>Schmalfilm</b> und <b>RetroCam</b> (frühe Testversion).",
            "en": "With the <a href=\"https://f-droid.org\">F-Droid</a> app you get every update automatically. Just add my repository once – it also contains my apps <b>Schmalfilm</b> and <b>RetroCam</b> (early test version).",
            "fr": "Avec l’application <a href=\"https://f-droid.org\">F-Droid</a>, vous recevez chaque mise à jour automatiquement. Ajoutez une fois mon dépôt – vous y trouverez aussi mes applications <b>Schmalfilm</b> et <b>RetroCam</b> (version de test précoce).",
            "ru": "С приложением <a href=\"https://f-droid.org\">F-Droid</a> все обновления приходят автоматически. Добавьте один раз мой репозиторий – в нём также есть мои приложения <b>Schmalfilm</b> и <b>RetroCam</b> (ранняя тестовая версия)."},
 "fdroid_btn": {"de": "Paketquelle in F-Droid hinzufügen", "en": "Add repository to F-Droid", "fr": "Ajouter le dépôt à F-Droid", "ru": "Добавить репозиторий в F-Droid"},
 "fdroid_qr": {"de": "Am Computer? Scanne den Code mit dem Handy:", "en": "On a computer? Scan the code with your phone:", "fr": "Sur un ordinateur ? Scannez le code avec votre téléphone :", "ru": "На компьютере? Отсканируйте код телефоном:"},
 "fdroid_manual": {"de": "Von Hand: Adresse", "en": "Manually: address", "fr": "Manuellement : adresse", "ru": "Вручную: адрес"},
 "fdroid_fp": {"de": "Fingerabdruck", "en": "Fingerprint", "fr": "Empreinte", "ru": "Отпечаток"},
 "fdroid_note": {"de": "Die F-Droid-Version startet in der Sprache deines Handys. Sie ist mit demselben Schlüssel signiert wie die APK hier – du kannst jederzeit wechseln, deine Einstellungen bleiben.",
                 "en": "The F-Droid version starts in your phone’s language. It is signed with the same key as the APK here – you can switch at any time and keep your settings.",
                 "fr": "La version F-Droid démarre dans la langue de votre téléphone. Elle est signée avec la même clé que l’APK d’ici – vous pouvez changer à tout moment et garder vos réglages.",
                 "ru": "Версия из F-Droid запускается на языке телефона. Она подписана тем же ключом, что и APK здесь, – можно переходить в любой момент, настройки сохраняются."},
 "report": {"de": "Fehler melden", "en": "Report a bug", "fr": "Signaler un bug", "ru": "Сообщить об ошибке"},
 "code": {"de": "Quellcode", "en": "Source code", "fr": "Code source", "ru": "Исходный код"},
 "back": {"de": "‹ Zur Startseite", "en": "‹ Back to home", "fr": "‹ Retour à l’accueil", "ru": "‹ На главную"},
 "guide_title": {"de": "Anleitung", "en": "Guide", "fr": "Guide", "ru": "Руководство"},
 "guide_intro": {"de": "Alles Wichtige zur Volkskamera – dieselben Erklärungen findest du auch in der App unter Zahnrad → „?“.",
                 "en": "Everything important about Volkskamera – you find the same explanations in the app under gear → “?”.",
                 "fr": "Tout l’essentiel sur Volkskamera – tu trouves les mêmes explications dans l’application sous roue dentée → « ? ».",
                 "ru": "Всё главное о Volkskamera – те же пояснения есть в приложении: шестерёнка → «?»."},
 "h_cal": {"de": "Sensor kalibrieren – Schritt für Schritt", "en": "Calibrating the sensor – step by step", "fr": "Calibrer le capteur – pas à pas", "ru": "Калибровка сенсора – шаг за шагом"},
}

CSS = """@font-face { font-family: "Great Vibes"; src: url("{root}great_vibes.ttf"); font-display: swap; }
:root { --gold:#e0c070; --gold2:#b8912e; --bg:#0d0c0b; --panel:#171512; --text:#ece6da; --muted:#a79f90; }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--text); font:17px/1.6 Georgia, "Times New Roman", serif; }
a { color:var(--gold); }
.wrap { max-width:880px; margin:0 auto; padding:0 20px; }
nav.lang { text-align:right; padding:10px 20px 0; font:14px system-ui,sans-serif; }
nav.lang a, nav.lang b { margin-left:12px; text-decoration:none; }
nav.lang b { color:var(--text); }
header { text-align:center; padding:30px 0 36px; border-bottom:1px solid #2a2620; background: radial-gradient(ellipse at top, #2a2112 0%, var(--bg) 70%); }
header img { width:112px; height:112px; border-radius:26px; box-shadow:0 10px 30px #000; }
h1 { font-family:"Great Vibes", cursive; font-weight:400; font-size:76px; margin:0; padding:.25em .2em .05em; line-height:1.1;
  background:linear-gradient(#fff4c4,var(--gold) 45%,var(--gold2)); -webkit-background-clip:text; background-clip:text; color:transparent; }
.claim { font-size:20px; color:var(--muted); margin:4px 0 26px; letter-spacing:.5px; }
.btn { display:inline-block; padding:14px 30px; border-radius:40px; background:linear-gradient(var(--gold),var(--gold2)); color:#1a1408; font-weight:bold; text-decoration:none; font-family:system-ui,sans-serif; font-size:17px; box-shadow:0 6px 18px #0008; }
.btn2 { display:inline-block; margin-top:14px; padding:9px 22px; border-radius:40px; border:1px solid var(--gold2); color:var(--gold); text-decoration:none; font-family:system-ui,sans-serif; font-size:15px; }
.meta { font:13px/1.5 system-ui,sans-serif; color:var(--muted); margin-top:14px; word-break:break-all; }
section { padding:34px 0; border-bottom:1px solid #2a2620; }
h2 { font-family:system-ui,sans-serif; letter-spacing:3px; text-transform:uppercase; font-size:15px; color:var(--gold); margin:0 0 14px; }
h2::before, h2::after { content:"◆"; font-size:9px; vertical-align:middle; margin:0 10px; color:var(--gold2); }
h3 { font-family:system-ui,sans-serif; color:var(--gold); font-size:17px; margin:22px 0 6px; }
.grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:14px; }
.card { background:var(--panel); border:1px solid #2c2720; border-radius:12px; padding:14px 16px; }
.card b { color:var(--gold); font-family:system-ui,sans-serif; }
.warn { border-color:var(--gold2); background:#211a0d; }
.fine { color:var(--muted); font-size:14px; }
.shots { display:grid; gap:18px; }
figure { margin:0; background:var(--panel); border:1px solid #2c2720; border-radius:14px; overflow:hidden; }
figure img { width:100%; height:auto; display:block; }
figcaption { padding:10px 16px 14px; font-size:15px; color:var(--muted); }
.pair { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin:12px 0; }
.pair img { width:100%; border-radius:8px; display:block; }
.pre { white-space:pre-line; }
.zaehler { margin:22px 0 0; font:14px system-ui,sans-serif; color:var(--muted); display:flex; justify-content:center; flex-wrap:wrap; gap:10px 26px; }
.zaehler span { display:inline-flex; align-items:center; gap:9px; }
.zw { font:600 17px/1 "Courier New",monospace; letter-spacing:3px; color:#f4f0e6; background:#050505; padding:6px 6px 6px 9px; border-radius:4px;
  border:2px solid var(--gold2); box-shadow:inset 0 2px 6px #000, 0 1px 0 #3a3226; }
.fdqr { display:flex; gap:18px; align-items:center; flex-wrap:wrap; margin:14px 0; }
.fdqr img { border-radius:8px; background:#fff; }
.fdqr p { flex:1; min-width:220px; margin:0; }
code.fp { word-break:break-all; }
footer { padding:28px 0 50px; text-align:center; color:var(--muted); font:13px system-ui,sans-serif; }
"""

def page(l, sub, title, body):
    root = "" if l == "de" else "../"
    def href(x): return (root if x == "de" else root + x + "/") + ("anleitung.html" if sub and x == "de" else "guide.html" if sub else "")
    langnav = " ".join(f"<b>{NAMES[x]}</b>" if x == l else f'<a href="{href(x) or "./"}" hreflang="{x}">{NAMES[x]}</a>' for x in LANGS)
    return f"""<!doctype html>
<html lang="{l}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(T['desc'][l])}">
<link rel="icon" href="{root}symbol.png">
{''.join(f'<link rel="alternate" hreflang="{x}" href="https://volkskamera.goip.de/{"" if x == "de" else x + "/"}{("anleitung.html" if x == "de" else "guide.html") if sub else ""}">' for x in LANGS)}
<style>{CSS.replace("{root}", root)}</style>
</head>
<body>
<nav class="lang">{langnav}</nav>
{body}
<footer>
  Volkskamera {VERSION} · {T['credit'][l]}<br>
  <a href="https://github.com/veritasX1/VolksKamera">{T['code'][l]}</a> · <a href="https://github.com/veritasX1/VolksKamera/issues">{T['report'][l]}</a>
</footer>
</body>
</html>
"""

def main_page(l):
    root = "" if l == "de" else "../"
    guide = "anleitung.html" if l == "de" else "guide.html"
    mb = round(os.path.getsize(os.path.join(HERE, apk(l))) / 1e6) if os.path.exists(os.path.join(HERE, apk(l))) else 81
    feats = "\n".join(f'<div class="card"><b>{a}</b><br>{b}</div>' for a, b in T["features"][l])
    names = ["01_kamera", "08_aufnahme", "03_steckbrief", "04_farbfilter", "05_luteditor", "06_mikrofon", "02_hersteller", "07_gehaeuse"]
    figs = "\n".join(f'<figure><img src="{root}bilder/{n}.webp" alt="{c}" loading="lazy"><figcaption><b>{c}</b></figcaption></figure>'
                     for n, c in zip(names, T["shots"][l]))
    body = f"""<header><div class="wrap">
  <img src="{root}symbol.png" alt="Volkskamera">
  <h1>Volkskamera</h1>
  <p class="claim">{T['claim'][l]}</p>
  <a class="btn" href="{root}{apk(l)}" download>{T['download'][l].format(v=VERSION)}</a><br>
  <a class="btn2" href="{guide}">{T['guide_link'][l]}</a>
  <p class="zaehler"><span><b class="zw">%%DL%%</b>{T['c_dl'][l]}</span><span><b class="zw">%%BES%%</b>{T['c_vis'][l]}</span></p>
  <p class="meta">{T['meta'][l].format(mb=mb)}<br>SHA-256: {SHAS.get(l, '')}<br>{T['cert'][l]} (SHA-256): {CERT}</p>
</div></header>
<main class="wrap">
<section><h2>{T['h_what'][l]}</h2><p>{T['what'][l]}</p><div class="grid">{feats}</div></section>
<section><h2>{T['h_shots'][l]}</h2>{('<p class="fine">' + T['shots_note'][l] + '</p>') if T['shots_note'][l] else ''}<div class="shots">{figs}</div></section>
<section><h2>{T['h_privacy'][l]}</h2>{''.join('<p>' + p + '</p>' for p in T['privacy'][l])}
  <p class="fine">{T['source_code'][l]} <a href="https://github.com/veritasX1/VolksKamera">github.com/veritasX1/VolksKamera</a></p></section>
<section><h2>{T['h_only'][l]}</h2><div class="card warn"><p style="margin:0">{T['only'][l]}</p></div></section>
<section><h2>{T['h_install'][l]}</h2><ol>{''.join('<li>' + x + '</li>' for x in T['install'][l])}</ol><p class="fine">{T['update_note'][l]}</p></section>
<section><h2>{T['h_fdroid'][l]}</h2><p>{T['fdroid'][l]}</p>
<p><a class="btn2" href="fdroidrepos://volkskamera.goip.de/fdroid/repo?fingerprint={FDROID_FP}">{T['fdroid_btn'][l]}</a></p>
<div class="fdqr"><img src="{root}bilder/fdroid_qr.png" alt="QR" width="150" height="150"><p class="fine">{T['fdroid_qr'][l]}<br>{T['fdroid_manual'][l]}: <code>{FDROID}</code><br>{T['fdroid_fp'][l]}: <code class="fp">{FDROID_FP}</code></p></div>
<p class="fine">{T['fdroid_note'][l]}</p></section>
<section><h2>{T['h_license'][l]}</h2><p>{T['license'][l]} <a href="https://github.com/veritasX1/VolksKamera/blob/main/LICENSE.md">{T['license_link'][l]}</a></p></section>
<section><h2>{T['h_sources'][l]}</h2><ul>{''.join('<li>' + x + '</li>' for x in T['sources'][l])}</ul>
  <p class="fine"><a href="https://github.com/veritasX1/VolksKamera/blob/main/QUELLEN.md">{T['sources_full'][l]}</a></p></section>
</main>"""
    return page(l, False, T["title"][l], body)

def guide_page(l):
    root = "" if l == "de" else "../"
    home = "./" if l == "de" else "./"
    parts = []
    for title, text in TOPICS:
        parts.append(f"<h3>{html.escape(app_t(l, title))}</h3><p class=\"pre\">{html.escape(app_t(l, text))}</p>")
        if title == "Aufnahme & Kalibrierung":
            steps = app_t(l, "1.  Lege Gegenstände mit vielen kräftigen Farben zusammen – zum Beispiel Buntstifte oder Filzstifte – dazu etwas Weißes und etwas Dunkles.\n2.  Sorge für gleichmäßiges Licht: Tageslicht vom Fenster ist ideal, kein direktes Gegenlicht, keine harten Schatten.\n3.  Halte das Handy ruhig, so dass die Farben den größten Teil des Bildes füllen.\n4.  Tippe auf „Jetzt kalibrieren“. Die App nimmt ein Rohbild und ein normales Bild zugleich auf und vergleicht sie.")
            why = app_t(l, "Jedes Handy bearbeitet sein Kamerabild anders – mit mehr Sättigung und mehr Kontrast, als der Sensor wirklich sieht. Die Filme der Volkskamera sind für ein neutrales Bild berechnet. Mit der Kalibrierung misst die App einmalig, wie dein Handy das Bild verändert, und gleicht es vor jedem Film aus.")
            ex = app_t(l, "Beispiel einer guten Kalibrier-Szene: viele Farben, eine helle und eine dunkle Fläche, gleichmäßiges Licht. Links das Bild des Handys, rechts dieselbe Aufnahme neutral entwickelt – die Kalibrierung gleicht genau diesen Unterschied aus.")
            parts.append(f"""<div class="card"><b>{T['h_cal'][l]}</b><p>{html.escape(why)}</p><p class="pre">{html.escape(steps)}</p>
<div class="pair"><figure><img src="{root}bilder/kal_handy.webp" alt=""><figcaption>{html.escape(app_t(l, 'So bearbeitet das Handy'))}</figcaption></figure>
<figure><img src="{root}bilder/kal_neutral.webp" alt=""><figcaption>{html.escape(app_t(l, 'So sieht der Sensor neutral'))}</figcaption></figure></div>
<p class="fine">{html.escape(ex)}</p></div>""")
    body = f"""<header><div class="wrap"><img src="{root}symbol.png" alt="Volkskamera"><h1>Volkskamera</h1>
<p class="claim">{T['guide_title'][l]}</p><a class="btn2" href="{home}">{T['back'][l]}</a></div></header>
<main class="wrap"><section><p>{T['guide_intro'][l]}</p>{''.join(parts)}</section></main>"""
    return page(l, True, f"Volkskamera – {T['guide_title'][l]}", body)

for l in LANGS:
    d = HERE if l == "de" else os.path.join(HERE, l)
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, "index.html"), "w", encoding="utf-8").write(main_page(l))
    open(os.path.join(d, "anleitung.html" if l == "de" else "guide.html"), "w", encoding="utf-8").write(guide_page(l))
print("Seiten erzeugt:", len(TOPICS), "Hilfethemen")
