package com.vikash.ampli.drive.voice.tts

/**
 * JNI entry points of libamplitts (app/src/main/cpp: Flite, CMU's small speech synthesiser,
 * BSD-style licence). Only [AmpliVoice] calls these, from one thread at a time. Public and not
 * internal so the JNI names stay exactly Java_..._FliteNative_loadVoice etc.
 */
object FliteNative {
    /** Loads the native library once; false when it is missing (JVM tests, an x86 emulator). */
    val loaded: Boolean by lazy {
        try {
            System.loadLibrary("amplitts")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** A voice from a .flitevox file, or 0 when it could not be read. */
    @JvmStatic external fun loadVoice(path: String): Long

    @JvmStatic external fun freeVoice(handle: Long)

    /**
     * 16-bit mono PCM for [utf8] (UTF-8 text), sample rate in [rateOut][0]; null on failure.
     * [stretch] > 1 speaks slower.
     */
    @JvmStatic external fun synth(handle: Long, utf8: ByteArray, stretch: Float, rateOut: IntArray): ShortArray?
}
