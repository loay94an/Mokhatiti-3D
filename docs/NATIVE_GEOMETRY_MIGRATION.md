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
3. Upgraded local JSON persistence to schema version 5. It stores wall ID, level ID, thickness and height, and reads earlier wall-only versions by supplying defaults for missing fields.
4. Updated the 2D wall stroke to reflect metric thickness and the native OpenGL renderer to consume wall footprint and height data.
5. Added a half-edge wall graph room detector that splits long segments at T-junctions and extracts bounded room faces. Tests cover a closed room, adjacent rooms and endpoint tolerance.
6. Added wall selection, drag-move, delete, endpoint/orthogonal snapping, pinch zoom, two-finger pan and editable wall thickness/height. Added host-wall door/window entities with 2D wall gaps, 3D mesh cut-outs and reveals. Added an initial Arabic furniture catalog (sofa, beds, table, chair, cabinet), 2D footprint placement/movement and basic 3D block meshes. Added project levels with active-floor drawing, per-floor walls/furniture/rooms, saved elevations and stacked 3D floor placement.
7. Added Android document-picker JSON import/export while retaining local autosave.
8. Replaced the projected preview with an interactive OpenGL ES 2.0 renderer using triangle meshes for wall prisms and room floors, with orbit, pinch zoom and camera fitting. Concave room floors use ear-clipping triangulation.
9. Added JVM unit tests for wall geometry, room area, graph extraction, endpoint tolerance, concave triangulation, opening bounds and project validation; CI runs tests before assembling the APK.

This is a **first native model-port step**, not the complete Sweet Home 3D engine. It does not yet import the upstream Java model classes, implement robust curved walls or full wall-junction cleanup, provide fully editable door/window frame and swing systems, load detailed furniture models, or provide a complete professional CAD/BIM-grade 3D scene and project manager.

## Required next stages

### A. Complete source/dependency and license inventory
- Pin the exact official source archive/revision used for the port.
- Trace the transitive dependencies of `Home`, `Wall`, `Room`, `Level`, furniture, doors/windows, dimensions and textures.
- Classify each dependency as reusable Java logic, AWT/Swing desktop-only, Java 3D-only, native platform dependency, or third-party library.
- Record the exact license for every copied/adapted class and catalog asset.

### B. Native architectural model
- Replace the current endpoint graph with persistent junction identities and robust wall joins/intersections, including curved walls and overlap/self-intersection repair.
- Improve room boundary generation for complex and concave plans, disconnected segments and wall thickness offsets.
- Extend door/window editing with adjustable dimensions, swing direction, frame/sash geometry, and robust collision/host-wall updates.
- Replace the initial furniture blocks with detailed parametric/catalog models, transforms, elevation, dimensions, levels and material references.
- Extend project schema for full level metadata, openings, furniture and materials with migration tests.

### C. Native 2D editor
- Add resize, rotate, multi-select, grouping and full undo/redo command history.
- Add midpoint/perpendicular/parallel snapping, dimension lines, guides and precise numeric coordinate entry.
- Add wall alignment and join editing, keyboard/pen input, and configurable touch gesture arbitration.

### D. Native 3D engine
- Improve mesh topology, face normals, lighting/shadows and physically based materials.
- Cut actual door/window openings; generate reveals, wall caps, ceilings, stairs and roof geometry.
- Add camera pan, orthographic views, section/cutaway modes, picking and object manipulation.
- Add furniture/model catalog loading and material editing.

### E. Verification
- Unit tests for geometry, room area, room-face extraction, concave polygon triangulation, wall-opening bounds, furniture transforms, invalid coordinates and project validation.
- Compatibility tests loading schema v2/v3 projects and saving/reopening schema v5.
- Instrumented tests on phone and tablet profiles; verify touch, rotation, memory and large plans.
- Do not label the APK complete until these tests and all advertised tools pass.

## References inspected

- Sweet Home 3D source mirror: https://github.com/lurume84/SweetHome3D
- `Home.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Home.java
- `Wall.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Wall.java
- `Room.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Room.java
- `Level.java`: https://github.com/lurume84/SweetHome3D/blob/master/SweetHome3D/src/com/eteks/sweethome3d/model/Level.java
- Official licensing: https://www.sweethome3d.com/license/

