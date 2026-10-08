"""Build a local ride-locator overlay on current geometry, without copying old meshes.

Inputs are installed jars/packs in ascending resource priority. Third-party assets are
read locally; the source repository distributes this script, not their generated ZIP.
"""
import argparse
import copy
import json
from pathlib import Path
import zipfile


def read_json(data):
    return json.loads(data.decode("utf-8-sig"))


def dump(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


PARENT_RENAMES = {
    "nidorina.geo.json": {"upper_torso": "torso2"},
    "exeggcute.geo.json": {"egg": "egg1"},
    "luvdisc.geo.json": {"fin_upper": "fin_upper_right"},
}

# A loaded minimap pack still uses the pre-1.8 family poser names. Keep its
# textures and resolver fields; change only these confirmed renamed references.
POSER_RENAMES = {
    ("cobblemon:maushold", "cobblemon:maushold_three.geo"): "cobblemon:maushold_three",
    ("cobblemon:mausholdfour", "cobblemon:maushold_four.geo"): "cobblemon:maushold_four",
}


def rebase(original, current, name):
    result = copy.deepcopy(current)
    issues, seats = [], []
    old_geometry = original.get("minecraft:geometry", [])
    new_geometry = result.get("minecraft:geometry", [])
    if len(old_geometry) != 1 or len(new_geometry) != 1:
        return None, ["几何结构数量不能自动匹配"], seats
    old_bones = {bone["name"]: bone for bone in old_geometry[0]["bones"]}
    bones = new_geometry[0]["bones"]
    by_name = {bone["name"]: bone for bone in bones}
    for bone in old_bones.values():
        locators = {key: value for key, value in bone.get("locators", {}).items() if key.startswith("seat_")}
        if not locators:
            continue
        # Dedicated empty seat bones are the only original structure retained.
        # Keep their parent only if it still exists in the current geometry.
        if bone["name"].startswith("locator_seat_") and not bone.get("cubes"):
            parent = bone.get("parent")
            parent = PARENT_RENAMES.get(Path(name).name, {}).get(parent, parent)
            if parent and parent not in by_name:
                issues.append("座位父骨骼不存在：" + parent)
                continue
            if bone["name"] in by_name:
                for key, value in locators.items():
                    by_name[bone["name"]].setdefault("locators", {}).setdefault(key, copy.deepcopy(value))
            else:
                seat_bone = {key: copy.deepcopy(value) for key, value in bone.items()
                             if key in ("name", "parent", "pivot", "rotation")}
                seat_bone["locators"] = copy.deepcopy(locators)
                if parent:
                    seat_bone["parent"] = parent
                bones.append(seat_bone)
                by_name[seat_bone["name"]] = seat_bone
        elif bone["name"] in by_name:
            for key, value in locators.items():
                by_name[bone["name"]].setdefault("locators", {}).setdefault(key, copy.deepcopy(value))
        else:
            issues.append("座位骨骼不存在：" + bone["name"])
            continue
        seats.extend(locators)
    if issues or not seats:
        return None, issues or ["没有可迁移座位"], seats
    # The base model's mesh, original locators, pivots and bone hierarchy remain exact.
    for base_bone in current["minecraft:geometry"][0]["bones"]:
        patched = copy.deepcopy(by_name[base_bone["name"]])
        previous = base_bone.get("locators", {})
        if "locators" in patched:
            patched["locators"] = {key: value for key, value in patched["locators"].items()
                                   if not key.startswith("seat_") or key in previous}
            if not patched["locators"] and "locators" not in base_bone:
                del patched["locators"]
        if patched != base_bone:
            raise AssertionError("座位迁移意外更改基础几何：" + base_bone["name"])
    return result, issues, seats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repair", type=Path, required=True)
    parser.add_argument("--seats-source", type=Path, help="旧修复包遗漏座位时，仅从原骑乘包读取座位骨骼")
    parser.add_argument("--base", type=Path, action="append", required=True, help="从低到高优先级，可多次指定")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    inputs = {args.repair.resolve(), *(path.resolve() for path in args.base)}
    if args.seats_source:
        inputs.add(args.seats_source.resolve())
    if args.output.resolve() in inputs:
        parser.error("输出必须为新文件，不能覆盖输入")
    effective = {}
    fallback = {}
    credits = None
    if args.seats_source:
        with zipfile.ZipFile(args.seats_source) as source:
            fallback = {name: source.read(name) for name in source.namelist() if name.endswith(".geo.json")}
            if "CREDITS.txt" in source.namelist():
                credits = source.read("CREDITS.txt")
    for path in args.base:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith("assets/") and name.endswith(".json") and ("/models/" in name or "/resolvers/" in name):
                    effective[name] = (path.name, archive.read(name))
    report = {"基础资源": [path.name for path in args.base], "成功模型": [], "舍弃覆盖": [], "姿态引用修复": [], "保留数据文件": 0}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.repair) as old, zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as out:
        for name in sorted(old.namelist()):
            if name.endswith("/") or name == "pack.mcmeta":
                continue
            if name.startswith("assets/") and "/models/" in name and name.endswith(".geo.json"):
                base = effective.get(name)
                if base is None:
                    report["舍弃覆盖"].append({"模型": name, "原因": ["当前资源没有同路径模型"]})
                    continue
                seat_source = read_json(old.read(name))
                has_seats = any(key.startswith("seat_") for geometry in seat_source.get("minecraft:geometry", [])
                                for bone in geometry["bones"] for key in bone.get("locators", {}))
                if not has_seats and name in fallback:
                    seat_source = read_json(fallback[name])
                patched, issues, seats = rebase(seat_source, read_json(base[1]), name)
                if patched is None:
                    report["舍弃覆盖"].append({"模型": name, "原因": issues, "来源": base[0]})
                    continue
                out.writestr(name, dump(patched))
                report["成功模型"].append({"模型": name, "来源": base[0], "座位": sorted(seats)})
            elif name.startswith("assets/"):
                # No old poser/animation/texture may silently overwrite the new base.
                report["舍弃覆盖"].append({"模型": name, "原因": ["不迁入旧姿态、动画或贴图"]})
            else:
                out.writestr(name, old.read(name))
                if name.startswith("data/"):
                    report["保留数据文件"] += 1
        for name, (source, data) in sorted(effective.items()):
            if "/resolvers/" not in name:
                continue
            resolver = read_json(data)
            changes = []
            for variation in resolver.get("variations", []):
                pair = (variation.get("poser"), variation.get("model"))
                if pair in POSER_RENAMES:
                    changes.append({"旧姿态": pair[0], "新姿态": POSER_RENAMES[pair], "模型": pair[1]})
                    variation["poser"] = POSER_RENAMES[pair]
            if changes:
                out.writestr(name, dump(resolver))
                report["姿态引用修复"].append({"文件": name, "来源": source, "更改": changes})
        out.writestr("pack.mcmeta", dump({"pack": {"pack_format": 34,
            "description": "骑乘座位兼容修复：在当前模型上补座位，保留新版模型与动画。适配方可梦 1.8.1。"}}))
        out.writestr("本地修复说明.txt", "由本地已安装资源生成。仅迁移座位和骑乘数据，不能独立使用。原作模型与骑乘数据仍属于各自作者。不要将生成包当作本项目 MIT 资源再发布。\n".encode("utf-8"))
        if credits:
            out.writestr("CREDITS.txt", credits)
    report["成功数量"] = len(report["成功模型"])
    report["舍弃数量"] = len(report["舍弃覆盖"])
    args.output.with_suffix(".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("成功数量", "舍弃数量", "保留数据文件")}, ensure_ascii=False))
    for problem in report["舍弃覆盖"]:
        print(json.dumps(problem, ensure_ascii=False))


if __name__ == "__main__":
    main()
