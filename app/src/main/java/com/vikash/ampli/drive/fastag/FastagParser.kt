package com.vikash.ampli.drive.fastag

/** What a FASTag SMS says happened. Only [Debit] counts towards toll totals. */
enum class FastagKind { Debit, Recharge, Balance }

/**
 * One parsed FASTag message. Amounts are in rupees. Any field the bank left out is null;
 * a [FastagKind.Balance] message (low-balance alert) has no [amount].
 */
data class FastagMessage(
    val kind: FastagKind,
    val amount: Double?,
    val plaza: String?,
    val balance: Double?,
    val vehicle: String?,
    val bank: String?,
)

/**
 * Reads FASTag debit, recharge and low-balance SMS from Indian banks and wallets
 * (ICICI, HDFC, SBI, Axis, Kotak, IDFC First, Paytm, Airtel, IndusInd, Bank of Baroda…).
 *
 * Banks word these very differently, so the parser looks for each piece on its own:
 * the FASTag/toll keyword, the balance ("Avl Bal Rs 1,240.50", "शेष ₹432"), the amount
 * (the first rupee figure that is not the balance), the plaza ("at KHED SHIVAPUR toll plaza"),
 * the vehicle number (Indian plate format) and the bank (from the sender or the text).
 *
 * Pure Kotlin with no Android calls, so it runs in plain JVM unit tests.
 */
object FastagParser {

