package com.vikash.ampli.drive.obd

/**
 * Plain-language meanings for common generic (SAE) powertrain codes, in English and Hindi.
 * Manufacturer-specific codes (P1xxx, and most B/C/U codes) fall back to a category line.
 * Pure: no Android resources, so it is unit-testable.
 */
object DtcCatalog {

    private class Entry(val en: String, val hi: String)

    private val CODES: Map<String, Entry> = mapOf(
        "P0100" to Entry("Air flow sensor problem", "एयर फ्लो सेंसर में खराबी"),
        "P0101" to Entry("Air flow sensor reading out of range", "एयर फ्लो सेंसर की रीडिंग गलत"),
        "P0102" to Entry("Air flow sensor signal low", "एयर फ्लो सेंसर का सिग्नल कम"),
        "P0106" to Entry("Manifold pressure sensor reading out of range", "मैनिफोल्ड प्रेशर सेंसर की रीडिंग गलत"),
        "P0110" to Entry("Intake air temperature sensor problem", "इनटेक हवा तापमान सेंसर में खराबी"),
        "P0113" to Entry("Intake air temperature sensor signal high", "इनटेक हवा तापमान सेंसर का सिग्नल ज़्यादा"),
        "P0115" to Entry("Coolant temperature sensor problem", "कूलेंट तापमान सेंसर में खराबी"),
        "P0117" to Entry("Coolant temperature sensor signal low", "कूलेंट तापमान सेंसर का सिग्नल कम"),
        "P0118" to Entry("Coolant temperature sensor signal high", "कूलेंट तापमान सेंसर का सिग्नल ज़्यादा"),
        "P0120" to Entry("Throttle position sensor problem", "थ्रॉटल पोज़िशन सेंसर में खराबी"),
        "P0128" to Entry("Engine not warming up, check the thermostat", "इंजन गर्म नहीं हो रहा, थर्मोस्टैट जाँचें"),
        "P0130" to Entry("Oxygen sensor problem, bank 1 sensor 1", "ऑक्सीजन सेंसर में खराबी, बैंक 1 सेंसर 1"),
        "P0133" to Entry("Oxygen sensor slow to respond, bank 1 sensor 1", "ऑक्सीजन सेंसर धीमा, बैंक 1 सेंसर 1"),
        "P0135" to Entry("Oxygen sensor heater problem, bank 1 sensor 1", "ऑक्सीजन सेंसर हीटर में खराबी, बैंक 1 सेंसर 1"),
        "P0141" to Entry("Oxygen sensor heater problem, bank 1 sensor 2", "ऑक्सीजन सेंसर हीटर में खराबी, बैंक 1 सेंसर 2"),
        "P0171" to Entry("Engine running too lean, bank 1", "इंजन में हवा ज़्यादा, ईंधन कम (बैंक 1)"),
        "P0172" to Entry("Engine running too rich, bank 1", "इंजन में ईंधन ज़्यादा (बैंक 1)"),
        "P0174" to Entry("Engine running too lean, bank 2", "इंजन में हवा ज़्यादा, ईंधन कम (बैंक 2)"),
        "P0175" to Entry("Engine running too rich, bank 2", "इंजन में ईंधन ज़्यादा (बैंक 2)"),
        "P0201" to Entry("Fuel injector problem, cylinder 1", "सिलिंडर 1 के इंजेक्टर में खराबी"),
        "P0217" to Entry("Engine overheating", "इंजन ज़्यादा गर्म हो रहा है"),
        "P0230" to Entry("Fuel pump circuit problem", "फ्यूल पंप सर्किट में खराबी"),
        "P0300" to Entry("Engine misfire, several cylinders", "इंजन मिसफायर, कई सिलिंडर"),
        "P0325" to Entry("Knock sensor problem", "नॉक सेंसर में खराबी"),
        "P0335" to Entry("Crankshaft position sensor problem", "क्रैंकशाफ्ट पोज़िशन सेंसर में खराबी"),
        "P0340" to Entry("Camshaft position sensor problem", "कैमशाफ्ट पोज़िशन सेंसर में खराबी"),
        "P0400" to Entry("Exhaust gas recirculation (EGR) problem", "EGR सिस्टम में खराबी"),
        "P0401" to Entry("EGR flow too low", "EGR फ्लो कम"),
        "P0420" to Entry("Catalytic converter not working well, bank 1", "कैटेलिटिक कन्वर्टर ठीक से काम नहीं कर रहा (बैंक 1)"),
        "P0430" to Entry("Catalytic converter not working well, bank 2", "कैटेलिटिक कन्वर्टर ठीक से काम नहीं कर रहा (बैंक 2)"),
        "P0440" to Entry("Fuel vapour (EVAP) system problem", "फ्यूल वेपर (EVAP) सिस्टम में खराबी"),
        "P0442" to Entry("Small fuel vapour leak, check the fuel cap", "फ्यूल वेपर में छोटा लीक, फ्यूल कैप जाँचें"),
        "P0455" to Entry("Large fuel vapour leak, check the fuel cap", "फ्यूल वेपर में बड़ा लीक, फ्यूल कैप जाँचें"),
        "P0456" to Entry("Very small fuel vapour leak", "फ्यूल वेपर में बहुत छोटा लीक"),
        "P0500" to Entry("Vehicle speed sensor problem", "स्पीड सेंसर में खराबी"),
        "P0505" to Entry("Idle speed control problem", "आइडल स्पीड कंट्रोल में खराबी"),
        "P0506" to Entry("Idle speed lower than expected", "आइडल स्पीड कम है"),
        "P0507" to Entry("Idle speed higher than expected", "आइडल स्पीड ज़्यादा है"),
        "P0562" to Entry("System voltage low", "सिस्टम वोल्टेज कम"),
        "P0563" to Entry("System voltage high", "सिस्टम वोल्टेज ज़्यादा"),
        "P0600" to Entry("Engine computer communication problem", "इंजन कंप्यूटर में संचार की खराबी"),
        "P0700" to Entry("Transmission control problem", "ट्रांसमिशन कंट्रोल में खराबी"),
        "P0715" to Entry("Transmission input speed sensor problem", "ट्रांसमिशन इनपुट स्पीड सेंसर में खराबी"),
    )

