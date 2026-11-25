# سجل قرار المحرك — Mokhatiti 3D

## حالة الوثيقة

هذه الوثيقة كانت مقترحاً أولياً لاستخدام Sweet Home 3D JS داخل WebView. **هذا المقترح ملغى لهذا الفرع** ولا يمثل بنية التطبيق الحالية أو القرار المعتمد.

## القرار الحالي

- الفرع: `feat/native-sweethome3d-core-port`.
- التشغيل المستهدف: Android Native باستخدام Kotlin وAndroid Views وCanvas وOpenGL ES.
- لا WebView ولا HTML ولا JavaScript في مسار تشغيل التطبيق.
- التنفيذ الحالي هو محرك Native مستقل يستلهم مفاهيم نموذج Sweet Home 3D؛ لا يجوز وصفه بأنه نقل كامل للمحرك الأصلي.
- أي نقل لاحق لشفرة Sweet Home 3D يخضع لتحليل الاعتماديات وفصل AWT/Swing/Java3D ومراجعة GPL-2.0-or-later والإشعارات.

## مراجع التنفيذ الملزمة

راجع الوثيقة [NATIVE_GEOMETRY_MIGRATION.md](NATIVE_GEOMETRY_MIGRATION.md) للحصول على:
- تحليل أصناف Home وWall وRoom وLevel.
- نتائج النقل الأصلي الحالي.
- قائمة المراحل الناقصة ومسارات العمل المتزامنة.
- معايير القبول والاختبارات المطلوبة قبل الإصدار النهائي.

## المراجع الخارجية

- https://www.sweethome3d.com/
- https://www.sweethome3d.com/license/
- https://github.com/lurume84/SweetHome3D/tree/master/SweetHome3DJS

أي كود أو نموذج أو خامة من مصدر خارجي يجب أن يسجل مصدره وترخيصه بصورة مستقلة قبل الدمج.
