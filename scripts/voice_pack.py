#!/usr/bin/env python3
"""Records the Haryanvi voice pack with ElevenLabs (run by .github/workflows/voice-pack.yml).

  voice_pack.py list-voices
      Prints Hindi / Indian male and female candidates (your voices and the shared library) and,
      for the workflow, picks a default for each gender when the VOICE_ID_* variables are empty.

  voice_pack.py generate --voices male,female [--only-missing]
      Reads voice/lines.json (generated from HaryanviLines.kt), records each line in each voice
      (model eleven_v3, Hindi), converts to mono 24 kHz Opus/Ogg ~24 kbps with ffmpeg and writes
      app/src/main/assets/voice/<voice>/<id>.ogg plus clips.json (id -> hash of the text recorded).
      The app only plays a clip whose hash matches the current line, so edited lines fall back to
      the phone voice until they are recorded again.

The API key comes from ELEVENLABS_API_KEY and is never printed. Standard library only (+ ffmpeg).
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://api.elevenlabs.io"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = os.path.join(ROOT, "voice", "lines.json")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "voice")
MODEL = "eleven_v3"

# Light delivery tags (eleven_v3 audio tags, not spoken). Fragments that get stitched together
# (distances, turn tails, turn instructions) get none, so the pieces sound alike.
TAG_BY_EVENT = {
    "TalkJoke": "[playfully]",
    "Greeting": "[warmly]",
    "WakeReply": "[warmly]",
    "TalkHowAreYou": "[warmly]",
    "TalkWhoAreYou": "[cheerfully]",
    "SpeedEaseOff": "[concerned]",
    "OverSpeed": "[concerned]",
    "SpeedLimitPlain": "[concerned]",
    "SpeedBackUnder": "[cheerfully]",
    "BreakReminderPlain": "[warmly]",
    "ParkingSaved": "[cheerfully]",
}
TAG_BY_GROUP = {
    "Welcome": "[warmly]",
    "Car": "[concerned]",
    "Music": "[cheerfully]",
}
# Numbers, paper names and the tails stitched after them are fragments too.
NO_TAG_EVENTS = {"Distance", "NavSoonTail", "CameraAheadTail", "NavNow", "NavSoon",
                 "Number", "PaperName", "PaperDueTail", "PaperExpiredTail", "PassTripsTail",
                 # Crash / SOS prompts: plain and calm, no delivery tag.
                 "Safety"}


def key():
    k = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    if not k:
        annotate("error", "Missing secret", "ELEVENLABS_API_KEY is missing. Add it under Settings > Secrets and "
                 "variables > Actions > New repository secret (name ELEVENLABS_API_KEY), then run this workflow again.")
        sys.exit(1)
    return k


def api(method, path, body=None, raw=False, retries=3):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"xi-api-key": key(), "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    for attempt in range(retries):
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                b = resp.read()
                return (b, dict(resp.headers)) if raw else json.loads(b or b"{}")
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 504) and attempt < retries - 1:
                time.sleep(5 * (attempt + 1))
                continue
            raise


class Fatal(Exception):
    """The account can't do this at all (no key, no plan, no permission): stop the run."""


def gh_escape(msg):
    return str(msg).replace("%", "%25").replace("\r", "").replace("\n", " ")


def annotate(level, title, msg):
    """GitHub annotations are readable without the job log."""
    print(f"::{level} title={title}::{gh_escape(msg)}", flush=True)


def explain(code, msg):
    m = msg.lower()
    if code == 402 or "paid_plan_required" in m or "payment" in m:
        return ("ElevenLabs refused with HTTP 402 (paid plan required): library voices need a paid plan "
                "(Starter or higher). Upgrade the account, then run this workflow again. API said: " + msg)
    if code == 401:
        return "ElevenLabs refused the API key (HTTP 401): check the ELEVENLABS_API_KEY secret. API said: " + msg
    if code == 403:
        return ("ElevenLabs refused with HTTP 403: the API key lacks a permission (needs Text to Speech, "
                "Voices read/write, User read). API said: " + msg)
    if "quota" in m or "credits" in m:
        return "ElevenLabs character quota exceeded: wait for the monthly reset or upgrade. API said: " + msg
    return f"HTTP {code}: {msg}"


def err_text(e):
    try:
        return e.read().decode("utf-8", "replace")[:600]
    except Exception:
        return str(e)


# ---------------------------------------------------------------------------- voices

