#!/usr/bin/env python3
"""Patch user-visible English fragments in the generated Arabic JS localization."""
from pathlib import Path
import json
import re
import sys

DEFAULT = Path("third_party/SweetHome3D/SweetHome3DJS/lib/resources/localization_ar.json")
path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT
if not path.is_file():
    raise SystemExit(f"Generated Arabic localization file not found: {path}")

OVERRIDES = {
    "PrintPreviewPanel.pageLabel.text": "<html><p align='right'>الصفحة %1$d من %2$d</p></html>",
    "FileContentManager.pluginDescription": "إضافة Sweet Home 3D",
    "ImportedFurnitureWizardStepsPanel.findModelsMessage.text": "للعثور على مواقع توفر نماذج ثلاثية الأبعاد، تفضل بزيارة:\nhttp://www.sweethome3d.com/importModels.html",
    "HomePane.findMoreExamplesMessage.text": "للعثور على مزيد من النماذج التجريبية، تفضل بزيارة:\nhttp://www.sweethome3d.com/gallery.html",
    "VideoPanel.videoFormatComboBox.format": "%1$dx%2$d بكسل - %3$s - %4$d إطاراً في الثانية",
    "HomePane.EXPORT_TO_OBJ.ShortDescription": "تصدير بصيغة OBJ",
    "HomePane.EXPORT_TO_CSV.Name": "تصدير بصيغة CSV...",
    "HomePane.exportToOBJDialog.title": "تصدير إلى OBJ",
    "HomePane.EXPORT_TO_SVG.ShortDescription": "تصدير بصيغة SVG",
    "HomePane.exportToCSVDialog.title": "تصدير إلى CSV",
    "HomePane.EXPORT_TO_OBJ.Name": "تصدير بصيغة OBJ...",
    "HomePane.exportToSVGDialog.title": "تصدير إلى SVG",
    "HomePane.EXPORT_TO_CSV.ShortDescription": "تصدير بصيغة CSV",
    "HomePane.EXPORT_TO_SVG.Name": "تصدير بصيغة SVG...",
    "HomePane.ABOUT.ShortDescription": "عرض معلومات عن Sweet Home 3D",
    "HomePane.HELP.ShortDescription": "عرض تعليمات Sweet Home 3D",
    "HomePane.EXIT.ShortDescription": "إنهاء Sweet Home 3D",
    "HomePane.HELP.Name": "تعليمات Sweet Home 3D",
    "HomePane.about.title": "حول Sweet Home 3D",
    "HomeController.exportToOBJError": "تعذر إنشاء ملف OBJ \"%s\".",
    "HomeController.exportToSVGError": "تعذر إنشاء ملف SVG \"%s\".",
    "HomeController.exportToOBJMessage": "تصدير العرض ثلاثي الأبعاد بصيغة OBJ...",
    "HomeController.exportToCSVMessage": "تصدير الأثاث بصيغة CSV...",
    "HomeController.exportToSVGMessage": "تصدير المخطط بصيغة SVG...",
    "HomeController.sizeUpdateMessage": "(%s ميغابايت)",
    "HomeController.importedPluginMessage": "تم استيراد الإضافة بنجاح. ستصبح متاحة عند تشغيل Sweet Home 3D في المرة القادمة.",
    "HomeController.downloadUpdateMessage": "<p>انتقل إلى <a href=\"%1$s\">صفحة التنزيل</a>.</p>",
    "HomeController.saveError": "<html><p align='right'>تعذر حفظ المشروع في<br>\"%s\"<br><font size=\"-2\">%s</font></p></html>",
    "HomeController.checkUpdatesIOError": "<html><p align='right'>تعذر قراءة التحديثات المتاحة.<br>تحقق من إعدادات الاتصال بالإنترنت.<br><font size=\"-2\">%s</font></p></html>",
    "HomeController.checkUpdatesXMLError": "<html><p align='right'>تعذر تحليل معلومات التحديثات.<br><font size=\"-2\">%s</font></p></html>",
    "HomeController.applicationUpdateMessage": "<h2 class=\"تحديث\"><b>%1$s</b> - الإصدار <b>%2$s</b></h2><p class=\"releaseDateAndSize\">نُشر بتاريخ %3$s، الحجم %4$s</p>",
    "HomeController.libraryUpdateMessage": "<h2 class=\"تحديث\"><b>%1$s</b> - الإصدار <b>%2$s</b></h2><p class=\"releaseDateAndSize\">نُشر بتاريخ %3$s، الحجم %4$s</p>",
    "HomeController.openRepairedHomeMessage": "<html><p align='right'><b>تم إصلاح بيانات المشروع \"%s\" بنجاح.</b><br>احفظ المشروع الآن للاحتفاظ بالإصلاح.</p></html>",
    "HomeController.moreRecentVersionHome": "<html><p align='right'><b>أُنشئ المشروع \"%s\" باستخدام إصدار أحدث من Sweet Home 3D.</b><br><font size=\"-2\">لن تظهر في هذا الإصدار المعلومات التي أُضيفت في الإصدار الأحدث، وقد تُفقد إذا حفظت المشروع هنا. لتجنب فقدانها، احفظ المشروع باسم آخر.</font></p></html>",
    "HomePane.confirmExit.message": "<html><p align='right'><b>هل تريد بالتأكيد الخروج؟</b><br><font size='-2'>ستُفقد التعديلات غير المحفوظة على المشاريع إذا خرجت الآن.</font></p></html>",
    "HomePane.confirmSave.message": "<html><p align='right'><b>هل تريد حفظ المشروع %s؟</b><br><font size='-2'>ستُفقد التعديلات إذا لم تحفظها.</font></p></html>",
    "HomePane.confirmSaveNewerHome.message": "<html><p align='right'><b>هل تريد بالتأكيد حفظ المشروع \"%s\"؟</b><br><font size='-2'>أُنشئ هذا المشروع باستخدام إصدار أحدث من Sweet Home 3D. قد تُفقد المعلومات التي أُضيفت في الإصدار الأحدث إذا حفظته بهذا الإصدار. إذا أردت الاحتفاظ بها، فاحفظ المشروع باسم مختلف.</font></p></html>",
    "HomePane.confirmReplaceFurnitureLibrary.message": "<html><p align='right'><b>مكتبة الأثاث \"%s\" موجودة بالفعل.<br>هل تريد استبدالها بالمكتبة الجديدة؟</b></p></html>",
    "HomePane.confirmReplaceTexturesLibrary.message": "<html><p align='right'><b>مكتبة الخامات \"%s\" موجودة بالفعل.<br>هل تريد استبدالها بالمكتبة الجديدة؟</b></p></html>",
    "HomePane.confirmReplacePlugin.message": "<html><p align='right'><b>الإضافة \"%s\" موجودة بالفعل.<br>هل تريد استبدالها بالإضافة الجديدة؟</b></p></html>",
    "HomePane.confirmReplaceLanguageLibrary.message": "<html><p align='right'><b>مكتبة اللغة \"%s\" موجودة بالفعل.<br>هل تريد استبدالها بالمكتبة الجديدة؟</b></p></html>",
    "FileContentManager.confirmOverwrite.message": "<html><p align='right'><b>الملف \"%s\" موجود بالفعل.<br>هل تريد استبداله؟</b></p></html>",
    "TextureChoiceComponent.confirmDeleteSelectedCatalogTexture.message": "<html><p align='right'><b>هل تريد بالتأكيد حذف الخامات المستوردة المحددة؟</b><br><font size='-2'>لن تظهر الخامات المحذوفة في القائمة بعد الآن.</font></p></html>",
    "HomePane.confirmDeleteCameras.message": "<html><p align='right'><b>هل تريد بالتأكيد حذف نقاط العرض المحددة؟</b></p></html>",
    "HomePane.confirmDeleteCatalogSelection.message": "<html><p align='right'><b>هل تريد بالتأكيد حذف قطع الأثاث المستوردة المحددة من الكتالوج؟</b><br><font size='-2'>لا يمكن استعادة قطع الأثاث المحذوفة من الكتالوج.</font></p></html>",
    "HomePane.confirmExportAllToOBJ.message": "هل تريد تصدير العناصر المحددة في المخطط فقط، أم جميع عناصر الأثاث الموجودة على مستوى الأرض عند ارتفاع صفر؟",
    "SweetHome3D.confirmSaveAfter3DError.message": "<html><p align='right'>يجب إغلاق Sweet Home 3D الآن بسبب خطأ خطير في نظام العرض ثلاثي الأبعاد. هل تريد حفظ جميع المشاريع المعدلة؟</p></html>",
    "SweetHome3D.3DError.message": "<html><p align='right'>سيتم إغلاق Sweet Home 3D بسبب خطأ خطير في نظام العرض ثلاثي الأبعاد. يرجى تحديث برامج تشغيل بطاقة الرسومات (DirectX / OpenGL).</p></html>",
    "HomePane.openDamagedHome.message": "<html><p align='right'><b>تعذر قراءة %2$d من النماذج ثلاثية الأبعاد و/أو الصور المطلوبة للمشروع \"%1$s\" لأنها تالفة.</b><br>يمكنك متابعة تحرير المشروع، لكن عليك استبدال النماذج والصور التالفة أو حذف العناصر التي تستخدمها. اختر الإجراء الذي تفضله.</p></html>",
    "VideoPanel.createVideoError.message": "<html><p align='right'>تعذر إنشاء الفيديو.<br><font size='-2'>%s</font></p></html>",
    "VideoPanel.saveVideoError.message": "<html><p align='right'>تعذر حفظ الفيديو.<br><font size='-2'>%s</font></p></html>",
    "VideoPanel.confirmStopCreation.message": "<html><p align='right'>لم تنتهِ عملية إنشاء الفيديو. هل تريد إيقافها بالتأكيد؟</p></html>",
    "PhotoPanel.confirmStopCreation.message": "<html><p align='right'>لم تنتهِ عملية إنشاء الصور. هل تريد إيقافها بالتأكيد؟</p></html>",
    "PhotosPanel.confirmOverwrite.message": "<html><p align='right'>إذا تابعت، فستُستبدل بعض الملفات الموجودة في المجلد<br>%s<br>هل تريد المتابعة؟</p></html>",
    "VideoPanel.seconds.format": "%d ث",
    "VideoPanel.minutesSeconds.format": "%1$d د %2$02d ث",
    "VideoPanel.hoursMinutes.format": "%1$d س %2$02d د",
    "HomePane.about.message": "<html><font face=\"sans-serif\"><center><font size=\"+2\"><b>Sweet Home 3D</b></font> <sup>&reg;</sup><br><b>الإصدار %1$s</b><br><font size=\"-1\">إصدار Java %2$s</font><br><font size=\"-1\">إصدار Java 3D %3$s</font><br><font size=\"-1\">مستندات الطباعة مقدمة من <a href=\"http://www.lowagie.com/iText/\">iText</a></font><br><font size=\"-1\">تصدير SVG مقدم من <a href=\"http://java.freehep.org/\">FreeHEP</a></font><br><font size=\"-1\">تجميع مسارات SVG مقدم من <a href=\"http://xmlgraphics.apache.org/batik/\">Batik</a></font><br><font size=\"-1\">دعم التعبيرات الرياضية مقدم من <a href=\"http://www.eteks.com/jeks/en/\">JeksParser</a></font><br><font size=\"-1\">تصيير الصور مقدم من <a href=\"http://sunflow.sourceforge.net/\">SunFlow</a> و <a href=\"https://github.com/YafaRay\">YafaRay</a></font><p>الترجمة العربية: فتحي الداودية</p><p>للحصول على تحديثات البرنامج والإبلاغ عن الأخطاء<br><a href=\"http://www.sweethome3d.com/\">http://www.sweethome3d.com/</a></p><p><font size=\"-2\">© حقوق النشر 2024 <a href=\"mailto:info@sweethome3d.com\">info@sweethome3d.com</a> Space Mushrooms<br>موزع بموجب <a href=\"http://www.gnu.org/copyleft/gpl.txt\">رخصة جنو العمومية العامة</a></font></p></center></font></html>",
}

def placeholders(value: str):
    return sorted(re.findall(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]", value))

data = json.loads(path.read_text(encoding="utf-8"))
missing = sorted(set(OVERRIDES) - set(data))
if missing:
    raise SystemExit("Arabic localization is missing required UI keys: " + ", ".join(missing))

for key, value in OVERRIDES.items():
    before = data[key]
    if placeholders(before) != placeholders(value):
        raise SystemExit(f"Format placeholders changed for {key}: {placeholders(before)} -> {placeholders(value)}")
    data[key] = value

path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Patched {len(OVERRIDES)} Arabic UI strings in {path}")
