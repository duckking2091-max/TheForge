"""Make The Forge's resource pack work on every Minecraft 1.21.x client (pack formats 34 to 75).

The editable pack in resource-pack/ is written for 1.21.4+ (item definitions in assets/*/items, equipment in
assets/*/equipment). This script adds version overlays next to it, which each client applies only for its own
pack format, and rewrites pack.mcmeta to declare them:

  overlay_1_21_0   formats 34-45 (1.21.0-1.21.3): classic item models with custom_model_data overrides
                   (the same numbers the plugin puts on items), keeping vanilla armour-trim overrides.
  overlay_1_21_2   formats 42-45 (1.21.2-1.21.3): custom armour models live in models/equipment there.
  overlay_pre_arb  formats 34-69 (1.21.0-1.21.10): model rotations rounded to the angles those clients accept.

Run it before packing (generate_expansion_pack.py --repack calls it). Only overlay folders and pack.mcmeta are
written; the base pack is never touched.
"""
import json, pathlib, shutil, zipfile, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "resource-pack"
CLIENT = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None   # 1.21.11 client jar, for vanilla base models

# formats: 1.21-1.21.1=34, 1.21.2-1.21.3=42, 1.21.4=46, 1.21.5=55, 1.21.6=63, 1.21.7-1.21.8=64, 1.21.9-1.21.10=69, 1.21.11=75
OLDEST, NEWEST = 34, 75
OVERLAYS = [("overlay_1_21_0", 34, 45), ("overlay_1_21_2", 42, 45), ("overlay_pre_arb", 34, 74)]
TRIM_VALUES = {"quartz": .1, "iron": .2, "netherite": .3, "redstone": .4, "copper": .5, "gold": .6,
               "emerald": .7, "diamond": .8, "lapis": .9, "amethyst": 1.0}   # resin is 1.21.4+
ALLOWED_ANGLES = (-45, -22.5, 0, 22.5, 45)

def load(path): return json.loads(path.read_text(encoding="utf-8"))
def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")

def simple(model, prefer="gui"):
    """One plain model path from an item-definition node (old clients have no select/condition)."""
    kind = model["type"].split(":")[-1]
    if kind == "model": return model["model"]
    if kind == "select":
        if model["property"].split(":")[-1] == "display_context":
            for case in model["cases"]:
                when = case["when"] if isinstance(case["when"], list) else [case["when"]]
                if prefer in when: return simple(case["model"])
        if "fallback" in model: return simple(model["fallback"])
        return simple(model["cases"][0]["model"])
    if kind == "range_dispatch": return simple(model.get("fallback") or model["entries"][0]["model"])
    if kind == "condition": return simple(model["on_false"])
    raise ValueError("unsupported item model type " + kind)

def vanilla_model(name):
    """The vanilla models/item/<name>.json from the 1.21.11 client (its parent and textures are unchanged since 1.21)."""
    if CLIENT is None: return None
    with zipfile.ZipFile(CLIENT) as jar:
        try: return json.loads(jar.read(f"assets/minecraft/models/item/{name}.json"))
        except KeyError: return None

def legacy_item_models(out):
    """assets/minecraft/items/<carrier>.json -> assets/minecraft/models/item/<carrier>.json with overrides."""
    written = 0
    for definition in sorted((PACK / "assets/minecraft/items").glob("*.json")):
        carrier = definition.stem
        if carrier.endswith("_spear"): continue                  # spears are 1.21.11+
        root = load(definition)["model"]
        if root["type"].split(":")[-1] != "range_dispatch" or root.get("property", "").split(":")[-1] != "custom_model_data": continue
        fallback = root["fallback"]
        overrides = []
        # vanilla armour trims (old clients drew them with trim_type overrides on the same model file)
        if fallback["type"].split(":")[-1] == "select" and fallback["property"].split(":")[-1] == "trim_material":
            piece = carrier.split("_", 1)[1]
            for material, value in TRIM_VALUES.items():
                darker = "_darker" if material == "netherite" else ""   # netherite-on-netherite is "..._netherite_darker_trim" before 1.21.4
                overrides.append({"predicate": {"trim_type": value}, "model": f"minecraft:item/netherite_{piece}_{material}{darker}_trim"})
        base_name = simple(fallback)
        if base_name == f"minecraft:item/{carrier}":
            model = vanilla_model(carrier)
            if model is None: raise SystemExit(f"need the 1.21.11 client jar for vanilla model item/{carrier}")
            model = {k: v for k, v in model.items() if k != "overrides"}
        else:
            model = {"parent": base_name}                        # block items: minecraft:block/anvil, ores...
        for entry in sorted(root["entries"], key=lambda e: e["threshold"]):
            overrides.append({"predicate": {"custom_model_data": int(entry["threshold"])}, "model": simple(entry["model"])})
        model["overrides"] = overrides
        save(out / f"assets/minecraft/models/item/{carrier}.json", model)
        written += 1
    return written

def legacy_equipment(out):
    """1.21.2-1.21.3 read custom armour from assets/<ns>/models/equipment/ instead of assets/<ns>/equipment/."""
    written = 0
    for src in (PACK / "assets").glob("*/equipment/*.json"):
        namespace = src.parent.parent.name
        shutil.copyfile(src, (out / f"assets/{namespace}/models/equipment").mkdir(parents=True, exist_ok=True) or out / f"assets/{namespace}/models/equipment/{src.name}")
        written += 1
    return written

def rounded_rotations(out):
    """Clients before 1.21.11 reject element rotations other than -45/-22.5/0/22.5/45 degrees on one axis."""
    written = 0
    for src in (PACK / "assets").glob("*/models/**/*.json"):
        model = load(src); changed = False
        for element in model.get("elements", []):
            rotation = element.get("rotation")
            if rotation and "angle" in rotation and rotation["angle"] not in ALLOWED_ANGLES:
                rotation["angle"] = min(ALLOWED_ANGLES, key=lambda a: abs(a - rotation["angle"])); changed = True
        if changed:
            save(out / src.relative_to(PACK), model); written += 1
    return written

def main():
    for name, _, _ in OVERLAYS:
        if (PACK / name).exists(): shutil.rmtree(PACK / name)
    counts = {"overlay_1_21_0": legacy_item_models(PACK / "overlay_1_21_0"),
              "overlay_1_21_2": legacy_equipment(PACK / "overlay_1_21_2"),
              "overlay_pre_arb": rounded_rotations(PACK / "overlay_pre_arb")}
    meta = load(PACK / "pack.mcmeta")
    description = meta["pack"]["description"].replace("Java 1.21.11", "Java 1.21.x")
    entries = []
    for name, low, high in OVERLAYS:
        if counts[name]:
            # "formats" for clients before 1.21.9, min/max_format for 1.21.9+
            entries.append({"formats": [low, high], "min_format": low, "max_format": high, "directory": name})
    meta = {"pack": {"description": description, "pack_format": OLDEST, "supported_formats": [OLDEST, NEWEST],
                     "min_format": OLDEST, "max_format": NEWEST},
            "overlays": {"entries": entries}}
    save(PACK / "pack.mcmeta", meta)
    print("overlays:", counts)

if __name__ == "__main__":
    main()
