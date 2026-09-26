package com.volkskamera.app.render

/** Bauart eines Mikrofons bzw. der Aufnahmekette – zum Einordnen und Sortieren. */
enum class MicType(val label: String) {
    AKUSTISCH("Akustisch (Trichter)"),
    KOHLE("Kohlemikrofon"),
    KONDENSATOR("Kondensator"),
    TAUCHSPULE("Tauchspule (dynamisch)"),
    BAENDCHEN("Bändchen"),
    KRISTALL("Kristall (Piezo)"),
    ELEKTRET("Elektret"),
    TONTRAEGER("Tonträger (Band, Draht, Lichtton)"),
    UEBERTRAGUNG("Übertragung (Radio, Telefon, Funk)"),
}

/** Steckbrief: Bauart, Richtcharakteristik und typischer Einsatz. */
class MicInfo(val type: MicType, val pattern: String, val use: String)

private val INFO: Map<MicProfile, MicInfo> = mapOf(
    MicProfile.PHONOGRAPH to MicInfo(MicType.AKUSTISCH, "Trichter (gerichtet)", "Walzenaufnahmen zu Hause und im Studio"),
    MicProfile.GRAMMOPHON to MicInfo(MicType.AKUSTISCH, "Trichter (gerichtet)", "Schellack-Studio vor der Elektrifizierung (bis 1925)"),
    MicProfile.TRICHTER to MicInfo(MicType.AKUSTISCH, "Trichter (gerichtet)", "Frühe Schallplatte, Gesang und Solisten"),
    MicProfile.KOHLE to MicInfo(MicType.KOHLE, "Kugel", "Rundfunk der 1920er"),
    MicProfile.CMV3 to MicInfo(MicType.KONDENSATOR, "Kugel", "Rundfunk und Konzertübertragung, Röhrenverstärker im Gehäuse"),
    MicProfile.MARCONI_SYKES to MicInfo(MicType.TAUCHSPULE, "Kugel", "BBC-Studio Savoy Hill, erste Rundfunkjahre"),
    MicProfile.WE394 to MicInfo(MicType.KONDENSATOR, "Kugel", "Rundfunk, elektrische Schallplatte, erste Tonfilme"),
    MicProfile.LICHTTON30 to MicInfo(MicType.TONTRAEGER, "Kugel (Tauchspule)", "Tonfilmstudio mit Lichttonspur"),
    MicProfile.RCA44 to MicInfo(MicType.BAENDCHEN, "Acht", "Rundfunk, Big Band, Sprecher"),
    MicProfile.VOLKSEMPFAENGER to MicInfo(MicType.UEBERTRAGUNG, "–", "Radiohören über Mittelwelle"),
    MicProfile.WE618 to MicInfo(MicType.TAUCHSPULE, "Kugel", "Tonfilm, Rundfunk, Beschallung"),
    MicProfile.KRISTALL to MicInfo(MicType.KRISTALL, "Kugel", "Amateure, Tanzkapellen, später Mundharmonika-Blues"),
    MicProfile.MAGNETOPHON_K1 to MicInfo(MicType.TONTRAEGER, "–", "Rundfunkarchiv und Mitschnitte auf frühem Magnetband"),
    MicProfile.RCA77 to MicInfo(MicType.BAENDCHEN, "Niere / Acht (umschaltbar)", "Rundfunkstudio, Talk und Nachrichten"),
    MicProfile.WOCHENSCHAU to MicInfo(MicType.TONTRAEGER, "–", "Kinoreportage auf Lichtton"),
    MicProfile.SHURE55 to MicInfo(MicType.TAUCHSPULE, "Niere", "Crooner-Gesang, Rede, Bühne"),
    MicProfile.FELDFUNK to MicInfo(MicType.UEBERTRAGUNG, "Handmikro", "Militär- und Behördenfunk"),
    MicProfile.DRAHTTON to MicInfo(MicType.TONTRAEGER, "–", "Reportage und Heimaufnahme auf Stahldraht"),
    MicProfile.TELEFON40 to MicInfo(MicType.KOHLE, "Kugel (Hörer)", "Telefongespräch"),
    MicProfile.U47 to MicInfo(MicType.KONDENSATOR, "Niere / Kugel", "Studio, Gesang, Orchester"),
    MicProfile.AURICON to MicInfo(MicType.TONTRAEGER, "–", "Fernsehnachrichten auf 16-mm-Lichtton"),
    MicProfile.ROEHRENRADIO to MicInfo(MicType.UEBERTRAGUNG, "–", "Radiohören im Wohnzimmer"),
    MicProfile.C12 to MicInfo(MicType.KONDENSATOR, "9 Charakteristiken (fernumschaltbar)", "Studio, Gesang, Solisten"),
    MicProfile.EV664 to MicInfo(MicType.TAUCHSPULE, "Niere (Variable-D)", "Bühne, Rundfunk, Beschallung"),
    MicProfile.M260 to MicInfo(MicType.BAENDCHEN, "Hyperniere", "Rundfunk und Bühne"),
    MicProfile.HEIMTONBAND50 to MicInfo(MicType.KRISTALL, "Kugel", "Heimaufnahme auf Tonband"),
    MicProfile.MD421 to MicInfo(MicType.TAUCHSPULE, "Niere", "Reportage, Dokumentarfilm, Instrumente"),
    MicProfile.TONBAND60 to MicInfo(MicType.TONTRAEGER, "–", "Heimaufnahme auf Spulentonband"),
    MicProfile.TRANSISTOR to MicInfo(MicType.UEBERTRAGUNG, "–", "Radiohören unterwegs"),
    MicProfile.U67 to MicInfo(MicType.KONDENSATOR, "Niere / Kugel / Acht", "Studio der 60er, Gesang und Instrumente"),
    MicProfile.UHER to MicInfo(MicType.TONTRAEGER, "–", "Hörfunkreportage mit tragbarem Tonband"),
    MicProfile.SHURE545 to MicInfo(MicType.TAUCHSPULE, "Niere", "Bühne der Beat-Ära"),
    MicProfile.SUPER8 to MicInfo(MicType.TONTRAEGER, "Niere (Kameramikro)", "Heimfilm mit Magnetrandspur"),
    MicProfile.MKH416 to MicInfo(MicType.KONDENSATOR, "Keule (Richtrohr)", "Film und Fernsehen am Drehort, Angel"),
    MicProfile.KASSETTE to MicInfo(MicType.ELEKTRET, "Kugel", "Heimaufnahme, Interviews"),
    MicProfile.SM57 to MicInfo(MicType.TAUCHSPULE, "Niere", "Instrumente, Rednerpult"),
    MicProfile.CB_FUNK to MicInfo(MicType.UEBERTRAGUNG, "Handmikro", "CB-Funk, Trucker"),
    MicProfile.ANRUFBEANTWORTER to MicInfo(MicType.TONTRAEGER, "–", "Nachrichten auf dem Anrufbeantworter"),
    MicProfile.SM58 to MicInfo(MicType.TAUCHSPULE, "Niere", "Gesang, Reportage"),
    MicProfile.ANSTECK to MicInfo(MicType.ELEKTRET, "Kugel", "Fernsehmoderation, Interview"),
    MicProfile.DIKTIERGERAET to MicInfo(MicType.ELEKTRET, "Kugel", "Büro, Notizen"),
    MicProfile.VHS_CAM to MicInfo(MicType.ELEKTRET, "Kugel", "Familienvideo"),
    MicProfile.WALKMAN to MicInfo(MicType.ELEKTRET, "Kugel", "Aufnahmen unterwegs"),
    MicProfile.MD441 to MicInfo(MicType.TAUCHSPULE, "Superniere", "Rundfunk, Gesang"),
    MicProfile.HI8 to MicInfo(MicType.ELEKTRET, "Stereo (Kugel)", "Urlaubsvideo"),
    MicProfile.MINIDISC to MicInfo(MicType.ELEKTRET, "Stereo (Kugel)", "Field Recording, Konzertmitschnitt"),
    MicProfile.GSM to MicInfo(MicType.UEBERTRAGUNG, "Kugel", "Mobiltelefonie"),
    MicProfile.MINIDV to MicInfo(MicType.ELEKTRET, "Stereo (Kugel)", "Digitalvideo"),
    MicProfile.HANDYVIDEO to MicInfo(MicType.ELEKTRET, "Kugel", "Frühe Handyvideos"),
    MicProfile.PODCAST to MicInfo(MicType.KONDENSATOR, "Niere", "Podcast, Heimstudio"),
)

val MicProfile.info: MicInfo get() = INFO.getValue(this)
/** Übertragener Frequenzbereich des Profils als Text. */
val MicProfile.rangeText: String get() {
    fun f(v: Double) = if (v >= 1000) "${"%.1f".format(v / 1000).removeSuffix(",0").removeSuffix(".0")} kHz" else "${v.toInt()} Hz"
    return "${f(hp)} – ${f(lp)}"
}
