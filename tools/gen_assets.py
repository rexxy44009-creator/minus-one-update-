#!/usr/bin/env python3
"""
Generates the Godzilla Minus One model, animations, scale-pattern texture + glowmask, and entity/PlateProfile.java.

  python3 tools/gen_assets.py            # writes the assets
  python3 tools/gen_assets.py --render   # also writes tools/preview_3d*.png (shaded three-quarter renders)

The body is a VOXEL SCULPT: smooth capsule/ellipsoid shapes (torso, thighs, tapering low tail, small head, short arms)
are voxelised at half-block resolution and only the surface voxels become cubes (merged along X to keep the count down),
so the silhouette is stepped like hand-built voxel art. Dorsal plates are tall feathered spikes built from tilted cubes.

If the model looks tilted the WRONG way in game (spikes point forward / head leans back), set LEAN_SIGN = -1 and re-run.
"""
import json, math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

LEAN_SIGN = 1
RENDER = "--render" in sys.argv or "--preview" in sys.argv

random.seed(1954)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODID = "godzilla_mod"
RES = f"{ROOT}/src/main/resources/assets/{MODID}"
JAVA = f"{ROOT}/src/main/java/com/example/godzilla/entity"
for d in (f"{RES}/geo", f"{RES}/animations", f"{RES}/textures/entity", JAVA):
    os.makedirs(d, exist_ok=True)

# =====================================================================================  TEXTURE (256x256 atlas)
REG = {  # name: (x, y, w, h)
    "hide": (0, 0, 192, 128), "belly": (0, 128, 128, 64), "plate": (128, 128, 64, 64),
    "glow": (192, 0, 64, 64), "teeth": (192, 64, 32, 32), "eye": (224, 64, 32, 32),
    "mouth": (192, 96, 64, 32), "claw": (192, 128, 64, 64),
}
rng = np.random.default_rng(1954)

def value_noise(h, w, cell):
    gh, gw = h // cell + 3, w // cell + 3
    g = rng.random((gh, gw))
    ys, xs = np.arange(h) / cell, np.arange(w) / cell
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a, b, c, d = g[y0][:, x0], g[y0][:, x0 + 1], g[y0 + 1][:, x0], g[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]

def fbm(h, w, cells):
    out, amp, tot = np.zeros((h, w)), 1.0, 0.0
    for c in cells:
        out += amp * value_noise(h, w, c); tot += amp; amp *= 0.55
    return out / tot

def hash01(a, b):
    return ((a * 73856093) ^ (b * 19349663)) % 1000 / 1000.0

def scale_tex(h, w, cw=7, ch=6, dark=(22, 21, 24), light=(98, 89, 76)):
    """Pixel-art reptile scales: staggered rows, dark seams, lit top-left edge, per-scale tone variation."""
    f = fbm(h, w, [20, 9, 4])
    out = np.zeros((h, w, 3))
    d0, d1 = np.array(dark, float), np.array(light, float)
    for y in range(h):
        row = y // ch
        off = cw // 2 if row % 2 else 0
        v = y % ch
        for x in range(w):
            col = (x + off) // cw
            u = (x + off) % cw
            t = 0.30 + 0.45 * hash01(row + 7, col + 3)
            if u == 0 or v == ch - 1:
                t *= 0.35
            elif v == ch - 2 and u in (1, cw - 1):
                t *= 0.55
            elif u <= 2 and v <= 1:
                t += 0.22
            t *= 0.75 + 0.5 * f[y, x]
            out[y, x] = d0 + (d1 - d0) * min(1.0, t)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).convert("RGBA")

def belly_tex(h, w):
    """Lighter, concentric-arc banding (chest, belly, inner thighs, tail underside)."""
    yy, xx = np.mgrid[0:h, 0:w]
    centers = [(random.uniform(0, w), random.uniform(0, h)) for _ in range(5)]
    d = np.min([np.hypot(xx - cx, (yy - cy) * 1.3) for cx, cy in centers], axis=0)
    band = (0.5 + 0.5 * np.sin(d * 0.85) > 0.55).astype(float)
    f = fbm(h, w, [16, 6])
    t = np.clip(0.30 + 0.38 * band + 0.32 * f, 0, 1)[..., None]
    rgb = np.array((52, 46, 38)) * (1 - t) + np.array((146, 128, 94)) * t
    return Image.fromarray(rgb.astype(np.uint8)).convert("RGBA")

atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 255))
glowmask = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
def put(name, im):
    x, y, w, h = REG[name]
    atlas.paste(im, (x, y))
