package com.volkskamera.app.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Historische Mikrofone und Aufnahmeketten 1920er … 1980er. Jedes Profil beschreibt den Klang
 * der ganzen Kette (Mikrofon + Medium), so wie man ihn aus Beiträgen der Zeit kennt:
 * Bandbreite, Resonanzen, Nahbesprechung, Verzerrung, Rauschen, Begrenzer/Automatik.
 *
 * Werte nach zeitgenössischen Angaben, u.a. Kohlemikrofon ~300–3400 Hz mit starker 2. Oberwelle,
 * Academy-Kurve 1938 (−10 dB bei 5 kHz, −18 dB bei 8 kHz), Super-8-Magnetton 80–8000 Hz bei 18 fps
 * mit Aussteuerungsautomatik, RCA 44 Bändchen 50 Hz–15 kHz mit kräftigem Nahbesprechungseffekt.
 */
enum class MicProfile(
    val decade: String,
    val label: String,
    val hint: String,
    val hp: Double,
    val lp: Double,
    /** steile Flanken (4. Ordnung) – enge, harte Bandbegrenzung */
    val steep: Boolean,
    /** Resonanzen/Präsenz: (Frequenz, dB, Güte) */
    val peaks: List<Triple<Double, Double, Double>> = emptyList(),
    /** Nahbesprechung / Wärme: Tiefenanhebung in dB unter ~200 Hz */
    val lowShelf: Double = 0.0,
    val drive: Float,
    /** Anteil gerader Obertöne (Kohle, Röhre) */
    val asym: Double = 0.0,
    val noise: Float,
    /** Klangfarbe des Eigenrauschens */
    val noiseLp: Double = 8000.0,
    /** Kohle-Prasseln: Rauschen wächst mit dem Signal, dazu feines Knistern */
    val carbon: Double = 0.0,
    /** Lichtton: harte Begrenzung bei Übersteuerung */
    val clip: Boolean = false,
    /** Sende-/Kinobegrenzer: gepresster Klang */
    val comp: Double = 0.0,
    /** Aussteuerungsautomatik: pumpt, hebt in Pausen das Rauschen an */
    val agc: Float = 0f,
    /** Bauform fürs Icon */
    val shape: MicShape = MicShape.CAPSULE,
) {
    PHONOGRAPH("1900er", "Edison-Phonograph (Wachswalze)", "Um 1900: akustisch auf Wachswalze – extrem eng, hohl, kräftiges Rauschen",
        350.0, 2500.0, true, listOf(Triple(900.0, 8.0, 5.0), Triple(1800.0, 6.0, 5.0)),
        drive = 0.35f, asym = 0.3, noise = 0.45f, noiseLp = 3500.0, shape = MicShape.HORN),
    GRAMMOPHON("1900er", "Grammophon-Schellack (akustisch)", "1910er: Trichteraufnahme auf Schellack – hohl, mittig, etwas voller als die Walze",
        200.0, 3500.0, true, listOf(Triple(600.0, 6.0, 3.0), Triple(2500.0, 5.0, 3.0)),
        drive = 0.3f, asym = 0.2, noise = 0.35f, noiseLp = 5000.0, shape = MicShape.HORN),

    TRICHTER("1920er", "Schalltrichter (akustisch)", "Vor dem elektrischen Mikrofon: in den Trichter gesungen – hohl, näselnd, nur Mitten",
        250.0, 2800.0, true, listOf(Triple(700.0, 7.0, 4.0), Triple(1400.0, 6.0, 4.0), Triple(2200.0, 5.0, 4.0)),
        drive = 0.3f, asym = 0.2, noise = 0.3f, noiseLp = 4000.0, shape = MicShape.HORN),
    KOHLE("1920er", "Reisz-Kohlemikrofon", "Rundfunk der 20er: Kohlekörner – eng, kratzig, prasselt und rauscht",
        300.0, 3300.0, true, listOf(Triple(1500.0, 6.0, 1.5), Triple(2600.0, 5.0, 3.0)),
        drive = 0.6f, asym = 0.6, noise = 0.35f, noiseLp = 5000.0, carbon = 0.6, shape = MicShape.CARBON),
    CMV3("1920er", "Neumann CMV 3 „Flasche“", "1928, erstes Serien-Kondensatormikrofon mit Röhre – schon erstaunlich klar",
        70.0, 7500.0, false, listOf(Triple(3500.0, 3.0, 1.0)),
        drive = 0.25f, asym = 0.3, noise = 0.12f, noiseLp = 9000.0, shape = MicShape.BOTTLE),
    MARCONI_SYKES("1920er", "Marconi-Sykes Magnetophon", "BBC 1923: frühe fremderregte Tauchspule, Spule in Watte gebettet – dumpf, weich, leises Rauschen",
        120.0, 4500.0, false, listOf(Triple(1000.0, 3.0, 1.2)), lowShelf = 2.0,
        drive = 0.25f, asym = 0.2, noise = 0.2f, noiseLp = 5000.0, shape = MicShape.CARBON),
    WE394("1920er", "Western Electric 394 Kondensator", "1922: früher Kondensator für Rundfunk und Schallplatte – klarer als Kohle, noch schmal",
        90.0, 5500.0, false, listOf(Triple(3000.0, 4.0, 1.5)),
        drive = 0.25f, asym = 0.2, noise = 0.18f, noiseLp = 7000.0, shape = MicShape.BOTTLE),

    LICHTTON30("1930er", "Western Electric + Lichtton", "Früher Tonfilm: Tauchspule, Lichttonspur, Academy-Kurve – Höhen stark gedämpft",
        90.0, 3600.0, false, listOf(Triple(2000.0, 2.0, 1.0)),
        drive = 0.35f, asym = 0.1, noise = 0.25f, noiseLp = 6000.0, clip = true, comp = 0.2, shape = MicShape.CAPSULE),
    RCA44("1930er", "RCA 44 Bändchen", "1932, Radio und Big Band: warm, weich, sanfte Höhen, voller Bass aus der Nähe",
        50.0, 9000.0, false, lowShelf = 5.0,
        drive = 0.15f, asym = 0.2, noise = 0.08f, noiseLp = 7000.0, shape = MicShape.RIBBON),
    VOLKSEMPFAENGER("1930er", "Volksempfänger (Mittelwelle)", "1933: Rundfunk über Mittelwelle und kleinen Lautsprecher – dünn, dröhnend, gepresst",
        180.0, 3800.0, true, listOf(Triple(900.0, 5.0, 2.0), Triple(250.0, 4.0, 3.0)),
        drive = 0.45f, asym = 0.5, noise = 0.25f, noiseLp = 4000.0, comp = 0.5, shape = MicShape.RADIO),
    WE618("1930er", "Western Electric 618A Tauchspule", "1931: erstes Serien-Tauchspulmikrofon (Bell Labs), Kugel, ca. 10 kHz – robust, mittig",
        80.0, 9000.0, false, listOf(Triple(2500.0, 4.0, 1.2)),
        drive = 0.2f, asym = 0.1, noise = 0.1f, noiseLp = 7000.0, shape = MicShape.CAPSULE),
    KRISTALL("1930er", "Kristallmikrofon (Astatic)", "Piezo-Kristall für Amateure und Tanzkapellen: hell, spitz, dünne Bässe",
        250.0, 6000.0, false, listOf(Triple(3500.0, 7.0, 2.0)),
        drive = 0.3f, asym = 0.1, noise = 0.15f, noiseLp = 8000.0, shape = MicShape.SLIM),
    MAGNETOPHON_K1("1930er", "AEG Magnetophon K1", "1935: frühes Magnetband ohne Hochfrequenz-Vormagnetisierung – verzerrt, dumpf, rauschig",
        120.0, 5000.0, false, listOf(Triple(1500.0, 2.0, 1.0)),
        drive = 0.5f, asym = 0.3, noise = 0.35f, noiseLp = 6000.0, shape = MicShape.TAPE),

    RCA77("1940er", "RCA 77 Bändchen", "Rundfunkstudio der 40er: rund, bassig, etwas gepresst vom Sendebegrenzer",
        80.0, 7000.0, false, listOf(Triple(1200.0, 2.0, 1.0)), lowShelf = 3.0,
        drive = 0.2f, asym = 0.2, noise = 0.1f, noiseLp = 7000.0, comp = 0.3, shape = MicShape.RIBBON),
    WOCHENSCHAU("1940er", "Wochenschau-Lichtton", "Kinoreportage: eng, laut, gepresst, harte Übersteuerung",
        150.0, 5000.0, true, listOf(Triple(2500.0, 5.0, 1.2)),
        drive = 0.55f, asym = 0.2, noise = 0.3f, noiseLp = 6000.0, clip = true, comp = 0.8, shape = MicShape.CAMERA),
    SHURE55("1940er", "Shure 55 Unidyne", "Tauchspule der Crooner-Zeit: mittig, präsent, nah am Mund",
        100.0, 8000.0, false, listOf(Triple(3000.0, 4.0, 1.2), Triple(180.0, 2.0, 1.0)),
        drive = 0.2f, asym = 0.1, noise = 0.06f, noiseLp = 8000.0, shape = MicShape.ELVIS),
    FELDFUNK("1940er", "Feldfunk / Funkgerät", "Militärfunk: extrem eng, gepresst, verzerrt, Rauschen",
        400.0, 2800.0, true, listOf(Triple(1500.0, 6.0, 2.0)),
        drive = 0.6f, asym = 0.3, noise = 0.4f, noiseLp = 4000.0, comp = 0.7, shape = MicShape.PHONE),
    DRAHTTON("1940er", "Drahttonrecorder (Webster)", "1946: Aufnahme auf Stahldraht – dumpf, schwankend, rauschig",
        150.0, 4500.0, false, listOf(Triple(2000.0, 3.0, 1.0)),
        drive = 0.35f, asym = 0.2, noise = 0.35f, noiseLp = 5000.0, shape = MicShape.TAPE),
    TELEFON40("1940er", "Telefonhörer (Kohlekapsel)", "Ferngespräch der 40er: Kohlekapsel im Hörer, Telefonband 300–3400 Hz",
        300.0, 3400.0, true, listOf(Triple(1800.0, 5.0, 1.5)),
        drive = 0.45f, asym = 0.5, noise = 0.25f, noiseLp = 4000.0, carbon = 0.3, shape = MicShape.PHONE),

    U47("1950er", "Neumann U 47 + Magnetband", "Röhrenmikrofon aufs Studioband: voll, seidige Höhen, leichte Bandsättigung",
        40.0, 12000.0, false, listOf(Triple(5000.0, 3.0, 0.9)), lowShelf = 1.0,
        drive = 0.3f, asym = 0.1, noise = 0.08f, noiseLp = 10000.0, shape = MicShape.BOTTLE),
    AURICON("1950er", "Auricon 16-mm-Lichtton", "Fernsehnachrichten auf 16 mm mit Lichttonspur: mittig, rauschig, begrenzt",
        150.0, 6000.0, false, listOf(Triple(2000.0, 4.0, 1.0)),
        drive = 0.4f, asym = 0.15, noise = 0.25f, noiseLp = 7000.0, clip = true, comp = 0.5, shape = MicShape.CAMERA),
    ROEHRENRADIO("1950er", "Röhrenradio", "Wohnzimmerradio der 50er: warm, dumpf, leicht verzerrt",
        120.0, 6500.0, false, listOf(Triple(350.0, 3.0, 1.2), Triple(1200.0, 2.0, 1.0)),
        drive = 0.3f, asym = 0.4, noise = 0.1f, noiseLp = 5000.0, comp = 0.3, shape = MicShape.RADIO),
    C12("1950er", "AKG C12 Röhrenkondensator", "1953: seidig, luftig, sehr detailreich – eine Studio-Legende",
        30.0, 15000.0, false, listOf(Triple(8000.0, 3.0, 1.0)),
        drive = 0.15f, asym = 0.1, noise = 0.05f, noiseLp = 12000.0, shape = MicShape.GRILL),
    EV664("1950er", "Electro-Voice 664 „Buchanan Hammer“", "Mitte 50er, erste Variable-D-Niere: dynamisch, mittig, präsent, unverwüstlich",
        90.0, 9000.0, false, listOf(Triple(4000.0, 4.0, 1.2)),
        drive = 0.15f, noise = 0.05f, noiseLp = 9000.0, shape = MicShape.CAPSULE),
    M260("1950er", "Beyer M 260 Bändchen", "1957: kleines Bändchen für Rundfunk und Bühne – warm, weich, sanfte Höhen",
        60.0, 11000.0, false, lowShelf = 2.0,
        drive = 0.1f, asym = 0.1, noise = 0.06f, noiseLp = 9000.0, shape = MicShape.SLIM),
    HEIMTONBAND50("1950er", "Heimtonband + Kristallmikro", "Erstes Heimtonband der 50er: hell, dünn, Bandrauschen",
        200.0, 7000.0, false, listOf(Triple(3000.0, 5.0, 1.5)),
        drive = 0.3f, asym = 0.1, noise = 0.25f, noiseLp = 8000.0, shape = MicShape.TAPE),

    MD421("1960er", "Sennheiser MD 421 + Nagra", "Reportage und Dokumentarfilm: klar, präsent, kaum Rauschen",
        70.0, 13000.0, false, listOf(Triple(5000.0, 4.0, 1.0), Triple(200.0, -2.0, 1.0)),
        drive = 0.15f, asym = 0.05, noise = 0.06f, noiseLp = 10000.0, comp = 0.2, shape = MicShape.HANDHELD),
    TONBAND60("1960er", "Heim-Tonbandgerät", "Spulentonband zu Hause: etwas dumpf, Bandrauschen, leichte Sättigung",
        80.0, 9000.0, false, listOf(Triple(3000.0, 2.0, 1.0)),
        drive = 0.35f, asym = 0.1, noise = 0.2f, noiseLp = 9000.0, shape = MicShape.TAPE),
    TRANSISTOR("1960er", "Kofferradio", "Transistorradio: dünn, blechern, kleiner Lautsprecher",
        350.0, 4500.0, true, listOf(Triple(1800.0, 6.0, 2.0)),
        drive = 0.4f, asym = 0.2, noise = 0.15f, noiseLp = 6000.0, comp = 0.4, shape = MicShape.RADIO),
    U67("1960er", "Neumann U 67 + Studioband", "Studio der 60er: warm, präsent, leichte Bandsättigung",
        35.0, 15000.0, false, listOf(Triple(6000.0, 2.0, 1.0), Triple(120.0, 2.0, 0.8)),
        drive = 0.3f, asym = 0.1, noise = 0.08f, noiseLp = 12000.0, shape = MicShape.GRILL),
    UHER("1960er", "Uher Report (Reportage)", "Tragbares Reportage-Tonband: klar, etwas mittig, leichtes Bandrauschen",
        80.0, 11000.0, false, listOf(Triple(3500.0, 3.0, 1.2)),
        drive = 0.2f, asym = 0.05, noise = 0.15f, noiseLp = 10000.0, comp = 0.2, shape = MicShape.TAPE),
    SHURE545("1960er", "Shure 545 Unidyne III", "Bühnenmikrofon der Beat-Ära: präsent, mittig, knackig",
        80.0, 12000.0, false, listOf(Triple(4500.0, 5.0, 1.2)), lowShelf = 2.0,
        drive = 0.2f, noise = 0.05f, noiseLp = 10000.0, shape = MicShape.HANDHELD),

    SUPER8("1970er", "Super-8-Tonkamera", "1973: Magnetspur mit Aussteuerungsautomatik – pumpt, rauscht in Pausen hoch",
        90.0, 7500.0, true, listOf(Triple(3500.0, 4.0, 1.2)),
        drive = 0.3f, asym = 0.1, noise = 0.25f, noiseLp = 8000.0, agc = 0.8f, shape = MicShape.CAMERA),
    MKH416("1970er", "Sennheiser MKH 416 Richtrohr", "Film und Fernsehen am Drehort: klar, präsent, leicht hohl",
        80.0, 14000.0, false, listOf(Triple(5500.0, 5.0, 1.5), Triple(9000.0, 3.0, 2.0), Triple(300.0, -2.0, 1.0)),
        drive = 0.05f, noise = 0.04f, noiseLp = 12000.0, shape = MicShape.SHOTGUN),
    KASSETTE("1970er", "Kassettenrekorder mit Einbaumikro", "Tragbarer Rekorder: Automatik pumpt kräftig, deutliches Bandrauschen",
        150.0, 7000.0, false, listOf(Triple(2500.0, 4.0, 1.2)),
        drive = 0.35f, asym = 0.1, noise = 0.25f, noiseLp = 9000.0, agc = 1f, shape = MicShape.CASSETTE),
    SM57("1970er", "Shure SM57", "Instrument und Rede: präsent, trocken, robust",
        80.0, 13000.0, false, listOf(Triple(5500.0, 5.0, 1.2), Triple(200.0, -1.0, 1.0)),
        drive = 0.1f, noise = 0.03f, noiseLp = 12000.0, shape = MicShape.HANDHELD),
    CB_FUNK("1970er", "CB-Funk", "Trucker-Funk: eng, stark komprimiert, Rauschen",
        300.0, 3000.0, true, listOf(Triple(1600.0, 6.0, 2.0)),
        drive = 0.55f, asym = 0.2, noise = 0.35f, noiseLp = 5000.0, comp = 0.8, shape = MicShape.PHONE),
    ANRUFBEANTWORTER("1970er", "Anrufbeantworter", "Endlosband im Telefon: Telefonband, Automatik, Rauschen",
        300.0, 3400.0, true, listOf(Triple(1200.0, 4.0, 1.5)),
        drive = 0.4f, asym = 0.2, noise = 0.35f, noiseLp = 4500.0, agc = 0.6f, shape = MicShape.PHONE),

    SM58("1980er", "Shure SM58 Reporter", "Handmikrofon der Reporter: präsent, nahbesprochen, robust",
        90.0, 13000.0, false, listOf(Triple(5000.0, 5.0, 1.2)), lowShelf = 3.0,
        drive = 0.1f, noise = 0.03f, noiseLp = 12000.0, shape = MicShape.HANDHELD),
    ANSTECK("1980er", "Elektret-Ansteckmikrofon", "Fernsehen der 80er: Ansteckmikro an der Brust – etwas brustig, klar",
        100.0, 15000.0, false, listOf(Triple(700.0, 3.0, 1.5), Triple(3000.0, -2.0, 1.0), Triple(10000.0, 4.0, 1.0)),
        drive = 0.05f, noise = 0.05f, noiseLp = 12000.0, shape = MicShape.LAVALIER),
    DIKTIERGERAET("1980er", "Diktiergerät (Mikrokassette)", "Mikrokassette: eng, rauschig, Automatik",
        300.0, 4000.0, true, listOf(Triple(2000.0, 5.0, 1.5)),
        drive = 0.3f, asym = 0.1, noise = 0.4f, noiseLp = 6000.0, agc = 0.9f, shape = MicShape.CASSETTE),
    VHS_CAM("1980er", "VHS-C-Camcorder (Einbaumikro)", "Familienvideo: Elektret im Gehäuse, dumpfe Höhen, Automatik pumpt",
        120.0, 10000.0, false, listOf(Triple(2500.0, 3.0, 1.2), Triple(8000.0, -4.0, 1.0)),
        drive = 0.2f, noise = 0.25f, noiseLp = 8000.0, agc = 0.8f, shape = MicShape.CAMERA),
    WALKMAN("1980er", "Walkman-Rekorder", "Kassetten-Walkman mit Aufnahme: Bandrauschen, Automatik",
        120.0, 9000.0, false, listOf(Triple(3000.0, 4.0, 1.2)),
        drive = 0.25f, asym = 0.05, noise = 0.3f, noiseLp = 9000.0, agc = 0.8f, shape = MicShape.CASSETTE),
    MD441("1980er", "Sennheiser MD 441", "Rundfunk-Dynamik: sehr neutral, präsent, sauber",
        60.0, 15000.0, false, listOf(Triple(5000.0, 2.0, 1.0)),
        drive = 0.05f, noise = 0.02f, noiseLp = 14000.0, shape = MicShape.HANDHELD),

    HI8("1990er", "Hi8-Camcorder", "Urlaubsvideo der 90er: Stereo-Elektret, klarer als VHS, leichte Automatik",
        90.0, 13000.0, false, listOf(Triple(3000.0, 2.0, 1.0)),
        drive = 0.1f, noise = 0.12f, noiseLp = 11000.0, agc = 0.5f, shape = MicShape.CAMERA),
    MINIDISC("1990er", "MiniDisc + Stereo-Mikro", "Datenreduktion (ATRAC): sauber, glatt, leicht glasige Höhen",
        50.0, 15500.0, false, listOf(Triple(10000.0, 3.0, 2.0)),
        drive = 0.05f, noise = 0.04f, noiseLp = 14000.0, shape = MicShape.LAVALIER),
    GSM("1990er", "Handy (GSM)", "Mobilfunk der 90er: Sprachcodec, eng, blechern, gepresst",
        300.0, 3400.0, true, listOf(Triple(2000.0, 4.0, 1.5)),
        drive = 0.3f, asym = 0.1, noise = 0.15f, noiseLp = 4000.0, comp = 0.6, agc = 0.4f, shape = MicShape.PHONE),

    MINIDV("2000er", "MiniDV-Camcorder", "Digitalvideo: sauber, etwas dünn in den Bässen",
        100.0, 16000.0, false, listOf(Triple(4000.0, 2.0, 1.0), Triple(150.0, -3.0, 1.0)),
        drive = 0.05f, noise = 0.08f, noiseLp = 12000.0, agc = 0.4f, shape = MicShape.CAMERA),
    HANDYVIDEO("2000er", "Handy-Video (2000er)", "Frühes Handyvideo: stark komprimierter Ton, eng, blubbernd",
        250.0, 4000.0, true, listOf(Triple(1500.0, 5.0, 1.5)),
        drive = 0.35f, asym = 0.1, noise = 0.2f, noiseLp = 4500.0, comp = 0.7, agc = 0.6f, shape = MicShape.PHONE),
    PODCAST("2000er", "USB-Podcastmikro", "Heimstudio: nah besprochen, bassig, sauber",
        60.0, 16000.0, false, listOf(Triple(5000.0, 3.0, 1.0)), lowShelf = 4.0,
        drive = 0.05f, noise = 0.03f, noiseLp = 14000.0, shape = MicShape.CAPSULE);

    companion object {
        val decades = entries.map { it.decade }.distinct()
    }
}

