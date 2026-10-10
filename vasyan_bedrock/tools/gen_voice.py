"""Voices every line of Vasyan and Klod with RHVoice and writes everything the packs need for it.

Run from anywhere:  python3 vasyan_bedrock/tools/gen_voice.py
Needs RHVoice-test and ffmpeg (Ubuntu: apt install rhvoice rhvoice-russian rhvoice-ukrainian ffmpeg).
Source of truth: tools/lines/<character>_<lang>.json, with "ui" (menu texts), "normal" and "mat" (swearing)
line sets: key -> list of lines.
Writes:
  resource_pack/sounds/<character>/<lang>/<set>/<key>_<n>.ogg
  resource_pack/sounds/sound_definitions.json   sound ids <character>.<lang>.<set>.<key>.<n>
  behavior_pack/scripts/voice.js                 texts + how long each line takes, for the subtitles
Lines whose text and voice did not change are not re-voiced (tools/voice_cache.json keeps the hashes).
"""
import hashlib, json, os, subprocess, sys, tempfile, wave
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
LINES = os.path.join(ROOT, "tools", "lines")
SOUNDS = os.path.join(ROOT, "resource_pack", "sounds")
CACHE = os.path.join(ROOT, "tools", "voice_cache.json")
VOICE_JS = os.path.join(ROOT, "behavior_pack", "scripts", "voice.js")

CHARACTERS = ["vasyan", "klod"]
LANGS = ["uk", "ru"]
# RHVoice profile, pitch and rate in percent, ffmpeg filter. Every line ends up at the same loudness.
# Bobik (the "vasyan" character, id kept from the first version): low, slow, raspy and heavy.
# Klod: calm and clean.
LOUD = "loudnorm=I=-15:TP=-1.5:LRA=11"
BOBIK = "bass=g=9:f=120,acrusher=bits=9:mode=log:aa=1:mix=0.2,aecho=0.8:0.4:35:0.22," + LOUD
VOICES = {
    "vasyan": {
        "uk": {"profile": "anatol", "pitch": 45, "rate": 85, "filter": BOBIK},
        "ru": {"profile": "yuriy", "pitch": 45, "rate": 85, "filter": BOBIK},
    },
    "klod": {
        "uk": {"profile": "volodymyr", "pitch": 95, "rate": 100, "filter": LOUD},
        "ru": {"profile": "aleksandr-hq", "pitch": 95, "rate": 100, "filter": LOUD},
    },
}
# Lines that exist only in one set: mat_on is said right after swearing gets turned on, mat_off after it is off.
ONLY = {"normal": {"mat_off"}, "mat": {"mat_on"}}


def load():
    data = {}
    for ch in CHARACTERS:
        data[ch] = {}
        for lang in LANGS:
            d = json.load(open(os.path.join(LINES, f"{ch}_{lang}.json"), encoding="utf-8"))
            data[ch][lang] = d
        first = data[ch][LANGS[0]]
        for lang in LANGS:
            d = data[ch][lang]
            if set(d["ui"]) != set(first["ui"]):
                sys.exit(f"{ch}_{lang}: ui keys differ from {ch}_{LANGS[0]}: {set(d['ui']) ^ set(first['ui'])}")
            for s in ("normal", "mat"):
                if set(d[s]) != set(first[s]):
                    sys.exit(f"{ch}_{lang}.{s}: keys differ from {ch}_{LANGS[0]}: {set(d[s]) ^ set(first[s])}")
            if set(d["normal"]) - ONLY["normal"] != set(d["mat"]) - ONLY["mat"]:
                sys.exit(f"{ch}_{lang}: normal and mat sets have different keys: {set(d['normal']) ^ set(d['mat'])}")
    return data