put("hide", scale_tex(128, 192))
put("belly", belly_tex(64, 128))
put("plate", scale_tex(64, 64, cw=6, ch=5, dark=(12, 12, 15), light=(66, 62, 60)))
put("claw", scale_tex(64, 64, cw=5, ch=5, dark=(12, 10, 9), light=(88, 76, 62)))

def flat(name, base, jitter):
    x, y, w, h = REG[name]
    for i in range(w):
        for j in range(h):
            k = random.randint(-jitter, jitter)
            atlas.putpixel((x + i, y + j), tuple(max(0, min(255, c + k)) for c in base) + (255,))
flat("teeth", (226, 217, 195), 10)
flat("eye", (255, 168, 40), 12)
flat("mouth", (96, 26, 30), 14)
gx, gy, gw, gh = REG["glow"]
for i in range(gw):
    for j in range(gh):
        r = math.hypot(i - gw / 2, j - gh / 2) / (gw / 2)
        tt = max(0.0, 1.0 - r)
        col = (int(70 + 185 * tt * tt), int(175 + 80 * tt), 255, 255)
        atlas.putpixel((gx + i, gy + j), col); glowmask.putpixel((gx + i, gy + j), col)
ex, ey, ew, eh = REG["eye"]
for i in range(ew):
    for j in range(eh):
        glowmask.putpixel((ex + i, ey + j), atlas.getpixel((ex + i, ey + j)))
atlas.save(f"{RES}/textures/entity/godzilla_minus_one.png")
glowmask.save(f"{RES}/textures/entity/godzilla_minus_one_glowmask.png")

# =====================================================================================  GEOMETRY
# Units: 1 = 1/16 block. Y up. Model faces -Z (front = negative z). Symmetric about x = 0.
bones, BONE, SHAPES, GLOW_SHAPES = [], {}, [], []

def add_bone(name, parent, pivot):
    b = {"name": name, "pivot": [round(float(p), 2) for p in pivot], "cubes": []}
    if parent:
        b["parent"] = parent
    bones.append(b); BONE[name] = b

def pick_uv(mat, fw, fh):
    rx, ry, rw, rh = REG[mat]
    uw, uh = max(1, min(int(round(fw)), rw)), max(1, min(int(round(fh)), rh))
    return {"uv": [rx + random.randint(0, rw - uw), ry + random.randint(0, rh - uh)], "uv_size": [uw, uh]}

def add_cube(bone, center, size, rot=(0, 0, 0), mat="hide", front=None, faces=None, inflate=0.0, record=True):
    """center=(x,y,z) size=(w,h,d); rot=(pitch,yaw,roll) degrees, INTENDED sense: +pitch = top tips forward (-z)."""
    w, h, d = size
    c = {"origin": [round(center[0] - w / 2, 2), round(center[1] - h / 2, 2), round(center[2] - d / 2, 2)],
         "size": [round(w, 2), round(h, 2), round(d, 2)]}
    if any(rot):
        c["pivot"] = [round(v, 2) for v in center]
        c["rotation"] = [round(LEAN_SIGN * rot[0], 2), round(rot[1], 2), round(rot[2], 2)]
    if inflate:
        c["inflate"] = inflate
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    fm = {}
    for f, (fw, fh) in dims.items():
        m = (faces or {}).get(f) or (front if (front and f == "north") else mat)
        fm[f] = m
    c["uv"] = {f: pick_uv(fm[f], dims[f][0], dims[f][1]) for f in dims}
    BONE[bone]["cubes"].append(c)
    (SHAPES if record else GLOW_SHAPES).append((center, size, rot, mat, fm))

# ---- skeleton -------------------------------------------------------------------------------------------------
def tail_pt(s):                      # centre line of the low tail: (z, y)
    return (36 + 280 * s, 145 - 125 * s + 70 * s ** 3)
def tail_rx(s): return 46 * (1 - s) ** 0.85 + 4
def tail_rr(s): return 44 * (1 - s) ** 0.85 + 4
TAIL_S = [0.0, 0.14, 0.29, 0.45, 0.62, 0.80, 1.0]

add_bone("root", None, (0, 0, 0))
add_bone("body", "root", (0, 150, 15))
add_bone("torso", "body", (0, 215, 0))
add_bone("head", "torso", (0, 322, -70))
add_bone("jaw", "head", (0, 306, -98))
add_bone("mouth_glow", "head", (0, 304, -112))
for s, x in (("left", -1), ("right", 1)):
    add_bone(f"arm_{s}", "torso", (x * 58, 288, -44))
    add_bone(f"leg_{s}", "root", (x * 38, 150, 8))
for i in range(6):
    z, y = tail_pt(TAIL_S[i])
    add_bone(f"tail_{i + 1}", "body" if i == 0 else f"tail_{i}", (0, y, z))

