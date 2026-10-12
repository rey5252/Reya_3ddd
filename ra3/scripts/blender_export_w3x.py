"""Fortress Protocol: convert the tower .glb models to RA3 W3X (Blender + OpenSAGE W3D/W3X plugin).

Run (Blender 3.6 / 4.x, the add-on "io_mesh_w3d" from github.com/OpenSAGE/OpenSAGE.BlenderPlugin
installed and enabled, or pointed to with --addon-path):

    blender -b -P blender_export_w3x.py -- <glb_dir | file.glb> <out_dir> [options]

    options:
      --scale F          RA3 world units per glTF unit (metre). Default 10 (see "Scale" below).
      --yaw DEG          rotation around the vertical axis for ALL models, degrees; overrides
                         the per-model YAW_FIX table below (default: use the table).
      --shader NAME      FX shader written for every material (default DefaultW3D.fx).
      --only A,B         convert only these files (TowerId or FT name, without .glb).
      --individual-files plugin option: hierarchy + every mesh in its own .w3x
                         (the container file then pulls them in with ART: includes).
      --drop-fx-meshes   do not export the tiny FX_* marker meshes at all (bones stay).
      --keep-case        keep node-name case for bones (default: UPPERCASE, like vanilla RA3 art).
      --save-blend DIR   also save the prepared scene as DIR/<FTName>.blend (for manual
                         shader / texture work and re-export from the Blender UI).
      --addon-path DIR   folder that CONTAINS the io_mesh_w3d package (e.g. a git clone of the
                         plugin); used when the add-on is not installed in Blender.

What it does for every .glb:
  1. empties the scene, imports the glTF with Blender's built-in importer
     (that importer already converts glTF Y-up to Blender/W3D Z-up);
  2. builds one armature "<FTName>_SKL": every glTF node (Base, Turret, Barrel, Emitter, FX_*, ...)
     becomes a bone = W3X pivot, with the same parent/child structure (the wrapper root node named
     after the model is dropped - its children hang under the W3X ROOTTRANSFORM pivot);
  3. splits every node mesh per material (one W3X mesh = one FX shader), moves the vertices into
     the bone's local space and parents the mesh rigidly to that bone (parent_type BONE) - exactly
     the layout the plugin's own importer produces for rigid W3X meshes;
  3b. rotates the model by YAW_FIX[<TowerId>] so the turret's rest direction is +X;
  4. FX_* marker meshes are hidden (W3X GEOMETRY_TYPE_HIDDEN) and cast no shadow;
  5. renames materials "<name>.<shader>" (the plugin takes the part after the first dot as the
     FX shader name) and copies the PBR colours into the fields the plugin exports;
  6. exports <out_dir>/<FTName>.w3x with bpy.ops.export_mesh.westwood_w3d(file_format='W3X',
     export_mode='HM').  The container (W3DContainer id) is the file name = <FTName>.

Naming (SPEC.md, section "3D-моделі"): faction prefix Allied/Soviet/Japan is replaced by "FT":
  AlliedPrismSpire -> FTPrismSpire, JapanTenguSpireNanocore -> FTTenguSpireNanocore.

Scale: the generators use 1 unit ~ 1 m. RA3 world units are not metres; vanilla defences are
~40-110 units tall with 20-28 unit footprint radius (see <Geometry> in the tower XMLs), while our
towers are 3.5-10 m tall on a 6 x 6 m plate, so 10 units per metre matches the existing XML
geometry (plate half-size 3 m -> 30 units, Prism 9 m -> 90 units).
# VERIFY: compare in-game with a vanilla tower; tune with --scale or with Scale= on the Draw module.

Nothing here touches the generators or the .glb files.  Pure stdlib + bpy.
"""
import math
import os
import re
import sys
import traceback

import bpy
import bmesh
from mathutils import Matrix

# ----------------------------------------------------------------------------- settings

DEFAULT_SCALE = 10.0          # RA3 world units per metre (see docstring)
DEFAULT_SHADER = "DefaultW3D.fx"
# VERIFY: DefaultW3D.fx is the plugin's default and exists in SAGE games, but vanilla RA3
# structures use faction shaders (e.g. ObjectsAllied.fx / ObjectsSoviet.fx / ObjectsJapan.fx)
# with DiffuseTexture + NormalMap + SpecMap (+ RecolorTexture for house colour).  Our models carry
# only flat PBR colours, so after export assign textures/shaders as the plugin requires.
HIERARCHY_SUFFIX = "_SKL"     # vanilla convention: XXX_SKN container + XXX_SKL hierarchy
FACTION_PREFIXES = ("Allied", "Soviet", "Japan")
MOVING_NODES = ("BASE", "TURRET", "BARREL", "EMITTER")

