#!/usr/bin/env python3
"""Records the same few Haryanvi lines in several Indian male and female ElevenLabs voices so the
owner can compare them (run by .github/workflows/voice-samples.yml on a push to voice-samples-run).

Writes voice-samples/<voice_key>/<line>.ogg and voice-samples/samples.json. Library voices are added
to the account only for the recording and removed again afterwards, so no voice slots stay used.
"""
import json
import os
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import voice_pack as vp  # noqa: E402

OUT = os.path.join(vp.ROOT, "voice-samples")
PER_GENDER = int(os.environ.get("SAMPLES_PER_GENDER", "5"))
CURRENT = {"male": ("7b9mYhmnp0y2qSH1FnBL", "Bunty (current)"),
           "female": ("9w21nMuk8CWXIME31V1S", "Simran (current)")}
LINES = [
    ("welcome", "[cheerfully] इंजन गरज्या, जणू अखाड़े मैं पहलवान उतर्या हो! चाल, बेल्ट ला ले।",
     "इंजन गरज्या, जणू अखाड़े मैं पहलवान उतर्या हो! चाल, बेल्ट ला ले।"),
    ("speed", "[concerned] धीरे कर, कैमरा आग्गै सै, ना तो चालान का लड्डू घरां पहुँचैगा।",
     "धीरे कर, कैमरा आग्गै सै, ना तो चालान का लड्डू घरां पहुँचैगा।"),
    ("fastag", "[playfully] फास्टैग भूखा सै, रुक कै रिचार्ज का चूरमा खुवा दिए।",
     "फास्टैग भूखा सै, रुक कै रिचार्ज का चूरमा खुवा दिए।"),
]


def shared(params):
    try:
        return vp.api("GET", "/v1/shared-voices?" + urllib.parse.urlencode(params)).get("voices", [])
    except urllib.error.HTTPError as e:
        print(f"  shared-voices {params}: HTTP {e.code} {vp.err_text(e)}")
        return []


def candidates(gender):
    found = []
    for q in ({"search": "haryanvi", "gender": gender, "page_size": 20},
              {"language": "hi", "gender": gender, "accent": "indian", "page_size": 40,
               "sort": "usage_character_count_1y"},
              {"language": "hi", "gender": gender, "page_size": 40, "sort": "usage_character_count_1y"}):
        found += shared(q)
    seen, out = {CURRENT[gender][0]}, []
    for v in found:
        if v.get("voice_id") in seen:
            continue
        seen.add(v.get("voice_id"))
        out.append(v)
    return out[:PER_GENDER]


def tts(voice_id, text, dest):
    b = {"text": text, "model_id": vp.MODEL, "language_code": "hi",
         "voice_settings": {"stability": 0.5, "similarity_boost": 0.75}}
    data, _ = vp.api("POST", f"/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128", b, raw=True)
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as f:
        f.write(data)
        src = f.name
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", src, "-ac", "1", "-ar", "24000",
                    "-c:a", "libopus", "-b:a", "32k", dest], check=True)
    os.unlink(src)


def main():
    mine = {v.get("voice_id") for v in vp.my_voices()}
    voices = []
    for gender in ("male", "female"):
        vid, name = CURRENT[gender]
        voices.append({"gender": gender, "voice_id": vid, "name": name, "owner": "", "accent": "indian",
                       "age": "", "description": "The voice the app uses today.", "current": True})
        for v in candidates(gender):
            voices.append({"gender": gender, "voice_id": v["voice_id"], "name": v.get("name", "?"),
                           "owner": v.get("public_owner_id", ""), "accent": v.get("accent", ""),
                           "age": v.get("age", ""), "description": (v.get("description") or "")[:200],
                           "preview_url": v.get("preview_url", ""), "current": False})
    chars, done = 0, []
    for i, v in enumerate(voices):
        key = f"{v['gender']}-{i:02d}"
        added = None
        try:
            vid = v["voice_id"]
            if vid not in mine and v["owner"]:
                try:
                    r = vp.api("POST", f"/v1/voices/add/{v['owner']}/{vid}", {"new_name": f"sample {v['name']}"[:40]})
                    added = r.get("voice_id", vid)
                    vid = added
                except urllib.error.HTTPError as e:
                    print(f"  add {v['name']}: HTTP {e.code} {vp.err_text(e)}")
            os.makedirs(os.path.join(OUT, key), exist_ok=True)
            clips = {}
            for lid, spoken, shown in LINES:
                dest = os.path.join(OUT, key, f"{lid}.ogg")
                try:
                    tts(vid, spoken, dest)
                    clips[lid] = f"{key}/{lid}.ogg"
                    chars += len(spoken)
                except urllib.error.HTTPError as e:
                    msg = vp.err_text(e)
                    vp.annotate("warning", "Sample", f"{v['name']} {lid}: HTTP {e.code} {msg}")
                    if "quota" in msg.lower():
                        raise SystemExit("quota")
            v.update(key=key, clips=clips)
            if clips:
                done.append(v)
            print(f"{key} {v['name']}: {len(clips)} clips")
        finally:
            if added:
                try:
                    vp.api("DELETE", f"/v1/voices/{added}")
                except urllib.error.HTTPError as e:
                    print(f"  remove {v['name']}: HTTP {e.code}")
    with open(os.path.join(OUT, "samples.json"), "w") as f:
        json.dump({"lines": [{"id": l, "text": s} for l, _, s in LINES], "voices": done,
                   "characters": chars}, f, ensure_ascii=False, indent=1)
    vp.annotate("notice", "Voice samples", f"Recorded {len(done)} voices, {chars} characters")


if __name__ == "__main__":
    main()
