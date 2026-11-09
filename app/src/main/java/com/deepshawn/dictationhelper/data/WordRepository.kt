package com.deepshawn.dictationhelper.data

import android.content.Context
import com.deepshawn.dictationhelper.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/** 词表导出 / 导入使用的 JSON 文件格式 */
@Serializable
data class WordBook(
    val app: String = "DictationHelper",
    val version: Int = 1,
    val words: List<WordEntry> = emptyList(),
)

/** 一条待导入的数据（来自 JSON 或视觉识别），尚未分配 id */
data class Draft(
    val text: String,
    val translation: String = "",
    val note: String = "",
    val type: EntryType? = null,
)

/**
 * 词表仓库：以 JSON 文件保存在应用私有目录，内存中用 StateFlow 暴露。
 */
class WordRepository(context: Context) {

    private val file = File(context.filesDir, "words.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _words = MutableStateFlow<List<WordEntry>>(emptyList())
    val words: StateFlow<List<WordEntry>> = _words.asStateFlow()

    init {
        scope.launch {
            val loaded = runCatching {
                if (file.exists()) json.decodeFromString(WordBook.serializer(), file.readText()).words else sampleWords()
            }.getOrElse { emptyList() }
            _words.value = loaded
            if (!file.exists()) persist(loaded)
        }
    }

    private var lastId = System.currentTimeMillis()
    @Synchronized
    private fun nextId(): Long {
        val now = System.currentTimeMillis()
        lastId = if (now > lastId) now else lastId + 1
        return lastId
    }

    fun newEntry(draft: Draft): WordEntry {
        val text = draft.text.trim()
        return WordEntry(
            id = nextId(),
            text = text,
            translation = draft.translation.trim(),
            note = draft.note.trim(),
            type = draft.type ?: TextUtils.detectType(text),
            lang = TextUtils.detectLang(text),
        )
    }

    fun upsert(entry: WordEntry) = update { list ->
        val i = list.indexOfFirst { it.id == entry.id }
        if (i >= 0) list.toMutableList().apply { set(i, entry) } else listOf(entry) + list
    }

    fun delete(ids: Set<Long>) = update { list -> list.filterNot { it.id in ids } }

    fun clear() = update { emptyList() }

    /** 批量添加，跳过与已有词条文本完全相同的项，返回实际新增数量 */
    fun addAll(drafts: List<Draft>): Int {
        val existing = _words.value.map { TextUtils.normalize(it.text) }.toHashSet()
        val fresh = drafts
            .filter { it.text.isNotBlank() }
            .filter { existing.add(TextUtils.normalize(it.text)) }
            .map(::newEntry)
        if (fresh.isNotEmpty()) update { list -> fresh + list }
        return fresh.size
    }

    fun exportJson(): String = json.encodeToString(WordBook.serializer(), WordBook(words = _words.value))

    private fun update(block: (List<WordEntry>) -> List<WordEntry>) {
        val newList = block(_words.value)
        _words.value = newList
        scope.launch { persist(newList) }
    }

    private suspend fun persist(list: List<WordEntry>) = mutex.withLock {
        runCatching {
            val tmp = File(file.parentFile, "words.json.tmp")
            tmp.writeText(json.encodeToString(WordBook.serializer(), WordBook(words = list)))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText()); tmp.delete()
            }
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * 宽松解析导入的 JSON，支持以下格式：
         * 1. 本应用导出的 {"words":[...]}
         * 2. 数组 ["apple", "苹果", ...]
         * 3. 数组 [{"text"|"word"|"en"|"zh":..., "translation"|"meaning":..., "type":...}, ...]
         * 4. 对象 {"apple":"苹果", ...}
         */
        fun parseDrafts(raw: String): List<Draft> {
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root = json.parseToJsonElement(cleaned)
            return when (root) {
                is JsonArray -> root.mapNotNull(::elementToDraft)
                is JsonObject -> {
                    val listKey = listOf("words", "items", "data", "list", "entries").firstOrNull { root[it] is JsonArray }
                    if (listKey != null) (root[listKey] as JsonArray).mapNotNull(::elementToDraft)
                    else root.entries.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { Draft(text = k, translation = it) }
                    }
                }
                else -> emptyList()
            }
        }

        private fun elementToDraft(e: JsonElement): Draft? = when (e) {
            is JsonPrimitive -> e.contentOrNull?.takeIf { it.isNotBlank() }?.let { Draft(it) }
            is JsonObject -> {
                fun str(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
                    (e[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
                val text = str("text", "word", "phrase", "sentence", "term", "en", "english", "zh", "chinese")
                text?.let {
                    val typeStr = str("type")?.lowercase()
                    Draft(
                        text = it,
                        translation = str("translation", "meaning", "definition", "trans", "cn", "zh_meaning").orEmpty()
                            .let { tr -> if (tr == it) "" else tr },
                        note = str("note", "phonetic", "example").orEmpty(),
                        type = when (typeStr) {
                            "word", "词汇", "单词", "词" -> EntryType.WORD
                            "phrase", "短语", "词组" -> EntryType.PHRASE
                            "sentence", "句子" -> EntryType.SENTENCE
                            else -> null
                        },
                    )
                }
            }
            else -> null
        }

        private fun sampleWords(): List<WordEntry> {
            var id = 1L
            fun w(text: String, tr: String, note: String = "") = WordEntry(
                id = id++, text = text, translation = tr, note = note,
                type = TextUtils.detectType(text), lang = TextUtils.detectLang(text),
            )
            return listOf(
                w("apple", "苹果", "/ˈæp.əl/"),
                w("environment", "环境", "/ɪnˈvaɪ.rən.mənt/"),
                w("look forward to", "期待；盼望"),
                w("How are you?", "你好吗？"),
                w("环境保护", "environmental protection"),
                w("知识", "knowledge"),
                w("书山有路勤为径，学海无涯苦作舟。", "Diligence is the path to the mountain of knowledge."),
            )
        }
    }
}
