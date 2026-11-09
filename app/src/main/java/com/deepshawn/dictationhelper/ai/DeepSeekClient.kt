package com.deepshawn.dictationhelper.ai

import android.graphics.Bitmap
import android.util.Base64
import com.deepshawn.dictationhelper.data.Draft
import com.deepshawn.dictationhelper.data.WordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekException(message: String) : Exception(message)

/** AI 补全的结果 */
data class Completion(
    val translation: String,
    val note: String,
    val type: String?,
)

/**
 * DeepSeek 开放平台客户端（OpenAI 兼容接口）。
 * 用户只需在设置中填写 API 密钥；接口地址与模型名固定在此处。
 */
object DeepSeekClient {
    private const val BASE_URL = "https://api.deepseek.com"
    /** 文本模型：用于自动补全释义 */
    const val TEXT_MODEL = "deepseek-v4-flash"
    /** 视觉模型：用于拍照/图片识别词表 */
    const val VISION_MODEL = "deepseek-v4-flash-vision-exp"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val VISION_PROMPT = """你是一个听写词表整理助手。请识别图片中所有需要听写/背诵的中文或英文内容（词汇、短语、句子），忽略页码、题号、序号、页眉页脚和无关装饰文字。
如果图片中同时给出了中英对照，请把对应的释义放到 translation；没有给出时，请你补充简短的中文或英文释义。
只输出 JSON，不要输出任何解释，格式：
{"items":[{"text":"原文","translation":"释义","type":"word|phrase|sentence"}]}"""

    private const val COMPLETE_PROMPT = """你是词典助手。用户给出一个中文或英文的词汇/短语/句子，请给出：
- translation：若原文是英文，给出简洁的中文释义（多个义项用“；”分隔）；若原文是中文，给出英文翻译。
- note：英文单词给出美式音标，例如 /ˈæp.əl/；中文给出带声调的拼音；短语或句子可给一个简短例句或留空。
- type：word、phrase 或 sentence。
只输出 JSON：{"translation":"...","note":"...","type":"..."}"""

    /** 识别图片中的词表 */
    suspend fun recognizeImage(apiKey: String, bitmap: Bitmap): List<Draft> = withContext(Dispatchers.IO) {
        val dataUrl = "data:image/jpeg;base64," + encodeJpeg(bitmap)
        val body = buildJsonObject {
            put("model", VISION_MODEL)
            put("temperature", 0.1)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", dataUrl) }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", VISION_PROMPT)
                        }
                    }
                }
            }
        }
        val content = chat(apiKey, body)
        val drafts = runCatching { WordRepository.parseDrafts(extractJson(content)) }.getOrElse {
            throw DeepSeekException("无法解析识别结果：${content.take(200)}")
        }
        drafts
    }

    /** 根据原文自动补全释义 / 音标 / 类型 */
    suspend fun complete(apiKey: String, text: String): Completion = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", TEXT_MODEL)
            put("temperature", 0.2)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", COMPLETE_PROMPT) }
                addJsonObject { put("role", "user"); put("content", text) }
            }
        }
        val content = chat(apiKey, body)
        val obj = runCatching { json.parseToJsonElement(extractJson(content)).jsonObject }.getOrElse {
            throw DeepSeekException("无法解析补全结果：${content.take(200)}")
        }
        fun s(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        Completion(translation = s("translation"), note = s("note"), type = s("type").ifBlank { null })
    }

    /** 测试密钥是否可用 */
    suspend fun ping(apiKey: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", TEXT_MODEL)
            put("max_tokens", 8)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "ping") } }
        }
        chat(apiKey, body)
    }

    private fun chat(apiKey: String, body: JsonObject): String {
        if (apiKey.isBlank()) throw DeepSeekException("请先在「设置」中填写 DeepSeek API 密钥")
        val conn = (URL("$BASE_URL/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")
                        ?.let { (it as JsonPrimitive).contentOrNull }
                }.getOrNull()
                throw DeepSeekException(
                    when (code) {
                        401 -> "API 密钥无效（401）"
                        402 -> "账户余额不足（402）"
                        429 -> "请求过于频繁，请稍后再试（429）"
                        else -> "请求失败（$code）：${msg ?: text.take(200)}"
                    }
                )
            }
            val root = json.parseToJsonElement(text).jsonObject
            val choices = root["choices"]?.jsonArray ?: JsonArray(emptyList())
            val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            return (message?.get("content") as? JsonPrimitive)?.contentOrNull
                ?: throw DeepSeekException("模型没有返回内容")
        } catch (e: DeepSeekException) {
            throw e
        } catch (e: Exception) {
            throw DeepSeekException("网络错误：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    /** 从模型回复中截取 JSON（去掉 ``` 代码块等） */
    private fun extractJson(s: String): String {
        val t = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = t.indexOfFirst { it == '{' || it == '[' }
        val end = t.indexOfLast { it == '}' || it == ']' }
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }

    /** 缩放到最长边 1600px 并压缩为 JPEG，减少流量 */
    private fun encodeJpeg(src: Bitmap): String {
        val maxSide = 1600
        val scale = maxSide.toFloat() / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
