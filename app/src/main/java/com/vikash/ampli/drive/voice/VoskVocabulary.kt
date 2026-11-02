package com.vikash.ampli.drive.voice

import android.util.Log
import com.sun.jna.NativeLibrary
import org.vosk.Model
import java.util.concurrent.ConcurrentHashMap

/**
 * Which words a loaded Vosk model knows, through libvosk's own vosk_model_find_word (the Java
 * binding does not wrap it, so it is called through JNA). Used to fit Roman Hindi phrases to the
 * English model ([HinglishSpelling]). When the lookup is not available, or plainly not working
 * (it does not even know "play"), [knows] returns null and the grammar is used unchanged.
 */
object VoskVocabulary {
    private const val TAG = "VoskVocabulary"
    private val cache = ConcurrentHashMap<String, Boolean>()
    private var cachedFor: Model? = null
    private var working: Boolean? = null

    @Synchronized
    fun knows(model: Model): ((String) -> Boolean)? {
        if (cachedFor !== model) {
            cachedFor = model
            cache.clear()
            working = null
        }
        if (working == null) {
            // Words every English model has: if these are "unknown" the lookup is broken.
            working = try {
                find(model, "play") && find(model, "music")
            } catch (e: Throwable) {
                Log.w(TAG, "vocabulary lookup unavailable", e)
                false
            }
        }
        if (working != true) return null
        return { word -> cache.getOrPut(word) { runCatching { find(model, word) }.getOrDefault(true) } }
    }

    private fun find(model: Model, word: String): Boolean {
        val fn = NativeLibrary.getInstance("vosk").getFunction("vosk_model_find_word")
        return fn.invokeInt(arrayOf<Any>(model.pointer, word)) >= 0
    }
}