# Per-model yaw correction, degrees counter-clockwise around Blender/W3D +Z (seen from above),
# applied to the whole model after the glTF import.  W3D/W3X units and turrets conventionally
# face +X; the generators did not all use the same "forward".  Axis mapping of Blender's glTF
# importer (Y-up -> Z-up): glTF +X -> +X, glTF +Y -> +Z, glTF +Z -> -Y, glTF -Z -> +Y.
#   rotation that maps Blender +Y to +X is -90 deg; -Y to +X is +90 deg.
# Models not listed: 0 (forward already +X, or rotationally symmetric / no turret).
YAW_FIX = {
    "AlliedCryoBastion": 0.0,          # turret forward = glTF +X  -> already +X
    "JapanTenguSpireNanocore": 0.0,    # nose = glTF +X            -> already +X
    "SovietFlakNest": -90.0,           # guns rest along glTF -Z = Blender +Y -> rotate -90 to +X
    # Aegis head is modelled turned 45 deg toward glTF (+x,+z) = Blender (+x,-y), while its Turret
    # node has no rotation: +45 makes the head look along +X at rest.
    "AlliedAegisSpire": 45.0,          # VERIFY in game (turret rest direction / NaturalTurretAngle)
}
BONE_LEN_M = 0.02             # edit-bone length in metres (irrelevant for W3X, only for display)
ADDON_MODULE = "io_mesh_w3d"


def log(msg=""):
    print(msg, flush=True)


# ----------------------------------------------------------------------------- arguments

USAGE = __doc__.split("What it does")[0]


def parse_args(argv):
    """Plain stdlib parsing of the arguments after '--'."""
    args = argv[argv.index("--") + 1:] if "--" in argv else []
    opts = dict(scale=DEFAULT_SCALE, yaw=None, shader=DEFAULT_SHADER, only=None,
                individual_files=False, drop_fx=False, keep_case=False, save_blend=None,
                addon_path=None)
    pos = []
    i = 0
    while i < len(args):
        a = args[i]

        def value():
            if i + 1 >= len(args):
                raise SystemExit("missing value after %s\n%s" % (a, USAGE))
            return args[i + 1]

        if a in ("-h", "--help"):
            log(USAGE)
            raise SystemExit(0)
        elif a == "--scale":
            opts["scale"] = float(value()); i += 1
        elif a == "--yaw":
            opts["yaw"] = float(value()); i += 1
        elif a == "--shader":
            opts["shader"] = value(); i += 1
        elif a == "--only":
            opts["only"] = {s.strip() for s in value().split(",") if s.strip()}; i += 1
        elif a == "--save-blend":
            opts["save_blend"] = os.path.abspath(value()); i += 1
        elif a == "--addon-path":
            opts["addon_path"] = os.path.abspath(value()); i += 1
        elif a == "--individual-files":
            opts["individual_files"] = True
        elif a == "--drop-fx-meshes":
            opts["drop_fx"] = True
        elif a == "--keep-case":
            opts["keep_case"] = True
        elif a.startswith("--"):
            raise SystemExit("unknown option %s\n%s" % (a, USAGE))
        else:
            pos.append(a)
        i += 1
    if len(pos) != 2:
        raise SystemExit("expected <glb_dir|file.glb> <out_dir>\n%s" % USAGE)
    if not opts["shader"].endswith(".fx"):
        raise SystemExit("--shader must end with .fx (got %r)" % opts["shader"])
    if opts["scale"] <= 0:
        raise SystemExit("--scale must be > 0")
    return os.path.abspath(pos[0]), os.path.abspath(pos[1]), opts


def ft_name(stem):
    """AlliedPrismSpire -> FTPrismSpire, JapanTenguSpireNanocore -> FTTenguSpireNanocore."""
    for p in FACTION_PREFIXES:
        if stem.startswith(p) and len(stem) > len(p):
            return "FT" + stem[len(p):]
    return stem if stem.startswith("FT") else "FT" + stem


def clean_name(name, keep_case):
    name = re.sub(r"\.\d{3}$", "", name)            # Blender duplicate suffix
    name = re.sub(r"[^A-Za-z0-9_]", "_", name)
    return name if keep_case else name.upper()


# ----------------------------------------------------------------------------- add-ons

def have_w3d_export():
    return hasattr(bpy.ops, "export_mesh") and "westwood_w3d" in dir(bpy.ops.export_mesh)


