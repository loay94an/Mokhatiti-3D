# OpenPlan3D import and Arabic localization

## Upstream source

- Repository: https://github.com/laanlabs/openPlan3D
- Upstream baseline: `771c9e1ad30bd1c3cc7584d6eca74deb2e14943d`
- License: MIT (retain upstream LICENSE and notices when importing)
- Current upstream stack: SvelteKit, Svelte 5, TypeScript, Three.js.

## Target layout

The OpenPlan3D source must be kept isolated from the existing Sweet Home 3D work in this repository. The intended location is:

`integrations/openPlan3D/`

Do not overwrite the repository root, Android wrapper, or Sweet Home 3D engine.

## Arabic localization started

`locales/ar.ts` contains the first Arabic translation batch for settings, floor elevations, AI provider settings, model selection, onboarding and starter templates.

This is an initial partial dictionary only. It is not yet connected to the OpenPlan3D application, and it must not be described as a complete Arabic translation.

## Remaining work

1. Import the complete upstream source and preserve its MIT license, notices, assets and history/provenance.
2. Integrate Arabic into the locale type, dictionaries, language selector and persistence.
3. Add RTL direction at the document/application shell and audit directional icons, drawers, toolbars, canvas controls, dialogs and properties panels.
4. Translate every remaining key in the upstream English locale, including errors, tooltips, exports, import validation and accessibility labels.
5. Preserve interpolation placeholders and technical file formats/units.
6. Run type-check, unit tests, production build and browser tests; then perform touch checks on Android.
7. Verify the existing Sweet Home 3D project remains unchanged.

## Current limitation

The GitHub connection available for this task can read and write individual repository files, but it does not provide a repository fork/import or archive upload operation. The current execution container cannot resolve GitHub for a direct clone. Therefore the full upstream source has **not** yet been copied into this repository. The branch contains only the initial Arabic localization resource and this integration record.
