#!/usr/bin/env python3
"""Fill small, user-visible Arabic resource gaps left by the older 7.1.1 archive.

The catalog's numeric model metadata and Swing-only resource bundles are not UI
strings and must not be blindly translated. This script adds only the visible
catalog titles/descriptions, sample-home names, and the missing foot unit label.
"""
from pathlib import Path
import re

ROOT = Path("third_party/SweetHome3D/SweetHome3D/src")

PATCHES = {
    ROOT / "com/eteks/sweethome3d/io/DefaultFurnitureCatalog_ar.properties": {
        "name": "كتالوج النماذج الافتراضي",
        "description": "نماذج من تصميم إيمانويل بويبارت / eTeks",
    },
    ROOT / "com/eteks/sweethome3d/io/DefaultTexturesCatalog_ar.properties": {
        "name": "كتالوج الخامات الافتراضي",
        "description": "خامات من تصميم إيمانويل بويبارت / eTeks",
    },
    ROOT / "com/eteks/sweethome3d/io/DefaultUserPreferences_ar.properties": {
        "homeExampleName#1": "استوديو",
        "homeExampleName#2": "شقة بغرفة نوم واحدة",
        "homeExampleName#3": "شقة بغرفتي نوم",
        "homeExampleName#4": "شقة بثلاث غرف نوم",
        "homeExampleName#5": "شقة بنافذة بارزة",
        "homeExampleName#6": "منزل بأربع غرف نوم",
        "homeExampleName#7": "منزل بغرفتي نوم مع قبو",
        "homeExampleName#8": "منزل بثلاث غرف نوم مع علّية",
        "homeExampleName#9": "منزل بثلاث غرف نوم مع طابق ميزانين",
    },
    ROOT / "com/eteks/sweethome3d/model/LengthUnit_ar.properties": {
        "footUnit": "قدم",
    },
}

def logical_lines(text: str):
    pending = ""
    for line in text.splitlines():
        if pending:
            line = line.lstrip()
        slashes = len(line) - len(line.rstrip("\\"))
        if slashes % 2:
            pending += line[:-1]
            continue
        yield pending + line
        pending = ""
    if pending:
        yield pending

def key_of(line: str):
    line = line.lstrip()
    if not line or line[0] in "#!":
        return None
    escaped = False
    for i, char in enumerate(line):
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char in "=: \t\f":
            return line[:i]
    return line

def java_escape(value: str) -> str:
    out = []
    for char in value:
        code = ord(char)
        if char == "\\":
            out.append("\\\\")
        elif char in "=:#!" :
            out.append("\\" + char)
        elif char == "\n":
            out.append("\\n")
        elif char == "\r":
            out.append("\\r")
        elif code < 0x20 or code > 0x7E:
            if code <= 0xFFFF:
                out.append(f"\\u{code:04X}")
            else:
                code -= 0x10000
                out.append(f"\\u{0xD800 + (code >> 10):04X}\\u{0xDC00 + (code & 0x3FF):04X}")
        else:
            out.append(char)
    return "".join(out)

for path, additions in PATCHES.items():
    if not path.is_file():
        raise SystemExit(f"Expected Arabic resource file is missing: {path}")
    original = path.read_text(encoding="ISO-8859-1")
    present = {key for line in logical_lines(original) if (key := key_of(line)) is not None}
    missing = [(key, value) for key, value in additions.items() if key not in present]
    if missing:
        with path.open("a", encoding="ISO-8859-1", newline="\n") as target:
            target.write("\n# Arabic UI labels added for Sweet Home 3D JS 7.5.2\n")
            for key, value in missing:
                target.write(f"{key}={java_escape(value)}\n")
    print(f"{path.name}: added {len(missing)} visible Arabic labels")
