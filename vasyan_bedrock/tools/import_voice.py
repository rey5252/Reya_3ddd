"""Replaces the synthesized voice with your own recordings (your voice, a friend who agreed, ...).

  python3 vasyan_bedrock/tools/import_voice.py --list > lines.txt   every line: where its file goes + the text
  python3 vasyan_bedrock/tools/import_voice.py DIR                  import the recordings found in DIR

Recordings in DIR follow the same layout as the list: <character>/<lang>/<set>/<key>_<n>.<ext>
(e.g. vasyan/uk/mat/hello_1.m4a). Any format ffmpeg reads works; files are levelled to the same loudness
and converted to ogg. Lines without a recording keep the synthesized voice. Imported lines are marked in
tools/voice_cache.json, so gen_voice.py leaves them alone; delete the entry there to go back to synthesis.
"""
import json, os, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_voice  # noqa: E402


def lines():
    data = gen_voice.load()
    for ch in gen_voice.CHARACTERS:
        for lang in gen_voice.LANGS:
            for s in ("normal", "mat"):
                for key, texts in data[ch][lang][s].items():
                    for i, text in enumerate(texts, 1):
                        yield f"{ch}/{lang}/{s}/{key}_{i}", text


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        sys.exit(__doc__)
    if sys.argv[1] == "--list":
        for path, text in lines():
            print(f"{path}\t{text}")
        return
    src = sys.argv[1]
    found = {}
    for dirpath, _, files in os.walk(src):
        for f in files:
            rel = os.path.relpath(os.path.join(dirpath, os.path.splitext(f)[0]), src).replace(os.sep, "/")
            found[rel] = os.path.join(dirpath, f)
    cache = json.load(open(gen_voice.CACHE, encoding="utf-8")) if os.path.exists(gen_voice.CACHE) else {}
    n = 0
    for path, _ in lines():
        if path not in found:
            continue
        out = os.path.join(gen_voice.SOUNDS, *path.split("/")) + ".ogg"
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", found.pop(path),
                        "-af", gen_voice.LOUD, "-ar", "24000", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", out], check=True)
        seconds = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", out],
                                       check=True, capture_output=True, text=True).stdout)
        cache[f"sounds/{path}"] = {"hash": "custom", "custom": True, "seconds": round(seconds + 0.1, 2)}
        n += 1
    json.dump(dict(sorted(cache.items())), open(gen_voice.CACHE, "w", encoding="utf-8"), indent=0)
    for extra in sorted(found):
        print("not a line, skipped:", extra)
    print(f"imported {n} recordings")
    gen_voice.main()  # rebuilds voice.js with the new lengths


if __name__ == "__main__":
    main()
