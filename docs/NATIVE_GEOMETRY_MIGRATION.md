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



---

# الخطة المرجعية الشاملة لإكمال Mokhatiti-3D إلى إصدار نهائي

> هذه الخطة هي مرجع التنفيذ والقبول. لا يُعد وجود واجهة أو كود أولي دليلاً على اكتمال الوظيفة. تُحدّث حالة كل بند بعد تعديل الكود وتشغيل الاختبار المرتبط به. لا يُنشأ APK للتسليم قبل إغلاق بوابات الإصدار النهائية.

## الهدف النهائي

تطبيق Android عربي RTL لتخطيط المنازل والتصميم الداخلي على الهاتف والتابلت، مع محرر معماري 2D ومشهد 3D متزامن، نموذج مشروع قابل للتحرير والحفظ، مكتبة عناصر، أدوات قياس وإخراج، وتجربة لمس أصلية. يبقى مسار التشغيل Native بلا WebView وفق القرار المعتمد في هذا الفرع. هذا لا يعني الادعاء بأن محرك Sweet Home 3D الأصلي قد نُقل بالكامل؛ أي إعادة استخدام لمصدره تتم بعد تدقيق الاعتماديات والترخيص، مع توثيق ما تم نقله فعلياً.

## قواعد التنفيذ

1. العمل على `feat/native-sweethome3d-core-port` وعدم تعديل `main` أو `native-engine-foundation` مباشرة.
2. التنفيذ المتوازي يكون عبر مسارات مستقلة (الهندسة، المحرر، 3D، المشاريع، الواجهة والاختبارات)، مع دمج التغييرات الصغيرة على الفرع نفسه بعد فحص التعارضات.
3. لا تُعلن ميزة مكتملة إلا إذا كانت قابلة للاستخدام واختبارها موثقاً.
4. لا يُنشأ APK جديد لمجرد أن المستخدم طلب إصلاحاً؛ يُؤجل بناء نسخة التسليم إلى ما بعد إغلاق الإصلاحات وبوابات الجودة.
5. الحفاظ على التوافق مع ملفات المشاريع السابقة، وإضافة ترحيل واختبارات عند تغيير المخطط.
6. لا تُستورد مكتبات أو نماذج أو خامات دون مراجعة الترخيص والحجم والأداء على Android.

## سجل المراحل وبنود القبول

### المسار A — تدقيق المحرك والمصدر والتراخيص
- [ ] توثيق المصدر الدقيق والنسخة/commit لأي شفرة مقتبسة من Sweet Home 3D.
- [ ] جرد الاعتماديات الانتقالية لنموذج Home وWall وRoom وLevel والفتحات والأثاث والأبعاد والخامات.
- [ ] تصنيف اعتماديات Java/AWT/Swing/Java3D وفصل ما لا يعمل على Android.
- [ ] مراجعة تراخيص كل شفرة ونموذج وخامة على حدة وإضافة إشعارات المصدر.
- [ ] تحديد حدود ما نُقل فعلياً وما هو تنفيذ Native مستقل؛ منع وصف المحرك بأنه Sweet Home 3D كامل قبل تحقق ذلك.

**قبول المسار:** سجل مصدر واعتماديات وتراخيص قابل للمراجعة، دون إدخال مكونات سطح مكتب غير متوافقة.

### المسار B — النموذج المعماري والهندسة
- [ ] هوية ثابتة لوصلات الجدران والعقد، ومعالجة T/X junctions والتقاطعات.
- [ ] وصلات الجدران والزوايا والقصّات وسماكات الجدران دون فجوات أو تداخلات.
- [ ] الجدران المنحنية والأقواس أو توثيق حدود الدعم بوضوح.
- [ ] كشف الغرف للخطط المقعرة والمتجاورة والمنفصلة ومعالجة الحدود غير المغلقة.
- [ ] تحقق هندسي من التداخلات الذاتية والقيم غير الصالحة وحدود الفتحات.
- [ ] فتحات أبواب ونوافذ قابلة للتحرير مع الأبعاد والارتفاع واتجاه الفتح والإطار والضلفة.
- [ ] نموذج أثاث بمعرفات وأبعاد وتحويلات وارتفاع ومواد ومستوى.
- [ ] توسيع مخطط المشروع مع ترحيل آمن واختبارات توافق للإصدارات السابقة.

