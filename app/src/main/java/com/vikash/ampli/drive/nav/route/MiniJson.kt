package com.vikash.ampli.drive.nav.route

/**
 * A small JSON reader for routing responses: objects become [Map], arrays [List], numbers
 * [Double], plus [String], [Boolean] and null. Plain Kotlin on purpose: org.json is only a stub
 * in local unit tests, and the parser must be tested against a real OSRM sample.
 */
object MiniJson {

    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val r = Reader(text)
        r.skipSpace()
        val value = r.value()
        r.skipSpace()
        if (!r.done()) throw ParseException("Trailing data at ${r.pos}")
        return value
    }

    private class Reader(private val s: String) {
        var pos = 0

        fun done() = pos >= s.length

        fun skipSpace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipSpace()
            if (done()) throw ParseException("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseException("Unexpected '$c' at $pos")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, pos)) throw ParseException("Bad literal at $pos")
            pos += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            pos++ // {
            val out = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                pos++
                return out
            }
            while (true) {
                skipSpace()
                if (peek() != '"') throw ParseException("Expected key at $pos")
                val key = string()
                skipSpace()
                expect(':')
                out[key] = value()
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return out
                    }
                    else -> throw ParseException("Expected , or } at $pos")
                }
            }
        }

        private fun array(): List<Any?> {
            pos++ // [
            val out = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                pos++
                return out
            }
            while (true) {
                out += value()
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return out
                    }
                    else -> throw ParseException("Expected , or ] at $pos")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (done()) throw ParseException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (done()) throw ParseException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw ParseException("Bad number at $start")
        }

        private fun peek(): Char = if (done()) '\u0000' else s[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw ParseException("Expected '$c' at $pos")
            pos++
        }
    }
}

/** Typed helpers over [MiniJson] output. */
@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?>? = this as? Map<String, Any?>

internal fun Any?.arr(): List<Any?>? = this as? List<Any?>

internal fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.trim()?.ifEmpty { null }

internal fun Map<String, Any?>.num(key: String): Double? = this[key] as? Double