# ---- voxel sculpt ---------------------------------------------------------------------------------------------
V = 8
xs = (np.arange(-12, 12) + 0.5) * V
ys = (np.arange(0, 46) + 0.5) * V
zs = (np.arange(-20, 43) + 0.5) * V
NX, NY, NZ = len(xs), len(ys), len(zs)
X, Y, Z = np.meshgrid(xs, ys, zs, indexing="ij")
bone_id = np.full(X.shape, -1, dtype=int)
BONE_IDS = {}

def blob(p0, p1, r0, r1, xc=0.0):
    """Tapered capsule from p0=(z,y) to p1=(z,y); radii=(lateral x, radial in the y-z plane); centred at x=xc."""
    (z0, y0), (z1, y1) = p0, p1
    az, ay = z1 - z0, y1 - y0
    L2 = az * az + ay * ay
    t = np.clip(((Z - z0) * az + (Y - y0) * ay) / L2, 0, 1) if L2 > 0 else np.zeros_like(Z)
    cz, cy = z0 + t * az, y0 + t * ay
    rx = r0[0] + (r1[0] - r0[0]) * t
    rr = r0[1] + (r1[1] - r0[1]) * t
    return ((X - xc) / rx) ** 2 + (np.hypot(Z - cz, Y - cy) / rr) ** 2 <= 1.0

def claim(bone, mask):
    BONE_IDS.setdefault(bone, len(BONE_IDS))
    m = mask & (bone_id < 0)
    bone_id[m] = BONE_IDS[bone]

# priority order: head, jaw, arms, legs, spine, tail
claim("head", blob((-92, 334), (-98, 332), (30, 27), (28, 25)))                      # skull
claim("head", blob((-100, 326), (-128, 321), (22, 16), (16, 11)))                    # snout
claim("head", blob((-100, 347), (-112, 346), (28, 5), (24, 5)))                      # brow ridge
claim("jaw", blob((-96, 298), (-126, 296), (17, 7), (13, 6)))                        # lower jaw
for sx, side in ((-1, "left"), (1, "right")):
    claim(f"arm_{side}", blob((-44, 290), (-50, 240), (15, 15), (12, 12), xc=sx * 60))   # upper arm
    claim(f"arm_{side}", blob((-50, 240), (-82, 222), (12, 12), (10, 10), xc=sx * 62))   # forearm
    claim(f"arm_{side}", blob((-84, 222), (-92, 218), (11, 10), (9, 9), xc=sx * 62))     # hand
for sx, side in ((-1, "left"), (1, "right")):
    claim(f"leg_{side}", blob((8, 150), (-14, 82), (32, 35), (25, 27), xc=sx * 37))      # thigh
    claim(f"leg_{side}", blob((-14, 82), (10, 26), (25, 26), (17, 17), xc=sx * 37))      # shin
    claim(f"leg_{side}", blob((14, 12), (-44, 10), (26, 11), (20, 9), xc=sx * 37))       # foot pad
SPINE = [  # (p0, p1, r0, r1)  -> body below y=215, torso above
    ((24, 150), (14, 190), (58, 50), (58, 48)),      # pelvis
    ((14, 190), (6, 230), (54, 47), (54, 45)),       # waist
    ((6, 230), (-14, 274), (60, 50), (62, 52)),      # chest
    ((-14, 274), (-34, 304), (60, 48), (48, 40)),    # shoulders
    ((8, 290), (0, 302), (34, 26), (30, 24)),        # back hump
    ((-34, 304), (-62, 330), (24, 20), (20, 18)),    # neck
]
spine_mask = np.zeros(X.shape, dtype=bool)
for p0, p1, r0, r1 in SPINE:
    spine_mask |= blob(p0, p1, r0, r1)
for sx in (-1, 1):
    spine_mask |= blob((-38, 300), (-38, 296), (16, 16), (16, 16), xc=sx * 54)           # shoulder knobs
claim("body", spine_mask & (Y < 215))
claim("torso", spine_mask & (Y >= 215))
NSUB = 18
for n in range(NSUB):
    s0, s1 = n / NSUB, (n + 1) / NSUB
    sm = (s0 + s1) / 2
    seg = max(1, min(6, 1 + sum(1 for t in TAIL_S[1:-1] if sm >= t)))
    claim(f"tail_{seg}", blob(tail_pt(s0), tail_pt(s1), (tail_rx(s0), tail_rr(s0)), (tail_rx(s1), tail_rr(s1))))

solid = bone_id >= 0
pad = np.pad(bone_id, 1, constant_values=-2)
same_all = np.ones(bone_id.shape, dtype=bool)
for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
    nb = pad[1 + dx:1 + dx + NX, 1 + dy:1 + dy + NY, 1 + dz:1 + dz + NZ]
    same_all &= (nb == bone_id)                         # a voxel is hidden only if all 6 neighbours are the same bone
