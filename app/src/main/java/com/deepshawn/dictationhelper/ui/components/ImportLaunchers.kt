package com.deepshawn.dictationhelper.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.deepshawn.dictationhelper.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 拍照 / 选图 / JSON 导入导出的系统入口 */
class ImportActions(
    val takePhoto: () -> Unit,
    val pickImage: () -> Unit,
    val pickJson: () -> Unit,
    val pasteJson: () -> Unit,
    val exportJson: () -> Unit,
)

@Composable
fun rememberImportActions(vm: MainViewModel): ImportActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun handleImage(uri: Uri?) {
        if (uri == null) return
        if (vm.settings.value.apiKey.isBlank()) {
            vm.toast("请先在「设置」中填写 DeepSeek API 密钥")
            return
        }
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { loadBitmap(context, uri) }.getOrNull() }
            if (bmp == null) vm.toast("无法读取图片") else vm.recognize(bmp)
        }
    }

    val photoUri = remember { arrayOfNulls<Uri>(1) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) handleImage(photoUri[0])
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handleImage(uri)
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text.isNullOrBlank()) vm.toast("无法读取文件") else vm.importJson(text)
        }
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.exportJson().toByteArray()) }
                }.isSuccess
            }
            vm.toast(if (ok) "已导出" else "导出失败")
        }
    }

    return remember(vm) {
        ImportActions(
            takePhoto = {
                runCatching {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    photoUri[0] = uri
                    takePicture.launch(uri)
                }.onFailure { vm.toast("无法打开相机：${it.message}") }
            },
            pickImage = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            pickJson = {
                runCatching { openJson.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    .onFailure { vm.toast("无法打开文件选择器") }
            },
            pasteJson = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                if (text.isNullOrBlank()) vm.toast("剪贴板为空") else vm.importJson(text)
            },
            exportJson = {
                val name = "词表_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date()) + ".json"
                runCatching { createJson.launch(name) }.onFailure { vm.toast("无法打开文件选择器") }
            },
        )
    }
}

/** 读取图片并按需降采样（最长边约 2048），自动处理旋转 */
fun loadBitmap(context: Context, uri: Uri): Bitmap {
    val maxSide = 2048
    return if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("decode failed")
    }
}
