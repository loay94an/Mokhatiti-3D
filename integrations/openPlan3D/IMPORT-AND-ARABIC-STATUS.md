# OpenPlan3D import and Arabic localization

## Source archive received and inspected

- Uploaded archive: `openPlan3D-main.zip`
- SHA-256: `224d2ec1137c74697dea6d68bcabc62a352c52f6339a6eef6c703be96f4970f1`
- ZIP integrity: passed (no corrupt member reported)
- Contents: 1,161 files; 16,352,525 uncompressed bytes
- Main source: 245 files under `src/`
- Automated test files: 361 matching test/spec naming patterns across the archive
- License file: MIT, copyright notice `theLodgeStudio (2026)`

## Provenance verification

The package metadata contains the legacy repository URL `https://github.com/theLodgeBots/open3dFloorplan.git`. A GitHub repository lookup for that URL resolves to the canonical upstream `laanlabs/openPlan3D`. The uploaded archive's `package.json`, `src/lib/i18n/index.ts`, and `src/app.html` were each compared with the files at upstream commit `771c9e1ad30bd1c3cc7584d6eca74deb2e14943d`; all three match exactly. This verifies the archive's documented baseline for those key files and resolves the apparent URL discrepancy. The ZIP does not contain `.git` history, so its full-tree provenance cannot be independently proven by archive metadata alone. Preserve the supplied LICENSE and source notices.

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

## Current import progress

The source archive is being transferred in sequential batches to branch `feat/openplan3d-import-arabic`, under `integrations/openPlan3D/`. The first seven batches have committed **60 upstream source/project files** so far, including:

- MIT license and runtime/package/build configuration
- Svelte application shell and TypeScript declarations
- Original locale engine and label maps
- Selected geometry, export, canvas, camera, drawing-scheduler, image utilities, deployment services, UI alert components and an initial set of regression tests/fixtures

The existing Arabic starter dictionary and this status report are additional integration files, not part of the 33 upstream source files. The upload is still partial: the archive contains 1,161 files. No claim is made that all assets, models, tests, documentation or application modules have been transferred.

## Remaining work

1. Continue transferring the complete source tree in batches, preserving original paths, binary assets, notices, tests and configuration.
2. Verify every archive entry against the committed tree; account for all 1,161 files.
3. Integrate Arabic into the locale type, dictionaries, selector and persistence; set `lang="ar"` and `dir="rtl"` correctly.
4. Translate all 1,567 English locale keys and audit additional user-facing strings outside the dictionaries, including canvas-rendered labels and errors.
5. Preserve interpolation placeholders and technical file formats/units.
6. Audit RTL layouts, icons, drawers, toolbars, dialogs, property panels and mobile touch flows without changing drawing geometry.
7. Run type-check, unit tests, production build and browser tests; then perform touch checks on Android.
8. Verify the existing Sweet Home 3D project remains unchanged.

## Import transport limitation

The supplied ZIP is available for local inspection, but the current GitHub connection exposes individual Git object/file operations and no archive upload or bulk directory import operation. The execution container also cannot resolve `github.com` for a direct Git clone/push. Therefore the transfer is being performed through individual file commits in batches. Build and test validation have not yet been run against a complete imported tree.
