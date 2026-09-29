"""The faces of the blocks in reference/panels.jpg. A strip is a row of n faces in a plane, given by
its four corners in the picture (front left, front right, back right, back left: the texture's top
is the back edge); a homography takes [0, n] x [0, 1] to it, so each face comes out square however
far off it is. Rows too thin to trust their far corners are given face by face (n = 1)."""
STRIPS = {
    # left wing, tops (solar panels): the grids on the floor
    "LA": dict(n=4, q=[(216, 490), (468, 326), (430, 304), (187, 437)]),
    # the next step up
    "LB0": dict(n=1, q=[(166, 370), (244, 336), (223, 310), (145, 339)]),
    "LB1": dict(n=1, q=[(248, 328), (316, 303), (294, 280), (230, 304)]),
    "LB2": dict(n=1, q=[(320, 297), (375, 276), (352, 260), (301, 280)]),
    "LB3": dict(n=1, q=[(372, 269), (424, 251), (408, 240), (358, 257)]),
    "LB4": dict(n=1, q=[(424, 249), (464, 234), (442, 224), (404, 235)]),
    "LB5": dict(n=1, q=[(468, 232), (498, 218), (478, 207), (448, 217)]),
    # and the next
    "LC0": dict(n=1, q=[(113, 268), (192, 245), (180, 229), (93, 248)]),
    "LC1": dict(n=1, q=[(200, 240), (268, 223), (256, 208), (186, 225)]),
    "LC2": dict(n=1, q=[(273, 217), (328, 203), (303, 191), (252, 204)]),
    "LC3": dict(n=1, q=[(337, 199), (382, 186), (357, 177), (317, 189)]),
    "LC4": dict(n=1, q=[(390, 181), (425, 172), (410, 164), (372, 172)]),
    "LC5": dict(n=1, q=[(430, 170), (464, 161), (446, 153), (410, 162)]),
    "LC6": dict(n=1, q=[(466, 159), (498, 150), (478, 144), (446, 150)]),
    "LC7": dict(n=1, q=[(506, 170), (534, 158), (504, 150), (480, 159)]),
}


def _fronts(prefix, v, length, below=False):
    """The fronts of a row of tops: from each top's front edge down one block (v, which is one block
    down where the front edge is `length` long, scaled with it); or with below=True the fronts of
    the row behind, standing on each top's back edge."""
    out = {}
    for name, s in list(STRIPS.items()):
        if not (name.startswith(prefix) and s["n"] == 1):
            continue
        fl, fr, br, bl = s["q"]
        if below:
            a, b = bl, br
        else:
            a, b = fl, fr
        k = ((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2) ** 0.5 / length
        d = (v[0] * k, v[1] * k)
        if below:
            q = [a, b, (b[0] - d[0], b[1] - d[1]), (a[0] - d[0], a[1] - d[1])]
            # texture top is up: reorder so the front edge (bottom of texture) is first
            q = [a, b, q[2], q[3]]
        else:
            q = [(a[0] + d[0], a[1] + d[1]), (b[0] + d[0], b[1] + d[1]), b, a]
        out["F" + name] = dict(n=1, q=q, front=True, below=below)
    return out


STRIPS.update(_fronts("LB", (20, 68), 85))
STRIPS.update(_fronts("LC", (31, 68), 82))
STRIPS.update({"FD" + k[3:]: v for k, v in _fronts("LC", (31, 68), 82, below=True).items()})


# right wing (quantum generators): its rows run down to the right and face down to the left
def _q(name, fl, fr, br, bl):
    STRIPS[name] = dict(n=1, q=[fl, fr, br, bl])


# the stripes on the floor, and the spirals a step up
for k, q in enumerate([[(447, 300), (477, 318), (503, 302), (473, 283)],
                       [(483, 317), (520, 342), (547, 327), (510, 303)],
                       [(520, 345), (570, 377), (597, 350), (547, 330)],
                       [(577, 375), (633, 402), (657, 377), (600, 355)],
                       [(643, 413), (703, 437), (730, 407), (670, 382)]]):
    _q(f"RA{k}", *q)
for k, q in enumerate([[(490, 233), (520, 245), (550, 233), (517, 220)],
                       [(522, 248), (557, 263), (580, 250), (547, 237)],
                       [(570, 267), (603, 285), (630, 268), (597, 253)],
                       [(630, 285), (667, 307), (690, 290), (657, 273)],
                       [(690, 313), (737, 338), (760, 317), (710, 297)]]):
    _q(f"RB{k}", *q)
# the top row's fronts (hearts and squares): top left, top right, bottom right, bottom left
R_TOP = [[(503, 103), (535, 107), (535, 157), (507, 152)],
         [(540, 105), (582, 113), (577, 170), (542, 160)],
         [(583, 115), (630, 123), (627, 178), (582, 172)],
         [(632, 125), (683, 135), (683, 190), (632, 178)],
         [(687, 135), (750, 147), (747, 207), (687, 193)],
         [(755, 148), (820, 160), (817, 223), (757, 212)]]
for k, (tl, tr, br, bl) in enumerate(R_TOP):
    STRIPS[f"FRD{k}"] = dict(n=1, q=[bl, br, tr, tl], front=True)


def _shift(p, d, k):
    return (p[0] + d[0] * k, p[1] + d[1] * k)


# the tops in front of them (the dashes) and on them, and the fronts of the spirals
for k, (tl, tr, br, bl) in enumerate(R_TOP):
    w = ((br[0] - bl[0]) ** 2 + (br[1] - bl[1]) ** 2) ** 0.5 / 45.0
    d = (-30, 22)
    _q(f"RC{k}", _shift(bl, d, w), _shift(br, d, w), br, bl)
    _q(f"RD{k}", tl, tr, _shift(tr, (26, -14), w), _shift(tl, (26, -14), w))
    fl, fr = _shift(bl, d, w), _shift(br, d, w)
    v = (-6, 52)
    STRIPS[f"FRC{k}"] = dict(n=1, q=[_shift(fl, v, w), _shift(fr, v, w), fr, fl], front=True)
for k in range(5):
    fl, fr, br, bl = STRIPS[f"RB{k}"]["q"]
    w = ((fr[0] - fl[0]) ** 2 + (fr[1] - fl[1]) ** 2) ** 0.5 / 34.0
    v = (-6, 52)
    STRIPS[f"FRB{k}"] = dict(n=1, q=[_shift(fl, v, w), _shift(fr, v, w), fr, fl], front=True)
