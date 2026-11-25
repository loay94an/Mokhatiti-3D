#!/usr/bin/env python3
"""Prepare a production editor entry point from the upstream Sweet Home 3D JS editor shell.

The upstream distribution exposes its complete editor UI in test/testHome.html.
This script keeps that original UI and engine wiring, removes its test runner/demo
project, and starts a new empty Home through the upstream application API.
"""
from pathlib import Path
import argparse
import re

parser = argparse.ArgumentParser()
parser.add_argument("--source", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args()

source = Path(args.source).read_text(encoding="utf-8")
source = source.replace("<html>", '<html lang="ar" dir="rtl">', 1)
source = source.replace("<title>SweetHome3DJS Test</title>", "<title>مخططي 3D</title>", 1)
source = source.replace('<base href="..">', '<base href=".">', 1)

# Remove the upstream page's test-only assertion and test-case declarations.
source, removed = re.subn(
    r'<script type="text/javascript">\s*function assertEquals[\s\S]*?</script>',
    "",
    source,
    count=1,
)
if removed != 1:
    raise SystemExit("Unable to isolate and remove upstream test-only JavaScript block")

# Replace the test server / sample-home bootstrap with a clean production project.
marker = '<script type="text/javascript">\n// If set to not null value'
start = source.rfind(marker)
if start < 0:
    raise SystemExit("Unable to locate upstream editor bootstrap")
end = source.find("</script>", start)
if end < 0:
    raise SystemExit("Unable to locate end of upstream editor bootstrap")
end += len("</script>")

bootstrap = r'''<script type="text/javascript">
var application = new SweetHome3DJSApplication({
  furnitureCatalogURLs: [ZIPTools.getScriptFolder() + "resources/DefaultFurnitureCatalog.json"],
  furnitureResourcesURLBase: ZIPTools.getScriptFolder() + "../",
  texturesCatalogURLs: [ZIPTools.getScriptFolder() + "resources/DefaultTexturesCatalog.json"],
  texturesResourcesURLBase: ZIPTools.getScriptFolder() + "../",
  includeAllContent: true,
  compressionLevel: 0,
  writeHomeWithWorker: true,
  autoRecovery: true,
  defaultHomeName: "مشروع جديد"
});
application.addHome(application.createHome());
console.log("MOKHATITI_EDITOR_READY " + application.getVersion());
</script>'''
source = source[:start] + bootstrap + source[end:]

if 'id="home-plan"' not in source or 'id="home-3D-view"' not in source:
    raise SystemExit("Upstream editor canvas elements are missing")
if 'HomeTest.sh3d' in source or 'testHomeModifications' in source:
    raise SystemExit("Test project bootstrap leaked into production editor")
if 'application.addHome(application.createHome())' not in source:
    raise SystemExit("Empty production project bootstrap is missing")

# Load the app's Arabic layout layer after the upstream stylesheet.
source = source.replace(
    "</head>",
    '<link rel="stylesheet" type="text/css" href="arabic-overrides.css">\n</head>',
    1,
)
Path(args.output).parent.mkdir(parents=True, exist_ok=True)
Path(args.output).write_text(source, encoding="utf-8")
print(f"Prepared production editor: {args.output}")
