# OpenBIMStudio migration

This branch is the migration work area for replacing the former Sweet Home 3D application in this repository with the upstream OpenBIMStudio web application.

## Canonical upstream

- Repository: https://github.com/amdghedy/openBIMstudio
- Branch: `main`
- Imported source commit: `f68369659b027ab22dcda98bc62cf7b62a5bcc63`
- Upstream commit date: 2026-11-25
- License: MIT (retain the upstream `LICENSE` file and notices)
- Upstream project version: 0.74.0 (as declared by its README at the imported revision)

## Migration requirements

1. Replace the old Sweet Home 3D application source; do not mix the two applications.
2. Preserve the complete upstream source tree, including its original history where possible.
3. Keep this repository name: `loay94an/Mokhatiti-3D`.
4. Keep the upstream MIT license and third-party notices intact.
5. Make the browser application the primary deliverable; no Android APK is part of this migration.
6. Add Arabic localization and RTL without changing BIM geometry, units, file formats, or engineering calculations.
7. Verify install, production build, browser smoke tests, model creation, save/open, and mobile touch before calling the migration complete.

## Current status

The upstream source has been inspected and its exact commit recorded. The GitHub integration available to this session can read source repositories and write individual files/commits, but does not expose the GitHub repository-import/fork operation or a binary archive transfer. A direct attempt to create a tree from the upstream Git tree was rejected because Git objects are repository-scoped. Therefore the full source tree has **not** yet been imported in this commit.

Do not describe this branch as a completed import until the full upstream tree and its license have been copied and verified.
