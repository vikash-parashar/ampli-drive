"""Record one greeting in 5 male + 5 female Hindi voices (ElevenLabs) for the owner to pick from."""
import json, os, sys, urllib.request, urllib.error, urllib.parse

KEY = os.environ.get("ELEVENLABS_API_KEY", "")
if not KEY:
    print("::error::ELEVENLABS_API_KEY secret is missing"); sys.exit(1)
BASE = "https://api.elevenlabs.io"
TEXT = "[warmly] राम राम भाई! के हाल सै, सब राजी खुशी? [pause] आज कित की तैयारी सै? चाल, सीट बेल्ट पहन ले।"
PLAIN = "राम राम भाई! के हाल सै, सब राजी खुशी? आज कित की तैयारी सै? चाल, सीट बेल्ट पहन ले।"
os.makedirs("out", exist_ok=True)

def req(method, path, body=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"xi-api-key": KEY, "Content-Type": "application/json"})
    with urllib.request.urlopen(r, timeout=120) as resp:
        b = resp.read()
        return b if raw else json.loads(b or b"{}")

def shared(gender):
    try:
        return _shared(gender)
    except urllib.error.HTTPError as e:
        print(f"::error::shared-voices {gender} {e.code} {e.read()[:300]!r}")
        return []

def _shared(gender):
    q = urllib.parse.urlencode({"language": "hi", "gender": gender, "page_size": 40,
                                "sort": "usage_character_count_1y"})
    return req("GET", "/v1/shared-voices?" + q).get("voices", [])

def tts(voice_id, path):
    for model, text, settings in (("eleven_v3", TEXT, {"stability": 0.5}),
                                  ("eleven_multilingual_v2", PLAIN, {"stability": 0.45, "similarity_boost": 0.8, "style": 0.3})):
        try:
            audio = req("POST", f"/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128",
                        {"text": text, "model_id": model, "voice_settings": settings}, raw=True)
            open(path, "wb").write(audio)
            return model
        except urllib.error.HTTPError as e:
            print(f"::error::tts {model} {voice_id} {e.code} {e.read()[:300]!r}")
    return None

index = []
for gender in ("male", "female"):
    got = 0
    for v in shared(gender):
        if got >= 5:
            break
        name = v.get("name", "voice")
        vid = v["voice_id"]
        # Library voices must be added to the account before TTS.
        try:
            added = req("POST", f"/v1/voices/add/{v['public_owner_id']}/{vid}", {"new_name": f"Ampli {name}"[:40]})
            use_id = added.get("voice_id", vid)
        except urllib.error.HTTPError as e:
            print(f"::warning::add {name} {e.code} {e.read()[:300]!r}")
            use_id = vid
        got += 1
        fname = f"{gender}-{got}-{''.join(c for c in name if c.isalnum())[:20]}.mp3"
        model = tts(use_id, "out/" + fname)
        if not model:
            got -= 1
            continue
        index.append({"file": fname, "gender": gender, "name": name, "voice_id": use_id,
                      "accent": v.get("accent"), "age": v.get("age"), "description": (v.get("description") or "")[:160],
                      "model": model})
        print("ok", fname, name, model)
    print(f"::notice::{gender} recorded {got}")

json.dump(index, open("out/voices.json", "w"), ensure_ascii=False, indent=1)
with open("out/README.md", "w") as f:
    for i in index:
        f.write(f"- {i['file']}: {i['name']} ({i['gender']}, {i.get('accent')}, {i.get('age')}) voice_id={i['voice_id']} model={i['model']}\n")
if not index:
    print("::error::no samples recorded"); sys.exit(1)