def shared_voices(gender):
    out = []
    for params in (
        {"language": "hi", "gender": gender, "accent": "indian", "page_size": 30, "sort": "usage_character_count_1y"},
        {"language": "hi", "gender": gender, "page_size": 30, "sort": "usage_character_count_1y"},
    ):
        try:
            out += api("GET", "/v1/shared-voices?" + urllib.parse.urlencode(params)).get("voices", [])
        except urllib.error.HTTPError as e:
            msg = err_text(e)
            if e.code in (401, 402, 403):
                annotate("error", "ElevenLabs", explain(e.code, msg))
                sys.exit(1)
            print(f"  shared-voices {gender}: HTTP {e.code} {msg}")
    seen, unique = set(), []
    for v in out:
        if v.get("voice_id") not in seen:
            seen.add(v.get("voice_id"))
            unique.append(v)
    return unique


def my_voices():
    voices, token = [], None
    while True:
        q = {"page_size": 100}
        if token:
            q["next_page_token"] = token
        try:
            d = api("GET", "/v2/voices?" + urllib.parse.urlencode(q))
        except urllib.error.HTTPError as e:
            msg = err_text(e)
            if e.code == 401:
                annotate("error", "ElevenLabs", explain(e.code, msg))
                sys.exit(1)
            print(f"  my voices: HTTP {e.code} {msg}")
            return voices
        voices += d.get("voices", [])
        token = d.get("next_page_token")
        if not d.get("has_more") or not token:
            return voices


def cmd_list_voices(_args):
    mine = my_voices()
    print(f"Your voices ({len(mine)}):")
    for v in mine:
        labels = v.get("labels") or {}
        print(f"  {v.get('voice_id')}  {v.get('name')}  [{labels.get('gender', '?')}, {labels.get('accent', '?')}, {v.get('category', '')}]")
    picks = {}
    for gender in ("male", "female"):
        cands = shared_voices(gender)
        print(f"\nShared library, Hindi {gender} ({len(cands)}), most used first:")
        for v in cands[:12]:
            print(f"  {v.get('voice_id')}  {v.get('name')}  [{v.get('accent')}, {v.get('age')}, "
                  f"used {v.get('usage_character_count_1y', 0)} chars/yr]  {(v.get('description') or '')[:90]}")
        if cands:
            picks[gender] = cands[0]
    out = os.environ.get("GITHUB_OUTPUT")
    lines = []
    for gender in ("male", "female"):
        chosen = os.environ.get(f"VOICE_ID_{gender.upper()}", "").strip()
        if chosen:
            print(f"\n{gender}: using repo variable VOICE_ID_{gender.upper()} = {chosen}")
            lines += [f"{gender}_id={chosen}", f"{gender}_owner="]
        elif gender in picks:
            v = picks[gender]
            print(f"\n{gender}: VOICE_ID_{gender.upper()} not set, default = {v['voice_id']} ({v.get('name')}). "
                  f"Set the variable to choose another.")
            lines += [f"{gender}_id={v['voice_id']}", f"{gender}_owner={v.get('public_owner_id', '')}"]
        else:
            print(f"\n{gender}: no candidate found; set VOICE_ID_{gender.upper()}.")
            lines += [f"{gender}_id=", f"{gender}_owner="]
    if out:
        with open(out, "a") as f:
            f.write("\n".join(lines) + "\n")


def usable_voice(voice_id, owner, label):
    """A voice id TTS accepts: library voices are added to the account first (like voice samples)."""
    if not voice_id:
        return None
    if voice_id in {v.get("voice_id") for v in my_voices()}:
        return voice_id
    if owner:
        try:
            added = api("POST", f"/v1/voices/add/{owner}/{voice_id}", {"new_name": label})
            print(f"  added library voice {voice_id} as '{label}'")
            return added.get("voice_id", voice_id)
        except urllib.error.HTTPError as e:
            msg = err_text(e)
            if e.code in (401, 402, 403):
                raise Fatal(explain(e.code, msg))
            print(f"  voices/add {voice_id}: HTTP {e.code} {msg}")
    return voice_id


# ---------------------------------------------------------------------------- recording