**قبول المسار:** اختبارات هندسية تغطي الحالات الطبيعية والحدية، وعدم فقدان أو تشويه العناصر عند الحفظ وإعادة الفتح.

### المسار C — محرر 2D والأدوات الدقيقة
- [ ] أوضاع واضحة للرسم والتحديد والتحريك والتعديل والحذف.
- [ ] تعديل نقاط البداية والنهاية والأطوال والزوايا والسماكة والارتفاع رقمياً.
- [ ] تحديد متعدد، نسخ/لصق، تجميع/فك تجميع، تدوير وتغيير الحجم.
- [ ] تراجع/إعادة قائم على أوامر كاملة لكل أنواع العناصر، لا حذفاً احتياطياً محدوداً.
- [ ] Snap للنهايات والمنتصف والتعامد والتوازي والتقاطعات والشبكة، مع إعدادات.
- [ ] خطوط أبعاد وقياسات ومساحات وتسميات وأدلة محاذاة.
- [ ] أدوات تحرير فتحات الجدران والغرف والطوابق.
- [ ] دعم لوحة المفاتيح والقلم حيث يتوفران.

**قبول المسار:** كل أداة مرئية تعمل على بيانات المشروع فعلياً، مع اختبارات لمس/ماوس/قلم حسب الجهاز.

### المسار D — تجربة اللمس والهاتف والتابلت
- [ ] فصل إيماءات الرسم عن التحريك والتكبير ومنع التداخل بينها.
- [ ] سلوك إنشاء الجدار باللمس: لمس نقطة البداية، سحب/تحديد النهاية، إظهار المعاينة ثم اعتمادها.
- [ ] مقابض لمس مناسبة لكثافة الشاشة والتكبير.
- [ ] ثبات نقطة الارتكاز أثناء pinch zoom وعدم حدوث قفز أو ترجمة مزدوجة.
- [ ] تمييز النقر والسحب والضغط المطوّل واللمس المتعدد.
- [ ] تخطيط متجاوب للهاتف والتابلت والاتجاهين الأفقي والعمودي.
- [ ] دعم القلم وإدخال لوحة المفاتيح دون كسر تجربة اللمس.

**قبول المسار:** سيناريوهات لمس موثقة على ملفات جهاز هاتف وجهاز لوحي/محاكيين بمقاسات مختلفة، مع عدم تداخل الأدوات.

### المسار E — محرك 3D والمزامنة
- [ ] جودة طوبولوجيا المجسمات والـ normals ومعالجة الأسطح والوجوه.
- [ ] فتحات حقيقية للأبواب والنوافذ مع reveals وإطارات.
- [ ] توليد الأسقف والسلالم والدرابزين والقبعات/النهايات الهندسية.
- [ ] خامات ومواد وألوان وإضاءة وظلال مع أداء مناسب.
- [ ] تحريك الكاميرا وتدويرها والتقريب والإسقاط المنظوري/المتعامد.
- [ ] تحديد العناصر من المشهد والتلاعب بها ومزامنة التحديد مع 2D.
- [ ] أوضاع مقطع/قصّ وعرض داخلي عند الحاجة.
- [ ] تحديث المشهد بعد كل تعديل 2D دون تأخر أو اختلاف في القياسات.

**قبول المسار:** اختبار مزامنة لكل نوع عنصر بين 2D و3D، وفحص الأداء والذاكرة على مشاريع صغيرة وكبيرة.

