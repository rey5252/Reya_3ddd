"""Preview images of the GUI with sample items, to look at the pixel art before a game run.

Items are taken from Botania's own textures (a clone of VazkiiMods/Botania next to this repo, found
through the BOTANIA env var or /home/user/vazkiimods/botania), so flowers look like they will in game.
"""
import os

from PIL import Image

from pix import ROOT, upscale

BOTANIA = os.environ.get("BOTANIA", "/home/user/vazkiimods/botania")
TEX = os.path.join(BOTANIA, "Xplat", "src", "main", "resources", "assets", "botania", "textures")
OUT_DIR = os.path.join(ROOT, "build", "preview")


def botania_icon(name):
    for sub in ("block", "item"):
        p = os.path.join(TEX, sub, name + ".png")
        if os.path.exists(p):
            img = Image.open(p).convert("RGBA")
            return img.crop((0, 0, 16, 16))
    return None


def preview_gui(cv, extra=None, scale=3, name="gui.png"):
    from gen_gui import M, FLOWERS, UPGRADES, CHARGE
    img = cv.img.copy()
    bg = Image.new("RGBA", (img.width + 40, img.height + 40), (40, 44, 52, 255))
    bg.alpha_composite(img, (20, 20))
    ox, oy = 20 + M, 20 + M
    flowers = ["endoflame", "hydroangeas", "thermalily", "rosa_arcana", "munchdew", "kekimurus", "gourmaryllis", "spectrolus"]
    for (x, y), f in zip(FLOWERS, flowers):
        icon = botania_icon(f)
        if icon:
            bg.alpha_composite(icon, (ox + x, oy + y))
    tablet = botania_icon("mana_tablet")
    if tablet:
        bg.alpha_composite(tablet, (ox + CHARGE[0], oy + CHARGE[1]))
    if extra:
        extra(bg, ox, oy)
    os.makedirs(OUT_DIR, exist_ok=True)
    out = upscale(bg, scale)
    out.save(os.path.join(OUT_DIR, name))
    return out