surface = solid & ~same_all
ID_TO_BONE = {v: k for k, v in BONE_IDS.items()}

def face_mat(bone, face, y):
    if bone == "torso" and face == "north" and 215 <= y < 295:
        return "belly"
    if bone.startswith("leg_"):
        inner = (face == "east" and bone == "leg_left") or (face == "west" and bone == "leg_right")
        if inner:
            return "belly"
    if bone.startswith("tail_") and face == "down":
        return "belly"
    if bone == "jaw" and face == "down":
        return "belly"
    return "hide"

voxel_boxes = 0
for j in range(NY):
    for k in range(NZ):
        i = 0
        while i < NX:
            b = bone_id[i, j, k]
            if b >= 0 and surface[i, j, k]:
                i2 = i
                while i2 + 1 < NX and surface[i2 + 1, j, k] and bone_id[i2 + 1, j, k] == b:
                    i2 += 1
                x0, x1 = xs[i] - V / 2, xs[i2] + V / 2
                bn = ID_TO_BONE[b]
                faces = {f: face_mat(bn, f, ys[j]) for f in ("north", "south", "east", "west", "up", "down")}
                add_cube(bn, ((x0 + x1) / 2, ys[j], zs[k]), (x1 - x0, V, V), mat="hide", faces=faces)
                voxel_boxes += 1
                i = i2 + 1
            else:
                i += 1

# ---- detail cubes: feet, hands, eyes, teeth, mouth -----------------------------------------------------------
for sx, side in ((-1, "left"), (1, "right")):
    x = sx * 37
    for dx in (-15, -5, 5, 15):                                                        # four toes + claws
        add_cube(f"leg_{side}", (x + dx, 7, -56), (10, 11, 22), mat="hide")
        add_cube(f"leg_{side}", (x + dx, 5, -71), (6, 7, 11), (-8, 0, 0), mat="claw")
    for dx in (-8, 0, 8):                                                              # three fingers + claws
        add_cube(f"arm_{side}", (sx * 62 + dx, 214, -102), (5, 6, 16), (-10, 0, 0), mat="hide")
        add_cube(f"arm_{side}", (sx * 62 + dx, 211, -114), (3, 3, 9), (-14, 0, 0), mat="claw")
    add_cube("head", (sx * 24, 341, -112), (5, 5, 3), mat="eye")
    add_cube("head", (sx * 7, 326, -129), (4, 3, 3), mat="plate")                      # nostrils
add_cube("head", (0, 311, -112), (30, 2, 34), mat="mouth")                             # mouth roof
add_cube("jaw", (0, 305, -112), (22, 2, 32), mat="mouth")                              # tongue
add_cube("mouth_glow", (0, 307, -112), (22, 5, 28), mat="glow", record=False)
for dx in (-12, -6, 0, 6, 12):
    add_cube("head", (dx, 309, -127), (4, 7, 4), mat="teeth")                          # upper front teeth
    add_cube("jaw", (dx * 0.9, 306, -122), (4, 7, 4), mat="teeth")                     # lower front teeth
for sx in (-1, 1):
    for k in range(5):
        add_cube("head", (sx * 17, 309, -118 + 7 * k), (4, 7, 4), mat="teeth")
        add_cube("jaw", (sx * 14, 306, -114 + 7 * k + 3), (4, 7, 4), mat="teeth")

