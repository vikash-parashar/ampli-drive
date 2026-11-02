/*
 * Ampli voice engine: a thin JNI layer over Flite (flite/, CMU BSD-style licence).
 * Kotlin side: com.vikash.ampli.drive.voice.tts.FliteNative.
 *
 * Flite reports fatal errors through cst_error(), which longjmps to cst_errjmp when it is set
 * and otherwise calls exit(). Every call into Flite below therefore runs under a setjmp guard,
 * so a bad voice file or odd text fails that one call instead of killing the app. Calls are
 * serialised with a mutex (cst_errjmp is a global).
 */
#include <jni.h>
#include <pthread.h>
#include <setjmp.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "flite.h"
#include "cst_error.h"
#include "usenglish.h"
#include "cmu_lex.h"
#include "cst_cg.h"

void cmu_indic_lang_init(cst_voice *v);
cst_lexicon *cmu_indic_lex_init(void);
void cst_cg_unload_voice(cst_voice *vox, cst_val *voice_list);

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static int initialised = 0;

static void ensure_init(void) {
    if (initialised) return;
    flite_init();
    /* English voices name their language "eng" (older ones "usenglish"); Indic voices
       "cmu_indic_lang" and use the English lexicon for Latin-script words. */
    flite_add_lang("eng", usenglish_init, cmu_lex_init);
    flite_add_lang("usenglish", usenglish_init, cmu_lex_init);
    flite_add_lang("cmu_indic_lang", cmu_indic_lang_init, cmu_indic_lex_init);
    initialised = 1;
}

JNIEXPORT jlong JNICALL
Java_com_vikash_ampli_drive_voice_tts_FliteNative_loadVoice(JNIEnv *env, jclass cls, jstring path) {
    (void) cls;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    if (p == NULL) return 0;
    cst_voice *volatile voice = NULL;
    pthread_mutex_lock(&lock);
    jmp_buf here;
    if (setjmp(here) == 0) {
        cst_errjmp = &here;
        ensure_init();
        voice = flite_voice_load(p);
    } else {
        voice = NULL;
    }
    cst_errjmp = NULL;
    pthread_mutex_unlock(&lock);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong) (intptr_t) voice;
}

JNIEXPORT void JNICALL
Java_com_vikash_ampli_drive_voice_tts_FliteNative_freeVoice(JNIEnv *env, jclass cls, jlong handle) {
    (void) env;
    (void) cls;
    cst_voice *voice = (cst_voice *) (intptr_t) handle;
    if (voice == NULL) return;
    pthread_mutex_lock(&lock);
    jmp_buf here;
    if (setjmp(here) == 0) {
        cst_errjmp = &here;
        cst_cg_unload_voice(voice, NULL);
    }
    cst_errjmp = NULL;
    pthread_mutex_unlock(&lock);
}

/*
 * Speaks [utf8] (UTF-8 bytes) with [handle] and returns 16-bit mono PCM; out[0] gets the sample
 * rate. [stretch] > 1 speaks slower. Null when synthesis failed.
 */
JNIEXPORT jshortArray JNICALL
Java_com_vikash_ampli_drive_voice_tts_FliteNative_synth(JNIEnv *env, jclass cls, jlong handle, jbyteArray utf8,
                                                       jfloat stretch, jintArray rateOut) {
    (void) cls;
    cst_voice *voice = (cst_voice *) (intptr_t) handle;
    if (voice == NULL || utf8 == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, utf8);
    char *text = (char *) malloc((size_t) n + 1);
    if (text == NULL) return NULL;
    (*env)->GetByteArrayRegion(env, utf8, 0, n, (jbyte *) text);
    text[n] = '\0';

    cst_wave *volatile wave = NULL;
    pthread_mutex_lock(&lock);
    jmp_buf here;
    if (setjmp(here) == 0) {
        cst_errjmp = &here;
        if (stretch > 0.3f && stretch < 3.0f) {
            feat_set_float(voice->features, "duration_stretch", stretch);
        }
        wave = flite_text_to_wave(text, voice);
    } else {
        wave = NULL;
    }
    cst_errjmp = NULL;
    pthread_mutex_unlock(&lock);
    free(text);
    if (wave == NULL) return NULL;

    jshortArray out = NULL;
    int samples = wave->num_samples;
    if (samples > 0 && wave->num_channels == 1) {
        out = (*env)->NewShortArray(env, samples);
        if (out != NULL) {
            (*env)->SetShortArrayRegion(env, out, 0, samples, (const jshort *) wave->samples);
        }
    }
    if (rateOut != NULL && (*env)->GetArrayLength(env, rateOut) > 0) {
        jint rate = wave->sample_rate;
        (*env)->SetIntArrayRegion(env, rateOut, 0, 1, &rate);
    }
    delete_wave(wave);
    return out;
}
