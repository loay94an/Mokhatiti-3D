# Native geometry migration — Sweet Home 3D

## Decision and non-negotiable constraints

- Target: Android-native Kotlin/Java UI and rendering; **no WebView, HTML or JavaScript runtime**.
- Work is isolated on `feat/native-sweethome3d-core-port`, based on `native-engine-foundation`.
- Keep `main` and `native-engine-foundation` unchanged until a reviewed, tested migration is ready.
- Preserve the original Sweet Home 3D copyright and license notices for any source file actually copied or adapted. The free Sweet Home 3D application is GPL-2.0-or-later; every imported model/catalog asset needs its own license review.

## Source inspection

The inspected upstream mirror is `lurume84/SweetHome3D`, which identifies itself as an unofficial mirror of the SourceForge SVN project (revision r9047 / release 7.5.2). It is a reference snapshot, not a claim that it is the latest official release.

Relevant model classes inspected:

| Sweet Home 3D class | Architectural responsibility | Android migration finding |
| --- | --- | --- |
| `Home` | Project aggregate: walls, rooms, furniture, levels, dimensions, labels and cameras | Large object graph; depends on many model/support classes, Java serialization and JavaBeans change notifications. Do not import in isolation. |
| `Wall` | Wall endpoints, thickness, height, arc, joins, side finishes and level | Geometry methods import `java.awt.Shape`, `GeneralPath`, `Line2D`, `Point2D` and `Rectangle2D`; those AWT geometry APIs are not an Android rendering API. Port geometry to platform-neutral primitives. |
| `Room` | Polygon boundary, name, floor/ceiling finish, area and level | Uses `java.awt.Shape`, `Area`, `GeneralPath`, `PathIterator` and `Rectangle2D`; polygon and area logic must be migrated without AWT. |
| `Level` | Storey name, elevation, floor thickness, height and visibility | The data concept is portable; adapt it to the project's metric model and persistence. |

The desktop UI/rendering layer is not part of this migration. Swing, Java 3D, AWT drawing, desktop file choosers and desktop print/export code must not enter the Android runtime by accident.

## First native migration increment in this branch

1. Added `PlanGeometry.kt`, an Android-independent metric domain layer with finite-coordinate validation, wall length/thickness/height, wall footprint polygon, room polygon area, level metadata, project validation and project bounds.
2. Moved `PlanWall` out of the drawing view so the view consumes the domain model rather than owning it.
3. Upgraded local JSON persistence to schema version 3. It stores wall ID, level ID, thickness and height, and still reads older files by supplying defaults for fields absent from version 2.
4. Updated the 2D wall stroke to reflect metric thickness and the axonometric preview to render wall footprints as 3D prisms using each wall's height.\n5. Added JVM unit tests for wall length/footprint, invalid geometry, room polygon area and project-level validation; CI now runs the unit-test task before assembling the APK.

This is a **first native model-port step**, not the complete Sweet Home 3D engine. It does not yet import the upstream Java model classes, implement robust curved walls or full wall-junction cleanup, create door/window openings, load furniture models, or provide a complete professional CAD/BIM-grade 3D scene and project manager.

## Required next stages

### A. Complete source/dependency and license inventory
- Pin the exact official source archive/revision used for the port.
- Trace the transitive dependencies of `Home`, `Wall`, `Room`, `Level`, furniture, doors/windows, dimensions and textures.
- Classify each dependency as reusable Java logic, AWT/Swing desktop-only, Java 3D-only, native platform dependency, or third-party library.
- Record the exact license for every copied/adapted class and catalog asset.

### B. Native architectural model
- Add connected wall graph and stable endpoint/junction identities.
- Add robust wall joins, intersections, wall thickness footprints and curved-wall representation.
- Derive closed room polygons from wall topology; handle gaps, overlaps and self-intersections.
- Add doors/windows as wall-hosted openings, not free-standing decorative objects.
- Add furniture transforms, elevation, dimensions, levels and material references.
- Use a versioned, documented project format with migration tests.

### C. Native 2D editor
- Selection, move, resize, rotate, delete, multi-select and undo/redo commands.
- Snap-to-grid, endpoint/midpoint/orthogonal snapping, dimensions and precise numeric entry.
- Gesture arbitration for pan, zoom, drawing and selection on phones and tablets.

### D. Native 3D engine
- Generate floor and wall meshes from the same model graph used by 2D.
- Cut actual door/window openings; generate reveals and wall caps.
- Add camera orbit/pan/zoom, lighting, materials and furniture model loading.
- Evaluate Android-supported rendering technology separately; do not pull in Java 3D or a WebView as a shortcut.

### E. Verification
- Unit tests for geometry, room area, intersections, invalid coordinates and project validation.
- Compatibility tests loading schema v2 projects and saving/reopening schema v3.
- Instrumented tests on phone and tablet profiles; verify touch, rotation, memory and large plans.
- Do not label the APK complete until these tests and all advertised tools pass.

## References inspected

- Sweet Home 3D source mirror: https://github.com/lurume84/SweetHome3D
- `Home.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Home.java
- `Wall.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Wall.java
- `Room.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Room.java
- `Level.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Level.java
- Official licensing: https://www.sweethome3d.com/license/