/** Bauformen für die Mikrofon-Icons. */
enum class MicShape { HORN, CARBON, BOTTLE, RIBBON, CAPSULE, GRILL, SLIM, ELVIS, HANDHELD, SHOTGUN, LAVALIER, RADIO, TAPE, CASSETTE, CAMERA, PHONE }

/**
 * Die Aufnahmekette für einen Look: mono rein, mono raus, Pegel wie am Eingang.
 *
 *   Pegelnachführung -> + Eigenrauschen -> Bandbreite/Resonanzen/Nahbesprechung -> Automatik
 *   -> Kohle-Prasseln -> Sättigung (gerade Obertöne) -> Lichtton-Begrenzung -> Begrenzer
 *   -> Glättung -> Gleichanteil weg -> zurück auf Eingangspegel
 */
class MicChain(private val sr: Int, look: FilmLook) {
    private val p = look.mic ?: MicProfile.CMV3
    private val drive = look.micDrive.coerceIn(0f, 1f).toDouble()
    private val noiseAmt = look.micNoise.coerceIn(0f, 1f).toDouble()
    private val agc = look.micAgc.coerceIn(0f, 1f).toDouble()
    /** Rauschsperre: leise Stellen (Eigenrauschen des Handys) werden abgesenkt, bevor die Kette sie hochzieht */
    private val gate = look.micGate.coerceIn(0f, 1f).toDouble()
    private var gateEnv = 0.0
    private var gateGain = 1.0
    private val bw = 2.0.pow(look.micBandwidth.coerceIn(-1f, 1f).toDouble())
    private val rnd = java.util.Random(23)

