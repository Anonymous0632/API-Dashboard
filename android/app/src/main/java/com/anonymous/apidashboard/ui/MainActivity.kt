package com.anonymous.apidashboard.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anonymous.apidashboard.data.ConfigImporter
import com.anonymous.apidashboard.data.SecureStore
import com.anonymous.apidashboard.network.QuotaRepository
import com.anonymous.apidashboard.widget.ApiQuotaWidget
import com.anonymous.apidashboard.worker.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var input: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        RefreshWorker.schedule(this)
        updateStatus("请粘贴或选择 aiquota-token.json，然后导入。")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_JSON && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                scope.launch {
                    val raw = readUri(uri)
                    input.setText(raw)
                    importRaw(raw)
                }
            }
        }
    }

    private fun buildContent(): ScrollView {
        val padding = dp(18)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(TextView(this).apply {
            text = "API Dashboard"
            textSize = 24f
            setTextColor(0xFF111827.toInt())
        })
        root.addView(TextView(this).apply {
            text = "安卓小组件配置"
            textSize = 14f
            setTextColor(0xFF4B5563.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF1F2937.toInt())
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(status)
        input = EditText(this).apply {
            hint = "粘贴 aiquota-token.json 内容"
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            setSingleLine(false)
            setTextColor(0xFF111827.toInt())
            setHintTextColor(0xFF9CA3AF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220),
            )
        }
        root.addView(input)
        root.addView(button("从剪贴板粘贴并导入") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            input.setText(raw)
            scope.launch { importRaw(raw) }
        })
        root.addView(button("选择 JSON 文件导入") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQ_PICK_JSON)
        })
        root.addView(button("导入上方文本") {
            scope.launch { importRaw(input.text.toString()) }
        })
        root.addView(button("立即刷新小组件") {
            scope.launch { refreshNow() }
        })
        root.addView(TextView(this).apply {
            text = "导入后，在桌面添加 API Dashboard 小组件。MiniMax 钱包余额建议继续使用 Cloudflare Worker 代理。"
            textSize = 13f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, dp(14), 0, 0)
        })
        return ScrollView(this).apply { addView(root) }
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setAllCaps(false)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
    }

    private suspend fun importRaw(raw: String) {
        if (raw.trim().isEmpty()) {
            updateStatus("没有可导入的 JSON。")
            return
        }
        runCatching {
            val result = ConfigImporter(SecureStore(this)).importConfig(raw)
            if (result.imported.isEmpty()) {
                updateStatus("JSON 已读取，但没有发现可导入的平台凭证。")
            } else {
                updateStatus("已导入：${result.imported.joinToString(" + ")}，正在刷新。")
                RefreshWorker.schedule(this)
                refreshNow()
            }
        }.onFailure {
            updateStatus("导入失败：${it.message ?: "JSON 格式错误"}")
        }
    }

    private suspend fun refreshNow() {
        updateStatus("正在刷新数据...")
        runCatching {
            withContext(Dispatchers.IO) {
                QuotaRepository(this@MainActivity).refresh()
            }
            ApiQuotaWidget().updateAll(this)
        }.onSuccess {
            updateStatus("刷新完成，可回到桌面查看小组件。")
        }.onFailure {
            updateStatus("刷新失败：${it.message ?: "网络请求异常"}")
        }
    }

    private suspend fun readUri(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
    }

    private fun updateStatus(message: String) {
        status.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_PICK_JSON = 1001
    }
}
