#!/usr/bin/env python3
"""Audit Java .properties bundle keys using Java-compatible continuation and separator rules."""
from pathlib import Path
import os
import sys

ROOTS = [
    Path("third_party/SweetHome3D/SweetHome3D/src"),
    Path("third_party/SweetHome3D/SweetHome3DJS/src"),
]

def logical_lines(path: Path):
    physical = path.read_text(encoding="ISO-8859-1").splitlines()
    pending = ""
    for line in physical:
        if pending:
            line = line.lstrip()
        # A continuation is triggered only by an odd number of trailing backslashes.
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
            continue
        if char == "\\":
            escaped = True
            continue
        if char in "=:" or char.isspace():
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
        failures.append(f"{arabic}: {len(absent)} missing keys: {', '.join(absent[:30])}")

summary = (
    f"Arabic resource audit: {checked} translated bundles checked, "
    f"{missing_keys} missing keys, {len(failures)} warnings."
)
print(summary)
summary_path = Path(os.environ.get("GITHUB_STEP_SUMMARY", "/tmp/mokhatiti-arabic-summary.md"))
summary_path.write_text("# Arabic translation audit\n\n" + summary + "\n", encoding="utf-8")
if checked < 6:
    print(
        f"Expected at least 6 source-matched Arabic resource bundles, found {checked}.",
        file=sys.stderr,
    )
    sys.exit(2)
for failure in failures[:100]:
    print("::warning::" + failure)
if failures:
    print(
        "Arabic resources are incomplete for this engine version; continuing debug build with explicit warning.",
        file=sys.stderr,
    )