    private val hp = p.hp / bw
    private val lp = (p.lp * bw).coerceAtMost(sr * 0.45)
    private val band = buildList {
        add(Bq().highpass(sr, hp)); add(Bq().lowpass(sr, lp))
        if (p.steep) { add(Bq().highpass(sr, hp)); add(Bq().lowpass(sr, lp)) }
        if (p.lowShelf != 0.0) add(Bq().lowShelf(sr, 200.0, p.lowShelf))
        p.peaks.forEach { (f, g, q) -> add(Bq().peak(sr, (f * bw.pow(0.5)).coerceAtMost(sr * 0.4), g, q)) }
    }
    private val post = Bq().lowpass(sr, (lp * 1.3).coerceAtMost(sr * 0.45))
    private val noiseLp = Bq().lowpass(sr, p.noiseLp)

    // langsame Pegelnachführung: Eingang auf Arbeitspegel, am Ende wieder zurück
    private var inPow = 1e-3
    private var ride = 1.0
    // Automatik: schnell runter, langsam wieder hoch (das "Pumpen")
    private var agcEnv = 0.0
    private var agcGain = 1.0
    // Begrenzer
    private var compEnv = 0.0
    // Kohle
    private var sigEnv = 0.0
    private var fry = 0.0
    // Gleichanteil
    private var dcX = 0.0
    private var dcY = 0.0