# ---- dorsal plates: 14 feathered-spike clusters, tail tip (0) -> upper back (13) --------------------------------------
def surface_top(z):
    """Highest solid voxel top near x=0 at model z (spine + tail only)."""
    k = int(math.floor(z / V)) + 20
    k = max(0, min(NZ - 1, k))
    col = np.zeros(NY, dtype=bool)
    for name in ["body", "torso"] + [f"tail_{n}" for n in range(1, 7)]:
        if name in BONE_IDS:
            col |= (bone_id[NX // 2 - 1:NX // 2 + 1, :, k] == BONE_IDS[name]).any(axis=0)
    js = np.where(col)[0]
    return (js.max() + 1) * V if len(js) else 150

def spike(bone, bz, by, H, w, tilt, x=0.0, roll=0.0, mat="plate", inflate=0.0, record=True, parts=(0.34, 0.28, 0.22, 0.16)):
    cz, cy, cur = bz, by, tilt
    for i, f in enumerate(parts):
        h = H * f
        ww = max(2.5, w * (1 - 0.28 * i))
        dd = max(3.0, w * 1.3 * (1 - 0.3 * i))
        t = math.radians(cur)
        add_cube(bone, (x, cy + h / 2 * math.cos(t), cz - h / 2 * math.sin(t)), (ww, h, dd), (cur, 0, roll), mat,
                 inflate=inflate, record=record)
        cy += h * math.cos(t); cz -= h * math.sin(t); cur -= 5

PLATE_DEF = []
for s, hgt in zip((0.95, 0.85, 0.75, 0.65, 0.55, 0.45), (16, 22, 30, 38, 46, 54)):
    z = tail_pt(s)[0]
    seg = max(1, min(6, 1 + sum(1 for t in TAIL_S[1:-1] if s >= t)))
    PLATE_DEF.append((z, surface_top(z) - 3, hgt, f"tail_{seg}"))
for z, hgt, parent in zip((56, 48, 36, 22, 6, -10, -26, -42), (62, 76, 88, 96, 92, 80, 64, 50),
                          ("body", "body", "torso", "torso", "torso", "torso", "torso", "torso")):
    PLATE_DEF.append((z, surface_top(z) - 3, hgt, parent))
assert len(PLATE_DEF) == 14
PLATE_FWD, PLATE_UP = [], []

for i, (z, y, hgt, parent) in enumerate(PLATE_DEF):
    for glow in (False, True):
        bn = f"plate_glow_{i:02d}" if glow else f"plate_{i:02d}"
        add_bone(bn, parent, (0, y, z))
        mat, infl, rec = ("glow", 1.0, False) if glow else ("plate", 0.0, True)
        w = 5 + hgt * 0.07
        tilt = -(24 + (i % 3) * 4)
        spike(bn, z, y, hgt, w, tilt, mat=mat, inflate=infl, record=rec)                       # main spike
        t0 = math.radians(tilt)
        for sx in (-1, 1):                                                                      # side branches
            for frac, hb in ((0.30, 0.42), (0.55, 0.32)):
                bz, by = z - frac * hgt * math.sin(t0), y + frac * hgt * math.cos(t0)
                spike(bn, bz, by, hgt * hb, max(3.0, w * 0.55), tilt - 20, x=sx * (w * 0.5 + 3), roll=sx * 30,
                      mat=mat, inflate=infl, record=rec, parts=(0.55, 0.45))
        spike(bn, z + 8, y - 2, hgt * 0.6, w * 0.8, tilt - 8, mat=mat, inflate=infl, record=rec, parts=(0.5, 0.3, 0.2))   # rear spike
    PLATE_FWD.append(-z / 16.0)
    PLATE_UP.append((y + hgt * 0.5) / 16.0)

geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.godzilla_minus_one", "texture_width": 256, "texture_height": 256,
                    "visible_bounds_width": 64, "visible_bounds_height": 48, "visible_bounds_offset": [0, 10, 0]},
    "bones": [{k: v for k, v in b.items() if not (k == "cubes" and not v)} for b in bones]}]}
with open(f"{RES}/geo/godzilla_minus_one.geo.json", "w") as f:
    json.dump(geo, f, separators=(",", ":"))

MOUTH_FWD, MOUTH_UP = 7.5, 19.0
def arr(v):
    return ", ".join(f"{x:.3f}" for x in v)
with open(f"{JAVA}/PlateProfile.java", "w") as f:
    f.write(f"""package com.example.godzilla.entity;

/**
 * GENERATED by tools/gen_assets.py. Plate positions (blocks, relative to the feet) for particles, plus the mouth.
 * Index 0 = tail tip ... index {len(PLATE_DEF) - 1} = upper back. FORWARD is along the facing direction (negative = behind).
 * If you replace the model, edit these numbers to match your plates and mouth.
 */
public final class PlateProfile {{
    private PlateProfile() {{}}
    public static final double[] FORWARD = {{{arr(PLATE_FWD)}}};
    public static final double[] UP = {{{arr(PLATE_UP)}}};
    public static final double MOUTH_FORWARD = {MOUTH_FWD};
    public static final double MOUTH_UP = {MOUTH_UP};
}}
""")

# =====================================================================================  ANIMATIONS
def t(x):
    return str(float(x))

class Anim:
    def __init__(self, name, length, loop):
        self.name, self.length, self.loop, self.b = name, length, loop, {}
    def rot(self, bone, keys):
        self.b.setdefault(bone, {})["rotation"] = {t(k): v for k, v in keys}; return self
    def pos(self, bone, keys):
        self.b.setdefault(bone, {})["position"] = {t(k): v for k, v in keys}; return self
    def out(self):
        return {f"animation.godzilla_minus_one.{self.name}": {"loop": self.loop, "animation_length": self.length, "bones": self.b}}

