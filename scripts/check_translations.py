#!/usr/bin/env python3
"""Check first-party English/Spanish resources without growing existing translation debt."""

import argparse
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


@dataclass(frozen=True, order=True)
class Issue:
    resource: str
    key: str
    locale: str
    defect: str
    variant: str = ""


# Match complete printf arguments, not ordinary percent signs in prose. Argument indices
# allow translators to reorder/repeat arguments, but their types must remain compatible.
FORMAT = re.compile(
    r"%(?:(?P<index>[1-9]\d*)\$)?(?P<flags>[-+#0,'(<]*)"
    r"(?P<width>\d+|\*(?:[1-9]\d*\$)?)?"
    r"(?:\.(?P<precision>\d+|\*(?:[1-9]\d*\$)?))?"
    r"(?P<length>hh|ll|[hljztLq])?(?P<type>[@diuoxXfFeEgGaAcCsSpbBhH%ntT])"
)
ANDROID_FORMAT = re.compile(FORMAT.pattern.replace("[hljztLq]", "[hljzLq]"))


def arguments(value, android=False):
    slots = {}
    next_index = 1
    previous = None

    def add(index, kind):
        nonlocal next_index
        slot = int(index) if index else next_index
        if not index:
            next_index += 1
        if slot in slots and slots[slot] != kind:
            raise ValueError("one argument has incompatible types")
        slots[slot] = kind
        return slot

    pattern = ANDROID_FORMAT if android else FORMAT
    matches = list(pattern.finditer(value))
    for candidate in re.finditer(r"%(?:\d+\$|[@A-Za-z*])", value):
        if candidate.start() and value[candidate.start() - 1] == "%":
            continue
        if not any(m.start() == candidate.start() for m in matches):
            raise ValueError("invalid format argument")
    for match in matches:
        kind = match["type"]
        if kind == "%" or (android and kind == "n"):
            continue
        for field in (match["width"], match["precision"]):
            if field and field.startswith("*"):
                add(field[1:].removesuffix("$") or None, "int")
        if android:
            if kind in "tT":
                suffix = value[match.end():match.end() + 1]
                if not suffix:
                    raise ValueError("missing date format suffix")
                kind = "date"
            elif kind in "doxX":
                kind = "int"
            elif kind in "eEfgGaA":
                kind = "float"
            else:
                kind = kind.lower()
        else:
            kind = (match["length"] or "") + ("d" if kind in "di" else kind.lower())
        index = match["index"]
        if android and "<" in match["flags"]:
            if previous is None:
                raise ValueError("relative format has no previous argument")
            index = str(previous)
        previous = add(index, kind)
    return slots


def compatible(source, target, android=False):
    try:
        return arguments(source, android) == arguments(target, android)
    except ValueError:
        return False


def apple_units(node, path=()):
    """A unit is identified by its device/plural selector, independent of locale."""
    if "stringUnit" in node:
        unit = dict(node["stringUnit"])
        for name, substitution in node.get("substitutions", {}).items():
            unit["value"] = unit["value"].replace(
                f"%#@{name}@", f"%{substitution['argNum']}${substitution['formatSpecifier']}"
            )
        yield path, unit
    for dimension, variants in node.get("variations", {}).items():
        if "other" not in variants:
            raise ValueError(f"{dimension} variation has no 'other' fallback")
        for variant, child in variants.items():
            yield from apple_units(child, path + ((dimension, variant),))
    for name, substitution in node.get("substitutions", {}).items():
        yield from apple_units(substitution, path + (("substitution", name),))


def apple_reference(units, path):
    if path in units:
        return units[path]
    fallback = tuple((dimension, variant if dimension == "substitution" else "other") for dimension, variant in path)
    if fallback in units:
        return units[fallback]
    if () in units:
        return units[()]
    # A scalar translation can cover every source device variant. Compare with the
    # source's default variant; every explicit localized variant is checked separately.
    return next((unit for p, unit in units.items() if all(d == "substitution" or v == "other" for d, v in p)), None)


def apple_issues(root):
    catalogs = sorted((root / "secant" / "Resources").glob("*.xcstrings"))
    if not catalogs:
        raise ValueError("no string catalogs found")
    result = set()
    for catalog in catalogs:
        data = json.loads(catalog.read_text())
        if data.get("sourceLanguage") != "en":
            raise ValueError(f"unexpected source language: {catalog}")
        for key, entry in data["strings"].items():
            if entry.get("shouldTranslate") is False:
                continue
            resource = catalog.relative_to(root).as_posix()
            localized = entry.get("localizations", {})
            source = dict(apple_units(localized.get("en", {})))
            for locale in ("en", "es"):
                units = dict(apple_units(localized.get(locale, {})))
                if not units:
                    result.add(Issue(resource, key, locale, "missing"))
                    continue
                for path, unit in units.items():
                    variant = "/".join(f"{d}:{v}" for d, v in path)
                    if not unit.get("value", "").strip():
                        result.add(Issue(resource, key, locale, "empty", variant))
                    if unit.get("state") != "translated":
                        result.add(Issue(resource, key, locale, "unreviewed", variant))
                    reference = apple_reference(source, path)
                    if locale != "en" and reference and not compatible(reference["value"], unit.get("value", "")):
                        result.add(Issue(resource, key, locale, "format", variant))
                # English and Spanish both have 'one' and 'other' plural categories.
                # A plain string is not a replacement for the source's plural selection.
                for path in source:
                    if any(d == "plural" for d, _ in path) and path not in units:
                        result.add(Issue(resource, key, locale, "missing-plural", "/".join(f"{d}:{v}" for d, v in path)))
                    target = apple_reference(units, path)
                    if locale != "en" and target and not compatible(source[path]["value"], target.get("value", "")):
                        result.add(Issue(resource, key, locale, "format", "/".join(f"{d}:{v}" for d, v in path)))
    return result