    private const val NUM = """([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    private const val CUR = """(?:Rs\.?|INR|₹|रु\.?|रुपये)"""

    private val KEYWORD = Regex("""(?i)fas\s?-?tag|\btoll\b|\bNETC\b|टोल|फास्टैग|फ़ास्टैग|फास्टटैग|फास्ट\s?टैग""")

    /** Messages to ignore even when they mention FASTag. */
    private val IGNORE = Regex("""(?i)\bOTP\b|one[\s-]time\s+pass|verification code|\bKYC\b|apply (?:now|for)|buy (?:a )?fastag|offer|cashback|\bwin\b|failed|declined|unsuccessful|reversed""")

    private val DEBIT = Regex("""(?i)debit|deduct|\bpaid\b|charged|\bspent\b|toll fee|toll (?:amount|charge|txn|transaction)|trxn|\btxn\b|transaction of|has been used|crossed|कट|काटे|डेबिट|भुगतान""")
    private val CREDIT = Regex("""(?i)recharg|credited|\breload|top[\s-]?up|added to|loaded|रिचार्ज|जमा""")

    private val BALANCE = Regex(
        """(?i)(?:avl\.?|avail(?:able)?\.?|a/?c|closing|current|remaining|wallet|tag|net|updated|effective|new)?\s*""" +
            """(?:bal(?:ance)?\.?|bal)\s*(?:is|of|now|:|-|=|amt\.?)?\s*(?:is\s*)?:?\s*$CUR\s*$NUM""" +
            """|(?:शेष(?:\s*राशि)?|बैलेंस|उपलब्ध\s*राशि)\s*(?:है)?\s*:?\s*$CUR\s*$NUM""",
    )

    /** "Balance: 432" or "bal is 432.00" with no rupee sign. */
    private val BALANCE_BARE = Regex("""(?i)\bbal(?:ance)?\.?\s*(?:is|:|-|=)\s*$NUM\b""")

    private val AMOUNT = Regex("""$CUR\s*$NUM""", RegexOption.IGNORE_CASE)

    /** "65 rupees", "INR 120.00" already caught above; this is for "Rs" written after the figure. */
    private val AMOUNT_AFTER = Regex("""(?i)\b$NUM\s*(?:rupees|/-|रुपये)""")

    private val PLAZA_PATTERNS = listOf(
        // "at KHED SHIVAPUR toll plaza", "at Vashi Toll Plaza on 12-03", "at Khed Shivapur TP"
        Regex("""(?i)(?:\bat|@)\s+([A-Za-z][A-Za-z0-9 .&'()/-]{1,48}?)\s*(?:toll\s*plaza|toll\s*naka|plaza|\bTP\b|\btoll\b|\bFP\b)"""),
        // "Toll Plaza: KHED SHIVAPUR", "Plaza Name - Vashi"
        Regex("""(?i)(?:toll\s*plaza|plaza)(?:\s*name)?\s*[:\-]\s*([A-Za-z][A-Za-z0-9 .&'()/-]{1,48}?)(?=\s*(?:\bon\b|\bat\b|\bfor\b|[,.;|]|\bavl|\bbal|\bveh|\bref|\btxn|$))"""),
        // "toll fee of INR 120 deducted at KHED SHIVAPUR on..." (no "plaza" word)
        Regex("""(?:\b[Aa]t|@)\s+([A-Z][A-Za-z0-9 .&'-]{2,40}?)(?=\s+(?:on|for|via|dated)\b|\s*[,.;|]|\s+avl|\s+bal|\s*$)"""),
        // Hindi: "खेड शिवापुर टोल प्लाजा पर"
        Regex("""((?:[\p{L}\p{M}]+\s+){0,3}[\p{L}\p{M}]+)\s*टोल\s*प्ला(?:ज़|ज)ा"""),
    )

    /** Indian state/UT registration codes, to keep plates from matching random IDs. */
    private val STATE_CODES = setOf(
        "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DL", "DN", "GA", "GJ", "HP", "HR", "JH", "JK",
        "KA", "KL", "LA", "LD", "MH", "ML", "MN", "MP", "MZ", "NL", "OD", "OR", "PB", "PY", "RJ", "SK",
        "TG", "TN", "TR", "TS", "UK", "UP", "UA", "WB",
    )
    private val PLATE = Regex("""\b([A-Z]{2})[ -]?([0-9]{1,2})[ -]?([A-Z]{0,3})[ -]?([0-9]{4})\b""")
    private val BH_PLATE = Regex("""\b([0-9]{2})[ -]?BH[ -]?([0-9]{4})[ -]?([A-Z]{1,2})\b""")

    /** Ordered: the first hit wins, so longer names come before their prefixes. */
    private val BANKS = listOf(
        "IDFC First" to Regex("""(?i)IDFC|IDFCFB"""),
        "ICICI" to Regex("""(?i)ICICI"""),
        "HDFC" to Regex("""(?i)HDFC"""),
        "SBI" to Regex("""(?i)\bSBI|State Bank|SBIFAS"""),
        "Axis" to Regex("""(?i)\bAxis|AXISBK|AXISFT"""),
        "Kotak" to Regex("""(?i)Kotak|KOTAKB"""),
        "Paytm" to Regex("""(?i)Paytm|PPBL|PYTM"""),
        "Airtel Payments" to Regex("""(?i)Airtel"""),
        "IndusInd" to Regex("""(?i)IndusInd|INDUSB"""),
        "Bank of Baroda" to Regex("""(?i)Bank of Baroda|\bBOB\b|BOBFTG|BOBTXN|Baroda"""),
        "Equitas" to Regex("""(?i)Equitas"""),
        "Federal" to Regex("""(?i)Federal Bank|FEDBNK"""),
        "Yes Bank" to Regex("""(?i)Yes ?Bank|YESBNK"""),
        "PNB" to Regex("""(?i)\bPNB\b|Punjab National"""),
        "Union Bank" to Regex("""(?i)Union Bank|UBOI"""),
        "Canara" to Regex("""(?i)Canara"""),
        "AU Bank" to Regex("""(?i)\bAU Small|AUBANK"""),
        "IDBI" to Regex("""(?i)IDBI"""),
        "Bank of Maharashtra" to Regex("""(?i)Bank of Maharashtra|MAHABK"""),
        "IHMCL" to Regex("""(?i)IHMCL|NHAI"""),
    )

    /** True when [text] looks like a FASTag message at all (cheap pre-check). */
    fun mentionsFastag(text: String): Boolean = KEYWORD.containsMatchIn(text)

    /**
     * Parses one SMS. [sender] is the notification title (sender id or contact name, e.g.
     * "AX-ICICIT" or "HDFC Bank"), used only to name the bank. Returns null for anything
     * that is not a FASTag debit, recharge or balance message.
     */
    fun parse(text: String, sender: String? = null): FastagMessage? {
        val body = text.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (body.isEmpty() || !KEYWORD.containsMatchIn(body)) return null
        if (IGNORE.containsMatchIn(body)) return null

        val balanceMatch = BALANCE.find(body)
        val balance = balanceMatch?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }?.let(::toRupees)
            ?: BALANCE_BARE.find(body)?.groupValues?.get(1)?.let(::toRupees)
        val balanceRange = balanceMatch?.range

        val amount = AMOUNT.findAll(body)
            .firstOrNull { m -> balanceRange == null || m.range.first !in balanceRange }
            ?.groupValues?.get(1)?.let(::toRupees)
            ?: AMOUNT_AFTER.find(body)?.groupValues?.get(1)?.let(::toRupees)

        val isDebit = DEBIT.containsMatchIn(body)
        val isCredit = CREDIT.containsMatchIn(body)
        val kind = when {
            amount != null && isDebit && !(isCredit && !body.contains("toll", ignoreCase = true)) -> FastagKind.Debit
            amount != null && isCredit -> FastagKind.Recharge
            balance != null -> FastagKind.Balance
            else -> return null
        }

        return FastagMessage(
            kind = kind,
            amount = if (kind == FastagKind.Balance) null else amount,
            plaza = if (kind == FastagKind.Debit) findPlaza(body) else null,
            balance = balance,
            vehicle = findVehicle(body),
            bank = findBank(sender, body),
        )
    }