def shake(start, end, step, a0, a1, axis=2):
    keys, k, i = [], start, 0
    while k <= end + 1e-6:
        a = a0 + (a1 - a0) * ((k - start) / max(1e-6, end - start))
        v = [0, 0, 0]; v[axis] = a if i % 2 == 0 else -a
        keys.append((round(k, 3), v)); k += step; i += 1
    return keys

Z = [0, 0, 0]
anims = []

a = Anim("idle", 4.0, True)
a.rot("body", [(0, Z), (2, [1.0, 0, 0]), (4, Z)])
a.rot("torso", [(0, Z), (2, [-1.5, 0, 0]), (4, Z)])
a.rot("head", [(0, Z), (1, [0, 4, 0]), (2, Z), (3, [0, -4, 0]), (4, Z)])
a.rot("jaw", [(0, Z), (2, [3, 0, 0]), (4, Z)])
for i, amp in enumerate((3, 4, 5, 6, 7, 8), start=1):
    sgn = 1 if i % 2 else -1
    a.rot(f"tail_{i}", [(0, Z), (1, [0, sgn * amp, 0]), (2, Z), (3, [0, -sgn * amp, 0]), (4, Z)])
a.rot("arm_left", [(0, Z), (2, [4, 0, 0]), (4, Z)])
a.rot("arm_right", [(0, Z), (2, [-4, 0, 0]), (4, Z)])
anims.append(a)

a = Anim("walk", 2.0, True)   # a foot plants every 1.0s = every 20 ticks (GodzillaTuning.STEP_INTERVAL_TICKS)
a.rot("leg_left", [(0, [-24, 0, 0]), (0.5, Z), (1, [24, 0, 0]), (1.5, Z), (2, [-24, 0, 0])])
a.rot("leg_right", [(0, [24, 0, 0]), (0.5, Z), (1, [-24, 0, 0]), (1.5, Z), (2, [24, 0, 0])])
a.pos("leg_left", [(0, Z), (1, Z), (1.5, [0, 8, 0]), (2, Z)])
a.pos("leg_right", [(0, Z), (0.5, [0, 8, 0]), (1, Z), (2, Z)])
a.pos("body", [(0, Z), (0.5, [0, -4, 0]), (1, Z), (1.5, [0, -4, 0]), (2, Z)])
a.rot("body", [(0, [0, 0, 2]), (1, [0, 0, -2]), (2, [0, 0, 2])])
a.rot("torso", [(0, [0, 4, 0]), (1, [0, -4, 0]), (2, [0, 4, 0])])
a.rot("head", [(0, [0, -3, 0]), (1, [0, 3, 0]), (2, [0, -3, 0])])
for i, amp in enumerate((5, 7, 9, 11, 13, 15), start=1):
    sgn = -1 if i % 2 else 1
    a.rot(f"tail_{i}", [(0, [0, sgn * amp, 0]), (1, [0, -sgn * amp, 0]), (2, [0, sgn * amp, 0])])
a.rot("arm_left", [(0, [14, 0, 0]), (1, [-14, 0, 0]), (2, [14, 0, 0])])
a.rot("arm_right", [(0, [-14, 0, 0]), (1, [14, 0, 0]), (2, [-14, 0, 0])])
anims.append(a)

a = Anim("roar", 2.5, False)  # 50 ticks = GodzillaTuning.ROAR_TICKS
a.rot("torso", [(0, Z), (0.6, [-10, 0, 0]), (2.0, [-10, 0, 0]), (2.5, Z)])
a.rot("head", [(0, Z), (0.6, [-28, 0, 0]), (2.0, [-28, 0, 0]), (2.5, Z)])
a.rot("jaw", [(0, Z), (0.7, [42, 0, 0]), (2.0, [42, 0, 0]), (2.4, Z)])
a.rot("arm_left", [(0, Z), (0.6, [-25, 0, 20]), (2.0, [-25, 0, 20]), (2.5, Z)])
a.rot("arm_right", [(0, Z), (0.6, [-25, 0, -20]), (2.0, [-25, 0, -20]), (2.5, Z)])
a.rot("tail_1", [(0, Z), (0.6, [-6, 0, 0]), (2.0, [-6, 0, 0]), (2.5, Z)])
a.rot("body", [(0, Z)] + shake(0.7, 2.0, 0.1, 0.8, 0.8) + [(2.5, Z)])
anims.append(a)

