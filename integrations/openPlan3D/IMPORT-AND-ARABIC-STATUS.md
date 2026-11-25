# OpenPlan3D import and Arabic localization

## Source archive received and inspected

- Uploaded archive: `openPlan3D-main.zip`
- SHA-256: `224d2ec1137c74697dea6d68bcabc62a352c52f6339a6eef6c703be96f4970f1`
- ZIP integrity: passed (no corrupt member reported)
- Contents: 1,161 files; 16,352,525 uncompressed bytes
- Main source: 245 files under `src/`
- Automated test files: 361 matching test/spec naming patterns across the archive
- License file: MIT, copyright notice `theLodgeStudio (2026)`

## Provenance discrepancy — must remain visible

The archive README identifies the application as **openplan3d**, but its `package.json` identifies the package as `open3dfloorplan` and declares this repository URL:

`https://github.com/theLodgeBots/open3dFloorplan.git`

This differs from the previously documented upstream URL `https://github.com/laanlabs/openPlan3D`. The archive itself does not include Git history, so its exact commit and relationship to either GitHub repository cannot be verified from the ZIP alone. Do not silently rewrite this metadata or claim a verified upstream commit. Preserve the supplied LICENSE and source notices.

## Localization architecture found in the archive

- Existing locale module: `src/lib/i18n/index.ts`
- Existing dictionaries: `src/lib/i18n/locales/en.ts` and `pt.ts`
- Both dictionaries contain 1,567 keys, with matching key sets.
- Locale type currently permits only `en` and `pt`.
- Locale persistence key is `o3d_locale`; initialization defaults to English.
- Language selection is in `src/lib/components/toolbar/SettingsDialog.svelte`.
- The document shell currently starts with `<html lang="en">` in `src/app.html`.
- The existing locale setter updates `lang` but does not set document `dir`.
- No Arabic dictionary is present in the uploaded source archive.

## RTL and mobile audit findings

Arabic support is not just a dictionary addition. The application shell, layout direction, mirrored navigation affordances, side panels, dialogs, menus, property controls, and responsive layouts need an RTL audit. Canvas geometry and world coordinates must remain unchanged; only UI direction and user-facing labels should be localized. Canvas-rendered text and directional controls need a separate review.

## Target layout and repository safety

Keep the application isolated under:

`integrations/openPlan3D/`

Do not overwrite the repository root, Android wrapper, or existing Sweet Home 3D engine.

## Current implementation state

The branch `feat/openplan3d-import-arabic` and draft PR #6 contain an initial Arabic translation resource and integration notes. The partial resource is not connected to the app and is not a complete translation. The uploaded archive has been inspected locally, but the full source tree has **not** been committed to the target repository.

## Remaining work

1. Resolve and document the source repository/provenance discrepancy against the supplied archive.
2. Copy the complete source tree under `integrations/openPlan3D/`, preserving LICENSE, notices, assets, tests and project configuration.
3. Integrate Arabic into the locale type, dictionaries, selector and persistence; set `lang="ar"` and `dir="rtl"` correctly.
4. Translate all 1,567 English locale keys and audit additional user-facing strings outside the dictionaries, including canvas-rendered labels and errors.
5. Preserve interpolation placeholders and technical file formats/units.
6. Audit RTL layouts, icons, drawers, toolbars, dialogs, property panels and mobile touch flows without changing drawing geometry.
7. Run type-check, unit tests, production build and browser tests; then perform touch checks on Android.
8. Verify the existing Sweet Home 3D project remains unchanged.

## Import transport limitation

The supplied ZIP is available for local inspection, but the current GitHub connection exposes individual Git object/file operations and no archive upload or bulk directory import operation. The execution container also cannot resolve `github.com` for a direct Git clone/push. Therefore this audit does not claim that the archive has been imported or that the application has been built or tested.
