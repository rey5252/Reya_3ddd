"""Downloads the photographic maps the film draws its planets and sky with, from their original publishers, and
packs them into the mod's resources (run from the mod folder; GitHub Actions does it, see
.github/workflows/starfall-textures.yml).

Every source is public domain (NASA) or under a licence that allows reuse with credit (ESO, CC BY 4.0); the
credits are written next to the textures. Nothing here comes from any other mod.
"""
import io
import os
import sys
import urllib.request

import numpy as np
from PIL import Image, ImageFilter

Image.MAX_IMAGE_PIXELS = None
OUT = "src/main/resources/assets/starfall/textures/film"

SOURCES = {
    # NASA Visible Earth, Blue Marble Next Generation (July 2004), public domain
    "earth_day": [
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74092/world.200407.3x5400x2700.jpg",
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74117/world.200407.3x5400x2700.jpg",
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73751/world.topo.bathy.200407.3x5400x2700.jpg",
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57752/land_shallow_topo_2048.jpg",
    ],
    # NASA Visible Earth, Blue Marble clouds, public domain
    "earth_clouds": [
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57747/cloud_combined_2048.jpg",
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57747/cloud_combined_8192.tif",
    ],
    # NASA Earth Observatory, Earth at Night (Black Marble), public domain
    "earth_lights": [
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/79000/79765/dnb_land_ocean_ice.2012.3600x1800.jpg",
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/144000/144898/BlackMarble_2016_3km.jpg",
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/55000/55167/earth_lights_lrg.jpg",
    ],
    # NASA Scientific Visualization Studio, CGI Moon Kit (LRO), public domain
    "moon": [
        "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_2k.tif",
        "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_1k.jpg",
        "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_4k.tif",
    ],
    # NASA/JPL/Space Science Institute, Cassini's map of Jupiter (PIA07782), public domain
    "jupiter": [
        "https://photojournal.jpl.nasa.gov/jpeg/PIA07782.jpg",
        "https://photojournal.jpl.nasa.gov/tiff/PIA07782.tif",
    ],
    # ESO/S. Brunier, the Milky Way panorama (eso0932a), CC BY 4.0
    "milky_way": [
        "https://cdn.eso.org/images/large/eso0932a.jpg",
        "https://cdn.eso.org/images/publicationjpg/eso0932a.jpg",
        "https://cdn.eso.org/images/screen/eso0932a.jpg",
    ],
}

CREDITS = """The film's photographic maps, downloaded from their publishers by tools/fetch_textures.py:

earth_day.jpg     NASA Earth Observatory, Blue Marble Next Generation (public domain)
earth_aux.png     red: NASA Earth Observatory, Earth at Night; green: NASA Blue Marble clouds; blue: water,
                  worked out from the Blue Marble (public domain)
moon.jpg          NASA Scientific Visualization Studio, CGI Moon Kit, LRO data (public domain)
jupiter.jpg       NASA/JPL/Space Science Institute, Cassini (PIA07782) (public domain)
milky_way.jpg     ESO/S. Brunier, https://www.eso.org/public/images/eso0932a/ (CC BY 4.0)
"""


def fetch(name):
    for url in SOURCES[name]:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (starfall texture fetch)"})
            with urllib.request.urlopen(req, timeout=180) as r:
                data = r.read()
            img = Image.open(io.BytesIO(data))
            img.load()
            print(f"{name}: {url} -> {img.size} {img.mode}")
            return img
        except Exception as e:  # try the next mirror
            print(f"{name}: {url} failed: {e}")
    return None


def equirect(img, w, mode="RGB"):
    """Resizes to a 2:1 map (cropping a map that isn't 2:1 to its middle)."""
    img = img.convert(mode)
    iw, ih = img.size
    if abs(iw / ih - 2.0) > 0.02:
        if iw / ih > 2.0:
            nw = ih * 2
            img = img.crop(((iw - nw) // 2, 0, (iw - nw) // 2 + nw, ih))
        else:
            nh = iw // 2
            img = img.crop((0, (ih - nh) // 2, iw, (ih - nh) // 2 + nh))
    return img.resize((w, w // 2), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    got = {}
    for name in SOURCES:
        img = fetch(name)
        if img is not None:
            got[name] = img
    if "earth_day" in got:
        day = equirect(got["earth_day"], 4096)
        day.save(f"{OUT}/earth_day.jpg", quality=90, optimize=True)
        # water: where the Blue Marble is clearly blue
        rgb = np.asarray(day.resize((2048, 1024), Image.LANCZOS), dtype=np.float32)
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        mask = Image.fromarray(((b > r * 1.25 + 6) & (b >= g * 0.95)).astype(np.uint8) * 255).filter(ImageFilter.MedianFilter(3))
        lights = equirect(got["earth_lights"], 2048, "L") if "earth_lights" in got else Image.new("L", (2048, 1024))
        clouds = equirect(got["earth_clouds"], 2048, "L") if "earth_clouds" in got else Image.new("L", (2048, 1024))
        Image.merge("RGB", (lights, clouds, mask)).save(f"{OUT}/earth_aux.png", optimize=True)
    if "moon" in got:
        equirect(got["moon"], 2048).save(f"{OUT}/moon.jpg", quality=90, optimize=True)
    if "jupiter" in got:
        equirect(got["jupiter"], 2048).save(f"{OUT}/jupiter.jpg", quality=92, optimize=True)
    if "milky_way" in got:
        equirect(got["milky_way"], 4096).save(f"{OUT}/milky_way.jpg", quality=88, optimize=True)
    with open(f"{OUT}/CREDITS.txt", "w") as f:
        f.write(CREDITS)
    print("have:", sorted(got))
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(f"{OUT}/{f}"))
    if "earth_day" not in got:
        sys.exit(1)


if __name__ == "__main__":
    main()