a = Anim("charge", 4.0, "hold_on_last_frame")  # 80 ticks = GodzillaTuning.CHARGE_TICKS
a.rot("torso", [(0, Z), (3.0, [-9, 0, 0]), (4.0, [-9, 0, 0])])
a.rot("head", [(0, Z), (3.0, [-22, 0, 0]), (4.0, [-22, 0, 0])])
a.rot("jaw", [(0, Z), (2.5, Z), (3.5, [28, 0, 0]), (4.0, [28, 0, 0])])
a.rot("arm_left", [(0, Z), (3.0, [-12, 0, 10]), (4.0, [-12, 0, 10])])
a.rot("arm_right", [(0, Z), (3.0, [-12, 0, -10]), (4.0, [-12, 0, -10])])
a.rot("body", [(0, Z)] + shake(0.5, 4.0, 0.125, 0.3, 1.2))
anims.append(a)

a = Anim("fire", 1.0, True)
a.rot("torso", [(0, [-8, 0, 0]), (1, [-8, 0, 0])])
a.rot("head", [(0, [-12, 0, 0]), (1, [-12, 0, 0])])
a.rot("jaw", [(0, [45, 0, 0]), (0.5, [48, 0, 0]), (1, [45, 0, 0])])
a.rot("arm_left", [(0, [-10, 0, 14]), (1, [-10, 0, 14])])
a.rot("arm_right", [(0, [-10, 0, -14]), (1, [-10, 0, -14])])
a.rot("body", [(0, [0, 0, 0.7]), (0.25, [0, 0, -0.7]), (0.5, [0, 0, 0.7]), (0.75, [0, 0, -0.7]), (1, [0, 0, 0.7])])
anims.append(a)

a = Anim("vent", 2.0, "hold_on_last_frame")  # 40 ticks = GodzillaTuning.VENT_TICKS
a.rot("torso", [(0, [-8, 0, 0]), (1.5, Z), (2, Z)])
a.rot("head", [(0, [-12, 0, 0]), (1.5, Z), (2, Z)])
a.rot("jaw", [(0, [45, 0, 0]), (1.2, Z), (2, Z)])
anims.append(a)

a = Anim("bite", 1.5, "hold_on_last_frame")  # 30 ticks, jaws snap at 0.7s = tick 14 (BITE_STRIKE_TICK)
a.rot("torso", [(0, Z), (0.6, [-9, 0, 0]), (0.7, [18, 0, 0]), (0.9, [18, 0, 0]), (1.5, Z)])
a.rot("head", [(0, Z), (0.6, [-14, 0, 0]), (0.7, [20, 0, 0]), (0.9, [20, 0, 0]), (1.5, Z)])
a.rot("jaw", [(0, Z), (0.55, [48, 0, 0]), (0.7, Z), (1.5, Z)])
a.pos("body", [(0, Z), (0.6, [0, 0, 4]), (0.7, [0, 0, -14]), (0.9, [0, 0, -14]), (1.5, Z)])
a.rot("arm_left", [(0, Z), (0.6, [-15, 0, 0]), (0.7, [20, 0, 0]), (1.5, Z)])
a.rot("arm_right", [(0, Z), (0.6, [-15, 0, 0]), (0.7, [20, 0, 0]), (1.5, Z)])
anims.append(a)

a = Anim("tail_swipe", 2.2, "hold_on_last_frame")  # 44 ticks, tail connects at 1.1s = tick 22 (TAIL_STRIKE_TICK)
a.rot("body", [(0, Z), (1.0, [0, -45, 0]), (1.1, [0, -45, 0]), (1.35, [0, 70, 0]), (1.6, [0, 70, 0]), (2.2, Z)])
for i, (w0, w1) in enumerate(((35, -55), (30, -70), (30, -80), (30, -85), (25, -85), (20, -80)), start=1):
    a.rot(f"tail_{i}", [(0, Z), (1.0, [0, w0, 0]), (1.1, [0, w0, 0]), (1.35, [0, w1, 0]), (1.6, [0, w1, 0]), (2.2, Z)])
a.rot("torso", [(0, Z), (1.0, [3, 0, 0]), (2.2, Z)])
a.rot("head", [(0, Z), (1.0, [0, -20, 0]), (1.35, [0, 30, 0]), (2.2, Z)])
anims.append(a)

a = Anim("death", 5.0, "hold_on_last_frame")  # 100 ticks = GodzillaEntity.DEATH_TICKS
a.rot("root", [(0, Z), (1.5, [0, 0, -6]), (3.0, [0, 0, -92]), (3.3, [0, 0, -86]), (3.6, [0, 0, -90]), (5, [0, 0, -90])])
a.rot("head", [(0, Z), (1.0, [-30, 0, 0]), (3.0, [10, 0, 0]), (5, [10, 0, 0])])
a.rot("jaw", [(0, Z), (1, [40, 0, 0]), (5, [40, 0, 0])])
a.rot("arm_left", [(0, Z), (2, [-20, 0, 15]), (5, [-20, 0, 15])])
a.rot("arm_right", [(0, Z), (2, [-20, 0, -15]), (5, [-20, 0, -15])])
anims.append(a)