    fun findVehicle(text: String): String? {
        val upper = text.uppercase()
        PLATE.findAll(upper).forEach { m ->
            val (state, district, series, number) = m.destructured
            if (state in STATE_CODES) return state + district + series + number
        }
        BH_PLATE.find(upper)?.let { m ->
            val (year, number, series) = m.destructured
            return year + "BH" + number + series
        }
        return null
    }

    fun findBank(sender: String?, text: String): String? {
        for (source in listOfNotNull(sender, text)) {
            BANKS.firstOrNull { it.second.containsMatchIn(source) }?.let { return it.first }
        }
        return null
    }

    fun findPlaza(text: String): String? {
        for (pattern in PLAZA_PATTERNS) {
            for (m in pattern.findAll(text)) {
                val name = cleanPlaza(m.groupValues[1]) ?: continue
                return name
            }
        }
        return null
    }

    private val PLAZA_NOISE = Regex(
        """(?i)^(?:the|your|fastag|tag|a/?c|account|vehicle|toll|plaza|on|at|for|of|with|in|via|by)\s+""",
    )
    private val PLAZA_TRAILING = Regex("""(?i)\s+(?:toll|plaza|tp|fp|on|at|the|for|and)$""")
    private val PLAZA_REJECT = Regex("""(?i)^(?:rs|inr|bal|balance|avl|available|fastag|tag|toll|plaza|your|txn|ref|date|time)$|[0-9]{3,}|\b(?:debited|deducted|paid|rs|inr)\b""")

    private fun cleanPlaza(raw: String): String? {
        var s = raw.trim().trim('-', ':', ',', '.', '(', ')', '/').trim()
        // Hindi: keep what follows the last postposition ("आपके फास्टैग से खेड शिवापुर" -> "खेड शिवापुर").
        listOf(" से ", " पर ", " को ").forEach { p -> s.lastIndexOf(p).takeIf { it >= 0 }?.let { s = s.substring(it + p.length) } }
        s = s.removePrefix("से ").removePrefix("पर ")
        s.lastIndexOf(" at ", ignoreCase = true).takeIf { it >= 0 }?.let { s = s.substring(it + 4) }
        repeat(3) { s = s.replace(PLAZA_NOISE, "") }
        repeat(3) { s = s.replace(PLAZA_TRAILING, "") }
        s = s.trim()
        if (s.length < 2 || PLAZA_REJECT.containsMatchIn(s)) return null
        if (s.any { it.isDigit() } && s.count { it.isLetter() } < 3) return null
        return titleCase(s)
    }

    /** "KHED SHIVAPUR" -> "Khed Shivapur"; mixed-case names are kept as written. */
    private fun titleCase(s: String): String {
        val letters = s.filter { it.isLetter() && it.code < 128 }
        if (letters.isEmpty() || letters.any { it.isLowerCase() } && letters.any { it.isUpperCase() }) return s
        return s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    private fun toRupees(s: String): Double? = s.replace(",", "").toDoubleOrNull()
}