### المسار F — مكتبة العناصر والأثاث
- [ ] استبدال صناديق الأثاث الأولية بنماذج تفصيلية أو عناصر parametric مناسبة.
- [ ] تصنيفات أثاث وغرف ومطابخ وحمامات وأجهزة وعناصر معمارية.
- [ ] بحث وتصفية ومعاينة وإضافة وحذف وتكرار.
- [ ] تغيير الأبعاد والارتفاع والدوران والمواد والألوان.
- [ ] استيراد صيغ نماذج مدعومة بعد مراجعة الترخيص والأمان والحجم.
- [ ] حفظ مرجع النموذج ومواده ضمن المشروع أو آلية حزم موثوقة.

**قبول المسار:** كل عنصر في المكتبة يضاف ويُحرر ويُحفظ ويظهر بشكل صحيح في 2D و3D.

### المسار G — إدارة المشاريع والبيانات
- [ ] شاشة إدارة مشاريع متعددة مع إنشاء وفتح وإعادة تسمية ونسخ وحذف.
- [ ] حفظ تلقائي آمن واستعادة بعد الإغلاق المفاجئ.
- [ ] فتح مشروع جديد دون الاعتماد على وجود ملف محفوظ مسبقاً.
- [ ] استيراد وتصدير عبر Android Storage Access Framework.
- [ ] تحقق من سلامة الملف ورسائل خطأ مفهومة عند الملفات التالفة.
- [ ] ترحيل إصدارات JSON السابقة واختبارات round-trip لكل عنصر.
- [ ] حفظ الصور المصغرة والبيانات الوصفية وإدارة وحدات القياس.

**قبول المسار:** إنشاء مشروع من الصفر، تعديله، إغلاق التطبيق، إعادة تشغيله وفتحه دون فقدان أو تغير هندسي.

### المسار H — القياسات والإخراج والطباعة
- [ ] أبعاد خطية وزاوية ومساحة وارتفاعات.
- [ ] إعداد مقياس الرسم والوحدات ودقة التقريب.
- [ ] تصدير PDF متعدد الصفحات مع أحجام الورق والهوامش.
- [ ] تصدير SVG وDXF أو صيغ CAD التي يتم دعمها فعلياً.
- [ ] تصدير صور المخطط والمشهد ثلاثي الأبعاد.
- [ ] معاينة الطباعة وإظهار الأبعاد والعناوين والمقياس.
- [ ] تقارير مساحات الغرف وكميات العناصر عند توفر بياناتها.

**قبول المسار:** فتح الملفات المصدرة في قارئات/برامج مناسبة والتحقق من الأبعاد والمقياس وعدم قص المحتوى.

### المسار I — الواجهة العربية وتجربة الاستخدام
- [ ] تعريب كامل لكل الشاشات والقوائم والأوامر والحوارات ورسائل الخطأ.
- [ ] RTL فعلي دون قلب اتجاه الأرقام أو القياسات أو رموز الأدوات بشكل خاطئ.
- [ ] شريط أدوات سياقي ولوحات خصائص سهلة للهاتف والتابلت.
- [ ] حالات فارغة وتعليمات بداية ومشروع نموذجي اختياري غير مفروض.
- [ ] إمكانية الوصول: تباين، أحجام لمس، وصف عناصر التحكم.
- [ ] توحيد المصطلحات الهندسية ووحدات القياس.

**قبول المسار:** جولة استخدام كاملة بالعربية دون نصوص غير مترجمة أو عناصر متداخلة.

### المسار J — الاختبارات والأداء والأمان
- [ ] اختبارات وحدات للهندسة والتحقق والترحيل.
- [ ] اختبارات تكامل للحفظ والفتح والاستيراد والتصدير.
- [ ] اختبارات Instrumentation لتدفقات المستخدم الرئيسية.
- [ ] اختبار أول تشغيل، مشروع فارغ، ملف تالف، انقطاع/استعادة التطبيق.
- [ ] اختبار خطط كبيرة وكثرة عناصر واستهلاك الذاكرة ومعدل الإطارات.
- [ ] اختبار توافق إصدارات Android وأحجام الشاشات.
- [ ] فحص الأعطال والسجلات والصلاحيات وعدم وجود أزرار شكلية.
- [ ] اختبار تثبيت/تحديث/إزالة وتثبيت نظيف على أجهزة فعلية.