merged = {}
for an in anims:
    merged.update(an.out())
with open(f"{RES}/animations/godzilla_minus_one.animation.json", "w") as f:
    json.dump({"format_version": "1.8.0", "animations": merged}, f, indent=1)

cube_count = sum(len(b["cubes"]) for b in bones)
print(f"geo: {len(bones)} bones, {cube_count} cubes | animations: {len(merged)} | plates: {len(PLATE_DEF)}")


# =====================================================================================  SHADED 3D RENDER (preview only)
if RENDER:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.patches import Polygon

    def corners3(center, size, rot):
        cx, cy, cz = center; w, h, d = size
        out = []
        for sx in (-.5, .5):
            for sy in (-.5, .5):
                for sz in (-.5, .5):
                    x, y, z = sx * w, sy * h, sz * d
                    th = math.radians(rot[0])
                    y2, z2 = z * math.sin(th) + y * math.cos(th), z * math.cos(th) - y * math.sin(th)
                    ro = math.radians(rot[2])
                    x3, y3 = x * math.cos(ro) - y2 * math.sin(ro), x * math.sin(ro) + y2 * math.cos(ro)
                    out.append(np.array([cx + x3, cy + y3, cz + z2]))
        return out

    FACES = [((0, 1, 3, 2), "west"), ((4, 6, 7, 5), "east"), ((0, 4, 5, 1), "down"),
             ((2, 3, 7, 6), "up"), ((0, 2, 6, 4), "north"), ((1, 5, 7, 3), "south")]
    BASE = {"hide": (0.30, 0.285, 0.27), "belly": (0.40, 0.35, 0.27), "plate": (0.13, 0.125, 0.14), "teeth": (0.90, 0.87, 0.76),
            "eye": (1.0, 0.65, 0.16), "mouth": (0.40, 0.10, 0.12), "claw": (0.10, 0.08, 0.07), "glow": (0.35, 0.80, 1.0)}

    def render(path, azim, elev, charged, bg="#c9cbd0"):
        a, e = math.radians(azim), math.radians(elev)
        def view(p):
            x1 = p[0] * math.cos(a) + p[2] * math.sin(a)
            z1 = -p[0] * math.sin(a) + p[2] * math.cos(a)
            return np.array([-x1, p[1] * math.cos(e) + z1 * math.sin(e), z1 * math.cos(e) - p[1] * math.sin(e)])
        polys = []
        rnd = random.Random(5)
        for center, size, rot, mat, fm in SHAPES + (GLOW_SHAPES if charged else []):
            emissive = mat == "glow"
            pts = [view(c) for c in corners3(center, size, rot)]
            body_c = sum(pts) / 8.0
            for f, name in FACES:
                q = [pts[i] for i in f]
                n = np.cross(q[1] - q[0], q[2] - q[0])
                if np.linalg.norm(n) < 1e-9: continue
                n = n / np.linalg.norm(n)
                cen = sum(q) / 4.0
                if np.dot(n, cen - body_c) < 0: n = -n
                if n[2] > 0.0 and not emissive: continue
                light = np.array([0.45, 0.75, -0.55]); light /= np.linalg.norm(light)
                shade = 0.40 + 0.75 * max(0.0, float(np.dot(n, light)))
                col = np.array(BASE[fm.get(name, mat)]) * (1.0 if emissive else shade * (1 + rnd.uniform(-0.12, 0.12)))
                polys.append((cen[2], [(p[0], p[1]) for p in q], np.clip(col, 0, 1), emissive))
        polys.sort(key=lambda t: -t[0])
        fig, ax = plt.subplots(figsize=(12, 9))
        fig.patch.set_facecolor(bg); ax.set_facecolor(bg)
        for _, xy, col, em in polys:
            ax.add_patch(Polygon(xy, closed=True, fc=col, ec=(0, 0, 0, 0.30) if not em else (0.8, 0.95, 1, 0.9), lw=0.2))
        ax.set_aspect("equal"); ax.set_xlim(-260, 260); ax.set_ylim(-40, 400); ax.axis("off")
        fig.savefig(path, dpi=100, bbox_inches="tight", facecolor=fig.get_facecolor()); plt.close(fig)

    render(f"{ROOT}/tools/preview_3d.png", 38, 12, False)
    render(f"{ROOT}/tools/preview_3d_side.png", 90, 6, False)
    render(f"{ROOT}/tools/preview_3d_charged.png", 38, 12, True, bg="#14161c")
    print("3D renders written")