def android_roots(module, source_set):
    gradle = module / "build.gradle.kts"
    if source_set == "main" and gradle.exists():
        text = re.sub(r"//[^\n]*", "", gradle.read_text())
        override = re.search(r'res\.setSrcDirs\(\s*(?:listOf|setOf)\((.*?)\)\s*\)', text, re.S)
        if override:
            return [module / p for p in re.findall(r'"([^"]+)"', override[1])]
        if "res.setSrcDirs" in text:
            raise ValueError(f"unsupported resource source-set declaration in {gradle}")
    return [module / "src" / source_set / "res"]


def android_resources(roots, locale):
    entries = {}
    directory = "values" if locale == "en" else f"values-{locale}"
    for root in roots:
        for file in sorted((root / directory).glob("*.xml")):
            for entry in ET.parse(file).getroot():
                if entry.tag in ("string", "plurals", "string-array"):
                    name = entry.attrib["name"]
                    if name in entries:
                        raise ValueError(f"duplicate resource {name} in {file}")
                    entries[name] = (file, entry)
    return entries


def android_issues(root):
    modules = sorted(p.parent for p in root.glob("*/build.gradle.kts") if (p.parent / "src/main").exists())
    modules = [p for p in modules if p.name not in ("test-lib", "ui-integration-test", "ui-benchmark-test", "ui-screenshot-test")]
    result = set()
    found = False
    for module in modules:
        main = {l: android_resources(android_roots(module, "main"), l) for l in ("en", "es")}
        found |= bool(main["en"])
        overlays = [p.name for p in (module / "src").iterdir() if p.is_dir() and p.name not in ("main", "test", "androidTest") and (p / "res").exists()]
        for source_set in ["main", *sorted(overlays)]:
            resources = {l: dict(main[l]) for l in ("en", "es")}
            if source_set != "main":
                for locale in resources:
                    resources[locale].update(android_resources(android_roots(module, source_set), locale))
            for key, (file, source) in resources["en"].items():
                if source.attrib.get("translatable") == "false":
                    continue
                resource = file.relative_to(root).as_posix()
                target_pair = resources["es"].get(key)
                if target_pair is None:
                    result.add(Issue(resource, key, "es", "missing"))
                for locale, pair in (("en", (file, source)), ("es", target_pair)):
                    if pair is None:
                        continue
                    target_file, target = pair
                    target_resource = target_file.relative_to(root).as_posix()
                    if target.tag != source.tag:
                        result.add(Issue(target_resource, key, locale, "type"))
                        continue
                    source_units = android_units(source)
                    units = android_units(target)
                    if not units:
                        result.add(Issue(target_resource, key, locale, "empty"))
                    for variant, text in units.items():
                        if not text.strip():
                            result.add(Issue(target_resource, key, locale, "empty", variant))
                        reference = source_units.get(variant, source_units.get("other"))
                        if source.attrib.get("formatted") != "false" and (reference is None or not compatible(reference, text, android=True)):
                            result.add(Issue(target_resource, key, locale, "format", variant))
                    if target.tag == "plurals" and "other" not in units:
                        result.add(Issue(target_resource, key, locale, "missing-other"))
                    if target.tag == "string-array" and units.keys() != source_units.keys():
                        result.add(Issue(target_resource, key, locale, "array-size"))
            for key in resources["es"].keys() - resources["en"].keys():
                file, _ = resources["es"][key]
                result.add(Issue(file.relative_to(root).as_posix(), key, "en", "missing-source"))
    if not found:
        raise ValueError("no Android string resources found")
    return result


def android_units(entry):
    if entry.tag == "string":
        return {"": "".join(entry.itertext())}
    return {item.attrib.get("quantity", str(i)): "".join(item.itertext()) for i, item in enumerate(entry)}


def compare_baseline(issues, baseline):
    return issues - baseline, baseline - issues


def write_baseline(path, issues):
    entries = [json.dumps(asdict(i), ensure_ascii=False) for i in sorted(issues)]
    path.write_text('{\n  "version": 1,\n  "issues": [\n' + ",\n".join(f"    {entry}" for entry in entries) + '\n  ]\n}\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--prune-baseline", action="store_true", help="remove resolved debt; never adds entries")
    args = parser.parse_args()
    root = args.root.resolve()
    baseline_path = Path(__file__).with_name("translation-baseline.json")
    try:
        issues = apple_issues(root) if (root / "secant").exists() else android_issues(root)
        data = json.loads(baseline_path.read_text())
        if data.get("version") != 1:
            raise ValueError("unsupported baseline version")
        baseline = {Issue(**entry) for entry in data["issues"]}
        new, resolved = compare_baseline(issues, baseline)
        if args.prune_baseline:
            write_baseline(baseline_path, baseline & issues)
            resolved = set()
        for label, entries in (("NEW", new), ("RESOLVED (prune baseline)", resolved)):
            for issue in sorted(entries):
                print(f"{label}: {issue.resource}: {issue.key} [{issue.locale}] {issue.defect} {issue.variant}")
        print(f"Translation checks: {len(issues)} existing defects; {len(new)} new; {len(resolved)} resolved.")
        return int(bool(new or resolved))
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        print(f"Translation check failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
