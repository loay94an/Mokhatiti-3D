#!/usr/bin/env python3
"""Audit the generated Arabic JS localization for remaining English UI phrases."""
from pathlib import Path
import json
import re
import sys

path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "third_party/SweetHome3D/SweetHome3DJS/lib/resources/localization_ar.json"
)
data = json.loads(path.read_text(encoding="utf-8"))
arabic = re.compile(r"[\u0600-\u06ff]")
latin = re.compile(r"[A-Za-z]{2,}")

# These values are deliberately Latin: keyboard mnemonics, URLs, help paths,
# coordinate/color notation, and dimension strings rather than interface prose.
ALLOWED_LATIN_ONLY = {
    "HomePane.findMoreExamples.url",
    "HelpController.helpIndex",
    "CatalogItemToolTip.tooltipTextureDimensions",
    "CatalogItemToolTip.tooltipPieceOfFurnitureDimensions",
    "ColorButton.rgbLabel.text",
    "ColorChooser.hsbHueText",
    "ColorChooser.hsbSaturationText",
    "ColorChooser.hsbBrightnessText",
    "ColorChooser.hsbNameText",
    "ColorChooser.rgbNameText",
}
ALLOWED_SUFFIXES = (".mnemonic", ".MnemonicKey")
FORBIDDEN_PHRASES = (
    "provided by",
    "to find web sites",
    "Visit http",
    "Sweet Home 3D plugin",
    "Page %1$d/%2$d",
    "frames/second",
)

required = (
    "PrintPreviewPanel.pageLabel.text",
    "FileContentManager.pluginDescription",
    "ImportedFurnitureWizardStepsPanel.findModelsMessage.text",
    "HomePane.findMoreExamplesMessage.text",
    "VideoPanel.videoFormatComboBox.format",
    "HomePane.about.message",
)
missing = [key for key in required if key not in data]
if missing:
    raise SystemExit("Missing required Arabic UI keys: " + ", ".join(missing))

empty = [key for key, value in data.items() if not isinstance(value, str) or not value.strip()]
latin_only = []
for key, value in data.items():
    if not isinstance(value, str) or not latin.search(value) or arabic.search(value):
        continue
    if key in ALLOWED_LATIN_ONLY or key.endswith(ALLOWED_SUFFIXES):
        continue
    latin_only.append((key, value))

forbidden = [
    (key, phrase)
    for key, value in data.items() if isinstance(value, str)
    for phrase in FORBIDDEN_PHRASES if phrase.lower() in value.lower()
]
if empty or latin_only or forbidden:
    for key in empty:
        print(f"::error::Empty Arabic localization value: {key}")
    for key, value in latin_only:
        print(f"::error::Untranslated visible string: {key} = {value}")
    for key, phrase in forbidden:
        print(f"::error::English phrase remains in {key}: {phrase}")
    raise SystemExit("Generated Arabic localization still contains untranslated UI text.")

print(
    f"Generated Arabic localization passed: {len(data)} keys; "
    f"{len(ALLOWED_LATIN_ONLY)} technical Latin-only labels allowlisted."
)