    /** Meaning of [code] in Hindi when [hindi], else English. */
    fun describe(code: String, hindi: Boolean): String {
        val c = code.uppercase()
        CODES[c]?.let { return if (hindi) it.hi else it.en }
        // P0301…P0312: misfire in one cylinder.
        if (c.length == 5 && c.startsWith("P030")) {
            val cyl = c.substring(3).toIntOrNull()
            if (cyl != null && cyl in 1..12) {
                return if (hindi) "सिलिंडर $cyl में इंजन मिसफायर" else "Engine misfire in cylinder $cyl"
            }
        }
        if (c.length == 5 && c.startsWith("P020")) {
            val cyl = c.substring(3).toIntOrNull()
            if (cyl != null && cyl in 1..12) {
                return if (hindi) "सिलिंडर $cyl के इंजेक्टर में खराबी" else "Fuel injector problem, cylinder $cyl"
            }
        }
        return category(c, hindi)
    }

    private fun category(c: String, hindi: Boolean): String {
        val (en, hi) = when {
            c.startsWith("P1") || c.startsWith("P3") -> "Manufacturer-specific engine code" to "कंपनी का अपना इंजन कोड"
            c.startsWith("P00") || c.startsWith("P01") || c.startsWith("P02") -> "Fuel and air system problem" to "ईंधन और हवा सिस्टम में खराबी"
            c.startsWith("P03") -> "Ignition or misfire problem" to "इग्निशन या मिसफायर की खराबी"
            c.startsWith("P04") -> "Emission control problem" to "प्रदूषण नियंत्रण सिस्टम में खराबी"
            c.startsWith("P05") -> "Speed, idle or sensor input problem" to "स्पीड, आइडल या सेंसर की खराबी"
            c.startsWith("P06") -> "Engine computer problem" to "इंजन कंप्यूटर में खराबी"
            c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") -> "Gearbox problem" to "गियरबॉक्स में खराबी"
            c.startsWith("C") -> "Chassis problem (brakes, ABS, steering)" to "चेसिस (ब्रेक, ABS, स्टीयरिंग) में खराबी"
            c.startsWith("B") -> "Body problem (airbags, lights, doors)" to "बॉडी (एयरबैग, लाइट, दरवाज़े) में खराबी"
            c.startsWith("U") -> "Wiring or module communication problem" to "वायरिंग या मॉड्यूल संचार की खराबी"
            else -> "Unknown fault" to "अज्ञात खराबी"
        }
        return if (hindi) hi else en
    }
}
