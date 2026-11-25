#!/usr/bin/env python3
"""Audit Arabic Sweet Home 3D UI bundles against the pinned English source."""
from pathlib import Path
import os
import re
import sys

ROOTS = [
    Path("third_party/SweetHome3D/SweetHome3D/src/com/eteks/sweethome3d/viewcontroller/resources"),
    Path("third_party/SweetHome3D/SweetHome3D/src/com/eteks/sweethome3d/swing/resources"),
    Path("third_party/SweetHome3D/SweetHome3D/src/com/eteks/sweethome3d/model/resources"),
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
        match = re.match(r"([^:=\s]+)\s*[:=]", stripped)
        if match:
            keys.add(match.group(1))
        else:
            match = re.match(r"([^\s]+)\s+", stripped)
            if match:
                keys.add(match.group(1))
    return keys

total = translated = missing_files = missing_keys = 0
failures = []
for root in ROOTS:
    if not root.exists():
        continue
    for english in root.rglob("*.properties"):
        if english.name == "package.properties" or english.name.startswith("package_"):
            continue
        if re.search(r"_[a-z]{2}(?:_[A-Z]{2})?\\.properties$", english.name):
            continue
        total += 1
        arabic = english.with_name(english.stem + "_ar.properties")
        if not arabic.is_file():
            missing_files += 1
            failures.append(f"Missing Arabic bundle: {english}")
            continue
        translated += 1
        absent = sorted(property_keys(english) - property_keys(arabic))
        missing_keys += len(absent)
        if absent:
            failures.append(f"{arabic}: {len(absent)} missing keys: {', '.join(absent[:12])}")

coverage = (translated / total * 100) if total else 0
summary = (
    f"Arabic UI resource coverage: {translated}/{total} bundles "
    f"({coverage:.1f}%), {missing_keys} missing keys, "
    f"{missing_files} missing bundles."
)
print(summary)
summary_path = Path(os.environ.get("GITHUB_STEP_SUMMARY", "/tmp/mokhatiti-arabic-summary.md"))
summary_path.write_text("# Arabic translation audit\n\n" + summary + "\n", encoding="utf-8")
if total == 0:
    print("No English UI resource bundles were found; audit cannot pass.", file=sys.stderr)
    sys.exit(2)
if failures:
    for failure in failures[:100]:
        print("::error::" + failure)
    sys.exit(1)
