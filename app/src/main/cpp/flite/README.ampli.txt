Flite (CMU's "festival-lite" speech synthesiser), vendored for Ampli's bundled voice engine.

Upstream: https://github.com/festvox/flite at commit 6c9f20dc915b17f5619340069889db0aa007fcdc
(2022-05-16, Flite 2.3). Licence: COPYING (BSD-style, free for commercial use; the few files with
other permissive licences are listed there). GPL appears only in upstream's configure scripts,
which are not included here.

Only the parts the app needs are copied, UNMODIFIED: include/, src/ (core; audio limited to the
"none" driver, utils to the stdio/posix variants), lang/usenglish, lang/cmulex, lang/cmu_indic_lang
and lang/cmu_indic_lex. Built by ../CMakeLists.txt; the JNI glue is ../ampli_tts.c (Ampli's own code).
Voices (.flitevox) are not here: they come from the :voicemodels asset pack or are downloaded.