**قبول المسار:** نجاح الاختبارات المطلوبة وتوثيق الأجهزة والنتائج والأخطاء المعالجة؛ نجاح Gradle وحده لا يكفي.

### المسار K — إعداد الإصدار النهائي
- [ ] إعداد Release build منفصل عن Debug.
- [ ] إعداد توقيع إصدار آمن وعدم تضمين أسرار التوقيع في المستودع.
- [ ] مراجعة الصلاحيات والاعتماديات والتراخيص.
- [ ] فحص APK/AAB ومعلومات الحزمة والإصدار والتوافق.
- [ ] اختبار تثبيت النسخة الموقعة وتشغيلها على هاتف وجهاز لوحي.
- [ ] إعداد سجل تغييرات وتعليمات تثبيت واستعادة مفتاح التوقيع.
- [ ] نشر ملف الإصدار فقط بعد اعتماد جميع بوابات القبول السابقة.

**قبول الإصدار:** نسخة Release موقعة، مثبتة ومختبرة، مع تقرير اختبارات واضح. لا يُستخدم APK Debug كملف تسليم نهائي.

## طريقة العمل المتزامن

تُنفذ المسارات التالية بالتوازي قدر الإمكان، مع واجهات عقود واضحة لتجنب التعارض:
- فريق/مسار الهندسة والنموذج (A+B).
- مسار محرر 2D واللمس (C+D).
- مسار 3D والمزامنة (E).
- مسار المكتبة والمشاريع (F+G).
- مسار القياسات والتصدير والواجهة (H+I).
- مسار الاختبارات والتوافق والترخيص (J+A)، ويكتب اختبارات مبكرة بالتوازي ولا ينتظر نهاية التطوير.

تُدمج التغييرات على دفعات صغيرة، ويُعاد تشغيل الاختبارات المتأثرة بعد كل دفعة. لا يعني "بالتوازي" تعديل الملفات نفسها بشكل متعارض أو تجاوز الاعتمادات؛ الأعمال التي تعتمد على نموذج البيانات تنتظر تثبيت عقده، بينما يستمر إعداد الاختبارات والواجهة والتراخيص بالتزامن.

## لوحة التقدم

| المسار | الحالة عند اعتماد هذه الخطة |
| --- | --- |
| A المصدر والتراخيص | قيد التدقيق |
| B الهندسة والنموذج | أساس موجود؛ استكمال مطلوب |
| C محرر 2D | أساس موجود؛ استكمال مطلوب |
| D اللمس والهاتف والتابلت | تحسينات سابقة؛ اختبارات وإكمال مطلوب |
| E محرك 3D | أساس OpenGL موجود؛ استكمال كبير |
| F المكتبة | عناصر كتلية أولية؛ غير مكتملة |
| G المشاريع | حفظ JSON محلي موجود؛ إدارة متعددة واختبارات ناقصة |
| H القياسات والتصدير | غير مكتمل |
| I العربية وتجربة الاستخدام | واجهة عربية أولية؛ مراجعة شاملة مطلوبة |
| J الاختبارات والجودة | اختبارات JVM وCI موجودة؛ تغطية تفاعلية/أجهزة ناقصة |
| K الإصدار النهائي | لم يعتمد |

**آخر تحديث:** 2026-11-25. هذه اللوحة تصف حالة التخطيط ولا تدّعي إنجاز البنود غير المختبرة.


## سجل التنفيذ الفعلي بعد اعتماد الخطة — 2026-11-25