def ensure_addons(addon_path):
    import addon_utils
    if not hasattr(bpy.ops.import_scene, "gltf"):
        addon_utils.enable("io_scene_gltf2", default_set=True)
    if have_w3d_export():
        return True
    if addon_path:
        if os.path.isdir(os.path.join(addon_path, ADDON_MODULE)):
            parent = addon_path
        elif os.path.basename(addon_path.rstrip("/\\")) == ADDON_MODULE:
            parent = os.path.dirname(addon_path.rstrip("/\\"))
        else:
            parent = addon_path
        if parent not in sys.path:
            sys.path.insert(0, parent)
    # legacy add-on name first, then Blender 4.2+ extension names (bl_ext.<repo>.io_mesh_w3d)
    candidates = [ADDON_MODULE] + [m.__name__ for m in addon_utils.modules()
                                   if m.__name__.endswith("." + ADDON_MODULE)]
    for mod in candidates:
        try:
            addon_utils.enable(mod, default_set=True)
        except Exception as exc:  # noqa: BLE001 - report and try the next candidate
            log("  (could not enable %s: %s)" % (mod, exc))
        if have_w3d_export():
            log("  enabled add-on %s" % mod)
            return True
    return False


# ----------------------------------------------------------------------------- scene

def reset_scene():
    """Empty the scene without read_factory_settings (that would also disable the add-ons)."""
    if bpy.context.object is not None and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    for coll in (bpy.data.objects, bpy.data.meshes, bpy.data.armatures, bpy.data.materials,
                 bpy.data.images, bpy.data.actions, bpy.data.cameras, bpy.data.lights,
                 bpy.data.collections):
        for block in list(coll):
            coll.remove(block)
    if hasattr(bpy.data, "orphans_purge"):
        bpy.data.orphans_purge(do_recursive=True)


def import_glb(path):
    before = set(bpy.data.objects)
    res = bpy.ops.import_scene.gltf(filepath=path)
    if "FINISHED" not in res:
        raise RuntimeError("glTF import failed: %s" % res)
    bpy.context.view_layer.update()
    return [o for o in bpy.data.objects if o not in before]


def principled_of(mat):
    if mat is None or not mat.node_tree:
        return None
    for n in mat.node_tree.nodes:
        if n.type == "BSDF_PRINCIPLED":
            return n
    return None


def socket_value(node, *names, default=None):
    for nm in names:
        s = node.inputs.get(nm) if node is not None else None
        if s is not None and not s.is_linked:
            return s.default_value
    return default


def prepare_material(mat, shader, used_names):
    """Rename to '<name>.<shader>' and mirror PBR values into what the plugin exports."""
    if getattr(mat, "_ft_done", False):
        return
    base = re.sub(r"[^A-Za-z0-9_]", "_", mat.name.split(".")[0]) or "mat"
    name, k = base, 1
    while name in used_names:
        k += 1
        name = "%s%d" % (base, k)
    used_names.add(name)
    bsdf = principled_of(mat)
    color = socket_value(bsdf, "Base Color", default=(0.8, 0.8, 0.8, 1.0))
    alpha = socket_value(bsdf, "Alpha", default=1.0)
    metallic = socket_value(bsdf, "Metallic", default=0.0)
    rough = socket_value(bsdf, "Roughness", default=0.6)
    # the plugin writes material.diffuse_color / specular_color / specular_intensity
    # (Blender viewport colours), not the node values - copy them over
    mat.diffuse_color = (color[0], color[1], color[2], alpha)
    spec = [0.04 * (1 - metallic) + c * metallic for c in color[:3]]
    mat.specular_color = spec
    mat.specular_intensity = (2.0 + (1.0 - rough) ** 2 * 98.0) / 200.0   # -> SpecularExponent
    mat.name = "%s.%s" % (name, shader)
    # VERIFY: transparent materials (alpha < 1): the plugin exports Opacity, but RA3 also needs a
    # blend mode / alpha shader; set "Blend mode" in the material's W3D panel before export.


# ----------------------------------------------------------------------------- conversion

def node_tree(objs, model_stem):
    """Return (bone_objects_in_parent_first_order, wrapper_roots)."""
    objset = set(objs)
    wrappers = set()
    for o in objs:
        if o.parent is None and o.type == "EMPTY" and o.children and \
                clean_name(o.name, False) not in MOVING_NODES:
            # meshkit writes a wrapper root named after the model (e.g. "AlliedPrismSpire")
            wrappers.add(o)
    order = []

    def visit(o):
        if o not in wrappers:
            order.append(o)
        for c in sorted(o.children, key=lambda x: x.name):
            if c in objset:
                visit(c)

    for o in sorted((o for o in objs if o.parent is None or o.parent not in objset),
                    key=lambda x: x.name):
        visit(o)
    return order, wrappers