class Settings:
    """Request settings; fields the API refuses are adjusted once and remembered for the run."""

    def __init__(self):
        self.stability = 0.4
        self.speed = 0.9
        self.send_speed = True
        self.send_language = True
        self.post_tempo = None  # ffmpeg atempo when the API refuses speed.

    def body(self, text):
        vs = {"stability": self.stability, "similarity_boost": 0.75}
        if self.send_speed:
            vs["speed"] = self.speed
        b = {"text": text, "model_id": MODEL, "voice_settings": vs}
        if self.send_language:
            b["language_code"] = "hi"
        return b

    def adjust(self, message):
        m = message.lower()
        if "stability" in m and self.stability not in (0.0, 0.5, 1.0):
            # eleven_v3 only takes 0.0 (creative), 0.5 (natural) or 1.0 (robust).
            self.stability = 0.5
            print("  API refused stability 0.4: using 0.5 (Natural)")
            return True
        if "speed" in m and self.send_speed:
            self.send_speed = False
            self.post_tempo = self.speed
            print(f"  API refused speed: slowing to {self.speed}x with ffmpeg instead")
            return True
        if "language" in m and self.send_language:
            self.send_language = False
            print("  API refused language_code: sending the Devanagari text without it")
            return True
        return False


def tagged(entry):
    if entry["event"] in NO_TAG_EVENTS or entry["group"] == "Navigation":
        return entry["text"]
    tag = TAG_BY_EVENT.get(entry["event"]) or TAG_BY_GROUP.get(entry["group"])
    return f"{tag} {entry['text']}" if tag else entry["text"]


def tts(voice_id, text, settings):
    for _ in range(4):
        try:
            audio, headers = api("POST", f"/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128",
                                 settings.body(text), raw=True)
            return audio, headers
        except urllib.error.HTTPError as e:
            msg = err_text(e)
            if e.code in (400, 422) and settings.adjust(msg):
                continue
            if e.code in (401, 402, 403) or "quota_exceeded" in msg or "paid_plan_required" in msg:
                raise Fatal(explain(e.code, msg))
            raise RuntimeError(explain(e.code, msg))
    raise RuntimeError("request settings kept being refused")


def to_opus(mp3, ogg, tempo):
    trim = "silenceremove=start_periods=1:start_threshold=-50dB"
    filters = [trim, "areverse", trim, "areverse", "apad=pad_dur=0.08"]
    if tempo:
        filters.insert(0, f"atempo={tempo}")
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", mp3, "-af", ",".join(filters),
         "-ac", "1", "-ar", "24000", "-c:a", "libopus", "-b:a", "24k", "-application", "voip", ogg],
        check=True,
    )


def text_hash(text):
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:12]


def cmd_generate(args):
    try:
        generate(args)
    except Fatal as e:
        annotate("error", "ElevenLabs", e)
        sys.exit(1)