    fun run(x: Float): Float {
        var xin = x.toDouble()
        if (gate > 0) {
            // Abwärts-Expander unter der Schwelle (−66 … −36 dBFS), weich ein- und ausgeblendet
            val a = abs(xin)
            gateEnv = if (a > gateEnv) gateEnv + (a - gateEnv) * (1.0 / (sr * 0.002)) else gateEnv * (1 - 1.0 / (sr * 0.08))
            val thr = 10.0.pow((-66.0 + 30.0 * gate) / 20.0)
            val target = if (gateEnv >= thr) 1.0 else (gateEnv / thr).pow(3.0).coerceAtLeast(0.001)
            gateGain += (target - gateGain) * (if (target > gateGain) 1.0 / (sr * 0.003) else 1.0 / (sr * 0.06))
            xin *= gateGain
        }
        // Pegelnachführung (~3 s), in Stille wird sie festgehalten
        val pw = xin * xin
        inPow += (pw - inPow) * (1.0 / (sr * 3.0))
        val rms = sqrt(inPow)
        if (rms > 0.003) ride = (0.15 / rms).coerceIn(0.25, 12.0)
        var y = xin * ride

        // Eigenrauschen vor dem Frequenzgang: wird mitgeformt wie das Signal
        y += noiseLp.run(rnd.nextGaussian()) * noiseAmt * 0.035

        for (f in band) y = f.run(y)

        if (agc > 0) {
            val a = abs(y)
            agcEnv = if (a > agcEnv) agcEnv + (a - agcEnv) * (1.0 / (sr * 0.005)) else agcEnv * (1 - 1.0 / (sr * 0.05))
            val target = (0.2 / (agcEnv + 1e-4)).coerceIn(0.3, 1.0 + 11.0 * agc)
            // runter schnell (10 ms), hoch langsam (1,5 s) -> Pumpen, Rauschen schwillt in Pausen an
            agcGain += (target - agcGain) * (if (target < agcGain) 1.0 / (sr * 0.01) else 1.0 / (sr * 1.5))
            y *= 1 + (agcGain - 1) * agc
        }

        if (p.carbon > 0) {
            sigEnv += (abs(y) - sigEnv) * (1.0 / (sr * 0.01))
            if (rnd.nextDouble() < p.carbon * 40.0 / sr) fry = (rnd.nextDouble() - 0.5) * 0.25 * p.carbon
            fry *= 0.7
            // Kohlerauschen wächst mit der Aussteuerung
            // Kohle-Prasseln folgt dem Rauschregler (0 = still)
            val nScale = (noiseAmt / p.noise.coerceAtLeast(0.01f)).coerceAtMost(2.0)
            y += (rnd.nextGaussian() * sigEnv * 0.25 * p.carbon + fry) * nScale
        }

        if (drive > 0.01) {
            val d = 1 + 5 * drive
            val z = y + p.asym * drive * 1.5 * y * y
            y = tanh(d * z) / d * (1 + drive * 1.5)
        }

        if (p.clip) {
            val c = 0.6 - 0.3 * drive
            y = y.coerceIn(-c, c) / c * 0.6
        }

        if (p.comp > 0) {
            val a = abs(y)
            compEnv = if (a > compEnv) compEnv + (a - compEnv) * (1.0 / (sr * 0.002)) else compEnv * (1 - 1.0 / (sr * 0.2))
            val thr = 0.12
            if (compEnv > thr) {
                val g = (thr + (compEnv - thr) / 4) / compEnv   // 4:1
                y *= 1 + (g * 1.6 - 1) * p.comp                // mit Aufholverstärkung
            }
        }

        y = post.run(y)
        // Gleichanteil (von den geraden Obertönen) entfernen
        dcY = y - dcX + 0.995 * dcY
        dcX = y
        return (dcY / ride).toFloat().coerceIn(-1f, 1f)
    }