def voice(job):
    """RHVoice -> wav -> filtered ogg (full quality). Returns the length in seconds."""
    text, lang, ch, out = job
    v = VOICES[ch][lang]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "line.wav")
        subprocess.run(["RHVoice-test", "-p", v["profile"], "-t", str(v["pitch"]), "-r", str(v["rate"]), "-o", wav],
                       input=text.encode("utf-8"), check=True, capture_output=True)
        with wave.open(wav) as w:
            seconds = w.getnframes() / w.getframerate()
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav, "-af", v["filter"],
                        "-ar", "24000", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", out], check=True)
    return round(seconds + 0.3, 2)  # the echo tail


def main():
    data = load()
    cache = json.load(open(CACHE, encoding="utf-8")) if os.path.exists(CACHE) else {}
    new_cache, jobs, defs = {}, [], {}
    voice_js = {}
    for ch in CHARACTERS:
        voice_js[ch] = {}
        for lang in LANGS:
            d = data[ch][lang]
            entry = {"ui": d["ui"], "normal": {}, "mat": {}}
            for s in ("normal", "mat"):
                for key, lines in d[s].items():
                    entry[s][key] = []
                    for i, text in enumerate(lines, 1):
                        rel = f"sounds/{ch}/{lang}/{s}/{key}_{i}"
                        out = os.path.join(SOUNDS, ch, lang, s, f"{key}_{i}.ogg")
                        h = hashlib.sha1(json.dumps([text, VOICES[ch][lang]], ensure_ascii=False).encode()).hexdigest()
                        old = cache.get(rel)
                        # own recordings brought in with tools/import_voice.py are never re-voiced
                        if old and (old.get("custom") or old["hash"] == h) and os.path.exists(out):
                            new_cache[rel] = old
                        else:
                            jobs.append((rel, h, (text, lang, ch, out)))
                        entry[s][key].append([text, rel])
                        defs[f"{ch}.{lang}.{s}.{key}.{i}"] = {
                            "category": "neutral",
                            "min_distance": 4.0,
                            "max_distance": 32.0,
                            "sounds": [{"name": rel, "volume": 1.0}],
                        }
            voice_js[ch][lang] = entry
    print(f"{len(defs)} lines, {len(jobs)} to voice")
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
        for (rel, h, _), seconds in zip(jobs, pool.map(voice, [j[2] for j in jobs])):
            new_cache[rel] = {"hash": h, "seconds": seconds}
    # drop sounds of lines that no longer exist
    keep = {os.path.normpath(os.path.join(ROOT, "resource_pack", rel + ".ogg")) for rel in new_cache}
    for dirpath, _, files in os.walk(SOUNDS):
        for f in files:
            p = os.path.normpath(os.path.join(dirpath, f))
            if f.endswith(".ogg") and p not in keep:
                os.remove(p)
    # [text, sound path] -> [text, ticks]: the script only needs how long to keep the subtitle and mouth going
    for ch in voice_js.values():
        for entry in ch.values():
            for s in ("normal", "mat"):
                for lines in entry[s].values():
                    for line in lines:
                        line[1] = int(new_cache[line[1]]["seconds"] * 20) + 4
    json.dump({"format_version": "1.20.20", "sound_definitions": defs},
              open(os.path.join(SOUNDS, "sound_definitions.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    with open(VOICE_JS, "w", encoding="utf-8") as f:
        f.write("// Generated by tools/gen_voice.py from tools/lines/*.json - edit those and re-run it.\n")
        f.write("// VOICE[character][lang] = { ui: {...}, normal: { key: [[text, ticks], ...] }, mat: {...} }\n")
        f.write("export const VOICE = " + json.dumps(voice_js, ensure_ascii=False, separators=(",", ":")) + ";\n")
    json.dump(dict(sorted(new_cache.items())), open(CACHE, "w", encoding="utf-8"), indent=0)
    print("wrote", os.path.relpath(VOICE_JS, os.getcwd()), "and", os.path.relpath(SOUNDS, os.getcwd()))


if __name__ == "__main__":
    main()