def generate(args):
    key()
    manifest = json.load(open(LINES, encoding="utf-8"))
    entries = manifest["lines"]
    for e in entries:
        if e["hash"] != text_hash(e["text"]):
            annotate("error", "Voice pack", f"voice/lines.json hash mismatch for {e['id']}: regenerate it (see VoiceManifestTest)."); sys.exit(1)
    all_entries = entries
    settings = Settings()
    summary = {"voices": {}, "failed": 0}
    try:
        before = api("GET", "/v1/user/subscription").get("character_count")
    except Exception:
        before = None

    planned_total = [0]
    for voice in [v.strip() for v in args.voices.split(",") if v.strip()]:
        if voice not in ("male", "female"):
            annotate("error", "Voice pack", f"unknown voice '{voice}' (use male, female)"); sys.exit(1)
        label = "Ampli Bhai" if voice == "male" else "Ampli Behen"
        vid = usable_voice(os.environ.get(f"{voice.upper()}_ID", "").strip(),
                           os.environ.get(f"{voice.upper()}_OWNER", "").strip(), label)
        if not vid:
            annotate("error", "Voice pack", f"no voice id for {voice}: set the repo variable VOICE_ID_{voice.upper()}."); sys.exit(1)
        # A line worded per voice ("voice": "male" / "female", ids <id>_m / <id>_f) is recorded
        # only by that voice; every other line by both.
        entries = [e for e in all_entries if e.get("voice") in (None, voice)]
        ids = {e["id"] for e in entries}
        folder = os.path.join(ASSETS, voice)
        os.makedirs(folder, exist_ok=True)
        index_path = os.path.join(folder, "clips.json")
        index = json.load(open(index_path, encoding="utf-8")) if os.path.exists(index_path) else {}
        clips = index.get("clips", {})
        if index.get("voice_id") and index.get("voice_id") != vid:
            print(f"{voice}: voice changed ({index.get('voice_id')} -> {vid}), recording every line again")
            clips = {}
        # Drop clips for lines that no longer exist.
        for name in os.listdir(folder):
            if name.endswith(".ogg") and name[:-4] not in ids:
                os.remove(os.path.join(folder, name))
        clips = {k: v for k, v in clips.items() if k in ids}

        todo = [e for e in entries
                if not (args.only_missing and os.path.exists(os.path.join(folder, e["id"] + ".ogg")) and clips.get(e["id"]) == e["hash"])]
        planned = sum(len(tagged(e)) for e in todo)
        print(f"{voice}: {len(todo)} clips to record, about {planned} characters")
        if args.max_chars and planned_total[0] + planned > args.max_chars:
            annotate("error", "Voice pack budget",
                     f"{voice}: this run would send about {planned_total[0] + planned} characters, over the --max-chars "
                     f"budget of {args.max_chars}. Nothing more was recorded. Raise VOICE_MAX_CHARS or record fewer lines.")
            save_index(index_path, vid, settings, clips)
            break
        planned_total[0] += planned

        chars = billed = done = skipped = failed = 0
        with tempfile.TemporaryDirectory() as tmp:
            for e in entries:
                ogg = os.path.join(folder, e["id"] + ".ogg")
                if args.only_missing and os.path.exists(ogg) and clips.get(e["id"]) == e["hash"]:
                    skipped += 1
                    continue
                text = tagged(e)
                try:
                    audio, headers = tts(vid, text, settings)
                    mp3 = os.path.join(tmp, e["id"] + ".mp3")
                    open(mp3, "wb").write(audio)
                    to_opus(mp3, ogg, settings.post_tempo)
                except Fatal:
                    save_index(index_path, vid, settings, clips)
                    raise
                except Exception as ex:
                    failed += 1
                    print(f"  FAILED {voice}/{e['id']}: {ex}")
                    if failed <= 5:
                        annotate("warning", f"Clip {voice}/{e['id']} failed", ex)
                    continue
                clips[e["id"]] = e["hash"]
                chars += len(text)
                billed += int(headers.get("x-character-count") or headers.get("X-Character-Count") or len(text))
                done += 1
                if done % 25 == 0:
                    print(f"  {voice}: {done} recorded, {chars} characters so far")
                    save_index(index_path, vid, settings, clips)
        save_index(index_path, vid, settings, clips)
        annotate("notice", f"Voice {voice}", f"{voice} ({vid}): recorded {done}, skipped {skipped} up to date, "
                 f"failed {failed}; {chars} characters sent, {billed} billed; {len(clips)}/{len(entries)} clips in the pack")
        summary["voices"][voice] = {"voice_id": vid, "recorded": done, "skipped": skipped, "failed": failed,
                                    "characters": chars, "billed": billed, "clips": len(clips), "total": len(entries)}
        summary["failed"] += failed

    try:
        after = api("GET", "/v1/user/subscription").get("character_count")
        if before is not None and after is not None:
            summary["account_characters_used"] = after - before
            annotate("notice", "Account", f"Account characters used by this run: {after - before}")
    except Exception:
        pass
    total = sum(v["characters"] for v in summary["voices"].values())
    annotate("notice", "Characters", f"Total characters sent this run: {total}")
    if summary["failed"]:
        annotate("error", "Some clips failed", f"{summary['failed']} clips failed (see warnings); the rest were "
                 "saved. Run again with only_missing to fill the gaps.")
    json.dump(summary, open(os.path.join(ROOT, "voice-pack-summary.json"), "w"), indent=1)
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a") as f:
            f.write(f"failed={summary['failed']}\nrecorded={sum(v['recorded'] for v in summary['voices'].values())}\n"
                    f"characters={total}\n")


def save_index(path, vid, settings, clips):
    data = {
        "voice_id": vid,
        "model": MODEL,
        "stability": settings.stability,
        "speed": settings.speed,
        "format": "ogg/opus mono 24 kHz 24 kbps",
        "clips": dict(sorted(clips.items())),
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list-voices")
    g = sub.add_parser("generate")
    g.add_argument("--voices", default="male,female")
    g.add_argument("--only-missing", action="store_true")
    g.add_argument("--max-chars", type=int, default=int(os.environ.get("VOICE_MAX_CHARS") or 0),
                   help="Stop before a voice whose clips would push the run over this many characters (0 = no limit)")
    args = p.parse_args()
    {"list-voices": cmd_list_voices, "generate": cmd_generate}[args.cmd](args)


if __name__ == "__main__":
    main()
