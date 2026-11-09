package com.deepshawn.dictationhelper.util

import com.deepshawn.dictationhelper.data.EntryType
import com.deepshawn.dictationhelper.data.Lang

object TextUtils {

    fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    fun containsHan(s: String): Boolean = s.any(::isHan)

    /** 含汉字即视为中文词条，否则视为英文词条 */
    fun detectLang(text: String): Lang = if (containsHan(text)) Lang.ZH else Lang.EN

    /** 根据长度与空格/标点自动推断词条类型 */
    fun detectType(text: String): EntryType {
        val t = text.trim()
        if (t.isEmpty()) return EntryType.WORD
        val sentenceEnd = t.last() in ".!?。！？…" || t.any { it in ",，;；" }
        return if (containsHan(t)) {
            val hanCount = t.count(::isHan)
            when {
                sentenceEnd || hanCount > 8 -> EntryType.SENTENCE
                hanCount > 4 -> EntryType.PHRASE
                else -> EntryType.WORD
            }
        } else {
            val words = t.split(Regex("\\s+")).filter { it.isNotBlank() }
            when {
                sentenceEnd || words.size >= 5 -> EntryType.SENTENCE
                words.size >= 2 -> EntryType.PHRASE
                else -> EntryType.WORD
            }
        }
    }

    /** 统一小写，去掉标点与多余空白，用于比较 */
    fun normalize(s: String): String = buildString {
        var lastSpace = true
        for (c in s.lowercase()) {
            when {
                c.isLetterOrDigit() -> { append(c); lastSpace = false }
                !lastSpace -> { append(' '); lastSpace = true }
            }
        }
    }.trim()

    /** 只保留英文字母，用于英文首字母/前缀检索 */
    fun lettersOnly(s: String): String = s.lowercase().filter { it in 'a'..'z' }

    /** 英文单词列表（小写） */
    fun englishWords(s: String): List<String> =
        normalize(s).split(' ').map { lettersOnly(it) }.filter { it.isNotEmpty() }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 0..1 的相似度 */
    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val max = maxOf(a.length, b.length)
        return 1f - levenshtein(a, b).toFloat() / max
    }
}
