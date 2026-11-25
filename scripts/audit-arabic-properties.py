#!/usr/bin/env python3
"""Validate every installed Arabic Sweet Home 3D resource bundle."""
from pathlib import Path
import os
import sys

ROOTS = [
    Path("third_party/SweetHome3D/SweetHome3D/src"),
    Path("third_party/SweetHome3D/SweetHome3DJS/src"),
]

def property_keys(path: Path) -> set[str]:
    physical = path.read_text(encoding="ISO-8859-1").splitlines()
    logical = []
    pending = ""
    for line in physical:
        line = pending + line.lstrip() if pending else line
        if line.endswith("\\"):
            pending = line[:-1]
            continue
        logical.append(line)
        pending = ""
    if pending:
        logical.append(pending)
    keys = set()
    for line in logical:
        stripped = line.lstrip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        if "=" in stripped or ":" in stripped:
            key = stripped.split("=", 1)[0].split(":", 1)[0].strip()
            if key:
                keys.add(key)
        else:
            key = stripped.split(None, 1)[0]
            if key:
                keys.add(key)
    return keys

arabic_files = []
for root in ROOTS:
    if root.exists():
        arabic_files.extend(root.rglob("*_ar.properties"))

failures = []
checked = 0
missing_keys = 0
for arabic in sorted(set(arabic_files)):
    if arabic.name.startswith("package_"):
        continue
    english = arabic.with_name(arabic.name[:-len("_ar.properties")] + ".properties")
    if not english.is_file():
        failures.append(f"Arabic bundle has no matching source: {arabic}")
        continue
    checked += 1
    absent = sorted(property_keys(english) - property_keys(arabic))
    missing_keys += len(absent)
    if absent:
        failures.append(f"{arabic}: {len(absent)} missing keys: {', '.join(absent[:12])}")

summary = (
    f"Arabic resource audit: {checked} translated bundles checked, "
    f"{missing_keys} missing keys, {len(failures)} errors."
)
print(summary)
summary_path = Path(os.environ.get("GITHUB_STEP_SUMMARY", "/tmp/mokhatiti-arabic-summary.md"))
summary_path.write_text("# Arabic translation audit\n\n" + summary + "\n", encoding="utf-8")
if checked < 9:
    print(f"Expected at least 9 official Arabic resource bundles, found {checked}.", file=sys.stderr)
    sys.exit(2)
if failures:
    for failure in failures[:100]:
        print("::error::" + failure)
    sys.exit(1)
