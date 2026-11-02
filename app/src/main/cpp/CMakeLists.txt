# Ampli voice engine: Flite (flite/, CMU BSD-style licence, see flite/COPYING) + JNI glue.
cmake_minimum_required(VERSION 3.22.1)
project(amplitts C)

set(F ${CMAKE_CURRENT_SOURCE_DIR}/flite)

file(GLOB CORE
    ${F}/src/cg/*.c
    ${F}/src/hrg/*.c
    ${F}/src/lexicon/*.c
    ${F}/src/regex/*.c
    ${F}/src/speech/*.c
    ${F}/src/stats/*.c
    ${F}/src/synth/*.c
    ${F}/src/utils/*.c
    ${F}/src/wavesynth/*.c
    ${F}/src/audio/*.c
    ${F}/lang/usenglish/*.c
    ${F}/lang/cmu_indic_lang/*.c
    ${F}/lang/cmu_indic_lex/*.c
)
# cmulex: these five; cmu_lex_data.c and cmu_lex_entries.c #include the other data files.
set(CMULEX
    ${F}/lang/cmulex/cmu_lts_rules.c
    ${F}/lang/cmulex/cmu_lts_model.c
    ${F}/lang/cmulex/cmu_lex.c
    ${F}/lang/cmulex/cmu_lex_entries.c
    ${F}/lang/cmulex/cmu_lex_data.c
    ${F}/lang/cmulex/cmu_postlex.c
)

add_library(amplitts SHARED ampli_tts.c ${CORE} ${CMULEX})
target_include_directories(amplitts PRIVATE
    ${F}/include ${F}/src/regex ${F}/src/audio ${F}/src/cg ${F}/lang/usenglish ${F}/lang/cmulex ${F}/lang/cmu_indic_lang)
# No audio device or sockets: the app plays the samples itself.
target_compile_definitions(amplitts PRIVATE CST_AUDIO_NONE CST_NO_SOCKETS)
target_compile_options(amplitts PRIVATE -O2 -w -fvisibility=hidden -ffunction-sections -fdata-sections)
# 16 KB pages (Play requires it for arm64 on Android 15+); drop unused code; only JNI symbols exported.
target_link_options(amplitts PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections -Wl,--exclude-libs,ALL)
target_link_libraries(amplitts m)
