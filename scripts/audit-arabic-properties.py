#!/usr/bin/env python3
"""Audit user-visible Arabic Java .properties keys for Sweet Home 3D JS 7.5.2."""
from pathlib import Path
import os
import re
import sys

ROOTS = [
    Path("third_party/SweetHome3D/SweetHome3D/src"),
    Path("third_party/SweetHome3D/SweetHome3DJS/src"),
]

# The old translation archive contains Swing-only bundles not used by the JS editor.
SKIP_BUNDLES = {"basic", "metal"}

def visible_key(bundle: str, key: str) -> bool:
    if bundle in {"DefaultFurnitureCatalog", "DefaultTexturesCatalog"}:
        # Names, descriptions and categories are shown in the catalog UI.
        return key in {"name", "description"} or bool(re.fullmatch(r"(name|description|category)#\d+", key))
    if bundle == "DefaultUserPreferences":
        # Preference flags, paths and numeric defaults are data, not translated UI.
        return key.startswith("homeExampleName#") or key.startswith("autoCompletionStrings#")
    if bundle == "LengthUnit":
        # Formatting templates remain locale-neutral; unit labels are visible.
        return key in {
            "meterUnit", "centimeterUnit", "millimeterUnit", "inchUnit",
            "footUnit", "squareMeterUnit", "squareFootUnit",
        }
    return True

def logical_lines(path: Path):
    physical = path.read_text(encoding="ISO-8859-1").splitlines()
    pending = ""
    for line in physical:
        if pending:
            line = line.lstrip()
        slash_count = len(line) - len(line.rstrip("\\"))
        if slash_count % 2:
            pending += line[:-1]
            continue
        yield pending + line
        pending = ""
    if pending:
        yield pending

def property_key(line: str):
    line = line.lstrip()
    if not line or line[0] in "#!":
        return None
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char in "=: \t\f":
            return line[:index]
    return line

def property_keys(path: Path) -> set[str]:
    return {
        key for line in logical_lines(path)
        if (key := property_key(line)) is not None
    }

arabic_files = []
for root in ROOTS:
    if root.exists():
        arabic_files.extend(root.rglob("*_ar.properties"))

failures = []
checked = 0
required_total = 0
missing_total = 0
ignored_swing = 0
ignored_metadata = 0

for arabic in sorted(set(arabic_files)):
    bundle = arabic.name[:-len("_ar.properties")]
    if bundle in SKIP_BUNDLES:
        ignored_swing += 1
        continue
    english = arabic.with_name(bundle + ".properties")
    if not english.is_file():
        failures.append(f"Arabic bundle has no matching source: {arabic}")
        continue
    checked += 1
    source_keys = property_keys(english)
    arabic_keys = property_keys(arabic)
    required = {key for key in source_keys if visible_key(bundle, key)}
    ignored_metadata += len(source_keys - required)
    absent = sorted(required - arabic_keys)
    required_total += len(required)
    missing_total += len(absent)
    if absent:
        failures.append(f"{arabic}: {len(absent)} missing visible keys: {', '.join(absent[:50])}")

summary = (
    f"Arabic UI resource audit: {checked} bundles checked, "
    f"{required_total} visible keys required, {missing_total} missing; "
    f"{ignored_metadata} non-UI metadata keys and {ignored_swing} Swing-only bundles excluded."
)
print(summary)
summary_path = Path(os.environ.get("GITHUB_STEP_SUMMARY", "/tmp/mokhatiti-arabic-summary.md"))
summary_path.write_text("# Arabic UI translation audit\n\n" + summary + "\n", encoding="utf-8")

if checked < 4:
    failures.append(f"Expected at least 4 source-matched Arabic UI bundles, found {checked}.")
for failure in failures:
    print("::error::" + failure)
if failures:
    print("Arabic UI resources are incomplete; stop before JSweet generation.", file=sys.stderr)
    sys.exit(1)
