package com.vikash.ampli.drive.voice

import java.util.Locale

/**
 * Recorded clips for the lines that used to be phone voice only: Hey Ampli's small talk
 * ([ChatLines]), the jokes ([Jokes]) and the plain Haryanvi replies ([DesiReplies]). They are in
 * the voice-pack manifest (voice/lines.json, groups "Chat", "Jokes" and "Replies") so the
 * voice-pack workflow records them in both voices. Plain Kotlin, unit-tested.
 *
 * Ids: "chat_<topic in snake case>_<variant, from 01>", "joke_<nn>" and "desi_<string name>".
 * Like every clip the app only plays one whose recorded hash still matches the line's text, so an
 * edited line goes back to the phone voice until it is recorded again. A line with something
 * filled in at run time (a place, a number, the date or the weather) has no clip and stays TTS.
 */
object BankClips {

    const val GROUP_CHAT = "Chat"
    const val GROUP_JOKES = "Jokes"
    const val GROUP_REPLIES = "Replies"

    fun chatId(topic: ChatLines.Topic, index: Int): String =
        "chat_" + snake(topic.name) + "_" + (index + 1).toString().padStart(2, '0')

    fun jokeId(index: Int): String = "joke_" + (index + 1).toString().padStart(2, '0')

    fun desiId(name: String): String = "desi_$name"

    /** A line can be recorded as is when nothing is filled in at run time. */
    fun recordable(text: String): Boolean = !text.contains('%')

    /** Every recordable bank line, in a stable order (appended to [VoiceClips.manifest]). */
    val entries: List<VoiceClips.Entry> by lazy {
        val out = ArrayList<VoiceClips.Entry>()
        for ((topic, lines) in ChatLines.all) {
            lines.forEachIndexed { i, l ->
                if (recordable(l.deva)) out += VoiceClips.Entry(chatId(topic, i), "Chat${topic.name}", GROUP_CHAT, l.deva)
            }
        }
        Jokes.ALL.forEachIndexed { i, l ->
            if (recordable(l.deva)) out += VoiceClips.Entry(jokeId(i), "Joke", GROUP_JOKES, l.deva)
        }
        for ((name, text) in DesiReplies.LINES) {
            if (recordable(text)) out += VoiceClips.Entry(desiId(name), "Reply", GROUP_REPLIES, text)
        }
        out
    }

    /** Chat or joke line → its clip id (both its Devanagari and Latin wording point at the clip). */
    private val byLine: Map<ChatLines.Line, String> by lazy {
        val m = HashMap<ChatLines.Line, String>()
        for ((topic, lines) in ChatLines.all) {
            lines.forEachIndexed { i, l -> if (recordable(l.deva)) m.putIfAbsent(l, chatId(topic, i)) }
        }
        Jokes.ALL.forEachIndexed { i, l -> if (recordable(l.deva)) m.putIfAbsent(l, jokeId(i)) }
        m
    }

    /** Spoken text → clip id, for replies that reach the speaker as plain text. */
    private val byText: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for ((line, id) in byLine) {
            m.putIfAbsent(line.deva, id)
            m.putIfAbsent(line.roman, id)
        }
        for ((name, text) in DesiReplies.LINES) if (recordable(text)) m.putIfAbsent(text, desiId(name))
        m
    }

    /**
     * The clips for [line] said in [script]: its recording, except in plain English (the English
     * style says other words). Empty when the line has none.
     */
    fun clipsFor(line: ChatLines.Line, script: ChatLines.Script): List<String> =
        if (script == ChatLines.Script.English) emptyList() else listOfNotNull(byLine[line])

    /** Several lines said one after the other: clips only when every one of them has one. */
    fun clipsFor(lines: List<ChatLines.Line>, script: ChatLines.Script): List<String> {
        val ids = lines.map { clipsFor(it, script) }
        return if (ids.isNotEmpty() && ids.all { it.isNotEmpty() }) ids.flatten() else emptyList()
    }

    /** The clip for a reply said as plain [text] (a [DesiReplies] line, a chat or joke line), if any. */
    fun clipsFor(text: String): List<String> = listOfNotNull(byText[text])

    /**
     * How [text] is said: with the recorded [voice] when that voice is picked and every clip in
     * [clips] is bundled and up to date, else null and the phone voice (TTS) says it, exactly as
     * before the clips existed.
     */
    fun recorded(library: ClipLibrary, voice: VoicePack, text: String, language: String, clips: List<String>): Spoken? {
        if (clips.isEmpty()) return null
        val spoken = Spoken(text, language, clips)
        return if (library.plan(voice, spoken) != null) spoken else null
    }

    private fun snake(name: String): String = name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase(Locale.US)
}