    /** RBJ-Biquads: Hoch-/Tiefpass, Glocke, Tiefenregal. */
    private class Bq {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        private fun w(sr: Int, f: Double) = 2 * PI * f.coerceIn(20.0, sr * 0.45) / sr

        fun lowpass(sr: Int, f: Double) = apply {
            val w = w(sr, f); val al = sin(w) / (2 * 0.7071); val c = cos(w)
            norm(1 + al, (1 - c) / 2, 1 - c, (1 - c) / 2, -2 * c, 1 - al)
        }

        fun highpass(sr: Int, f: Double) = apply {
            val w = w(sr, f); val al = sin(w) / (2 * 0.7071); val c = cos(w)
            norm(1 + al, (1 + c) / 2, -(1 + c), (1 + c) / 2, -2 * c, 1 - al)
        }

        fun peak(sr: Int, f: Double, db: Double, q: Double) = apply {
            val a = 10.0.pow(db / 40); val w = w(sr, f); val al = sin(w) / (2 * q); val c = cos(w)
            norm(1 + al / a, 1 + al * a, -2 * c, 1 - al * a, -2 * c, 1 - al / a)
        }

        fun lowShelf(sr: Int, f: Double, db: Double) = apply {
            val a = 10.0.pow(db / 40); val w = w(sr, f); val c = cos(w); val s = sin(w)
            val al = s / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
            norm((a + 1) + (a - 1) * c + sa,
                a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
                -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa)
        }

        private fun norm(a0: Double, nb0: Double, nb1: Double, nb2: Double, na1: Double, na2: Double) {
            b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0
        }

        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }
}