def split_by_material(obj):
    """Yield (bmesh-built Mesh datablock, material) per material used by obj's faces."""
    me = obj.data
    used = sorted({p.material_index for p in me.polygons})
    for idx in used:
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.material_index != idx], context="FACES")
        bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
        for f in bm.faces:
            f.material_index = 0
        new = bpy.data.meshes.new(me.name + "_m%d" % idx)
        bm.to_mesh(new)
        bm.free()
        mat = me.materials[idx] if idx < len(me.materials) else None
        yield new, mat


def convert(glb_path, out_dir, opts):
    stem = os.path.splitext(os.path.basename(glb_path))[0]
    ft = ft_name(stem)
    keep_case = opts["keep_case"]
    s = opts["scale"]
    reset_scene()
    objs = import_glb(glb_path)
    if not objs:
        raise RuntimeError("glTF contains no nodes")
    for o in objs:
        if o.type not in ("MESH", "EMPTY"):
            log("  warning: %s is a %s - ignored" % (o.name, o.type))
    objs = [o for o in objs if o.type in ("MESH", "EMPTY")]
    order, wrappers = node_tree(objs, stem)

    # world transform used for the game: yaw around Z, then uniform scale
    yaw_deg = opts["yaw"] if opts["yaw"] is not None else YAW_FIX.get(stem, 0.0)
    if yaw_deg:
        log("  yaw correction %+g deg" % yaw_deg)
    yaw = Matrix.Rotation(math.radians(yaw_deg), 4, "Z")
    bone_mat, bone_name, used_bone_names = {}, {}, set()
    for o in order:
        loc, rot, _ = (yaw @ o.matrix_world).decompose()
        bone_mat[o] = Matrix.Translation(loc * s) @ rot.to_matrix().to_4x4()
        name = clean_name(o.name, keep_case)
        if name in used_bone_names:
            raise RuntimeError("two nodes map to the same bone name %r" % name)
        used_bone_names.add(name)
        bone_name[o] = name

    # --- armature
    skl = ft + HIERARCHY_SUFFIX
    arm = bpy.data.armatures.new(skl)
    rig = bpy.data.objects.new(skl, arm)
    bpy.context.scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    blen = BONE_LEN_M * s
    for o in order:
        eb = arm.edit_bones.new(bone_name[o])
        eb.head = (0.0, 0.0, 0.0)
        eb.tail = (0.0, 0.0, blen)
        eb.matrix = bone_mat[o]
        par = o.parent
        while par is not None and par in wrappers:
            par = par.parent
        if par is not None and par in bone_name:
            eb.parent = arm.edit_bones[bone_name[par]]
    bpy.ops.object.mode_set(mode="OBJECT")

    # --- meshes, rigidly bound to their bone
    used_mats = set()
    new_meshes = []
    for o in order:
        if o.type != "MESH" or not o.data.polygons:
            continue
        bname = bone_name[o]
        is_fx = bname.upper().startswith("FX_")
        if is_fx and opts["drop_fx"]:
            continue
        to_local = bone_mat[o].inverted() @ Matrix.Scale(s, 4) @ yaw @ o.matrix_world
        parts = list(split_by_material(o))
        for k, (me, mat) in enumerate(parts):
            me.transform(to_local)
            me.materials.clear()
            if mat is not None:
                prepare_material(mat, opts["shader"], used_mats)
                mat["_ft_done"] = True
                me.materials.append(mat)
            if len(parts) == 1:
                oname = bname
            else:
                mname = mat.name.split(".")[0] if mat is not None else "M%d" % k
                oname = clean_name("%s_%s" % (bname, mname), keep_case)
            ob = bpy.data.objects.new(oname, me)
            bpy.context.scene.collection.objects.link(ob)
            ob.parent = rig
            ob.parent_type = "BONE"
            ob.parent_bone = bname
            # bone children sit at the bone tail; cancel that so Blender shows the true position
            ob.matrix_parent_inverse = Matrix.Translation((0.0, -arm.bones[bname].length, 0.0))
            if hasattr(me, "casts_shadow"):
                me.casts_shadow = not is_fx
            if is_fx:
                ob.hide_set(True)       # -> W3X GEOMETRY_TYPE_HIDDEN
                ob.hide_render = True
            new_meshes.append(ob)

    # --- remove the imported originals; keep a flat scene (collections = LODs for the plugin!)
    for o in objs:
        bpy.data.objects.remove(o, do_unlink=True)
    for coll in list(bpy.data.collections):
        bpy.data.collections.remove(coll)
    bpy.data.orphans_purge(do_recursive=True) if hasattr(bpy.data, "orphans_purge") else None
    bpy.context.view_layer.update()
    if not new_meshes:
        raise RuntimeError("no meshes to export")

    # --- export
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, ft + ".w3x")
    if opts["save_blend"]:
        os.makedirs(opts["save_blend"], exist_ok=True)
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(opts["save_blend"], ft + ".blend"),
                                    copy=True)
    for o in bpy.context.selected_objects:
        o.select_set(False)
    # VERIFY: operator id / property names checked against OpenSAGE.BlenderPlugin v0.7.x
    # (io_mesh_w3d/__init__.py: ExportW3D, bl_idname 'export_mesh.westwood_w3d').
    res = bpy.ops.export_mesh.westwood_w3d(
        filepath=out,
        file_format="W3X",
        export_mode="HM",                     # hierarchical model: skeleton + meshes + container
        use_existing_skeleton=False,
        individual_files=opts["individual_files"],
        create_texture_xmls=False)
    if "FINISHED" not in res or not os.path.isfile(out):
        raise RuntimeError("W3X export failed (%s) - see the plugin messages above" % res)

    tris = sum(len(ob.data.polygons) for ob in new_meshes)   # already triangles from glTF
    return dict(src=os.path.basename(glb_path), ft=ft, out=out, bones=len(order), yaw=yaw_deg,
                meshes=len(new_meshes), tris=tris,
                fx=sorted(b for b in used_bone_names if b.upper().startswith("FX_")),
                moving=sorted(b for b in used_bone_names if b.upper() in MOVING_NODES))


