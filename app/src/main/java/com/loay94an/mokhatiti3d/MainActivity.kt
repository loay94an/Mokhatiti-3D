package com.loay94an.mokhatiti3d

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var workspace: PlanCanvasView
    private lateinit var preview: Room3DView
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 24, 42)
        window.navigationBarColor = Color.rgb(13, 24, 42)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildNativeInterface()
    }

    private fun buildNativeInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 14)
            setBackgroundColor(Color.rgb(13, 24, 42))
        }
        header.addView(TextView(this).apply {
            text = "مخططي 3D"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.RIGHT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "استوديو التصميم المعماري"
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 210))
            gravity = Gravity.RIGHT
        })
        root.addView(header)

        val tools = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.WHITE)
        }
        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 9, 8, 9)
        }
        fun tool(label: String, action: () -> Unit) {
            toolRow.addView(Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-2, 46).apply { marginEnd = 6 })
        }
        tool("جدار") { workspace.setTool(PlanCanvasTool.WALL); hint.text = "اسحب لرسم جدار جديد" }
        tool("تحديد وتحريك") { workspace.setTool(PlanCanvasTool.SELECT); hint.text = "المس الجدار لتحديده ثم اسحبه لتحريكه" }
        tool("حذف عنصر") { workspace.setTool(PlanCanvasTool.ERASE); hint.text = "المس الجدار الذي تريد حذفه" }
        tool("خصائص الجدار") { editWallProperties() }
        tool("تراجع") { workspace.undo(); preview.refresh() }
        tool("غرف") { val count = workspace.detectRooms(); preview.refresh(); hint.text = if (count > 0) "تم اكتشاف $count غرفة من الجدران المغلقة" else "لم تُكتشف غرف مغلقة؛ تأكد من اتصال الجدران" }
        tool("مسح") { workspace.clearPlan(); preview.refresh() }
        tool("حفظ") { workspace.savePlan(); Toast.makeText(this, "تم حفظ المشروع محلياً", Toast.LENGTH_SHORT).show() }
        tool("فتح محلي") { workspace.loadPlan(); preview.refresh(); Toast.makeText(this, "تم تحميل المشروع المحلي", Toast.LENGTH_SHORT).show() }
        tool("تصدير JSON") { exportProject() }
        tool("استيراد JSON") { importProject() }
        tool("عرض 3D") { show3d(true) }
        tool("مخطط 2D") { show3d(false) }
        tools.addView(toolRow)
        root.addView(tools)

        hint = TextView(this).apply {
            text = "اسحب بإصبعك على مساحة الرسم لإضافة الجدران"
            textSize = 12f
            setTextColor(Color.rgb(75, 91, 110))
            setPadding(16, 10, 16, 10)
            gravity = Gravity.RIGHT
        }
        root.addView(hint)

        workspace = PlanCanvasView(this)
        preview = Room3DView(this, workspace.plan, workspace.rooms)
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        preview.visibility = View.GONE

        val footer = TextView(this).apply {
            text = "المشروع المحلي  •  القياسات بالمتر  •  تصميم Android أصلي"
            textSize = 11f
            setTextColor(Color.rgb(105, 117, 132))
            setPadding(14, 12, 14, 12)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }
        root.addView(footer)
        setContentView(root)
    }

    private fun editWallProperties() {
        val wall = workspace.selectedWall()
        if (wall == null) {
            Toast.makeText(this, "حدد جداراً أولاً باستخدام أداة التحديد", Toast.LENGTH_SHORT).show()
            return
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 4)
        }
        val thickness = android.widget.EditText(this).apply {
            hint = "سماكة الجدار بالمتر"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(wall.thickness.toString())
        }
        val height = android.widget.EditText(this).apply {
            hint = "ارتفاع الجدار بالمتر"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(wall.height.toString())
        }
        fields.addView(thickness)
        fields.addView(height)
        AlertDialog.Builder(this)
            .setTitle("خصائص الجدار")
            .setView(fields)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تطبيق") { _, _ ->
                val t = thickness.text.toString().toFloatOrNull()
                val h = height.text.toString().toFloatOrNull()
                if (t == null || h == null || !workspace.updateSelectedWall(t, h)) {
                    Toast.makeText(this, "أدخل قيماً موجبة وصحيحة", Toast.LENGTH_SHORT).show()
                } else {
                    preview.refresh()
                    Toast.makeText(this, "تم تحديث خصائص الجدار", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun exportProject() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "Mokhatiti-Project.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun importProject() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Use the document picker result APIs when migrating to ComponentActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_EXPORT -> {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(workspace.exportPlanJson().toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("تعذر فتح الملف للكتابة")
                    Toast.makeText(this, "تم تصدير ملف المشروع", Toast.LENGTH_SHORT).show()
                }
                REQUEST_IMPORT -> {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("تعذر قراءة ملف المشروع")
                    if (!workspace.importPlanJson(json)) throw IllegalArgumentException("ملف المشروع غير صالح أو غير مدعوم")
                    preview.refresh()
                    Toast.makeText(this, "تم استيراد المشروع بنجاح", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "تعذر تنفيذ العملية", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQUEST_EXPORT = 4101
        private const val REQUEST_IMPORT = 4102
    }

    override fun onResume() { super.onResume(); if (::preview.isInitialized) preview.onResume() }

    override fun onPause() { if (::preview.isInitialized) preview.onPause(); super.onPause() }

    private fun show3d(show: Boolean) {
        workspace.visibility = if (show) View.GONE else View.VISIBLE
        preview.visibility = if (show) View.VISIBLE else View.GONE
        preview.refresh()
        hint.text = if (show) "معاينة مجسمة للمخطط الحالي" else "اختر أداة الجدار أو التحديد أو الحذف من الشريط"
    }
}