### تغييرات بدأت ونُفذت على فرع التطوير
- [x] إضافة الخطة المرجعية الشاملة أعلاه ومعايير قبول كل مسار.
- [x] ربط الخطة من README وتحديث وثيقة ENGINE-INTEGRATION القديمة لتوضيح أن اقتراح WebView ملغى لهذا الفرع.
- [x] مسار الهندسة B: إضافة تقسيم تقاطعات الجدران الداخلية (X crossings) قبل استخراج الوجوه، مع اختبار جديد لمخطط مربع تتقاطع قطراه ويجب أن ينتج أربع مناطق بمجموع مساحة 16 م².
- [x] مسار المحرر C: إضافة إعادة التنفيذ Redo بجانب Undo، وتحويل Undo إلى سجل صريح لا يحذف جداراً عشوائياً عند عدم وجود تاريخ.
- [x] مسار المحرر/النموذج B+C: حفظ حالة التراجع قبل اكتشاف الغرف فقط عند تغير النتيجة، ومنع فتحات الأبواب والنوافذ الافتراضية من التداخل على الجدار نفسه.
- [x] مسار المشاريع G: التحقق من صيغة JSON وإصداراتها المدعومة (2 إلى 5) قبل اعتماد الاستيراد، ومسح سجل التراجع عند فتح مشروع آخر حتى لا تنتقل تغييرات المشروع السابق إلى المشروع الجديد.
- [x] مسار الطوابق B/G: الحفاظ على غرف الطوابق غير النشطة عند تعديل جدار في الطابق الحالي.
- [x] مسار CI/J: تحويل سير العمل أثناء مرحلة الإكمال إلى اختبارات JVM فقط، وتأجيل تجميع/رفع APK حتى إغلاق بوابات القبول النهائية.

### مراجع commits التنفيذ
- الخطة المرجعية: `9411e470be1a905aeb7ce7de8005858222bfe130`
- اكتشاف تقاطع الجدران: `d8783834af4a365cd8dba6bac752f5a70820111e`
- اختبار تقاطع الجدران: `965467b8ae604d5d7ba6a5f3de6584973fd7f053`
- سجل Undo/Redo والتحقق من الاستيراد والفتحات: `c55e4836798a66bca33cb8974004e64dace0f152`
- إصلاحات حفظ غرف الطوابق وإظهار Redo: `7207e10add2062949e49c23ab895ca8a66dbe1f4`
- CI اختبارات فقط: `ca6706780164d9e21c122a3dac16244cb9683a7d`
- آخر تعديل لحفظ غرف الطوابق عند إضافة الفتحات: `fc0c6eb5231ebcda8728867d4e80ac95e487e1d7`

### حالة التحقق
- اختبار JVM على commit `fc0c6eb5231ebcda8728867d4e80ac95e487e1d7` قيد التنفيذ في GitHub Actions، ولم تُسجل نتيجة نجاح بعد.
- لا يُعلن نجاح اختبار تقاطعات الجدران أو عدم وجود أخطاء ترجمة قبل صدور نتيجة CI.
- تشغيلان قديمان من سير العمل السابق (على commits أسبق من تحويل CI) كانا قد رفعا بالفعل ملفي Debug artifact قبل إيقاف تغليف APK في المسار الجديد. هذان الملفان ليسا إصداراً ولا نسخة تسليم، ولم يُطلب إنشاؤهما؛ لا يستطيع التعديل اللاحق محو ما رُفع سابقاً. المسار الجديد لا يجمع APK.
- لم تُغلق بعد بقية الأعمال في المحرك، اللمس، 3D، المكتبة، إدارة المشاريع، التصدير، الترجمة والاختبارات على الأجهزة.

**قاعدة تحديث السجل:** تُنقل أي خانة من "قيد التنفيذ" إلى "منجزة" فقط بعد نجاح اختبار قبولها. علامة [x] أعلاه تعني أن التعديل البرمجي أُدخل إلى الفرع، لا أن اختباره النهائي قد اجتاز.