def collect_inputs(src, only):
    if os.path.isfile(src):
        files = [src]
    elif os.path.isdir(src):
        files = sorted(os.path.join(src, f) for f in os.listdir(src) if f.lower().endswith(".glb"))
    else:
        raise SystemExit("input not found: %s" % src)
    if only:
        files = [f for f in files
                 if os.path.splitext(os.path.basename(f))[0] in only
                 or ft_name(os.path.splitext(os.path.basename(f))[0]) in only]
    return files


def main():
    src, out_dir, opts = parse_args(sys.argv)
    log("=" * 72)
    log("Fortress Protocol -> W3X   (Blender %s)" % bpy.app.version_string)
    log("  input : %s" % src)
    log("  output: %s" % out_dir)
    log("  scale : %g units per metre, yaw %s, shader %s" % (
        opts["scale"], "per-model table" if opts["yaw"] is None else "%g deg" % opts["yaw"],
        opts["shader"]))
    if not ensure_addons(opts["addon_path"]):
        log("ERROR: OpenSAGE W3D/W3X add-on (%s) is not available." % ADDON_MODULE)
        log("       Install it in Blender (Edit > Preferences > Add-ons / Extensions) or pass")
        log("       --addon-path <folder containing io_mesh_w3d>.")
        sys.exit(2)
    files = collect_inputs(src, opts["only"])
    if not files:
        log("nothing to do: no .glb files found")
        sys.exit(1)
    done, failed = [], []
    for f in files:
        log("-" * 72)
        log("%s -> %s.w3x" % (os.path.basename(f), ft_name(os.path.splitext(os.path.basename(f))[0])))
        try:
            done.append(convert(f, out_dir, opts))
        except Exception as exc:  # noqa: BLE001 - keep going, report at the end
            traceback.print_exc()
            failed.append((os.path.basename(f), str(exc)))
    log("=" * 72)
    log("SUMMARY")
    log("  %-34s %-26s %5s %6s %7s %5s" % ("source", "W3X container", "bones", "meshes", "tris", "yaw"))
    for d in done:
        log("  %-34s %-26s %5d %6d %7d %+5g" % (d["src"], d["ft"], d["bones"], d["meshes"],
                                               d["tris"], d["yaw"]))
        log("      moving bones: %s" % (" ".join(d["moving"]) or "-"))
        log("      FX bones    : %s" % (" ".join(d["fx"]) or "-"))
    for name, err in failed:
        log("  FAILED %-27s %s" % (name, err))
    log("  %d exported, %d failed -> %s" % (len(done), len(failed), out_dir))
    log("  Next: include the files in data/Art/FortressModels.xml (ART:<FTName>.w3x) and point")
    log("  ScriptedModelDraw <Model Name=\"<FTName>\"/> at them (see README, section 3D-моделі).")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
